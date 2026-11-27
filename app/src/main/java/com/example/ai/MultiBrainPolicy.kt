package com.example.ai

import com.example.data.AiProvider
import com.example.data.OmegaSettings
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * V110 Advanced Multi-Brain Intelligence Registry & Routing Policy.
 * Supports 10 specialized AI Brains backed ONLY by actually discovered/configured
 * official Gemini Direct models or authorized OpenAI-compatible gateway models.
 * Never fabricates connections or assumes access to unconfigured models.
 */
enum class AiBrainFamily(
    val id: String,
    val displayName: String,
    val specialty: String,
    val providerHint: String
) {
    GEMINI_DIRECT("gemini_direct", "Gemini Direct Brain", "Multimodal, Voice & Free Tier", "Google Gemini API"),
    OPENAI_GPT("openai_gpt", "OpenAI GPT Brain", "General Intelligence & Vision", "OpenAI / Gateway"),
    CLAUDE_CODING("claude_coding", "Claude Coding Brain", "Software Engineering & Architecture", "Anthropic / Gateway"),
    DEEPSEEK_REASONING("deepseek_reasoning", "DeepSeek Reasoning Brain", "Deep Math, Logic & Coding", "DeepSeek / Gateway"),
    QWEN_INTELLIGENCE("qwen_intelligence", "Qwen Intelligence Brain", "Multilingual & Technical Tasks", "Qwen / Gateway"),
    GROK_AI("grok_ai", "Grok AI Brain", "Fast Synthesis & Analysis", "xAI / Gateway"),
    KIMI_LONG_CONTEXT("kimi_long_context", "Kimi Long-Context Brain", "Documents, PDFs & Long Context", "Moonshot / Gateway"),
    MISTRAL_FAST("mistral_fast", "Mistral Fast Brain", "Low-Latency Chat & Tasks", "Mistral / Gateway"),
    LLAMA_AI("llama_ai", "Llama AI Brain", "Open Weights & Fast Free Tier", "Meta Llama / Gateway"),
    GPT_OSS_REASONING("gpt_oss_reasoning", "GPT-OSS Reasoning Brain", "Open Reasoning & Verification", "GPT-OSS / Gateway");

    companion object {
        fun fromId(id: String?): AiBrainFamily? =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) }
    }
}

enum class BrainThinkingMode(val id: String, val label: String) {
    FAST("FAST", "Fast"),
    AUTO("AUTO", "Auto"),
    REASONING("REASONING", "Reasoning"),
    DEEP_THINKING("DEEP_THINKING", "Deep Thinking");

    companion object {
        fun fromStored(raw: String?): BrainThinkingMode =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: AUTO
    }
}

enum class BrainAvailability(val label: String) {
    READY("Ready"),
    FREE_READY("Free Tier Ready"),
    COOLING_DOWN("Cooling Down"),
    KEY_REQUIRED("Key Required"),
    NOT_DISCOVERED("Not Discovered"),
    BLOCKED_ZERO_CREDITS("Zero Paid Credits ON")
}

data class BrainQuotaStats(
    val requests: Int = 0,
    val failures: Int = 0,
    val promptTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val knownCostUsd: Double = 0.0,
    val lastUsedModel: String = "",
    val cooldownUntilMs: Long = 0L
)

data class BrainStatusSnapshot(
    val brain: AiBrainFamily,
    val availability: BrainAvailability,
    val bestModelId: String?,
    val discoveredCount: Int,
    val freeModelCount: Int,
    val stats: BrainQuotaStats,
    val cooldownRemainingSec: Long,
    val detail: String
)

object PerBrainQuotaTracker {
    private val statsMap = ConcurrentHashMap<AiBrainFamily, BrainQuotaStats>()

    fun recordSuccess(
        brain: AiBrainFamily,
        modelId: String,
        promptTokens: Long? = null,
        outputTokens: Long? = null,
        costUsd: Double? = null
    ) {
        statsMap.compute(brain) { _, cur ->
            val prev = cur ?: BrainQuotaStats()
            prev.copy(
                requests = prev.requests + 1,
                promptTokens = prev.promptTokens + (promptTokens?.coerceAtLeast(0L) ?: 0L),
                outputTokens = prev.outputTokens + (outputTokens?.coerceAtLeast(0L) ?: 0L),
                knownCostUsd = prev.knownCostUsd + (costUsd?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0),
                lastUsedModel = modelId
            )
        }
    }

    fun recordFailure(brain: AiBrainFamily, cooldownUntilMs: Long = 0L) {
        statsMap.compute(brain) { _, cur ->
            val prev = cur ?: BrainQuotaStats()
            prev.copy(
                requests = prev.requests + 1,
                failures = prev.failures + 1,
                cooldownUntilMs = maxOf(prev.cooldownUntilMs, cooldownUntilMs)
            )
        }
    }

    fun snapshot(brain: AiBrainFamily): BrainQuotaStats = statsMap[brain] ?: BrainQuotaStats()

    fun resetForTest() {
        statsMap.clear()
    }
}

object MultiBrainPolicy {

    fun classifyModelFamily(modelId: String, isDirectGeminiProvider: Boolean = false): AiBrainFamily? {
        val m = modelId.trim().lowercase(Locale.ROOT)
        if (m.isBlank()) return null
        return when {
            "gpt-oss" in m -> AiBrainFamily.GPT_OSS_REASONING
            isDirectGeminiProvider && m.removePrefix("models/").startsWith("gemini-") -> AiBrainFamily.GEMINI_DIRECT
            "google/gemini" in m || m.startsWith("gemini-") -> AiBrainFamily.GEMINI_DIRECT
            "anthropic/" in m || "claude-" in m -> AiBrainFamily.CLAUDE_CODING
            "deepseek/" in m || "deepseek-" in m -> AiBrainFamily.DEEPSEEK_REASONING
            "qwen/" in m || m.startsWith("qwen") -> AiBrainFamily.QWEN_INTELLIGENCE
            "x-ai/" in m || "grok" in m -> AiBrainFamily.GROK_AI
            "moonshotai/" in m || "kimi" in m -> AiBrainFamily.KIMI_LONG_CONTEXT
            "mistralai/" in m || "mistral" in m || "codestral" in m -> AiBrainFamily.MISTRAL_FAST
            "meta-llama/" in m || "llama-" in m || "llama3" in m || "llama4" in m -> AiBrainFamily.LLAMA_AI
            "openai/" in m || "gpt-4" in m || "gpt-5" in m || m.startsWith("o3") || m.startsWith("o4") || "/o3" in m || "/o4" in m -> AiBrainFamily.OPENAI_GPT
            else -> null
        }
    }

    fun buildBrainInventory(
        settings: OmegaSettings,
        discoveredGeminiModels: List<String>,
        discoveredGatewayModels: List<SmartModelPolicy.Model>,
        nowMs: Long = System.currentTimeMillis()
    ): List<BrainStatusSnapshot> {
        val hasGemini = settings.effectiveGeminiKey().isNotBlank()
        val hasGateway = settings.effectiveOpenRouterKey().isNotBlank() || settings.effectiveSecondaryGatewayKey().isNotBlank()
        val isFreeGateway = settings.smartMode == SmartModelPolicy.Mode.FREE
        val paidGatewayAllowed = ZeroPaidCreditsPolicy.allowsPaidGateway(settings.zeroPaidCreditsMode, isFreeGateway)

        return AiBrainFamily.entries.map { family ->
            val stats = PerBrainQuotaTracker.snapshot(family)
            val cooldownLeft = ((stats.cooldownUntilMs - nowMs).coerceAtLeast(0L) + 999L) / 1000L

            if (family == AiBrainFamily.GEMINI_DIRECT) {
                val validGemini = discoveredGeminiModels.filter { GeminiCompatibility.isChat(it) }
                val best = validGemini.firstOrNull() ?: settings.geminiChatModel.takeIf { hasGemini && GeminiCompatibility.isChat(it) }
                val avail = when {
                    !hasGemini && !hasGateway -> BrainAvailability.KEY_REQUIRED
                    cooldownLeft > 0 -> BrainAvailability.COOLING_DOWN
                    hasGemini -> BrainAvailability.FREE_READY
                    else -> buildGatewayAvailability(
                        family = family,
                        gatewayModels = discoveredGatewayModels,
                        hasGateway = hasGateway,
                        isFreeGateway = isFreeGateway,
                        paidGatewayAllowed = paidGatewayAllowed,
                        cooldownLeft = cooldownLeft
                    ).first
                }
                BrainStatusSnapshot(
                    brain = family,
                    availability = avail,
                    bestModelId = best,
                    discoveredCount = validGemini.size,
                    freeModelCount = if (hasGemini) validGemini.size else 0,
                    stats = stats,
                    cooldownRemainingSec = cooldownLeft,
                    detail = when (avail) {
                        BrainAvailability.FREE_READY -> "${validGemini.size} discovered Gemini chat model(s)"
                        BrainAvailability.COOLING_DOWN -> "Cooling down (${cooldownLeft}s remaining)"
                        BrainAvailability.KEY_REQUIRED -> "Add Gemini API key in Slot 2"
                        else -> "Gateway fallback status"
                    }
                )
            } else {
                val (avail, matching, freeOnes) = buildGatewayAvailability(
                    family = family,
                    gatewayModels = discoveredGatewayModels,
                    hasGateway = hasGateway,
                    isFreeGateway = isFreeGateway,
                    paidGatewayAllowed = paidGatewayAllowed,
                    cooldownLeft = cooldownLeft
                )
                val best = if (isFreeGateway) freeOnes.firstOrNull()?.id else matching.firstOrNull()?.id
                BrainStatusSnapshot(
                    brain = family,
                    availability = avail,
                    bestModelId = best,
                    discoveredCount = matching.size,
                    freeModelCount = freeOnes.size,
                    stats = stats,
                    cooldownRemainingSec = cooldownLeft,
                    detail = when (avail) {
                        BrainAvailability.FREE_READY -> "${freeOnes.size} verified free / ${matching.size} total discovered"
                        BrainAvailability.READY -> "${matching.size} discovered model(s)"
                        BrainAvailability.COOLING_DOWN -> "Cooling down (${cooldownLeft}s remaining)"
                        BrainAvailability.KEY_REQUIRED -> "Add Smart Gateway or Slot 3 key"
                        BrainAvailability.BLOCKED_ZERO_CREDITS -> "Zero Paid Credits ON (${matching.size} paid models gated)"
                        BrainAvailability.NOT_DISCOVERED -> "Run Discover Models to check provider catalog"
                    }
                )
            }
        }
    }

    private fun buildGatewayAvailability(
        family: AiBrainFamily,
        gatewayModels: List<SmartModelPolicy.Model>,
        hasGateway: Boolean,
        isFreeGateway: Boolean,
        paidGatewayAllowed: Boolean,
        cooldownLeft: Long
    ): Triple<BrainAvailability, List<SmartModelPolicy.Model>, List<SmartModelPolicy.Model>> {
        val matching = gatewayModels.filter { classifyModelFamily(it.id, isDirectGeminiProvider = false) == family }
        val freeOnes = matching.filter { SmartModelPolicy.isVerifiedFree(it) }
        val avail = when {
            !hasGateway -> BrainAvailability.KEY_REQUIRED
            cooldownLeft > 0 -> BrainAvailability.COOLING_DOWN
            matching.isEmpty() -> BrainAvailability.NOT_DISCOVERED
            freeOnes.isNotEmpty() -> BrainAvailability.FREE_READY
            isFreeGateway || !paidGatewayAllowed -> BrainAvailability.BLOCKED_ZERO_CREDITS
            else -> BrainAvailability.READY
        }
        return Triple(avail, matching, freeOnes)
    }

    fun rankedBrainsForTask(
        plan: BrainTaskPlan,
        thinkingMode: BrainThinkingMode,
        preferredBrainId: String,
        isDocumentTask: Boolean = false
    ): List<AiBrainFamily> {
        val base = when {
            thinkingMode == BrainThinkingMode.FAST -> listOf(
                AiBrainFamily.GEMINI_DIRECT,
                AiBrainFamily.MISTRAL_FAST,
                AiBrainFamily.LLAMA_AI,
                AiBrainFamily.GROK_AI,
                AiBrainFamily.QWEN_INTELLIGENCE
            )
            isDocumentTask -> listOf(
                AiBrainFamily.KIMI_LONG_CONTEXT,
                AiBrainFamily.GEMINI_DIRECT,
                AiBrainFamily.CLAUDE_CODING,
                AiBrainFamily.OPENAI_GPT,
                AiBrainFamily.DEEPSEEK_REASONING
            )
            plan.category in setOf(
                BrainIntentCategory.FAST_CODING,
                BrainIntentCategory.ADVANCED_CODING,
                BrainIntentCategory.MAJOR_DEVELOPMENT,
                BrainIntentCategory.SELF_UPGRADE,
                BrainIntentCategory.CRASH_FIX
            ) -> listOf(
                AiBrainFamily.GEMINI_DIRECT,
                AiBrainFamily.CLAUDE_CODING,
                AiBrainFamily.DEEPSEEK_REASONING,
                AiBrainFamily.QWEN_INTELLIGENCE,
                AiBrainFamily.OPENAI_GPT,
                AiBrainFamily.GPT_OSS_REASONING
            )
            thinkingMode in setOf(BrainThinkingMode.REASONING, BrainThinkingMode.DEEP_THINKING) ||
                plan.effort == ReasoningEffort.HIGH -> listOf(
                AiBrainFamily.GEMINI_DIRECT,
                AiBrainFamily.DEEPSEEK_REASONING,
                AiBrainFamily.GPT_OSS_REASONING,
                AiBrainFamily.OPENAI_GPT,
                AiBrainFamily.CLAUDE_CODING,
                AiBrainFamily.QWEN_INTELLIGENCE
            )
            else -> listOf(
                AiBrainFamily.GEMINI_DIRECT,
                AiBrainFamily.OPENAI_GPT,
                AiBrainFamily.LLAMA_AI,
                AiBrainFamily.MISTRAL_FAST,
                AiBrainFamily.GROK_AI,
                AiBrainFamily.QWEN_INTELLIGENCE
            )
        }
        val preferred = AiBrainFamily.fromId(preferredBrainId)
        val ordered = LinkedHashSet<AiBrainFamily>()
        if (preferred != null) ordered.add(preferred)
        ordered.addAll(base)
        ordered.addAll(AiBrainFamily.entries)
        return ordered.toList()
    }
}
