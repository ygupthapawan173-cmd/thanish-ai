package com.example.ai

import com.example.autonomous.VoiceUpgradeIntentParser
import java.util.Locale

enum class BrainIntentCategory {
    DIRECT_MOBILE_COMMAND,
    DAILY_QUESTION,
    FAST_CODING,
    ADVANCED_CODING,
    MAJOR_DEVELOPMENT,
    SELF_UPGRADE,
    CRASH_FIX,
    MEMORY_QUERY
}

enum class ReasoningEffort { LOW, MEDIUM, HIGH }

data class BrainTaskPlan(
    val category: BrainIntentCategory,
    val effort: ReasoningEffort,
    val responseLanguage: String,
    val steps: List<String>,
    val verificationCheck: String
)

/**
 * V108 Master AI Brain Orchestrator.
 * Unifies bilingual (Telugu/English) intent classification, task complexity detection,
 * multi-step planning, and privacy-safe preference routing without duplicating existing engines.
 */
object MasterAiBrain {

    private val MOBILE_CMD = Regex(
        "\\b(?:open|launch|turn\\s+(?:on|off)|toggle|flashlight|torch|wifi|bluetooth|volume|brightness|alarm|call|dial|battery)\\b",
        RegexOption.IGNORE_CASE
    )
    private val TELUGU_SCRIPT = Regex("[\\u0C00-\\u0C7F]")
    private val MEMORY_WORDS = Regex("\\b(?:remember|memory|what did i|saved fact|my preference|గుర్తు)\\b", RegexOption.IGNORE_CASE)
    private val MAJOR_DEV = Regex("\\b(?:architect|architecture|refactor|multi[-\\s]?file|master\\s+upgrade|redesign|module)\\b", RegexOption.IGNORE_CASE)
    private val ADV_CODING = Regex("\\b(?:kotlin|compose|coroutine|security|encrypt|gradle|proguard|crash|stacktrace|debug)\\b", RegexOption.IGNORE_CASE)

    fun detectResponseLanguage(text: String, configuredLanguage: String): String {
        if (configuredLanguage != "auto" && configuredLanguage.isNotBlank()) return configuredLanguage
        return if (TELUGU_SCRIPT.containsMatchIn(text)) "te-IN" else "en-IN"
    }

    fun classifyAndPlan(
        userMessage: String,
        hasImage: Boolean = false,
        configuredLanguage: String = "auto"
    ): BrainTaskPlan {
        val q = userMessage.trim()
        val lang = detectResponseLanguage(q, configuredLanguage)

        VoiceUpgradeIntentParser.parse(q)?.let { upgrade ->
            val cat = if (upgrade.isCrashFix) BrainIntentCategory.CRASH_FIX else BrainIntentCategory.SELF_UPGRADE
            return BrainTaskPlan(
                category = cat,
                effort = ReasoningEffort.HIGH,
                responseLanguage = lang,
                steps = listOf(
                    "Analyze target source & sanitized diagnostics",
                    "Generate minimal patch with model auto-switching",
                    "Run Antigravity code review & regression checks",
                    "Commit to isolated branch & monitor CI APK build"
                ),
                verificationCheck = "Security scan + unit tests + PR gate"
            )
        }

        if (!hasImage && MOBILE_CMD.containsMatchIn(q) && q.length <= 120) {
            return BrainTaskPlan(
                category = BrainIntentCategory.DIRECT_MOBILE_COMMAND,
                effort = ReasoningEffort.LOW,
                responseLanguage = lang,
                steps = listOf("Execute Android direct command", "Verify device state result"),
                verificationCheck = "Action executor result"
            )
        }

        if (MEMORY_WORDS.containsMatchIn(q)) {
            return BrainTaskPlan(
                category = BrainIntentCategory.MEMORY_QUERY,
                effort = ReasoningEffort.LOW,
                responseLanguage = lang,
                steps = listOf("Query SemanticMemoryEngine", "Synthesize grounded personal reply"),
                verificationCheck = "Untrusted memory boundary preserved"
            )
        }

        val task = SmartModelPolicy.task(q, hasImage)
        if (MAJOR_DEV.containsMatchIn(q)) {
            return BrainTaskPlan(
                category = BrainIntentCategory.MAJOR_DEVELOPMENT,
                effort = ReasoningEffort.HIGH,
                responseLanguage = lang,
                steps = listOf("Decompose architecture", "Propose scoped Kotlin changes", "Recommend Antigravity CLI handoff if needed"),
                verificationCheck = "Regression plan + minSdk 24 check"
            )
        }

        if (task == SmartModelPolicy.Task.CODE || ADV_CODING.containsMatchIn(q)) {
            val isAdvanced = ADV_CODING.containsMatchIn(q) || q.length > 260
            return BrainTaskPlan(
                category = if (isAdvanced) BrainIntentCategory.ADVANCED_CODING else BrainIntentCategory.FAST_CODING,
                effort = if (isAdvanced) ReasoningEffort.HIGH else ReasoningEffort.MEDIUM,
                responseLanguage = lang,
                steps = listOf("Select optimal coding model", "Generate verified Kotlin/explanation"),
                verificationCheck = "No embedded secrets or unsafe APIs"
            )
        }

        val effort = if (task == SmartModelPolicy.Task.REASONING || task == SmartModelPolicy.Task.VISION) {
            ReasoningEffort.MEDIUM
        } else {
            ReasoningEffort.LOW
        }
        return BrainTaskPlan(
            category = BrainIntentCategory.DAILY_QUESTION,
            effort = effort,
            responseLanguage = lang,
            steps = listOf("Inject live device context", "Route via free-quota-first provider"),
            verificationCheck = "Cost protection + bounded response"
        )
    }
}
