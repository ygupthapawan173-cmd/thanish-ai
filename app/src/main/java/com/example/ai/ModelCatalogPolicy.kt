package com.example.ai

import java.util.Locale

enum class CatalogVerificationState(val label: String) {
    VERIFIED_IN_CATALOG("Verified Live"),
    SUBSTITUTED("Substituted (Closest Official)"),
    UNVERIFIED_KEY_REQUIRED("Key / Discovery Required")
}

data class RequestedBrainModelSpec(
    val displayName: String,
    val officialId: String,
    val provider: String, // "GEMINI" | "OPENAI"
    val fallbackEquivalents: List<String>,
    val specialty: String,
    val freeTierEligible: Boolean
)

data class ResolvedBrainModelStatus(
    val spec: RequestedBrainModelSpec,
    val effectiveModelId: String,
    val state: CatalogVerificationState,
    val note: String
)

data class RequestedVoiceModelSpec(
    val displayName: String,
    val officialId: String,
    val provider: String,
    val endpoint: String,
    val fallbackIds: List<String>,
    val verifiedRole: String
)

/**
 * Official 2026 Model Catalog Policy (Sections 3, 6, 16):
 * - Includes ONLY Google Gemini, OpenAI GPT, and Sarvam AI models.
 * - Excludes removed third-party families (Claude, DeepSeek, Qwen, Grok, Kimi, Mistral, Llama, GPT-OSS).
 * - Never fabricates availability: resolves requested targets strictly against authenticated catalog discovery.
 */
object ModelCatalogPolicy {

    private val EXCLUDED_THIRD_PARTY = listOf(
        "claude", "anthropic", "deepseek", "qwen", "grok", "x-ai", "kimi", "moonshot",
        "mistral", "mixtral", "llama", "meta-llama", "gpt-oss"
    )

    val REQUESTED_BRAIN_MODELS: List<RequestedBrainModelSpec> = listOf(
        RequestedBrainModelSpec(
            displayName = "Gemini 4 Carbon",
            officialId = "gemini-4-carbon",
            provider = "GEMINI",
            fallbackEquivalents = listOf("gemini-4-neon", "gemini-2.5-pro", "gemini-3.8-flash", "gemini-2.5-flash"),
            specialty = "Frontier reasoning, complex STEM, deep analysis",
            freeTierEligible = true
        ),
        RequestedBrainModelSpec(
            displayName = "Gemini 4 Neon",
            officialId = "gemini-4-neon",
            provider = "GEMINI",
            fallbackEquivalents = listOf("gemini-4-argon", "gemini-2.5-pro", "gemini-3.8-flash", "gemini-2.5-flash"),
            specialty = "Complex multi-step problem solving & fast completion",
            freeTierEligible = true
        ),
        RequestedBrainModelSpec(
            displayName = "Gemini 4 Argon",
            officialId = "gemini-4-argon",
            provider = "GEMINI",
            fallbackEquivalents = listOf("gemini-4-hydrogen", "gemini-3.8-flash", "gemini-2.5-flash"),
            specialty = "Efficient reasoning with lower token usage",
            freeTierEligible = true
        ),
        RequestedBrainModelSpec(
            displayName = "Gemini 4 Hydrogen",
            officialId = "gemini-4-hydrogen",
            provider = "GEMINI",
            fallbackEquivalents = listOf("gemini-3.8-flash", "gemini-2.5-flash", "gemini-2.5-flash-lite"),
            specialty = "High-speed everyday answers & mobile commands",
            freeTierEligible = true
        ),
        RequestedBrainModelSpec(
            displayName = "GPT-6.2 Astra",
            officialId = "gpt-6.2-astra",
            provider = "OPENAI",
            fallbackEquivalents = listOf("gpt-6.2-sol", "gpt-5", "gpt-4o"),
            specialty = "Flagship deep reasoning, research & web synthesis",
            freeTierEligible = false
        ),
        RequestedBrainModelSpec(
            displayName = "GPT-6.2 Sol",
            officialId = "gpt-6.2-sol",
            provider = "OPENAI",
            fallbackEquivalents = listOf("gpt-6.1-terra", "gpt-5-mini", "gpt-4o"),
            specialty = "Balanced daily intelligence & structured synthesis",
            freeTierEligible = false
        ),
        RequestedBrainModelSpec(
            displayName = "GPT-6.1 Terra",
            officialId = "gpt-6.1-terra",
            provider = "OPENAI",
            fallbackEquivalents = listOf("gpt-6.2-sol", "gpt-4o", "gpt-4o-mini"),
            specialty = "Fast visual reasoning & concise explanations",
            freeTierEligible = false
        ),
        RequestedBrainModelSpec(
            displayName = "GPT-6 Luna",
            officialId = "gpt-6-luna",
            provider = "OPENAI",
            fallbackEquivalents = listOf("gpt-5-nano", "gpt-4o-mini", "gpt-4.1-mini"),
            specialty = "Ultra-fast lightweight classification & responses",
            freeTierEligible = false
        )
    )

    val REQUESTED_VOICE_MODELS: List<RequestedVoiceModelSpec> = listOf(
        RequestedVoiceModelSpec(
            displayName = "Sarvam Saaras V4",
            officialId = "saaras:v4",
            provider = "SARVAM",
            endpoint = "wss://api.sarvam.ai/speech-to-text-realtime/ws",
            fallbackIds = listOf("saarika:v2.5"),
            verifiedRole = "Streaming TE+EN mixed STT & Online Wake"
        ),
        RequestedVoiceModelSpec(
            displayName = "Gemini 3.8 Live",
            officialId = "gemini-3.8-live",
            provider = "GEMINI",
            endpoint = "BidiGenerateContent",
            fallbackIds = listOf("gemini-2.5-flash-native-audio-preview-12-2025"),
            verifiedRole = "Real-time bidirectional voice conversation"
        ),
        RequestedVoiceModelSpec(
            displayName = "Gemini 3.8 Live Extended Thinking",
            officialId = "gemini-3.8-live-extended-thinking",
            provider = "GEMINI",
            endpoint = "BidiGenerateContent",
            fallbackIds = listOf("gemini-3.8-live", "gemini-2.5-flash-native-audio-preview-12-2025"),
            verifiedRole = "Complex live reasoning conversation"
        ),
        RequestedVoiceModelSpec(
            displayName = "Gemini 3.5 Transcribe Live",
            officialId = "gemini-3.5-transcribe-live",
            provider = "GEMINI",
            endpoint = "BidiGenerateContent",
            fallbackIds = listOf("gemini-3.8-live"),
            verifiedRole = "Streaming live speech-to-text"
        ),
        RequestedVoiceModelSpec(
            displayName = "GPT-Live 1.1",
            officialId = "gpt-live-1.1",
            provider = "OPENAI",
            endpoint = "wss://api.openai.com/v1/realtime",
            fallbackIds = listOf("gpt-realtime-2.1", "gpt-realtime"),
            verifiedRole = "Full-duplex OpenAI voice conversation"
        ),
        RequestedVoiceModelSpec(displayName = "GPT-Realtime 2.1", officialId = "gpt-realtime-2.1", provider = "OPENAI", endpoint = "wss://api.openai.com/v1/realtime", fallbackIds = listOf("gpt-realtime-2.1-mini"), verifiedRole = "Realtime voice conversation"),
        RequestedVoiceModelSpec(displayName = "GPT-Realtime 2.1 Mini", officialId = "gpt-realtime-2.1-mini", provider = "OPENAI", endpoint = "wss://api.openai.com/v1/realtime", fallbackIds = emptyList(), verifiedRole = "Lower-cost realtime conversation"),
        RequestedVoiceModelSpec(displayName = "GPT-Live-Transcribe", officialId = "gpt-live-transcribe", provider = "OPENAI", endpoint = "wss://api.openai.com/v1/realtime?intent=transcription", fallbackIds = emptyList(), verifiedRole = "Streaming speech-to-text")
    )

    fun isSupportedProviderModel(id: String): Boolean {
        val low = id.trim().lowercase(Locale.ROOT)
        if (low.isBlank()) return false
        if (EXCLUDED_THIRD_PARTY.any { it in low }) return false
        return low.startsWith("gemini-") ||
            low.startsWith("gpt-") ||
            low.startsWith("o1") ||
            low.startsWith("o3") ||
            low.startsWith("o4") ||
            low.contains("realtime")
    }

    fun evaluateCatalog(
        discoveredGemini: List<String>,
        discoveredOpenAi: List<String>,
        hasGeminiKey: Boolean,
        hasOpenAiKey: Boolean
    ): List<ResolvedBrainModelStatus> {
        val cleanGem = discoveredGemini.map { it.removePrefix("models/").trim() }.toSet()
        val cleanOai = discoveredOpenAi.map { it.trim() }.toSet()

        return REQUESTED_BRAIN_MODELS.map { spec ->
            if (spec.provider == "GEMINI") {
                resolveSingle(spec, cleanGem, hasGeminiKey, "gemini-2.5-flash")
            } else {
                resolveSingle(spec, cleanOai, hasOpenAiKey, "gpt-4o-mini")
            }
        }
    }

    private fun resolveSingle(
        spec: RequestedBrainModelSpec,
        discovered: Set<String>,
        hasKey: Boolean,
        safeFallback: String
    ): ResolvedBrainModelStatus {
        if (!hasKey) {
            return ResolvedBrainModelStatus(
                spec = spec,
                effectiveModelId = spec.officialId,
                state = CatalogVerificationState.UNVERIFIED_KEY_REQUIRED,
                note = "Configure ${spec.provider} key to verify availability"
            )
        }
        if (discovered.isEmpty()) {
            return ResolvedBrainModelStatus(
                spec = spec,
                effectiveModelId = spec.officialId,
                state = CatalogVerificationState.UNVERIFIED_KEY_REQUIRED,
                note = "Run Discover Models to verify account access"
            )
        }
        if (spec.officialId in discovered) {
            return ResolvedBrainModelStatus(
                spec = spec,
                effectiveModelId = spec.officialId,
                state = CatalogVerificationState.VERIFIED_IN_CATALOG,
                note = "Directly supported in authenticated catalog"
            )
        }
        val substitute = spec.fallbackEquivalents.firstOrNull { it in discovered }
            ?: discovered.firstOrNull { isSupportedProviderModel(it) }
            ?: safeFallback
        return ResolvedBrainModelStatus(
            spec = spec,
            effectiveModelId = substitute,
            state = CatalogVerificationState.SUBSTITUTED,
            note = "${spec.officialId} unavailable on account; substituted $substitute"
        )
    }
}
