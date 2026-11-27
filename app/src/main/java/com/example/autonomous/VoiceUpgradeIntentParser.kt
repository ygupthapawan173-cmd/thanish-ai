package com.example.autonomous

import com.example.security.DiagnosticRedaction

data class VoiceUpgradeProposal(
    val featureRequest: String,
    val suggestedTargetPath: String,
    val isCrashFix: Boolean = false
)

/**
 * V107 Voice-Based Self-Upgrade & Crash-Fix Intent Parser.
 * Always produces a confirmation proposal — never triggers edits without user approval.
 */
object VoiceUpgradeIntentParser {

    private val UPGRADE_PREFIX = Regex(
        "^(?:hey\\s+jarvis[,:\\s]+)?(?:self[-\\s]?upgrade|auto[-\\s]?upgrade|upgrade\\s+yourself|code\\s+upgrade|self[-\\s]?code)\\b[,:\\s]*(.*)$",
        RegexOption.IGNORE_CASE
    )
    private val CRASH_FIX_PATTERN = Regex(
        "\\b(?:fix\\s+(?:the\\s+)?(?:last\\s+)?crash|analyze\\s+crash|self[-\\s]?heal|bug\\s*fix)\\b",
        RegexOption.IGNORE_CASE
    )

    fun parse(utterance: String, defaultTargetPath: String = "app/src/main/java/com/example/ui/OmegaDashboardApp.kt"): VoiceUpgradeProposal? {
        val clean = DiagnosticRedaction.clean(utterance.trim())
        if (clean.isBlank() || clean.length > 4000) return null

        if (CRASH_FIX_PATTERN.containsMatchIn(clean)) {
            return VoiceUpgradeProposal(
                featureRequest = clean,
                suggestedTargetPath = defaultTargetPath,
                isCrashFix = true
            )
        }

        val match = UPGRADE_PREFIX.matchEntire(clean) ?: return null
        val body = match.groupValues[1].trim().ifBlank { clean }
        return VoiceUpgradeProposal(
            featureRequest = body,
            suggestedTargetPath = defaultTargetPath,
            isCrashFix = false
        )
    }
}

object CrashBugFixPlanner {

    private val STACK_CLASS = Regex("""at\s+com\.example\.([A-Za-z0-9_./$]+)\.""")

    fun buildFixProposal(rawCrashLogs: String): VoiceUpgradeProposal? {
        val cleanLogs = DiagnosticRedaction.clean(rawCrashLogs.trim())
        if (cleanLogs.isBlank() || cleanLogs.contains("No crashes recorded", ignoreCase = true)) {
            return null
        }
        val latestSection = cleanLogs.substringAfterLast("=== CRASH [", cleanLogs).take(1800)
        val matchedRel = STACK_CLASS.find(latestSection)?.groupValues?.getOrNull(1)
            ?.substringBefore('$')
            ?.replace('.', '/')
        val targetPath = if (!matchedRel.isNullOrBlank()) {
            val candidate = "app/src/main/java/com/example/$matchedRel.kt"
            if (AntigravitySelfCodingAgent.isWithinRestrictedWorkspace(candidate)) candidate
            else "app/src/main/java/com/example/ui/PanduViewModel.kt"
        } else {
            "app/src/main/java/com/example/ui/PanduViewModel.kt"
        }

        val request = "Analyze and fix the latest recorded Android crash while preserving minSdk 24 and existing features:\n$latestSection"
            .take(3800)
        return VoiceUpgradeProposal(
            featureRequest = request,
            suggestedTargetPath = targetPath,
            isCrashFix = true
        )
    }
}
