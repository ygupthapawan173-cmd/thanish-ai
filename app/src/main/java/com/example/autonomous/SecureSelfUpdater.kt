package com.example.autonomous

import com.example.ai.boundedJsonText
import com.example.ai.awaitParsed
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.BuildConfig
import com.example.data.OmegaPreferences
import com.example.security.VerifiedApkUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class ReleaseUpdateInfo(
    val tagName: String,
    val changelog: String,
    val apkDownloadUrl: String,
    val expectedSha256: String?,
    val isNewerThanInstalled: Boolean
)

class SecureSelfUpdater(
    private val context: Context,
    private val preferences: OmegaPreferences
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .followSslRedirects(false)
        .build()

    suspend fun checkLatestRelease(repoSlug: String, token: String): Result<ReleaseUpdateInfo> = withContext(Dispatchers.IO) {
        if (!EngineeringPolicy.validRepo(repoSlug.trim())) return@withContext Result.failure(IllegalArgumentException("Set GitHub Repo (owner/repo) first."))
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/${repoSlug.trim()}/releases/latest")
                .addHeader("Accept", "application/vnd.github+json")
                .apply { if (token.isNotBlank()) addHeader("Authorization", "Bearer ${token.trim()}") }
                .get()
                .build()

            val obj = client.newCall(req).awaitParsed { resp ->
                if (!resp.isSuccessful) throw IOException("Release check HTTP ${resp.code}")
                JSONObject(resp.boundedJsonText(1024L * 1024))
            }
            val tag = obj.optString("tag_name", "unknown")
            val body = obj.optString("body", "No changelog provided.")
            val assets = obj.optJSONArray("assets")

            require((assets?.length() ?: 0) <= 1000) { "Too many release assets to validate safely" }
            val valid = (0 until (assets?.length() ?: 0)).mapNotNull { i ->
                val a = assets?.optJSONObject(i) ?: return@mapNotNull null
                val name = a.optString("name")
                val url = a.optString("browser_download_url")
                if (UpdateTransferPolicy.belongsTo(url, repoSlug.trim(), tag, name)) name to url else null
            }
            val apks = valid.filter { it.first.endsWith(".apk", ignoreCase = true) }
            val apk = apks.singleOrNull() ?: throw IOException("Release must contain exactly one eligible APK; ambiguous or missing APK blocked")
            val (apkName, apkUrl) = apk
            val checksums = valid.filter { it.first == "$apkName.sha256" || it.first == apkName.removeSuffix(".apk") + ".sha256" || it.first == "SHA256SUMS.txt" }
            val expectedSha: String? = if (checksums.isNotEmpty()) {
                val hashes = checksums.map { (name, url) ->
                    client.newCall(Request.Builder().url(url).get().build()).awaitParsed { sr ->
                        if (!sr.isSuccessful) throw IOException("Checksum download HTTP ${sr.code}")
                        UpdateTransferPolicy.checksumFor(sr.boundedJsonText(65_536L), apkName, name != "SHA256SUMS.txt")
                            ?: throw IOException("Checksum does not identify selected APK")
                    }
                }.toSet()
                hashes.singleOrNull() ?: throw IOException("Conflicting APK checksums")
            } else {
                Regex("""(?im)^SHA256:\s*([a-fA-F0-9]{64})\s*$""").findAll(body).map { it.groupValues[1].lowercase(java.util.Locale.ROOT) }.toSet().singleOrNull()
            }
            val isNewer = ReleaseVersionPolicy.newer(tag, BuildConfig.VERSION_NAME)
            ReleaseUpdateInfo(tag, body, apkUrl, expectedSha, isNewer)
        }.onFailure { if (it is CancellationException) throw it }
    }

    /**
     * Downloads APK, creates encrypted settings backup, enforces MANDATORY SHA-256 & Signing Certificate checks,
     * and launches Android's user-approved installation dialog.
     */
    suspend fun downloadVerifyAndPromptInstall(
        release: ReleaseUpdateInfo,
        onProgress: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(release.isNewerThanInstalled) { "Release is not a newer recognized version" }
            require(UpdateTransferPolicy.allowedAsset(release.apkDownloadUrl)) { "Untrusted release URL" }
            require(UpdateTransferPolicy.validHash(release.expectedSha256)) { "Missing or invalid SHA-256" }
            // 1. Mandatory pre-update encrypted backup
            onProgress("Creating encrypted pre-update backup...")
            val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
            val backupFile = File.createTempFile("pre_update_", ".enc", backupDir)
            try { backupFile.writeText(preferences.exportEncryptedBackupJson()) }
            catch (e: Exception) { backupFile.delete(); throw e }

            // 2. Mandatory SHA-256 presence check
            val expectedHash = release.expectedSha256
                ?: throw SecurityException("Update blocked: Release missing mandatory SHA-256 checksum.")

            // 3. Download APK to isolated cache directory
            onProgress("Downloading ${release.tagName} APK...")
            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File.createTempFile("infinity-update-", ".apk", updateDir)
            var handedOff = false
            try {
            val req = Request.Builder().url(release.apkDownloadUrl).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("APK download failed HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("Empty APK stream.")
                require(body.contentLength() <= UpdateTransferPolicy.MAX_APK_BYTES) { "APK exceeds size limit" }
                body.byteStream().use { input ->
                    apkFile.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            if (n == 0) throw IOException("APK stream made no progress")
                            total += n
                            require(total <= UpdateTransferPolicy.MAX_APK_BYTES) { "APK exceeds size limit" }
                            out.write(buffer, 0, n)
                        }
                        require(total > 0L && (body.contentLength() < 0L || total == body.contentLength())) { "Incomplete APK stream" }
                    }
                }
            }

            // 4. Mandatory SHA-256 verification
            onProgress("Verifying SHA-256 integrity...")
            val actualHash = VerifiedApkUpdater.sha256OfFile(apkFile)
            if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                apkFile.delete()
                throw SecurityException("SHA-256 mismatch! Expected ${expectedHash.take(12)}..., got ${actualHash.take(12)}.... Update deleted.")
            }

            // 5. Mandatory APK Signing Certificate verification against currently installed app
            onProgress("Verifying APK signing certificate...")
            val installedCerts = getInstalledAppCertFingerprints()
            val downloadedCerts = getApkArchiveCertFingerprints(apkFile.absolutePath)
            if (downloadedCerts.isEmpty() || installedCerts != downloadedCerts) {
                apkFile.delete()
                throw SecurityException("Signing certificate mismatch! Blocked to prevent signature conflicts and protect saved API keys, GitHub config, and user data.")
            }

            // 6. Launch Android OS user-approved installation screen
            onProgress("Verified! Launching user-approved installer...")
            coroutineContext.ensureActive()
            VerifiedApkUpdater.verifyAndInstall(context, apkFile, expectedHash).also { handedOff = true }
            } finally { if (!handedOff) apkFile.delete() }
        }.onFailure { if (it is CancellationException) throw it }
    }

    private fun getInstalledAppCertFingerprints(): Set<String> {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        return extractCertHashes(info)
    }

    private fun getApkArchiveCertFingerprints(apkPath: String): Set<String> {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNATURES)
        } ?: return emptySet()
        return extractCertHashes(info)
    }

    private fun extractCertHashes(info: android.content.pm.PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            @Suppress("DEPRECATION")
            info.signatures ?: emptyArray()
        }
        val md = MessageDigest.getInstance("SHA-256")
        return sigs.map { sig ->
            md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}
