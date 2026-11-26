package com.example

import com.example.ai.AiRetryPolicy
import com.example.ai.OmegaContext
import com.example.ai.ReplyStyle
import com.example.omega.OmegaWakeAck
import com.example.omega.OmegaWakeEngine
import com.example.omega.WakeAckStyle
import com.example.omega.WakeGate
import com.example.omega.WakeSensitivity
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class OmegaR103SafetyTest {
    @Test fun safeWakeMatching() {
        val phrases = listOf("hey jarvis", "hey omega")
        assertEquals("open settings", OmegaWakeEngine.match(
            "Hey Jarvis open settings", phrases)?.tail)
        assertEquals("", OmegaWakeEngine.match("Hey JARVIS", phrases)?.tail)
        assertNull(OmegaWakeEngine.match("I said hey jarvis", phrases))
        assertNull(OmegaWakeEngine.match("hey jarvisian", phrases))
        assertNull(OmegaWakeEngine.match("hey jorvis", phrases))
        assertNotNull(OmegaWakeEngine.match(
            "hey singularty", listOf("hey singularity"), WakeSensitivity.BALANCED))
        assertNull(OmegaWakeEngine.match(
            "hey panda", listOf("hey pandu"), WakeSensitivity.SENSITIVE))
        assertNull(OmegaWakeEngine.match(
            "omego", listOf("omega"), WakeSensitivity.SENSITIVE))
        assertEquals("hey pandu", OmegaWakeEngine.validCustomPhrase(" Hey   Pandu "))
        assertNull(OmegaWakeEngine.validCustomPhrase("omega"))
        assertNull(OmegaWakeEngine.validCustomPhrase("a b"))
    }

    @Test fun wakeCooldownForPartialAndFinal() {
        val gate = WakeGate(1600L)
        assertTrue(gate.allow(10000))
        assertFalse(gate.allow(10010))
        assertFalse(gate.allow(11599))
        assertTrue(gate.allow(11600))
        gate.reset()
        assertTrue(gate.allow(11601))
    }

    @Test fun replyAndEchoSafety() {
        assertEquals("Yes, Sir!", OmegaWakeAck.spokenText(WakeAckStyle.YES_SIR, ""))
        assertEquals("చెప్పండి బాస్", OmegaWakeAck.spokenText(WakeAckStyle.TELUGU_BOSS, ""))
        assertNull(OmegaWakeAck.spokenText(WakeAckStyle.SILENT, ""))
        assertEquals("hello", OmegaWakeAck.sanitizeCustom("  hello  "))
        assertEquals(40, OmegaWakeAck.sanitizeCustom("a".repeat(80)).length)
        assertTrue(OmegaWakeAck.isEcho("Yes sir", "Yes, Sir!"))
        assertFalse(OmegaWakeAck.isEcho("yes", "Yes, Sir!"))
        assertFalse(OmegaWakeAck.isEcho("yes sir open maps", "Yes, Sir!"))
        assertEquals(1f, OmegaWakeAck.clampRate(Float.NaN))
    }

    @Test fun boundedNetworkRetries() {
        assertEquals(700L, AiRetryPolicy.retryDelayMs(503, null, 0))
        assertEquals(1400L, AiRetryPolicy.retryDelayMs(503, null, 1))
        assertNull(AiRetryPolicy.retryDelayMs(503, null, 2))
        assertEquals(2250L, AiRetryPolicy.retryDelayMs(429, "2", 0))
        assertNull(AiRetryPolicy.retryDelayMs(429, "40", 0))
        assertNull(AiRetryPolicy.retryDelayMs(401, null, 0))
        assertTrue(AiRetryPolicy.isTransientNetwork(SocketTimeoutException()))
        assertFalse(AiRetryPolicy.isTransientNetwork(UnknownHostException()))
    }

    @Test fun contextAndStyles() {
        assertEquals(listOf("bb", "ccc"),
            OmegaContext.recent(listOf("aaaaa", "bb", "ccc"), 3, 5) { it.length })
        assertTrue(OmegaContext.recent(listOf("a".repeat(20000)), 20, 12000) {
            it.length
        }.isEmpty())
        assertEquals(300, ReplyStyle.SHORT.maxTokens)
        assertTrue(ReplyStyle.TELUGU_ONLY.systemWith("base").contains("Telugu script"))
    }
}
