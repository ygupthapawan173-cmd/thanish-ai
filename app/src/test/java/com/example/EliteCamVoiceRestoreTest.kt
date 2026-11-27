package com.example
import com.example.voice.VoiceHealthPolicy
import com.example.ai.GeminiCompatibility
import org.junit.Test
import org.junit.Assert.*
class EliteCamVoiceRestoreTest {
    @Test fun zeroFramesAndDeadRecorderAreDetected() {
        assertFalse(VoiceHealthPolicy.stalled(12000,11900,0))
        assertTrue(VoiceHealthPolicy.stalled(12001,11900,0))
        assertTrue(VoiceHealthPolicy.stalled(9000,0,0))
        assertFalse(VoiceHealthPolicy.stalled(18000,17999,17900))
    }
    @Test fun cloudPrivacy() {
        assertTrue(VoiceHealthPolicy.canUseCloud(false,false,false))
        assertFalse(VoiceHealthPolicy.canUseCloud(true,false,false))
        assertFalse(VoiceHealthPolicy.canUseCloud(false,true,false))
        assertFalse(VoiceHealthPolicy.canUseCloud(false,false,true))
    }
    @Test fun capabilityRetryIsNotAuthenticationRetry() {
        assertTrue(GeminiCompatibility.canDowngrade("HTTP 400: model does not support tools",true,false))
        assertFalse(GeminiCompatibility.canDowngrade("HTTP 400: invalid API key",true,false))
        assertFalse(GeminiCompatibility.canDowngrade("HTTP 400: model does not support tools",true,true))
        assertTrue(GeminiCompatibility.tryAnotherModel("HTTP 502: bad gateway"))
        assertFalse(GeminiCompatibility.tryAnotherModel("HTTP 403: forbidden"))
    }
}
