package com.example.ai

import com.example.autonomous.VoiceUpgradeIntentParser

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

enum class BrainPipelineStage(val label: String) {
    UNDERSTAND("Understand"),
    REASON("Reason"),
    PLAN("Plan"),
    SELECT_MODEL("Select Model"),
    EXECUTE("Execute"),
    VERIFY("Verify"),
    RESPOND("Respond")
}

data class BrainTaskPlan(
    val category: BrainIntentCategory,
    val effort: ReasoningEffort,
    val responseLanguage: String,
    val steps: List<String>,
    val verificationCheck: String,
    val isTranslationRequest: Boolean = false,
    val isPlanningRequest: Boolean = false,
    val pipelineStages: List<BrainPipelineStage> = BrainPipelineStage.entries
)

/**
 * V110 Master AI Brain Orchestrator.
 * Unifies bilingual (Telugu + Tenglish + English) intent classification, reasoning depth,
 * multi-step planning, and privacy-safe routing through:
 * Understand -> Reason -> Plan -> Select Model -> Execute -> Verify -> Respond.
 */
object MasterAiBrain {

    private val MOBILE_CMD = Regex(
        "\\b(?:open|launch|turn\\s+(?:on|off)|toggle|flashlight|torch|wifi|bluetooth|volume|brightness|alarm|call|dial|battery|teruvu|pettu|teeyi)\\b",
        RegexOption.IGNORE_CASE
    )
    private val TELUGU_SCRIPT = Regex("[\\u0C00-\\u0C7F]")
    private val TENGLISH_HINTS = Regex(
        "\\b(?:cheppu|cheppandi|ela\\s+unnav|emiti|enti|samayam|vaatavaranam|anuvadam|gurthu|naaku|nenu|telugu\\s+lo)\\b",
        RegexOption.IGNORE_CASE
    )
    private val MEMORY_WORDS = Regex("\\b(?:remember|memory|what did i|saved fact|my preference|gurthu|గుర్తు|గుర్తుంచుకో)\\b", RegexOption.IGNORE_CASE)
    private val MAJOR_DEV = Regex("\\b(?:architect|architecture|refactor|multi[-\\s]?file|master\\s+upgrade|redesign|module)\\b", RegexOption.IGNORE_CASE)
    private val ADV_CODING = Regex("\\b(?:kotlin|compose|coroutine|security|encrypt|gradle|proguard|crash|stacktrace|debug)\\b", RegexOption.IGNORE_CASE)
    private val TRANSLATE_WORDS = Regex("\\b(?:translate|translation|anuvadam|అనువాదం|అనువదించు|in\\s+telugu|in\\s+english|in\\s+hindi)\\b", RegexOption.IGNORE_CASE)
    private val PLAN_WORDS = Regex("\\b(?:daily\\s+briefing|daily\\s+plan|schedule\\s+my\\s+day|agenda|plan\\s+my\\s+day|ఈరోజు\\s+ప్లాన్)\\b", RegexOption.IGNORE_CASE)

    fun detectResponseLanguage(text: String, configuredLanguage: String): String {
        if (configuredLanguage != "auto" && configuredLanguage.isNotBlank()) return configuredLanguage
        return if (TELUGU_SCRIPT.containsMatchIn(text) || TENGLISH_HINTS.containsMatchIn(text)) "te-IN" else "en-IN"
    }

    fun classifyAndPlan(
        userMessage: String,
        hasImage: Boolean = false,
        configuredLanguage: String = "auto",
        thinkingMode: BrainThinkingMode = BrainThinkingMode.AUTO
    ): BrainTaskPlan {
        val q = userMessage.trim()
        val lang = detectResponseLanguage(q, configuredLanguage)
        val isTranslate = TRANSLATE_WORDS.containsMatchIn(q)
        val isPlanning = PLAN_WORDS.containsMatchIn(q)

        fun applyEffortOverride(defaultEffort: ReasoningEffort): ReasoningEffort = when (thinkingMode) {
            BrainThinkingMode.FAST -> ReasoningEffort.LOW
            BrainThinkingMode.REASONING, BrainThinkingMode.DEEP_THINKING -> ReasoningEffort.HIGH
            BrainThinkingMode.AUTO -> defaultEffort
        }

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
                effort = applyEffortOverride(ReasoningEffort.LOW),
                responseLanguage = lang,
                steps = listOf("Query SemanticMemoryEngine", "Synthesize grounded personal reply"),
                verificationCheck = "Untrusted memory boundary preserved"
            )
        }

        val task = SmartModelPolicy.task(q, hasImage)
        if (MAJOR_DEV.containsMatchIn(q)) {
            return BrainTaskPlan(
                category = BrainIntentCategory.MAJOR_DEVELOPMENT,
                effort = applyEffortOverride(ReasoningEffort.HIGH),
                responseLanguage = lang,
                steps = listOf("Decompose architecture", "Propose scoped Kotlin changes", "Recommend Antigravity CLI handoff if needed"),
                verificationCheck = "Regression plan + minSdk 24 check"
            )
        }

        if (task == SmartModelPolicy.Task.CODE || ADV_CODING.containsMatchIn(q)) {
            val isAdvanced = ADV_CODING.containsMatchIn(q) || q.length > 260
            return BrainTaskPlan(
                category = if (isAdvanced) BrainIntentCategory.ADVANCED_CODING else BrainIntentCategory.FAST_CODING,
                effort = applyEffortOverride(if (isAdvanced) ReasoningEffort.HIGH else ReasoningEffort.MEDIUM),
                responseLanguage = lang,
                steps = listOf("Select optimal coding brain & model", "Generate verified Kotlin/explanation"),
                verificationCheck = "No embedded secrets or unsafe APIs"
            )
        }

        val defaultEffort = if (task == SmartModelPolicy.Task.REASONING || task == SmartModelPolicy.Task.VISION || isPlanning) {
            ReasoningEffort.MEDIUM
        } else {
            ReasoningEffort.LOW
        }
        return BrainTaskPlan(
            category = BrainIntentCategory.DAILY_QUESTION,
            effort = applyEffortOverride(defaultEffort),
            responseLanguage = lang,
            steps = buildList {
                add("Inject live device context")
                if (isPlanning) add("Aggregate calendar, reminders & routines")
                if (isTranslate) add("Preserve faithful multilingual meaning")
                add("Route via free-quota-first multi-brain engine")
                add("Verify response quality & consistency")
            },
            verificationCheck = "Cost protection + quality verification",
            isTranslationRequest = isTranslate,
            isPlanningRequest = isPlanning
        )
    }
}
