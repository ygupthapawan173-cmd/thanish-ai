package com.example.voice

internal class MicLease {
    private var holder: Any? = null
    @Synchronized fun acquire(owner: Any): Boolean {
        if (holder != null && holder !== owner) return false
        holder = owner
        return true
    }
    @Synchronized fun release(owner: Any) { if (holder === owner) holder = null }
    @Synchronized fun owns(owner: Any): Boolean = holder === owner
}
internal object VoiceMic { val lease = MicLease() }
