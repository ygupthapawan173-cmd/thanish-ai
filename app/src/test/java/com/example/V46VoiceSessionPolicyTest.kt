package com.example

import com.example.voice.VoiceSessionPolicy
import com.example.autonomous.SelectedDependencyAnalysis
import org.junit.Assert.*
import org.junit.Test

class V46VoiceSessionPolicyTest {
    @Test fun lockedAlwaysLocal() {
        assertTrue(VoiceSessionPolicy.localOnly(false,true))
        assertTrue(VoiceSessionPolicy.localOnly(true,false))
        assertFalse(VoiceSessionPolicy.localOnly(false,false))
    }
    @Test fun boundedFallback() {
        val attempted = setOf("model-en-us")
        assertEquals("model-te-in", VoiceSessionPolicy.nextPack("auto","en-IN",attempted,true))
        assertNull(VoiceSessionPolicy.nextPack("auto","en-IN",attempted,false))
        assertNull(VoiceSessionPolicy.nextPack("en-IN","en-IN",attempted,true))
        assertNull(VoiceSessionPolicy.nextPack("auto","en-IN",attempted+"model-te-in",true))
    }
    @Test fun watchdogBounded() {
        assertEquals(20_000L, VoiceSessionPolicy.ttsTimeoutMs(0))
        assertEquals(55_000L, VoiceSessionPolicy.ttsTimeoutMs(100))
        assertEquals(600_000L, VoiceSessionPolicy.ttsTimeoutMs(Int.MAX_VALUE))
    }
    @Test fun dependencyCycleTerminates() {
        val graph=mapOf("a" to "package a\nimport b.B\nclass A", "b" to "package b\nimport a.A\nclass B", "c" to "package c\nclass C")
        assertEquals(setOf("b"), SelectedDependencyAnalysis.reachable("a",graph))
        assertTrue(SelectedDependencyAnalysis.reachable("c",graph).isEmpty())
    }
}
