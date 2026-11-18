package com.example.voice

enum class TtsPersonalityPreset(
    val id: String,
    val pitch: Float,
    val speechRate: Float,
    val shortHudLabel: String
) {
    WITTY_JARVIS(
        id = "witty_jarvis",
        pitch = 1.08f,
        speechRate = 1.06f,
        shortHudLabel = "WITTY"
    ),
    CALM_JARVIS(
        id = "calm_jarvis",
        pitch = 0.94f,
        speechRate = 0.96f,
        shortHudLabel = "COOL"
    ),
    MASS_MACHA(
        id = "mass_macha",
        pitch = 1.15f,
        speechRate = 1.14f,
        shortHudLabel = "JOSH"
    );

    companion object {
        fun fromId(id: String?): TtsPersonalityPreset {
            return entries.firstOrNull { it.id == id } ?: WITTY_JARVIS
        }
    }
}

data class TeluguTtsConfig(
    val preset: TtsPersonalityPreset = TtsPersonalityPreset.WITTY_JARVIS,
    val pitch: Float = TtsPersonalityPreset.WITTY_JARVIS.pitch,
    val speechRate: Float = TtsPersonalityPreset.WITTY_JARVIS.speechRate,
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
