package com.example.ai

/** Provider discovery first, with documented current Gemini chat fallback IDs. */
internal object GeminiModelSelectionPolicy {

    val VERIFIED_FALLBACK_MODELS: List<String> = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.8-flash"
    )

    // Legacy placeholder IDs that must only be used if explicitly returned by provider model discovery.
    private val unverifiedLegacyPlaceholders: Set<String> = setOf(
        "gemini-4-hydrogen",
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite"
    )

    fun buildCandidates(
        primary: String,
        discoveredModels: List<String>,
        maxDiscovered: Int = 5
    ): List<String> {
        val cleanPrimary = primary.trim().removePrefix("models/")
        val validDiscovered = discoveredModels
            .map { it.trim().removePrefix("models/") }
            .filter { it.isNotBlank() && SmartModelPolicy.validId(it) && GeminiCompatibility.isChat(it) }
            .distinct()
            .take(maxDiscovered)

        val ordered = LinkedHashSet<String>()

        if (validDiscovered.isNotEmpty()) {
            // Only prioritize primary when the provider actually confirmed it in discovery.
            if (cleanPrimary in validDiscovered) {
                ordered.add(cleanPrimary)
            }
            ordered.addAll(validDiscovered)
        } else {
            // Discovery cache empty: allow primary if valid and not an unverified placeholder.
            if (cleanPrimary.isNotBlank() &&
                SmartModelPolicy.validId(cleanPrimary) &&
                GeminiCompatibility.isChat(cleanPrimary) &&
                cleanPrimary !in unverifiedLegacyPlaceholders
            ) {
                ordered.add(cleanPrimary)
            }
        }

        // Preserve automatic fallback with verified official models.
        for (fallback in VERIFIED_FALLBACK_MODELS) {
            if (GeminiCompatibility.isChat(fallback)) {
                ordered.add(fallback)
            }
        }

        return ordered.toList()
    }
}
