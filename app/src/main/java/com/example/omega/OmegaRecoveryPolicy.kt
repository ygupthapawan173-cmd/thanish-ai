package com.example.omega

object OmegaRecoveryPolicy {
    fun platformFallback(mode: OmegaVoiceMode, local: Boolean, alreadyFallback: Boolean,
        available: Boolean, errorCode: Int): Boolean =
        mode == OmegaVoiceMode.HYBRID && local && !alreadyFallback && available &&
            errorCode in setOf(3, 4, 5, 8, 11, 12, 13, 14, 15)

    fun mayAcceptWake(pending: String): Boolean = pending.isBlank()
    fun keepListeningAfterCapture(continuous: Boolean): Boolean = continuous

    fun accessibilityLabel(enabled: Boolean, connected: Boolean): String = when {
        connected && enabled -> "Connected"
        enabled -> "Enabled in Android; service not bound"
        connected -> "Binding state changing; refresh"
        else -> "Disabled in Android settings"
    }
}
