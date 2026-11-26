package com.example.omega
import android.content.Context
enum class OmegaVoiceMode(val label: String) {
    PLATFORM("Android speech service"), LOCAL("On-device only"), HYBRID("Prefer on-device")
}
object OmegaVoicePreferences {
    fun get(c: Context): OmegaVoiceMode = runCatching {
        OmegaVoiceMode.valueOf(c.getSharedPreferences("omega_voice", 0)
            .getString("mode", OmegaVoiceMode.PLATFORM.name)!!)
    }.getOrDefault(OmegaVoiceMode.PLATFORM)
    fun set(c: Context, m: OmegaVoiceMode) {
        c.getSharedPreferences("omega_voice", 0).edit().putString("mode", m.name).apply()
    }
}

