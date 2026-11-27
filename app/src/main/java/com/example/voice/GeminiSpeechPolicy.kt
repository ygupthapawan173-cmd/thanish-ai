package com.example.voice

import com.example.ai.SmartModelPolicy
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object GeminiSpeechPolicy {
    const val MODEL = "gemini-2.5-flash-preview-tts"
    const val MAX_PCM = 8 * 1024 * 1024
    const val QUOTA_COOLDOWN_MS = 60_000L
    const val TRANSIENT_COOLDOWN_MS = 30_000L

    val VERIFIED_TTS_MODELS = listOf(
        "gemini-2.5-flash-preview-tts",
        "gemini-2.5-pro-preview-tts"
    )

    val speakers = listOf(
        "Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede",
        "Callirrhoe", "Autonoe", "Enceladus", "Iapetus", "Umbriel", "Algieba",
        "Despina", "Erinome", "Algenib", "Rasalgethi", "Laomedeia", "Achernar",
        "Alnilam", "Schedar", "Gacrux", "Pulcherrima", "Achird", "Zubenelgenubi",
        "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat"
    )

    fun speaker(value: String, lang: String) =
        value.takeIf { it in speakers } ?: if (lang == "te") "Kore" else "Charon"

    fun allowed(
        enabled: Boolean,
        gateway: Boolean,
        live: Boolean,
        privacy: Boolean,
        foreground: Boolean,
        keyPresent: Boolean,
        length: Int
    ): Boolean = enabled && gateway && !live && !privacy && foreground && keyPresent && length in 1..CloudVoicePolicy.MAX_TEXT

    /**
     * Builds an ordered list of verified Gemini TTS models for auto-switching.
     * Prioritizes preferred/discovered TTS models, then falls back to verified defaults.
     */
    fun candidateModels(
        discoveredTtsModels: List<String> = emptyList(),
        preferredModel: String = MODEL
    ): List<String> {
        val raw = buildList {
            if (preferredModel.isNotBlank()) add(preferredModel.trim())
            addAll(discoveredTtsModels.map { it.trim() })
            addAll(VERIFIED_TTS_MODELS)
        }
        return raw
            .filter { SmartModelPolicy.validId(it) && it.startsWith("gemini-") && it.contains("tts", ignoreCase = true) }
            .distinct()
    }

    fun isQuotaError(httpCode: Int, message: String? = null): Boolean {
        if (httpCode == 429) return true
        val msg = message?.lowercase().orEmpty()
        return "quota" in msg || "resource_exhausted" in msg || "rate limit" in msg
    }

    fun isModelSwitchableError(httpCode: Int, message: String? = null): Boolean {
        if (httpCode in setOf(404, 429, 500, 502, 503, 504)) return true
        val msg = message?.lowercase().orEmpty()
        return isQuotaError(httpCode, msg) || "not found" in msg || "not supported" in msg || "overloaded" in msg
    }

    fun cooldownFor(httpCode: Int, message: String? = null): Long =
        if (isQuotaError(httpCode, message)) QUOTA_COOLDOWN_MS else TRANSIENT_COOLDOWN_MS

    fun wav(pcm: ByteArray): ByteArray {
        require(pcm.isNotEmpty() && pcm.size <= MAX_PCM && pcm.size % 2 == 0)
        return ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            .putInt(16).putShort(1.toShort()).putShort(1.toShort()).putInt(24000).putInt(48000).putShort(2.toShort()).putShort(16.toShort())
            .put("data".toByteArray(Charsets.US_ASCII)).putInt(pcm.size).put(pcm).array()
    }
}
