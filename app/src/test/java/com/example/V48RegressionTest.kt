package com.example
import com.example.voice.VoiceRecoveryPolicy
import com.example.omega.WakeRetryPolicy
import com.example.ai.LiveRetryPolicy
import com.example.ai.GatewayRetryPolicy
import com.example.data.SemanticMemoryEngine
import com.example.data.MemoryFact
import org.junit.Test
import org.junit.Assert.*
class V48RegressionTest {
 @Test fun speechLocale() { assertEquals("te-IN",VoiceRecoveryPolicy.localeFromText("నమస్తే","en-IN")); assertEquals("en-IN",VoiceRecoveryPolicy.localeFromText("hello","te-IN")) }
 @Test fun wakeRetry() { assertEquals(1000L,WakeRetryPolicy.delayMs(1)); assertEquals(32000L,WakeRetryPolicy.delayMs(100)) }
 @Test fun liveRetry() { assertTrue(LiveRetryPolicy.mayRetry("HTTP 400",0,"a","b")); assertFalse(LiveRetryPolicy.mayRetry("HTTP 400",0,"models/b","b")); assertFalse(LiveRetryPolicy.mayRetry("HTTP 401",0,"a","b")); assertEquals(30000L,LiveRetryPolicy.delayMs("HTTP 429",2)) }
 @Test fun gatewayRetry() { assertTrue(GatewayRetryPolicy.modelSpecific400("model_not_found")); assertFalse(GatewayRetryPolicy.modelSpecific400("invalid_request_error")); assertEquals(300000L,GatewayRetryPolicy.cooldownMs(429,"99999")); assertEquals(0L,GatewayRetryPolicy.cooldownMs(500,null)) }
 @Test fun unrelatedMemoryNotDisclosed() { val facts=listOf(MemoryFact(1,"dog","Jannu"),MemoryFact(2,"city","Guntur")); assertTrue(SemanticMemoryEngine.search("astronomy",facts).isEmpty()); assertEquals("dog",SemanticMemoryEngine.search("my dog",facts).single().key); assertTrue(SemanticMemoryEngine.search("dog",facts,0).isEmpty()) }
}
