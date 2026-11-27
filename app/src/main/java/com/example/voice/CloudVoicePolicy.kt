package com.example.voice

/** Documented OpenAI-compatible /audio/speech speakers (AICredits, OpenAI, OneAI). Presets are not voice clones. */
internal object CloudVoicePolicy {
    const val BASE = "https://api.aicredits.in/v1"
    val SUPPORTED_TTS_BASES = setOf(
        "https://api.aicredits.in/v1",
        "https://api.openai.com/v1",
        "https://api.oneaiapi.com/v1"
    )
    const val MAX_TEXT = 1000 // one bounded request per utterance
    const val MAX_AUDIO = 8 * 1024 * 1024
    val english = listOf("onyx", "alloy", "echo", "fable", "nova", "shimmer")
    val telugu = listOf("anushka", "priya", "rahul", "karun")
    data class Choice(val model: String, val voice: String)
    fun select(text: String, en: String, te: String, hd: Boolean): Choice =
        if (text.any { it in '\u0C00'..'\u0C7F' }) Choice("sarvam/bulbul-v2", te.takeIf { it in telugu } ?: "anushka")
        else Choice(if (hd) "openai/tts-1-hd" else "openai/tts-1", en.takeIf { it in english } ?: "onyx")
    fun selectForBase(baseUrl: String, text: String, en: String, te: String, hd: Boolean): Choice {
        val base = select(text, en, te, hd)
        val cleanBase = baseUrl.trim().trimEnd('/').lowercase(java.util.Locale.ROOT)
        return if (cleanBase == BASE.lowercase(java.util.Locale.ROOT)) base
        else {
            // Official OpenAI / OneAI /audio/speech expects unprefixed model IDs and OpenAI voices (onyx preferred)
            val model = if (hd) "tts-1-hd" else "tts-1"
            val voice = en.takeIf { it in english } ?: "onyx"
            Choice(model, voice)
        }
    }
    fun allowed(base: String, enabled: Boolean, gateway: Boolean, free: Boolean, budget: Double, privateMode: Boolean, foreground: Boolean, keyPresent: Boolean, length: Int): Boolean {
        val norm = base.trim().trimEnd('/')
        val validBase = norm in SUPPORTED_TTS_BASES
        return validBase && enabled && gateway && !free && budget == 0.0 && !privateMode && foreground && keyPresent && length in 1..MAX_TEXT
    }
    fun audioType(type: String?): Boolean = type?.substringBefore(';')?.trim()?.lowercase()?.let { it.startsWith("audio/") || it == "application/octet-stream" } == true
}
