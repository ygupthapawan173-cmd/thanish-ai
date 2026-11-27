package com.example.autonomous

internal object FinalIntegrationPolicy {
    fun validRequest(text: String): Boolean = text.isNotBlank() && text.length <= 4000 && '\u0000' !in text
    fun validSha(sha: String): Boolean = sha.matches(Regex("[0-9a-f]{40}"))
    fun isolatedBranch(branch: String): Boolean = branch.startsWith("omega-auto/upgrade-") && EngineeringPolicy.validBase(branch)
    fun shouldRepair(conclusion: String): Boolean = conclusion == "failure"
    fun progress(current: Int, next: Int): Int = maxOf(current.coerceIn(0, 100), next.coerceIn(0, 100))
    fun battery(value: Int): Int = if (value in 0..100) value else -1
    fun temperature(tenths: Int?): Float = if (tenths != null && tenths in -400..1000) tenths / 10f else Float.NaN
    fun ram(total: Long, available: Long): Int = if (total <= 0 || available !in 0..total) -1 else (((total - available).toDouble() / total) * 100).toInt().coerceIn(0, 100)
    fun percentLabel(value: Int): String = if (value in 0..100) "$value%" else "Unavailable"
    fun tempLabel(value: Float): String = if (value.isFinite()) "$value°C" else "Unavailable"
}
