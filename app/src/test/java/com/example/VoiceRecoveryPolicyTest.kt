package com.example

import com.example.voice.VoiceRecoveryPolicy
import org.junit.Assert.*
import org.junit.Test

class VoiceRecoveryPolicyTest {
    @Test fun recoveryIsBounded() {
        for (code in listOf(5,8,11)) {
            assertTrue(VoiceRecoveryPolicy.retryable(code,true,0))
            assertTrue(VoiceRecoveryPolicy.retryable(code,true,1))
            assertFalse(VoiceRecoveryPolicy.retryable(code,true,2))
        }
    }
    @Test fun permissionFailureNeverRetries() { assertFalse(VoiceRecoveryPolicy.retryable(9,true,0)) }
    @Test fun autoModeAlternatesLanguage() {
        assertEquals("te-IN",VoiceRecoveryPolicy.nextLocale("en-IN"))
        assertEquals("en-IN",VoiceRecoveryPolicy.nextLocale("te-IN"))
        assertTrue(VoiceRecoveryPolicy.retryable(7,true,0))
        assertFalse(VoiceRecoveryPolicy.retryable(7,false,0))
    }
}
