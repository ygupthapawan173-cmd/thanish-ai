package com.example.ai

internal object LiveRetryPolicy {
    fun mayRetry(message: String, attempt: Int, selected: String, fallback: String): Boolean {
        if (attempt >= 2 || listOf("HTTP 401", "HTTP 403", "(1008)").any { it in message }) return false
        if ("HTTP 400" in message || "HTTP 404" in message) return attempt == 0 && selected.isNotBlank() && selected.removePrefix("models/") != fallback.removePrefix("models/")
        return true
    }
    fun delayMs(message: String, attempt: Int): Long = if ("HTTP 429" in message) 15_000L * attempt.coerceIn(1,2) else if (attempt == 1) 1000L else 2500L
}
