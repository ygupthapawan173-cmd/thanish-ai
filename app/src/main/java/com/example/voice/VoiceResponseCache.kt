package com.example.voice

import java.util.LinkedHashMap

/**
 * V108 Bounded In-Memory Voice Response Cache.
 * Caches synthesized WAV audio for repeated phrases (e.g. "Yes, Boss")
 * to eliminate redundant API/quota calls.
 */
object VoiceResponseCache {
    private const val MAX_ENTRIES = 16
    private const val MAX_TOTAL_BYTES = 4 * 1024 * 1024

    private val entries = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {}
    private var totalBytes = 0

    private fun cacheKey(speaker: String, text: String): String =
        "${speaker.trim()}::${text.trim()}"

    @Synchronized
    fun get(speaker: String, text: String): ByteArray? =
        entries[cacheKey(speaker, text)]

    @Synchronized
    fun put(speaker: String, text: String, wavBytes: ByteArray) {
        if (wavBytes.isEmpty() || wavBytes.size > MAX_TOTAL_BYTES / 2) return
        val key = cacheKey(speaker, text)
        entries.remove(key)?.let { totalBytes -= it.size }
        entries[key] = wavBytes
        totalBytes += wavBytes.size
        while (entries.size > MAX_ENTRIES || totalBytes > MAX_TOTAL_BYTES) {
            val eldest = entries.entries.iterator().next()
            totalBytes -= eldest.value.size
            entries.remove(eldest.key)
        }
    }

    @Synchronized
    fun size(): Int = entries.size

    @Synchronized
    fun clear() {
        entries.clear()
        totalBytes = 0
    }
}
