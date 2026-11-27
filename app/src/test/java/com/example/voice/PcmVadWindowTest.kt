package com.example.voice
import org.junit.Assert.*
import org.junit.Test
class PcmVadWindowTest {
    @Test fun oddReadAndContextPreserved() {
        val w = PcmVadWindow(); val out = mutableListOf<FloatArray>()
        val positive = ByteArray(1024) { if (it % 2 == 0) 0 else 64 }
        w.feed(positive.copyOfRange(0, 1), 1) { out.add(it) }
        assertTrue(out.isEmpty())
        w.feed(positive.copyOfRange(1, 1024), 1023) { out.add(it) }
        assertEquals(1, out.size)
        assertTrue(out[0].take(64).all { it == 0f })
        assertTrue(out[0].drop(64).all { it == 0.5f })
        w.feed(ByteArray(1024), 1024) { out.add(it) }
        assertTrue(out[1].take(64).all { it == 0.5f })
        assertTrue(out[1].drop(64).all { it == 0f })
    }
}
