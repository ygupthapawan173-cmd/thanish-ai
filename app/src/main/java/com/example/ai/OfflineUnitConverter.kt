package com.example.ai

import java.util.Locale
import java.math.BigDecimal
import java.math.MathContext

/** Deterministic offline conversions; no network or model. */
object OfflineUnitConverter {
    private data class UnitDef(val dimension: String, val factor: String)
    private val units = mapOf(
        "mm" to UnitDef("length", "0.001"), "cm" to UnitDef("length", "0.01"), "m" to UnitDef("length", "1"), "km" to UnitDef("length", "1000"),
        "in" to UnitDef("length", "0.0254"), "ft" to UnitDef("length", "0.3048"), "mi" to UnitDef("length", "1609.344"),
        "mg" to UnitDef("mass", "0.001"), "g" to UnitDef("mass", "1"), "kg" to UnitDef("mass", "1000"), "lb" to UnitDef("mass", "453.59237"),
        "ml" to UnitDef("volume", "1"), "l" to UnitDef("volume", "1000"),
        "sec" to UnitDef("time", "1"), "min" to UnitDef("time", "60"), "hr" to UnitDef("time", "3600"))
    private val pattern = Regex("^(?:convert\\s+)?([+-]?\\d{1,15}(?:\\.\\d{1,12})?)\\s*([a-z]+)\\s+(?:to|in|లోకి)\\s+([a-z]+)\\s*\\??$")
    fun convert(input: String): String? {
        if (input.length > 120) return null
        val m = pattern.matchEntire(input.trim().lowercase(Locale.ROOT)) ?: return null
        val (amount, from, to) = m.destructured
        val source = units[from] ?: return null
        val dest = units[to] ?: return null
        if (source.dimension != dest.dimension) return null
        val result = BigDecimal(amount).multiply(BigDecimal(source.factor)).divide(BigDecimal(dest.factor), MathContext.DECIMAL64).stripTrailingZeros().toPlainString()
        return "$amount $from ≈ $result $to"
    }
}
