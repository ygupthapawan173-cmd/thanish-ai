package com.example

import com.example.ai.GeminiCompatibility
import com.example.voice.SpeakerProfilePolicy
import com.example.voice.LiveSilencePolicy
import org.junit.Assert.*
import org.junit.Test

class EliteVoicePolicyTest {
    @Test fun onlyCapabilityErrorsDowngrade() {
        val error="HTTP 400: Function calling is not enabled for this model"
        assertTrue(GeminiCompatibility.canDowngrade(error,true,false))
        assertFalse(GeminiCompatibility.canDowngrade(error,true,true))
        assertFalse(GeminiCompatibility.canDowngrade(error,false,false))
        assertFalse(GeminiCompatibility.canDowngrade("HTTP 400: Invalid API key",true,false))
        assertFalse(GeminiCompatibility.tryAnotherModel("HTTP 401: Unauthorized"))
        assertTrue(GeminiCompatibility.tryAnotherModel("HTTP 429: Quota"))
    }
    @Test fun ownerMatchRequiresTwoOfThreeAndValidVectors() {
        val refs=listOf(doubleArrayOf(1.0,0.0),doubleArrayOf(0.9,0.1),doubleArrayOf(0.8,0.2))
        assertTrue(SpeakerProfilePolicy.matches(refs,doubleArrayOf(1.0,0.05)))
        assertFalse(SpeakerProfilePolicy.matches(refs,doubleArrayOf(0.0,1.0)))
        assertFalse(SpeakerProfilePolicy.matches(refs,doubleArrayOf(Double.NaN,1.0)))
        assertFalse(SpeakerProfilePolicy.matches(refs.take(2),doubleArrayOf(1.0,0.0)))
        assertFalse(SpeakerProfilePolicy.matches(refs,doubleArrayOf(0.0,0.0)))
    }
    @Test fun silencePolicyRequiresSustainedAudio() {
        val vad=LiveSilencePolicy()
        repeat(25) { assertFalse(vad.update(0.01f)) }
        assertFalse(vad.update(0.4f)); assertFalse(vad.update(0.4f)); assertTrue(vad.update(0.4f))
        repeat(7) { assertTrue(vad.update(0.01f)) }
        assertFalse(vad.update(0.01f))
    }
}
