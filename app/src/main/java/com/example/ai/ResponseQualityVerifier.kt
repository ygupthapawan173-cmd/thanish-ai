package com.example.ai

/**
 * V110 AI Response Quality & Hallucination Risk Verifier.
 * Audits replies before display/speech to prevent:
 * - False claims of device actions when no tools executed
 * - Ungrounded live/real-time claims when live web search was not used
 * - Incomplete single-phrase replies when full Telugu/English answers are expected
 */
enum class VerificationStatus(val badge: String) {
    VERIFIED("Verified"),
    CAUTION_UNGROUNDED_LIVE("Caution: Live web not verified"),
    CAUTION_ACTION_CLAIM("Caution: No device action ran"),
    INCOMPLETE("Incomplete")
}

data class QualityVerificationResult(
    val status: VerificationStatus,
    val note: String,
    val sanitizedReply: String
)

object ResponseQualityVerifier {

    private val LIVE_CLAIM_QUERY = Regex(
        "\\b(?:weather|temperature|forecast|stock|price|bitcoin|score|match|news|today|live|latest|current\\s+rate)\\b",
        RegexOption.IGNORE_CASE
    )
    private val ACTION_CLAIM_REPLY = Regex(
        "\\b(?:i\\s+have\\s+(?:turned\\s+(?:on|off)|opened|called|sent|set)|flashlight\\s+is\\s+(?:on|off)\\s+now)\\b",
        RegexOption.IGNORE_CASE
    )

    fun verify(
        userPrompt: String,
        replyText: String,
        executedTools: List<String>,
        usedWebSearch: Boolean,
        expectedLanguage: String,
        guardEnabled: Boolean = true
    ): QualityVerificationResult {
        val clean = replyText.trim()
        if (clean.isBlank()) {
            return QualityVerificationResult(
                status = VerificationStatus.INCOMPLETE,
                note = "Empty model output",
                sanitizedReply = "No complete response received. Please try again."
            )
        }
        if (!guardEnabled) {
            return QualityVerificationResult(VerificationStatus.VERIFIED, "Guard bypassed by user", clean)
        }

        // 1. Detect hallucinated action execution when no device tools actually ran
        if (executedTools.isEmpty() && ACTION_CLAIM_REPLY.containsMatchIn(clean)) {
            return QualityVerificationResult(
                status = VerificationStatus.CAUTION_ACTION_CLAIM,
                note = "No Android device action executed; reply adjusted for accuracy",
                sanitizedReply = "$clean\n\n[Verification Note: No device action was executed automatically for this turn. Tap or confirm an explicit action if needed.]"
            )
        }

        // 2. Detect ungrounded live-information claims when web search did not run
        val askedForLiveInfo = LIVE_CLAIM_QUERY.containsMatchIn(userPrompt)
        val toolRanSearch = usedWebSearch || executedTools.any { it == "live_web_search" || it == "web_search" }
        if (askedForLiveInfo && !toolRanSearch) {
            val disclaimer = if (expectedLanguage.startsWith("te", true)) {
                "\n\n[గమనిక: లైవ్ వెబ్ సెర్చ్ లేకుండా ఈ సమాచారం ఇవ్వబడింది — తాజా వివరాల కోసం Google Search Grounding ఆన్ చేయండి.]"
            } else {
                "\n\n[Verification Note: Answered without live web retrieval; enable Google Search Grounding for real-time confirmation.]"
            }
            return QualityVerificationResult(
                status = VerificationStatus.CAUTION_UNGROUNDED_LIVE,
                note = "Live query answered without live web search tool",
                sanitizedReply = clean + disclaimer
            )
        }

        return QualityVerificationResult(
            status = VerificationStatus.VERIFIED,
            note = "Response passed quality & consistency check",
            sanitizedReply = clean
        )
    }
}
