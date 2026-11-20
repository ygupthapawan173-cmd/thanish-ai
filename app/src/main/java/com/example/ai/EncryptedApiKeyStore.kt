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

enum class AiProvider(
    val title: String,
    val subtitle: String,
    val defaultModel: String,
    val endpoint: String,
    val isGemini: Boolean = false
) {
    GEMINI("Google Gemini", "Google AI Studio API", "gemini-3.8-flash",
        "https://generativelanguage.googleapis.com/v1beta/models", true),
    GROQ("Groq", "Fast hosted open models", "openai/gpt-oss-20b",
        "https://api.groq.com/openai/v1/chat/completions"),
    OPENROUTER("OpenRouter", "Multi-provider model routing", "openrouter/free",
        "https://openrouter.ai/api/v1/chat/completions"),
    OPENAI("OpenAI", "OpenAI API", "gpt-4.1-mini",
        "https://api.openai.com/v1/chat/completions")
}

/**
 * API keys are encrypted with an AES-GCM key held by Android Keystore.
 * Only ciphertext and the random IV are persisted in SharedPreferences.
 */
class EncryptedApiKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "pandu_ai_provider_vault", Context.MODE_PRIVATE
    )

    fun saveApiKey(provider: AiProvider, key: String): Boolean {
        val clean = key.trim()
        if (clean.isEmpty()) return false
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return prefs.edit().putString(keyName(provider), value).commit()
    }

    fun getApiKey(provider: AiProvider): String? {
        val value = prefs.getString(keyName(provider), null) ?: return null
        return runCatching {
            val parts = value.split(".", limit = 2)
            require(parts.size == 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8).takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun hasApiKey(provider: AiProvider): Boolean = getApiKey(provider) != null

    fun deleteApiKey(provider: AiProvider) {
        prefs.edit().remove(keyName(provider)).commit()
    }

    fun setActiveProvider(provider: AiProvider) {
        prefs.edit().putString("active_provider", provider.name).apply()
    }

    fun getActiveProvider(): AiProvider =
        prefs.getString("active_provider", null)?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() }
            ?: AiProvider.GEMINI

    fun setModel(provider: AiProvider, model: String) {
        prefs.edit().putString("model_${provider.name}", model.trim().ifBlank { provider.defaultModel }).apply()
    }

    fun getModel(provider: AiProvider): String =
        prefs.getString("model_${provider.name}", null)?.takeIf { it.isNotBlank() } ?: provider.defaultModel

    private fun keyName(provider: AiProvider) = "key_${provider.name.lowercase()}"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "pandu_babu_api_key_vault_v1"
    }
}
