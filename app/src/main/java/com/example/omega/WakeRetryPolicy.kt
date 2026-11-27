package com.example.omega

/** Bounded exponential delay; never disable user wake preference on a transient audio failure. */
internal object WakeRetryPolicy {
    fun delayMs(attempt: Int): Long = 1000L shl (attempt.coerceIn(1,6)-1)
}
