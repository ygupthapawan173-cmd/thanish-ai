package com.example.voice

/**
 * Bounded speaker-model loading recovery policy for owner voice verification.
 * - Caps consecutive speaker-model load retries at MAX_LOAD_ATTEMPTS (3).
 * - Uses bounded backoff delays (1000ms, 2500ms, 5000ms).
 * - NEVER bypasses owner verification when owner filtering is enabled, even after retries exhaust.
 */
internal class OwnerModelRecoveryTracker(
    private val maxAttempts: Int = MAX_LOAD_ATTEMPTS
) {
    enum class LoadState {
        IDLE,
        LOADING,
        READY,
        FAILED_BOUNDED
    }

    companion object {
        const val MAX_LOAD_ATTEMPTS = 3

        fun retryDelayMs(attempt: Int): Long = when (attempt.coerceIn(1, MAX_LOAD_ATTEMPTS)) {
            1 -> 1_000L
            2 -> 2_500L
            else -> 5_000L
        }
    }

    @Volatile
    var state: LoadState = LoadState.IDLE
        private set

    @Volatile
    var attempts: Int = 0
        private set

    @Volatile
    var nextRetryAtMs: Long = 0L
        private set

    @Synchronized
    fun beginLoad(nowMs: Long): Boolean {
        if (state == LoadState.READY || state == LoadState.LOADING || state == LoadState.FAILED_BOUNDED) {
            return false
        }
        if (attempts >= maxAttempts) {
            state = LoadState.FAILED_BOUNDED
            return false
        }
        if (nowMs < nextRetryAtMs) return false
        attempts++
        state = LoadState.LOADING
        return true
    }

    @Synchronized
    fun onLoadSuccess() {
        state = LoadState.READY
        attempts = 0
        nextRetryAtMs = 0L
    }

    @Synchronized
    fun onLoadFailure(nowMs: Long): LoadState {
        if (attempts >= maxAttempts) {
            state = LoadState.FAILED_BOUNDED
            nextRetryAtMs = Long.MAX_VALUE
        } else {
            state = LoadState.IDLE
            nextRetryAtMs = nowMs + retryDelayMs(attempts)
        }
        return state
    }

    @Synchronized
    fun reset() {
        state = LoadState.IDLE
        attempts = 0
        nextRetryAtMs = 0L
    }

    fun canArmWake(ownerFilterEnabled: Boolean, modelLoaded: Boolean): Boolean {
        if (!ownerFilterEnabled) return true
        // Strict security invariant: never bypass owner verification when owner filtering is enabled.
        return modelLoaded
    }
}
