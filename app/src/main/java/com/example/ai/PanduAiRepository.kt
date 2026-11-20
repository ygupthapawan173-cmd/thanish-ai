package com.example.ai

import android.content.Context
import android.util.Log
import com.example.data.ChatMessageEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "PanduAiRepository"
private const val AI_TIMEOUT_MS = 45_000L

private val PANDU_SYSTEM_INSTRUCTION = """
    You are "Pandu Babu – Jarvis Edition" (పాండు బాబు – జార్విస్ ఎడిషన్), a smart, friendly Telugu AI voice assistant.
    Address the user respectfully as "బాస్" or "గురూ". Be helpful, warm, concise, and natural.
    Reply primarily in everyday Telugu using Telugu script. Understand Telugu, English, and Tenglish.
    Keep casual answers short and voice-friendly. Use clear structure for detailed requests.
    Never claim to have performed a phone action unless the app confirms it succeeded.
""".trimIndent()

sealed interface AiStreamEvent {
    data class Chunk(val accumulatedText: String) : AiStreamEvent
    data class Completed(val fullText: String) : AiStreamEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class PanduAiRepository(context: Context) {

    private val appContext = context.applicationContext
    private val keyStore = EncryptedApiKeyStore(appContext)

    private val firebaseModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.65f
                topP = 0.9f
                maxOutputTokens = 700
            },
            systemInstruction = content { text(PANDU_SYSTEM_INSTRUCTION) }
        )
    }

    fun streamReply(
        history: List<ChatMessageEntity>,
        userPrompt: String
    ): Flow<AiStreamEvent> = channelFlow {
        val configured = AiProvider.values().filter { keyStore.hasApiKey(it) }
        if (configured.isEmpty()) {
            firebaseFallback(history, userPrompt).collect { send(it) }
            return@channelFlow
        }

        val selected = keyStore.getActiveProvider()
        val orderedProviders = buildList {
            if (selected in configured) add(selected)
            configured.filterNot { it == selected }.forEach(::add)
        }
        var lastError: Throwable? = null

        for (provider in orderedProviders) {
            val apiKey = keyStore.getApiKey(provider) ?: continue
            val accumulated = StringBuilder()
            try {
                withTimeout(AI_TIMEOUT_MS) {
                    providerStream(provider, apiKey, keyStore.getModel(provider), history, userPrompt)
                        .collect { piece ->
                            accumulated.append(piece)
                            send(AiStreamEvent.Chunk(accumulated.toString()))
                        }
                }
                val finalText = accumulated.toString().trim()
                if (finalText.isBlank()) {
                    throw IllegalStateException("${provider.title} returned an empty response.")
                }
                send(AiStreamEvent.Completed(finalText))
                return@channelFlow
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "${provider.name} timed out; checking configured fallback providers", e)
                lastError = e
                // Do not splice two providers' answers together after visible text has streamed.
                if (accumulated.isNotEmpty()) throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "${provider.name} failed; checking configured fallback providers", e)
                lastError = e
                // Do not splice two providers' answers together after visible text has streamed.
                if (accumulated.isNotEmpty()) throw e
            }
        }

        val detail = lastError?.localizedMessage?.take(240)
            ?: "No configured AI provider could complete the request."
        throw PanduAiException("బాస్, AI కనెక్షన్‌లో సమస్య వచ్చింది: $detail", lastError)
    }

    suspend fun testProvider(provider: AiProvider, apiKey: String, model: String): String {
        return withTimeout(20_000L) {
            providerStream(
                provider = provider,
                apiKey = apiKey.trim(),
                model = model.trim().ifBlank { provider.defaultModel },
                history = emptyList(),
                userPrompt = "Reply with only the word OK."
            ).first().let { "Connection successful. First response received." }
        }
    }

    private fun providerStream(
        provider: AiProvider,
        apiKey: String,
        model: String,
        history: List<ChatMessageEntity>,
        userPrompt: String
    ): Flow<String> = channelFlow {
        val connection = (URL(buildEndpoint(provider, model)).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 35_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "text/event-stream")
            if (provider.isGemini) {
                connection.setRequestProperty("x-goog-api-key", apiKey)
            } else {
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                if (provider == AiProvider.OPENROUTER) {
                    connection.setRequestProperty("HTTP-Referer", "https://github.com/ygupthapawan173-cmd/thanish-ai")
                    connection.setRequestProperty("X-Title", "Pandu Babu Jarvis")
                }
            }

            val requestBody = if (provider.isGemini) {
                buildGeminiRequest(history, userPrompt)
            } else {
                buildOpenAiCompatibleRequest(history, userPrompt, model)
            }
            withContext(Dispatchers.IO) {
                connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                if (status !in 200..299) {
                    val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val message = runCatching {
                        JSONObject(errorText).optJSONObject("error")?.optString("message")
                    }.getOrNull()?.takeIf { !it.isNullOrBlank() } ?: errorText.take(260)
                    throw IllegalStateException("${provider.title} API error ($status): ${message.ifBlank { "Request rejected" }}")
                }

                connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        if (line.startsWith("data:")) {
                            val payload = line.removePrefix("data:").trim()
                            if (payload.isNotEmpty() && payload != "[DONE]") {
                                val piece = parseStreamPiece(provider, payload)
                                if (piece.isNotEmpty()) trySend(piece)
                            }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun buildEndpoint(provider: AiProvider, model: String): String =
        if (provider.isGemini) {
            "${provider.endpoint}/${model.ifBlank { provider.defaultModel }}:streamGenerateContent?alt=sse"
        } else provider.endpoint

    private fun buildGeminiRequest(
        history: List<ChatMessageEntity>,
        userPrompt: String
    ): String {
        val contents = JSONArray()
        val turns = mutableListOf<Pair<String, String>>()
        history.filter { it.text.isNotBlank() }.takeLast(20).forEach { message ->
            val role = if (message.role == ChatMessageEntity.ROLE_USER) "user" else "model"
            val text = message.text.trim()
            if (turns.lastOrNull()?.first == role) {
                val previous = turns.removeAt(turns.lastIndex)
                turns += role to "${previous.second}\n$text"
            } else {
                turns += role to text
            }
        }
        if (turns.lastOrNull()?.first == "user") turns.removeAt(turns.lastIndex)
        (turns + ("user" to userPrompt.trim())).forEach { (role, text) ->
            contents.put(JSONObject().put("role", role).put(
                "parts", JSONArray().put(JSONObject().put("text", text))
            ))
        }

        return JSONObject()
            .put("systemInstruction", JSONObject().put(
                "parts", JSONArray().put(JSONObject().put("text", PANDU_SYSTEM_INSTRUCTION))
            ))
            .put("contents", contents)
            .put("generationConfig", JSONObject()
                .put("temperature", 0.65)
                .put("topP", 0.9)
                .put("maxOutputTokens", 550))
            .toString()
    }

    private fun buildOpenAiCompatibleRequest(
        history: List<ChatMessageEntity>,
        userPrompt: String,
        model: String
    ): String {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", PANDU_SYSTEM_INSTRUCTION))
        history.filter { it.text.isNotBlank() }.takeLast(20).forEach { message ->
            val role = if (message.role == ChatMessageEntity.ROLE_USER) "user" else "assistant"
            messages.put(JSONObject().put("role", role).put("content", message.text.trim()))
        }
        messages.put(JSONObject().put("role", "user").put("content", userPrompt.trim()))
        return JSONObject()
            .put("model", model.ifBlank { "openrouter/free" })
            .put("messages", messages)
            .put("stream", true)
            .put("temperature", 0.65)
            .put("max_tokens", 550)
            .toString()
    }

    private fun parseStreamPiece(provider: AiProvider, payload: String): String {
        val json = JSONObject(payload)
        if (provider.isGemini) {
            val candidates = json.optJSONArray("candidates") ?: return ""
            val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                ?: return ""
            return buildString {
                for (index in 0 until parts.length()) {
                    parts.optJSONObject(index)?.optString("text")?.let(::append)
                }
            }
        }
        val choices = json.optJSONArray("choices") ?: return ""
        val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return ""
        val value = delta.opt("content")
        return when (value) {
            is String -> value
            is JSONArray -> buildString {
                for (index in 0 until value.length()) {
                    val item = value.optJSONObject(index)
                    if (item != null) append(item.optString("text"))
                }
            }
            else -> ""
        }
    }

    private fun firebaseFallback(
        history: List<ChatMessageEntity>,
        userPrompt: String
    ): Flow<AiStreamEvent> = channelFlow {
        val chatHistory = normalizeChatHistory(history)
        val chat = firebaseModel.startChat(history = chatHistory)
        val accumulated = StringBuilder()
        try {
            withTimeout(AI_TIMEOUT_MS) {
                chat.sendMessageStream(userPrompt).collect { chunk ->
                    val text = chunk.text.orEmpty()
                    if (text.isNotEmpty()) {
                        accumulated.append(text)
                        send(AiStreamEvent.Chunk(accumulated.toString()))
                    }
                }
            }
            val result = accumulated.toString().trim()
            if (result.isBlank()) throw IllegalStateException("Gemini returned an empty response.")
            send(AiStreamEvent.Completed(result))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI fallback failed", e)
            throw PanduAiException(
                e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Firebase AI connection failed.",
                e
            )
        }
    }

    companion object {
        internal fun normalizeChatHistory(history: List<ChatMessageEntity>): List<Content> {
            val recent = history.filter { it.text.isNotBlank() }.takeLast(24)
            val alternating = mutableListOf<Pair<String, String>>()
            for (msg in recent) {
                val role = if (msg.role == ChatMessageEntity.ROLE_USER) "user" else "model"
                val text = msg.text.trim()
                if (alternating.isEmpty()) {
                    if (role == "user") alternating += role to text
                } else {
                    val last = alternating.last()
                    if (last.first == role) alternating[alternating.lastIndex] = role to "${last.second}\n$text"
                    else alternating += role to text
                }
            }
            if (alternating.lastOrNull()?.first == "user") alternating.removeAt(alternating.lastIndex)
            return alternating.map { (role, text) -> content(role) { text(text) } }
        }
    }
}

class PanduAiException(message: String, cause: Throwable? = null) : Exception(message, cause)
