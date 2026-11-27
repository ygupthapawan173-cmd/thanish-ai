package com.example.ai

enum class RouteTarget {
    ANDROID_DIRECT_EXECUTION,
    OFFICIAL_GEMINI_FREE_TIER,
    FAST_CODING_MODEL,
    ADVANCED_CODING_MODEL,
    ANTIGRAVITY_WORKFLOW_HANDOFF,
    AUTHORIZED_PAID_FALLBACK,
    BLOCKED_ZERO_CREDITS
}

data class SmartRouteResult(
    val target: RouteTarget,
    val preferredModel: String,
    val reason: String
)

/**
 * V108 Smart AI Task Router.
 * Enforces priority routing:
 * - Simple mobile commands -> Android Direct Execution
 * - Daily questions -> Official Gemini free-tier models
 * - Fast coding -> Fast coding models
 * - Advanced coding -> Advanced Gemini/Claude coding models
 * - Major development -> Antigravity permitted workflow handoff
 * - Paid requests -> Only when ZeroPaidCreditsPolicy explicitly permits
 */
object SmartAiTaskRouter {

    fun route(
        plan: BrainTaskPlan,
        hasGeminiKey: Boolean,
        hasGatewayKey: Boolean,
        zeroPaidCreditsMode: Boolean,
        isFreeGatewayMode: Boolean,
        preferredGeminiModel: String
    ): SmartRouteResult {
        return when (plan.category) {
            BrainIntentCategory.DIRECT_MOBILE_COMMAND -> SmartRouteResult(
                target = RouteTarget.ANDROID_DIRECT_EXECUTION,
                preferredModel = "local-device-action",
                reason = "Simple mobile command routed to Android Direct Execution"
            )
            BrainIntentCategory.MAJOR_DEVELOPMENT,
            BrainIntentCategory.SELF_UPGRADE,
            BrainIntentCategory.CRASH_FIX -> {
                if (hasGeminiKey) {
                    SmartRouteResult(
                        target = RouteTarget.ADVANCED_CODING_MODEL,
                        preferredModel = preferredGeminiModel.ifBlank { "gemini-2.5-pro" },
                        reason = "Autonomous coding/upgrade via Official Gemini API + Antigravity review"
                    )
                } else {
                    SmartRouteResult(
                        target = RouteTarget.ANTIGRAVITY_WORKFLOW_HANDOFF,
                        preferredModel = "gemini-4-hydrogen-medium",
                        reason = "Major development routed to permitted Antigravity CLI workflow handoff"
                    )
                }
            }
            BrainIntentCategory.ADVANCED_CODING -> {
                if (hasGeminiKey) {
                    SmartRouteResult(
                        target = RouteTarget.ADVANCED_CODING_MODEL,
                        preferredModel = preferredGeminiModel.ifBlank { "gemini-2.5-pro" },
                        reason = "Advanced coding routed to Official Gemini API"
                    )
                } else paidOrBlocked(hasGatewayKey, zeroPaidCreditsMode, isFreeGatewayMode)
            }
            BrainIntentCategory.FAST_CODING -> {
                if (hasGeminiKey) {
                    SmartRouteResult(
                        target = RouteTarget.FAST_CODING_MODEL,
                        preferredModel = preferredGeminiModel.ifBlank { "gemini-2.5-flash" },
                        reason = "Fast coding routed to Official Gemini Flash"
                    )
                } else paidOrBlocked(hasGatewayKey, zeroPaidCreditsMode, isFreeGatewayMode)
            }
            BrainIntentCategory.DAILY_QUESTION,
            BrainIntentCategory.MEMORY_QUERY -> {
                if (hasGeminiKey) {
                    SmartRouteResult(
                        target = RouteTarget.OFFICIAL_GEMINI_FREE_TIER,
                        preferredModel = preferredGeminiModel.ifBlank { "gemini-2.5-flash" },
                        reason = "Daily question routed to free-quota-first Official Gemini API"
                    )
                } else paidOrBlocked(hasGatewayKey, zeroPaidCreditsMode, isFreeGatewayMode)
            }
        }
    }

    private fun paidOrBlocked(
        hasGatewayKey: Boolean,
        zeroPaidCreditsMode: Boolean,
        isFreeGatewayMode: Boolean
    ): SmartRouteResult {
        if (!hasGatewayKey) {
            return SmartRouteResult(
                target = RouteTarget.BLOCKED_ZERO_CREDITS,
                preferredModel = "",
                reason = "No authorized API key configured"
            )
        }
        return if (ZeroPaidCreditsPolicy.allowsPaidGateway(zeroPaidCreditsMode, isFreeGatewayMode)) {
            SmartRouteResult(
                target = RouteTarget.AUTHORIZED_PAID_FALLBACK,
                preferredModel = "openrouter-gateway",
                reason = "Authorized Smart Gateway / AICredits fallback"
            )
        } else {
            SmartRouteResult(
                target = RouteTarget.BLOCKED_ZERO_CREDITS,
                preferredModel = "",
                reason = "Zero Paid Credits Mode is ON; paid fallback blocked"
            )
        }
    }
}
