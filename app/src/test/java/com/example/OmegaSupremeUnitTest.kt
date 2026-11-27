package com.example

import com.example.autonomous.SelfCodingEngine
import com.example.data.AiProvider
import com.example.data.ApiQuotaTracker
import com.example.data.MemoryFact
import com.example.data.OmegaSettings
import com.example.data.SemanticMemoryEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmegaSupremeUnitTest {

    @Test
    fun onlyTwoCloudProvidersExistGeminiAndOpenRouter() {
        val providers = AiProvider.entries
        assertEquals(2, providers.size)
        assertTrue(providers.contains(AiProvider.GEMINI))
        assertTrue(providers.contains(AiProvider.OPENROUTER))
    }

    @Test
    fun separateChatAndLiveModelsPreserved() {
        val s = OmegaSettings(
            geminiChatModel = "gemini-3.8-flash",
            geminiLiveVoiceModel = "gemini-2.5-flash-native-audio-preview",
            openRouterModel = "google/gemini-2.5-flash"
        )
        assertEquals("gemini-3.8-flash", s.activeChatModel())
        assertEquals("gemini-2.5-flash-native-audio-preview", s.geminiLiveVoiceModel)
    }


    @Test
    fun selfCodingSecurityScannerBlocksEmbeddedGitHubTokensAndBadBraces() {
        val leaked = "package com.example\nval t = \"ghp_1234567890abcdefghij1234567890\""
        val resLeaked = SelfCodingEngine.scanForSecurityAndSyntax("Test.kt", leaked)
        assertFalse(resLeaked.passed)

        val unbalanced = "package com.example\nclass Broken " + "{"
        val resUnbal = SelfCodingEngine.scanForSecurityAndSyntax("Broken.kt", unbalanced)
        assertFalse(resUnbal.passed)

        val clean = "package com.example\nclass Clean { fun ok() = 1 }"
        val resClean = SelfCodingEngine.scanForSecurityAndSyntax("Clean.kt", clean)
        assertTrue(resClean.passed)
    }

    @Test
    fun semanticMemorySearchRanksRelevantFactsFirst() {
        val memories = listOf(
            MemoryFact(1, "pet_dog", "My dog name is Jannu"),
            MemoryFact(2, "favorite_coffee", "Filter coffee in the morning"),
            MemoryFact(3, "home_city", "Gudivada Andhra Pradesh")
        )
        val ranked = SemanticMemoryEngine.search("What is my dog name?", memories, topK = 2)
        assertEquals("pet_dog", ranked.first().key)
    }

    @Test
    fun apiQuotaTrackerRecordsLatencyAndTokens() {
        ApiQuotaTracker.reset()
        ApiQuotaTracker.recordSuccess("gemini-3.8-flash", 100, 200, 320L)
        val snap = ApiQuotaTracker.snapshot.value
        assertEquals(1, snap.totalRequests)
        assertEquals(320L, snap.averageLatencyMs)
        assertEquals(100L, snap.reportedPromptTokens)
        assertEquals(200L, snap.reportedOutputTokens)
        assertEquals(1, snap.requestsWithoutCost)
    }
}
