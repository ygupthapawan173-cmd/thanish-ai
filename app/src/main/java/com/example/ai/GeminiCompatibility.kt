package com.example.ai

internal object GeminiCompatibility {
    fun isTts(id: String): Boolean = id.lowercase().contains("tts") || id.lowercase().contains("text-to-speech")
    fun isLive(id: String): Boolean = id.lowercase().let { "live" in it || "native-audio" in it }
    fun isChat(id: String): Boolean = id.removePrefix("models/").lowercase().let { it.startsWith("gemini-") && !isTts(it) && !isLive(it) && listOf("embedding", "image", "imagen", "robotics", "computer-use").none { part -> part in it } }
    fun isModalityMismatch(message: String): Boolean = message.lowercase().let { "http 400" in it && ("modality" in it || "modalities" in it || "audio output" in it || "text output" in it || "only supports audio" in it) }
    fun canDowngrade(message: String, hasTools: Boolean, hasToolHistory: Boolean): Boolean {
        val m = message.lowercase()
        return hasTools && !hasToolHistory && m.contains("http 400") &&
            ((m.contains("function calling") || m.contains("function declarations") || m.contains("tools")) &&
            (m.contains("not enabled") || m.contains("not supported") || m.contains("unsupported") || m.contains("does not support") || m.contains("not available")))
    }
    fun tryAnotherModel(message: String): Boolean = listOf("HTTP 404", "HTTP 429", "HTTP 500", "HTTP 502", "HTTP 503", "HTTP 504").any { message.contains(it,ignoreCase=true) } || isModalityMismatch(message) || canDowngrade(message, true, false)
}
