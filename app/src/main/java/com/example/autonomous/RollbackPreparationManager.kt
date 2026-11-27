package com.example.autonomous

import android.content.Context
import com.example.BuildConfig
import com.example.data.OmegaPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class RollbackSnapshot(
    val id: String,
    val versionCode: Int,
    val versionName: String,
    val createdAtMs: Long,
    val sha256: String,
    val fileName: String
)

/**
 * V107.2 Safe Rollback Preparation Manager.
 * Creates SHA-256 verified, Keystore-encrypted configuration snapshots before upgrades
 * and validates checksums before restoring.
 */
object RollbackPreparationManager {

    private const val BACKUP_DIR = "backups"
    private const val INDEX_FILE = "rollback_index_v1072.json"

    fun sha256Bytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun prepareSnapshot(context: Context, preferences: OmegaPreferences): Result<RollbackSnapshot> = runCatching {
        val dir = File(context.applicationContext.filesDir, BACKUP_DIR).apply { mkdirs() }
        val now = System.currentTimeMillis()
        val id = "rb_${BuildConfig.VERSION_CODE}_$now"
        val fileName = "$id.enc"
        val target = File(dir, fileName)
        val encryptedJson = preferences.exportEncryptedBackupJson()
        val bytes = encryptedJson.toByteArray(Charsets.UTF_8)
        val hash = sha256Bytes(bytes)
        target.writeBytes(bytes)

        val snapshot = RollbackSnapshot(
            id = id,
            versionCode = BuildConfig.VERSION_CODE,
            versionName = BuildConfig.VERSION_NAME,
            createdAtMs = now,
            sha256 = hash,
            fileName = fileName
        )
        val updated = (listOf(snapshot) + listSnapshots(context)).take(10)
        saveIndex(dir, updated)
        snapshot
    }

    fun listSnapshots(context: Context): List<RollbackSnapshot> {
        val dir = File(context.applicationContext.filesDir, BACKUP_DIR)
        val index = File(dir, INDEX_FILE)
        if (!index.isFile) return emptyList()
        return runCatching {
            val arr = JSONArray(index.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val fileName = o.optString("fileName")
                if (fileName.isBlank() || ".." in fileName || "/" in fileName) return@mapNotNull null
                if (!File(dir, fileName).isFile) return@mapNotNull null
                RollbackSnapshot(
                    id = o.optString("id"),
                    versionCode = o.optInt("versionCode"),
                    versionName = o.optString("versionName"),
                    createdAtMs = o.optLong("createdAtMs"),
                    sha256 = o.optString("sha256"),
                    fileName = fileName
                )
            }
        }.getOrDefault(emptyList())
    }

    fun restoreSnapshot(context: Context, preferences: OmegaPreferences, snapshotId: String): Result<String> = runCatching {
        val snap = listSnapshots(context).firstOrNull { it.id == snapshotId }
            ?: throw IllegalArgumentException("Rollback snapshot not found: $snapshotId")
        val file = File(File(context.applicationContext.filesDir, BACKUP_DIR), snap.fileName)
        require(file.isFile) { "Rollback file missing on disk" }
        val bytes = file.readBytes()
        require(sha256Bytes(bytes).equals(snap.sha256, ignoreCase = true)) {
            "Rollback snapshot SHA-256 verification failed"
        }
        preferences.importEncryptedBackupJson(bytes.toString(Charsets.UTF_8))
        "Restored verified encrypted config from ${snap.versionName} (v${snap.versionCode})."
    }

    private fun saveIndex(dir: File, list: List<RollbackSnapshot>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("versionCode", s.versionCode)
                    .put("versionName", s.versionName)
                    .put("createdAtMs", s.createdAtMs)
                    .put("sha256", s.sha256)
                    .put("fileName", s.fileName)
            )
        }
        File(dir, INDEX_FILE).writeText(arr.toString())
    }
}
