package com.example.voice

import java.util.Locale

/**
 * Fixed "Hey JARVIS" wake detector policy (Sections 6, 7, 16):
 * - No Custom Wake Word, no Owner Voice filtering.
 * - Supports bilingual English & Telugu phonetic wake matches and partial-transcript wake detection.
 */
internal object HandsFreeWakePolicy {
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

    private val WAKE_PREFIX_REGEX = Regex(
        "^(?:hey|hi|ok|okay|hello)?\\s*(?:jarvis|jervis|jar\\s*vis|jar\\s*vice|jarvish|jarves|జార్విస్)[,:\\s]*",
        RegexOption.IGNORE_CASE
    )

    fun accept(text: String, confidence: List<Double>, threshold: Double): Boolean {
        val heard = text.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        return heard in phrases &&
            confidence.size == heard.split(" ").size &&
            confidence.all { AudioCapturePolicy.confidenceAccepted(it, threshold) }
    }

    fun matchesPartialWake(transcript: String): Boolean {
        val clean = transcript.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return false
        return phrases.any { clean.startsWith(it) || " $it" in " $clean" } ||
            "హే జార్విస్" in clean ||
            clean.startsWith("jarvis ")
    }

    fun stripWakePrefix(transcript: String): String {
        val stripped = WAKE_PREFIX_REGEX.replace(transcript.trim(), "").trim()
        return if (stripped == transcript.trim()) "" else stripped
    }

    fun foregroundBusy(inForeground: Boolean, micBusy: Boolean, ttsBusy: Boolean, thinking: Boolean): Boolean =
        micBusy || ttsBusy || (inForeground && thinking)

    fun retryDelay(failures: Int): Long = (1000L * failures.coerceIn(1, 10)).coerceAtMost(10000L)
}
