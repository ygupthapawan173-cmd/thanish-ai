package com.example.ai

/** Prefer the current key's actual discovered chat models. Do not inject unlisted IDs
 * into a successful discovery result. Use documented current defaults only without discovery. */
internal object GeminiModelSelectionPolicy {
    val VERIFIED_FALLBACK_MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.8-flash")

    fun buildCandidates(
        primary: String,
        discoveredModels: List<String>,
        maxDiscovered: Int = 5
    ): List<String> {
        val cleanPrimary = primary.trim().removePrefix("models/")
        val valid = discoveredModels
            .map { it.trim().removePrefix("models/") }
            .filter { SmartModelPolicy.validId(it) && GeminiCompatibility.isChat(it) }
            .distinct()
        if (valid.isNotEmpty()) {
            val ordered = LinkedHashSet<String>()
            if (cleanPrimary in valid) ordered.add(cleanPrimary)
            ordered.addAll(valid)
            return ordered.take(maxDiscovered.coerceAtLeast(1))
        }
        val defaults = LinkedHashSet<String>()
        if (cleanPrimary in VERIFIED_FALLBACK_MODELS) defaults.add(cleanPrimary)
        defaults.addAll(VERIFIED_FALLBACK_MODELS)
        return defaults.toList()
    }
}
