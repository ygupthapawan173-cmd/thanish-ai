package com.example.ai

import android.util.Log
import com.example.data.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

internal class OpenRouterClient(private val keyStore: OpenRouterKeyStore) {
    fun stream(history: List<ChatMessageEntity>, prompt: String): Flow<AiStreamEvent> = flow {
        val key = keyStore.getKey() ?: throw PanduAiException("OpenRouter API key Settingsలో నమోదు చేయండి.")
        val messages = JSONArray().put(JSONObject()
            .put("role", "system")
            .put("content", "You are THANISH AI, a helpful Telugu-English assistant. Reply naturally in the user's language."))
        history.filter { it.text.isNotBlank() }.takeLast(20).forEach {
            messages.put(JSONObject()
                .put("role", if (it.role == ChatMessageEntity.ROLE_USER) "user" else "assistant")
                .put("content", it.text))
        }
        messages.put(JSONObject().put("role", "user").put("content", prompt))

        val payload = JSONObject()
            .put("model", "openrouter/free")
            .put("messages", messages)
            .put("stream", true)
            .put("temperature", 0.7)
        val connection = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20000
            connection.readTimeout = 90000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("HTTP-Referer", "https://github.com/ygupthapawan173-cmd/thanish-ai")
            connection.setRequestProperty("X-Title", "THANISH AI")
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw PanduAiException("OpenRouter error $status: ${detail.take(400)}")
            }
            val accumulated = StringBuilder()
            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val content = runCatching {
                        JSONObject(data).optJSONArray("choices")
                            ?.optJSONObject(0)?.optJSONObject("delta")?.optString("content")
                    }.getOrNull().orEmpty()
                    if (content.isNotEmpty()) {
                        accumulated.append(content)
                        emit(AiStreamEvent.Chunk(accumulated.toString()))
                    }
                }
            }
            val finalText = accumulated.toString().trim()
            if (finalText.isBlank()) throw PanduAiException("OpenRouter ఖాళీ సమాధానం ఇచ్చింది. మళ్లీ ప్రయత్నించండి.")
            emit(AiStreamEvent.Completed(finalText))
        } catch (e: PanduAiException) {
            throw e
        } catch (e: Exception) {
            Log.e("OpenRouterClient", "Streaming failed", e)
            throw PanduAiException("OpenRouter connection failed: ${e.localizedMessage ?: "unknown error"}", e)
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)
}
