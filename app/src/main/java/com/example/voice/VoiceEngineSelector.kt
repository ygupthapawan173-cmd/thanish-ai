package com.example.voice

enum class StreamingVoiceEngineKind(val label: String) {
    SARVAM_SAARAS_V4("Sarvam Saaras V4"),
    GEMINI_TRANSCRIBE_LIVE("Gemini 3.5 Transcribe Live"),
    OPENAI_LIVE_TRANSCRIBE("GPT-Live-Transcribe"),
    LOCAL_KEYWORD_WAKE("Local keyword wake")
}
data class VoiceEngineBenchmark(
    val sarvamLatencyMs: Long = 0L,
    val geminiLatencyMs: Long = 0L,
    val selectedEngine: StreamingVoiceEngineKind = StreamingVoiceEngineKind.LOCAL_KEYWORD_WAKE,
    val reason: String = "Evaluating available voice engines",
    val openAiLatencyMs: Long = 0L
)
object VoiceEngineSelector {
    private val samples = mutableMapOf<StreamingVoiceEngineKind, Long>()
    @Synchronized fun recordLatency(engine: StreamingVoiceEngineKind, ms: Long) {
        if (engine == StreamingVoiceEngineKind.LOCAL_KEYWORD_WAKE || ms <= 0) return
        val prior = samples[engine]
        samples[engine] = if (prior == null) ms else (prior * 3 + ms) / 4
    }
    @Synchronized fun selectBest(
        hasInternet: Boolean, isLocked: Boolean, cloudConsentGranted: Boolean,
        hasSarvamKey: Boolean, hasGeminiKey: Boolean, hasOpenAiKey: Boolean = false
    ): VoiceEngineBenchmark {
        val sarvam = samples[StreamingVoiceEngineKind.SARVAM_SAARAS_V4] ?: 0L
        val gemini = samples[StreamingVoiceEngineKind.GEMINI_TRANSCRIBE_LIVE] ?: 0L
        val openai = samples[StreamingVoiceEngineKind.OPENAI_LIVE_TRANSCRIBE] ?: 0L
        val available = listOfNotNull(
            StreamingVoiceEngineKind.SARVAM_SAARAS_V4.takeIf { hasSarvamKey },
            StreamingVoiceEngineKind.GEMINI_TRANSCRIBE_LIVE.takeIf { hasGeminiKey },
            StreamingVoiceEngineKind.OPENAI_LIVE_TRANSCRIBE.takeIf { hasOpenAiKey })
        if (isLocked || !hasInternet || !cloudConsentGranted || available.isEmpty()) return VoiceEngineBenchmark(
            sarvam, gemini, StreamingVoiceEngineKind.LOCAL_KEYWORD_WAKE, "Local wake; cloud unavailable or not permitted", openai)
        val selected = if (available.all { samples.containsKey(it) }) available.minByOrNull { samples[it] ?: Long.MAX_VALUE }!! else available.first()
        return VoiceEngineBenchmark(sarvam, gemini, selected,
            if (available.all { samples.containsKey(it) }) "Lowest measured connection latency" else "Configured engine priority; not all latencies measured", openai)
    }
}
