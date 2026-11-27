package com.example.voice
import org.junit.Assert.*
import org.junit.Test
class InstalledSpeechPolicyTest {
    @Test fun paidVoiceNeedsFreshOptIn() {
        assertFalse(InstalledSpeechPolicy.paidAllowed(false,true))
        assertFalse(InstalledSpeechPolicy.paidAllowed(true,false))
        assertTrue(InstalledSpeechPolicy.paidAllowed(true,true))
    }
    @Test fun freeModeRejectsNetworkVoice() {
        assertFalse(InstalledSpeechPolicy.networkVoiceAllowed(false,false,false))
        assertFalse(InstalledSpeechPolicy.voiceEligible("en","en",false,true,false))
        assertTrue(InstalledSpeechPolicy.voiceEligible("en","en",false,false,false))
    }
    @Test fun languageAndMissingData() {
        assertEquals("te",InstalledSpeechPolicy.language("Hello బాస్"))
        assertEquals("en",InstalledSpeechPolicy.language("Hello Boss"))
        assertFalse(InstalledSpeechPolicy.voiceEligible("te","en",false,false,false))
        assertFalse(InstalledSpeechPolicy.voiceEligible("te","te",true,false,false))
    }
}
