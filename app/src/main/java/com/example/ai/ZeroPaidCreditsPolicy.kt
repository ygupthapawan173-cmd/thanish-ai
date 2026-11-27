package com.example.ai

/**
 * V107 Zero Additional Paid Credits Mode.
 * When enabled, blocks paid cloud TTS and paid gateway calls while keeping
 * free-tier Gemini TTS, offline Android TTS, and manual Antigravity CLI handoffs.
 */
object ZeroPaidCreditsPolicy {
    fun allowsPaidGateway(zeroPaidCreditsMode: Boolean, isFreeMode: Boolean): Boolean =
        !zeroPaidCreditsMode || isFreeMode

    fun allowsPaidCloudVoice(zeroPaidCreditsMode: Boolean): Boolean =
        !zeroPaidCreditsMode

    fun statusLabel(zeroPaidCreditsMode: Boolean): String =
        if (zeroPaidCreditsMode) "Zero Paid Credits ON (Free tier + offline fallback only)"
        else "Paid Credits Allowed (AICredits / Gateway active)"
}
