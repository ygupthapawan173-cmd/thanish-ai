package com.example
import com.example.security.PortableBackupCipher as B
import org.junit.Test
import org.junit.Assert.*
class PortableBackupCipherTest {
    @Test fun authenticatedRoundTripAndRandomness() {
        val p="JUnit only example password".toCharArray()
        val raw="తెలుగు data".toByteArray()
        val a=B.seal(raw,p); val b=B.seal(raw,p)
        assertArrayEquals(raw,B.open(a,p))
        assertFalse(a.contentEquals(b))
        assertTrue(runCatching { B.open(a,"Incorrect password for test".toCharArray()) }.isFailure)
        a[a.lastIndex]=(a.last().toInt() xor 1).toByte()
        assertTrue(runCatching { B.open(a,p) }.isFailure)
        p.fill('\u0000')
    }
}
