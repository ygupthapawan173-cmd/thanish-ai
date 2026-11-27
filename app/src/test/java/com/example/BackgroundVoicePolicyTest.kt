package com.example

import com.example.voice.BackgroundVoicePolicy
import com.example.voice.VoiceRecoveryPolicy
import org.junit.Assert.*
import org.junit.Test

class BackgroundVoicePolicyTest {
    @Test fun offlineCommandsAreExactAndBilingual() {
        assertEquals(BackgroundVoicePolicy.Action.TORCH_ON, BackgroundVoicePolicy.classify("turn on flashlight"))
        assertEquals(BackgroundVoicePolicy.Action.TIME, BackgroundVoicePolicy.classify("సమయం ఎంత"))
        assertEquals(BackgroundVoicePolicy.Action.BATTERY, BackgroundVoicePolicy.classify("Battery level?"))
        assertEquals(BackgroundVoicePolicy.Action.QUESTION, BackgroundVoicePolicy.classify("Do not turn on flashlight"))
        assertEquals(BackgroundVoicePolicy.Action.QUESTION, BackgroundVoicePolicy.classify("send WhatsApp message"))
    }
    @Test fun conversationIsBoundedAndOptIn() {
        assertFalse(BackgroundVoicePolicy.followUp(false,1))
        assertTrue(BackgroundVoicePolicy.followUp(true,2))
        assertFalse(BackgroundVoicePolicy.followUp(true,3))
        assertEquals(BackgroundVoicePolicy.Action.STOP,BackgroundVoicePolicy.classify("stop listening"))
    }
    @Test fun recoverableErrorsHaveBoundedRetry() {
        for(code in listOf(5,6,7,8,11,12,13)) {
            assertTrue(VoiceRecoveryPolicy.retryable(code,true,0))
            assertFalse(VoiceRecoveryPolicy.retryable(code,true,2))
        }
        assertFalse(VoiceRecoveryPolicy.retryable(9,true,0))
    }
}
