package com.example
import com.example.omega.OmegaPolicy
import org.junit.Assert.*
import org.junit.Test
class OmegaPolicyTest {
    @Test fun wake() {
        assertEquals("", OmegaPolicy.wakeTail("Hey JARVIS!"))
        assertEquals("open settings", OmegaPolicy.wakeTail("Hey Jarvis open settings"))
        assertNull(OmegaPolicy.wakeTail("I said hey Jarvis"))
        assertNull(OmegaPolicy.wakeTail("hey jarvisian"))
    }
    @Test fun search() {
        assertTrue(OmegaPolicy.matches("నా తెలుగు జ్ఞాపకం", "తెలుగు"))
        assertFalse(OmegaPolicy.matches("నా తెలుగు జ్ఞాపకం", "హిందీ"))
        assertTrue(OmegaPolicy.matches("Open Router models", "router mod"))
    }
    @Test fun limits() {
        assertEquals(300000L, OmegaPolicy.retryMillis(429, "9999999"))
        assertEquals(30000L, OmegaPolicy.retryMillis(429, "bad"))
        assertEquals(10000L, OmegaPolicy.retryMillis(503, null))
        assertEquals(0L, OmegaPolicy.retryMillis(401, null))
        assertTrue(OmegaPolicy.modelId("openrouter/free"))
        assertFalse(OmegaPolicy.modelId("../secret"))
        assertFalse(OmegaPolicy.modelId("model?key=abc"))
    }
}
