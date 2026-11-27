package com.example.voice
import org.junit.Assert.*
import org.junit.Test
class TtsChunkPolicyTest {
    @Test fun longMultilingualTextIsPreserved() {
        val text="తెలుగు English 😀 ".repeat(1000)
        for (limit in listOf(2,3,31,4000)) {
            val chunks=TtsChunkPolicy.split(text,limit)
            assertEquals(text,chunks.joinToString(""))
            assertTrue(chunks.all { it.isNotEmpty() && it.length<=limit && !Character.isHighSurrogate(it.last()) && !Character.isLowSurrogate(it.first()) })
        }
    }
    @Test fun emptyTextHasNoChunks() { assertTrue(TtsChunkPolicy.split("",4000).isEmpty()) }
}
