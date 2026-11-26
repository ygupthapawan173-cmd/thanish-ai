package com.example.omega
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class OmegaEntry(val id: String, val text: String, val task: Boolean, val done: Boolean)
class OmegaNotebook(c: Context) {
    private val prefs = c.applicationContext.getSharedPreferences("omega_notebook_v1", 0)
    companion object { private const val ALIAS = "omega_notebook_aes_v1"; private val lock = Any() }
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    private fun read(): List<OmegaEntry> {
        val raw = prefs.getString("vault", null) ?: return emptyList()
        val parts = raw.split(".", limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        val a = JSONArray(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
        require(a.length() <= 80)
        return (0 until a.length()).map { i -> a.getJSONObject(i).let { OmegaEntry(it.getString("id"), it.getString("text"), it.getBoolean("task"), it.getBoolean("done")) } }
    }
    private fun save(entries: List<OmegaEntry>): List<OmegaEntry> {
        require(entries.size <= 80)
        val a = JSONArray()
        entries.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text).put("task", it.task).put("done", it.done)) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.doFinal(a.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("vault", Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(bytes, Base64.NO_WRAP)).commit())
        return entries
    }
    fun list() = synchronized(lock) { read() }
    fun add(text: String, task: Boolean) = synchronized(lock) {
        val value = text.trim(); require(value.length in 1..1000)
        save(listOf(OmegaEntry(UUID.randomUUID().toString(), value, task, false)) + read())
    }
    fun edit(id: String, text: String) = synchronized(lock) {
        val value = text.trim(); require(value.length in 1..1000)
        save(read().map { if (it.id == id) it.copy(text = value) else it })
    }
    fun toggle(id: String) = synchronized(lock) { save(read().map { if (it.id == id && it.task) it.copy(done = !it.done) else it }) }
    fun delete(id: String) = synchronized(lock) { save(read().filterNot { it.id == id }) }
}

