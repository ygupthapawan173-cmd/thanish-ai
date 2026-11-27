package com.example.voice

import android.content.Context

internal object CloudVoicePreferences {
    private fun prefs(c: Context) = c.getSharedPreferences("cloud_voice_v101", Context.MODE_PRIVATE)
    fun enabled(c: Context) = InstalledSpeechPolicy.paidAllowed(prefs(c).getBoolean("v104_paid_opt_in", false), prefs(c).getBoolean("enabled", false))
    fun hd(c: Context) = prefs(c).getBoolean("hd", false)
    fun voice(c: Context, lang: String) = prefs(c).getString(lang, if (lang == "te") "anushka" else "onyx").orEmpty()
    fun enabled(c: Context, value: Boolean) { prefs(c).edit().putBoolean("enabled", value).putBoolean("v104_paid_opt_in", value).apply() }
    fun hd(c: Context, value: Boolean) { prefs(c).edit().putBoolean("hd", value).apply() }
    fun voice(c: Context, lang: String, value: String) {
        if (value in (if (lang == "te") CloudVoicePolicy.telugu else CloudVoicePolicy.english)) prefs(c).edit().putString(lang, value).apply()
    }
}
