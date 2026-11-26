package com.example.ai

import java.net.SocketException
import java.net.SocketTimeoutException

/** Pure retry/failover rules (no Android dependencies, unit-testable). */
object AiRetryPolicy {
    const val MAX_RETRIES = 2

    /** 700 ms, 1.4 s, 2.8 s ... plus optional jitter (0..500 ms). */
    fun backoffMs(attempt: Int, jitterMs: Long = 0L): Long =
        700L * (1L shl attempt.coerceIn(0, 4)) + jitterMs.coerceIn(0L, 500L)

    /**
     * Delay before retrying the SAME provider, or null to stop and fail over to the next provider.
     * Callers must never retry once reply text has already streamed.
     */
    fun retryDelayMs(status: Int, retryAfter: String?, attempt: Int, jitterMs: Long = 0L): Long? = when {
        status == 429 -> {
            val seconds = retryAfter?.trim()
                ?.takeIf { it.length in 1..3 && it.all(Char::isDigit) }?.toLongOrNull()
            if (attempt == 0 && seconds != null && seconds <= 4L) seconds * 1000L + 250L else null
        }
        status == 500 -> if (attempt < 1) backoffMs(attempt, jitterMs) else null
        status == 502 || status == 503 || status == 504 ->
            if (attempt < MAX_RETRIES) backoffMs(attempt, jitterMs) else null
        else -> null
    }

    fun isTransientNetwork(e: Throwable): Boolean =
        e is SocketTimeoutException || e is SocketException

    fun describeStatus(status: Int): String = when (status) {
        400 -> "bad request/model"
        401 -> "API key rejected"
        402 -> "billing/credits"
        403 -> "access denied"
        404 -> "model not found"
        429 -> "rate limit/quota"
        500, 502, 503, 504 -> "provider unavailable"
        else -> "HTTP $status"
    }
}
