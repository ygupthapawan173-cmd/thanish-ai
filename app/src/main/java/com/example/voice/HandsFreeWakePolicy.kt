package com.example.voice

/** Keyword detector only; no conversation, command routing or cloud audio. */
internal object HandsFreeWakePolicy {
    val phrases = listOf("hey jarvis", "hey jervis", "hey jar vis", "hey jar vice")
    fun accept(text: String, confidence: List<Double>, threshold: Double): Boolean {
        val heard = text.trim().lowercase().replace(Regex("\\s+"), " ")
        return heard in phrases && confidence.size == heard.split(" ").size && confidence.all { AudioCapturePolicy.confidenceAccepted(it, threshold) }
    }
    fun retryDelay(failures: Int): Long = (1000L * failures.coerceIn(1, 10)).coerceAtMost(10000L)
}
