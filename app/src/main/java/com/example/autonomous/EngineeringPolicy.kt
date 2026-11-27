package com.example.autonomous

/** Explicitly selected Kotlin files only; no workflow, build script or secret modifications. */
object EngineeringPolicy {
    private val source = Regex("app/src/(main|test)/java/(?:[A-Za-z0-9_]+/)*[A-Za-z0-9_]+\\.kt")
    private val repo = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
    fun paths(raw: String): List<String> {
        val paths = raw.split(',', '\n').map(String::trim).filter(String::isNotEmpty).distinct()
        require(paths.size in 1..4) { "Select 1–4 existing Kotlin source files" }
        require(paths.all { source.matches(it) }) { "Only explicit app Kotlin source/test paths are allowed" }
        return paths
    }
    fun validRepo(raw: String): Boolean = repo.matches(raw) && raw.split('/').none { it == "." || it == ".." }
    fun validBase(raw: String): Boolean = raw.length in 1..100 && raw.split('/').all { it.isNotBlank() && it != "." && it != ".." && Regex("[A-Za-z0-9_-][A-Za-z0-9_.-]*").matches(it) && !it.endsWith(".lock") && !it.endsWith('.') && !it.contains("..") }
    fun matchingCommit(expected: String, actual: String): Boolean = expected.isNotBlank() && expected == actual
    fun completeSource(content: String): Boolean = content.isNotBlank() && content.length <= 40_000 && '\u0000' !in content
    // Preserve package identity; compiler remains the authoritative dependency check.
    fun samePackage(before: String, after: String): Boolean {
        val pattern = Regex("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*$")
        val original = pattern.find(before)?.groupValues?.get(1) ?: return false
        return pattern.find(after)?.groupValues?.get(1) == original
    }
}
