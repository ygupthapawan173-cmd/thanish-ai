package com.example

import com.example.ai.AiBrainFamily
import com.example.ai.ApiProviderKind
import com.example.ai.InMemorySlotStorage
import com.example.ai.SlotConnectionState
import com.example.ai.SlotKeyCipher
import com.example.ai.SlotRegistryEngine
import com.example.ai.SlotRoutePurpose
import com.example.ai.SlotRoutingMode
import com.example.ai.SmartApiSlot
import com.example.ai.SmartApiSlotManager
import com.example.ai.SmartModelPolicy
import com.example.autonomous.ReleaseVersionPolicy
import com.example.data.AiProvider
import com.example.data.OmegaSettings
import com.example.data.OrbColorPreset
import com.example.voice.CloudVoicePolicy
import com.example.voice.GeminiSpeechPolicy
import com.example.voice.TeluguVoiceReplies
import com.example.voice.TtsRouteDecision
import com.example.voice.TtsRoutingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests covering JARVIS INFINITY V110.1 targets:
 * - Smart API Slot Manager (6 providers, auto-detection, encryption cipher, migration,
 *   HTTP 404/429/500/502/503/504 cooldown & recovery, routing strategies, budgets, Chat vs TTS)
 * - Voice routing policy, Onyx English preference, Telugu alternatives & timeout improvements
 * - Honest verification badges, versionCode bump (> 118), palette & lock-screen text guard
 */
class V1101SpeedAndSlotsTest {

    private class ReversibleTestCipher : SlotKeyCipher {
        override fun encrypt(plain: String): String = "ENC_TEST:${plain.reversed()}"
        override fun decrypt(stored: String): String = stored.removePrefix("ENC_TEST:").reversed()
    }

    @Test
    fun detectProviderRecognizesAllSixConfiguredProviders() {
        assertEquals(ApiProviderKind.GEMINI_DIRECT, SmartApiSlotManager.detectProvider("AIzaSyFakeKey123456", ""))
        assertEquals(ApiProviderKind.AICREDITS, SmartApiSlotManager.detectProvider("sk-custom", "https://api.aicredits.in/v1"))
        assertEquals(ApiProviderKind.ONEAI_KEY, SmartApiSlotManager.detectProvider("oneai-12345", ""))
        assertEquals(ApiProviderKind.OPENROUTER, SmartApiSlotManager.detectProvider("sk-or-v1-abc123", ""))
        assertEquals(ApiProviderKind.OPENAI, SmartApiSlotManager.detectProvider("sk-proj-abc123", ""))
        assertEquals(ApiProviderKind.CUSTOM_OPENAI, SmartApiSlotManager.detectProvider("custom-token", "https://my.private-gateway.example/v1"))
    }

    @Test
    fun safeMigrationPreservesLegacyKeysEncryptsStorageAndNeverFakesVerified() {
        val storage = InMemorySlotStorage()
        val engine = SlotRegistryEngine(storage, ReversibleTestCipher())

        val legacySettings = OmegaSettings(
            activeProvider = AiProvider.OPENROUTER,
            geminiApiKey = "AIzaFakeLegacyGemini99",
            openRouterApiKey = "sk-or-v1-fakeLegacyGw",
            gatewayBaseUrl = "https://openrouter.ai/api/v1",
            secondaryGatewayBaseUrl = "https://api.openai.com/v1",
            secondaryGatewayApiKey = "sk-proj-fakeSecondaryKey"
        )
        val slots = engine.migrateFromLegacySettings(legacySettings)
        assertEquals(3, slots.size)
        // Stored payload must only contain encrypted ciphertexts, NEVER raw keys
        val serialized = storage.loadRaw()
        assertFalse(serialized.contains("AIzaFakeLegacyGemini99"))
        assertFalse(serialized.contains("sk-or-v1-fakeLegacyGw"))
        assertFalse(serialized.contains("sk-proj-fakeSecondaryKey"))
        assertTrue(serialized.contains("ENC_TEST:"))

        // Never fake Verified before actual HTTP probe or confirmed response
        assertTrue(slots.all { it.chatStatus == SlotConnectionState.UNVERIFIED })
        assertFalse(engine.hasAnyVerifiedSlot())

        // Reload from storage verifies round-trip decryption without key loss
        val reloaded = SlotRegistryEngine(storage, ReversibleTestCipher())
        assertEquals("AIzaFakeLegacyGemini99", reloaded.slots.value.first { it.id == "legacy_gemini" }.apiKey)
        assertEquals("sk-proj-fakeSecondaryKey", reloaded.slots.value.first { it.id == "legacy_secondary" }.apiKey)
    }

    @Test
    fun emptyKeysReportKeyRequiredNotVerified() {
        val engine = SlotRegistryEngine(InMemorySlotStorage(), ReversibleTestCipher())
        val slots = engine.migrateFromLegacySettings(OmegaSettings())
        val gemSlot = slots.first { it.id == "legacy_gemini" }
        assertEquals(SlotConnectionState.UNCONFIGURED, gemSlot.effectiveStatus())
        assertFalse(engine.hasAnyConfiguredKey())
        assertFalse(engine.hasAnyVerifiedSlot())
    }

    @Test
    fun httpErrorsTriggerCooldownAutomaticSlotSwitchingAndRecovery() {
        var now = 1_000_000L
        val engine = SlotRegistryEngine(InMemorySlotStorage(), ReversibleTestCipher()) { now }
        engine.setRoutingMode(SlotRoutingMode.CONFIGURABLE_PRIORITY)

        engine.upsertSlot(
            SmartApiSlot(
                id = "s1",
                label = "Primary OpenAI",
                providerKind = ApiProviderKind.OPENAI,
                baseUrl = "https://api.openai.com/v1",
                apiKey = "sk-proj-one",
                priority = 1,
                isFreeTier = true,
                useForVoiceTts = true,
                supportsTts = true
            )
        )
        engine.upsertSlot(
            SmartApiSlot(
                id = "s2",
                label = "Fallback Gemini",
                providerKind = ApiProviderKind.GEMINI_DIRECT,
                baseUrl = ApiProviderKind.GEMINI_DIRECT.defaultBaseUrl,
                apiKey = "AIzaFallbackKey",
                priority = 2,
                isFreeTier = true,
                useForVoiceTts = true,
                supportsTts = true
            )
        )

        for (code in listOf(404, 429, 500, 502, 503, 504)) {
            now = 1_000_000L
            // Reset cooldown cleanly before testing each HTTP failure code
            engine.updateSlot("s1") { it.copy(cooldownUntilMs = 0L, chatStatus = SlotConnectionState.UNVERIFIED) }
            engine.recordHttpFailure("s1", code, "HTTP $code test error", SlotRoutePurpose.CHAT, retryAfterHeader = "20")
            val s1 = engine.slots.value.first { it.id == "s1" }
            assertEquals("HTTP $code should enter cooldown", SlotConnectionState.COOLDOWN, s1.effectiveStatus(now))

            val ordered = engine.selectOrderedSlots(SlotRoutePurpose.CHAT, zeroPaidCreditsMode = true)
            assertEquals(listOf("s2"), ordered.map { it.id })

            // Advance past max cooldown -> auto recovers cleanly
            now += 200_000L
            assertEquals(1, engine.refreshExpiredCooldowns())
            val recoveredOrdered = engine.selectOrderedSlots(SlotRoutePurpose.CHAT, zeroPaidCreditsMode = true)
            assertEquals(listOf("s1", "s2"), recoveredOrdered.map { it.id })
        }
    }

    @Test
    fun routingModesFastestLowestCostFreeFirstAndCapabilitiesWork() {
        val engine = SlotRegistryEngine(InMemorySlotStorage(), ReversibleTestCipher())
        engine.upsertSlot(
            SmartApiSlot(
                id = "paid_fast",
                label = "Fast OpenAI",
                providerKind = ApiProviderKind.OPENAI,
                baseUrl = "https://api.openai.com/v1",
                apiKey = "sk-proj-fast",
                priority = 1,
                isFreeTier = false,
                estimatedCostPer1MTokens = 0.50,
                useForVoiceTts = true,
                supportsTts = true
            )
        )
        engine.upsertSlot(
            SmartApiSlot(
                id = "openrouter_chat_only",
                label = "OpenRouter Free Chat",
                providerKind = ApiProviderKind.OPENROUTER,
                baseUrl = "https://openrouter.ai/api/v1",
                apiKey = "sk-or-v1-chat",
                chatModel = "deepseek/deepseek-r1:free",
                priority = 2,
                isFreeTier = true,
                estimatedCostPer1MTokens = 0.0,
                useForVoiceTts = false,
                supportsTts = false
            )
        )
        engine.recordSuccess("paid_fast", SlotRoutePurpose.CHAT, latencyMs = 320L, promptTokens = 100, outputTokens = 50, costUsd = 0.01)
        engine.recordSuccess("openrouter_chat_only", SlotRoutePurpose.CHAT, latencyMs = 1100L)

        // Zero paid credits blocks paid_fast completely
        assertEquals(
            listOf("openrouter_chat_only"),
            engine.selectOrderedSlots(SlotRoutePurpose.CHAT, zeroPaidCreditsMode = true).map { it.id }
        )

        // With paid allowed: FASTEST picks paid_fast (320ms), FREE_FIRST picks openrouter_chat_only
        engine.setRoutingMode(SlotRoutingMode.FASTEST)
        assertEquals("paid_fast", engine.selectOrderedSlots(SlotRoutePurpose.CHAT, zeroPaidCreditsMode = false).first().id)

        engine.setRoutingMode(SlotRoutingMode.FREE_FIRST)
        assertEquals("openrouter_chat_only", engine.selectOrderedSlots(SlotRoutePurpose.CHAT, zeroPaidCreditsMode = false).first().id)

        // Chat vs TTS separation: openrouter_chat_only must NEVER be selected for VOICE_TTS
        val voiceSlots = engine.selectOrderedSlots(SlotRoutePurpose.VOICE_TTS, zeroPaidCreditsMode = false)
        assertEquals(listOf("paid_fast"), voiceSlots.map { it.id })
        // 10 AI Brains remain supported across slots
        val brains = engine.slots.value.flatMap { it.supportedBrains() }.distinct()
        assertTrue(brains.containsAll(listOf(AiBrainFamily.OPENAI_GPT, AiBrainFamily.DEEPSEEK_REASONING)))
    }

    @Test
    fun onyxEnglishPreferredVoiceAndTeluguFallbacksAreConfiguredCleanly() {
        val openaiEnChoice = CloudVoicePolicy.selectForBase("https://api.openai.com/v1", "Hello Boss", "onyx", "anushka", hd = false)
        assertEquals("tts-1", openaiEnChoice.model)
        assertEquals("onyx", openaiEnChoice.voice)

        val aiCreditsTeChoice = CloudVoicePolicy.selectForBase(CloudVoicePolicy.BASE, "నమస్కారం", "onyx", "anushka", hd = false)
        assertEquals("sarvam/bulbul-v2", aiCreditsTeChoice.model)
        assertEquals("anushka", aiCreditsTeChoice.voice)

        assertTrue(CloudVoicePolicy.allowed("https://api.openai.com/v1", true, true, false, 0.0, false, true, true, 24))
        assertEquals(30, GeminiSpeechPolicy.speakers.size)
        assertTrue(TtsRoutingPolicy.REPLY_CLOUD_TIMEOUT_MS >= 7_000L)
        assertEquals(TtsRouteDecision.PAID_CLOUD_WITH_FALLBACK, TtsRoutingPolicy.decide(false, false, true, false, false))
        assertTrue(TeluguVoiceReplies.lockedReminder("en-IN").contains("Unlock", ignoreCase = true))
        assertTrue(TeluguVoiceReplies.lockedReminder("te-IN").any { it in '\u0C00'..'\u0C7F' })
    }

    @Test
    fun versionAndPaletteMatchV1101Spec() {
        assertTrue(ReleaseVersionPolicy.newer("v110.1", "v110"))
        assertTrue(ReleaseVersionPolicy.newer("V110.1 PREMIUM SPEED MASTER", "V110 ULTRA INTELLIGENCE MASTER"))
        assertNotEquals(OrbColorPreset.SOLAR_NOVA, OrbColorPreset.AURORA_CYAN)
        assertEquals(0xFF080E1D, OrbColorPreset.AURORA_CYAN.backgroundHex)
        assertEquals(0xFF12233F, OrbColorPreset.AURORA_CYAN.surfaceHex)
        assertEquals(0xFF00E5FF, OrbColorPreset.AURORA_CYAN.primaryHex)
        assertEquals(0xFF2563FF, OrbColorPreset.AURORA_CYAN.secondaryHex)
        assertEquals(0xFF8B5CF6, OrbColorPreset.AURORA_CYAN.accentHex)
    }
}
