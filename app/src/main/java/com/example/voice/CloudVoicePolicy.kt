package com.example.voice

/** Documented AICredits /audio/speech speakers. Presets are not voice clones. */
internal object CloudVoicePolicy {
    const val BASE = "https://api.aicredits.in/v1"
    const val MAX_TEXT = 1000 // one bounded paid request per utterance
    const val MAX_AUDIO = 8 * 1024 * 1024
    val english = listOf("onyx", "alloy", "echo", "fable", "nova", "shimmer")
    val telugu = listOf("rahul", "priya", "karun", "anushka")
    data class Choice(val model: String, val voice: String)
    fun select(text: String, en: String, te: String, hd: Boolean): Choice =
        if (text.any { it in '\u0C00'..'\u0C7F' }) Choice("sarvam/bulbul-v2", te.takeIf { it in telugu } ?: "anushka")
        else Choice(if (hd) "openai/tts-1-hd" else "openai/tts-1", en.takeIf { it in english } ?: "onyx")
    fun allowed(base: String, enabled: Boolean, gateway: Boolean, free: Boolean, budget: Double, privateMode: Boolean, foreground: Boolean, keyPresent: Boolean, length: Int): Boolean =
        base.trim().trimEnd('/') == BASE && enabled && gateway && !free && budget == 0.0 && !privateMode && foreground && keyPresent && length in 1..MAX_TEXT
    fun audioType(type: String?): Boolean = type?.substringBefore(';')?.trim()?.lowercase()?.let { it.startsWith("audio/") || it == "application/octet-stream" } == true
}
