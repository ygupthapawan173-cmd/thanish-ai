package com.example.autonomous

import com.example.ai.ModelCooldownLedger
import com.example.ai.SmartModelPolicy

/**
 * V108.2 Automatic Coding Model Switching within permitted quotas.
 * Handles HTTP 429, 404, 500, 502, 503 (high demand), and 504 with temporary
 * model cooldowns and stops cleanly when all permitted models are exhausted.
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

    fun markQuotaExhausted(
        scope: String,
        model: String,
        nowMs: Long = System.currentTimeMillis(),
        customCooldownMs: Long = cooldownMs
    ) {
        ledger.mark(scope, model, nowMs, customCooldownMs.coerceAtLeast(1_000L))
    }

    companion object {
        private val SWITCHABLE_CODES = listOf("429", "404", "500", "502", "503", "504")

        fun isSwitchableError(message: String?): Boolean {
            val m = message?.lowercase().orEmpty()
            if (SWITCHABLE_CODES.any { "http $it" in m || "code $it" in m || it in m }) return true
            return "quota" in m || "resource_exhausted" in m || "high demand" in m ||
                "overloaded" in m || "unavailable" in m || "not found" in m || "not supported" in m
        }

        fun cooldownForError(message: String?): Long {
            val m = message?.lowercase().orEmpty()
            return when {
                "429" in m || "quota" in m || "resource_exhausted" in m -> 300_000L
                "404" in m || "not found" in m -> 600_000L
                else -> 90_000L // Temporary cooldown for 500/502/503/504 high-demand
            }
        }
    }
}
