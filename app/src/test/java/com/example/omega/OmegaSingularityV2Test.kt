package com.example.omega
import com.example.ai.GeminiLiveDuplexEngine
import com.example.ai.SelfEvolutionCompilerLoop
import com.example.control.AutonomousReActOrchestrator
import org.junit.Assert.*
import org.junit.Test
class OmegaSingularityV2Test {
    @Test fun liveMicrophoneExclusion() {
        val gate=AssistantSessionGate()
        assertTrue(gate.acquireMic(MicOwner.LIVE))
        assertFalse(gate.acquireMic(MicOwner.WAKE))
        assertFalse(gate.acquireMic(MicOwner.MANUAL))
        assertNull(gate.beginForeground())
        gate.releaseMic(MicOwner.LIVE)
        assertTrue(gate.acquireMic(MicOwner.WAKE))
    }
    @Test fun rmsAndModelValidation() {
        assertEquals(0f,GeminiLiveDuplexEngine.pcmRms(byteArrayOf(0,0),2),0.001f)
        assertTrue(GeminiLiveDuplexEngine.pcmRms(byteArrayOf(0,-128),2)>0.99f)
        assertTrue(GeminiLiveDuplexEngine.validModel("models/gemini-live-test"))
        assertFalse(GeminiLiveDuplexEngine.validModel("x?key=secret"))
    }
    @Test fun reviewChainRejectsUnsupportedSteps() {
        assertNotNull(AutonomousReActOrchestrator.parseChain("check battery -> increase volume"))
        assertNull(AutonomousReActOrchestrator.parseChain("check battery -> arbitrary unknown command"))
        assertNull(AutonomousReActOrchestrator.parseChain("check battery ->"))
    }
    @Test fun updateAndDraftValidation() {
        assertTrue(SelfEvolutionCompilerLoop.validDigest("a".repeat(64)))
        assertFalse(SelfEvolutionCompilerLoop.validDigest("not verified"))
        assertTrue(SelfEvolutionCompilerLoop.validPath("app/src/main/java/com/example/Test.kt"))
        assertFalse(SelfEvolutionCompilerLoop.validPath("app/src/main/java/com/example/../Secret.kt"))
    }
}
