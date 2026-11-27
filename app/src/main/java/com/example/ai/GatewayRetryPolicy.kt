package com.example.ai

internal object GatewayRetryPolicy {
    fun modelSpecific400(code: String): Boolean = code.lowercase(java.util.Locale.ROOT) in setOf("model_not_found", "model_not_supported", "unsupported_model", "invalid_model")
    fun cooldownMs(status: Int, retryAfter: String?): Long = when (status) {
        404, 400 -> 300_000L
        429 -> (retryAfter?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.coerceIn(1L,300L) ?: 30L)*1000L
        else -> 0L
    }
}
