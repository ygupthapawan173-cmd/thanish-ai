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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "PanduAiRepository"
private const val MODEL_NAME = "gemini-3.8-flash"
private const val AI_TIMEOUT_MS = 30_000L
private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 30_000

private val PANDU_SYSTEM_INSTRUCTION = """
    You are Pandu Babu – Jarvis Edition, a smart, warm, witty Telugu voice assistant.
    Address the user respectfully as బాస్ or గురూ. Reply mainly in natural spoken Telugu script,
    even when the user writes English or Tenglish. Keep casual answers short, clear, useful and
    voice-friendly. Use simple English tech terms naturally. Never claim to have performed a
    phone action unless the app confirms it succeeded. Ask before sensitive actions.
""".trimIndent()

sealed interface AiStreamEvent {
    data class Chunk(val accumulatedText: String) : AiStreamEvent
    data class Completed(val fullText: String) : AiStreamEvent
}

class PanduAiRepository(context: Context) {

    private val apiKeyVault = GeminiApiKeyVault(context)

    private val firebaseModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.65f
                topP = 0.9f
                maxOutputTokens = 512
            },
            systemInstruction = content {
                text(PANDU_SYSTEM_INSTRUCTION)
            }
        )
    }

    /**
     * If one or more local key slots are configured, try them in slot order (1 → 4).
     * With no saved keys, keep using the app's existing Firebase AI Logic connection.
     */
    fun streamReply(
        history: List<ChatMessageEntity>,
        userPrompt: String
    ): Flow<AiStreamEvent> = flow {
        val configuredKeys = apiKeyVault.configuredKeys()
        if (configuredKeys.isEmpty()) {
            emitAll(streamWithFirebase(history, userPrompt))
            return@flow
        }

        var lastFailure: Exception? = null
        for ((slot, apiKey) in configuredKeys) {
            try {
                withTimeout(AI_TIMEOUT_MS) {
                    emitAll(streamWithGeminiKey(apiKey, history, userPrompt))
                }
                return@flow
            } catch (e: TimeoutCancellationException) {
                lastFailure = e
                Log.w(TAG, "Gemini API key slot " + slot + " timed out", e)
                emit(AiStreamEvent.Chunk(""))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastFailure = e
                Log.w(TAG, "Gemini API key slot " + slot + " failed; trying next configured slot", e)
                emit(AiStreamEvent.Chunk(""))
            }
        }

        val detail = lastFailure?.localizedMessage?.take(180)
            ?: "No configured key accepted the request"
        throw PanduAiException(
            "Gemini API slots 1–4 failed. Check key, quota, billing and model access. Details: " + detail,
            lastFailure
        )
    }.flowOn(Dispatchers.IO)

    private fun streamWithFirebase(
        history: List<ChatMessageEntity>,
        userPrompt: String
    ): Flow<AiStreamEvent> = flow {
        val chat = firebaseModel.startChat(history = normalizeChatHistory(history))
        val accumulated = StringBuilder()
        try {
            withTimeout(AI_TIMEOUT_MS) {
                chat.sendMessageStream(userPrompt).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                        emit(AiStreamEvent.Chunk(accumulated.toString()))
                    }
                }
            }
            val finalText = accumulated.toString().trim()
            if (finalText.isBlank()) {
                throw IllegalStateException("Gemini returned an empty response.")
            }
            emit(AiStreamEvent.Completed(finalText))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI Logic stream timed out", e)
            throw PanduAiException(
                "Boss, AI response exceeded 30 seconds. Please retry.",
                e
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI Logic stream failed", e)
            val detail = e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Unknown AI error"
            throw PanduAiException("Firebase AI connection failed: " + detail, e)
        }
    }

    private fun streamWithGeminiKey(
        apiKey: String,
        history: List<ChatMessageEntity>,
        userPrompt: String
    ): Flow<AiStreamEvent> = flow {
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                MODEL_NAME + ":streamGenerateContent?alt=sse"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        val accumulated = StringBuilder()
        try {
            val requestBody = buildRequestBody(history, userPrompt)
            connection.outputStream.use { output ->
                output.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val serverMessage = runCatching {
                    JSONObject(errorBody).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                throw IOException(
                    "HTTP " + status +
                        (if (serverMessage.isNotBlank()) ": " + serverMessage else "")
                )
            }

            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.substringAfter("data:").trim()
                    if (data.isBlank() || data == "[DONE]") continue

                    val event = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    val candidates = event.optJSONArray("candidates") ?: continue
                    val parts = candidates.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts") ?: continue

                    for (index in 0 until parts.length()) {
                        val partText = parts.optJSONObject(index)?.optString("text").orEmpty()
                        if (partText.isNotEmpty()) accumulated.append(partText)
                    }
                    if (accumulated.isNotEmpty()) {
                        emit(AiStreamEvent.Chunk(accumulated.toString()))
                    }
                }
            }

            val finalText = accumulated.toString().trim()
            if (finalText.isBlank()) {
                throw IOException("Gemini stream returned no text.")
            }
            emit(AiStreamEvent.Completed(finalText))
        } finally {
            connection.disconnect()
        }
    }

    private fun buildRequestBody(
        history: List<ChatMessageEntity>,
        userPrompt: String
    ): JSONObject {
        val contents = JSONArray()
        normalizedTextHistory(history).forEach { turn ->
            contents.put(
                JSONObject()
                    .put("role", turn.first)
                    .put("parts", JSONArray().put(JSONObject().put("text", turn.second)))
            )
        }
        contents.put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
        )

        return JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", PANDU_SYSTEM_INSTRUCTION))
                )
            )
            .put("contents", contents)
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.65)
                    .put("topP", 0.9)
                    .put("maxOutputTokens", 512)
            )
    }

    private fun normalizedTextHistory(history: List<ChatMessageEntity>): List<Pair<String, String>> {
        val recent = history
            .filter { it.text.isNotBlank() }
            .takeLast(12)

        val alternating = mutableListOf<Pair<String, String>>()
        for (message in recent) {
            val role = if (message.role == ChatMessageEntity.ROLE_USER) "user" else "model"
            val text = message.text.trim()
            if (alternating.isEmpty()) {
                if (role == "user") alternating += role to text
            } else {
                val last = alternating.last()
                if (last.first == role) {
                    alternating[alternating.lastIndex] = role to (last.second + "\n" + text)
                } else {
                    alternating += role to text
                }
            }
        }

        if (alternating.lastOrNull()?.first == "user") {
            alternating.removeAt(alternating.lastIndex)
        }
        return alternating
    }

    internal fun normalizeChatHistory(history: List<ChatMessageEntity>): List<Content> =
        normalizedTextHistory(history).map { (role, text) ->
            content(role) {
                text(text)
            }
        }
}

class PanduAiException(message: String, cause: Throwable? = null) : Exception(message, cause)
