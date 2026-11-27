package com.example.autonomous

import com.example.data.ChatMessageEntity
import com.example.data.LocationReminder
import com.example.data.VoiceRoutine
import java.util.Locale

data class ProactiveSuggestion(
    val id: String,
    val title: String,
    val detail: String,
    val actionLabel: String,
    val promptToRun: String
)

data class RoutineSuggestion(
    val suggestedName: String,
    val triggerPhrase: String,
    val summary: String,
    val stepsJson: String
)

enum class AutomationConditionType(val label: String) {
    BATTERY_BELOW_15("IF Battery < 15%"),
    CHARGER_CONNECTED("IF Charger Connected"),
    BLUETOOTH_CONNECTED("IF Bluetooth Audio Connected"),
    UNREAD_NOTIFS_HIGH("IF Unread Notifications >= 3")
}

data class ConditionalAutomationRule(
    val id: String,
    val condition: AutomationConditionType,
    val thenActionSummary: String,
    val enabled: Boolean
)

/**
 * V110 Privacy-Preserving Personal Assistant & Conditional Automation Engine.
 * All learning and suggestions are computed locally on-device and controlled by user toggles.
 */
object PersonalAssistantEngine {

    fun buildDailyPlanPrompt(
        batteryPercent: Int,
        calendarSummary: String,
        reminders: List<LocationReminder>,
        routines: List<VoiceRoutine>,
        unreadNotifsSummary: String,
        languageTag: String
    ): String {
        val remText = if (reminders.isEmpty()) "None" else reminders.take(5).joinToString("; ") { "${it.placeLabel}: ${it.reminderText}" }
        val routineText = if (routines.isEmpty()) "None" else routines.take(5).joinToString(", ") { it.name }
        val langNote = if (languageTag.startsWith("te", true)) "Reply clearly in Telugu." else "Reply concisely in English or Telugu."
        return """
            Create a practical, prioritized Daily Plan for today. $langNote
            Battery: $batteryPercent%
            Calendar:
            $calendarSummary
            Reminders: $remText
            Available Routines: $routineText
            Recent Notifications:
            $unreadNotifsSummary
        """.trimIndent()
    }

    fun proactiveSuggestions(
        enabled: Boolean,
        batteryPercent: Int,
        unreadNotifCount: Int,
        hourOfDay: Int,
        memoriesCount: Int
    ): List<ProactiveSuggestion> {
        if (!enabled) return emptyList()
        val list = mutableListOf<ProactiveSuggestion>()
        if (batteryPercent in 1..20) {
            list.add(
                ProactiveSuggestion(
                    id = "low_battery_opt",
                    title = "Battery Saver Recommended ($batteryPercent%)",
                    detail = "Lower media volume & inspect power-heavy diagnostics.",
                    actionLabel = "Run Diagnostics",
                    promptToRun = "Check system health and battery recommendations"
                )
            )
        }
        if (hourOfDay in 6..11) {
            list.add(
                ProactiveSuggestion(
                    id = "morning_briefing",
                    title = "Morning Daily Plan",
                    detail = "Review today's calendar, reminders & unread notifications.",
                    actionLabel = "Plan Today",
                    promptToRun = "Give me my smart daily plan based on my calendar, reminders, and notifications."
                )
            )
        }
        if (unreadNotifCount >= 3) {
            list.add(
                ProactiveSuggestion(
                    id = "notif_digest",
                    title = "$unreadNotifCount Unread Notifications",
                    detail = "Summarize priority alerts before replying.",
                    actionLabel = "Summarize",
                    promptToRun = "Read my notifications and summarize the urgent ones."
                )
            )
        }
        if (memoriesCount == 0) {
            list.add(
                ProactiveSuggestion(
                    id = "memory_tip",
                    title = "Personal Preference Memory",
                    detail = "Save preferences locally (AES-256 encrypted) for smarter planning.",
                    actionLabel = "Save Tip",
                    promptToRun = "What personal preferences can you remember for me?"
                )
            )
        }
        return list.take(3)
    }

    fun suggestRoutinesFromHistory(
        learningEnabled: Boolean,
        messages: List<ChatMessageEntity>,
        existingRoutines: List<VoiceRoutine>
    ): List<RoutineSuggestion> {
        if (!learningEnabled || messages.isEmpty()) return emptyList()
        val existingTriggers = existingRoutines.map { it.triggerPhrase.lowercase(Locale.ROOT).trim() }.toSet()
        val userPhrases = messages.asSequence()
            .filter { it.isFromUser && !it.isError && it.text.length in 6..60 }
            .map { it.text.trim().lowercase(Locale.ROOT) }
            .groupingBy { it }
            .eachCount()

        val suggestions = mutableListOf<RoutineSuggestion>()
        for ((phrase, count) in userPhrases.entries.sortedByDescending { it.value }) {
            if (count < 2 || phrase in existingTriggers) continue
            if ("briefing" in phrase || "plan" in phrase || "calendar" in phrase) {
                suggestions.add(
                    RoutineSuggestion(
                        suggestedName = "Daily Brief Routine",
                        triggerPhrase = phrase,
                        summary = "Check calendar & notifications",
                        stepsJson = """[{"tool":"read_calendar","args":{"days":1}},{"tool":"read_notifications","args":{"limit":5}}]"""
                    )
                )
            } else if ("battery" in phrase || "volume" in phrase || "flashlight" in phrase) {
                suggestions.add(
                    RoutineSuggestion(
                        suggestedName = "Quick Power Mode",
                        triggerPhrase = phrase,
                        summary = "Check battery & adjust media volume to 35%",
                        stepsJson = """[{"tool":"get_battery_status","args":{}},{"tool":"set_media_volume","args":{"percent":35}}]"""
                    )
                )
            }
            if (suggestions.size >= 2) break
        }
        return suggestions
    }

    fun evaluateConditionalRules(
        rules: List<ConditionalAutomationRule>,
        batteryPercent: Int,
        isCharging: Boolean,
        bluetoothConnected: Boolean,
        unreadNotifCount: Int
    ): List<ConditionalAutomationRule> {
        return rules.filter { rule ->
            if (!rule.enabled) return@filter false
            when (rule.condition) {
                AutomationConditionType.BATTERY_BELOW_15 -> batteryPercent in 1..14 && !isCharging
                AutomationConditionType.CHARGER_CONNECTED -> isCharging
                AutomationConditionType.BLUETOOTH_CONNECTED -> bluetoothConnected
                AutomationConditionType.UNREAD_NOTIFS_HIGH -> unreadNotifCount >= 3
            }
        }
    }
}
