package com.example
import com.example.ai.SmartModelPolicy as P
import org.junit.Assert.*
import org.junit.Test
class SmartModelPolicyTest {
    private val catalog = listOf(P.Model("google/gemini-flash-lite", true, true, 0.1, 0.2), P.Model("anthropic/claude-sonnet", true, true, 2.0, 5.0), P.Model("deepseek/deepseek-r1", false, true, 1.0, 2.0), P.Model("audio/native-audio"), P.Model("unknown-chat"))
    @Test fun autoClassifiesTeluguAndEnglish() {
        assertEquals(P.Task.CODE, P.task("Kotlin code రాయి",false))
        assertEquals(P.Task.REASONING,P.task("లెక్క చేయి",false))
        assertEquals(P.Task.VISION,P.task("describe",true))
        assertEquals(P.Task.CHAT,P.task("hello",false))
    }
    @Test fun routesDifferentTasks() {
        assertEquals(catalog[0],P.rank(catalog,P.Task.CHAT,P.Mode.SMART,"").first())
        assertEquals(catalog[1],P.rank(catalog,P.Task.CODE,P.Mode.SMART,"").first())
        assertEquals(catalog[2],P.rank(catalog,P.Task.REASONING,P.Mode.SMART,"").first())
    }
    @Test fun excludesAudioAndUnknownVision() {
        assertFalse(P.eligible(catalog[3],P.Task.CHAT)); assertFalse(P.eligible(catalog[4],P.Task.VISION)); assertFalse(P.eligible(catalog[2],P.Task.VISION))
        assertEquals(2,P.rank(catalog,P.Task.VISION,P.Mode.SMART,"").size)
    }
    @Test fun economyAndManual() {
        assertEquals(catalog[0],P.rank(catalog,P.Task.CODE,P.Mode.ECONOMY,"").first())
        assertEquals(listOf(catalog[1]),P.rank(catalog,P.Task.CHAT,P.Mode.MANUAL,catalog[1].id))
        assertTrue(P.rank(catalog,P.Task.CHAT,P.Mode.MANUAL,"missing").isEmpty())
    }
    @Test fun retryOnlyKnownTemporaryOrUnavailable() {
        assertTrue(P.mayTryAnother(429)); assertTrue(P.mayTryAnother(404)); assertTrue(P.mayTryAnother(503))
        for (status in listOf(400,401,402,403)) assertFalse(P.mayTryAnother(status))
    }
    @Test fun fixesV22ClassifierAndMultimodalRegressions() {
        assertEquals(P.Task.REASONING, P.task("85 - 32",false))
        assertEquals(P.Task.REASONING, P.task("lekka cheppu",false))
        assertEquals(P.Task.CHAT, P.task("What is a postal code?",false))
        assertTrue(P.eligible(P.Model("vendor/image-audio-chat",true,true),P.Task.VISION))
        val m = P.Model("vendor/vision-chat",null,true)
        assertTrue(P.rank(listOf(m),P.Task.VISION,P.Mode.SMART,"",true).isEmpty())
        assertTrue(P.rank(listOf(m),P.Task.VISION,P.Mode.MANUAL,m.id).isEmpty())
        assertEquals(listOf(m),P.rank(listOf(m),P.Task.VISION,P.Mode.MANUAL,m.id,true))
        assertTrue(P.rank(listOf(m.copy(imageInput=false)),P.Task.VISION,P.Mode.MANUAL,m.id,true).isEmpty())
    }
}
