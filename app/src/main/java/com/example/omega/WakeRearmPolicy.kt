package com.example.omega

/** All calls from the wake service main thread. One pending attempt; watchdog cannot shorten retry backoff. */
internal class WakeRearmPolicy {
    private var pending = false
    fun reserve(eligible: Boolean, modelReady: Boolean, capturing: Boolean): Boolean {
        if (!eligible || !modelReady || capturing || pending) return false
        pending = true
        return true
    }
    fun consume() { pending = false }
    fun cancel() { pending = false }
    companion object {
        fun eligible(started: Boolean, closed: Boolean, enabled: Boolean, muted: Boolean, permission: Boolean,
                     ownerReady: Boolean, waiting: Boolean, backgroundActive: Boolean, micBusy: Boolean, ttsBusy: Boolean): Boolean =
            started && !closed && enabled && !muted && permission && ownerReady && !waiting && !backgroundActive && !micBusy && !ttsBusy
    }
}
