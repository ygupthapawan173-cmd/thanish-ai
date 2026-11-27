package com.example.omega

/** A stale, cancelled, stopped or previous-day job must not produce device side effects. */
object QueueRunPolicy {
    fun allowed(token: String, currentToken: String?, receivedEpoch: Long, currentEpoch: Long,
                day: String, currentDay: String, stopped: Boolean): Boolean =
        token.isNotBlank() && token == currentToken && receivedEpoch >= 0 && receivedEpoch == currentEpoch &&
            day.isNotBlank() && day == currentDay && !stopped
}
