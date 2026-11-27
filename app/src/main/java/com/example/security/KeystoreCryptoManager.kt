package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed Android Keystore AES-256-GCM encryption for API keys & personal memory.
 */
object KeystoreCryptoManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "omega_v18_master_aes_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    @Synchronized private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(spec)
        return gen.generateKey()
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            "ENC1:" + Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (_: Exception) {
            throw IllegalStateException("Android Keystore encryption failed; data was not saved")
        }
    }

    /** Strict decoder for migrated database columns. Never returns an empty value on auth failure. */
    fun decryptStrict(stored: String): String {
        if (stored.isEmpty()) return ""
        require(stored.startsWith("ENC1:")) { "Encrypted database value required" }
        val parts = stored.removePrefix("ENC1:").split(":")
        require(parts.size == 2) { "Invalid encrypted value" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val data = Base64.decode(parts[1], Base64.NO_WRAP)
        require(iv.size == 12 && data.size >= 16) { "Invalid encrypted value" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val existing = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw IllegalStateException("Original encryption key unavailable")
        cipher.init(Cipher.DECRYPT_MODE, existing, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    fun decrypt(stored: String): String {
        if (!stored.startsWith("ENC1:")) return stored // legacy preference compatibility
        return runCatching { decryptStrict(stored) }.getOrDefault("")
    }
}
