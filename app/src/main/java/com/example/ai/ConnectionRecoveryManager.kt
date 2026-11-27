package com.example.ai

import com.example.data.AiProvider
import kotlinx.coroutines.delay

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, RECOVERING, QUOTA_LIMITED }

data class ConnectionHealthSnapshot(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val providerLabel: String = "Not connected",
    val activeChatModel: String = "",
    val activeCodingModel: String = "",
    val activeTtsModel: String = "",
    val quotaState: String = "OK",
    val latencyMs: Long = 0L,
    val recoveryAttempt: Int = 0,
    val detail: String = "Waiting for API credentials"
)

/**
 * V107.1 Automatic Connection & Recovery Manager.
 * Runs bounded exponential backoff (1s, 3s, 8s) on transient failures without
 * bypassing quotas or enabling paid overages.
 */
object ConnectionRecoveryManager {
    private val BACKOFF_MS = longArrayOf(1_000L, 3_000L, 8_000L)

    fun backoffForAttempt(attempt: Int): Long =
        BACKOFF_MS[attempt.coerceIn(0, BACKOFF_MS.lastIndex)]

    fun isTransientError(message: String?): Boolean {
        val m = message?.lowercase().orEmpty()
        return "timeout" in m || "timed out" in m || "502" in m || "503" in m || "504" in m ||
            "connect" in m || "reset" in m || "unavailable" in m
    }

    fun isQuotaError(message: String?): Boolean {
        val m = message?.lowercase().orEmpty()
        return "429" in m || "quota" in m || "resource_exhausted" in m
    }

    suspend fun <T> runWithAutoRecovery(
        maxAttempts: Int = 3,
        onRecovering: (Int, Long) -> Unit = { _, _ -> },
        block: suspend () -> Result<T>
    ): Result<T> {
        var lastResult = block()
        var attempt = 0
        while (lastResult.isFailure && attempt < maxAttempts) {
            val msg = lastResult.exceptionOrNull()?.message
            if (!isTransientError(msg) || isQuotaError(msg)) break
            val waitMs = backoffForAttempt(attempt)
            attempt++
            onRecovering(attempt, waitMs)
            delay(waitMs)
            lastResult = block()
        }
        return lastResult
    }

    fun initialProviderLabel(provider: AiProvider, hasGemini: Boolean, hasGateway: Boolean): String = when {
        provider == AiProvider.GEMINI && hasGemini -> "Google Gemini (Official API)"
        provider == AiProvider.OPENROUTER && hasGateway -> "Smart Gateway (AICredits)"
        hasGemini -> "Google Gemini (Official API)"
        else -> "No Key Configured"
    }
}
