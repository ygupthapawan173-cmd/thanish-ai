package com.example.control

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-consented service for system navigation and one-step, confirmed phone-agent actions.
 * Screen capture is allowed only while the user has explicitly started an Agent task.
 */
class PanduAccessibilityService : AccessibilityService() {

    private var agentNodes: List<AccessibilityNodeInfo> = emptyList()
    private var agentNodeLabels: List<String> = emptyList()

    private fun captureVisibleAgentScreen(): String? {
        if (!agentScreenCaptureGranted) return null
        val root = rootInActiveWindow ?: return null
        val packageName = root.packageName?.toString().orEmpty()
        if (packageName in BLOCKED_AGENT_PACKAGES) return null

        val nodes = mutableListOf<AccessibilityNodeInfo>()
        val labels = mutableListOf<String>()
        val screen = StringBuilder("App: $packageName\n")

        fun walk(node: AccessibilityNodeInfo) {
            if (!node.isVisibleToUser || node.isPassword) return

            val editable = node.isEditable
            // Do not send the current value of editable fields to the AI provider.
            val rawLabel = if (editable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    node.hintText?.toString()?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            } else {
                node.text?.toString()?.takeIf { it.isNotBlank() }
                    ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
            }

            val useful = node.isClickable || editable || node.isScrollable || !rawLabel.isNullOrBlank()
            if (useful && nodes.size < 100) {
                val kind = when {
                    editable -> "input"
                    node.isScrollable -> "scrollable"
                    node.isClickable -> "button"
                    else -> "text"
                }
                val label = (rawLabel ?: if (editable) "text field" else kind)
                    .replace("\n", " ")
                    .replace("\r", " ")
                    .replace("\"", "'")
                    .take(90)
                val id = nodes.size
                nodes += node
                labels += "$kind $label"
                screen.append("[$id] $kind \"$label\"\n")
            }

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(::walk)
            }
        }

        walk(root)
        agentNodes = nodes
        agentNodeLabels = labels
        return screen.toString().take(7_000)
    }

    private fun describeAgentAction(name: String, args: JSONObject): String? {
        return when (name) {
            "open_app" -> "Open app: " + args.optString("name").take(60)
            "tap" -> agentNodeLabels.getOrNull(args.optInt("id", -1))?.let { "Tap $it" }
            "set_text" -> {
                val id = args.optInt("id", -1)
                val node = agentNodes.getOrNull(id)
                val text = args.optString("text").take(100)
                if (node != null && node.isEditable && !node.isPassword && text.isNotBlank()) {
                    "Enter \"$text\" into " + (agentNodeLabels.getOrNull(id) ?: "input field")
                } else {
                    null
                }
            }
            "scroll" -> "Scroll " + args.optString("direction")
            "press" -> "Press " + args.optString("key")
            else -> null
        }
    }

    private fun executeAgentAction(name: String, argsJson: String): CommandExecutionResult {
        return runCatching {
            val args = JSONObject(argsJson)
            when (name) {
                "open_app" -> {
                    val requested = args.optString("name").trim()
                    require(requested.isNotBlank() && requested.length <= 60)
                    val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    val match = packageManager.queryIntentActivities(launch, 0).firstOrNull {
                        it.loadLabel(packageManager).toString().equals(requested, ignoreCase = true)
                    } ?: packageManager.queryIntentActivities(launch, 0).firstOrNull {
                        it.loadLabel(packageManager).toString().contains(requested, ignoreCase = true)
                    } ?: error("App not found: $requested")
                    val intent = packageManager.getLaunchIntentForPackage(match.activityInfo.packageName)
                        ?: error("App cannot be launched.")
                    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                "tap" -> {
                    val node = agentNodes.getOrNull(args.optInt("id", -1)) ?: error("Screen item expired.")
                    require(node.refresh() && node.isVisibleToUser && !node.isPassword)
                    var current: AccessibilityNodeInfo? = node
                    var clicked = false
                    while (current != null && !clicked) {
                        val parent = current.parent
                        if (current.isClickable) {
                            clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }
                        current = if (clicked) null else parent
                    }
                    require(clicked) { "That control is no longer clickable." }
                }
                "set_text" -> {
                    val node = agentNodes.getOrNull(args.optInt("id", -1)) ?: error("Input field expired.")
                    require(node.refresh() && node.isVisibleToUser && node.isEditable && !node.isPassword)
                    val value = args.optString("text")
                    require(value.isNotBlank() && value.length <= 300)
                    val bundle = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                    }
                    require(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)) {
                        "Could not enter text in that field."
                    }
                }
                "scroll" -> {
                    val direction = args.optString("direction")
                    require(direction == "up" || direction == "down")
                    val target = if (args.has("id")) {
                        agentNodes.getOrNull(args.optInt("id", -1))
                    } else {
                        agentNodes.firstOrNull { it.isScrollable }
                    }
                    require(target != null && target.refresh() && target.isVisibleToUser && target.isScrollable)
                    val action = if (direction == "down") {
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    } else {
                        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    }
                    require(target.performAction(action)) { "Nothing to scroll." }
                }
                "press" -> {
                    val action = when (args.optString("key")) {
                        "back" -> GLOBAL_ACTION_BACK
                        "home" -> GLOBAL_ACTION_HOME
                        "recents" -> GLOBAL_ACTION_RECENTS
                        "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
                        else -> error("Unsupported navigation key.")
                    }
                    require(performGlobalAction(action))
                }
                else -> error("Unsupported action.")
            }
            CommandExecutionResult(true, "చేశాను బాస్.")
        }.getOrElse {
            CommandExecutionResult(false, "Agent action చేయలేకపోయాను: " + (it.localizedMessage ?: "unknown error"))
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Agent screen snapshots are captured only on demand, never collected in the background.
    }

    override fun onInterrupt() {
        // Keep service connection state accurate: interruption is not disconnection.
        // Discard temporary screen data and stop agent capture until consent is renewed.
        agentNodes = emptyList()
        agentNodeLabels = emptyList()
        agentScreenCaptureGranted = false
    }

    override fun onUnbind(intent: Intent?): Boolean {
        markDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        markDisconnected()
        super.onDestroy()
    }

    private fun markDisconnected() {
        if (instance === this) {
            instance = null
            agentNodes = emptyList()
            agentNodeLabels = emptyList()
            agentScreenCaptureGranted = false
            _isConnected.value = false
        }
    }

    companion object {
        @Volatile
        private var instance: PanduAccessibilityService? = null

        @Volatile
        private var agentScreenCaptureGranted = false

        private val BLOCKED_AGENT_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.google.android.apps.messaging",
            "com.google.android.gm",
            "com.google.android.apps.authenticator2",
            "com.google.android.apps.walletnfcrel",
            "com.phonepe.app",
            "net.one97.paytm",
            "com.google.android.apps.nbu.paisa.user"
        )

        fun setAgentScreenCaptureConsent(granted: Boolean) {
            agentScreenCaptureGranted = granted
            if (!granted) {
                instance?.agentNodes = emptyList()
                instance?.agentNodeLabels = emptyList()
            }
        }

        fun captureAgentScreen(): String? = instance?.captureVisibleAgentScreen()

        fun describeAgentAction(name: String, args: JSONObject): String? =
            instance?.describeAgentAction(name, args)

        fun executeAgentAction(name: String, argsJson: String): CommandExecutionResult =
            instance?.executeAgentAction(name, argsJson)
                ?: CommandExecutionResult(false, "Accessibility Service కనెక్ట్ కాలేదు బాస్.")

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        fun performGlobalNav(action: GlobalNavigationAction): Boolean {
            val service = instance ?: return false
            val globalConstant = when (action) {
                GlobalNavigationAction.BACK -> GLOBAL_ACTION_BACK
                GlobalNavigationAction.HOME -> GLOBAL_ACTION_HOME
                GlobalNavigationAction.RECENTS -> GLOBAL_ACTION_RECENTS
                GlobalNavigationAction.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
            }
            return runCatching {
                service.performGlobalAction(globalConstant)
            }.getOrDefault(false)
        }
    }
}
