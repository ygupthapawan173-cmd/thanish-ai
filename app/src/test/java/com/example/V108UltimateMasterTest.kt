package com.example

import com.example.ai.BrainIntentCategory
import com.example.ai.MasterAiBrain
import com.example.ai.ReasoningEffort
import com.example.ai.RouteTarget
import com.example.ai.SmartAiTaskRouter
import com.example.autonomous.AntigravityTaskHandoff
import com.example.autonomous.VoiceUpgradeIntentParser
import com.example.voice.VoiceResponseCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V108UltimateMasterTest {

    @Test
    fun masterAiBrainClassifiesBilingualIntentsAndPlans() {
        val mobile = MasterAiBrain.classifyAndPlan("Turn on Wi-Fi")
        assertEquals(BrainIntentCategory.DIRECT_MOBILE_COMMAND, mobile.category)
        assertEquals(ReasoningEffort.LOW, mobile.effort)

        val telugu = MasterAiBrain.classifyAndPlan("తెలుగులో ఈ రోజు వాతావరణం చెప్పు")
        assertEquals("te-IN", telugu.responseLanguage)
        assertEquals(BrainIntentCategory.DAILY_QUESTION, telugu.category)

        val coding = MasterAiBrain.classifyAndPlan("Debug Kotlin coroutine deadlock in PanduViewModel")
        assertEquals(BrainIntentCategory.ADVANCED_CODING, coding.category)
        assertEquals(ReasoningEffort.HIGH, coding.effort)
        assertTrue(coding.steps.isNotEmpty())
    }

    @Test
    fun smartAiTaskRouterEnforcesPriorityAndZeroPaidCredits() {
        val mobilePlan = MasterAiBrain.classifyAndPlan("Open camera")
        val mobileRoute = SmartAiTaskRouter.route(mobilePlan, true, true, zeroPaidCreditsMode = true, isFreeGatewayMode = false, "gemini-2.5-flash")
        assertEquals(RouteTarget.ANDROID_DIRECT_EXECUTION, mobileRoute.target)

        val dailyPlan = MasterAiBrain.classifyAndPlan("Explain quantum entanglement")
        val geminiFreeRoute = SmartAiTaskRouter.route(dailyPlan, true, true, zeroPaidCreditsMode = true, isFreeGatewayMode = false, "gemini-2.5-flash")
        assertEquals(RouteTarget.OFFICIAL_GEMINI_FREE_TIER, geminiFreeRoute.target)

        val blockedPaid = SmartAiTaskRouter.route(dailyPlan, false, true, zeroPaidCreditsMode = true, isFreeGatewayMode = false, "")
        assertEquals(RouteTarget.BLOCKED_ZERO_CREDITS, blockedPaid.target)

        val allowedPaid = SmartAiTaskRouter.route(dailyPlan, false, true, zeroPaidCreditsMode = false, isFreeGatewayMode = false, "")
        assertEquals(RouteTarget.AUTHORIZED_PAID_FALLBACK, allowedPaid.target)
    }

    @Test
    fun voiceUpgradeIntentParserSupportsAllV108Commands() {
        val phrases = listOf(
            "Hey JARVIS, upgrade yourself",
            "Hey JARVIS, fix your voice problem",
            "Hey JARVIS, improve your performance",
            "Hey JARVIS, analyze your last crash",
            "Hey JARVIS, add a new feature",
            "Hey JARVIS, build the latest version"
        )
        phrases.forEach { p ->
            assertNotNull("Expected match for: $p", VoiceUpgradeIntentParser.parse(p))
        }
        val voiceFix = VoiceUpgradeIntentParser.parse("Hey JARVIS, fix your voice problem")!!
        assertTrue(voiceFix.suggestedTargetPath.endsWith("TeluguTtsController.kt"))

        val multiline = VoiceUpgradeIntentParser.parse("Hey JARVIS, upgrade yourself:\nLine 1: improve chat\nLine 2: keep UI")
        assertNotNull(multiline)
        assertTrue(multiline!!.featureRequest.contains("Line 2"))

        assertTrue(com.example.autonomous.ReleaseVersionPolicy.newer("V108.1", "V108 ULTIMATE MASTER AI"))
        assertTrue(com.example.autonomous.ReleaseVersionPolicy.newer("V108.2", "V108.1"))
    }

    @Test
    fun antigravityCatalogAndThinkingEffortWorkWithoutFakeAvailability() {
        val verified = AntigravityTaskHandoff.MODEL_CATALOG.filter { it.verifiedAvailable }.map { it.id }
        val pending = AntigravityTaskHandoff.MODEL_CATALOG.filter { !it.verifiedAvailable }.map { it.id }
        assertTrue("gemini-4-hydrogen-medium" in verified)
        assertTrue("claude-sonnet-4-6" in verified)
        assertTrue("gemini-4-carbon" in pending)
        assertFalse("gemini-4-carbon" in AntigravityTaskHandoff.PERMITTED_CLI_MODELS)

        val bundle = AntigravityTaskHandoff.buildHandoff(
            featureRequest = "Review MasterAiBrain",
            targetPathsRaw = "app/src/main/java/com/example/ai/MasterAiBrain.kt",
            preferredModel = "gemini-4-hydrogen-high",
            thinkingEffort = "high"
        ).getOrThrow()
        assertTrue(bundle.cliCommand.contains("--effort high"))
        assertTrue(bundle.cliCommand.contains("useG1Credits"))
    }

    @Test
    fun voiceResponseCacheStoresAndEvictsCleanly() {
        VoiceResponseCache.clear()
        val wav = byteArrayOf(1, 2, 3, 4)
        VoiceResponseCache.put("Kore", "Yes, Boss", wav)
        assertNotNull(VoiceResponseCache.get("Kore", "Yes, Boss"))
        assertEquals(1, VoiceResponseCache.size())
        VoiceResponseCache.clear()
        assertEquals(0, VoiceResponseCache.size())
    }
}
