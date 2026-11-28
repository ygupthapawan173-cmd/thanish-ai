package com.example.voice

/** No network recognizer when local mode or lock screen applies. */
internal object VoiceSessionPolicy {
    fun localOnly(requested: Boolean, locked: Boolean) = requested || locked
    fun ttsTimeoutMs(characters: Int): Long = (15_000L + characters.coerceAtLeast(0).toLong() * 400L).coerceIn(20_000L, 600_000L)
}
