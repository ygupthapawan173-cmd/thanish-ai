package com.example.autonomous

import com.example.security.DiagnosticRedaction

data class AntigravityModelOption(
    val id: String,
    val label: String,
    val verifiedAvailable: Boolean
)

data class AntigravityHandoffBundle(
    val title: String,
    val targetPaths: List<String>,
    val cliCommand: String,
    val taskSpecMarkdown: String
)

/**
 * V108 Manual Antigravity CLI Task Handoff.
 * Supports Thinking Effort selection and honest model availability metadata
 * (never hardcodes fake availability for unverified models).
 */
object AntigravityTaskHandoff {

    val MODEL_CATALOG = listOf(
        AntigravityModelOption("gemini-4-hydrogen-medium", "Gemini 4 Hydrogen (Medium)", verifiedAvailable = true),
        AntigravityModelOption("gemini-4-hydrogen-high", "Gemini 4 Hydrogen (High)", verifiedAvailable = true),
        AntigravityModelOption("gemini-4-hydrogen-low", "Gemini 4 Hydrogen (Low)", verifiedAvailable = true),
        AntigravityModelOption("gemini-3.8-flash-medium", "Gemini 3.8 Flash (Medium)", verifiedAvailable = true),
        AntigravityModelOption("gemini-3.8-flash-low", "Gemini 3.8 Flash (Low)", verifiedAvailable = true),
        AntigravityModelOption("claude-sonnet-4-6", "Claude Sonnet 4.6 (Thinking)", verifiedAvailable = true),
        AntigravityModelOption("claude-opus-4-6-thinking", "Claude Opus 4.6 (Thinking)", verifiedAvailable = true),
        AntigravityModelOption("gpt-oss-120b-medium", "GPT-OSS 120B (Medium)", verifiedAvailable = true),
        AntigravityModelOption("gemini-4-carbon", "Gemini 4 Carbon (Pending Account)", verifiedAvailable = false),
        AntigravityModelOption("gemini-4-argon", "Gemini 4 Argon (Pending Account)", verifiedAvailable = false),
        AntigravityModelOption("gemini-4-neon", "Gemini 4 Neon (Pending Account)", verifiedAvailable = false)
    )

    val PERMITTED_CLI_MODELS = MODEL_CATALOG.filter { it.verifiedAvailable }.map { it.id }

    val THINKING_EFFORTS = listOf("low", "medium", "high", "xhigh", "max")

    fun buildHandoff(
        featureRequest: String,
        targetPathsRaw: String,
        preferredModel: String = "gemini-4-hydrogen-medium",
        thinkingEffort: String = "medium"
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
        val effort = thinkingEffort.lowercase().takeIf { it in THINKING_EFFORTS } ?: "medium"
        val escapedPrompt = cleanReq
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
        val cliCommand = buildString {
            append("agy --print=\"")
            append("Inspect and propose a minimal patch for ${paths.joinToString()}: $escapedPrompt. ")
            append("Preserve minSdk 24, Android 16 compatibility, and run unit tests before committing.")
            append("\" --output-format json --disable-slash-commands --mode plan --sandbox ")
            append("--effort $effort --settings '{\"useG1Credits\":false}' --model $model")
        }
        val markdown = buildString {
            append("# JARVIS V108 Manual Antigravity CLI Handoff\n")
            append("- **Compliance:** Manual user execution (`useG1Credits: false`, sandboxed, no OAuth extraction)\n")
            append("- **Model:** `$model` (Verified on account)\n")
            append("- **Thinking Effort:** `$effort`\n")
            append("- **Target Files:** ${paths.joinToString()}\n")
            append("- **Request:** $cleanReq\n\n")
            append("## Command\n```bash\n$cliCommand\n```\n")
        }
        AntigravityHandoffBundle(
            title = "Antigravity Handoff ($model • $effort): ${cleanReq.take(36)}",
            targetPaths = paths,
            cliCommand = cliCommand,
            taskSpecMarkdown = markdown
        )
    }
}
