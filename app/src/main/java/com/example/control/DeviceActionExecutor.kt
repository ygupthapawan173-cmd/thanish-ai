package com.example.control

import android.content.Context
import com.example.ai.AgentExecutionPolicy
import com.example.data.AppDatabase
import com.example.data.OmegaPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import org.json.JSONArray
import org.json.JSONObject

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
    private val phoneControl = PhoneControlManager(appContext)

    fun getBatteryPercent(): Int = phoneControl.getBatteryPercent()
    fun getDeviceContextLine(): String = com.example.ai.DeviceContextProvider.capture(appContext).toPromptLine()
    fun readCalendarSummary(days: Int = 1): String = phoneControl.readUpcomingCalendarEvents(days)

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
            "set_alarm" -> "Setting alarm"
            "set_timer" -> "Starting timer"
            "lookup_contact" -> "Searching contacts"
            "make_phone_call" -> "Preparing call"
            "send_whatsapp_message" -> "Opening WhatsApp"
            "read_calendar" -> "Checking calendar"
            "create_calendar_event" -> "Adding event"
            "compose_email" -> "Drafting email"
            "read_clipboard" -> "Reading clipboard"
            "write_clipboard" -> "Copying text"
            "add_location_reminder" -> "Saving place reminder"
            "open_system_settings" -> "Opening settings"
            "read_notifications" -> "Checking notifications"
            "reply_notification" -> "Preparing reply"
            "get_battery_status" -> "Battery status"
            "save_memory" -> "Saving memory"
            "recall_memories" -> "Searching memory"
            "run_routine" -> "Routine: ${args.optString("routine_name")}"
            else -> "Running $name"
        }

        AgentHudBus.updateState(AgentHudState.Active(statusLabel, isThinking = true, audioAmplitude = 0.55f))
        onActionLog(statusLabel)

        coroutineContext.ensureActive()
        var screenshotBase64: String? = null
        val message = try { when (name) {
            "live_web_search" -> webSearchRunner(args.optString("query"))
            "open_app" -> phoneControl.openAppByName(args.optString("app_name"))
            "toggle_flashlight" -> phoneControl.setFlashlight(args.optBoolean("enabled", true))
            "set_media_volume" -> phoneControl.setMediaVolume(args.optInt("percent", 50))
            "adjust_media_volume" -> phoneControl.adjustMediaVolume(args.optString("direction"))
            "dial_contact" -> phoneControl.dialForConfirmation(args.optString("contact_or_number"))
            "web_search" -> phoneControl.openWebSearch(args.optString("query"))
            "get_battery_status" -> phoneControl.getBatterySummary()
            "set_alarm" -> phoneControl.setAlarm(args.optInt("hour_24", 7), args.optInt("minute", 0), args.optString("label"))
            "set_timer" -> phoneControl.setTimer(args.optInt("seconds", 60), args.optString("label"))
            "lookup_contact" -> phoneControl.lookupContact(args.optString("name"))
            "make_phone_call" -> phoneControl.callContactOrNumber(args.optString("contact_or_number").ifBlank { args.optString("phone_number") })
            "send_whatsapp_message" -> phoneControl.openWhatsAppMessage(
                args.optString("contact_or_number").ifBlank { args.optString("phone_number") },
                args.optString("message")
            )
            "read_calendar" -> phoneControl.readUpcomingCalendarEvents(args.optInt("days", 2))
            "create_calendar_event" -> phoneControl.createCalendarEvent(args.optString("title"), args.optString("description"))
            "compose_email" -> phoneControl.composeEmail(args.optString("to"), args.optString("subject"), args.optString("body"))
            "read_clipboard" -> phoneControl.readClipboard()
            "write_clipboard" -> phoneControl.writeClipboard(args.optString("text"))
            "add_location_reminder" -> {
                val place = args.optString("place")
                val text = args.optString("reminder")
                database.addLocationReminder(place, text)
                "Saved location reminder for '$place': $text."
            }
            "open_system_settings" -> phoneControl.openSystemSetting(args.optString("section", "general"))
            "read_notifications" -> NotificationInbox.formatForAgent(args.optInt("limit", 6))
            "reply_notification" -> {
                val sender = args.optString("sender_or_app")
                val text = args.optString("reply_text")
                if (preferences.settings.value.confirmNotifReply) {
                    NotificationInbox.queueReplyForConfirmation(sender, text)
                } else {
                    NotificationInbox.sendDirectReply(appContext, sender, text)
                }
            }
            "save_memory" -> {
                val k = args.optString("key"); val v = args.optString("value")
                database.upsertMemory(k, v)
                "Encrypted & saved '$k' = '$v' in personal memory."
            }
            "recall_memories" -> database.formatRelevantMemoriesForPrompt(args.optString("query")).ifBlank { "No relevant saved memories found." }
            "run_routine" -> executeRoutineByName(args.optString("routine_name"), onActionLog)
            "tap_ui_text" -> PanduAccessibilityService.instance?.clickNodeByText(args.optString("target_text"))
                ?: "Automation Service is OFF. Enable it in Settings > Accessibility."
            "tap_screen_coordinates" -> PanduAccessibilityService.instance?.tapNormalizedCoordinates(
                args.optInt("x_norm", 500), args.optInt("y_norm", 500), args.optString("label", "Visual Tap")
            ) ?: "Automation Service is OFF."
            "type_screen_text" -> PanduAccessibilityService.instance?.typeTextIntoFocusedField(args.optString("text"))
                ?: "Automation Service is OFF."
            "scroll_screen" -> PanduAccessibilityService.instance?.scrollScreen(args.optString("direction", "down"))
                ?: "Automation Service is OFF."
            "navigate_device" -> PanduAccessibilityService.instance?.performDeviceNavigation(args.optString("action", "back"))
                ?: "Automation Service is OFF."
            "capture_screen_vision" -> {
                val svc = PanduAccessibilityService.instance
                if (svc == null) "Automation Service is OFF."
                else {
                    val b64 = svc.captureScreenBase64Jpeg()
                    if (b64 != null) {
                        screenshotBase64 = b64
                        "Live screen screenshot captured for visual inspection."
                    } else "Screenshot requires Android 11+."
                }
            }
            else -> "Unknown tool: $name"
        } } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Action $name failed: ${e.message ?: "unknown error"}"
        }

        coroutineContext.ensureActive()
        return ToolExecutionOutput(JSONObject().put("result", message), screenshotBase64)
    }

    suspend fun executeRoutineByName(query: String, onActionLog: (String) -> Unit = {}): String {
        val routine = database.findRoutineByNameOrTrigger(query) ?: return "No routine found matching '$query'."
        val steps = runCatching { JSONArray(routine.stepsJson) }.getOrNull() ?: return "Invalid routine steps."
        if (!AgentExecutionPolicy.routineAllowed(steps.length())) return "Routine has an invalid step count; no actions dispatched."
        val validated = (0 until steps.length()).map { i ->
            val step = steps.optJSONObject(i) ?: return "Invalid routine step; no actions dispatched."
            val tool = step.optString("tool")
            if (!AgentExecutionPolicy.validRoutineTool(tool)) return "Invalid or nested routine tool; no actions dispatched."
            val args = step.optJSONObject("args") ?: return "Invalid routine arguments; no actions dispatched."
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
        return "Routine '${routine.name}' execution results: ${results.joinToString(" | ")}"
    }
}
