package com.example.voice

import android.content.Context
import android.net.Uri
import com.example.security.KeystoreCryptoManager
import java.io.File

object PorcupineWakeConfig {
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("jarvis_porcupine", Context.MODE_PRIVATE)

    fun accessKey(context: Context): String =
        KeystoreCryptoManager.decrypt(prefs(context).getString("encrypted_key", "").orEmpty())

    fun saveKey(context: Context, value: String) {
        val key = value.trim()
        val encrypted = if (key.isEmpty()) "" else KeystoreCryptoManager.encrypt(key)
        check(key.isEmpty() || encrypted.startsWith("ENC1:")) { "Keystore unavailable" }
        check(prefs(context).edit().putString("encrypted_key", encrypted).commit()) { "Key save failed" }
    }

    fun modelFile(context: Context): File = File(context.noBackupFilesDir, "hey-jarvis-android.ppn")

    fun importModel(context: Context, uri: Uri) {
        val destination = modelFile(context)
        val temp = File.createTempFile("porcupine-", ".ppn", context.noBackupFilesDir)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open model")
            input.use { source ->
                temp.outputStream().use { out ->
                    val bytes = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val n = source.read(bytes)
                        if (n < 0) break
                        total += n
                        require(total <= 4 * 1024 * 1024) { "Model exceeds 4 MB" }
                        out.write(bytes, 0, n)
                    }
                    require(total > 0) { "Empty model" }
                }
            }
            check(temp.renameTo(destination)) { "Could not replace model" }
        } finally {
            temp.delete()
        }
    }

    fun configured(context: Context): Boolean =
        modelFile(context).isFile && runCatching { accessKey(context).isNotBlank() }.getOrDefault(false)
}
