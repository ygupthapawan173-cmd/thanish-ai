package com.example.voice

/**
 * Provides complete Telugu and English wake acknowledgements and resolves effective reply locale.
 * In Auto mode, defaults to Telugu ("te-IN") and tracks the last successfully recognized language.
 */
internal object TeluguVoiceReplies {

    fun effectiveLocale(
        languageTag: String,
        lastRecognizedLocale: String = AutoLanguageSession.currentLastLocale(),
        utterance: String = ""
    ): String {
        if (utterance.any { it in '\u0C00'..'\u0C7F' }) return "te-IN"
        return AutoLanguageSession.resolveEffectiveLocale(languageTag, lastRecognizedLocale)
    }

    fun isTelugu(
        languageTag: String,
        utterance: String = "",
        lastRecognizedLocale: String = AutoLanguageSession.currentLastLocale()
    ): Boolean = effectiveLocale(languageTag, lastRecognizedLocale, utterance)
        .startsWith("te", ignoreCase = true)

    fun wakeAck(
        languageTag: String,
        lastRecognizedLocale: String = AutoLanguageSession.currentLastLocale()
    ): String = if (isTelugu(languageTag, lastRecognizedLocale = lastRecognizedLocale)) {
        "చెప్పండి బాస్, నేను వింటున్నాను."
    } else {
        "Yes Boss, I am listening."
    }

    fun wakeAckLocale(
        languageTag: String,
        lastRecognizedLocale: String = AutoLanguageSession.currentLastLocale()
    ): String = if (isTelugu(languageTag, lastRecognizedLocale = lastRecognizedLocale)) {
        "te-IN"
    } else {
        "en-IN"
    }

    fun lockedReminder(
        languageTag: String,
        lastRecognizedLocale: String = AutoLanguageSession.currentLastLocale()
    ): String = if (isTelugu(languageTag, lastRecognizedLocale = lastRecognizedLocale)) {
        "అవును బాస్. కొనసాగించడానికి స్క్రీన్ అన్‌లాక్ చేయండి."
    } else {
        "Yes Boss. Unlock screen to continue."
    }
}
