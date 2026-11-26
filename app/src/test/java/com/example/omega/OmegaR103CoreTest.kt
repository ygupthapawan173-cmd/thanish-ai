package com.example.omega

import com.example.ai.AiRetryPolicy
import com.example.ai.OmegaContext
import com.example.ai.ReplyStyle
import org.junit.Assert.*
import org.junit.Test

class OmegaR103CoreTest {
    @Test fun wakeMatchedAtStartAndCommandPreserved() {
        val detected = OmegaWakeEngine.match("Hey Jarvis open camera", listOf("hey jarvis"))
        assertNotNull(detected)
        assertEquals("open camera", detected!!.tail)
        assertNull(OmegaWakeEngine.match("I said hey jarvis", listOf("hey jarvis")))
    }

    @Test fun strictDoesNotFalselyWake() {
        assertNull(OmegaWakeEngine.match("hey panda", listOf("hey pandu")))
        assertNull(OmegaWakeEngine.match("hey jarlis", listOf("hey jarvis")))
    }

    @Test fun gateStopsDuplicatePartialAndFinalEvents() {
        val gate = WakeGate(1600L)
        assertTrue(gate.allow(5000L))
        assertFalse(gate.allow(5200L))
        assertTrue(gate.allow(6800L))
    }

    @Test fun customPhraseValidation() {
        assertEquals("hey guardian", OmegaWakeEngine.validCustomPhrase("Hey Guardian"))
        assertNull(OmegaWakeEngine.validCustomPhrase("jarvis"))
        assertNull(OmegaWakeEngine.validCustomPhrase("a b"))
    }

    @Test fun echoDoesNotSuppressShortCommands() {
        assertTrue(OmegaWakeAck.isEcho("yes sir", "Yes, Sir!"))
        assertFalse(OmegaWakeAck.isEcho("yes", "Yes, Sir!"))
        assertFalse(OmegaWakeAck.isEcho("yes please", "Yes, Sir!"))
    }

    @Test fun contextNeverExceedsBudget() {
        val x = listOf("short", "12345678901234567890")
        val out = OmegaContext.recent(x, 20, 12) { it.length }
        assertEquals(listOf("short"), out)
        assertTrue(out.sumOf { it.length } <= 12)
    }

    @Test fun retryIsBoundedAndRateLimitRequiresShortRetryAfter() {
        assertEquals(700L, AiRetryPolicy.retryDelayMs(503, null, 0))
        assertEquals(1400L, AiRetryPolicy.retryDelayMs(503, null, 1))
        assertNull(AiRetryPolicy.retryDelayMs(503, null, 2))
        assertEquals(2250L, AiRetryPolicy.retryDelayMs(429, "2", 0))
        assertNull(AiRetryPolicy.retryDelayMs(429, "9", 0))
    }

    @Test fun stylePromptHasExplicitInstruction() {
        assertEquals("base", ReplyStyle.BALANCED.systemWith("base"))
        assertTrue(ReplyStyle.TELUGU_ONLY.systemWith("base").contains("Telugu"))
    }
}
