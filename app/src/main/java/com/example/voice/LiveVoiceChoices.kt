package com.example.voice

object LiveVoiceChoices {
    // Empty means provider default, preserving existing behavior.
    val names = listOf("", "Kore", "Puck", "Charon", "Fenrir", "Aoede", "Zephyr")
    fun normalize(value: String?): String = names.firstOrNull { it.equals(value?.trim(), ignoreCase = true) } ?: ""
    fun label(value: String) = if (value.isEmpty()) "Provider default" else value
}
