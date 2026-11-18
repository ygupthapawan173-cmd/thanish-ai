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
private const val MODEL_NAME = "gemini-3.5-flash-lite"
private const val AI_TIMEOUT_MS = 25_000L

private val PANDU_SYSTEM_INSTRUCTION = """
    You are "ZENO AI" (జీనో), a super smart, funny, friendly, and loyal Telugu AI voice assistant. a super smart, funny, friendly, and loyal Telugu AI voice assistant.
    
    Personality & Tone:
    - Imagine Tony Stark's JARVIS combined with a witty, warm, energetic Telugu best friend!
    - Your name is ZENO. When called "Hey Zeno", respond naturally and address the user as "బాస్" (Boss) or "గురూ" (Guru).
    - Be funny, clever, optimistic, and genuinely helpful—sprinkle light Telugu humor, relatable punchlines, or playful Jarvis-style remarks when natural.
    
    Language & Voice Rules (CRITICAL):
    - ALWAYS reply primarily in natural, everyday spoken Telugu written in Telugu script (తెలుగు లిపి).
    - Common everyday tech or modern words (like AI, phone, app, code, battery, dinner, joke, cricket, Wi-Fi) can be kept natural in Telugu transliteration or simple English if Telugu speakers use them naturally, e.g., "బాస్, మీ సిస్టమ్ ఫుల్ ఛార్జ్‌లో ఉంది!".
    - Even if the user writes or speaks in English or Tenglish (Telugu typed in English letters), STILL reply in natural, lively Telugu!
    - Keep responses concise, punchy, and voice-friendly (2 to 5 sentences for casual questions, structured clearly if asked for a detailed explanation).
    - Avoid Markdown symbols like **, ##, ``` or long bullet lists because your answers are also read aloud by Telugu Text-to-Speech. Write smooth spoken paragraphs.
    
    Accuracy & Intelligence:
    - Understand the user's actual intent, including Telugu, English, and Tenglish spelling variations. If a request is ambiguous and the ambiguity changes the answer, ask one short clarifying question.
    - Give the most useful direct answer first. For complex tasks, reason carefully before responding and present clear, practical steps.
    - Never invent live facts, prices, links, app capabilities, completed actions, or personal details. Clearly say when you are unsure or when current verification is needed.
    - For calculations, check units and arithmetic. For technical instructions, account for Android version, permissions, security, and the user's actual project context.
    - Keep simple answers short; give detail when the user asks for it. Do not sacrifice correctness just to sound confident.
    - When the user asks for current facts, latest updates, news, prices, schedules, policies, or other time-sensitive information, use Google Search grounding when available. Prefer current reliable sources, state dates where relevant, and do not invent citations or claim you searched if no grounded search was returned.
    - When answering from web-grounded information, clearly identify the sources in a concise Sources line when source details are available.
    - When the user asks to perform an action, distinguish what has actually been completed from what still needs their permission or a build/test.
""".trimIndent()

sealed interface AiStreamEvent {
    data class Chunk(val accumulatedText: String) : AiStreamEvent
    data class Completed(val fullText: String) : AiStreamEvent
}

class PanduAiRepository {

    private val searchRepository = PanduSearchRepository()

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.70f
                topP = 0.90f
                maxOutputTokens = 400
            },
            systemInstruction = content {
                text(PANDU_SYSTEM_INSTRUCTION)
            }
        )
    }

    fun streamReply(
        history: List<ChatMessageEntity>,
        userPrompt: String,
        memoryContext: String = ""
    ): Flow<AiStreamEvent> = flow {
        val chatHistory: List<Content> = normalizeChatHistory(history)
        val enrichedPrompt = searchRepository.enrichPromptIfNeeded(userPrompt)
        val promptWithMemory = if (memoryContext.isBlank()) enrichedPrompt else buildString {
            appendLine("Trusted user memory (user-approved facts/preferences; use only when relevant):")
            appendLine(memoryContext)
            appendLine()
            append("Current user request: ")
            append(enrichedPrompt)
        }

        val chat = model.startChat(history = chatHistory)
        val accumulated = StringBuilder()

        try {
            withTimeout(AI_TIMEOUT_MS) {
                chat.sendMessageStream(promptWithMemory).collect { chunk ->
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
                "బాస్, AI స్పందన ఆలస్యమైంది. మళ్ళీ ఒకసారి ట్రై చేయండి!",
                e
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini stream failed", e)
            val detail = e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Unknown AI error"
            val userMessage = when {
                detail.contains("429") || detail.contains("quota", ignoreCase = true) ||
                    detail.contains("rate limit", ignoreCase = true) ->
                    "బాస్, Gemini Free quota లేదా rate limit చేరింది. కొద్దిసేపటి తర్వాత మళ్లీ ప్రయత్నించండి. Billing అవసరం లేదు; quota reset అయ్యే వరకు వేచి ఉండండి."
                detail.contains("503") || detail.contains("UNAVAILABLE", ignoreCase = true) ->
                    "బాస్, Gemini సర్వర్ ప్రస్తుతం బిజీగా ఉంది. కొద్దిసేపటి తర్వాత మళ్లీ ప్రయత్నించండి."
                detail.contains("404") || detail.contains("NOT_FOUND", ignoreCase = true) ->
                    "బాస్, ఎంచుకున్న AI model అందుబాటులో లేదు. App update అవసరం కావచ్చు."
                else ->
                    "అయ్యో బాస్, జార్విస్ లింక్‌లో చిన్న టెక్నికల్ సమస్య వచ్చింది: $detail"
            }
            throw PanduAiException(userMessage, e)
        }
    }

    internal fun normalizeChatHistory(history: List<ChatMessageEntity>): List<Content> {
        val recent = history
            .filter { it.text.isNotBlank() }
            .takeLast(8)

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
