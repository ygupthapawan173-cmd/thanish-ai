package com.example.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Hardened Cross-App Accessibility Automation Service (Section 11):
 * - Reactive connection state & defensive exception guards on window/node tree traversal
 * - Strict bitmap & hardware buffer lifecycle cleanup
 * - Screen-lock safety boundary; NO Screen Reader narration.
 */
class PanduAccessibilityService : AccessibilityService() {

    private var haptics: HapticFeedbackHelper? = null
    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onServiceConnected() {
        super.onServiceConnected()
        runCatching { serviceScope.cancel() }
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        instance = this
        _isConnected.value = true
        haptics = runCatching { HapticFeedbackHelper(this) }.getOrNull()

        serviceScope.launch {
            AgentHudBus.abortSignal.collect {
                runCatching { haptics?.abortPulse() }
                AgentHudBus.updateState(AgentHudState.Hidden)
            }
        }
    }

    private fun available(): Boolean = runCatching {
        instance === this && !(getSystemService(KeyguardManager::class.java)?.isDeviceLocked ?: true)
    }.getOrDefault(false)

    suspend fun performVisualTap(x: Float, y: Float, label: String, highlightBox: Rect? = null): Boolean =
        withContext(Dispatchers.Main) {
            val metrics = resources.displayMetrics
            if (!available() || !x.isFinite() || !y.isFinite() ||
                x < 0f || y < 0f || x >= metrics.widthPixels || y >= metrics.heightPixels
            ) return@withContext false

            runCatching { haptics?.softTap() }
            withTimeoutOrNull(2_500L) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    runCatching {
                        AgentHudBus.updateState(AgentHudState.Active(label, highlightBox = highlightBox, audioAmplitude = 0.6f))
                        AgentHudBus.emitEvent(HudVisualEvent.TapRipple(x, y))
                        val path = Path().apply { moveTo(x, y) }
                        val gesture = GestureDescription.Builder()
                            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
                            .build()
                        val ok = dispatchGesture(gesture, object : GestureResultCallback() {
                            override fun onCompleted(gestureDescription: GestureDescription?) {
                                if (cont.isActive) cont.resume(true)
                            }
                            override fun onCancelled(gestureDescription: GestureDescription?) {
                                if (cont.isActive) cont.resume(false)
                            }
                        }, null)
                        if (!ok && cont.isActive) cont.resume(false)
                    }.onFailure {
                        if (cont.isActive) cont.resume(false)
                    }
                }
            } ?: false
        }

    suspend fun tapNormalizedCoordinates(xNorm: Int, yNorm: Int, label: String): String {
        val m = resources.displayMetrics
        val px = (xNorm.coerceIn(0, 1000) / 1000f) * m.widthPixels
        val py = (yNorm.coerceIn(0, 1000) / 1000f) * m.heightPixels
        val box = Rect((px - 42).toInt(), (py - 42).toInt(), (px + 42).toInt(), (py + 42).toInt())
        val ok = performVisualTap(px, py, label.ifBlank { "Tap ($xNorm,$yNorm)" }, box)
        delay(220)
        return if (ok) "Tapped ($xNorm, $yNorm)." else "Tap cancelled or blocked."
    }

    suspend fun typeTextIntoFocusedField(text: String): String = withContext(Dispatchers.Main) {
        if (!available()) return@withContext "Accessibility unavailable or device locked."
        runCatching {
            val root = rootInActiveWindow ?: return@withContext "No active window found."
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: findBounded(root) { it.isEditable }
                ?: return@withContext "No editable text field found."
            if (focused.isPassword || !focused.isEditable) return@withContext "Protected or non-editable field."
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            if (focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "Typed text." else "Unable to type."
        }.getOrElse { "Accessibility window busy: ${it.message ?: "retry"}" }
    }

    private fun findBounded(root: AccessibilityNodeInfo?, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (root == null) return null
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited++ < 1200) {
            val node = pending.removeLast()
            val matched = runCatching { matches(node) }.getOrDefault(false)
            if (matched) return node
            val childCount = runCatching { node.childCount }.getOrDefault(0)
            for (i in minOf(childCount, 200) - 1 downTo 0) {
                if (pending.size + visited >= 1200) break
                runCatching { node.getChild(i) }.getOrNull()?.let { pending.add(it) }
            }
        }
        return null
    }

    suspend fun scrollScreen(direction: String): String = withContext(Dispatchers.Main) {
        if (!available() || direction.lowercase() !in setOf("up", "down")) {
            return@withContext "Scroll unavailable or invalid direction."
        }
        val m = resources.displayMetrics
        val cx = m.widthPixels * 0.5f
        val isDown = direction.lowercase() != "up"
        val startY = if (isDown) m.heightPixels * 0.74f else m.heightPixels * 0.28f
        val endY = if (isDown) m.heightPixels * 0.28f else m.heightPixels * 0.74f
        AgentHudBus.emitEvent(HudVisualEvent.SwipeTrail(cx, startY, cx, endY))
        val ok = withTimeoutOrNull(3_000L) {
            suspendCancellableCoroutine<Boolean> { cont ->
                runCatching {
                    val path = Path().apply { moveTo(cx, startY); lineTo(cx, endY) }
                    val gesture = GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, 0L, 320L))
                        .build()
                    val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(true)
                        }
                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(false)
                        }
                    }, null)
                    if (!dispatched && cont.isActive) cont.resume(false)
                }.onFailure {
                    if (cont.isActive) cont.resume(false)
                }
            }
        } ?: false
        if (ok) "Scrolled ${if (isDown) "down" else "up"}." else "Scroll cancelled."
    }

    suspend fun clickNodeByText(targetText: String): String = withContext(Dispatchers.Main) {
        if (!available()) return@withContext "Accessibility unavailable or device locked."
        val q = targetText.trim()
        if (q.isEmpty()) return@withContext "Target empty."
        runCatching {
            val root = rootInActiveWindow ?: return@withContext "No active window."
            val exact = mutableListOf<AccessibilityNodeInfo>()
            val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
            pending.add(root)
            var visited = 0
            while (pending.isNotEmpty() && visited++ < 1200) {
                val node = pending.removeLast()
                val isMatch = runCatching {
                    node.isVisibleToUser && !node.isPassword &&
                        (node.text?.toString()?.equals(q, true) == true ||
                            node.contentDescription?.toString()?.equals(q, true) == true)
                }.getOrDefault(false)
                if (isMatch) exact.add(node)
                if (exact.size > 1) break
                val childCount = runCatching { node.childCount }.getOrDefault(0)
                for (i in minOf(childCount, 200) - 1 downTo 0) {
                    if (pending.size + visited >= 1200) break
                    runCatching { node.getChild(i) }.getOrNull()?.let { pending.add(it) }
                }
            }
            if (pending.isNotEmpty() && exact.size < 2) return@withContext "Window too complex; be more specific."
            if (exact.size > 1) return@withContext "Multiple matching controls; specify a unique target."
            val matched = exact.singleOrNull() ?: return@withContext "Could not find control '$q'."
            val rect = Rect()
            runCatching { matched.getBoundsInScreen(rect) }
            if (!rect.isEmpty) {
                val tapped = performVisualTap(rect.exactCenterX(), rect.exactCenterY(), "Tapping '$q'", rect)
                delay(220)
                if (tapped) return@withContext "Tapped '$q'."
            }
            var node: AccessibilityNodeInfo? = matched
            var depth = 0
            while (node != null && !node.isClickable && depth++ < 64) {
                node = runCatching { node.parent }.getOrNull()
            }
            if (node?.let { runCatching { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false) } == true) {
                "Clicked '$q'."
            } else {
                "Could not click '$q'."
            }
        }.getOrElse { "Control tap interrupted: ${it.message ?: "retry"}" }
    }

    fun performDeviceNavigation(action: String): String {
        if (!available()) return "Accessibility unavailable or device locked."
        val code = when (action.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return "Unknown action: $action"
        }
        return if (runCatching { performGlobalAction(code) }.getOrDefault(false)) "Performed $action." else "Failed $action."
    }

    suspend fun captureScreenBase64Jpeg(): String? = withContext(Dispatchers.Main) {
        if (!available() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext null
        withTimeoutOrNull(5_000L) {
            suspendCancellableCoroutine<String?> { cont ->
                runCatching {
                    takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            var hw: Bitmap? = null
                            var sw: Bitmap? = null
                            try {
                                val buffer = screenshot.hardwareBuffer
                                hw = try {
                                    if (cont.isActive) Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace) else null
                                } finally {
                                    runCatching { buffer.close() }
                                }
                                if (hw != null) {
                                    sw = hw.copy(Bitmap.Config.ARGB_8888, false)
                                    val b64 = if (sw != null) VisionHelper.bitmapToBase64Jpeg(sw) else null
                                    if (cont.isActive) cont.resume(b64)
                                } else if (cont.isActive) {
                                    cont.resume(null)
                                }
                            } catch (_: Exception) {
                                if (cont.isActive) cont.resume(null)
                            } finally {
                                runCatching { hw?.recycle() }
                                runCatching { sw?.recycle() }
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                }.onFailure {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            AgentHudBus.triggerAbort()
        }
        return super.onKeyEvent(event)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        _isConnected.value = false
        runCatching { serviceScope.cancel() }
        AgentHudBus.updateState(AgentHudState.Hidden)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        _isConnected.value = false
        runCatching { serviceScope.cancel() }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() {
        AgentHudBus.updateState(AgentHudState.Hidden)
    }

    companion object {
        @Volatile
        var instance: PanduAccessibilityService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    }
}
