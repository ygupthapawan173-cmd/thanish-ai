package com.example.voice

enum class TtsPersonalityPreset(
    val id: String,
    val pitch: Float,
    val speechRate: Float,
    val shortHudLabel: String
) {
    TELUGU_MALE_JARVIS("telugu_male_jarvis", 0.88f, 1.02f, "JARVIS"),
    TELUGU_FEMALE_SWEET("telugu_female_sweet", 1.18f, 0.96f, "SWEET"),
    TELUGU_FUNNY_COMEDY("telugu_funny_comedy", 1.24f, 1.16f, "FUNNY"),
    TELUGU_NATURAL("telugu_natural", 1.00f, 1.00f, "NORMAL"),

    // Legacy IDs are retained so previously saved settings continue to load.
    WITTY_JARVIS("witty_jarvis", 1.08f, 1.06f, "WITTY"),
    CALM_JARVIS("calm_jarvis", 0.94f, 0.96f, "COOL"),
    MASS_MACHA("mass_macha", 1.15f, 1.14f, "JOSH");

    companion object {
        fun fromId(id: String?): TtsPersonalityPreset {
            return entries.firstOrNull { it.id == id } ?: TELUGU_MALE_JARVIS
        }
    }
}

data class TeluguTtsConfig(
    val preset: TtsPersonalityPreset = TtsPersonalityPreset.TELUGU_MALE_JARVIS,
    val pitch: Float = TtsPersonalityPreset.TELUGU_MALE_JARVIS.pitch,
    val speechRate: Float = TtsPersonalityPreset.TELUGU_MALE_JARVIS.speechRate,
    val selectedVoiceName: String? = null,
    val autoSpeakEnabled: Boolean = true
)

data class TeluguVoiceOption(
    val name: String,
    val displayLabel: String,
    val isNetworkRequired: Boolean,
    val qualityScore: Int
)

/**
 * Formats Pandu Babu's Telugu responses for expressive, natural-sounding TTS playback
 * without robotic punctuation or spoken emoji descriptions.
 */
object TeluguSpeechFormatter {

    private val markdownPattern = Regex("[*_`~#>]")
    private val multiSpacePattern = Regex("\\s+")
    private val ellipsisPattern = Regex("\\.{2,}")
    private val dashPausePattern = Regex("\\s*[—–]+\\s*")
    private val emojiOrSymbolPattern = Regex("[\\p{So}\\p{Cn}]")

    fun formatForTeluguSpeech(raw: String): String {
        if (raw.isBlank()) return ""

        return raw
            // Remove Markdown tokens without disturbing Telugu combining marks (Mn/Mc)
            .replace(markdownPattern, "")
            // Strip emojis/pictographs so TTS doesn't read English emoji names mid-Telugu
            .replace(emojiOrSymbolPattern, " ")
            // Turn em-dashes and ellipses into natural comedic breathing pauses
            .replace(dashPausePattern, ", ")
            .replace(ellipsisPattern, ", ")
            // Collapse whitespace
            .replace(multiSpacePattern, " ")
            .trim()
    }
}
