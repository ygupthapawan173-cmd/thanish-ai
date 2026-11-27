package com.example.ai

/** Optional lifetime credit cap. Fail closed if the provider cannot attest to it. */
object ProviderBudgetPolicy {
    fun validCap(cap: Double): Boolean = cap.isFinite() && cap in 0.0..10000.0
    fun rejection(cap: Double, limit: Double?, remaining: Double?, lifetime: Boolean, includesByok: Boolean, ordinaryKey: Boolean): String? {
        if (!validCap(cap)) return "Invalid app budget"
        if (cap == 0.0) return null
        if (!ordinaryKey || !lifetime || !includesByok) return "Use an ordinary OpenRouter key with a lifetime limit including BYOK"
        if (limit == null || !limit.isFinite() || limit <= 0 || limit > cap) return "Provider key lifetime limit must be positive and no greater than the app cap"
        if (remaining == null || !remaining.isFinite() || remaining <= 0 || remaining > limit) return "Provider budget exhausted or unverifiable"
        return null
    }
}
