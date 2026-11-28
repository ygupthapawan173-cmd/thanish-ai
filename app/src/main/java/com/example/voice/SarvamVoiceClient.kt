package com.example.voice

import android.util.Base64
import com.example.ai.awaitParsed
import com.example.ai.boundedJsonText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SarvamSpeakerProfile(
    val id: String,
    val label: String,
    val model: String
)

/**
 * Official Sarvam AI Telugu + Mixed Tenglish/English Premium Voice Client (Section 8 & 16):
 * - Calls `https://api.sarvam.ai/text-to-speech` (`bulbul:v3` & `bulbul:v4-flash`)
 * - Natural male Telugu speakers (`shubh`, `ratan`, `mohan_te_conversational`)
 * - Automatic network retry (backoff), real error parsing, and ZERO Android TTS fallback
 */
object SarvamVoiceClient {

    const val REST_TTS_URL = "https://api.sarvam.ai/text-to-speech"
    const val WS_TTS_URL = "wss://api.sarvam.ai/text-to-speech/ws"
    const val MAX_CHARS = 2400

    val MALE_SPEAKERS: List<SarvamSpeakerProfile> = listOf(
        SarvamSpeakerProfile("shubh", "Shubh (Natural Male • Bulbul v3)", "bulbul:v3"),
        SarvamSpeakerProfile("ratan", "Ratan (Crisp Assistant Male • Bulbul v3)", "bulbul:v3"),
        SarvamSpeakerProfile("mohan_te_conversational", "Mohan (Telugu Conversational • Bulbul v4-Flash)", "bulbul:v4-flash")
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(28, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun detectLanguageCode(text: String, configuredLanguage: String = "auto"): String {
        if (configuredLanguage == "te-IN" || configuredLanguage == "en-IN") return configuredLanguage
        return if (text.any { it in '\u0C00'..'\u0C7F' }) "te-IN" else "en-IN"
    }

    fun normalizeForSarvamSpeech(rawText: String, isTelugu: Boolean): String {
        return rawText
            .replace(Regex("```[\\s\\S]*?```"), if (isTelugu) " కోడ్ వివరాలు స్క్రీన్‌పై ఉన్నాయి. " else " Code shown on screen. ")
            .replace(Regex("https?://\\S+"), if (isTelugu) " లింక్ " else " link ")
            .replace("*", "")
            .replace("#", "")
            .replace("`", "")
            .replace("•", ", ")
            .replace("°C", if (isTelugu) " డిగ్రీల సెల్సియస్" else " degrees Celsius")
            .replace("%", if (isTelugu) " శాతం" else " percent")
            .replace("&", if (isTelugu) " మరియు " else " and ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_CHARS)
    }

    suspend fun synthesize(
        apiKey: String,
        rawText: String,
        languageTag: String = "auto",
        speakerId: String = "shubh",
        modelOverride: String = "bulbul:v3"
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Sarvam AI API key is missing. Add it in Voice or Settings."))
        }
        val langCode = detectLanguageCode(rawText, languageTag)
        val clean = normalizeForSarvamSpeech(rawText, langCode == "te-IN")
        if (clean.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Empty speech text"))
        }

        val profile = MALE_SPEAKERS.firstOrNull { it.id == speakerId } ?: MALE_SPEAKERS.first()
        val effectiveModel = if (speakerId.contains("_te_")) "bulbul:v4-flash" else modelOverride.ifBlank { profile.model }
        val cacheKey = "$effectiveModel:${profile.id}:$langCode"
        VoiceResponseCache.get(cacheKey, clean)?.let { return@withContext Result.success(it) }

        val payload = JSONObject().apply {
            put("text", clean)
            put("language_code", langCode)
            put("model", effectiveModel)
            put("speaker", profile.id)
            put("pace", 1.0)
            put("speech_sample_rate", 22050)
            put("enable_preprocessing", true)
            put("output_audio_codec", "mp3")
        }

        var lastErr: Exception? = null
        for (attempt in 0 until 2) {
            try {
                val bytes = callTtsOnce(key, payload)
                VoiceResponseCache.put(cacheKey, clean, bytes)
                return@withContext Result.success(bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastErr = e
                val msg = e.message.orEmpty()
                if ("HTTP 401" in msg || "HTTP 403" in msg || "HTTP 400" in msg) break
                if (attempt == 0) delay(900L)
            }
        }
        Result.failure(lastErr ?: IOException("Sarvam speech synthesis failed"))
    }

    suspend fun testConnection(apiKey: String, speakerId: String = "shubh"): Result<String> {
        val sample = "నమస్కారం బాస్, సర్వమ్ తెలుగు వాయిస్ సిద్ధంగా ఉంది."
        val started = System.currentTimeMillis()
        val res = synthesize(apiKey, sample, languageTag = "te-IN", speakerId = speakerId)
        val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)
        return res.map { audio ->
            "Sarvam ($speakerId) active • ${audio.size / 1024} KB audio in ${elapsed}ms"
        }
    }

    private suspend fun callTtsOnce(apiKey: String, payload: JSONObject): ByteArray {
        val req = Request.Builder()
            .url(REST_TTS_URL)
            .addHeader("Api-Subscription-Key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return client.newCall(req).awaitParsed { resp ->
            val contentType = resp.header("Content-Type").orEmpty().lowercase()
            if (!resp.isSuccessful) {
                val errBody = runCatching { resp.boundedJsonText(65_536L) }.getOrDefault("")
                val detail = runCatching {
                    val o = JSONObject(errBody)
                    o.optJSONObject("error")?.optString("message") ?: o.optString("detail")
                }.getOrNull().orEmpty()
                throw IOException("HTTP ${resp.code}: ${detail.ifBlank { "Sarvam TTS error" }}")
            }

            if (contentType.startsWith("audio/") || contentType == "application/octet-stream") {
                val rawAudio = resp.body?.bytes() ?: throw IOException("Empty Sarvam audio stream")
                if (rawAudio.isEmpty()) throw IOException("Empty Sarvam audio response")
                rawAudio
            } else {
                val json = JSONObject(resp.boundedJsonText(4L * 1024L * 1024L))
                val audios = json.optJSONArray("audios") ?: throw IOException("Sarvam response missing audio array")
                val b64 = audios.optString(0).trim()
                if (b64.isBlank()) throw IOException("Sarvam returned blank base64 audio")
                Base64.decode(b64, Base64.DEFAULT)
            }
        }
    }
}
