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
            .putString("wake_phrase", phrase.name).apply()
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

