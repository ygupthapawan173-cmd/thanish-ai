package com.example.voice

/** Routing only. Engine reports are metadata, not verification of neural quality. */
internal object InstalledSpeechPolicy {
    fun language(text: String): String = if (text.any { it in '\u0C00'..'\u0C7F' }) "te" else "en"
    fun paidAllowed(freshOptIn: Boolean, enabled: Boolean): Boolean = freshOptIn && enabled
    fun networkVoiceAllowed(paidEnabled: Boolean, localOnly: Boolean, locked: Boolean): Boolean = paidEnabled && !localOnly && !locked
    fun voiceEligible(language: String, voiceLanguage: String, needsData: Boolean, network: Boolean, allowNetwork: Boolean): Boolean =
        language == voiceLanguage && !needsData && (!network || allowNetwork)
}
