package com.example.omega

import org.junit.Assert.*
import org.junit.Test

class OmegaEliteWakeTest {
    @Test fun grammarContainsExplicitUnknownAlternative() {
        assertEquals(listOf("hey jarvis", "hey jervis", "[unk]"),
            OmegaWakeEngine.grammarPhrases(listOf("Hey JARVIS", "hey jervis", "hey jarvis")))
    }
    @Test fun strictWakeRequiresEntireFinalPhrase() {
        val phrases = listOf("hey jarvis")
        assertTrue(OmegaWakeEngine.strictGrammarMatch("Hey JARVIS", phrases))
        assertFalse(OmegaWakeEngine.strictGrammarMatch("hey", phrases))
        assertFalse(OmegaWakeEngine.strictGrammarMatch("I said hey jarvis", phrases))
        assertFalse(OmegaWakeEngine.strictGrammarMatch("hey jarvis open camera", phrases))
        assertFalse(OmegaWakeEngine.strictGrammarMatch("[unk] hey jarvis", phrases))
    }
    @Test fun explicitAlternativesOnly() {
        val phrases = OmegaWakePhrase.HEY_JARVIS.spoken
        assertTrue(OmegaWakeEngine.strictGrammarMatch("hey jervis", phrases))
        assertFalse(OmegaWakeEngine.strictGrammarMatch("hey jorvis", phrases))
        assertFalse(OmegaWakeEngine.strictGrammarMatch("jarvis", phrases))
    }
}
