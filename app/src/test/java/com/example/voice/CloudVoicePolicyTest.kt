package com.example.voice
import org.junit.Assert.*
import org.junit.Test
class CloudVoicePolicyTest {
    @Test fun selectsGenuineSpeakers() {
        for (id in CloudVoicePolicy.english) assertEquals(id, CloudVoicePolicy.select("Hello",id,"anushka",true).voice)
        for (id in CloudVoicePolicy.telugu) assertEquals(id, CloudVoicePolicy.select("తెలుగు", "onyx",id,false).voice)
        assertEquals("sarvam/bulbul-v2",CloudVoicePolicy.select("తెలుగు English","onyx","priya",true).model)
        assertEquals("openai/tts-1-hd",CloudVoicePolicy.select("Hello","onyx","priya",true).model)
    }
    @Test fun paidVoiceRequiresExplicitConsent() {
        for (enabled in listOf(false,true)) for(gateway in listOf(false,true)) for(free in listOf(false,true)) for(privateMode in listOf(false,true)) for(foreground in listOf(false,true)) for(key in listOf(false,true)) for(len in listOf(0,1,1000,1001)) {
            assertEquals(enabled && gateway && !free && !privateMode && foreground && key && len in 1..1000, CloudVoicePolicy.allowed(CloudVoicePolicy.BASE,enabled,gateway,free,0.0,privateMode,foreground,key,len))
        }
        assertFalse(CloudVoicePolicy.allowed("https://other.invalid/v1",true,true,false,0.0,false,true,true,10))
        assertFalse(CloudVoicePolicy.allowed(CloudVoicePolicy.BASE,true,true,false,1.0,false,true,true,10))
        assertFalse(CloudVoicePolicy.audioType("application/json"))
        assertTrue(CloudVoicePolicy.audioType("audio/mpeg"))
    }
}
