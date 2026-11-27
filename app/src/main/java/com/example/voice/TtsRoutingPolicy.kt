package com.example.voice

import android.content.Context

/**
 * Independent TTS routing policy and preference storage.
 * Does NOT depend on OmegaPreferences.preferOfflineSpeech (which V108.1 forces to false).
 *
 * Precedence:
 * 1. forceLocalOnly -> ANDROID_LOCAL
 * 2. Explicitly enabled Gemini TTS -> GEMINI_TTS_WITH_FALLBACK (bounded timeout + automatic Android fallback)
 * 3. Explicitly enabled paid Cloud TTS (subject to existing budget and privacy checks) -> PAID_CLOUD_WITH_FALLBACK
 * 4. Otherwise (default local-first or explicit Android TTS engine/voice selection) -> ANDROID_LOCAL
 */
internal enum class TtsRouteDecision {
    ANDROID_LOCAL,
    GEMINI_TTS_WITH_FALLBACK,
    PAID_CLOUD_WITH_FALLBACK
}

internal object TtsRoutingPreferences {
    private const val PREFS_NAME = "jarvis_tts_routing_v109"
    private const val KEY_LOCAL_FIRST = "local_first_tts"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isLocalFirst(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCAL_FIRST, true)

    fun setLocalFirst(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCAL_FIRST, enabled).apply()
    }
}

internal object TtsRoutingPolicy {
    const val WAKE_ACK_CLOUD_TIMEOUT_MS = 2_500L
    const val REPLY_CLOUD_TIMEOUT_MS = 4_500L

    fun decide(
        localFirstEnabled: Boolean,
        geminiTtsExplicitlyEnabled: Boolean,
        cloudTtsExplicitlyEnabled: Boolean,
        androidEngineExplicitlySelected: Boolean,
        forceLocalOnly: Boolean
    ): TtsRouteDecision {
        if (forceLocalOnly) return TtsRouteDecision.ANDROID_LOCAL
        if (geminiTtsExplicitlyEnabled) return TtsRouteDecision.GEMINI_TTS_WITH_FALLBACK
        if (cloudTtsExplicitlyEnabled) {
            return TtsRouteDecision.PAID_CLOUD_WITH_FALLBACK
        }
        return TtsRouteDecision.ANDROID_LOCAL
    }

    fun cloudSynthesisTimeoutMs(isShortAck: Boolean): Long =
        if (isShortAck) WAKE_ACK_CLOUD_TIMEOUT_MS else REPLY_CLOUD_TIMEOUT_MS
}
