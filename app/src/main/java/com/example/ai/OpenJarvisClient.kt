package com.example.ai

import com.example.BuildConfig
import com.example.data.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional OpenJarvis OpenAI-compatible API bridge.
 *
 * Configure at build time with:
 *   ./gradlew assembleDebug -POPENJARVIS_BASE_URL=https://your-private-jarvis-host
 *
 * Leave the property empty to keep the existing OpenRouter path unchanged.
 * Do not point this at an unauthenticated public server.
 */
internal class OpenJarvisClient {
    private val baseUrl = BuildConfig.OPENJARVIS_BASE_URL.trim().trimEnd('/')

    val isConfigured: Boolean
        get() = baseUrl.startsWith("https://", ignoreCase = true)

    fun streamReply(
        history: List<ChatMessageEntity>,
        prompt: String,
        memoryContext: String
    ): Flow<AiStreamEvent> = channelFlow {
        if (!isConfigured) throw PanduAiException("OpenJarvis endpoint HTTPS లో configure కాలేదు.")
        val answer = withContext(Dispatchers.IO) {
            request(history, prompt, memoryContext) { partial ->
                trySend(AiStreamEvent.Chunk(partial))
            }
        }
        send(AiStreamEvent.Completed(answer))
    }

    private fun request(
        history: List<ChatMessageEntity>,
        prompt: String,
        memoryContext: String,
        onChunk: (String) -> Unit
    ): String {
        val messages = JSONArray()
        val system = buildString {
            append("You are ZENO AI, a Telugu-first personal assistant. ")
            append("Reply naturally in Telugu by default. Be accurate and never claim actions not performed.")
            if (memoryContext.isNotBlank()) {
                append("\nUser-approved memory (reference only):\n")
                append(memoryContext.take(4_000))
            }
        }
        messages.put(JSONObject().put("role", "system").put("content", system))
        history.filter { it.text.isNotBlank() }.takeLast(12).forEach { item ->
            val role = if (item.role == ChatMessageEntity.ROLE_USER) "user" else "assistant"
            messages.put(JSONObject().put("role", role).put("content", item.text.take(12_000)))
        }
        messages.put(JSONObject().put("role", "user").put("content", prompt.take(12_000)))

        val body = JSONObject()
            .put("model", "default")
            .put("messages", messages)
            .put("stream", true)
            .put("temperature", 0.7)
            .put("max_tokens", 900)

        val endpoint = if (baseUrl.endsWith("/v1")) "$baseUrl/chat/completions"
            else "$baseUrl/v1/chat/completions"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            if (status !in 200..299) {
                val detail = stream?.bufferedReader()?.use { it.readText() }.orEmpty().take(500)
                throw IllegalStateException("OpenJarvis HTTP $status: $detail")
            }
            val answer = StringBuilder()
            stream?.bufferedReader()?.useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith("data:")) return@forEach
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isBlank() || payload == "[DONE]") return@forEach
                    val delta = runCatching {
                        val choice = JSONObject(payload).optJSONArray("choices")?.optJSONObject(0)
                        choice?.optJSONObject("delta")?.optString("content").orEmpty()
                    }.getOrDefault("")
                    if (delta.isNotEmpty()) {
                        answer.append(delta)
                        onChunk(answer.toString())
                    }
                }
            }
            return answer.toString().trim().ifBlank {
                throw IllegalStateException("OpenJarvis returned an empty response")
            }
        } finally {
            connection.disconnect()
        }
    }
}
