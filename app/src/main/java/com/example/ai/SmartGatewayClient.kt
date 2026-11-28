package com.example.ai

import com.example.data.ApiQuotaTracker
import com.example.data.IntelligenceMode
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI GPT Client (Sections 3, 5, 10, 16):
 * - Communicates strictly with OpenAI / authorized GPT HTTPS endpoints.
 * - Excludes removed model families (Claude, DeepSeek, Qwen, Grok, Kimi, Mistral, Llama, GPT-OSS).
 * - Handles HTTP 400, 401/403, 404 model recovery, 429 cooldowns, and network retries cleanly.
 */
internal class SmartGatewayClient(private val networkAllowed: () -> Boolean = { true }) {
    data class Reply(val text: String, val model: String, val httpCode: Int = 200)

    private data class Catalog(val url: String, val keyHash: String, val expires: Long, val models: List<SmartModelPolicy.Model>)
    @Volatile private var cache: Catalog? = null
    private val cooldowns = ModelCooldownLedger()

    private fun now() = android.os.SystemClock.elapsedRealtime()

    private fun keyScope(url: String, apiKey: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(apiKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return "$url|$digest"
    }

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(55, TimeUnit.SECONDS)
        .build()

    private fun endpoint(baseUrl: String, suffix: String): String {
        val clean = baseUrl.trim().trimEnd('/').ifEmpty { "https://api.openai.com/v1" }
        val url = clean.toHttpUrlOrNull() ?: throw IOException("Invalid OpenAI HTTPS base URL")
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) {
            throw IOException("API URL must use HTTPS cleanly")
        }
        return url.toString().trimEnd('/') + suffix
    }

    suspend fun discover(baseUrl: String, apiKey: String, refresh: Boolean = false): List<SmartModelPolicy.Model> {
        check(networkAllowed()) { "Network requests unavailable" }
        require(apiKey.isNotBlank()) { "OpenAI API key missing" }
        val url = endpoint(baseUrl, "/models")
        val scope = keyScope(url, apiKey)
        if (!refresh) cache?.let { if (it.url == url && it.keyHash == scope && it.expires > now()) return it.models }

        val req = Request.Builder().url(url).header("Authorization", "Bearer ${apiKey.trim()}").get().build()
        val models = client.newCall(req).awaitParsed { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}: OpenAI model discovery failed")
            val data = JSONObject(r.boundedJsonText()).optJSONArray("data")
                ?: throw IOException("Provider did not return model list")
            (0 until minOf(data.length(), 2_000)).mapNotNull { i ->
                val m = data.optJSONObject(i) ?: return@mapNotNull null
                val id = m.optString("id").takeIf { SmartModelPolicy.validId(it) && ModelCatalogPolicy.isSupportedProviderModel(it) }
                    ?: return@mapNotNull null
                SmartModelPolicy.Model(id = id, imageInput = true, textOutput = true)
            }.distinctBy { it.id }
        }
        cache = Catalog(url, scope, now() + 300_000L, models)
        return models
    }

    suspend fun answerOpenAi(
        baseUrl: String,
        apiKey: String,
        preferredModel: String,
        intelligenceMode: IntelligenceMode,
        freePriority: Boolean,
        question: String,
        system: String,
        previous: List<Pair<String, String>> = emptyList(),
        imageBase64: String? = null,
        onPartial: ((String) -> Unit)? = null
    ): Reply {
        check(networkAllowed()) { "Internet unavailable" }
        if (apiKey.isBlank()) throw IOException("OpenAI API key missing")

        val task = SmartModelPolicy.task(question, !imageBase64.isNullOrBlank())
        val discovered = runCatching { discover(baseUrl, apiKey, refresh = false) }.getOrDefault(emptyList())
        val url = endpoint(baseUrl, "/chat/completions")
        val scope = keyScope(url, apiKey)

        val candidates = if (discovered.isNotEmpty()) {
            SmartModelPolicy.rank(discovered, task, intelligenceMode, preferredModel, freePriority)
                .filter { !cooldowns.blocked(scope, it.id, now()) }
                .take(4)
                .map { it.id }
        } else {
            listOf(preferredModel.ifBlank { "gpt-6.2-sol" }, "gpt-6.2-sol", "gpt-5-mini", "gpt-4o-mini").distinct()
        }

        val messages = JSONArray().put(
            JSONObject().put("role", "system").put("content", system)
        )
        previous.filter { it.first in setOf("user", "assistant") }.takeLast(8).forEach { (role, text) ->
            messages.put(JSONObject().put("role", role).put("content", text.take(4000)))
        }
        val content: Any = if (imageBase64.isNullOrBlank()) {
            question
        } else {
            JSONArray()
                .put(JSONObject().put("type", "text").put("text", question))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageBase64")))
        }
        messages.put(JSONObject().put("role", "user").put("content", content))

        var lastError = "OpenAI request failed"
        for (modelId in candidates) {
            if (cooldowns.blocked(scope, modelId, now())) continue
            val payload = JSONObject()
                .put("model", modelId)
                .put("messages", messages)
            if (onPartial != null) payload.put("stream", true)

            val started = now()
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val reply = client.newCall(req).awaitParsed { r ->
                if (!r.isSuccessful) {
                    val code = r.code
                    val errMsg = runCatching {
                        JSONObject(r.peekBody(8192L).string()).optJSONObject("error")?.optString("message").orEmpty()
                    }.getOrDefault("")
                    lastError = "HTTP $code ($modelId): ${errMsg.ifBlank { "Request failed" }}"
                    ApiQuotaTracker.recordFailure(modelId)

                    // HTTP 401/403 must stop immediately on this slot (no retry loop to bypass auth)
                    if (code == 401 || code == 403) throw IOException("HTTP $code Invalid API key or access denied")
                    if (!SmartModelPolicy.mayTryAnother(code)) throw IOException(lastError)

                    val cd = GatewayRetryPolicy.cooldownMs(code, r.header("Retry-After"))
                    if (cd > 0) cooldowns.mark(scope, modelId, now(), cd)
                    if (code == 404) cache = null
                    null
                } else {
                    val streamed = if (onPartial != null && r.header("Content-Type").orEmpty().contains("text/event-stream", true)) {
                        r.readGatewayStream(modelId, onPartial)
                    } else null
                    val json = if (streamed == null) JSONObject(r.boundedJsonText()) else null
                    val text = streamed?.text
                        ?: json?.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.trim().orEmpty()
                    val actualModel = streamed?.model ?: json?.optString("model", modelId) ?: modelId
                    val usage = streamed?.usage ?: json?.optJSONObject("usage")
                    val promptTok = usage?.optLong("prompt_tokens", -1)?.takeIf { it >= 0 }
                    val outTok = usage?.optLong("completion_tokens", -1)?.takeIf { it >= 0 }
                    if (text.isBlank()) throw IOException("OpenAI returned empty text")
                    ApiQuotaTracker.recordSuccess(actualModel, promptTok, outTok, (now() - started).coerceAtLeast(0L))
                    Reply(text, actualModel, r.code)
                }
            }
            if (reply != null) return reply
        }
        throw IOException(lastError)
    }
}
