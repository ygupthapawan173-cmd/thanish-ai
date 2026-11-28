package com.example.ai

import com.example.data.IntelligenceMode
import java.util.Locale

/**
 * Capability & compatibility fallback policy for OpenAI GPT & Gemini models (Section 10).
 * Third-party non-Gemini/OpenAI models are filtered out.
 */
object SmartModelPolicy {
    enum class Task { CHAT, CODE, REASONING, VISION }

    data class Model(
        val id: String,
        val imageInput: Boolean? = null,
        val textOutput: Boolean? = null,
        val promptPrice: Double? = null,
        val completionPrice: Double? = null
    )

    fun task(question: String, image: Boolean): Task {
        if (image) return Task.VISION
        val q = question.lowercase(Locale.ROOT).take(32_768)
        if (Regex("\\b(?:code|coding|kotlin|python|javascript|debug|program|programming|algorithm|sql)\\b").containsMatchIn(q)) {
            return Task.CODE
        }
        if (Regex("\\b(?:reason|calculate|math|maths|prove|solve|compare|analysis|analyze|why|how does|physics|medical|financial)\\b").containsMatchIn(q) ||
            Regex("\\d+\\s*[+*/=−×÷-]\\s*\\d+").containsMatchIn(q)
        ) {
            return Task.REASONING
        }
        return Task.CHAT
    }

    fun validId(id: String): Boolean =
        id.isNotBlank() && id.length <= 512 && id.none { it.isWhitespace() || it.isISOControl() }

    fun eligible(m: Model, task: Task): Boolean {
        if (!validId(m.id)) return false
        if (!ModelCatalogPolicy.isSupportedProviderModel(m.id)) return false
        val id = m.id.lowercase(Locale.ROOT)
        if (m.textOutput == false || listOf(
                ":batch", "embedding", "rerank", "whisper", "transcribe", "tts", "text-to-speech",
                "native-audio", "realtime", "-live", "moderation", "imagen", "dall-e", "audio-preview",
                "image-generation", "video-generation", "sora"
            ).any { it in id }
        ) return false
        if (m.textOutput != true && listOf("-image", "-audio").any { it in id }) return false
        return task != Task.VISION || m.imageInput != false
    }

    fun isVerifiedFree(m: Model): Boolean =
        m.promptPrice == 0.0 && m.completionPrice == 0.0

    // Mode affinity is separate from the configured preference: a default fast
    // model must not suppress deep reasoning, or vice versa.
    private fun modeAffinity(m: Model, mode: IntelligenceMode): Int {
        val id = m.id.lowercase(Locale.ROOT)
        val markers = when (mode) {
            IntelligenceMode.FAST -> listOf("hydrogen", "luna", "flash", "mini", "nano")
            IntelligenceMode.SMART -> listOf("argon", "neon", "sol", "terra", "flash", "4o")
            IntelligenceMode.DEEP -> listOf("carbon", "astra", "pro", "o3", "o4", "gpt-5", "gpt-6.2")
        }
        // Match model-name tokens, not substrings: "gemini" contains "mini",
        // which previously misclassified every Gemini model as FAST.
        return if (markers.any { marker ->
            Regex("(?:^|[-_/.])" + Regex.escape(marker) + "(?:$|[-_/.])").containsMatchIn(id)
        }) 1 else 0
    }

    private fun score(m: Model, task: Task, preferredId: String): Int {
        val id = m.id.lowercase(Locale.ROOT)
        var s = 0
        if (preferredId.isNotBlank() && id == preferredId.trim().lowercase(Locale.ROOT)) s += 500
        if (task == Task.REASONING || task == Task.CODE) {
            if (listOf("carbon", "astra", "pro", "o3", "o4").any { it in id }) s += 80
        }
        if (task == Task.VISION && m.imageInput == true) s += 60
        return s
    }

    fun rank(
        models: List<Model>,
        task: Task,
        mode: IntelligenceMode,
        preferredId: String,
        freePriority: Boolean = true
    ): List<Model> {
        val valid = models.distinctBy { it.id }.filter { eligible(it, task) }
        fun cost(m: Model): Double =
            if (m.promptPrice != null && m.completionPrice != null && m.promptPrice.isFinite() && m.completionPrice.isFinite()) {
                m.promptPrice + m.completionPrice
            } else Double.POSITIVE_INFINITY

        // Stable sorting from the least significant key to the most significant
        // makes priority explicit and independent of the catalog's input order.
        val ranked = valid
            .sortedBy { it.id }
            .sortedBy { cost(it) }
            .sortedByDescending { score(it, task, preferredId) }
            .sortedByDescending { modeAffinity(it, mode) }
        if (!freePriority) return ranked
        val (free, remaining) = ranked.partition { isVerifiedFree(it) }
        return free + remaining
    }

    fun mayTryAnother(status: Int): Boolean = status in listOf(404, 429, 500, 502, 503, 504)
}
