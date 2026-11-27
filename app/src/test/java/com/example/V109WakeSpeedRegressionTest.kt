package com.example

import com.example.ai.GeminiModelSelectionPolicy
import com.example.omega.FastWakeDetector
import com.example.voice.AutoLanguageSession
import com.example.voice.BackgroundVoicePolicy
import com.example.voice.HandsFreeWakePolicy
import com.example.voice.MicLease
import com.example.voice.OwnerModelRecoveryTracker
import com.example.voice.SpeakerProfilePolicy
import com.example.voice.TeluguVoiceReplies
import com.example.voice.TtsRouteDecision
import com.example.voice.TtsRoutingPolicy
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

    // 1. Duplicate cleanup & idempotent single MicLease release
    @Test
    fun duplicateCleanupIsIdempotentAndReleasesMicLeaseExactlyOnce() {
        var cancelCalls = 0
        var shutdownCalls = 0
        var closeRecognizerCalls = 0
        var releaseCalls = 0

        val lease = MicLease()
        val wakeOwner = Any()
        val detector = FastWakeDetector(lease, wakeOwner, { 0.5f })

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

    // 2. Microphone ownership isolation (single active owner via MicLease)
    @Test
    fun microphoneLeasePreventsConflictingOwnershipAndAllowsHandoffAfterRelease() {
        val lease = MicLease()
        val wakeOwner = Any()
        val commandOwner = Any()
        val detector = FastWakeDetector(lease, wakeOwner, { 0.5f })

        val session = object : FastWakeDetector.Session {
            override fun cancel() {}
            override fun shutdown() {}
            override fun closeRecognizer() {}
        }

        assertTrue(detector.acquireAndBind(session))
        assertFalse("Command mic cannot acquire while wake holds lease", lease.acquire(commandOwner))

        // Foreign owner must not be able to release wake's lease
        lease.release(commandOwner)
        assertTrue(lease.owns(wakeOwner))

        // After wake releases mic, command mic acquires cleanly
        assertTrue(detector.stopAndReleaseMic())
        assertTrue(lease.acquire(commandOwner))
        assertTrue(lease.owns(commandOwner))
        lease.release(commandOwner)
    }

    // 3. Independent TTS routing (does NOT depend on OmegaPreferences.preferOfflineSpeech)
    @Test
    fun ttsRoutingRespectsIndependentLocalFirstAndExplicitGeminiOrAndroidSettings() {
        // Default local-first routes to Android TTS when no cloud voice is enabled
        assertEquals(
            TtsRouteDecision.ANDROID_LOCAL,
            TtsRoutingPolicy.decide(
                localFirstEnabled = true,
                geminiTtsExplicitlyEnabled = false,
                cloudTtsExplicitlyEnabled = false,
                androidEngineExplicitlySelected = false,
                forceLocalOnly = false
            )
        )

        // Explicitly selected Gemini TTS is respected (with bounded timeout + fallback)
        assertEquals(
            TtsRouteDecision.GEMINI_TTS_WITH_FALLBACK,
            TtsRoutingPolicy.decide(
                localFirstEnabled = true,
                geminiTtsExplicitlyEnabled = true,
                cloudTtsExplicitlyEnabled = false,
                androidEngineExplicitlySelected = false,
                forceLocalOnly = false
            )
        )

        // Explicitly selected cloud voice takes precedence over a saved Android fallback voice
        assertEquals(
            TtsRouteDecision.PAID_CLOUD_WITH_FALLBACK,
            TtsRoutingPolicy.decide(
                localFirstEnabled = true,
                geminiTtsExplicitlyEnabled = false,
                cloudTtsExplicitlyEnabled = true,
                androidEngineExplicitlySelected = true,
                forceLocalOnly = false
            )
        )

        // forceLocalOnly overrides all cloud routes
        assertEquals(
            TtsRouteDecision.ANDROID_LOCAL,
            TtsRoutingPolicy.decide(
                localFirstEnabled = false,
                geminiTtsExplicitlyEnabled = true,
                cloudTtsExplicitlyEnabled = true,
                androidEngineExplicitlySelected = false,
                forceLocalOnly = true
            )
        )

        assertTrue(TtsRoutingPolicy.cloudSynthesisTimeoutMs(isShortAck = true) < TtsRoutingPolicy.cloudSynthesisTimeoutMs(isShortAck = false))
    }

    // 4. Owner-model recovery is bounded and NEVER bypasses owner verification
    @Test
    fun ownerModelRecoveryIsBoundedAndNeverBypassesOwnerVerification() {
        val tracker = OwnerModelRecoveryTracker(maxAttempts = 3)
        var now = 1_000L

        assertFalse("Cannot arm wake with owner filter enabled before model loads", tracker.canArmWake(true, false))

        // Attempt 1 fails
        assertTrue(tracker.beginLoad(now))
        assertEquals(OwnerModelRecoveryTracker.LoadState.IDLE, tracker.onLoadFailure(now))
        assertEquals(1, tracker.attempts)
        assertFalse("Must respect retry backoff window", tracker.beginLoad(now + 100L))

        // Attempt 2 fails
        now = tracker.nextRetryAtMs
        assertTrue(tracker.beginLoad(now))
        assertEquals(OwnerModelRecoveryTracker.LoadState.IDLE, tracker.onLoadFailure(now))
        assertEquals(2, tracker.attempts)

        // Attempt 3 fails -> FAILED_BOUNDED
        now = tracker.nextRetryAtMs
        assertTrue(tracker.beginLoad(now))
        assertEquals(OwnerModelRecoveryTracker.LoadState.FAILED_BOUNDED, tracker.onLoadFailure(now))
        assertEquals(3, tracker.attempts)

        // Attempt 4 must be blocked (bounded) AND owner verification must still NOT be bypassed
        assertFalse(tracker.beginLoad(now + 60_000L))
        assertFalse("Owner verification must never be bypassed after load failure", tracker.canArmWake(true, false))
        assertTrue("Non-owner-filtered mode can arm without speaker model", tracker.canArmWake(false, false))
    }

    @Test
    fun ownerVerificationNeverBypassesShortSamplesOrPartialResults() {
        assertFalse(
            HandsFreeWakePolicy.acceptWithOwnerGuard(
                text = "hey jarvis",
                confidence = listOf(0.95, 0.95),
                threshold = 0.55,
                isPartial = true,
                ownerVerificationEnabled = true,
                spkFrames = 200,
                minFrames = SpeakerProfilePolicy.minFrames,
                ownerVerified = true
            )
        )
        assertFalse(
            HandsFreeWakePolicy.acceptWithOwnerGuard(
                text = "hey jarvis",
                confidence = listOf(0.95, 0.95),
                threshold = 0.55,
                isPartial = false,
                ownerVerificationEnabled = true,
                spkFrames = SpeakerProfilePolicy.minFrames - 10,
                minFrames = SpeakerProfilePolicy.minFrames,
                ownerVerified = true
            )
        )
        assertTrue(
            HandsFreeWakePolicy.acceptWithOwnerGuard(
                text = "hey jarvis",
                confidence = listOf(0.95, 0.95),
                threshold = 0.55,
                isPartial = false,
                ownerVerificationEnabled = true,
                spkFrames = SpeakerProfilePolicy.minFrames + 20,
                minFrames = SpeakerProfilePolicy.minFrames,
                ownerVerified = true
            )
        )
    }

    // 5. Auto language defaults to Telugu and tracks last successfully recognized language
    @Test
    fun autoLanguageDefaultsToTeluguAndFollowsLastRecognizedLanguage() {
        // Initial Auto default is Telugu
        assertEquals("te-IN", AutoLanguageSession.resolveEffectiveLocale("auto"))
        assertEquals("te-IN", TeluguVoiceReplies.wakeAckLocale("auto"))
        assertTrue(TeluguVoiceReplies.wakeAck("auto").contains("బాస్"))
        assertFalse(TeluguVoiceReplies.wakeAck("auto").equals("Yes, Boss", ignoreCase = true))

        // After successful English recognition, Auto mode switches to en-IN
        assertEquals("en-IN", AutoLanguageSession.recordRecognizedText("What is the weather today?"))
        assertEquals("en-IN", TeluguVoiceReplies.wakeAckLocale("auto"))
        assertEquals("Yes Boss, I am listening.", TeluguVoiceReplies.wakeAck("auto"))

        // After successful Telugu recognition, Auto mode switches back to te-IN
        assertEquals("te-IN", AutoLanguageSession.recordRecognizedText("ఈరోజు వాతావరణం ఎలా ఉంది?"))
        assertEquals("te-IN", TeluguVoiceReplies.wakeAckLocale("auto"))
        assertTrue(TeluguVoiceReplies.wakeAck("auto").contains("బాస్"))

        // Explicit language overrides Auto session
        assertEquals("en-IN", TeluguVoiceReplies.wakeAckLocale("en-IN"))
        assertEquals("te-IN", TeluguVoiceReplies.wakeAckLocale("te-IN"))
    }

    // 6. Provider-discovered Gemini model selection without unverified hardcoded models
    @Test
    fun geminiModelSelectionUsesProviderDiscoveredModelsAndExcludesUnverifiedPlaceholders() {
        val discovered = listOf(
            "models/gemini-2.5-flash",
            "models/gemini-2.5-flash-preview-tts", // excluded (not chat)
            "models/gemini-2.5-pro"
        )
        val fromDiscovered = GeminiModelSelectionPolicy.buildCandidates(
            primary = "gemini-3.8-flash", // unverified placeholder not in discovery
            discoveredModels = discovered
        )
        assertFalse("Unverified placeholder must not be prioritized over discovered models", fromDiscovered.contains("gemini-3.8-flash"))
        assertFalse("TTS model must be filtered out", fromDiscovered.contains("gemini-2.5-flash-preview-tts"))
        assertEquals("gemini-2.5-flash", fromDiscovered.first())
        assertTrue(fromDiscovered.contains("gemini-2.5-pro"))

        // Empty discovery cache falls back to verified models without unverified placeholders
        val emptyDiscovery = GeminiModelSelectionPolicy.buildCandidates(
            primary = "gemini-3.8-flash",
            discoveredModels = emptyList()
        )
        assertFalse(emptyDiscovery.contains("gemini-3.8-flash"))
        assertFalse(emptyDiscovery.contains("gemini-3.5-flash-lite"))
        assertEquals(listOf("gemini-2.5-flash", "gemini-2.5-flash-lite"), emptyDiscovery)
    }

    // 7. Telugu-accented wake recognition without false triggers
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
