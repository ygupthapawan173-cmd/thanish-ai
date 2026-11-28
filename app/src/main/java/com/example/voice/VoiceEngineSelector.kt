package com.example.voice

enum class StreamingVoiceEngineKind(val label: String) {
    SARVAM_SAARAS_V4("Sarvam Saaras V4"),
    GEMINI_TRANSCRIBE_LIVE("Gemini 3.5 Transcribe Live"),
    VOSK_OFFLINE_FALLBACK("Vosk + Silero Offline")
}

data class VoiceEngineBenchmark(
    val sarvamLatencyMs: Long = 0L,
    val geminiLatencyMs: Long = 0L,
    val selectedEngine: StreamingVoiceEngineKind = StreamingVoiceEngineKind.VOSK_OFFLINE_FALLBACK,
    val reason: String = "Evaluating available voice engines"
)

/**
 * Selects the fastest reliable streaming engine honestly based on measured connect/response times
 * and real credential + network availability (Sections 6 & 7).
 */
object VoiceEngineSelector {

    @Volatile
    private var lastSarvamLatencyMs: Long = 0L

    @Volatile
    private var lastGeminiLatencyMs: Long = 0L

    fun recordLatency(engine: StreamingVoiceEngineKind, ms: Long) {
        val clamped = ms.coerceAtLeast(1L)
        when (engine) {
            StreamingVoiceEngineKind.SARVAM_SAARAS_V4 -> lastSarvamLatencyMs = clamped
            StreamingVoiceEngineKind.GEMINI_TRANSCRIBE_LIVE -> lastGeminiLatencyMs = clamped
            StreamingVoiceEngineKind.VOSK_OFFLINE_FALLBACK -> Unit
        }
    }

    fun selectBest(
        hasInternet: Boolean,
        isLocked: Boolean,
        cloudConsentGranted: Boolean,
        hasSarvamKey: Boolean,
        hasGeminiKey: Boolean
    ): VoiceEngineBenchmark {
        if (isLocked || !hasInternet || !cloudConsentGranted) {
            val why = when {
                isLocked -> "Screen locked — local offline wake only"
                !hasInternet -> "Offline — Vosk + Silero VAD fallback"
                else -> "Cloud wake consent OFF — offline wake active"
            }
            return VoiceEngineBenchmark(
                sarvamLatencyMs = lastSarvamLatencyMs,
                geminiLatencyMs = lastGeminiLatencyMs,
                selectedEngine = StreamingVoiceEngineKind.VOSK_OFFLINE_FALLBACK,
                reason = why
            )
        }

        if (hasSarvamKey && hasGeminiKey && lastSarvamLatencyMs > 0L && lastGeminiLatencyMs > 0L) {
            val chosen = if (lastSarvamLatencyMs <= lastGeminiLatencyMs) {
                StreamingVoiceEngineKind.SARVAM_SAARAS_V4
            } else {
                StreamingVoiceEngineKind.GEMINI_TRANSCRIBE_LIVE
            }
            return VoiceEngineBenchmark(
                sarvamLatencyMs = lastSarvamLatencyMs,
                geminiLatencyMs = lastGeminiLatencyMs,
                selectedEngine = chosen,
                reason = "Fastest measured online engine (${lastSarvamLatencyMs}ms vs ${lastGeminiLatencyMs}ms)"
            )
        }

        return when {
            hasSarvamKey -> VoiceEngineBenchmark(
                sarvamLatencyMs = lastSarvamLatencyMs,
                geminiLatencyMs = lastGeminiLatencyMs,
                selectedEngine = StreamingVoiceEngineKind.SARVAM_SAARAS_V4,
                reason = "Sarvam Saaras V4 selected (TE/EN low-latency WS)"
            )
            hasGeminiKey -> VoiceEngineBenchmark(
                sarvamLatencyMs = lastSarvamLatencyMs,
                geminiLatencyMs = lastGeminiLatencyMs,
                selectedEngine = StreamingVoiceEngineKind.GEMINI_TRANSCRIBE_LIVE,
                reason = "Gemini Live streaming selected"
            )
            else -> VoiceEngineBenchmark(
                selectedEngine = StreamingVoiceEngineKind.VOSK_OFFLINE_FALLBACK,
                reason = "No cloud STT key; Vosk offline fallback active"
            )
        }
    }
}
