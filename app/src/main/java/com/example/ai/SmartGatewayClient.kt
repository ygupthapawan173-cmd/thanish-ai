package com.example.ai

import com.example.data.OmegaSettings
import com.example.data.ApiQuotaTracker
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class SmartGatewayClient(private val networkAllowed: () -> Boolean = { true }) {
    data class Reply(val text: String, val model: String)
    private data class Catalog(val url: String, val key: String, val expires: Long, val models: List<SmartModelPolicy.Model>)
    @Volatile private var cache: Catalog? = null
    private val cooldowns = ModelCooldownLedger()
    private fun now() = android.os.SystemClock.elapsedRealtime()
    private fun cooldownScope(s: OmegaSettings): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(s.effectiveOpenRouterKey().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
        return "${endpoint(s, "/models")}|$digest"
    }
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(50, TimeUnit.SECONDS).build()
    private fun endpoint(s: OmegaSettings, suffix: String): String {
        val url = s.gatewayBaseUrl.trim().trimEnd('/').toHttpUrlOrNull() ?: throw IOException("Invalid gateway base URL")
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null) throw IOException("Gateway must be an HTTPS base URL without credentials, query or fragment")
        if (url.encodedPath.endsWith("/chat/completions") || url.encodedPath.endsWith("/models")) throw IOException("Enter the API base URL, not a request endpoint")
        return url.toString().trimEnd('/') + suffix
    }
    private fun request(s: OmegaSettings, suffix: String): Request.Builder {
        check(networkAllowed()) { "Cloud AI requests are disabled" }
        val key = s.effectiveOpenRouterKey()
        if (key.isBlank()) throw IOException("Enter and save the Smart API key")
        return Request.Builder().url(endpoint(s, suffix)).header("Authorization", "Bearer $key")
    }
    private fun price(o: JSONObject?, field: String): Double? = o?.optString(field)?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    private fun modalities(o: JSONObject?, field: String): List<String>? = o?.optJSONArray(field)?.let { a -> (0 until a.length()).map { a.optString(it).lowercase() } }
    suspend fun discover(s: OmegaSettings, refresh: Boolean = false): List<SmartModelPolicy.Model> {
        check(networkAllowed()) { "Model discovery is disabled" }
        val url = endpoint(s, "/models")
        val key = cooldownScope(s)
        if (!refresh) cache?.let { if (it.url == url && it.key == key && it.expires > now()) return it.models }
        val models = client.newCall(request(s, "/models").get().build()).awaitParsed { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}: gateway model discovery failed")
            val data = JSONObject(r.boundedJsonText()).optJSONArray("data") ?: throw IOException("Gateway did not return an OpenAI-style data model list")
            (0 until minOf(data.length(), 10_000)).mapNotNull { i ->
                val m = data.optJSONObject(i) ?: return@mapNotNull null
                val id = m.optString("id").takeIf { SmartModelPolicy.validId(it) } ?: return@mapNotNull null
                val arch = m.optJSONObject("architecture")
                val input = modalities(arch, "input_modalities") ?: modalities(m, "input_modalities")
                val output = modalities(arch, "output_modalities") ?: modalities(m, "output_modalities")
                val pricing = m.optJSONObject("pricing")
                SmartModelPolicy.Model(id, input?.contains("image"), output?.contains("text"), price(pricing, "prompt"), price(pricing, "completion"))
            }.distinctBy { it.id }
        }
        cache = Catalog(url, key, now() + 300_000L, models)
        return models
    }
    private suspend fun verifyBudget(s: OmegaSettings) {
        if (s.providerBudgetUsd == 0.0) return
        if (endpoint(s, "/key") != "https://openrouter.ai/api/v1/key") throw IOException("Budget protection requires the official OpenRouter gateway")
        client.newCall(request(s, "/key").get().build()).awaitParsed { response ->
            if (!response.isSuccessful) throw IOException("Cannot verify provider budget: HTTP ${response.code}")
            val data = JSONObject(response.boundedJsonText()).optJSONObject("data") ?: throw IOException("Provider budget metadata missing")
            val reason = ProviderBudgetPolicy.rejection(s.providerBudgetUsd,
                data.optDouble("limit").takeIf { it.isFinite() }, data.optDouble("limit_remaining").takeIf { it.isFinite() },
                data.has("limit_reset") && data.isNull("limit_reset"), data.optBoolean("include_byok_in_limit", false),
                data.has("is_management_key") && !data.optBoolean("is_management_key", true))
            if (reason != null) throw IOException(reason)
        }
    }
    suspend fun answer(s: OmegaSettings, question: String, system: String, previous: List<Pair<String,String>> = emptyList(), image: String? = null, preferredBrain: AiBrainFamily? = null, onPartial: ((String) -> Unit)? = null): Reply {
        val task = SmartModelPolicy.task(question, !image.isNullOrBlank())
        if (s.smartMode == SmartModelPolicy.Mode.FREE && endpoint(s, "/models") != "https://openrouter.ai/api/v1/models") throw IOException("Strict Free mode is supported only on OpenRouter; no request sent")
        val available = discover(s, refresh = s.smartMode == SmartModelPolicy.Mode.FREE)
        val rankedAll = SmartModelPolicy.rank(available, task, s.smartMode, s.openRouterModel, s.manualVisionConfirmed)
        val ranked = if (preferredBrain != null) {
            val matching = rankedAll.filter { MultiBrainPolicy.classifyModelFamily(it.id) == preferredBrain }
            if (matching.isNotEmpty()) matching + rankedAll.filterNot { it in matching } else rankedAll
        } else rankedAll
        val scope = cooldownScope(s)
        val checkedAt = now()
        val candidates = ranked.filter { !cooldowns.blocked(scope, it.id, checkedAt) }.take(3)
        if (ranked.isNotEmpty() && candidates.isEmpty()) throw IOException("Selected eligible models are temporarily cooling down; retry later")
        if (candidates.isEmpty() && s.smartMode == SmartModelPolicy.Mode.FREE) throw IOException("No verified zero-price free model available; no paid fallback")
        if (candidates.isEmpty()) throw IOException(if (task == SmartModelPolicy.Task.VISION) "No model advertises image input; choose a verified vision model in Manual mode and confirm image support, or use Gemini Direct" else "No eligible discovered model; check provider and model selection")
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", "$system\nYou are a text assistant; never claim to have executed device actions."))
        previous.filter { it.first in setOf("user", "assistant") }.takeLast(12).forEach { (role, text) -> messages.put(JSONObject().put("role", role).put("content", text.take(4000))) }
        val content: Any = if (image.isNullOrBlank()) question else JSONArray().put(JSONObject().put("type", "text").put("text", question)).put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$image")))
        messages.put(JSONObject().put("role", "user").put("content", content))
        var error = "No response"
        for (m in candidates) {
            if (cooldowns.blocked(scope, m.id, now())) continue
            verifyBudget(s)
            val payload = JSONObject().put("model", m.id).put("messages", messages)
            if (s.providerBudgetUsd > 0) payload.put("max_tokens", 2048)
            if (onPartial != null) payload.put("stream", true)
            // Bounded automatic fallback only for explicit retryable HTTP responses; don't replay ambiguous timeouts.
            val started = now()
            val reply = try { client.newCall(request(s, "/chat/completions").post(payload.toString().toRequestBody("application/json".toMediaType())).build()).awaitParsed { r ->
                if (!r.isSuccessful) {
                    error = "HTTP ${r.code}: ${m.id} failed"
                    val model400 = r.code == 400 && GatewayRetryPolicy.modelSpecific400(runCatching { JSONObject(r.peekBody(8192L).string()).optJSONObject("error")?.optString("code").orEmpty() }.getOrDefault(""))
                    if (!SmartModelPolicy.mayTryAnother(r.code) && !model400) throw IOException(error)
                    val cooldown = GatewayRetryPolicy.cooldownMs(r.code, r.header("Retry-After"))
                        .let { if (it > 0L) it else if (r.code in 500..504) 90_000L else 0L }
                    if (cooldown > 0) cooldowns.mark(scope, m.id, now(), cooldown)
                    MultiBrainPolicy.classifyModelFamily(m.id)?.let { fam ->
                        PerBrainQuotaTracker.recordFailure(fam, if (cooldown > 0) System.currentTimeMillis() + cooldown else 0L)
                    }
                    if (r.code == 404 || model400) cache = null
                    ApiQuotaTracker.recordFailure()
                    null
                } else {
                    val streamed = if (onPartial != null && r.header("Content-Type").orEmpty().contains("text/event-stream", true)) r.readGatewayStream(m.id, onPartial) else null
                    val json = if (streamed == null) JSONObject(r.boundedJsonText()) else null
                    val text = streamed?.text ?: json?.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.trim().orEmpty()
                    val actualModel = streamed?.model ?: json?.optString("model", m.id)?.takeIf { SmartModelPolicy.validId(it) } ?: m.id
                    val usage = streamed?.usage ?: json?.optJSONObject("usage")
                    val prompt = usage?.optLong("prompt_tokens", -1)?.takeIf { it >= 0 }
                    val output = usage?.optLong("completion_tokens", -1)?.takeIf { it >= 0 }
                    val knownUnits = endpoint(s, "/models") == "https://openrouter.ai/api/v1/models"
                    val cost = if (knownUnits) usage?.optDouble("cost")?.takeIf { it.isFinite() && it >= 0 } ?: (if (actualModel == m.id && prompt != null && output != null && m.promptPrice != null && m.completionPrice != null) prompt * m.promptPrice + output * m.completionPrice else null) else null
                    if (text.isBlank() || text == "null") throw IOException("Gateway returned no text")
                    ApiQuotaTracker.recordSuccess(actualModel, prompt, output, (now() - started).coerceAtLeast(0L), cost?.takeIf { it.isFinite() && it >= 0 })
                    MultiBrainPolicy.classifyModelFamily(actualModel)?.let { fam ->
                        PerBrainQuotaTracker.recordSuccess(fam, actualModel, prompt, output, cost)
                    }
                    Reply(text, actualModel)
                }
            }
            } catch (e: IOException) { ApiQuotaTracker.recordFailure(); throw e }
            if (reply != null) return reply
        }
        throw IOException(error)
    }
}
