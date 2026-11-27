package com.example.voice

/** Keyword detector only; no conversation, command routing or cloud audio. */
internal object HandsFreeWakePolicy {
    // Improved Telugu-accented "Hey JARVIS" coverage; strictly excludes standalone "jarvis" and "service".
    val phrases = listOf(
        "hey jarvis",
        "hey jervis",
        "hey jar vis",
        "hey jar vice",
        "hey jarvish",
        "hey jarves",
        "hey javis",
        "hey charvis",
        "hi jarvis",
        "hello jarvis",
        "ok jarvis",
        "okay jarvis"
    )

    fun accept(text: String, confidence: List<Double>, threshold: Double): Boolean {
        val heard = text.trim().lowercase().replace(Regex("\\s+"), " ")
        return heard in phrases &&
            confidence.size == heard.split(" ").size &&
            confidence.all { AudioCapturePolicy.confidenceAccepted(it, threshold) }
    }

    /**
     * Strict owner voice verification guard:
     * NEVER bypasses owner verification for partial results or short speaker samples (< minFrames).
     */
    fun acceptWithOwnerGuard(
        text: String,
        confidence: List<Double>,
        threshold: Double,
        isPartial: Boolean,
        ownerVerificationEnabled: Boolean,
        spkFrames: Int,
        minFrames: Int = SpeakerProfilePolicy.minFrames,
        ownerVerified: Boolean
    ): Boolean {
        if (!accept(text, confidence, threshold)) return false
        if (!ownerVerificationEnabled) return true
        if (isPartial) return false
        if (spkFrames < minFrames) return false
        return ownerVerified
    }

    // Stale UI thinking state must not strand the background detector; real mic and TTS owners still block it.
    fun foregroundBusy(inForeground: Boolean, micBusy: Boolean, ttsBusy: Boolean, thinking: Boolean): Boolean =
        micBusy || ttsBusy || (inForeground && thinking)

    fun retryDelay(failures: Int): Long = (1000L * failures.coerceIn(1, 10)).coerceAtMost(10000L)
}
