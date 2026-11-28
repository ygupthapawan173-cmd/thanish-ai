package com.example.autonomous

import java.util.Locale

enum class ReleaseVoiceAction {
    CHECK_FOR_UPDATES,
    UPGRADE_SELF
}

/**
 * Recognizes explicit voice commands to check or apply verified release updates (Section 15):
 * - "Hey JARVIS, upgrade yourself."
 * - "Hey JARVIS, check for updates."
 * - "JARVIS, కొత్త version ఉందా?"
 */
object VoiceUpgradeIntentParser {

    private val TELUGU_UPDATE_QUERY = Regex(
        "(కొత్త\\s*version|కొత్త\\s*వెర్షన్|అప్.?డేట్\\s*ఉందా|కొత్త\\s*అప్.?డేట్|కొత్త\\s*వెర్షన్\\s*ఉందా)",
        RegexOption.IGNORE_CASE
    )
    private val CHECK_UPDATES = Regex(
        "^(?:hey\\s+)?(?:jarvis(?:\\s+infinity)?[,:\\s]+)?(?:please\\s+)?(?:check\\s+(?:for\\s+)?updates?|is\\s+there\\s+(?:a\\s+)?new\\s+version|kotha\\s+version\\s+undha|new\\s+version\\s+available)\\b.*$",
        RegexOption.IGNORE_CASE
    )
    private val UPGRADE_SELF = Regex(
        "^(?:hey\\s+)?(?:jarvis(?:\\s+infinity)?[,:\\s]+)?(?:please\\s+)?(?:upgrade\\s+yourself|self[-\\s]?upgrade|update\\s+yourself|install\\s+(?:the\\s+)?(?:latest\\s+)?update)\\b.*$",
        RegexOption.IGNORE_CASE
    )

    fun parse(utterance: String): ReleaseVoiceAction? {
        val clean = utterance.trim().replace(Regex("\\s+"), " ")
        if (clean.isBlank() || clean.length > 300) return null
        val lower = clean.lowercase(Locale.ROOT)
        return when {
            UPGRADE_SELF.matches(clean) -> ReleaseVoiceAction.UPGRADE_SELF
            CHECK_UPDATES.matches(clean) || TELUGU_UPDATE_QUERY.containsMatchIn(lower) ->
                ReleaseVoiceAction.CHECK_FOR_UPDATES
            else -> null
        }
    }
}
