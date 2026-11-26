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
    OMEGA("OMEGA", listOf("omega"))
}
object OmegaVoicePreferences {
    fun wakePhrase(c: Context): OmegaWakePhrase = runCatching {
        OmegaWakePhrase.valueOf(c.getSharedPreferences("omega_voice", 0)
            .getString("wake_phrase", OmegaWakePhrase.HEY_JARVIS.name)!!)
    }.getOrDefault(OmegaWakePhrase.HEY_JARVIS)
    fun setWakePhrase(c: Context, phrase: OmegaWakePhrase) {
        c.getSharedPreferences("omega_voice", 0).edit()
            .putString("wake_phrase", phrase.name)
            .putBoolean("custom_wake_enabled", false)
            .apply()
    }

    fun sensitivity(c: Context): WakeSensitivity = runCatching {
        WakeSensitivity.valueOf(c.getSharedPreferences("omega_voice", 0)
            .getString("wake_sensitivity", WakeSensitivity.STRICT.name)!!)
    }.getOrDefault(WakeSensitivity.STRICT)

    fun setSensitivity(c: Context, value: WakeSensitivity) {
        c.getSharedPreferences("omega_voice", 0).edit()
            .putString("wake_sensitivity", value.name).apply()
    }

    fun customWakePhrase(c: Context): String? =
        c.getSharedPreferences("omega_voice", 0).getString("custom_wake_phrase", null)
            ?.let(OmegaWakeEngine::validCustomPhrase)

    fun customWakeEnabled(c: Context): Boolean =
        c.getSharedPreferences("omega_voice", 0).getBoolean("custom_wake_enabled", false) &&
            customWakePhrase(c) != null

    fun setCustomWakePhrase(c: Context, raw: String): Boolean {
        val valid = OmegaWakeEngine.validCustomPhrase(raw) ?: return false
        return c.getSharedPreferences("omega_voice", 0).edit()
            .putString("custom_wake_phrase", valid)
            .putBoolean("custom_wake_enabled", true).commit()
    }

    fun activeWakePhrases(c: Context): List<String> =
        if (customWakeEnabled(c)) listOfNotNull(customWakePhrase(c))
        else wakePhrase(c).spoken

    fun activeWakeLabel(c: Context): String =
        if (customWakeEnabled(c)) customWakePhrase(c).orEmpty()
        else wakePhrase(c).label

    fun ackStyle(c: Context): WakeAckStyle = runCatching {
        WakeAckStyle.valueOf(c.getSharedPreferences("omega_voice", 0)
            .getString("wake_ack", WakeAckStyle.YES_SIR.name)!!)
    }.getOrDefault(WakeAckStyle.YES_SIR)

    fun setAckStyle(c: Context, value: WakeAckStyle) {
        c.getSharedPreferences("omega_voice", 0).edit()
            .putString("wake_ack", value.name).apply()
    }

    fun customAck(c: Context): String =
        c.getSharedPreferences("omega_voice", 0)
            .getString("wake_custom_ack", "").orEmpty()

    fun setCustomAck(c: Context, value: String) {
        c.getSharedPreferences("omega_voice", 0).edit()
            .putString("wake_custom_ack", OmegaWakeAck.sanitizeCustom(value)).apply()
    }

    fun get(c: Context): OmegaVoiceMode = runCatching {
        OmegaVoiceMode.valueOf(c.getSharedPreferences("omega_voice", 0)
            .getString("mode", OmegaVoiceMode.PLATFORM.name)!!)
    }.getOrDefault(OmegaVoiceMode.PLATFORM)
    fun set(c: Context, m: OmegaVoiceMode) {
        c.getSharedPreferences("omega_voice", 0).edit().putString("mode", m.name).apply()
    }
    fun continuous(c: Context): Boolean =
        c.getSharedPreferences("omega_voice", 0).getBoolean("continuous_opt_in", false)
    fun setContinuous(c: Context, enabled: Boolean) {
        c.getSharedPreferences("omega_voice", 0).edit().putBoolean("continuous_opt_in", enabled).apply()
    }
}

