package com.example.voice

import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/** Gemini direct speech generation. Never sends a gateway credential to Google. */
internal object GeminiSpeechOutput {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(22, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    suspend fun synthesize(key: String, text: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", text.take(1500))))))
                .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("AUDIO"))
                    .put("speechConfig", JSONObject().put("voiceConfig", JSONObject()
                        .put("prebuiltVoiceConfig", JSONObject().put("voiceName", "Charon")))))
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash-lite-tts:generateContent")
                .header("x-goog-api-key", key)
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Gemini speech HTTP ${response.code}")
                val json = JSONObject(response.body?.string().orEmpty())
                val parts = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    ?: throw IOException("Gemini returned no speech")
                for (i in 0 until parts.length()) {
                    val inline = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                    val mime = inline.optString("mimeType").lowercase()
                    if (!mime.startsWith("audio/")) continue
                    val encoded = inline.optString("data")
                    if (encoded.length > 16_000_000) throw IOException("Speech response too large")
                    val bytes = Base64.decode(encoded, Base64.DEFAULT)
                    if (bytes.size < 4) continue
                    if (String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF") return@use bytes
                    if (mime.contains("l16") || mime.contains("pcm")) {
                        val rate = Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: 24000
                        return@use wav(bytes, rate)
                    }
                    if (mime.contains("mpeg") || mime.contains("mp3") || mime.contains("wav")) return@use bytes
                }
                throw IOException("Gemini returned no supported audio")
            }
        }.onFailure { if (it is CancellationException) throw it }
    }

    internal fun wav(pcm: ByteArray, rate: Int): ByteArray {
        require(rate in 8000..48000 && pcm.size % 2 == 0)
        val b = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray()).putInt(16)
        b.putShort(1.toShort()).putShort(1.toShort()).putInt(rate).putInt(rate * 2)
        b.putShort(2.toShort()).putShort(16.toShort()).put("data".toByteArray()).putInt(pcm.size).put(pcm)
        return b.array()
    }
}
