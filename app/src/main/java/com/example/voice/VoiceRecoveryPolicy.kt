package com.example.voice

internal object VoiceRecoveryPolicy {
    fun retryable(code: Int, automatic: Boolean, attempts: Int): Boolean = attempts in 0..1 &&
        (code in setOf(1,2,3,5,8,11,12,13) || (automatic && code in setOf(6,7)))
    fun localeFromText(text: String, previous: String): String {
        val te = text.count { it in '\u0C00'..'\u0C7F' }
        val en = text.count { it in 'a'..'z' || it in 'A'..'Z' }
        return when { te > en -> "te-IN"; en > te -> "en-IN"; else -> previous }
    }
    fun nextLocale(locale: String): String = if (locale.startsWith("en", ignoreCase = true)) "te-IN" else "en-IN"
}
