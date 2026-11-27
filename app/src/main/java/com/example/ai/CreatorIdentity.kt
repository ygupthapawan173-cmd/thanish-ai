package com.example.ai

import java.util.Locale

object CreatorIdentity {
    const val APP_NAME = "JARVIS INFINITY"
    const val CREATOR = "Yeluchuri Guptha"
    const val SYSTEM_CONTEXT = "The Android assistant app JARVIS INFINITY was created by Yeluchuri Guptha. Distinguish the app creator from the underlying AI model provider; do not claim he created the model."
    private val wake = Regex("^(?:hey\\s+)?(?:jarvis(?:\\s+infinity)?|జార్విస్)[, :]*\\s+", RegexOption.IGNORE_CASE)
    private val english = setOf("who created you", "who made you", "who built you", "who developed you", "who is your creator", "who created jarvis infinity", "who created this app", "who is the creator of jarvis infinity", "who is your developer", "who developed this app", "who made this app", "who created jarvis")
    private val telugu = setOf("నిన్ను ఎవరు సృష్టించారు", "నిన్ను ఎవరు రూపొందించారు", "నిన్ను ఎవరు తయారు చేశారు", "నిన్ను ఎవరు క్రియేట్ చేశారు", "నీ సృష్టికర్త ఎవరు")
    private val mixed = setOf("ninnu evaru create chesaru", "ninnu evaru create chesadu", "ninnu evaru tayaru chesaru", "ninnu evaru chesaru", "ninnu evaru develop chesaru", "ninnu evaru create chesav", "ninnu evaru create chesindi", "ninnu evaru tayaru chesadu")
    fun reply(prompt: String): String? {
        val q = prompt.trim().lowercase(Locale.ROOT).replace(wake, "").trimEnd('?', '.', '!', ' ').replace(Regex("\\s+"), " ")
        return when(q) {
            in english -> "Yes Boss! The JARVIS INFINITY assistant app was created by $CREATOR."
            in telugu, in mixed -> "బాస్, JARVIS INFINITY యాప్‌ను యెలుచూరి గుప్తా రూపొందించారు."
            else -> null
        }
    }
}
