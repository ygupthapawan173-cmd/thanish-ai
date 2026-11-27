package com.example.data

import com.example.ai.CostProtectionPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ApiTelemetrySnapshot(
    val totalRequests: Int = 0,
    val failedRequests: Int = 0,
    val reportedPromptTokens: Long = 0,
    val reportedOutputTokens: Long = 0,
    val requestsWithoutUsage: Int = 0,
    val knownCostUsd: Double = 0.0,
    val requestsWithoutCost: Int = 0,
    val averageLatencyMs: Long = 0L,
    val lastUsedModel: String = "",
    val persistenceHealthy: Boolean = false,
    val ttsRequests: Int = 0,
    val ttsCharacters: Long = 0L,
    val ttsFallbackCount: Int = 0
) {
    fun diagnosticSummary(): String = CostProtectionPolicy.formatDiagnostics(
        totalRequests = totalRequests,
        failedRequests = failedRequests,
        promptTokens = reportedPromptTokens,
        outputTokens = reportedOutputTokens,
        knownCostUsd = knownCostUsd,
        ttsRequests = ttsRequests,
        ttsFallbacks = ttsFallbackCount,
        avgLatencyMs = averageLatencyMs
    )
}

/** Persistent HTTP chat & TTS telemetry, not a billing statement; Live audio excluded. */
interface ApiTelemetryStore {
    fun load(): ApiTelemetrySnapshot
    fun save(snapshot: ApiTelemetrySnapshot): Boolean
}

object ApiQuotaTracker {
    private val _snapshot = MutableStateFlow(ApiTelemetrySnapshot())
    val snapshot: StateFlow<ApiTelemetrySnapshot> = _snapshot.asStateFlow()
    private var store: ApiTelemetryStore? = null
    private fun add(a: Long, b: Long): Long = a.coerceAtLeast(0) + b.coerceIn(0, Long.MAX_VALUE-a.coerceAtLeast(0))
    private fun inc(a: Int): Int = if (a >= Int.MAX_VALUE) Int.MAX_VALUE else a.coerceAtLeast(0)+1
    private fun sanitize(s: ApiTelemetrySnapshot): ApiTelemetrySnapshot {
        val total=s.totalRequests.coerceAtLeast(0)
        val failed=s.failedRequests.coerceIn(0,total)
        return s.copy(
            totalRequests=total,
            failedRequests=failed,
            reportedPromptTokens=s.reportedPromptTokens.coerceAtLeast(0),
            reportedOutputTokens=s.reportedOutputTokens.coerceAtLeast(0),
            requestsWithoutUsage=s.requestsWithoutUsage.coerceIn(0,total-failed),
            requestsWithoutCost=s.requestsWithoutCost.coerceIn(0,total-failed),
            knownCostUsd=s.knownCostUsd.takeIf { it.isFinite() && it>=0 } ?: 0.0,
            averageLatencyMs=s.averageLatencyMs.coerceAtLeast(0),
            lastUsedModel=s.lastUsedModel.take(512),
            ttsRequests=s.ttsRequests.coerceAtLeast(0),
            ttsCharacters=s.ttsCharacters.coerceAtLeast(0L),
            ttsFallbackCount=s.ttsFallbackCount.coerceAtLeast(0)
        )
    }
    @Synchronized fun attach(storage: ApiTelemetryStore) {
        _snapshot.value = sanitize(runCatching { storage.load() }.getOrElse { ApiTelemetrySnapshot() })
        store = storage
    }
    private fun publish(value: ApiTelemetrySnapshot) {
        // Keep live counters even if disk write fails; do not claim persistence on failure.
        _snapshot.value = value.copy(persistenceHealthy = runCatching { store?.save(value) ?: false }.getOrDefault(false))
    }
    @Synchronized fun recordSuccess(model: String, promptTokens: Long?, outputTokens: Long?, latencyMs: Long, costUsd: Double? = null) {
        val cur = _snapshot.value
        if (cur.totalRequests == Int.MAX_VALUE) return
        val successes = cur.totalRequests - cur.failedRequests
        val prompt = promptTokens?.takeIf { it >= 0 }
        val output = outputTokens?.takeIf { it >= 0 }
        val cost = costUsd?.takeIf { it.isFinite() && it >= 0 }
        publish(cur.copy(totalRequests = inc(cur.totalRequests),
            reportedPromptTokens = add(cur.reportedPromptTokens, prompt ?: 0),
            reportedOutputTokens = add(cur.reportedOutputTokens, output ?: 0),
            requestsWithoutUsage = if (prompt == null && output == null) inc(cur.requestsWithoutUsage) else cur.requestsWithoutUsage,
            knownCostUsd = cur.knownCostUsd + (cost ?: 0.0),
            requestsWithoutCost = if (cost == null) inc(cur.requestsWithoutCost) else cur.requestsWithoutCost,
            averageLatencyMs = ((cur.averageLatencyMs * successes) + latencyMs.coerceAtLeast(0)) / (successes + 1),
            lastUsedModel = model.take(512)))
    }
    @Synchronized fun recordFailure(model: String) {
        val cur = _snapshot.value
        if (cur.totalRequests == Int.MAX_VALUE) return
        publish(cur.copy(totalRequests = inc(cur.totalRequests), failedRequests = inc(cur.failedRequests), lastUsedModel = model.take(512)))
    }
    @Synchronized fun recordTtsUsage(model: String, characters: Int, fallbackUsed: Boolean) {
        val cur = _snapshot.value
        publish(cur.copy(
            ttsRequests = inc(cur.ttsRequests),
            ttsCharacters = add(cur.ttsCharacters, characters.coerceAtLeast(0).toLong()),
            ttsFallbackCount = if (fallbackUsed) inc(cur.ttsFallbackCount) else cur.ttsFallbackCount,
            lastUsedModel = model.ifBlank { cur.lastUsedModel }.take(512)
        ))
    }
    @Synchronized fun reset() { publish(ApiTelemetrySnapshot()) }
}
