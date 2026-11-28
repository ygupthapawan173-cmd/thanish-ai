package com.example

import com.example.data.AiProvider
import com.example.data.ApiQuotaTracker
import com.example.data.OmegaSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OmegaSupremeUnitTest {

    @Test
    fun onlyTwoCloudProvidersExistGeminiAndOpenAi() {
        val providers = AiProvider.entries
        assertEquals(2, providers.size)
        assertTrue(providers.contains(AiProvider.GEMINI))
        assertTrue(providers.contains(AiProvider.OPENAI))
    }

    @Test
    fun separateChatAndLiveModelsPreserved() {
        val s = OmegaSettings(
            geminiChatModel = "gemini-4-hydrogen",
            geminiLiveVoiceModel = "gemini-3.8-live",
            openAiModel = "gpt-6.2-sol"
        )
        assertEquals("gemini-4-hydrogen", s.activeChatModel())
        assertEquals("gemini-3.8-live", s.geminiLiveVoiceModel)
    }

    @Test
    fun apiQuotaTrackerRecordsLatencyAndTokens() {
        ApiQuotaTracker.reset()
        ApiQuotaTracker.recordSuccess("gemini-4-hydrogen", 100, 200, 320L)
        val snap = ApiQuotaTracker.snapshot.value
        assertEquals(1, snap.totalRequests)
        assertEquals(320L, snap.averageLatencyMs)
        assertEquals(100L, snap.reportedPromptTokens)
        assertEquals(200L, snap.reportedOutputTokens)
        assertEquals(1, snap.requestsWithoutCost)
    }
}
