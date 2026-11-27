package com.example.data

import android.content.Context

class PersistentApiTelemetry(context: Context) : ApiTelemetryStore {
    private val prefs = context.applicationContext.getSharedPreferences("infinity_api_telemetry_v1", Context.MODE_PRIVATE)
    override fun load(): ApiTelemetrySnapshot = ApiTelemetrySnapshot(
        totalRequests = prefs.getInt("total", 0), failedRequests = prefs.getInt("failed", 0),
        reportedPromptTokens = prefs.getLong("prompt", 0), reportedOutputTokens = prefs.getLong("output", 0),
        requestsWithoutUsage = prefs.getInt("unknown_usage", 0),
        knownCostUsd = Double.fromBits(prefs.getLong("cost", 0L)),
        requestsWithoutCost = prefs.getInt("unknown_cost", 0), averageLatencyMs = prefs.getLong("latency", 0),
        lastUsedModel = prefs.getString("model", "").orEmpty(), persistenceHealthy = true,
        ttsRequests = prefs.getInt("tts_requests", 0),
        ttsCharacters = prefs.getLong("tts_chars", 0L),
        ttsFallbackCount = prefs.getInt("tts_fallbacks", 0)
    )
    override fun save(snapshot: ApiTelemetrySnapshot): Boolean = runCatching {
        prefs.edit().putInt("total", snapshot.totalRequests).putInt("failed", snapshot.failedRequests)
            .putLong("prompt", snapshot.reportedPromptTokens).putLong("output", snapshot.reportedOutputTokens)
            .putInt("unknown_usage", snapshot.requestsWithoutUsage).putLong("cost", snapshot.knownCostUsd.toBits())
            .putInt("unknown_cost", snapshot.requestsWithoutCost).putLong("latency", snapshot.averageLatencyMs)
            .putString("model", snapshot.lastUsedModel)
            .putInt("tts_requests", snapshot.ttsRequests)
            .putLong("tts_chars", snapshot.ttsCharacters)
            .putInt("tts_fallbacks", snapshot.ttsFallbackCount)
            .commit()
    }.getOrDefault(false)
}
