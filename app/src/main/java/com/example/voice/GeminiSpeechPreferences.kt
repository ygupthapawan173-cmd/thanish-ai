package com.example.voice

import android.content.Context

internal object GeminiSpeechPreferences {
    private fun prefs(c: Context) = c.getSharedPreferences("gemini_speech_v105", Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun enabled(c: Context, value: Boolean) {
        if (value) CloudVoicePreferences.enabled(c, false)
        prefs(c).edit().putBoolean("enabled", value).apply()
    }
    fun voice(c: Context, lang: String) = GeminiSpeechPolicy.speaker(prefs(c).getString(lang, "").orEmpty(), lang)
    fun voice(c: Context, lang: String, value: String) {
        if (lang in listOf("en", "te") && value in GeminiSpeechPolicy.speakers) {
            prefs(c).edit().putString(lang, value).apply()
        }
    }

    fun saveDiscoveredModels(c: Context, models: List<String>) {
        val clean = GeminiSpeechPolicy.candidateModels(models).joinToString(",")
        prefs(c).edit().putString("discovered_tts_models", clean).apply()
    }

    fun discoveredModels(c: Context): List<String> =
        prefs(c).getString("discovered_tts_models", "")
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }

    fun lastActiveModel(c: Context): String =
        prefs(c).getString("last_active_tts_model", GeminiSpeechPolicy.MODEL)
            .orEmpty()
            .ifBlank { GeminiSpeechPolicy.MODEL }

    fun lastActiveModel(c: Context, model: String) {
        if (model.isNotBlank()) prefs(c).edit().putString("last_active_tts_model", model).apply()
    }
}
