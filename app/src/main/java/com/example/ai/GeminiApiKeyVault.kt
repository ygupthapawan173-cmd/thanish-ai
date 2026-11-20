package com.example.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores user-supplied Gemini API keys encrypted at rest with an Android Keystore AES-GCM key.
 * Keys are never written into source code, BuildConfig, or APK resources.
 */
class GeminiApiKeyVault(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "pandu_gemini_api_keys",
        Context.MODE_PRIVATE
    )

    fun saveKey(slot: Int, value: String) {
        require(slot in 1..SLOT_COUNT) { "API key slot must be between 1 and $SLOT_COUNT" }
        val preferenceName = preferenceName(slot)
        val key = value.trim()
        if (key.isEmpty()) {
            preferences.edit().remove(preferenceName).apply()
            return
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val packed = ByteBuffer.allocate(1 + iv.size + encrypted.size)
            .put(iv.size.toByte())
            .put(iv)
            .put(encrypted)
            .array()
        preferences.edit()
            .putString(preferenceName, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
    }

    fun readKey(slot: Int): String? {
        require(slot in 1..SLOT_COUNT) { "API key slot must be between 1 and $SLOT_COUNT" }
        val encoded = preferences.getString(preferenceName(slot), null) ?: return null
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(packed)
            val ivSize = buffer.get().toInt() and 0xff
            require(ivSize in 12..16 && buffer.remaining() > ivSize)
            val iv = ByteArray(ivSize).also(buffer::get)
            val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            // A restored preference may not have its original device-bound Keystore key.
            preferences.edit().remove(preferenceName(slot)).apply()
            null
        }
    }

    fun configuredKeys(): List<Pair<Int, String>> =
        (1..SLOT_COUNT).mapNotNull { slot -> readKey(slot)?.let { slot to it } }

    private fun preferenceName(slot: Int) = "gemini_key_slot_$slot"

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val SLOT_COUNT = 4
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "pandu_babu_gemini_keys_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
