package com.example.control

import android.content.Context
import com.example.ai.AgentExecutionPolicy
import com.example.data.AppDatabase
import com.example.data.OmegaPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

data class ToolExecutionOutput(
    val responseJson: JSONObject,
    val capturedImageBase64: String? = null
)

class DeviceActionExecutor(
    context: Context,
    private val database: AppDatabase,
    private val preferences: OmegaPreferences
) {
    private val appContext = context.applicationContext
    val phoneControl = PhoneControlManager(appContext)

    fun getBatteryPercent(): Int = phoneControl.getBatteryPercent()
    fun getDeviceContextLine(): String = com.example.ai.DeviceContextProvider.capture(appContext).toPromptLine()

    suspend fun executeTool(
        name: String,
        args: JSONObject,
        webSearchRunner: suspend (String) -> String = { "Web search unavailable." },
        onActionLog: (String) -> Unit = {}
    ): ToolExecutionOutput {
        coroutineContext.ensureActive()
        val statusLabel = when (name) {
            "live_web_search" -> "Searching: ${args.optString("query")}"
            "open_app" -> "Opening ${args.optString("app_name")}"
            "tap_ui_text" -> "Tapping ${args.optString("target_text")}"
            "tap_screen_coordinates" -> "Visual Tap (${args.optInt("x_norm")},${args.optInt("y_norm")})"
            "type_screen_text" -> "Typing text..."
            "scroll_screen" -> "Scrolling ${args.optString("direction")}"
            "navigate_device" -> "Navigating ${args.optString("action")}"
            "capture_screen_vision" -> "Capturing Vision..."
            "toggle_flashlight" -> "Flashlight"
            "set_media_volume" -> "Volume ${args.optInt("percent")}%"
            "adjust_media_volume" -> "Volume ${args.optString("direction")}"
            "set_alarm" -> "Setting alarm"
            "set_timer" -> "Starting timer"
            "lookup_contact" -> "Searching contacts"
            "dial_contact", "make_phone_call" -> "Preparing call"
            "read_calendar" -> "Checking calendar"
            "create_calendar_event" -> "Adding event"
            "open_system_settings" -> "Opening settings"
            "read_notifications" -> "Checking notifications"
            "reply_notification" -> "Preparing message reply"
            "get_battery_status" -> "Battery status"
            "run_routine" -> "Scheduled Automation: ${args.optString("routine_name")}"
            else -> "Running $name"
        }

        AgentHudBus.updateState(AgentHudState.Active(statusLabel, isThinking = true, audioAmplitude = 0.55f))
        onActionLog(statusLabel)

        coroutineContext.ensureActive()
        var screenshotBase64: String? = null
        val confirmSensitive = preferences.settings.value.confirmSensitiveActions
        val message = try {
            when (name) {
                "live_web_search" -> webSearchRunner(args.optString("query"))
                "open_app" -> phoneControl.openAppByName(args.optString("app_name"))
                "toggle_flashlight" -> phoneControl.setFlashlight(args.optBoolean("enabled", true))
                "set_media_volume" -> phoneControl.setMediaVolume(args.optInt("percent", 50))
                "adjust_media_volume" -> phoneControl.adjustMediaVolume(args.optString("direction"))
                "dial_contact", "make_phone_call" -> {
                    val target = args.optString("contact_or_number").ifBlank { args.optString("phone_number") }
                    if (confirmSensitive) phoneControl.dialForConfirmation(target)
                    else phoneControl.callContactOrNumber(target)
                }
                "get_battery_status" -> phoneControl.getBatterySummary()
                "set_alarm" -> phoneControl.setAlarm(args.optInt("hour_24", 7), args.optInt("minute", 0), args.optString("label"))
                "set_timer" -> phoneControl.setTimer(args.optInt("seconds", 60), args.optString("label"))
                "lookup_contact" -> phoneControl.lookupContact(args.optString("name"))
                "read_calendar" -> phoneControl.readUpcomingCalendarEvents(args.optInt("days", 2))
                "create_calendar_event" -> phoneControl.createCalendarEvent(args.optString("title"), args.optString("description"))
                "open_system_settings" -> phoneControl.openSystemSetting(args.optString("section", "general"))
                "read_notifications" -> NotificationInbox.formatForAgent(args.optInt("limit", 6))
                "reply_notification" -> {
                    val sender = args.optString("sender_or_app")
                    val text = args.optString("reply_text")
                    if (confirmSensitive) {
                        NotificationInbox.queueReplyForConfirmation(sender, text)
                    } else {
                        NotificationInbox.sendDirectReply(appContext, sender, text)
                    }
                }
                "run_routine" -> executeRoutineByName(args.optString("routine_name"), onActionLog)
                "tap_ui_text" -> PanduAccessibilityService.instance?.clickNodeByText(args.optString("target_text"))
                    ?: "Accessibility Automation Service is OFF. Enable it in Control > Accessibility."
                "tap_screen_coordinates" -> PanduAccessibilityService.instance?.tapNormalizedCoordinates(
                    args.optInt("x_norm", 500), args.optInt("y_norm", 500), args.optString("label", "Visual Tap")
                ) ?: "Accessibility Automation Service is OFF."
                "type_screen_text" -> PanduAccessibilityService.instance?.typeTextIntoFocusedField(args.optString("text"))
                    ?: "Accessibility Automation Service is OFF."
                "scroll_screen" -> PanduAccessibilityService.instance?.scrollScreen(args.optString("direction", "down"))
                    ?: "Accessibility Automation Service is OFF."
                "navigate_device" -> PanduAccessibilityService.instance?.performDeviceNavigation(args.optString("action", "back"))
                    ?: "Accessibility Automation Service is OFF."
                "capture_screen_vision" -> {
                    val svc = PanduAccessibilityService.instance
                    if (svc == null) {
                        "Accessibility Automation Service is OFF."
                    } else {
                        val b64 = svc.captureScreenBase64Jpeg()
                        if (b64 != null) {
                            screenshotBase64 = b64
                            "Live screen screenshot captured for visual inspection."
                        } else {
                            "Screenshot capture requires Android 11+."
                        }
                    }
                }
                else -> "Unknown tool: $name"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Action $name failed: ${e.message ?: "unknown error"}"
        }

        coroutineContext.ensureActive()
        return ToolExecutionOutput(JSONObject().put("result", message), screenshotBase64)
    }

    suspend fun executeRoutineByName(query: String, onActionLog: (String) -> Unit = {}): String {
        val routine = database.findRoutineByNameOrTrigger(query) ?: return "No scheduled automation found matching '$query'."
        val steps = runCatching { JSONArray(routine.stepsJson) }.getOrNull() ?: return "Invalid automation steps."
        if (!AgentExecutionPolicy.routineAllowed(steps.length())) return "Invalid step count; no actions dispatched."
        val validated = (0 until steps.length()).map { i ->
            val step = steps.optJSONObject(i) ?: return "Invalid automation step."
            val tool = step.optString("tool")
            if (!AgentExecutionPolicy.validRoutineTool(tool)) return "Invalid nested tool."
            val args = step.optJSONObject("args") ?: return "Invalid arguments."
            tool to args
        }
        val results = mutableListOf<String>()
        for ((tool, args) in validated) {
            coroutineContext.ensureActive()
            if (tool.isNotBlank() && tool != "run_routine") {
                val out = executeTool(tool, args, onActionLog = onActionLog)
                results.add(out.responseJson.optString("result"))
                delay(320)
            }
        }
        return "Automation '${routine.name}' completed: ${results.joinToString(" | ")}"
    }
}
