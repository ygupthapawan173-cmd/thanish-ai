package com.example

import com.example.ai.ProviderBudgetPolicy
import com.example.ai.OfflineUnitConverter
import com.example.autonomous.SelectedDependencyAnalysis
import org.junit.Assert.*
import org.junit.Test

class BudgetDependencyTest {
    @Test fun rejectsUnknownOrOversizedProviderLimit() {
        assertNotNull(ProviderBudgetPolicy.rejection(5.0, null, 2.0, true, true, true))
        assertNotNull(ProviderBudgetPolicy.rejection(5.0, 6.0, 2.0, true, true, true))
        assertNotNull(ProviderBudgetPolicy.rejection(5.0, 5.0, 0.0, true, true, true))
        assertNotNull(ProviderBudgetPolicy.rejection(5.0, 5.0, 2.0, false, true, true))
        assertNull(ProviderBudgetPolicy.rejection(5.0, 5.0, 2.0, true, true, true))
    }
    @Test fun discoversSelectedImports() {
        val a = "package a\nimport b.B\nclass A"
        val b = "package b\nclass B"
        assertTrue(SelectedDependencyAnalysis.describe("a", mapOf("a" to a, "b" to b)).contains("possible dependency=true"))
    }
    @Test fun convertsWithoutNetwork() {
        assertEquals("1 kg ≈ 1000 g", OfflineUnitConverter.convert("1 kg లోకి g"))
        assertNull(OfflineUnitConverter.convert("1 kg to km"))
    }
}
