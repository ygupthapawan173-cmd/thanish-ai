package com.example.autonomous

import com.example.ai.PanduAiRepository

data class SecurityScanResult(
    val passed: Boolean,
    val findings: List<String>
)

data class GeneratedCodePatch(
    val targetPath: String,
    val updatedContent: String,
    val commitMessage: String,
    val reviewSummary: String,
    val securityScan: SecurityScanResult
)

/**
 * Self-Coding & Security Scanning Engine (v19 Autonomous):
 * - Scans generated Kotlin/XML code for hardcoded tokens/keys, brace balance, & unsafe calls
 * - Generates minimal, surgical patches using active AI provider (Gemini -> OpenRouter fallback)
 */
class SelfCodingEngine(private val aiRepository: PanduAiRepository) {

    suspend fun generatePatch(
        featureRequest: String,
        targetPath: String,
        existingFileContent: String,
        buildErrorFeedback: String? = null,
        relatedFilePaths: List<String> = emptyList(),
        dependencySummary: String = ""
    ): Result<GeneratedCodePatch> {
        if (!FinalIntegrationPolicy.validRequest(featureRequest)) return Result.failure(IllegalArgumentException("Invalid engineering request"))
        if (runCatching { EngineeringPolicy.paths(targetPath).single() }.getOrNull() != targetPath) return Result.failure(IllegalArgumentException("Invalid Kotlin source path"))
        if (com.example.security.DiagnosticRedaction.clean(featureRequest) != featureRequest || com.example.security.DiagnosticRedaction.clean(existingFileContent) != existingFileContent) return Result.failure(SecurityException("Possible secret detected; cloud processing blocked"))
        if (!EngineeringPolicy.completeSource(existingFileContent)) return Result.failure(IllegalArgumentException("Source exceeds safe context size; select a smaller file. No truncated overwrite."))
        val existingScan = scanForSecurityAndSyntax(targetPath, existingFileContent)
        if (existingScan.findings.any { it.contains("token", true) || it.contains("API key", true) }) return Result.failure(SecurityException("Existing source may contain secrets; remove before cloud processing."))
        val prompt = buildString {
            append("You are the JARVIS INFINITY Kotlin Android Engineer.\n")
            append("Task: $featureRequest\n")
            append("Target file: $targetPath\n")
            append("Other explicitly selected files: ${relatedFilePaths.joinToString()}\n")
            append("Selected-file static dependency hints (not a compiler graph):\n${dependencySummary.take(6000)}\n")
            if (!buildErrorFeedback.isNullOrBlank()) {
                append("PREVIOUS GRADLE BUILD FAILED WITH THIS ERROR — FIX IT:\n${com.example.security.DiagnosticRedaction.clean(buildErrorFeedback).take(6000)}\n")
            }
            append("STRICT RULES:\n")
            append("1. Return ONLY the complete valid updated file content inside a single ```kotlin ... ``` or ```xml ... ``` block.\n")
            append("2. NEVER embed API keys, GitHub tokens, or secrets.\n")
            append("3. Preserve package name, existing working features, and Android 16 compatibility.\n\n")
            append("EXISTING FILE CONTENT:\n```\n")
            append(existingFileContent)
            append("\n```")
        }

        val aiRes = aiRepository.sendRawEngineeringPrompt(prompt)
        return aiRes.mapCatching { rawText ->
            val extracted = extractCodeBlock(rawText)
                ?: throw IllegalStateException("AI did not return a valid code block.")

            require(extracted.trim() != existingFileContent.trim()) { "Generated patch makes no change" }
            require(EngineeringPolicy.completeSource(extracted)) { "Generated source exceeds safe size" }
            require(EngineeringPolicy.samePackage(existingFileContent, extracted)) { "Generated patch changed package identity" }
            val scan = scanForSecurityAndSyntax(targetPath, extracted)
            if (!scan.passed) {
                throw SecurityException("Patch blocked by Security/Syntax Scanner: ${scan.findings.joinToString("; ")}")
            }

            val review = AntigravitySelfCodingAgent.reviewPatch(targetPath, existingFileContent, extracted)
            if (!review.passed) {
                throw SecurityException("Patch blocked by Antigravity Code Review: ${review.findings.joinToString("; ")}")
            }
            val regressionPlan = AntigravitySelfCodingAgent.buildRegressionPlan(targetPath)
            val fullReview = "${review.summary} | Regression: ${regressionPlan.checks.first()}"
            GeneratedCodePatch(
                targetPath = targetPath,
                updatedContent = extracted,
                commitMessage = "infinity-auto: ${featureRequest.take(56)}",
                reviewSummary = fullReview,
                securityScan = scan
            )
        }
    }

    companion object {
        fun extractCodeBlock(raw: String): String? {
            val regex = Regex("```(?:kotlin|kt|xml|gradle)?\\s*\n([\\s\\S]*?)```")
            return regex.matchEntire(raw.trim())?.groupValues?.getOrNull(1)?.trim()?.ifBlank { null }
        }

        fun scanForSecurityAndSyntax(path: String, code: String): SecurityScanResult {
            val findings = mutableListOf<String>()
            // 1. Secret / Token leak scanner
            if (Regex("ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}").containsMatchIn(code)) {
                findings.add("Blocked: Embedded GitHub token detected.")
            }
            if (Regex("""AIza[0-9A-Za-z\-_]{30,}""").containsMatchIn(code)) {
                findings.add("Blocked: Hardcoded Google API key detected.")
            }
            if (Regex("sk-(?:or-v1-|proj-)?[A-Za-z0-9_-]{20,}").containsMatchIn(code)) {
                findings.add("Blocked: Hardcoded OpenRouter API key detected.")
            }
            if (Regex("Runtime\\s*\\.\\s*getRuntime\\s*\\(\\s*\\)\\s*\\.\\s*exec\\s*\\(").containsMatchIn(code) || Regex("\\bProcessBuilder\\s*\\(").containsMatchIn(code)) {
                findings.add("Blocked: Unsafe shell exec detected.")
            }

            // 2. Kotlin structural checks
            if (path.endsWith(".kt")) {
                if (!code.trimStart().startsWith("package ")) {
                    findings.add("Syntax error: Missing package declaration.")
                }
                val openB = code.count { it == '{' }
                val closeB = code.count { it == '}' }
                if (openB != closeB) {
                    findings.add("Syntax error: Unbalanced braces ({=$openB, }=$closeB).")
                }
            }
            return SecurityScanResult(passed = findings.isEmpty(), findings = findings)
        }

        private fun buildAutoReview(oldCode: String, newCode: String, path: String): String {
            val oldLines = oldCode.lines().size
            val newLines = newCode.lines().size
            val delta = newLines - oldLines
            val sign = if (delta >= 0) "+$delta" else "$delta"
            return "Reviewed $path ($oldLines -> $newLines lines, $sign). Heuristic scan passed; compiler and human review still required."
        }
    }
}
