package com.example.autonomous

import com.example.security.DiagnosticRedaction

data class VoiceUpgradeProposal(
    val featureRequest: String,
    val suggestedTargetPath: String,
    val isCrashFix: Boolean = false
)

/**
 * V108 Voice-Based Self-Upgrade & Crash-Fix Intent Parser.
 * Recognizes voice commands such as:
 * - "Hey JARVIS, upgrade yourself"
 * - "Hey JARVIS, fix your voice problem"
 * - "Hey JARVIS, improve your performance"
 * - "Hey JARVIS, analyze your last crash"
 * - "Hey JARVIS, add a new feature"
 * - "Hey JARVIS, build the latest version"
 */
object VoiceUpgradeIntentParser {

    private val UPGRADE_PREFIX = Regex(
        "^(?:hey\\s+jarvis[,:\\s]+)?(?:self[-\\s]?upgrade|auto[-\\s]?upgrade|upgrade\\s+yourself|code\\s+upgrade|self[-\\s]?code|fix\\s+your\\s+.+|improve\\s+your\\s+.+|add\\s+a\\s+new\\s+feature.*|build\\s+the\\s+latest\\s+version.*)\\b[,:\\s]*(.*)$",
        RegexOption.IGNORE_CASE
    )
    private val CRASH_FIX_PATTERN = Regex(
        "\\b(?:fix\\s+(?:the\\s+)?(?:last\\s+)?crash|analyze\\s+(?:your\\s+)?(?:last\\s+)?crash|self[-\\s]?heal|bug\\s*fix)\\b",
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
        val trailing = match.groupValues.getOrNull(1)?.trim().orEmpty()
        val request = if (trailing.isNotBlank()) trailing else clean
        val target = when {
            "voice" in clean.lowercase() || "tts" in clean.lowercase() ->
                "app/src/main/java/com/example/voice/TeluguTtsController.kt"
            "performance" in clean.lowercase() || "latency" in clean.lowercase() ->
                "app/src/main/java/com/example/ui/PanduViewModel.kt"
            else -> defaultTargetPath
        }
        return VoiceUpgradeProposal(
            featureRequest = request,
            suggestedTargetPath = target,
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
