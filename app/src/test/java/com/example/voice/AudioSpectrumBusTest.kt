package com.example.voice

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AudioSpectrumBusTest {
    @Test fun silenceHasNoArtificialBars() {
        assertTrue(AudioSpectrumBus.analyze(ByteArray(512),512,16000).all { it == 0f })
    }
    @Test fun fftDetectsActualTone() {
        val pcm=ByteArray(512)
        for(i in 0 until 256) {
            val v=(sin(2*PI*13*i/256)*16000).toInt()
            pcm[2*i]=v.toByte()
            pcm[2*i+1]=(v shr 8).toByte()
        }
        val bands=AudioSpectrumBus.analyze(pcm,pcm.size,16000)
        assertEquals(16,bands.size)
        assertEquals(3,bands.indices.maxByOrNull { bands[it] })
        assertTrue(bands[3]>0.1f)
    }
}
