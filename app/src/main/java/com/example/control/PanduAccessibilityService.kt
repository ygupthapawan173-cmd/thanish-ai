package com.example.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.app.KeyguardManager
import kotlin.coroutines.resume

/**
 * Permission-based Cross-App Automation & Visual HUD Service (NO screen-reader narration).
 */
class PanduAccessibilityService : AccessibilityService() {

    private lateinit var haptics: HapticFeedbackHelper
    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceScope.cancel()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        android.util.Log.i("OmegaAccessibility", "SERVICE_CONNECTED")
        instance = this
        haptics = HapticFeedbackHelper(this)

        serviceScope.launch {
            AgentHudBus.abortSignal.collect {
                haptics.abortPulse()
                AgentHudBus.updateState(AgentHudState.Hidden)
            }
        }
    }

    private fun available() = instance === this && !getSystemService(KeyguardManager::class.java).isDeviceLocked

    suspend fun performVisualTap(x: Float, y: Float, label: String, highlightBox: Rect? = null): Boolean =
        withContext(Dispatchers.Main) {
            if (!available() || !x.isFinite() || !y.isFinite() || x < 0 || y < 0 || x >= resources.displayMetrics.widthPixels || y >= resources.displayMetrics.heightPixels) return@withContext false
            haptics.softTap()
            withTimeoutOrNull(2500L) { suspendCancellableCoroutine<Boolean> { cont ->
                AgentHudBus.updateState(AgentHudState.Active(label, highlightBox = highlightBox, audioAmplitude = 0.6f))
                AgentHudBus.emitEvent(HudVisualEvent.TapRipple(x, y))
                val path = Path().apply { moveTo(x, y) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
                    .build()
                val ok = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                    override fun onCancelled(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(false) }
                }, null)
                if (!ok && cont.isActive) cont.resume(false)
            } } ?: false
        }

    suspend fun tapNormalizedCoordinates(xNorm: Int, yNorm: Int, label: String): String {
        val m = resources.displayMetrics
        val px = (xNorm.coerceIn(0, 1000) / 1000f) * m.widthPixels
        val py = (yNorm.coerceIn(0, 1000) / 1000f) * m.heightPixels
        val box = Rect((px - 42).toInt(), (py - 42).toInt(), (px + 42).toInt(), (py + 42).toInt())
        val ok = performVisualTap(px, py, label.ifBlank { "Tap ($xNorm,$yNorm)" }, box)
        delay(220)
        return if (ok) "Tapped ($xNorm, $yNorm)." else "Tap cancelled."
    }

    suspend fun typeTextIntoFocusedField(text: String): String = withContext(Dispatchers.Main) {
        if (!available()) return@withContext "Accessibility unavailable or device locked."
        val root = rootInActiveWindow ?: return@withContext "No active window found."
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFirstEditable(root)
            ?: return@withContext "No editable text field found."
        if (focused.isPassword || !focused.isEditable) return@withContext "Protected or non-editable field."
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        if (focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "Typed text." else "Unable to type."
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? = findBounded(node) { it.isEditable }

    // Bound traversal on large or malformed windows; avoid recursive stack overflow on the UI thread.
    private fun findBounded(root: AccessibilityNodeInfo?, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (root == null) return null
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited++ < 1500) {
            val node = pending.removeLast()
            if (matches(node)) return node
            for (i in minOf(node.childCount, 1500) - 1 downTo 0) {
                if (pending.size + visited >= 1500) break
                node.getChild(i)?.let { pending.add(it) }
            }
        }
        return null
    }

    suspend fun scrollScreen(direction: String): String = withContext(Dispatchers.Main) {
        if (!available() || direction.lowercase() !in setOf("up", "down")) return@withContext "Scroll unavailable or invalid direction."
        val m = resources.displayMetrics
        val cx = m.widthPixels * 0.5f
        val isDown = direction.lowercase() != "up"
        val startY = if (isDown) m.heightPixels * 0.74f else m.heightPixels * 0.28f
        val endY = if (isDown) m.heightPixels * 0.28f else m.heightPixels * 0.74f
        AgentHudBus.emitEvent(HudVisualEvent.SwipeTrail(cx, startY, cx, endY))
        val ok = withTimeoutOrNull(3000L) { suspendCancellableCoroutine<Boolean> { cont ->
            val path = Path().apply { moveTo(cx, startY); lineTo(cx, endY) }
            val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0L, 320L)).build()
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(false) }
            }, null)
            if (!dispatched && cont.isActive) cont.resume(false)
        } } ?: false
        if (ok) "Scrolled ${if (isDown) "down" else "up"}." else "Scroll cancelled."
    }

    suspend fun clickNodeByText(targetText: String): String = withContext(Dispatchers.Main) {
        if (!available()) return@withContext "Accessibility unavailable or device locked."
        val q = targetText.trim()
        if (q.isEmpty()) return@withContext "Target empty."
        val root = rootInActiveWindow ?: return@withContext "No active window."
        val exact = mutableListOf<AccessibilityNodeInfo>()
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>(); pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited++ < 1500) {
            val node = pending.removeLast()
            if (node.isVisibleToUser && !node.isPassword && (node.text?.toString()?.equals(q, true) == true || node.contentDescription?.toString()?.equals(q, true) == true)) exact.add(node)
            if (exact.size > 1) break
            for (i in minOf(node.childCount, 1500) - 1 downTo 0) {
                if (pending.size + visited >= 1500) break
                node.getChild(i)?.let { pending.add(it) }
            }
        }
        if (pending.isNotEmpty() && exact.size < 2) return@withContext "Window too complex; use a more specific action."
        if (exact.size > 1) return@withContext "Multiple matching controls; specify a unique target."
        val matched = exact.singleOrNull() ?: return@withContext "Could not find a unique exact control '$q'."
        val rect = Rect()
        matched.getBoundsInScreen(rect)
        if (!rect.isEmpty) {
            val tapped = performVisualTap(rect.exactCenterX(), rect.exactCenterY(), "Tapping '$q'", rect)
            delay(220)
            if (tapped) return@withContext "Tapped '$q'."
        }
        var node: AccessibilityNodeInfo? = matched
        var depth = 0
        while (node != null && !node.isClickable && depth++ < 64) node = node.parent
        if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) "Clicked '$q'." else "Could not click '$q'."
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
        return if (performGlobalAction(code)) "Performed $action." else "Failed $action."
    }

    suspend fun captureScreenBase64Jpeg(): String? = withContext(Dispatchers.Main) {
        if (!available() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext null
        withTimeoutOrNull(5000L) { suspendCancellableCoroutine<String?> { cont ->
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    try {
                        val buffer = screenshot.hardwareBuffer
                        val hw = try { if (cont.isActive) Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace) else null } finally { buffer.close() }
                        if (hw != null) {
                            val sw = hw.copy(Bitmap.Config.ARGB_8888, false)
                            hw.recycle()
                            val b64 = VisionHelper.bitmapToBase64Jpeg(sw)
                            sw.recycle()
                            if (cont.isActive) cont.resume(b64)
                        } else if (cont.isActive) cont.resume(null)
                    } catch (_: Exception) { if (cont.isActive) cont.resume(null) }
                }
                override fun onFailure(errorCode: Int) { if (cont.isActive) cont.resume(null) }
            })
        } }
    }

    private fun findByDesc(node: AccessibilityNodeInfo?, query: String): AccessibilityNodeInfo? =
        findBounded(node) { it.contentDescription?.toString()?.contains(query, ignoreCase = true) == true }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            AgentHudBus.triggerAbort()
        }
        return super.onKeyEvent(event)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        serviceScope.cancel()
        AgentHudBus.updateState(AgentHudState.Hidden)
        android.util.Log.w("OmegaAccessibility", "SERVICE_UNBOUND")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        android.util.Log.i("OmegaAccessibility", "SERVICE_DESTROYED")
        if (instance == this) instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { AgentHudBus.updateState(AgentHudState.Hidden) }

    companion object {
        @Volatile
        var instance: PanduAccessibilityService? = null
            private set
    }
}
