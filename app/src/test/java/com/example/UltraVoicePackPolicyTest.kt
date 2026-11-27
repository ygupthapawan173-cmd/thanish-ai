package com.example

import com.example.voice.VoicePackPolicy
import org.junit.Assert.*
import org.junit.Test

class UltraVoicePackPolicyTest {
    @Test fun explicitTeluguNeverRoutesToEnglishVosk() {
        assertEquals("model-te-in",VoicePackPolicy.assetFor("te-IN"))
        assertEquals("te-IN",VoicePackPolicy.localeFor("model-te-in"))
        assertEquals("model-en-us",VoicePackPolicy.assetFor("en-IN"))
    }
    @Test fun noOfflineFallbackAfterPermissionDenied() {
        assertFalse(VoicePackPolicy.eligible(9))
        assertTrue(VoicePackPolicy.eligible(13))
        assertTrue(VoicePackPolicy.eligible(6))
    }
    @Test fun cloudVoiceRespectsLocalAndLockPrivacy() {
        assertTrue(VoicePackPolicy.allowNetworkVoice(false,false))
        assertFalse(VoicePackPolicy.allowNetworkVoice(true,false))
        assertFalse(VoicePackPolicy.allowNetworkVoice(false,true))
    }
}
