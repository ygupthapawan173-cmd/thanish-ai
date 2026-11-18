package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.voice.TeluguTtsConfig
import com.example.voice.TtsPersonalityPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ttsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pandu_telugu_tts_prefs"
)

class TtsPreferencesRepository(private val context: Context) {

    private object Keys {
        val PRESET_ID = stringPreferencesKey("tts_preset_id")
        val PITCH = floatPreferencesKey("tts_pitch")
        val SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val VOICE_NAME = stringPreferencesKey("tts_voice_name")
        val AUTO_SPEAK = booleanPreferencesKey("tts_auto_speak")
    }

    val ttsConfig: Flow<TeluguTtsConfig> = context.ttsDataStore.data.map { prefs ->
        val preset = TtsPersonalityPreset.fromId(prefs[Keys.PRESET_ID])
        TeluguTtsConfig(
            preset = preset,
            pitch = (prefs[Keys.PITCH] ?: preset.pitch).coerceIn(0.75f, 1.35f),
            speechRate = (prefs[Keys.SPEECH_RATE] ?: preset.speechRate).coerceIn(0.75f, 1.40f),
            selectedVoiceName = prefs[Keys.VOICE_NAME]?.takeIf { it.isNotBlank() },
            autoSpeakEnabled = prefs[Keys.AUTO_SPEAK] ?: true
        )
    }

    suspend fun applyPreset(preset: TtsPersonalityPreset) {
        context.ttsDataStore.edit { prefs ->
            prefs[Keys.PRESET_ID] = preset.id
            prefs[Keys.PITCH] = preset.pitch
            prefs[Keys.SPEECH_RATE] = preset.speechRate
        }
    }

    suspend fun updatePitch(pitch: Float) {
        context.ttsDataStore.edit { prefs ->
            prefs[Keys.PITCH] = pitch.coerceIn(0.75f, 1.35f)
        }
    }

    suspend fun updateSpeechRate(speechRate: Float) {
        context.ttsDataStore.edit { prefs ->
            prefs[Keys.SPEECH_RATE] = speechRate.coerceIn(0.75f, 1.40f)
        }
    }

    suspend fun updateSelectedVoice(voiceName: String?) {
        context.ttsDataStore.edit { prefs ->
            if (voiceName.isNullOrBlank()) {
                prefs.remove(Keys.VOICE_NAME)
            } else {
                prefs[Keys.VOICE_NAME] = voiceName
            }
        }
    }

    suspend fun setAutoSpeak(enabled: Boolean) {
        context.ttsDataStore.edit { prefs ->
            prefs[Keys.AUTO_SPEAK] = enabled
        }
    }
}
