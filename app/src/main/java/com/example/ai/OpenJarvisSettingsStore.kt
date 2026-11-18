package com.example.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class OpenJarvisConnection(
    val baseUrl: String,
    val apiKey: String,
    val model: String
)

/** Stores the OpenJarvis endpoint and encrypts its API key with Android Keystore. */
class OpenJarvisSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("zeno_openjarvis_settings", Context.MODE_PRIVATE)

    fun save(baseUrl: String, apiKey: String, model: String = "openrouter/free") {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        val cleanKey = apiKey.trim()
        val cleanModel = model.trim()
        require(cleanUrl.startsWith("https://", ignoreCase = true)) {
            "OpenJarvis URL తప్పనిసరిగా https:// తో ప్రారంభం కావాలి."
        }
        require(cleanKey.length >= 20) { "OpenJarvis API key సరైనది కాదు." }
        require(cleanModel.isNotBlank() && cleanModel.length <= 180) { "Model ID ఇవ్వండి." }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(cleanKey.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        check(prefs.edit()
            .putString(KEY_URL, cleanUrl)
            .putString(KEY_MODEL, cleanModel)
            .putString(KEY_SECRET, Base64.encodeToString(payload, Base64.NO_WRAP))
            .commit()) { "OpenJarvis settings save కాలేదు." }
    }

    fun read(): OpenJarvisConnection? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        val model = prefs.getString(KEY_MODEL, "openrouter/free") ?: "openrouter/free"
        val encoded = prefs.getString(KEY_SECRET, null) ?: return null
        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > IV_LENGTH)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(128, payload.copyOfRange(0, IV_LENGTH))
            )
            val key = String(cipher.doFinal(payload.copyOfRange(IV_LENGTH, payload.size)), Charsets.UTF_8)
            OpenJarvisConnection(url, key, model)
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "zeno_openjarvis_aes_key"
        const val KEY_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_SECRET = "encrypted_api_key"
        const val IV_LENGTH = 12
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
