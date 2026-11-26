package com.example.omega
import java.text.Normalizer
import java.util.Locale

object OmegaPolicy {
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ").trim()
    fun wakeTail(text: String): String? {
        val normalized = normalize(text)
        for (phrase in listOf("hey jarvis", "hey jervis", "hey jar vis", "hey jar vice")) {
            if (normalized == phrase) return ""
            if (normalized.startsWith(phrase + " ")) return normalized.removePrefix(phrase + " ").take(300)
        }
        return null
    }
    fun matches(text: String, query: String): Boolean {
        val words = normalize(text).split(" ").filter { it.isNotEmpty() }
        return normalize(query).split(" ").filter { it.isNotEmpty() }.all { q -> words.any { it.startsWith(q) } }
    }
    fun retryMillis(status: Int, header: String?): Long = when {
        status == 429 -> (header?.trim()?.takeIf { it.length <= 8 && it.all(Char::isDigit) }
            ?.toLongOrNull() ?: 30L).coerceIn(1, 300) * 1000
        status in 500..599 -> 10000L
        else -> 0L
    }
    fun modelId(value: String): Boolean = value.length in 1..160 &&
        value.matches(Regex("[A-Za-z0-9._:/+@-]+")) && !value.contains("..") && !value.startsWith("/")
}

