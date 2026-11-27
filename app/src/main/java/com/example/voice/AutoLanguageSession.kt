package com.example.voice

/**
 * Tracks the last successfully recognized spoken language for Auto language mode.
 * - Initial default in Auto mode is Telugu ("te-IN").
 * - Updated only on successful speech recognition or OS language detection callbacks
 *   (never overwritten by temporary retry locale flips).
 */
internal object AutoLanguageSession {
    const val DEFAULT_AUTO_LOCALE = "te-IN"

    @Volatile
    private var lastRecognizedLocale: String = DEFAULT_AUTO_LOCALE

    fun currentLastLocale(): String = lastRecognizedLocale

    fun resolveEffectiveLocale(
        languageTag: String,
        lastRecognized: String = lastRecognizedLocale
    ): String {
        val clean = languageTag.trim()
        if (clean.isBlank() || clean.equals("auto", ignoreCase = true)) {
            return normalizeSupportedLocale(lastRecognized)
        }
        return clean
    }

    fun recordRecognizedText(text: String, explicitLocale: String? = null): String {
        val explicit = explicitLocale?.trim().orEmpty()
        val updated = when {
            explicit.startsWith("te", ignoreCase = true) -> "te-IN"
            explicit.startsWith("en", ignoreCase = true) -> "en-IN"
            text.isNotBlank() -> normalizeSupportedLocale(
                VoiceRecoveryPolicy.localeFromText(text, lastRecognizedLocale)
            )
            else -> lastRecognizedLocale
        }
        lastRecognizedLocale = updated
        return updated
    }

    fun reset(initialLocale: String = DEFAULT_AUTO_LOCALE) {
        lastRecognizedLocale = normalizeSupportedLocale(initialLocale)
    }

    private fun normalizeSupportedLocale(locale: String): String = when {
        locale.startsWith("te", ignoreCase = true) -> "te-IN"
        locale.startsWith("en", ignoreCase = true) -> "en-IN"
        else -> DEFAULT_AUTO_LOCALE
    }
}
