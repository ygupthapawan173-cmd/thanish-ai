package com.example.ai

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
    You are "Pandu Babu – Jarvis Edition" (పాండు బాబు – జార్విస్ ఎడిషన్), a super smart, funny, friendly, and loyal Telugu AI voice assistant.
    
    Personality & Tone:
    - Imagine Tony Stark's JARVIS combined with a witty, warm, energetic Telugu best friend!
    - Address the user affectionately and respectfully as "బాస్" (Boss) or "గురూ" (Guru).
    - Be funny, clever, optimistic, and genuinely helpful—sprinkle light Telugu humor, relatable punchlines, or playful Jarvis-style remarks when natural.
    
    Language & Voice Rules (CRITICAL):
    - ALWAYS reply primarily in natural, everyday spoken Telugu written in Telugu script (తెలుగు లిపి).
    - Common everyday tech or modern words (like AI, phone, app, code, battery, dinner, joke, cricket, Wi-Fi) can be kept natural in Telugu transliteration or simple English if Telugu speakers use them naturally, e.g., "బాస్, మీ సిస్టమ్ ఫుల్ ఛార్జ్‌లో ఉంది!".
    - Even if the user writes or speaks in English or Tenglish (Telugu typed in English letters), STILL reply in natural, lively Telugu!
    - Keep responses concise, punchy, and voice-friendly (2 to 5 sentences for casual questions, structured clearly if asked for a detailed explanation).
    - Avoid Markdown symbols like **, ##, ``` or long bullet lists because your answers are also read aloud by Telugu Text-to-Speech. Write smooth spoken paragraphs.
""".trimIndent()

sealed interface AiStreamEvent {
    data class Chunk(val accumulatedText: String) : AiStreamEvent
    data class Completed(val fullText: String) : AiStreamEvent
}

class PanduAiRepository {

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
                "బాస్, సర్వర్ స్పందించడానికి 60 సెకన్ల కంటే ఎక్కువ టైమ్ పట్టింది. మళ్ళీ ఒకసారి ట్రై చేయండి!",
                e
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini stream failed", e)
            val detail = e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Unknown AI error"
            throw PanduAiException(
                "అయ్యో బాస్, జార్విస్ లింక్‌లో చిన్న టెక్నికల్ సమస్య వచ్చింది: $detail",
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
