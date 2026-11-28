package com.example.ai

import com.example.data.AiProvider
import com.example.data.IntelligenceMode

enum class RouteTarget {
    ANDROID_DIRECT_EXECUTION,
    LOCAL_RELEASE_UPDATE,
    OFFICIAL_GEMINI_FREE_TIER,
    DEEP_THINKING_ENGINE,
    LIVE_WEB_SEARCH,
    DEEP_RESEARCH_ENGINE,
    AUTHORIZED_PAID_FALLBACK,
    BLOCKED_ZERO_CREDITS
}

data class SmartRouteResult(
    val target: RouteTarget,
    val preferredModel: String,
    val fallbackModels: List<String>,
    val reason: String
)

/**
 * V110 Master AI Task Router (Sections 3, 4, 5, 10, 16):
 * Coordinates Fast / Smart / Deep Intelligence modes, Live Internet Search,
 * Gemini Direct free-tier priority, OpenAI GPT capability fallback, and paid authorization checks.
 */
object SmartAiTaskRouter {

    fun route(
        plan: BrainTaskPlan,
        activeProvider: AiProvider,
        hasGeminiKey: Boolean,
        hasOpenAiKey: Boolean,
        freePriority: Boolean,
        allowPaidModels: Boolean,
        preferredGeminiModel: String,
        preferredOpenAiModel: String,
        isDirectCommand: Boolean = false
    ): SmartRouteResult {
        if (plan.releaseAction != null) {
            return SmartRouteResult(
                target = RouteTarget.LOCAL_RELEASE_UPDATE,
                preferredModel = "local-self-upgrade",
                fallbackModels = emptyList(),
                reason = "Voice release check / upgrade handled securely via Self-Upgrade Center"
            )
        }

        if (isDirectCommand) {
            return SmartRouteResult(
                target = RouteTarget.ANDROID_DIRECT_EXECUTION,
                preferredModel = "android-direct",
                fallbackModels = emptyList(),
                reason = "Explicit Android device control executed directly"
            )
        }

        if (plan.prefersDeepResearch && hasGeminiKey && (freePriority || activeProvider == AiProvider.GEMINI || !allowPaidModels)) {
            return SmartRouteResult(
                target = RouteTarget.DEEP_RESEARCH_ENGINE,
                preferredModel = "deep-research-preview-04-2026",
                fallbackModels = listOf(preferredGeminiModel.ifBlank { "gemini-4-carbon" }, "gemini-2.5-pro", "gemini-2.5-flash"),
                reason = "Deep multi-source investigation routed to Gemini Deep Research with grounded fallback"
            )
        }

        if (plan.requiresWebSearch) {
            if ((activeProvider == AiProvider.GEMINI || freePriority || !allowPaidModels) && hasGeminiKey) {
                val primary = preferredGeminiModel.ifBlank { "gemini-4-hydrogen" }
                return SmartRouteResult(
                    target = RouteTarget.LIVE_WEB_SEARCH,
                    preferredModel = primary,
                    fallbackModels = listOf("gemini-3.8-flash", "gemini-2.5-flash"),
                    reason = "Real-time query routed to Gemini Google Search Grounding"
                )
            }
            if (hasOpenAiKey && allowPaidModels) {
                val oaiPrimary = preferredOpenAiModel.ifBlank { "gpt-6.2-sol" }
                return SmartRouteResult(
                    target = RouteTarget.LIVE_WEB_SEARCH,
                    preferredModel = oaiPrimary,
                    fallbackModels = listOf("gpt-6.2-sol", "gpt-5", "gpt-4o"),
                    reason = "Real-time query routed to OpenAI Responses Web Search"
                )
            }
        }

        val wantsGeminiFirst = when {
            activeProvider == AiProvider.GEMINI && hasGeminiKey -> true
            freePriority && hasGeminiKey -> true
            !allowPaidModels && hasGeminiKey -> true
            else -> false
        }

        if (wantsGeminiFirst) {
            val defaultGem = when (plan.intelligenceMode) {
                IntelligenceMode.FAST -> "gemini-4-hydrogen"
                IntelligenceMode.SMART -> "gemini-4-argon"
                IntelligenceMode.DEEP -> "gemini-4-carbon"
            }
            val primary = preferredGeminiModel.ifBlank { defaultGem }
            val target = if (plan.intelligenceMode == IntelligenceMode.DEEP) {
                RouteTarget.DEEP_THINKING_ENGINE
            } else {
                RouteTarget.OFFICIAL_GEMINI_FREE_TIER
            }
            return SmartRouteResult(
                target = target,
                preferredModel = primary,
                fallbackModels = listOf("gemini-4-neon", "gemini-3.8-flash", "gemini-2.5-flash", "gemini-2.5-flash-lite").distinct(),
                reason = "Routed via Official Gemini Direct (${plan.intelligenceMode.label}; Free Priority=$freePriority)"
            )
        }

        if (hasOpenAiKey) {
            if (!allowPaidModels) {
                return SmartRouteResult(
                    target = RouteTarget.BLOCKED_ZERO_CREDITS,
                    preferredModel = "",
                    fallbackModels = emptyList(),
                    reason = "Paid OpenAI GPT models require explicit user authorization in Settings"
                )
            }
            val defaultOai = when (plan.intelligenceMode) {
                IntelligenceMode.FAST -> "gpt-6-luna"
                IntelligenceMode.SMART -> "gpt-6.2-sol"
                IntelligenceMode.DEEP -> "gpt-6.2-astra"
            }
            val primary = preferredOpenAiModel.ifBlank { defaultOai }
            return SmartRouteResult(
                target = RouteTarget.AUTHORIZED_PAID_FALLBACK,
                preferredModel = primary,
                fallbackModels = listOf("gpt-6.2-sol", "gpt-6.1-terra", "gpt-5-mini", "gpt-4o-mini").distinct(),
                reason = "Routed via Authorized OpenAI GPT (${plan.intelligenceMode.label})"
            )
        }

        if (hasGeminiKey) {
            return SmartRouteResult(
                target = RouteTarget.OFFICIAL_GEMINI_FREE_TIER,
                preferredModel = preferredGeminiModel.ifBlank { "gemini-4-hydrogen" },
                fallbackModels = listOf("gemini-3.8-flash", "gemini-2.5-flash"),
                reason = "Automatic provider fallback to Google Gemini Direct"
            )
        }

        return SmartRouteResult(
            target = RouteTarget.BLOCKED_ZERO_CREDITS,
            preferredModel = "",
            fallbackModels = emptyList(),
            reason = "No authorized Gemini or OpenAI API slot configured"
        )
    }
}
