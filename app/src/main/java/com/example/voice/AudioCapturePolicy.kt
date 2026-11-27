package com.example.voice

/** Shared bounded recovery rules for PCM capture and wake confidence. */
internal object AudioCapturePolicy {
    const val MAX_EMPTY_READS = 100
    const val EMPTY_READ_DELAY_MS = 20L
    fun validPcmRead(read: Int, capacity: Int) = read > 0 && read <= capacity && read % 2 == 0
    fun emptyReadLimit(count: Int) = count >= MAX_EMPTY_READS
    fun wakeThreshold(sensitivity: Float): Double {
        val safe = if (sensitivity.isFinite()) sensitivity.coerceIn(0.2f, 1f) else 0.5f
        return (0.85 - 0.35 * safe.toDouble()).coerceIn(0.5, 0.8)
    }
    fun confidenceAccepted(confidence: Double, threshold: Double) =
        confidence.isFinite() && threshold.isFinite() && confidence in 0.0..1.0 && threshold in 0.0..1.0 && confidence >= threshold
    fun ownerWakeAllowed(filterEnabled: Boolean, verified: Boolean) = !filterEnabled || verified
}
