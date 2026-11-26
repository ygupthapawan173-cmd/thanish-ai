package com.example.omega

enum class WakeAckStyle(val label: String, val speech: String?) {
    YES_SIR("Yes, Sir!", "Yes, Sir!"),
    AT_SERVICE("At your service", "At your service."),
    LISTENING("Listening", "Listening."),
    TELUGU_BOSS("చెప్పండి బాస్", "చెప్పండి బాస్"),
    CHIME("Chime (no TTS)", null),
    CUSTOM("Custom", null),
    SILENT("Silent", null)
}
object OmegaWakeAck {
    fun sanitizeCustom(value: String): String = value.replace(Regex("[\\p{Cntrl}]+"), " ").trim().take(40)
    fun spokenText(style: WakeAckStyle, custom: String): String? =
        if (style == WakeAckStyle.CUSTOM) sanitizeCustom(custom).ifBlank { null } else style.speech
    fun isTelugu(text: String) = text.any { it in '\u0C00'..'\u0C7F' }
    fun isEcho(heard: String, ack: String?): Boolean {
        if (ack.isNullOrBlank()) return false
        val norm = OmegaPolicy.normalize(heard)
        return norm.length >= 4 && norm == OmegaPolicy.normalize(ack)
    }
}
