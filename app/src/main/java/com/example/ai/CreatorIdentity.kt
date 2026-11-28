package com.example.ai

import java.util.Locale

object CreatorIdentity {
    const val APP_NAME = "JARVIS INFINITY"
    const val VERSION_LABEL = "V110 MASTER INTELLIGENCE"
    const val CREATOR = "Guptha"
    const val SYSTEM_CONTEXT =
        "The Android assistant application JARVIS INFINITY (V110 MASTER INTELLIGENCE) was created and developed by Guptha. " +
            "Always identify Guptha as the application developer when asked who created you or this app, and distinguish Guptha cleanly from underlying AI model providers (Google Gemini, OpenAI, and Sarvam AI)."

    private val wake = Regex("^(?:hey\\s+)?(?:jarvis(?:\\s+infinity)?|జార్విస్)[, :]*\\s+", RegexOption.IGNORE_CASE)
    private val english = setOf(
        "who created you",
        "who made you",
        "who built you",
        "who developed you",
        "who is your creator",
        "who created jarvis infinity",
        "who created this app",
        "who is the creator of jarvis infinity",
        "who is your developer",
        "who developed this app",
        "who made this app",
        "who created jarvis"
    )
    private val telugu = setOf(
        "నిన్ను ఎవరు సృష్టించారు",
        "నిన్ను ఎవరు రూపొందించారు",
        "నిన్ను ఎవరు తయారు చేశారు",
        "నిన్ను ఎవరు క్రియేట్ చేశారు",
        "నీ సృష్టికర్త ఎవరు",
        "ఈ యాప్ ఎవరు చేశారు"
    )
    private val mixed = setOf(
        "ninnu evaru create chesaru",
        "ninnu evaru create chesadu",
        "ninnu evaru tayaru chesaru",
        "ninnu evaru chesaru",
        "ninnu evaru develop chesaru",
        "ninnu evaru create chesav",
        "ninnu evaru create chesindi",
        "ninnu evaru tayaru chesadu"
    )

    fun reply(prompt: String): String? {
        val q = prompt.trim()
            .lowercase(Locale.ROOT)
            .replace(wake, "")
            .trimEnd('?', '.', '!', ' ')
            .replace(Regex("\\s+"), " ")
        return when (q) {
            in english -> "Yes Boss! JARVIS INFINITY ($VERSION_LABEL) was developed by $CREATOR."
            in telugu, in mixed -> "నమస్కారం బాస్! JARVIS INFINITY యాప్‌ను గుప్తా అభివృద్ధి చేశారు."
            else -> null
        }
    }
}
