package com.example.ai

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the user's OpenRouter key encrypted with an Android Keystore AES-GCM key.
 * The API key is never compiled into the APK or written to source control.
 */
class OpenRouterKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("zeno_secure_settings", Context.MODE_PRIVATE)

    fun save(apiKey: String) {
        val clean = apiKey.trim()
        require(clean.startsWith("sk-or-") && clean.length > 20) { "చెల్లుబాటు అయ్యే OpenRouter API key ఇవ్వండి." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        check(prefs.edit().putString(KEY_API, Base64.encodeToString(payload, Base64.NO_WRAP)).commit()) {
            "API key సేవ్ కాలేదు. మళ్లీ ప్రయత్నించండి."
        }
    }

    fun read(): String? {
        val encoded = prefs.getString(KEY_API, null) ?: return null
        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > IV_LENGTH)
            val iv = payload.copyOfRange(0, IV_LENGTH)
            val encrypted = payload.copyOfRange(IV_LENGTH, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_API).apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "zeno_openrouter_aes_key"
        const val KEY_API = "openrouter_api_key"
        const val IV_LENGTH = 12
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
