package com.example.voice

/** No network recognizer when local mode or lock screen applies. Never retry a pack twice. */
internal object VoiceSessionPolicy {
    fun localOnly(requested: Boolean, locked: Boolean) = requested || locked
    fun nextPack(language: String, lastLocale: String, attempted: Set<String>, allowAlternate: Boolean): String? {
        val first = VoicePackPolicy.assetFor(if (language == "auto") lastLocale else language)
        val candidates = if (language == "auto" && allowAlternate) listOf(first, if (first == "model-te-in") "model-en-us" else "model-te-in") else listOf(first)
        return candidates.firstOrNull { it !in attempted }
    }
    fun ttsTimeoutMs(characters: Int): Long = (15_000L + characters.coerceAtLeast(0).toLong() * 400L).coerceIn(20_000L, 600_000L)
}
