package com.example

import com.example.ai.SmartModelPolicy as P
import com.example.data.IntelligenceMode
import org.junit.Assert.*
import org.junit.Test

class SmartModelPolicyTest {
    private val catalog = listOf(
        P.Model("gemini-4-hydrogen", true, true, 0.0, 0.0),
        P.Model("gemini-4-carbon", true, true, 0.0, 0.0),
        P.Model("gpt-6.2-astra", false, true, 5.0, 15.0),
        P.Model("gemini-3.8-live"),
        P.Model("anthropic/claude-sonnet", true, true, 2.0, 5.0)
    )

    @Test fun classifiesTasksAndExcludesThirdPartyOrAudioModels() {
        assertEquals(P.Task.CODE, P.task("Kotlin code debug", false))
        assertEquals(P.Task.REASONING, P.task("Calculate 85 - 32", false))
        assertEquals(P.Task.VISION, P.task("describe", true))
        assertEquals(P.Task.CHAT, P.task("hello", false))

        assertFalse(P.eligible(catalog[3], P.Task.CHAT))
        assertFalse(P.eligible(catalog[4], P.Task.CHAT))
        assertFalse(P.eligible(catalog[2], P.Task.VISION))
    }

    @Test fun routesFastSmartDeepModesWithFreePriority() {
        assertEquals("gemini-4-hydrogen", P.rank(catalog, P.Task.CHAT, IntelligenceMode.FAST, "").first().id)
        assertEquals("gemini-4-carbon", P.rank(catalog, P.Task.REASONING, IntelligenceMode.DEEP, "").first().id)
        assertEquals("gpt-6.2-astra", P.rank(catalog, P.Task.REASONING, IntelligenceMode.DEEP, "gpt-6.2-astra", freePriority = false).first().id)
    }

    @Test fun retryOnlyKnownRecoverableStatuses() {
        assertTrue(P.mayTryAnother(429))
        assertTrue(P.mayTryAnother(404))
        assertTrue(P.mayTryAnother(503))
        for (status in listOf(400, 401, 402, 403)) assertFalse(P.mayTryAnother(status))
    }
}
