package com.example.omega

import org.junit.Assert.*
import org.junit.Test

class OmegaR106RecoveryTest {
    @Test fun hybridFallbackOnlyOnceAndOnlyWhenAvailable() {
        assertTrue(OmegaRecoveryPolicy.platformFallback(OmegaVoiceMode.HYBRID, true, false, true, 12))
        assertTrue(OmegaRecoveryPolicy.platformFallback(OmegaVoiceMode.HYBRID, true, false, true, 5))
        assertFalse(OmegaRecoveryPolicy.platformFallback(OmegaVoiceMode.HYBRID, true, true, true, 12))
        assertFalse(OmegaRecoveryPolicy.platformFallback(OmegaVoiceMode.HYBRID, true, false, false, 12))
        assertFalse(OmegaRecoveryPolicy.platformFallback(OmegaVoiceMode.HYBRID, false, false, true, 12))
    }
    @Test fun localNeverFallsBackAndPermissionDenialNeverRetries() {
        for (code in 1..15) assertFalse(OmegaRecoveryPolicy.platformFallback(OmegaVoiceMode.LOCAL, true, false, true, code))
        assertFalse(OmegaRecoveryPolicy.platformFallback(OmegaVoiceMode.HYBRID, true, false, true, 9))
    }
    @Test fun pendingReviewPreventsWakeOverwrite() {
        assertFalse(OmegaRecoveryPolicy.mayAcceptWake("pending command"))
        assertTrue(OmegaRecoveryPolicy.mayAcceptWake(""))
        assertTrue(OmegaRecoveryPolicy.keepListeningAfterCapture(true))
        assertFalse(OmegaRecoveryPolicy.keepListeningAfterCapture(false))
    }
    @Test fun accessibilitySettingIsNotServiceBinding() {
        assertEquals("Enabled in Android; service not bound", OmegaRecoveryPolicy.accessibilityLabel(true,false))
        assertEquals("Disabled in Android settings", OmegaRecoveryPolicy.accessibilityLabel(false,false))
        assertEquals("Connected", OmegaRecoveryPolicy.accessibilityLabel(true,true))
        assertEquals("Binding state changing; refresh", OmegaRecoveryPolicy.accessibilityLabel(false,true))
    }
}
