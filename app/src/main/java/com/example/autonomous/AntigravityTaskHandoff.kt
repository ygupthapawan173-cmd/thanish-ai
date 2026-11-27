package com.example.autonomous

import com.example.security.DiagnosticRedaction

data class AntigravityHandoffBundle(
    val title: String,
    val targetPaths: List<String>,
    val cliCommand: String,
    val taskSpecMarkdown: String
)

/**
 * V107 Manual Antigravity CLI Task Handoff.
 * Complies with Google's consumer subscription terms by generating a user-executed
 * CLI command/task specification instead of running an automated APK-to-CLI proxy.
 */
object AntigravityTaskHandoff {

    val PERMITTED_CLI_MODELS = listOf(
        "gemini-4-hydrogen-medium",
        "gemini-4-hydrogen-low",
        "gemini-3.8-flash-low",
        "claude-sonnet-4-6"
    )

    fun buildHandoff(
        featureRequest: String,
        targetPathsRaw: String,
        preferredModel: String = "gemini-4-hydrogen-medium"
    ): Result<AntigravityHandoffBundle> = runCatching {
        val cleanReq = DiagnosticRedaction.clean(featureRequest.trim())
        require(FinalIntegrationPolicy.validRequest(cleanReq)) {
            "Enter a valid upgrade request (1..4000 chars, no secrets)."
        }
        val paths = EngineeringPolicy.paths(targetPathsRaw)
        require(paths.all { AntigravitySelfCodingAgent.isWithinRestrictedWorkspace(it) }) {
            "All target paths must be inside app/src/*.kt"
        }
        val model = preferredModel.takeIf { it in PERMITTED_CLI_MODELS } ?: "gemini-4-hydrogen-medium"
        val escapedPrompt = cleanReq
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
        val cliCommand = buildString {
            append("agy --print=\"")
            append("Inspect and propose a minimal patch for ${paths.joinToString()}: $escapedPrompt. ")
            append("Preserve minSdk 24, Android 16 compatibility, and run unit tests before committing.")
            append("\" --output-format json --disable-slash-commands --mode plan --sandbox ")
            append("--settings '{\"useG1Credits\":false}' --model $model")
        }
        val markdown = buildString {
            append("# JARVIS V107 Manual Antigravity CLI Handoff\n")
            append("- **Compliance:** Manual user execution (`useG1Credits: false`, sandboxed, no OAuth extraction)\n")
            append("- **Recommended Model:** `$model`\n")
            append("- **Target Files:** ${paths.joinToString()}\n")
            append("- **Request:** $cleanReq\n\n")
            append("## Command\n```bash\n$cliCommand\n```\n")
        }
        AntigravityHandoffBundle(
            title = "Antigravity Handoff: ${cleanReq.take(48)}",
            targetPaths = paths,
            cliCommand = cliCommand,
            taskSpecMarkdown = markdown
        )
    }
}
