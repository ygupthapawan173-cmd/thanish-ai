package com.example.omega

/** What the assistant does when the wake phrase is heard. */
enum class WakeAckStyle(val label: String, val text: String?) {
    YES_SIR("Voice: Yes, Sir!", "Yes, Sir!"),
    AT_SERVICE("Voice: At your service", "At your service."),
    LISTENING("Voice: Listening", "Listening."),
    TELUGU_BOSS("Telugu voice: చెప్పండి బాస్", "చెప్పండి బాస్"),
    CHIME("Chime only (no voice)", null),
    CUSTOM("Custom text", null),
    SILENT("Silent (nothing plays)", null)
}

object OmegaWakeAck {
    const val MAX_CUSTOM = 40

    fun sanitizeCustom(raw: String): String =
        raw.replace(Regex("[\\p{Cntrl}]+"), " ").trim().take(MAX_CUSTOM)

    fun spokenText(style: WakeAckStyle, custom: String): String? = when (style) {
        WakeAckStyle.CUSTOM -> sanitizeCustom(custom).ifBlank { null }
        else -> style.text
    }

    fun isTelugu(text: String): Boolean = text.any { it in '\u0C00'..'\u0C7F' }

    fun clampRate(v: Float): Float = if (v.isNaN()) 1f else v.coerceIn(0.6f, 1.6f)
    fun clampPitch(v: Float): Float = if (v.isNaN()) 1f else v.coerceIn(0.6f, 1.5f)

    fun isEcho(heard: String, ack: String?): Boolean {
        if (ack.isNullOrBlank()) return false
        val h = OmegaPolicy.normalize(heard)
        val a = OmegaPolicy.normalize(ack)
        if (h.isEmpty() || a.isEmpty()) return false
        return h == a && h.length >= 4
    }
}
