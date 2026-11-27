package com.example.voice

/** null = unavailable. Local-only privacy never falls back to a network provider. */
internal object OnlineSpeechPolicy {
    fun useLocal(localOnly: Boolean, system: Boolean, local: Boolean, attempt: Int, onlineOnly: Boolean = false): Boolean? = when {
        onlineOnly -> if (!localOnly && system) false else null
        localOnly -> if (local) true else null
        system && (attempt != 1 || !local) -> false
        local -> true
        else -> null
    }
}
