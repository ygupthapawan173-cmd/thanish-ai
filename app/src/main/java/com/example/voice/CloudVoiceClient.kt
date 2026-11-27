package com.example.voice

import com.example.ai.awaitParsed
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

internal object CloudVoiceClient {
    private val client = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(4, 5, TimeUnit.MINUTES))
        .followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).connectTimeout(8, TimeUnit.SECONDS).callTimeout(18, TimeUnit.SECONDS).build()

    suspend fun synthesize(
        key: String,
        text: String,
        choice: CloudVoicePolicy.Choice,
        baseUrl: String = CloudVoicePolicy.BASE
    ): ByteArray {
        require(key.isNotBlank() && text.length in 1..CloudVoicePolicy.MAX_TEXT)
        val cleanBase = baseUrl.trim().trimEnd('/').ifBlank { CloudVoicePolicy.BASE }
        val speechUrl = if (cleanBase == CloudVoicePolicy.BASE) CloudVoicePolicy.BASE + "/audio/speech" else "$cleanBase/audio/speech"
        val payload = JSONObject().put("model", choice.model).put("input", text).put("voice", choice.voice).put("response_format", "mp3")
        val request = Request.Builder().url(speechUrl).header("Authorization", "Bearer ${key.trim()}")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        return client.newCall(request).awaitParsed { r ->
            if (!r.isSuccessful) {
                val rawErr = runCatching { r.peekBody(16_384L).string() }.getOrDefault("")
                val providerMsg = runCatching {
                    val obj = JSONObject(rawErr)
                    obj.optJSONObject("error")?.optString("message").orEmpty().ifBlank { obj.optString("message", "") }
                }.getOrDefault("")
                val detail = providerMsg.take(120).ifBlank { r.message }
                throw IOException("Cloud speech HTTP ${r.code}: $detail")
            }
            if (!CloudVoicePolicy.audioType(r.header("Content-Type"))) throw IOException("Cloud speech did not return audio")
            val body = r.body ?: throw IOException("Empty audio response")
            if (body.contentLength() > CloudVoicePolicy.MAX_AUDIO) throw IOException("Audio exceeds size limit")
            val out = ByteArrayOutputStream()
            body.byteStream().use { stream ->
                val b = ByteArray(16384)
                while (true) {
                    val n = stream.read(b)
                    if (n < 0) break
                    if (out.size() + n > CloudVoicePolicy.MAX_AUDIO) throw IOException("Audio exceeds size limit")
                    out.write(b, 0, n)
                }
            }
            out.toByteArray().also { if (it.isEmpty()) throw IOException("Empty audio response") }
        }
    }
}
