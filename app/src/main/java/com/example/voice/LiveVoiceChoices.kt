package com.example.voice

object LiveVoiceChoices {
    // Empty means provider default, preserving existing behavior; supports all 30 Gemini speakers.
    val names = listOf(
        "", "Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede",
        "Callirrhoe", "Autonoe", "Enceladus", "Iapetus", "Umbriel", "Algieba",
        "Despina", "Erinome", "Algenib", "Rasalgethi", "Laomedeia", "Achernar",
        "Alnilam", "Schedar", "Gacrux", "Pulcherrima", "Achird", "Zubenelgenubi",
        "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat"
    )
    fun normalize(value: String?): String = names.firstOrNull { it.equals(value?.trim(), ignoreCase = true) } ?: ""
    fun label(value: String) = if (value.isEmpty()) "Provider default" else value
}
