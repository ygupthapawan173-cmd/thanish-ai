package com.example.voice
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
class GeminiSpeechPolicyTest {
    @Test fun catalog() {
        assertEquals(30, GeminiSpeechPolicy.speakers.toSet().size)
        for (v in GeminiSpeechPolicy.speakers) assertEquals(v, GeminiSpeechPolicy.speaker(v,"te"))
        assertEquals("Charon",GeminiSpeechPolicy.speaker("invalid","en"))
    }
    @Test fun route() {
        assertTrue(GeminiSpeechPolicy.allowed(true,true,false,false,true,true,1000))
        assertFalse(GeminiSpeechPolicy.allowed(true,true,true,false,true,true,1000))
        assertFalse(GeminiSpeechPolicy.allowed(true,true,false,true,true,true,1000))
        assertFalse(GeminiSpeechPolicy.allowed(true,true,false,false,true,false,1000))
        assertFalse(GeminiSpeechPolicy.allowed(true,true,false,false,true,true,1001))
    }
    @Test fun wav() {
        val raw=byteArrayOf(1,2,3,4)
        val wav=GeminiSpeechPolicy.wav(raw)
        assertEquals(24000,ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).getInt(24))
        assertArrayEquals(raw,wav.copyOfRange(44,wav.size))
        assertTrue(runCatching { GeminiSpeechPolicy.wav(byteArrayOf(1)) }.isFailure)
    }
}
