package com.example.omega

/** Shared by the Activity and wake-service receivers, using monotonic time. */
class TriggerDebouncePolicy(private val cooldownMs: Long = 2500L) {
    private val last = mutableMapOf<String, Long>()
    @Synchronized fun claim(action: String, now: Long): Boolean {
        if (action.isBlank() || now < 0) return false
        val previous = last[action]
        if (previous != null && now >= previous && now - previous < cooldownMs) return false
        last[action] = now
        return true
    }
}
