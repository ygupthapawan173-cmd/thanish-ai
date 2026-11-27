package com.example.autonomous

import com.example.ai.ModelCooldownLedger
import com.example.ai.SmartModelPolicy

/**
 * V107 Automatic Coding Model Switching within permitted quotas.
 * Tracks 429/quota cooldowns and stops cleanly when all permitted models are exhausted.
 */
class CodingModelSwitchPolicy(
    private val cooldownMs: Long = 300_000L
) {
    private val ledger = ModelCooldownLedger(limit = 64)

    fun candidateModels(discoveredChatModels: List<String>, preferredModel: String): List<String> {
        val defaults = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash")
        return (listOf(preferredModel.trim()) + discoveredChatModels.map { it.trim() } + defaults)
            .filter { SmartModelPolicy.validId(it) && it.startsWith("gemini-") && !it.contains("tts", ignoreCase = true) }
            .distinct()
    }

    fun nextAvailable(
        scope: String,
        candidates: List<String>,
        nowMs: Long = System.currentTimeMillis()
    ): List<String> = candidates.filter { !ledger.blocked(scope, it, nowMs) }

    fun markQuotaExhausted(scope: String, model: String, nowMs: Long = System.currentTimeMillis()) {
        ledger.mark(scope, model, nowMs, cooldownMs)
    }
}
