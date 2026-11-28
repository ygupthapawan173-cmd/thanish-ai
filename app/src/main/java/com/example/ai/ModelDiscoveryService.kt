package com.example.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class DiscoveredModelsResult(
    val chatModels: List<String>,
    val liveVoiceModels: List<String>,
    val freeModelsCount: Int,
    val message: String
)

/**
 * Dynamic Real API Model Discovery (Sections 3, 10, 14):
 * - Queries authenticated Google Gemini `/v1beta/models`
 * - Queries authenticated OpenAI `/v1/models`
 * - Filters strictly to Gemini & OpenAI GPT families (removed third-party families excluded)
 * - Never fabricates model availability.
 */
class ModelDiscoveryService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun discoverGeminiModels(apiKey: String): Result<DiscoveredModelsResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("Enter Gemini API key first."))
        try {
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=100")
                .addHeader("x-goog-api-key", apiKey.trim())
                .get()
                .build()

            client.newCall(req).awaitParsed { resp ->
                val raw = resp.boundedJsonText(2L * 1024L * 1024L)
                if (!resp.isSuccessful) {
                    val msg = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull()
                    return@awaitParsed Result.failure(IOException("HTTP ${resp.code}: ${msg ?: "Gemini discovery failed"}"))
                }
                val modelsArr = JSONObject(raw).optJSONArray("models")
                    ?: return@awaitParsed Result.failure(IOException("No models returned by Gemini."))

                val chatList = mutableListOf<String>()
                val liveList = mutableListOf<String>()

                for (i in 0 until minOf(modelsArr.length(), 2_000)) {
                    val m = modelsArr.optJSONObject(i) ?: continue
                    val fullName = m.optString("name", "").removePrefix("models/")
                    if (!fullName.startsWith("gemini-") || !SmartModelPolicy.validId(fullName)) continue
                    val methods = m.optJSONArray("supportedGenerationMethods")
                    var supportsGen = false
                    var supportsBidi = false
                    if (methods != null) {
                        for (j in 0 until methods.length()) {
                            val meth = methods.optString(j)
                            if (meth == "generateContent") supportsGen = true
                            if (meth == "bidiGenerateContent") supportsBidi = true
                        }
                    }
                    if (supportsGen && GeminiCompatibility.isChat(fullName)) {
                        chatList.add(fullName)
                    }
                    if (supportsBidi || fullName.contains("transcribe-live", true) || fullName.contains("-live", true)) {
                        liveList.add(fullName)
                    }
                }

                val sortedChat = chatList.distinct().sortedByDescending { scoreGeminiRecency(it) }
                val sortedLive = liveList.distinct().sortedByDescending { scoreGeminiRecency(it) }
                Result.success(
                    DiscoveredModelsResult(
                        chatModels = sortedChat,
                        liveVoiceModels = sortedLive,
                        freeModelsCount = sortedChat.size,
                        message = "Verified ${sortedChat.size} Gemini Chat & ${sortedLive.size} Live Voice models."
                    )
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun discoverOpenAiModels(apiKey: String, baseUrl: String): Result<DiscoveredModelsResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("Enter OpenAI API key first."))
        try {
            val cleanBase = baseUrl.trim().trimEnd('/').ifEmpty { "https://api.openai.com/v1" }
            val parsed = "$cleanBase/models".toHttpUrlOrNull()
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid OpenAI HTTPS URL."))
            require(parsed.isHttps) { "OpenAI URL must use HTTPS" }

            val req = Request.Builder()
                .url(parsed)
                .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                .get()
                .build()

            client.newCall(req).awaitParsed { resp ->
                val raw = resp.boundedJsonText(2L * 1024L * 1024L)
                if (!resp.isSuccessful) {
                    val msg = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull()
                    return@awaitParsed Result.failure(IOException("HTTP ${resp.code}: ${msg ?: "OpenAI discovery failed"}"))
                }
                val data = JSONObject(raw).optJSONArray("data")
                    ?: return@awaitParsed Result.failure(IOException("No OpenAI model list returned."))
                val chatList = mutableListOf<String>()
                val liveList = mutableListOf<String>()

                for (i in 0 until minOf(data.length(), 2_000)) {
                    val id = data.optJSONObject(i)?.optString("id", "").orEmpty()
                    if (!ModelCatalogPolicy.isSupportedProviderModel(id)) continue
                    val low = id.lowercase()
                    if (low.contains("realtime") || low.contains("gpt-live")) {
                        liveList.add(id)
                    } else if (SmartModelPolicy.eligible(SmartModelPolicy.Model(id), SmartModelPolicy.Task.CHAT)) {
                        chatList.add(id)
                    }
                }
                val sortedChat = chatList.distinct().sortedByDescending { scoreOpenAiRecency(it) }
                val sortedLive = liveList.distinct()
                Result.success(
                    DiscoveredModelsResult(
                        chatModels = sortedChat,
                        liveVoiceModels = sortedLive,
                        freeModelsCount = 0,
                        message = "Verified ${sortedChat.size} OpenAI GPT & ${sortedLive.size} Realtime models."
                    )
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private fun scoreGeminiRecency(id: String): Int {
        var score = 0
        when {
            "gemini-4" in id -> score += 600
            "3.8" in id -> score += 500
            "3.5" in id -> score += 450
            "3.0" in id -> score += 400
            "2.5" in id -> score += 300
        }
        when {
            "carbon" in id -> score += 90
            "neon" in id -> score += 80
            "argon" in id -> score += 70
            "hydrogen" in id -> score += 65
            "pro" in id -> score += 60
            "flash" in id -> score += 50
        }
        return score
    }

    private fun scoreOpenAiRecency(id: String): Int {
        var score = 0
        when {
            "gpt-6.2" in id -> score += 650
            "gpt-6.1" in id -> score += 600
            "gpt-6" in id -> score += 550
            "gpt-5" in id -> score += 450
            "gpt-4o" in id -> score += 350
        }
        when {
            "astra" in id -> score += 90
            "sol" in id -> score += 80
            "terra" in id -> score += 70
            "luna" in id -> score += 60
        }
        return score
    }
}
