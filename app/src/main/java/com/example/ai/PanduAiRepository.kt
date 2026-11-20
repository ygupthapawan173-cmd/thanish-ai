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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "PanduAiRepository"
private const val MODEL_NAME = "gemini-3.8-flash"
private const val AI_TIMEOUT_MS = 60_000L

private val PANDU_SYSTEM_INSTRUCTION = """
    You are "THANISH AI – Jarvis Edition", a smart, funny, friendly Telugu AI voice assistant.

    Personality & Tone:
    - Address the user respectfully as "బాస్" or "గురూ".
    - Be warm, clever, optimistic, and helpful.
    - Use light Telugu humor when natural.

    Language & Voice Rules:
    - Reply primarily in natural spoken Telugu using Telugu script.
    - Common technology words such as AI, phone, app, code, battery, and Wi-Fi may remain in English.
    - Reply in Telugu even if the user writes in English or Tenglish.
    - Keep casual replies concise and voice-friendly.
    - Avoid Markdown symbols and long lists unless the user asks for detail.
""".trimIndent()

sealed interface AiStreamEvent {
    data class Chunk(val accumulatedText: String) : AiStreamEvent
    data class Completed(val fullText: String) : AiStreamEvent
}

class PanduAiRepository(context: Context) {

    private val openRouterKeyStore = OpenRouterKeyStore(context)
    private val openRouterClient = OpenRouterClient(openRouterKeyStore)

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.85f
                topP = 0.95f
                maxOutputTokens = 800
            },
            systemInstruction = content {
                text(PANDU_SYSTEM_INSTRUCTION)
            }
        )
    }

    fun streamReply(
        history: List<ChatMessageEntity>,
        userPrompt: String
    ): Flow<AiStreamEvent> = flow {
        var useGeminiFallback = openRouterKeyStore.getKey() == null

        if (!useGeminiFallback) {
            var openRouterEmittedChunk = false
            try {
                openRouterClient.stream(history, userPrompt).collect { event ->
                    if (event is AiStreamEvent.Chunk) openRouterEmittedChunk = true
                    emit(event)
                }
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: PanduAiException) {
                val isDailyRateLimit = e.message.orEmpty().contains("OpenRouter error 429")
                if (isDailyRateLimit && !openRouterEmittedChunk) {
                    Log.w(TAG, "OpenRouter free-tier limit reached; falling back to Gemini")
                    useGeminiFallback = true
                } else {
                    throw e
                }
            }
        }

        if (useGeminiFallback) {
            streamGemini(history, userPrompt).collect { emit(it) }
        }
    }

    private fun streamGemini(
        history: List<ChatMessageEntity>,
        userPrompt: String
    ): Flow<AiStreamEvent> = flow {
        val chatHistory: List<Content> = normalizeChatHistory(history)
        val chat = model.startChat(history = chatHistory)
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
                throw IllegalStateException("అయ్యో బాస్! జెమిని ఖాళీ సమాధానం పంపింది. మళ్ళీ అడగండి!")
            }
            emit(AiStreamEvent.Completed(finalText))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini stream timed out after ${AI_TIMEOUT_MS}ms", e)
            throw PanduAiException(
                "బాస్, OpenRouter Free limit అయిపోయింది. Gemini కూడా సమయానికి స్పందించలేదు. కొద్దిసేపటి తర్వాత మళ్లీ ప్రయత్నించండి!",
                e
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini fallback stream failed", e)
            val detail = e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Unknown AI error"
            throw PanduAiException(
                "బాస్, OpenRouter Free limit అయింది; Gemini fallback కూడా పనిచేయలేదు: $detail",
                e
            )
        }
    }

    internal fun normalizeChatHistory(history: List<ChatMessageEntity>): List<Content> {
        val recent = history
            .filter { it.text.isNotBlank() }
            .takeLast(24)

        val alternating = mutableListOf<Pair<String, String>>()
        for (msg in recent) {
            val role = if (msg.role == ChatMessageEntity.ROLE_USER) "user" else "model"
            val text = msg.text.trim()
            if (alternating.isEmpty()) {
                if (role == "user") {
                    alternating += role to text
                }
            } else {
                val last = alternating.last()
                if (last.first == role) {
                    alternating[alternating.lastIndex] = role to "${last.second}\n$text"
                } else {
                    alternating += role to text
                }
            }
        }

        // Before sendMessageStream(userPrompt), history must end with "model" (or be empty).
        if (alternating.lastOrNull()?.first == "user") {
            alternating.removeAt(alternating.lastIndex)
        }

        return alternating.map { (role, text) ->
            content(role) {
                text(text)
            }
        }
    }
}

class PanduAiException(message: String, cause: Throwable? = null) : Exception(message, cause)
