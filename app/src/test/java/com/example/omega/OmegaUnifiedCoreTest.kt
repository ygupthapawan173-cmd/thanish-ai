package com.example.omega

import org.junit.Assert.*
import org.junit.Test

class OmegaUnifiedCoreTest {
    @Test fun microphoneExclusionAndRelease() {
        val gate = AssistantSessionGate()
        assertTrue(gate.acquireMic(MicOwner.WAKE))
        assertFalse(gate.acquireMic(MicOwner.MANUAL))
        gate.releaseMic(MicOwner.MANUAL)
        assertEquals(MicOwner.WAKE, gate.owner.value)
        gate.releaseMic(MicOwner.WAKE)
        assertTrue(gate.acquireMic(MicOwner.MANUAL))
        gate.releaseMic(MicOwner.MANUAL)
        assertEquals(MicOwner.NONE, gate.owner.value)
    }
    @Test fun backgroundAndForegroundTurnsAreExclusive() {
        val gate = AssistantSessionGate()
        assertFalse(gate.beginBackground())
        assertTrue(gate.acquireMic(MicOwner.WAKE))
        val first = gate.beginForeground()!!
        val next = gate.beginForeground()!!
        gate.endForeground(first)
        assertFalse(gate.beginBackground())
        gate.endForeground(next)
        assertTrue(gate.beginBackground())
        assertNull(gate.beginForeground())
        assertFalse(gate.beginBackground())
        gate.endBackground()
        assertNotNull(gate.beginForeground())
    }
    @Test fun wakeAiRequiresConsentAndExcludesPhoneCommands() {
        assertEquals(WakeRoute.REVIEW, OmegaAssistantCore.routeWake("what is a star", false, OmegaVoiceMode.HYBRID))
        assertEquals(WakeRoute.REVIEW, OmegaAssistantCore.routeWake("what is a star", true, OmegaVoiceMode.LOCAL))
        assertEquals(WakeRoute.REVIEW, OmegaAssistantCore.routeWake("open youtube", true, OmegaVoiceMode.HYBRID))
        assertEquals(WakeRoute.AI, OmegaAssistantCore.routeWake("what is a star", true, OmegaVoiceMode.HYBRID))
    }
    @Test fun memoryRetrievesMatchingNotesOnlyAndExcludesTasks() {
        val notes = listOf(OmegaEntry("1","project star telescope",false,false), OmegaEntry("2","star task",true,false),
            OmegaEntry("3","unrelated bicycle",false,false))
        assertEquals(listOf("project star telescope"), OmegaAssistantCore.relevantNotes("star telescope", notes))
        assertTrue(OmegaAssistantCore.relevantNotes("nothing matching", notes).isEmpty())
    }
    @Test fun memoryContextIsBounded() {
        val notes = (1..8).map { OmegaEntry(it.toString(), "star " + "a".repeat(500), false,false) }
        val selected = OmegaAssistantCore.relevantNotes("star", notes)
        assertEquals(3, selected.size)
        assertTrue(selected.all { it.length <= 350 })
    }
}
