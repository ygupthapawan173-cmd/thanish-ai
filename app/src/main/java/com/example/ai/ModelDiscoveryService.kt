package com.example.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class DiscoveredModelsResult(
    val chatModels: List<String>,
    val liveVoiceModels: List<String>,
    val message: String,
    val ttsModels: List<String> = emptyList()
)

/**
 * Dynamic Real API Model Discovery (v18 Supreme):
 * Queries live Google Gemini /v1beta/models and OpenRouter /api/v1/models endpoints.
 */
class ModelDiscoveryService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false)
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
                val raw = resp.boundedJsonText(2L * 1024 * 1024)
                if (!resp.isSuccessful) {
                    return@awaitParsed Result.failure(IOException("HTTP ${resp.code}: Model discovery failed"))
                }
                val modelsArr = JSONObject(raw).optJSONArray("models")
                    ?: return@awaitParsed Result.failure(IOException("No models returned."))

                val chatList = mutableListOf<String>()
                val liveList = mutableListOf<String>()
                val ttsList = mutableListOf<String>()

                for (i in 0 until minOf(modelsArr.length(), 10_000)) {
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
                    if (supportsGen && GeminiCompatibility.isTts(fullName)) ttsList.add(fullName)
                    if (supportsBidi && !GeminiCompatibility.isTts(fullName)) {
                        liveList.add(fullName)
                    }
                }

                val sortedChat = chatList.distinct().sortedByDescending { scoreModelRecency(it) }
                val sortedLive = liveList.distinct().sortedByDescending { scoreModelRecency(it) }
                Result.success(
                    DiscoveredModelsResult(
                        chatModels = sortedChat,
                        liveVoiceModels = sortedLive,
                        message = "Discovered ${sortedChat.size} Chat, ${sortedLive.size} Live, ${ttsList.size} audio-only TTS models.",
                        ttsModels = ttsList.distinct().sortedByDescending { scoreModelRecency(it) }
                    )
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun discoverOpenRouterModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder().url("https://openrouter.ai/api/v1/models").get()
            if (apiKey.isNotBlank()) builder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
            client.newCall(builder.build()).awaitParsed { resp ->
                val raw = resp.boundedJsonText(2L * 1024 * 1024)
                if (!resp.isSuccessful) return@awaitParsed Result.failure(IOException("HTTP ${resp.code}: OpenRouter models failed"))
                val data = JSONObject(raw).optJSONArray("data") ?: return@awaitParsed Result.failure(IOException("Empty OpenRouter list"))
                val out = mutableListOf<String>()
                for (i in 0 until minOf(data.length(), 10_000)) {
                    val id = data.optJSONObject(i)?.optString("id", "") ?: continue
                    if (!SmartModelPolicy.validId(id)) continue
                    if (id.contains("gemini") || id.contains("claude") || id.contains("gpt-4") || id.contains("llama-3.3") || id.contains("deepseek")) {
                        out.add(id)
                    }
                }
                Result.success(out.distinct().take(25))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private fun scoreModelRecency(id: String): Int {
        var score = 0
        if (id.contains("3.8")) score += 500
        else if (id.contains("3.5")) score += 450
        else if (id.contains("3.0")) score += 400
        else if (id.contains("2.5")) score += 300
        else if (id.contains("2.0")) score += 200
        if (id.contains("flash")) score += 60
        if (id.contains("pro")) score += 40
        if (id.contains("lite")) score -= 10
        return score
    }
}
