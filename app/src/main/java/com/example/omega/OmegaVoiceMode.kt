package com.example.omega
import android.content.Context
enum class OmegaVoiceMode(val label: String) {
    PLATFORM("Android speech service (may use internet)"),
    LOCAL("On-device only (never uploads voice)"),
    HYBRID("Hybrid: offline first, online if unavailable")
}
enum class OmegaWakePhrase(val label: String, val spoken: List<String>) {
    HEY_JARVIS("Hey JARVIS", listOf("hey jarvis", "hey jervis", "hey jar vis", "hey jar vice")),
    HEY_OMEGA("Hey OMEGA", listOf("hey omega")),
    OMEGA("OMEGA", listOf("omega")),
    CUSTOM("Custom wake phrase", emptyList())
}
object OmegaVoicePreferences {
    private fun prefs(c: Context) = c.getSharedPreferences("omega_voice", 0)

    fun customWake(c: Context): String = prefs(c).getString("custom_wake", "").orEmpty()
    fun setCustomWake(c: Context, value: String): Boolean {
        val validated = OmegaWakeEngine.validCustomPhrase(value) ?: return false
        prefs(c).edit().putString("custom_wake", validated).apply()
        return true
    }
    fun activeWakePhrases(c: Context): List<String> =
        if (wakePhrase(c) == OmegaWakePhrase.CUSTOM) {
            listOfNotNull(OmegaWakeEngine.validCustomPhrase(customWake(c)))
                .ifEmpty { OmegaWakePhrase.HEY_JARVIS.spoken }
        } else wakePhrase(c).spoken

    fun sensitivity(c: Context): WakeSensitivity = runCatching {
        WakeSensitivity.valueOf(prefs(c).getString("wake_sensitivity", WakeSensitivity.STRICT.name)!!)
    }.getOrDefault(WakeSensitivity.STRICT)
    fun setSensitivity(c: Context, value: WakeSensitivity) {
        prefs(c).edit().putString("wake_sensitivity", value.name).apply()
    }

    fun acknowledgement(c: Context): WakeAckStyle = runCatching {
        WakeAckStyle.valueOf(prefs(c).getString("wake_ack", WakeAckStyle.YES_SIR.name)!!)
    }.getOrDefault(WakeAckStyle.YES_SIR)
    fun setAcknowledgement(c: Context, value: WakeAckStyle) {
        prefs(c).edit().putString("wake_ack", value.name).apply()
    }
    fun customAcknowledgement(c: Context): String = prefs(c).getString("wake_ack_custom", "").orEmpty()
    fun setCustomAcknowledgement(c: Context, value: String) {
        prefs(c).edit().putString("wake_ack_custom", OmegaWakeAck.sanitizeCustom(value)).apply()
    }

    fun wakePhrase(c: Context): OmegaWakePhrase = runCatching {
        OmegaWakePhrase.valueOf(c.getSharedPreferences("omega_voice", 0)
            .getString("wake_phrase", OmegaWakePhrase.HEY_JARVIS.name)!!)
    }.getOrDefault(OmegaWakePhrase.HEY_JARVIS)
    fun setWakePhrase(c: Context, phrase: OmegaWakePhrase) {
        c.getSharedPreferences("omega_voice", 0).edit()
            .putString("wake_phrase", phrase.name).apply()
    }

    fun get(c: Context): OmegaVoiceMode = runCatching {
        OmegaVoiceMode.valueOf(c.getSharedPreferences("omega_voice", 0)
            .getString("mode", OmegaVoiceMode.PLATFORM.name)!!)
    }.getOrDefault(OmegaVoiceMode.PLATFORM)
    fun set(c: Context, m: OmegaVoiceMode) {
        c.getSharedPreferences("omega_voice", 0).edit().putString("mode", m.name).apply()
    }
    fun backgroundAi(c: Context): Boolean = prefs(c).getBoolean("background_ai_opt_in", false)
    fun setBackgroundAi(c: Context, enabled: Boolean) { prefs(c).edit().putBoolean("background_ai_opt_in", enabled).apply() }
    fun memoryContext(c: Context): Boolean = prefs(c).getBoolean("memory_context_opt_in", false)
    fun setMemoryContext(c: Context, enabled: Boolean) { prefs(c).edit().putBoolean("memory_context_opt_in", enabled).apply() }
    fun continuousWake(c: Context): Boolean = prefs(c).getBoolean("continuous_wake_opt_in", false)
    fun setContinuousWake(c: Context, enabled: Boolean) {
        prefs(c).edit().putBoolean("continuous_wake_opt_in", enabled).apply()
    }
    fun continuous(c: Context): Boolean =
        c.getSharedPreferences("omega_voice", 0).getBoolean("continuous_opt_in", false)
    fun setContinuous(c: Context, enabled: Boolean) {
        c.getSharedPreferences("omega_voice", 0).edit().putBoolean("continuous_opt_in", enabled).apply()
    }
}

