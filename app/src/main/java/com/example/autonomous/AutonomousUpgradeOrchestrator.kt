package com.example.autonomous

import com.example.data.AppDatabase
import com.example.data.OmegaPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import java.util.UUID

data class AutonomousUpgradeState(
    val isRunning: Boolean = false,
    val currentStage: String = "Idle",
    val progressPercent: Int = 0,
    val activeBranch: String? = null,
    val pullRequestUrl: String? = null,
    val repairAttempt: Int = 0,
    val maxRepairAttempts: Int = 3,
    val stepLogs: List<String> = emptyList(),
    val error: String? = null
)

class AutonomousUpgradeOrchestrator(
    private val preferences: OmegaPreferences,
    private val database: AppDatabase,
    private val selfCoding: SelfCodingEngine,
    private val github: GitHubEngineeringClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    private val _state = MutableStateFlow(AutonomousUpgradeState())
    val state: StateFlow<AutonomousUpgradeState> = _state.asStateFlow()

    @Synchronized
    fun startSelfUpgrade(featureRequest: String, targetFilePath: String) {
        if (activeJob?.isCompleted == false) return
        if (!FinalIntegrationPolicy.validRequest(featureRequest) || com.example.security.DiagnosticRedaction.clean(featureRequest) != featureRequest) {
            _state.value = AutonomousUpgradeState(error = "Enter a non-empty upgrade request of at most 4000 characters without credentials.")
            return
        }
        val s = preferences.settings.value
        val repo = s.githubRepoSlug.trim()
        val token = s.githubToken.trim()
        val baseBranch = s.githubBaseBranch.trim().ifEmpty { "main" }

        if (!EngineeringPolicy.validRepo(repo) || !EngineeringPolicy.validBase(baseBranch) || token.isBlank()) {
            _state.value = AutonomousUpgradeState(
                error = "Configure GitHub Repo (owner/repo) and Encrypted GitHub Token in Settings > System or Autonomous Lab first."
            )
            return
        }

        val paths = runCatching { EngineeringPolicy.paths(targetFilePath) }.getOrElse {
            _state.value = AutonomousUpgradeState(error = it.message); return
        }
        if (activeJob?.isCompleted == false) return
        activeJob = scope.launch {
            val branchName = "omega-auto/upgrade-${UUID.randomUUID()}"
            _state.value = AutonomousUpgradeState(
                isRunning = true,
                currentStage = "Planning Upgrade...",
                progressPercent = 10,
                activeBranch = branchName,
                stepLogs = listOf("1. Planned self-upgrade for ${paths.size} explicit file(s)")
            )
            database.logAudit("SELF_UPGRADE", "Started: ${featureRequest.take(80)}", "RUNNING")

            try {
                // Step 1: Resolve base branch HEAD SHA
                logStep("2. Fetching base branch '$baseBranch' HEAD...", 20)
                val baseSha = github.getBranchHeadSha(repo, baseBranch, token).getOrThrow()

                // Step 2: Create isolated development branch
                logStep("3. Creating isolated branch '$branchName'...", 32)
                github.createIsolatedBranch(repo, branchName, baseSha, token).getOrThrow()

                var lastBuildError: String? = null
                var ciStatus = "Not verified"
                var lastReview = ""
                for (attempt in 0..MAX_REPAIR_ATTEMPTS) {
                    ensureActive()
                    _state.update { it.copy(repairAttempt = attempt) }
                    val attemptHead = github.getBranchHeadSha(repo, branchName, token).getOrThrow()
                    val snapshots = paths.map { path ->
                        ensureActive()
                        github.fetchFileContent(repo, path, attemptHead, token).getOrThrow()
                    }
                    // Generate every patch before committing any; no overwrite from truncated source.
                    val selectedSources = snapshots.associate { it.path to it.content }
                    val patches = snapshots.map { snapshot ->
                        ensureActive()
                        selfCoding.generatePatch(featureRequest, snapshot.path, snapshot.content, lastBuildError,
                            paths.filter { it != snapshot.path }, SelectedDependencyAnalysis.describe(snapshot.path, selectedSources)).getOrThrow()
                    }
                    lastReview = patches.joinToString("\n") { it.reviewSummary }
                    ensureActive()
                    logStep("Publishing ${patches.size} validated file(s) in one commit", 65)
                    val headSha = github.commitAtomicPatch(repo, branchName, attemptHead, snapshots, patches, token).getOrThrow()
                    logStep("Atomic commit published; checking CI for exact commit", 75)
                    val ciResult = pollCiBuild(repo, branchName, token, headSha)
                    if (ciResult == null) {
                        ciStatus = "Pending or unavailable; NOT verified"
                        logStep("CI not verified. Draft PR only.", 88)
                        break
                    } else if (ciResult.conclusion == "success") {
                        ciStatus = "A workflow succeeded for exact commit $headSha; inspect required checks"
                        logStep("Workflow succeeded for exact commit; review required", 90)
                        break
                    } else {
                        ciStatus = "Workflow ${ciResult.conclusion}; not verified"
                        if (!FinalIntegrationPolicy.shouldRepair(ciResult.conclusion)) {
                            logStep("Workflow ended without success; review required, no automatic repair.", 88)
                            break
                        }
                        lastBuildError = github.fetchFailedJobSummary(repo, ciResult.runId, token)
                        logStep("CI failed: $lastBuildError", 78)
                    }
                }
                ensureActive()

                // Step 5: Open Pull Request for Human Approval (NEVER auto-merges!)
                logStep("Opening Pull Request for Human Approval...", 95)
                val prBody = buildString {
                    append("### JARVIS INFINITY Self-Upgrade Proposal\n")
                    append("- **Request:** $featureRequest\n")
                    append("- **Target Files:** ${paths.joinToString()}\n")
                    append("- **Auto Review:** $lastReview\n")
                    append("- **CI Status:** $ciStatus\n")
                    append("- **Safety:** Isolated branch `$branchName`. Human approval required before merge.")
                }
                val prUrl = github.createPullRequest(
                    repoSlug = repo,
                    headBranch = branchName,
                    baseBranch = baseBranch,
                    title = "[INFINITY Auto] ${featureRequest.take(60)}",
                    body = prBody,
                    token = token
                ).getOrThrow()

                database.logAudit("SELF_UPGRADE", "PR opened: $prUrl", "AWAITING_APPROVAL")
                _state.update {
                    it.copy(
                        isRunning = false,
                        currentStage = "PR Ready for Human Approval",
                        progressPercent = 100,
                        pullRequestUrl = prUrl,
                        stepLogs = it.stepLogs + "PR Created: $prUrl (Review & Merge on GitHub)"
                    )
                }
            } catch (ce: CancellationException) {
                database.logAudit("SELF_UPGRADE", "Emergency STOP triggered by user", "ABORTED")
                _state.update {
                    it.copy(
                        isRunning = false,
                        currentStage = "Emergency Stopped",
                        error = "Aborted by user."
                    )
                }
            } catch (e: Exception) {
                database.logAudit("SELF_UPGRADE", "Failed: ${e.message}", "FAILED")
                _state.update {
                    it.copy(
                        isRunning = false,
                        currentStage = "Upgrade Halted",
                        error = e.message ?: "Autonomous upgrade failed."
                    )
                }
            }
        }
    }

    private suspend fun pollCiBuild(repo: String, branch: String, token: String, headSha: String): WorkflowRunStatus? {
        delay(6000L)
        repeat(30) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val run = github.getLatestWorkflowRunForBranch(repo, branch, token, headSha).getOrNull()
            if (run != null && run.status == "completed") return run
            delay(10000L)
        }
        return null
    }

    private fun logStep(msg: String, pct: Int) {
        _state.update { it.copy(currentStage = msg, progressPercent = FinalIntegrationPolicy.progress(it.progressPercent, pct), stepLogs = it.stepLogs + msg) }
    }

    @Synchronized
    fun emergencyStop() {
        // Keep the job reference until cancellation completes; a new run must not overlap.
        activeJob?.cancel()
    }

    companion object {
        const val MAX_REPAIR_ATTEMPTS = 3
    }
}
