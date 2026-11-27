package com.example

import com.example.ai.AiBrainFamily
import com.example.ai.BrainAvailability
import com.example.ai.BrainThinkingMode
import com.example.ai.MasterAiBrain
import com.example.ai.MultiBrainPolicy
import com.example.ai.PerBrainQuotaTracker
import com.example.ai.ReasoningEffort
import com.example.ai.ResponseQualityVerifier
import com.example.ai.SmartModelPolicy
import com.example.ai.VerificationStatus
import com.example.autonomous.AutomationConditionType
import com.example.autonomous.ConditionalAutomationRule
import com.example.autonomous.PersonalAssistantEngine
import com.example.autonomous.ReleaseVersionPolicy
import com.example.control.DocumentIntelligenceHelper
import com.example.data.ChatMessageEntity
import com.example.data.OmegaSettings
import com.example.voice.LiveVoiceChoices
import com.example.voice.TeluguVoiceReplies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class V110UltraIntelligenceTest {

    @Before
    fun resetQuotaTracker() {
        PerBrainQuotaTracker.resetForTest()
    }

    @Test
    fun allTenBrainsClassifyRealDiscoveredModelsAndRespectZeroPaidCredits() {
        assertEquals(10, AiBrainFamily.entries.size)
        assertEquals(AiBrainFamily.GEMINI_DIRECT, MultiBrainPolicy.classifyModelFamily("gemini-2.5-flash", true))
        assertEquals(AiBrainFamily.OPENAI_GPT, MultiBrainPolicy.classifyModelFamily("openai/gpt-4o"))
        assertEquals(AiBrainFamily.GPT_OSS_REASONING, MultiBrainPolicy.classifyModelFamily("openai/gpt-oss-120b"))
        assertEquals(AiBrainFamily.CLAUDE_CODING, MultiBrainPolicy.classifyModelFamily("anthropic/claude-3.7-sonnet"))
        assertEquals(AiBrainFamily.DEEPSEEK_REASONING, MultiBrainPolicy.classifyModelFamily("deepseek/deepseek-r1:free"))
        assertEquals(AiBrainFamily.QWEN_INTELLIGENCE, MultiBrainPolicy.classifyModelFamily("qwen/qwen-2.5-coder-32b-instruct"))
        assertEquals(AiBrainFamily.GROK_AI, MultiBrainPolicy.classifyModelFamily("x-ai/grok-3"))
        assertEquals(AiBrainFamily.KIMI_LONG_CONTEXT, MultiBrainPolicy.classifyModelFamily("moonshotai/kimi-k2"))
        assertEquals(AiBrainFamily.MISTRAL_FAST, MultiBrainPolicy.classifyModelFamily("mistralai/mistral-small-3.1-24b-instruct:free"))
        assertEquals(AiBrainFamily.LLAMA_AI, MultiBrainPolicy.classifyModelFamily("meta-llama/llama-3.3-70b-instruct:free"))

        val s = OmegaSettings(
            geminiApiKey = "AIzaTestFakeKeyNotReal00000000000",
            openRouterApiKey = "sk-or-v1-test-not-real",
            smartMode = SmartModelPolicy.Mode.SMART,
            zeroPaidCreditsMode = true
        )
        val discoveredGateway = listOf(
            SmartModelPolicy.Model("deepseek/deepseek-r1:free", promptPrice = 0.0, completionPrice = 0.0),
            SmartModelPolicy.Model("anthropic/claude-3.7-sonnet", promptPrice = 0.003, completionPrice = 0.015)
        )
        val inventory = MultiBrainPolicy.buildBrainInventory(
            settings = s,
            discoveredGeminiModels = listOf("gemini-2.5-flash"),
            discoveredGatewayModels = discoveredGateway
        )
        val gemini = inventory.first { it.brain == AiBrainFamily.GEMINI_DIRECT }
        val deepseek = inventory.first { it.brain == AiBrainFamily.DEEPSEEK_REASONING }
        val claude = inventory.first { it.brain == AiBrainFamily.CLAUDE_CODING }
        val grok = inventory.first { it.brain == AiBrainFamily.GROK_AI }

        assertEquals(BrainAvailability.FREE_READY, gemini.availability)
        assertEquals(BrainAvailability.FREE_READY, deepseek.availability)
        assertEquals(BrainAvailability.BLOCKED_ZERO_CREDITS, claude.availability)
        assertEquals(BrainAvailability.NOT_DISCOVERED, grok.availability)
    }

    @Test
    fun masterAiBrainHandlesTenglishPlanningTranslationAndEffortModes() {
        val tenglish = MasterAiBrain.classifyAndPlan("Ee roju vaatavaranam cheppu")
        assertEquals("te-IN", tenglish.responseLanguage)

        val translate = MasterAiBrain.classifyAndPlan("Translate good morning into Telugu")
        assertTrue(translate.isTranslationRequest)

        val deepPlan = MasterAiBrain.classifyAndPlan(
            userMessage = "Explain distributed consensus",
            thinkingMode = BrainThinkingMode.DEEP_THINKING
        )
        assertEquals(ReasoningEffort.HIGH, deepPlan.effort)
        assertEquals(7, deepPlan.pipelineStages.size)
    }

    @Test
    fun responseQualityVerifierFlagsUngroundedLiveAndActionClaims() {
        val actionMismatch = ResponseQualityVerifier.verify(
            userPrompt = "Turn on Wi-Fi",
            replyText = "I have turned on Wi-Fi for you.",
            executedTools = emptyList(),
            usedWebSearch = false,
            expectedLanguage = "en-IN"
        )
        assertEquals(VerificationStatus.CAUTION_ACTION_CLAIM, actionMismatch.status)

        val liveUngrounded = ResponseQualityVerifier.verify(
            userPrompt = "What is the Bitcoin price today?",
            replyText = "Bitcoin is around $90,000.",
            executedTools = emptyList(),
            usedWebSearch = false,
            expectedLanguage = "en-IN"
        )
        assertEquals(VerificationStatus.CAUTION_UNGROUNDED_LIVE, liveUngrounded.status)

        val valid = ResponseQualityVerifier.verify(
            userPrompt = "What is the weather today?",
            replyText = "Today in Hyderabad it is 29°C and clear.",
            executedTools = listOf("live_web_search"),
            usedWebSearch = true,
            expectedLanguage = "en-IN"
        )
        assertEquals(VerificationStatus.VERIFIED, valid.status)
    }

    @Test
    fun personalAssistantAndDocumentHelpersWorkLocallyAndSafely() {
        assertTrue(PersonalAssistantEngine.proactiveSuggestions(false, 10, 5, 8, 0).isEmpty())
        val sugs = PersonalAssistantEngine.proactiveSuggestions(true, 12, 4, 8, 0)
        assertEquals(3, sugs.size)

        val history = listOf(
            ChatMessageEntity(text = "check battery and volume", isFromUser = true),
            ChatMessageEntity(text = "check battery and volume", isFromUser = true)
        )
        assertTrue(PersonalAssistantEngine.suggestRoutinesFromHistory(false, history, emptyList()).isEmpty())
        assertEquals(1, PersonalAssistantEngine.suggestRoutinesFromHistory(true, history, emptyList()).size)

        val rules = listOf(
            ConditionalAutomationRule("1", AutomationConditionType.BATTERY_BELOW_15, "Save power", true),
            ConditionalAutomationRule("2", AutomationConditionType.BLUETOOTH_CONNECTED, "Brief", false)
        )
        val triggered = PersonalAssistantEngine.evaluateConditionalRules(rules, batteryPercent = 12, isCharging = false, bluetoothConnected = true, unreadNotifCount = 0)
        assertEquals(1, triggered.size)
        assertEquals("1", triggered.single().id)

        assertTrue(DocumentIntelligenceHelper.isSupportedTextExtension("notes.md"))
        assertFalse(DocumentIntelligenceHelper.isSupportedTextExtension("malware.exe"))
        val big = "a".repeat(DocumentIntelligenceHelper.MAX_TEXT_CHARS + 200)
        assertTrue(DocumentIntelligenceHelper.truncateDocumentText(big).contains("Truncated"))
    }

    @Test
    fun liveVoiceChoicesIncludesAll30VoicesAndV110VersionIsNewer() {
        assertEquals(31, LiveVoiceChoices.names.size)
        assertTrue(ReleaseVersionPolicy.newer("V110 ULTRA INTELLIGENCE MASTER", "V108.2"))
        assertNotNull(TeluguVoiceReplies.wakeAck("te-IN"))
    }
}
