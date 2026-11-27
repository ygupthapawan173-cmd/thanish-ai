package com.example

import com.example.autonomous.AntigravityTaskHandoff
import com.example.autonomous.RollbackPreparationManager
import com.example.autonomous.TermuxControlHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V1072ControlCenterTest {

    @Test
    fun rollbackSha256IsDeterministic() {
        val a = RollbackPreparationManager.sha256Bytes("jarvis-v107.2".toByteArray())
        val b = RollbackPreparationManager.sha256Bytes("jarvis-v107.2".toByteArray())
        assertEquals(64, a.length)
        assertEquals(a, b)
    }

    @Test
    fun termuxConstantsAndHandoffModelsArePermitted() {
        assertEquals("com.termux", TermuxControlHelper.TERMUX_PACKAGE)
        assertEquals("com.termux.api", TermuxControlHelper.TERMUX_API_PACKAGE)
        assertTrue(AntigravityTaskHandoff.PERMITTED_CLI_MODELS.contains("gemini-4-hydrogen-medium"))
        assertTrue(AntigravityTaskHandoff.PERMITTED_CLI_MODELS.contains("claude-sonnet-4-6"))
    }
}
