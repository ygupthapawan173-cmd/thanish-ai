package com.example
import com.example.ai.GeminiCompatibility
import com.example.voice.VoicePackPolicy
import org.junit.Assert.*
import org.junit.Test
class ObsidianVoiceApiTest {
    @Test fun separatesTextLiveAndTtsModels() {
        assertTrue(GeminiCompatibility.isChat("models/gemini-3.8-flash"))
        assertTrue(GeminiCompatibility.isTts("gemini-2.5-flash-preview-tts"))
        assertFalse(GeminiCompatibility.isChat("gemini-2.5-flash-preview-tts"))
        assertFalse(GeminiCompatibility.isChat("gemini-3.8-flash-lite-tts"))
        assertFalse(GeminiCompatibility.isChat("gemini-2.5-flash-native-audio-preview"))
        assertFalse(GeminiCompatibility.isChat("gemini-3.8-live"))
        assertFalse(GeminiCompatibility.isChat("gemini-2.5-flash-image"))
    }
    @Test fun retrySpecificCapabilityErrorsNotBadCredentials() {
        assertTrue(GeminiCompatibility.tryAnotherModel("HTTP 400: requested response modality TEXT is not supported"))
        assertTrue(GeminiCompatibility.tryAnotherModel("HTTP 429: quota"))
        assertFalse(GeminiCompatibility.tryAnotherModel("HTTP 400: invalid API key"))
        assertFalse(GeminiCompatibility.tryAnotherModel("HTTP 401: unauthenticated"))
    }
    @Test fun languageErrorCanUseBundledTelugu() {
        assertTrue(VoicePackPolicy.eligible(12))
        assertTrue(VoicePackPolicy.eligible(13))
        assertEquals("model-te-in",VoicePackPolicy.assetFor("te-IN"))
        assertEquals("model-en-us",VoicePackPolicy.assetFor("en-IN"))
        assertFalse(VoicePackPolicy.eligible(9))
    }
}
