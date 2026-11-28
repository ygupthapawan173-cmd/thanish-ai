package com.example

import com.example.voice.VoiceSessionPolicy
import org.junit.Assert.*
import org.junit.Test

class V46VoiceSessionPolicyTest {
    @Test fun lockedAlwaysLocal() {
        assertTrue(VoiceSessionPolicy.localOnly(false, true))
        assertTrue(VoiceSessionPolicy.localOnly(true, false))
        assertFalse(VoiceSessionPolicy.localOnly(false, false))
    }

    @Test fun watchdogBounded() {
        assertEquals(20_000L, VoiceSessionPolicy.ttsTimeoutMs(0))
        assertEquals(55_000L, VoiceSessionPolicy.ttsTimeoutMs(100))
        assertEquals(600_000L, VoiceSessionPolicy.ttsTimeoutMs(Int.MAX_VALUE))
    }
}
