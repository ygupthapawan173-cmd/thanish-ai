package com.example

import com.example.ai.AiActivityState
import com.example.ai.CatalogVerificationState
import com.example.ai.CreatorIdentity
import com.example.ai.KnowledgeDomain
import com.example.ai.MasterAiBrain
import com.example.ai.ModelCatalogPolicy
import com.example.ai.RouteTarget
import com.example.ai.SmartAiTaskRouter
import com.example.ai.WebResearchClient
import com.example.autonomous.ReleaseVersionPolicy
import com.example.autonomous.ReleaseVoiceAction
import com.example.autonomous.UpdateTransferPolicy
import com.example.autonomous.VoiceUpgradeIntentParser
import com.example.data.AiProvider
import com.example.data.ApiSlot
import com.example.data.ApiSlotSelectionPolicy
import com.example.data.IntelligenceMode
import com.example.data.SlotProvider
import com.example.data.SlotStatus
import com.example.voice.HandsFreeWakePolicy
import com.example.voice.SarvamVoiceClient
import com.example.voice.StreamingVoiceEngineKind
import com.example.voice.VoiceEngineSelector
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V110MasterIntelligenceTest {

    @Test
    fun requestedEightBrainAndFiveVoiceModelsMapCleanlyAndExcludeThirdParty() {
        assertEquals(8, ModelCatalogPolicy.REQUESTED_BRAIN_MODELS.size)
        assertEquals(5, ModelCatalogPolicy.REQUESTED_VOICE_MODELS.size)
        assertEquals(20, KnowledgeDomain.entries.size)
        assertEquals(6, AiActivityState.entries.size)

        assertTrue(ModelCatalogPolicy.isSupportedProviderModel("gemini-4-carbon"))
        assertTrue(ModelCatalogPolicy.isSupportedProviderModel("gpt-6.2-astra"))
        for (removed in listOf("claude-3-opus", "deepseek-r1", "qwen-2.5", "grok-3", "kimi-k2", "mistral-large", "llama-3.3", "gpt-oss-120b")) {
            assertFalse("Must exclude removed model $removed", ModelCatalogPolicy.isSupportedProviderModel(removed))
        }

        val resolved = ModelCatalogPolicy.evaluateCatalog(
            discoveredGemini = listOf("models/gemini-4-hydrogen", "models/gemini-2.5-flash"),
            discoveredOpenAi = listOf("gpt-6.2-sol", "gpt-4o-mini"),
            hasGeminiKey = true,
            hasOpenAiKey = true
        )
        val hydrogen = resolved.first { it.spec.officialId == "gemini-4-hydrogen" }
        val carbon = resolved.first { it.spec.officialId == "gemini-4-carbon" }
        val sol = resolved.first { it.spec.officialId == "gpt-6.2-sol" }
        assertEquals(CatalogVerificationState.VERIFIED_IN_CATALOG, hydrogen.state)
        assertEquals(CatalogVerificationState.SUBSTITUTED, carbon.state)
        assertEquals("gemini-2.5-flash", carbon.effectiveModelId)
        assertEquals(CatalogVerificationState.VERIFIED_IN_CATALOG, sol.state)
    }

    @Test
    fun masterBrainClassifiesDomainsWebSearchDeepResearchAndTelugu() {
        val news = MasterAiBrain.classifyAndPlan("Today latest news in Hyderabad", IntelligenceMode.FAST)
        assertTrue(news.requiresWebSearch)
        assertEquals(KnowledgeDomain.LIVE_NEWS_ANALYSIS, news.domain)

        val deep = MasterAiBrain.classifyAndPlan("Deep research comparing solid state battery chemistry", IntelligenceMode.SMART)
        assertTrue(deep.prefersDeepResearch)
        assertEquals(IntelligenceMode.DEEP, deep.intelligenceMode)

        val url = MasterAiBrain.classifyAndPlan("Summarize https://developer.android.com/about/versions/16", IntelligenceMode.SMART)
        assertEquals("https://developer.android.com/about/versions/16", url.extractedUrl)
        assertEquals(KnowledgeDomain.WEBSITE_UNDERSTANDING, url.domain)

        val mythology = MasterAiBrain.classifyAndPlan("Explain Kakatiya dynasty history", IntelligenceMode.SMART)
        assertEquals(KnowledgeDomain.INDIAN_HISTORY_MYTHOLOGY, mythology.domain)

        val te = MasterAiBrain.classifyAndPlan("ఈరోజు తాజా వార్తలు చెప్పు", IntelligenceMode.SMART)
        assertEquals("te-IN", te.responseLanguage)
        assertTrue(te.requiresWebSearch)
    }

    @Test
    fun smartRouterRespectsFreePriorityAndPaidAuthorization() {
        val searchPlan = MasterAiBrain.classifyAndPlan("Latest cricket score today", IntelligenceMode.SMART)
        val routeSearch = SmartAiTaskRouter.route(
            plan = searchPlan,
            activeProvider = AiProvider.GEMINI,
            hasGeminiKey = true,
            hasOpenAiKey = false,
            freePriority = true,
            allowPaidModels = false,
            preferredGeminiModel = "gemini-4-hydrogen",
            preferredOpenAiModel = "gpt-6.2-sol"
        )
        assertEquals(RouteTarget.LIVE_WEB_SEARCH, routeSearch.target)

        val deepPlan = MasterAiBrain.classifyAndPlan("Deep research on superconductors", IntelligenceMode.DEEP)
        val routeDeep = SmartAiTaskRouter.route(
            plan = deepPlan,
            activeProvider = AiProvider.GEMINI,
            hasGeminiKey = true,
            hasOpenAiKey = false,
            freePriority = true,
            allowPaidModels = false,
            preferredGeminiModel = "gemini-4-carbon",
            preferredOpenAiModel = "gpt-6.2-sol"
        )
        assertEquals(RouteTarget.DEEP_RESEARCH_ENGINE, routeDeep.target)
    }

    @Test
    fun webResearchClientEnforcesSsrfStripsHtmlAndExtractsCitations() {
        val client = WebResearchClient()
        assertFalse(client.isPublicHttpsUrl("http://example.com"))
        assertFalse(client.isPublicHttpsUrl("https://localhost/admin"))
        assertFalse(client.isPublicHttpsUrl("https://127.0.0.1/status"))
        assertFalse(client.isPublicHttpsUrl("https://169.254.169.254/latest/meta-data"))
        assertFalse(client.isPublicHttpsUrl("https://192.168.1.1"))

        val clean = client.stripHtmlToPlainText("<html><script>alert(1)</script><body><h1>News</h1><p>Hello &amp; world</p></body></html>")
        assertTrue(clean.contains("News Hello & world"))
        assertFalse(clean.contains("alert"))

        val geminiMeta = JSONObject().put(
            "groundingChunks",
            JSONArray().put(JSONObject().put("web", JSONObject().put("title", "Android 16").put("uri", "https://developer.android.com/16")))
        )
        val gemRefs = WebResearchClient.parseGeminiGroundingSources(geminiMeta)
        assertEquals(1, gemRefs.size)
        assertEquals("Android 16", gemRefs.first().title)

        val oaiJson = JSONObject().put(
            "output",
            JSONArray().put(
                JSONObject().put(
                    "content",
                    JSONArray().put(
                        JSONObject()
                            .put("text", "Verified update summary.")
                            .put("annotations", JSONArray().put(JSONObject().put("title", "OpenAI Docs").put("url", "https://platform.openai.com/docs")))
                    )
                )
            )
        )
        val (oaiText, oaiRefs) = WebResearchClient.parseOpenAiResponsesOutput(oaiJson)
        assertEquals("Verified update summary.", oaiText)
        assertEquals(1, oaiRefs.size)
        assertEquals("OpenAI Docs", oaiRefs.first().title)
    }

    @Test
    fun apiSlotSelectionRespectsPaidGateInvalidAuthAndRateLimits() {
        val now = 1_000_000L
        val slots = listOf(
            ApiSlot(id = "paid_oai", label = "OpenAI", provider = SlotProvider.OPENAI, apiKey = "sk-test", isPaidTier = true, priority = 1),
            ApiSlot(id = "free_gem", label = "Gemini", provider = SlotProvider.GEMINI, apiKey = "AIzaTest", isPaidTier = false, priority = 2),
            ApiSlot(id = "bad_gem", label = "Bad", provider = SlotProvider.GEMINI, apiKey = "bad", status = SlotStatus.INVALID_AUTH, isPaidTier = false, priority = 0),
            ApiSlot(id = "cool_gem", label = "Cool", provider = SlotProvider.GEMINI, apiKey = "cool", cooldownUntilMs = now + 20_000L, isPaidTier = false, priority = 0)
        )

        val freeOnly = ApiSlotSelectionPolicy.selectChatSlots(slots, AiProvider.OPENAI, freePriority = true, allowPaidModels = false, nowMs = now)
        assertEquals(listOf("free_gem"), freeOnly.map { it.id })

        val withPaid = ApiSlotSelectionPolicy.selectChatSlots(slots, AiProvider.OPENAI, freePriority = false, allowPaidModels = true, nowMs = now)
        assertEquals(listOf("paid_oai", "free_gem"), withPaid.map { it.id })
        assertTrue(ApiSlotSelectionPolicy.backoffMsForHttp(429, 1) >= 45_000L)
    }

    @Test
    fun sarvamVoiceVoiceEngineSelectorWakeAndSelfUpgradePoliciesWork() {
        assertEquals(3, SarvamVoiceClient.MALE_SPEAKERS.size)
        assertEquals("te-IN", SarvamVoiceClient.detectLanguageCode("ఈరోజు వార్తలు", "auto"))
        assertEquals("en-IN", SarvamVoiceClient.detectLanguageCode("Hello Boss", "auto"))
        val normTe = SarvamVoiceClient.normalizeForSarvamSpeech("32°C & 50%```val x=1```", true)
        assertTrue(normTe.contains("డిగ్రీల") && normTe.contains("శాతం"))

        VoiceEngineSelector.recordLatency(StreamingVoiceEngineKind.SARVAM_SAARAS_V4, 240L)
        VoiceEngineSelector.recordLatency(StreamingVoiceEngineKind.GEMINI_TRANSCRIBE_LIVE, 510L)
        assertEquals(
            StreamingVoiceEngineKind.VOSK_OFFLINE_FALLBACK,
            VoiceEngineSelector.selectBest(hasInternet = true, isLocked = true, cloudConsentGranted = true, hasSarvamKey = true, hasGeminiKey = true).selectedEngine
        )
        assertEquals(
            StreamingVoiceEngineKind.VOSK_OFFLINE_FALLBACK,
            VoiceEngineSelector.selectBest(hasInternet = true, isLocked = false, cloudConsentGranted = false, hasSarvamKey = true, hasGeminiKey = true).selectedEngine
        )
        assertEquals(
            StreamingVoiceEngineKind.SARVAM_SAARAS_V4,
            VoiceEngineSelector.selectBest(hasInternet = true, isLocked = false, cloudConsentGranted = true, hasSarvamKey = true, hasGeminiKey = true).selectedEngine
        )

        assertTrue(HandsFreeWakePolicy.matchesPartialWake("Hey JARVIS what time is it"))
        assertEquals("what time is it", HandsFreeWakePolicy.stripWakePrefix("Hey JARVIS what time is it"))

        assertTrue(CreatorIdentity.reply("Who created JARVIS INFINITY?")!!.contains("Guptha"))
        assertTrue(CreatorIdentity.reply("నిన్ను ఎవరు తయారు చేశారు?")!!.contains("గుప్తా"))
        assertEquals(ReleaseVoiceAction.CHECK_FOR_UPDATES, VoiceUpgradeIntentParser.parse("కొత్త version ఉందా"))
        assertEquals(ReleaseVoiceAction.UPGRADE_SELF, VoiceUpgradeIntentParser.parse("upgrade yourself"))
        assertTrue(ReleaseVersionPolicy.newer("v111", "V110 MASTER INTELLIGENCE"))
        assertTrue(UpdateTransferPolicy.validRepo("ygupthapawan173-cmd/thanish-ai"))
    }
}
