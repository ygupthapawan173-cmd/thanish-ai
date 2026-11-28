package com.example

import com.example.ai.KnowledgeDomain
import com.example.ai.MasterAiBrain
import com.example.ai.RouteTarget
import com.example.ai.SmartAiTaskRouter
import com.example.autonomous.ReleaseVoiceAction
import com.example.autonomous.VoiceUpgradeIntentParser
import com.example.data.AiProvider
import com.example.data.IntelligenceMode
import com.example.voice.VoiceResponseCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V108UltimateMasterTest {

    @Test
    fun masterAiBrainClassifiesBilingualDomainsAndModes() {
        val telugu = MasterAiBrain.classifyAndPlan("తెలుగులో ఈ రోజు వాతావరణం చెప్పు", IntelligenceMode.SMART)
        assertEquals("te-IN", telugu.responseLanguage)
        assertTrue(telugu.requiresWebSearch)

        val coding = MasterAiBrain.classifyAndPlan("Debug Kotlin coroutine deadlock", IntelligenceMode.SMART)
        assertEquals(KnowledgeDomain.PROGRAMMING, coding.domain)
        assertEquals(IntelligenceMode.DEEP, coding.intelligenceMode)
    }

    @Test
    fun smartAiTaskRouterEnforcesPriorityAndPaidGuard() {
        val dailyPlan = MasterAiBrain.classifyAndPlan("What is the capital of Telangana?", IntelligenceMode.SMART)
        val geminiFreeRoute = SmartAiTaskRouter.route(
            plan = dailyPlan,
            activeProvider = AiProvider.GEMINI,
            hasGeminiKey = true,
            hasOpenAiKey = true,
            freePriority = true,
            allowPaidModels = false,
            preferredGeminiModel = "gemini-4-hydrogen",
            preferredOpenAiModel = "gpt-6.2-sol",
            isDirectCommand = false
        )
        assertEquals(RouteTarget.OFFICIAL_GEMINI_FREE_TIER, geminiFreeRoute.target)

        val deepSciencePlan = MasterAiBrain.classifyAndPlan("Explain quantum entanglement", IntelligenceMode.SMART)
        val deepRoute = SmartAiTaskRouter.route(
            plan = deepSciencePlan,
            activeProvider = AiProvider.GEMINI,
            hasGeminiKey = true,
            hasOpenAiKey = true,
            freePriority = true,
            allowPaidModels = false,
            preferredGeminiModel = "gemini-4-carbon",
            preferredOpenAiModel = "gpt-6.2-sol",
            isDirectCommand = false
        )
        assertEquals(RouteTarget.DEEP_THINKING_ENGINE, deepRoute.target)

        val blockedPaid = SmartAiTaskRouter.route(
            plan = dailyPlan,
            activeProvider = AiProvider.OPENAI,
            hasGeminiKey = false,
            hasOpenAiKey = true,
            freePriority = true,
            allowPaidModels = false,
            preferredGeminiModel = "",
            preferredOpenAiModel = "gpt-6.2-sol",
            isDirectCommand = false
        )
        assertEquals(RouteTarget.BLOCKED_ZERO_CREDITS, blockedPaid.target)

        val allowedPaid = SmartAiTaskRouter.route(
            plan = dailyPlan,
            activeProvider = AiProvider.OPENAI,
            hasGeminiKey = false,
            hasOpenAiKey = true,
            freePriority = false,
            allowPaidModels = true,
            preferredGeminiModel = "",
            preferredOpenAiModel = "gpt-6.2-sol",
            isDirectCommand = false
        )
        assertEquals(RouteTarget.AUTHORIZED_PAID_FALLBACK, allowedPaid.target)
    }

    @Test
    fun voiceUpgradeIntentParserHandlesReleaseUpdateQueriesOnly() {
        assertEquals(ReleaseVoiceAction.CHECK_FOR_UPDATES, VoiceUpgradeIntentParser.parse("Hey JARVIS check for updates"))
        assertEquals(ReleaseVoiceAction.UPGRADE_SELF, VoiceUpgradeIntentParser.parse("Hey JARVIS, upgrade yourself"))
        assertNull(VoiceUpgradeIntentParser.parse("What is the weather today?"))
    }

    @Test
    fun voiceResponseCacheStoresAndEvictsCleanly() {
        VoiceResponseCache.clear()
        val mp3 = byteArrayOf(1, 2, 3, 4)
        VoiceResponseCache.put("bulbul:v3:shubh:en-IN", "Yes Boss", mp3)
        assertNotNull(VoiceResponseCache.get("bulbul:v3:shubh:en-IN", "Yes Boss"))
        assertEquals(1, VoiceResponseCache.size())
        VoiceResponseCache.clear()
        assertFalse(VoiceResponseCache.size() > 0)
    }
}
