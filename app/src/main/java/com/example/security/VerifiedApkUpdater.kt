package com.example.security

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/**
 * Verified APK Self-Update Helper (v18 Supreme):
 * Verifies SHA-256 checksum of a downloaded APK file before launching the Android package installer.
 */
object VerifiedApkUpdater {

    fun installedSigningCertSha256(context: Context): String? = runCatching {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        signers(pm.getPackageInfo(context.packageName, flags)).firstOrNull()
    }.getOrNull()

    fun sha256OfFile(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                md.update(buf, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifyAndInstall(context: Context, apkFile: File, expectedSha256: String? = null): String {
        require(apkFile.isFile) { "APK file not found" }
        val expected = expectedSha256?.trim().orEmpty()
        require(Regex("[0-9a-fA-F]{64}").matches(expected)) { "Mandatory SHA-256 missing or invalid" }
        require(sha256OfFile(apkFile).equals(expected, ignoreCase = true)) { "SHA-256 mismatch" }
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = pm.getPackageInfo(context.packageName, flags)
        val incoming = pm.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: throw SecurityException("Unreadable APK")
        require(incoming.packageName == context.packageName) { "APK package mismatch" }
        val currentSigners = signers(installed)
        require(currentSigners.isNotEmpty() && currentSigners == signers(incoming)) { "APK signer mismatch" }
        require(version(incoming) > version(installed)) { "APK must have a newer version code" }
        require(pm.canRequestPackageInstalls()) { "Allow installation from this app in Android settings" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Checksum, package and signing identity verified; Android installer opened for approval."
    }
    @Suppress("DEPRECATION")
    private fun version(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}
