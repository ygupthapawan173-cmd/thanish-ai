package com.example.autonomous

import com.example.security.DiagnosticRedaction

data class AutoCodeReviewResult(
    val passed: Boolean,
    val score: Int,
    val findings: List<String>,
    val summary: String
)

data class RegressionTestPlan(
    val targetPath: String,
    val suggestedTestFile: String,
    val checks: List<String>
)

/**
 * Antigravity Self Coding Agent (V106):
 * - Enforces restricted local development workspace boundaries (app/src/main|test/java/com/example/...)
 * - Performs automatic code review (security, minSdk 24, Android 16, no silent install/shell exec)
 * - Generates regression test plans for self-coded patches before PR creation
 */
object AntigravitySelfCodingAgent {

    private const val WORKSPACE_PREFIX = "app/src/"
    private val UNGUARDED_JAVA_TIME = Regex("""\bjava\.time\.(LocalDate|LocalDateTime|ZonedDateTime|Instant|DateTimeFormatter)\b""")
    private val SILENT_INSTALL = Regex("""\b(pm\s+install|PackageInstaller|INSTALL_SILENT)\b""")
    private val SHELL_EXEC = Regex("""Runtime\s*\.\s*getRuntime\s*\(\s*\)\s*\.\s*exec\s*\(|\bProcessBuilder\s*\(""")

    fun isWithinRestrictedWorkspace(path: String): Boolean {
        val clean = path.trim()
        if (clean.isEmpty() || clean.startsWith("/") || ".." in clean || "\\" in clean) return false
        if (!clean.startsWith(WORKSPACE_PREFIX)) return false
        return runCatching { EngineeringPolicy.paths(clean).single() == clean }.getOrDefault(false)
    }

    fun reviewPatch(path: String, oldCode: String, newCode: String): AutoCodeReviewResult {
        val findings = mutableListOf<String>()
        var score = 100

        if (!isWithinRestrictedWorkspace(path)) {
            findings.add("CRITICAL: Path '$path' is outside restricted workspace ($WORKSPACE_PREFIX).")
            score -= 50
        }

        val baseScan = SelfCodingEngine.scanForSecurityAndSyntax(path, newCode)
        if (!baseScan.passed) {
            findings.addAll(baseScan.findings.map { "CRITICAL: $it" })
            score -= 40
        }

        if (DiagnosticRedaction.clean(newCode) != newCode) {
            findings.add("CRITICAL: Potential credential/secret detected in patch.")
            score -= 50
        }

        if (SHELL_EXEC.containsMatchIn(newCode)) {
            findings.add("CRITICAL: Unrestricted shell execution is forbidden.")
            score -= 40
        }

        if (SILENT_INSTALL.containsMatchIn(newCode)) {
            findings.add("CRITICAL: Silent APK installation API is forbidden; use user-approved FileProvider intent.")
            score -= 40
        }

        if (UNGUARDED_JAVA_TIME.containsMatchIn(newCode) && !newCode.contains("Build.VERSION.SDK_INT")) {
            findings.add("WARN: java.time.* used without API 26+ SDK_INT guard (minSdk is 24).")
            score -= 15
        }

        val openP = newCode.count { it == '(' }
        val closeP = newCode.count { it == ')' }
        if (openP != closeP) {
            findings.add("CRITICAL: Unbalanced parentheses ((=$openP, )=$closeP).")
            score -= 30
        }

        if (!EngineeringPolicy.samePackage(oldCode, newCode)) {
            findings.add("CRITICAL: Package declaration changed.")
            score -= 40
        }

        val clampedScore = score.coerceIn(0, 100)
        val passed = findings.none { it.startsWith("CRITICAL:") }
        val summary = "Antigravity Review [$clampedScore/100]: ${if (passed) "PASSED" else "BLOCKED"} (${findings.size} finding(s))"
        return AutoCodeReviewResult(
            passed = passed,
            score = clampedScore,
            findings = findings,
            summary = summary
        )
    }

    fun buildRegressionPlan(targetPath: String): RegressionTestPlan {
        val fileName = targetPath.substringAfterLast('/').removeSuffix(".kt")
        val suggestedTest = "app/src/test/java/com/example/${fileName}RegressionTest.kt"
        val checks = listOf(
            "Verify existing unit tests pass without regression",
            "Verify minSdk 24 & Android 16 compatibility",
            "Verify no embedded credentials or silent install paths",
            "Verify deterministic behavior for $fileName"
        )
        return RegressionTestPlan(targetPath, suggestedTest, checks)
    }
}
