package com.example.autonomous

import android.util.Base64
import com.example.ai.boundedJsonText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.io.IOException
import java.util.concurrent.TimeUnit

data class RepoFileSnapshot(
    val path: String,
    val sha: String,
    val content: String
)

data class WorkflowRunStatus(
    val runId: Long,
    val status: String,     // queued | in_progress | completed
    val conclusion: String, // success | failure | cancelled | ""
    val htmlUrl: String,
    val headSha: String
)

/**
 * Real GitHub REST API v3 Engineering Client:
 * - Zero embedded credentials; requires user-provided encrypted least-privilege token
 * - Strictly creates isolated `omega-auto/` branches and Pull Requests (NEVER force-pushes main)
 */
class GitHubEngineeringClient {

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun baseReq(url: String, token: String): Request.Builder = Request.Builder()
        .url(url)
        .addHeader("Accept", "application/vnd.github+json")
        .addHeader("X-GitHub-Api-Version", "2022-11-28")
        .apply {
            if (token.isNotBlank()) addHeader("Authorization", "Bearer ${token.trim()}")
        }

    suspend fun getBranchHeadSha(repoSlug: String, branch: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = baseReq("https://api.github.com/repos/$repoSlug/git/ref/heads/$branch", token).get().build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.boundedJsonText(2L * 1024 * 1024)
                if (!resp.isSuccessful) throw IOException("GitHub ref HTTP ${resp.code}: ${extractErr(raw)}")
                JSONObject(raw).getJSONObject("object").getString("sha")
            }
        }.onFailure { if (it is CancellationException) throw it }
    }

    suspend fun createIsolatedBranch(repoSlug: String, newBranch: String, baseSha: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(EngineeringPolicy.validRepo(repoSlug) && FinalIntegrationPolicy.isolatedBranch(newBranch) && FinalIntegrationPolicy.validSha(baseSha))
            coroutineContext.ensureActive()
            val payload = JSONObject().put("ref", "refs/heads/$newBranch").put("sha", baseSha)
            val req = baseReq("https://api.github.com/repos/$repoSlug/git/refs", token)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.boundedJsonText(2L * 1024 * 1024)
                if (!resp.isSuccessful) throw IOException("Branch create HTTP ${resp.code}: ${extractErr(raw)}")
                newBranch
            }
        }.onFailure { if (it is CancellationException) throw it }
    }

    suspend fun fetchFileContent(repoSlug: String, path: String, branch: String, token: String): Result<RepoFileSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val req = baseReq("https://api.github.com/repos/$repoSlug/contents/$path?ref=$branch", token).get().build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.boundedJsonText(2L * 1024 * 1024)
                if (!resp.isSuccessful) throw IOException("Fetch $path HTTP ${resp.code}: ${extractErr(raw)}")
                val obj = JSONObject(raw)
                require(obj.optString("type") == "file" && obj.optString("encoding") == "base64") { "Expected regular source file" }
                val sha = obj.getString("sha")
                require(FinalIntegrationPolicy.validSha(sha)) { "Invalid source SHA" }
                val b64 = obj.getString("content").replace("\n", "")
                require(b64.length <= 220_000) { "Source too large" }
                val decoded = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(Base64.decode(b64, Base64.DEFAULT))).toString()
                require(EngineeringPolicy.completeSource(decoded)) { "Source exceeds safe context size" }
                RepoFileSnapshot(path, sha, decoded)
            }
        }.onFailure { if (it is CancellationException) throw it }
    }

    /** One Git tree + commit + non-force ref update: all selected file edits become visible together. */
    suspend fun commitAtomicPatch(repoSlug: String, branch: String, expectedHead: String,
        snapshots: List<RepoFileSnapshot>, patches: List<GeneratedCodePatch>, token: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(EngineeringPolicy.validRepo(repoSlug) && branch.startsWith("omega-auto/upgrade-") && EngineeringPolicy.validBase(branch))
            require(snapshots.size == patches.size && EngineeringPolicy.paths(snapshots.joinToString(",") { it.path }).size == snapshots.size)
            require(expectedHead.matches(Regex("[0-9a-f]{40}")))
            val root="https://api.github.com/repos/$repoSlug"
            fun request(path: String, method: String="GET", payload: JSONObject?=null): JSONObject {
                val builder=baseReq(root+path,token)
                if(payload!=null) builder.method(method,payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())) else builder.get()
                return client.newCall(builder.build()).execute().use { resp ->
                    val raw=resp.boundedJsonText(2L * 1024 * 1024)
                    if(!resp.isSuccessful) throw IOException("Git data $method HTTP ${resp.code}: ${extractErr(raw)}")
                    JSONObject(raw)
                }
            }
            check(getBranchHeadSha(repoSlug,branch,token).getOrThrow()==expectedHead) { "Branch advanced before patch; retry from current source" }
            val baseTree=request("/git/commits/$expectedHead").getJSONObject("tree").getString("sha")
            val entries=JSONArray()
            for((snapshot,patch) in snapshots.zip(patches)) {
                coroutineContext.ensureActive()
                require(patch.targetPath==snapshot.path && patch.securityScan.passed && EngineeringPolicy.completeSource(patch.updatedContent) && EngineeringPolicy.samePackage(snapshot.content,patch.updatedContent))
                val actual=fetchFileContent(repoSlug,snapshot.path,expectedHead,token).getOrThrow()
                check(actual.sha==snapshot.sha) { "Source changed: ${snapshot.path}" }
                entries.put(JSONObject().put("path",snapshot.path).put("mode","100644").put("type","blob").put("content",patch.updatedContent))
            }
            val tree=request("/git/trees","POST",JSONObject().put("base_tree",baseTree).put("tree",entries)).getString("sha")
            coroutineContext.ensureActive()
            val commit=request("/git/commits","POST",JSONObject().put("message","JARVIS: atomic selected source upgrade").put("tree",tree).put("parents",JSONArray().put(expectedHead))).getString("sha")
            coroutineContext.ensureActive()
            check(getBranchHeadSha(repoSlug,branch,token).getOrThrow()==expectedHead) { "Branch advanced; no patch published" }
            val ref=request("/git/refs/heads/$branch","PATCH",JSONObject().put("sha",commit).put("force",false))
            check(ref.getJSONObject("object").getString("sha")==commit) { "Branch update not confirmed" }
            commit
        }.onFailure { if (it is CancellationException) throw it }
    }

    suspend fun verifyConnection(repoSlug: String, baseBranch: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        if (!EngineeringPolicy.validRepo(repoSlug.trim())) return@withContext Result.failure(IllegalArgumentException("Invalid repo (owner/repo)."))
        if (!EngineeringPolicy.validBase(baseBranch.trim())) return@withContext Result.failure(IllegalArgumentException("Invalid base branch."))
        if (token.isBlank()) return@withContext Result.failure(IllegalArgumentException("Enter GitHub token first."))
        runCatching {
            val sha = getBranchHeadSha(repoSlug.trim(), baseBranch.trim(), token.trim()).getOrThrow()
            "Verified ${repoSlug.trim()} (${baseBranch.trim()} @ ${sha.take(7)})"
        }
    }

    suspend fun listRecentWorkflowRuns(repoSlug: String, token: String, limit: Int = 5): Result<List<WorkflowRunStatus>> = withContext(Dispatchers.IO) {
        if (!EngineeringPolicy.validRepo(repoSlug.trim())) return@withContext Result.failure(IllegalArgumentException("Configure GitHub Repo (owner/repo) first."))
        runCatching {
            val req = baseReq("https://api.github.com/repos/${repoSlug.trim()}/actions/runs?per_page=${limit.coerceIn(1, 10)}", token).get().build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.boundedJsonText(2L * 1024 * 1024)
                if (!resp.isSuccessful) throw IOException("Actions list HTTP ${resp.code}: ${extractErr(raw)}")
                val arr = JSONObject(raw).optJSONArray("workflow_runs") ?: return@use emptyList()
                (0 until arr.length()).map { i ->
                    val r = arr.getJSONObject(i)
                    WorkflowRunStatus(
                        runId = r.getLong("id"),
                        status = r.optString("status", ""),
                        conclusion = r.optString("conclusion", ""),
                        htmlUrl = r.optString("html_url", ""),
                        headSha = r.optString("head_sha", "")
                    )
                }
            }
        }.onFailure { if (it is CancellationException) throw it }
    }

    suspend fun getLatestWorkflowRunForBranch(repoSlug: String, branch: String, token: String, expectedSha: String): Result<WorkflowRunStatus?> = withContext(Dispatchers.IO) {
        runCatching {
            val req = baseReq("https://api.github.com/repos/$repoSlug/actions/runs?branch=$branch&head_sha=$expectedSha&per_page=30", token).get().build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.boundedJsonText(2L * 1024 * 1024)
                if (!resp.isSuccessful) throw IOException("Actions poll HTTP ${resp.code}: ${extractErr(raw)}")
                val arr = JSONObject(raw).optJSONArray("workflow_runs")
                if (arr == null || arr.length() == 0) return@use null
                val allowedWorkflows = setOf(
                    ".github/workflows/build-apk.yml",
                    ".github/workflows/jarvis-infinity-v110-ultra.yml",
                    ".github/workflows/jarvis-infinity-v108-ultimate.yml",
                    ".github/workflows/jarvis-infinity-v107-autonomous.yml",
                    ".github/workflows/jarvis-infinity-v107-2-control-center.yml"
                )
                val r = (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { EngineeringPolicy.matchingCommit(expectedSha, it.optString("head_sha")) && it.optString("path").substringBefore('@') in allowedWorkflows } ?: return@use null
                WorkflowRunStatus(
                    runId = r.getLong("id"),
                    status = r.optString("status", ""),
                    conclusion = r.optString("conclusion", ""),
                    htmlUrl = r.optString("html_url", ""),
                    headSha = r.optString("head_sha", "")
                )
            }
        }.onFailure { if (it is CancellationException) throw it }
    }

    suspend fun fetchFailedJobSummary(repoSlug: String, runId: Long, token: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val req = baseReq("https://api.github.com/repos/$repoSlug/actions/runs/$runId/jobs", token).get().build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.boundedJsonText(2L * 1024 * 1024)
                if (!resp.isSuccessful) return@use "Workflow failed (HTTP ${resp.code})."
                val jobs = JSONObject(raw).optJSONArray("jobs") ?: return@use "No job details."
                val sb = StringBuilder()
                for (i in 0 until jobs.length()) {
                    val job = jobs.getJSONObject(i)
                    val steps = job.optJSONArray("steps") ?: continue
                    for (j in 0 until steps.length()) {
                        val st = steps.getJSONObject(j)
                        if (st.optString("conclusion") == "failure") {
                            sb.append("Failed step: ${st.optString("name")} (job: ${job.optString("name")})\n")
                        }
                    }
                }
                com.example.security.DiagnosticRedaction.clean(sb.toString()).take(6000).ifBlank { "Build/Test step failed. Check Actions log." }
            }
        }.onFailure { if (it is CancellationException) throw it }.getOrDefault("Unable to inspect failure logs.")
    }

    suspend fun createPullRequest(
        repoSlug: String,
        headBranch: String,
        baseBranch: String,
        title: String,
        body: String,
        token: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(EngineeringPolicy.validRepo(repoSlug) && FinalIntegrationPolicy.isolatedBranch(headBranch) && EngineeringPolicy.validBase(baseBranch))
            coroutineContext.ensureActive()
            val payload = JSONObject()
                .put("title", title)
                .put("head", headBranch)
                .put("base", baseBranch)
                .put("body", body)
                .put("draft", true)
            val req = baseReq("https://api.github.com/repos/$repoSlug/pulls", token)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.boundedJsonText(2L * 1024 * 1024)
                if (resp.code == 422) {
                    val existingUrl = findOpenPullRequest(repoSlug, headBranch, baseBranch, token)
                    if (existingUrl != null) return@use existingUrl
                }
                if (!resp.isSuccessful) throw IOException("PR create HTTP ${resp.code}: ${extractErr(raw)}")
                JSONObject(raw).getString("html_url")
            }
        }.onFailure { if (it is CancellationException) throw it }
    }

    private fun findOpenPullRequest(repoSlug: String, headBranch: String, baseBranch: String, token: String): String? {
        val owner = repoSlug.substringBefore('/')
        val url = "https://api.github.com/repos/$repoSlug/pulls?state=open&head=$owner:$headBranch&base=$baseBranch"
        val req = baseReq(url, token).get().build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val arr = JSONArray(resp.boundedJsonText(2L * 1024 * 1024))
                if (arr.length() > 0) arr.getJSONObject(0).optString("html_url").ifBlank { null } else null
            }
        }.getOrNull()
    }

    private fun extractErr(raw: String): String =
        runCatching { JSONObject(raw).optString("message") }.getOrNull().orEmpty().ifBlank { raw.take(180) }
}
