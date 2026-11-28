package com.example

import com.example.ai.GeminiModelSelectionPolicy
import com.example.omega.FastWakeDetector
import com.example.voice.AutoLanguageSession
import com.example.voice.BackgroundVoicePolicy
import com.example.voice.HandsFreeWakePolicy
import com.example.voice.MicLease
import com.example.voice.TeluguVoiceReplies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class V109WakeSpeedRegressionTest {

    @Before
    fun resetAutoLanguage() {
        AutoLanguageSession.reset()
    }

    @Test
    fun duplicateCleanupIsIdempotentAndReleasesMicLeaseExactlyOnce() {
        var cancelCalls = 0
        var shutdownCalls = 0
        var closeRecognizerCalls = 0
        var releaseCalls = 0

        val lease = MicLease()
        val wakeOwner = Any()
        val detector = FastWakeDetector(lease, wakeOwner) { 0.5f }

        val session = object : FastWakeDetector.Session {
            override fun cancel() { cancelCalls++ }
            override fun shutdown() { shutdownCalls++ }
            override fun closeRecognizer() { closeRecognizerCalls++ }
        }

        assertTrue(detector.acquireAndBind(session))
        assertTrue(lease.owns(wakeOwner))

        val releaseAction = {
            releaseCalls++
            lease.release(wakeOwner)
        }

        assertTrue("First cleanup should report active session cleaned", detector.stopAndReleaseMic(releaseAction))
        assertFalse("Second cleanup must be a no-op", detector.stopAndReleaseMic(releaseAction))
        assertFalse("Third cleanup must be a no-op", detector.stopAndReleaseMic(releaseAction))

        assertEquals(1, cancelCalls)
        assertEquals(1, shutdownCalls)
        assertEquals(1, closeRecognizerCalls)
        assertEquals(1, releaseCalls)
        assertFalse(lease.owns(wakeOwner))
    }

    @Test
    fun microphoneLeasePreventsConflictingOwnershipAndAllowsHandoffAfterRelease() {
        val lease = MicLease()
        val wakeOwner = Any()
        val commandOwner = Any()
        val detector = FastWakeDetector(lease, wakeOwner) { 0.5f }

        val session = object : FastWakeDetector.Session {
            override fun cancel() {}
            override fun shutdown() {}
            override fun closeRecognizer() {}
        }

        assertTrue(detector.acquireAndBind(session))
        assertFalse("Command mic cannot acquire while wake holds lease", lease.acquire(commandOwner))

        lease.release(commandOwner)
        assertTrue(lease.owns(wakeOwner))

        assertTrue(detector.stopAndReleaseMic())
        assertTrue(lease.acquire(commandOwner))
        assertTrue(lease.owns(commandOwner))
        lease.release(commandOwner)
    }

    @Test
    fun autoLanguageDefaultsToTeluguAndFollowsLastRecognizedLanguage() {
        assertEquals("te-IN", AutoLanguageSession.resolveEffectiveLocale("auto"))
        assertEquals("te-IN", TeluguVoiceReplies.wakeAckLocale("auto"))
        assertTrue(TeluguVoiceReplies.wakeAck("auto").contains("బాస్"))
        assertFalse(TeluguVoiceReplies.wakeAck("auto").equals("Yes, Boss", ignoreCase = true))

        assertEquals("en-IN", AutoLanguageSession.recordRecognizedText("What is the weather today?"))
        assertEquals("en-IN", TeluguVoiceReplies.wakeAckLocale("auto"))
        assertEquals("Yes Boss, I am listening.", TeluguVoiceReplies.wakeAck("auto"))

        assertEquals("te-IN", AutoLanguageSession.recordRecognizedText("ఈరోజు వాతావరణం ఎలా ఉంది?"))
        assertEquals("te-IN", TeluguVoiceReplies.wakeAckLocale("auto"))
        assertTrue(TeluguVoiceReplies.wakeAck("auto").contains("బాస్"))

        assertEquals("en-IN", TeluguVoiceReplies.wakeAckLocale("en-IN"))
        assertEquals("te-IN", TeluguVoiceReplies.wakeAckLocale("te-IN"))
    }

    @Test
    fun geminiModelSelectionUsesProviderDiscoveredModels() {
        val discovered = listOf(
            "models/gemini-2.5-flash",
            "models/gemini-2.5-flash-preview-tts",
            "models/gemini-2.5-pro"
        )
        val fromDiscovered = GeminiModelSelectionPolicy.buildCandidates(
            primary = "gemini-3.8-flash",
            discoveredModels = discovered
        )
        assertFalse(fromDiscovered.contains("gemini-3.8-flash"))
        assertFalse(fromDiscovered.contains("gemini-2.5-flash-preview-tts"))
        assertEquals("gemini-2.5-flash", fromDiscovered.first())
        assertTrue(fromDiscovered.contains("gemini-2.5-pro"))
        assertEquals(listOf("gemini-2.5-flash", "gemini-2.5-pro"), fromDiscovered)

        val emptyDiscovery = GeminiModelSelectionPolicy.buildCandidates(
            primary = "gemini-3.8-flash",
            discoveredModels = emptyList()
        )
        assertEquals(listOf("gemini-3.8-flash", "gemini-3.5-flash-lite"), emptyDiscovery)
    }

    @Test
    fun currentGeminiDefaultsExcludeStaleAndUnverifiedIds() {
        assertEquals(listOf("gemini-3.5-flash-lite", "gemini-3.8-flash"),
            GeminiModelSelectionPolicy.buildCandidates("gemini-4-hydrogen", emptyList()))
        assertEquals(listOf("gemini-3.5-flash-lite", "gemini-3.8-flash"),
            GeminiModelSelectionPolicy.buildCandidates("gemini-2.5-flash-lite", emptyList()))
        assertEquals(listOf("gemini-3.5-flash-lite"),
            GeminiModelSelectionPolicy.buildCandidates("gemini-3.8-flash",
                listOf("models/gemini-3.5-flash-lite", "models/gemini-3.8-flash-lite-tts")))
        assertEquals(listOf("gemini-3.8-flash"),
            GeminiModelSelectionPolicy.buildCandidates("gemini-3.8-flash",
                listOf("gemini-3.5-flash-lite", "gemini-3.8-flash"), maxDiscovered = 1))
    }

    @Test
    fun teluguAccentedHeyJarvisMatchesWithoutFalseTriggers() {
        assertNotNull(BackgroundVoicePolicy.matchWake("hey jarvis", HandsFreeWakePolicy.phrases))
        assertNotNull(BackgroundVoicePolicy.matchWake("hey jarvish", HandsFreeWakePolicy.phrases))
        assertNotNull(BackgroundVoicePolicy.matchWake("hi jarvis battery", HandsFreeWakePolicy.phrases))
        assertNull(BackgroundVoicePolicy.matchWake("jarvis", HandsFreeWakePolicy.phrases))
        assertNull(BackgroundVoicePolicy.matchWake("service", HandsFreeWakePolicy.phrases))
        assertNull(BackgroundVoicePolicy.matchWake("hey service", HandsFreeWakePolicy.phrases))
    }
}
