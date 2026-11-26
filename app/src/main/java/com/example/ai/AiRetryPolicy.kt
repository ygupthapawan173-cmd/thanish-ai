package com.example.ai

import java.net.SocketException
import java.net.SocketTimeoutException

/** Bounded retry rules. Never retry a response after streamed text is visible. */
object AiRetryPolicy {
    const val MAX_RETRIES = 2

    fun backoffMs(attempt: Int, jitterMs: Long = 0L): Long =
        700L * (1L shl attempt.coerceIn(0, 4)) + jitterMs.coerceIn(0L, 500L)

    fun retryDelayMs(status: Int, retryAfter: String?, attempt: Int, jitterMs: Long = 0L): Long? = when {
        status == 429 -> {
            val s = retryAfter?.trim()?.takeIf { it.length in 1..3 && it.all(Char::isDigit) }?.toLongOrNull()
            if (attempt == 0 && s != null && s <= 4L) s * 1000L + 250L else null
        }
        status == 500 -> if (attempt < 1) backoffMs(attempt, jitterMs) else null
        status in 502..504 -> if (attempt < MAX_RETRIES) backoffMs(attempt, jitterMs) else null
        else -> null
    }

    fun isTransientNetwork(e: Throwable): Boolean = e is SocketTimeoutException || e is SocketException

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
