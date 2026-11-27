package com.example.ai

/**
 * V106 API Cost Protection & Usage Guardrails.
 * Prevents runaway API spend or request loops while preserving free/offline fallback.
 */
object CostProtectionPolicy {
    const val DEFAULT_MAX_DAILY_REQUESTS = 500
    const val DEFAULT_MAX_SESSION_USD = 2.0

    data class GuardResult(
        val allowed: Boolean,
        val reason: String? = null
    )

    fun evaluateRequest(
        totalRequests: Int,
        knownCostUsd: Double,
        providerBudgetUsd: Double,
        isFreeMode: Boolean,
        maxDailyRequests: Int = DEFAULT_MAX_DAILY_REQUESTS
    ): GuardResult {
        if (totalRequests < 0 || !knownCostUsd.isFinite() || knownCostUsd < 0.0) {
            return GuardResult(false, "Invalid telemetry state")
        }
        if (totalRequests >= maxDailyRequests) {
            return GuardResult(false, "Daily request safety cap reached ($maxDailyRequests requests)")
        }
        if (!isFreeMode && providerBudgetUsd > 0.0 && knownCostUsd >= providerBudgetUsd) {
            return GuardResult(false, "Configured API budget cap reached ($${"%.2f".format(providerBudgetUsd)})")
        }
        return GuardResult(true)
    }

    fun formatDiagnostics(
        totalRequests: Int,
        failedRequests: Int,
        promptTokens: Long,
        outputTokens: Long,
        knownCostUsd: Double,
        ttsRequests: Int,
        ttsFallbacks: Int,
        avgLatencyMs: Long
    ): String {
        val successRate = if (totalRequests > 0) ((totalRequests - failedRequests) * 100) / totalRequests else 100
        return "Requests: $totalRequests ($successRate% ok) | Tokens: ${promptTokens + outputTokens} | Est. Cost: $${"%.4f".format(knownCostUsd)} | TTS: $ttsRequests (Fallbacks: $ttsFallbacks) | Avg: ${avgLatencyMs}ms"
    }
}
