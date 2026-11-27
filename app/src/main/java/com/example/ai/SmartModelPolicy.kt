package com.example.ai

import java.util.Locale

/** Local, deterministic classification: no extra paid classifier call. Model IDs are hints, not capability proof. */
object SmartModelPolicy {
    enum class Mode { ECONOMY, SMART, ULTRA, MANUAL, FREE }
    fun allowsDirectGemini(mode:Mode) = mode != Mode.FREE
    enum class Task { CHAT, CODE, REASONING, VISION }
    data class Model(val id: String, val imageInput: Boolean? = null, val textOutput: Boolean? = null,
                     val promptPrice: Double? = null, val completionPrice: Double? = null)
    fun task(question: String, image: Boolean): Task {
        if (image) return Task.VISION
        val q = question.lowercase(Locale.ROOT).take(32_768)
        val nonProgramming = Regex("\\b(?:postal|zip|pin|area|country|verification|otp|discount|coupon|dress|error)\\s+code\\b")
        val programming = q.replace(nonProgramming, " ")
        if (Regex("\\b(?:code|coding|kotlin|python|javascript|debug|program|programming)\\b").containsMatchIn(programming) || listOf("కోడ్", "కోడింగ్", "ప్రోగ్రామ్").any { it in programming }) return Task.CODE
        if (Regex("\\b(?:reason|calculate|math|maths|prove|solve|compare|analysis|lekka|lekkalu|ganitham|hisab|polchu|visleshana)\\b").containsMatchIn(q) || listOf("లెక్క", "గణితం", "విశ్లేష", "పోల్చు").any { it in q } || Regex("\\d+\\s*[+*/=−×÷-]\\s*\\d+").containsMatchIn(q)) return Task.REASONING
        return Task.CHAT
    }
    fun validId(id: String): Boolean = id.isNotBlank() && id.length <= 512 && id.none { it.isWhitespace() || it.isISOControl() }
    fun eligible(m: Model, task: Task): Boolean {
        if (!validId(m.id)) return false
        val id = m.id.lowercase(Locale.ROOT)
        if (m.textOutput == false || listOf(":batch", "embedding", "rerank", "whisper", "bulbul", "saarika", "saaras", "voxtral", "tts", "text-to-speech", "native-audio", "realtime", "-live", "moderation", "imagen", "stable-diffusion", "seedance", "image-generation", "video-generation", "dall-e").any { it in id }) return false
        if (m.textOutput != true && listOf("-image", "-audio").any { it in id }) return false
        // Never infer image support from a brand name. Require advertised modality metadata.
        return task != Task.VISION || m.imageInput == true
    }
    private fun light(id: String) = listOf("lite", "nano", "mini", "haiku", ":free", "flash").any { it in id }
    private fun strong(id: String) = listOf("pro", "opus", "sonnet", "reason", "deepseek-r1", "gpt-5", "o3", "o4").any { it in id }
    private fun score(m: Model, task: Task, mode: Mode): Int {
        val id = m.id.lowercase(Locale.ROOT)
        if (mode == Mode.ULTRA) return (if (strong(id)) 100 else 0) + (if (light(id)) -20 else 0)
        if (mode == Mode.ECONOMY) return (if (light(id)) 100 else 0) + (if (":free" in id) 40 else 0)
        return when (task) {
            Task.CHAT -> (if (light(id)) 100 else 0) + (if ("gemini" in id) 15 else 0)
            Task.CODE -> (if ("claude" in id || "coder" in id || "codex" in id) 100 else 0) + (if (strong(id)) 30 else 0)
            Task.REASONING -> (if ("reason" in id || "deepseek" in id || "o3" in id) 100 else 0) + (if (strong(id)) 30 else 0)
            Task.VISION -> (if ("gemini" in id) 30 else 0) + (if (light(id)) 10 else 0)
        }
    }
    fun isVerifiedFree(m: Model): Boolean = m.id.endsWith(":free") && m.promptPrice == 0.0 && m.completionPrice == 0.0
    fun rank(models: List<Model>, task: Task, mode: Mode, manualId: String, manualVisionConfirmed: Boolean = false): List<Model> {
        val valid = models.distinctBy { it.id }.filter { eligible(it, task) && (mode != Mode.FREE || isVerifiedFree(it)) }
        if (mode == Mode.MANUAL) return models.distinctBy { it.id }.filter {
            it.id == manualId.trim() && (eligible(it, task) || (task == Task.VISION && manualVisionConfirmed && it.imageInput == null && eligible(it, Task.CHAT)))
        }
        fun cost(m: Model): Double = if (m.promptPrice != null && m.completionPrice != null && m.promptPrice.isFinite() && m.completionPrice.isFinite() && m.promptPrice >= 0 && m.completionPrice >= 0) m.promptPrice + m.completionPrice else Double.POSITIVE_INFINITY
        return if (mode == Mode.ECONOMY) valid.sortedWith(compareBy<Model> { cost(it) }.thenByDescending { score(it, task, mode) }.thenBy { it.id })
        else valid.sortedWith(compareByDescending<Model> { score(it, task, mode) }.thenBy { cost(it) }.thenBy { it.id })
    }
    fun mayTryAnother(status: Int): Boolean = status in listOf(404, 429, 500, 502, 503, 504)
}
