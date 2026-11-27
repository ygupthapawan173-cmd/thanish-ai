package com.example.voice

import android.os.SystemClock
import android.util.Base64
import com.example.ai.ModelCooldownLedger
import com.example.ai.awaitParsed
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class GeminiSpeechResult(
    val wavBytes: ByteArray,
    val usedModel: String
)

internal class GeminiSpeechHttpException(
    val httpCode: Int,
    message: String
) : IOException(message)

internal object GeminiSpeechClient {
    private const val MAX_JSON = 12L * 1024 * 1024
    private val modelCooldowns = ModelCooldownLedger(limit = 64)
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(8, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    suspend fun synthesize(key: String, text: String, speaker: String): ByteArray =
        synthesizeWithAutoSwitch(key, text, speaker, listOf(GeminiSpeechPolicy.MODEL)).wavBytes

    suspend fun synthesizeWithAutoSwitch(
        key: String,
        text: String,
        speaker: String,
        candidateModels: List<String>
    ): GeminiSpeechResult {
        require(key.isNotBlank() && text.length in 1..CloudVoicePolicy.MAX_TEXT && speaker in GeminiSpeechPolicy.speakers)
        VoiceResponseCache.get(speaker, text)?.let { cached ->
            return GeminiSpeechResult(wavBytes = cached, usedModel = "voice-cache")
        }
        val candidates = GeminiSpeechPolicy.candidateModels(candidateModels)
        val scope = "gemini_tts_${key.trim().takeLast(6)}"
        val now = SystemClock.elapsedRealtime()
        val eligible = candidates.filter { !modelCooldowns.blocked(scope, it, now) }
        val toTry = eligible.ifEmpty { candidates }
        var lastError: Exception? = null

        for (model in toTry) {
            try {
                val wav = synthesizeForModel(key.trim(), text, speaker, model)
                VoiceResponseCache.put(speaker, text, wav)
                return GeminiSpeechResult(wavBytes = wav, usedModel = model)
            } catch (e: GeminiSpeechHttpException) {
                lastError = e
                if (GeminiSpeechPolicy.isModelSwitchableError(e.httpCode, e.message)) {
                    modelCooldowns.mark(scope, model, SystemClock.elapsedRealtime(), GeminiSpeechPolicy.cooldownFor(e.httpCode, e.message))
                    continue
                }
                throw e
            } catch (e: IOException) {
                lastError = e
                continue
            }
        }
        throw lastError ?: IOException("All Gemini TTS models unavailable")
    }

    private suspend fun synthesizeForModel(
        key: String,
        text: String,
        speaker: String,
        model: String
    ): ByteArray {
        val voice = JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", speaker))
        val generation = JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put("speechConfig", JSONObject().put("voiceConfig", voice))
        val payload = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "Read the following text aloud naturally, exactly as written:\n$text")))))
            .put("generationConfig", generation)
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return client.newCall(request).awaitParsed { response ->
            if (!response.isSuccessful) {
                throw GeminiSpeechHttpException(response.code, "Gemini speech HTTP ${response.code} ($model)")
            }
            val body = response.body ?: throw IOException("Missing speech body")
            val source = body.source()
            if (body.contentLength() > MAX_JSON || source.request(MAX_JSON + 1)) throw IOException("Speech response exceeds limit")
            val candidate = JSONObject(source.readUtf8()).optJSONArray("candidates")?.optJSONObject(0)
                ?: throw IOException("Missing speech candidate")
            if (candidate.optString("finishReason") != "STOP") throw IOException("Incomplete speech")
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                ?: throw IOException("Missing speech parts")
            val out = java.io.ByteArrayOutputStream()
            for (i in 0 until parts.length()) {
                val data = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                val mime = data.optString("mimeType").lowercase().replace(" ", "")
                if (!mime.startsWith("audio/l16") && !mime.startsWith("audio/pcm")) throw IOException("Unexpected speech format")
                if (mime.contains("rate=") && !mime.contains("rate=24000")) throw IOException("Unsupported sample rate")
                val encoded = data.optString("data")
                if (encoded.length > MAX_JSON) throw IOException("Audio exceeds limit")
                val chunk = Base64.decode(encoded, Base64.DEFAULT)
                if (out.size() + chunk.size > GeminiSpeechPolicy.MAX_PCM) throw IOException("Audio exceeds limit")
                out.write(chunk)
            }
            GeminiSpeechPolicy.wav(out.toByteArray())
        }
    }
}
