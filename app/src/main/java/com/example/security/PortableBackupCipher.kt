package com.example.security

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Versioned authenticated portable backup. No Android Keystore dependency. */
object PortableBackupCipher {
    const val MAX_PLAINTEXT = 16 * 1024 * 1024
    const val HEADER_SIZE = 36
    const val MAX_ARCHIVE = MAX_PLAINTEXT + HEADER_SIZE + 16
    private const val ITERATIONS = 600_000
    private val magic = byteArrayOf(0x4a, 0x49, 0x42, 0x31)
    private fun key(password: CharArray, salt: ByteArray): ByteArray {
        require(password.size in 12..256) { "Use a password of 12–256 characters" }
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    fun seal(plain: ByteArray, password: CharArray): ByteArray {
        require(plain.size in 1..MAX_PLAINTEXT) { "Backup size limit exceeded" }
        val rng = SecureRandom()
        val salt = ByteArray(16).also(rng::nextBytes)
        val iv = ByteArray(12).also(rng::nextBytes)
        val header = ByteBuffer.allocate(HEADER_SIZE).put(magic).putInt(ITERATIONS).put(salt).put(iv).array()
        val secret = key(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(header)
            header + cipher.doFinal(plain)
        } finally { secret.fill(0) }
    }
    fun open(archive: ByteArray, password: CharArray): ByteArray {
        require(archive.size in (HEADER_SIZE + 17)..MAX_ARCHIVE) { "Invalid backup size" }
        val header = archive.copyOfRange(0, HEADER_SIZE)
        val buffer = ByteBuffer.wrap(header)
        require(ByteArray(4).also { buffer.get(it) }.contentEquals(magic) && buffer.int == ITERATIONS) { "Unsupported backup format" }
        val salt = ByteArray(16).also { buffer.get(it) }
        val iv = ByteArray(12).also { buffer.get(it) }
        val secret = key(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(header)
            cipher.doFinal(archive, HEADER_SIZE, archive.size - HEADER_SIZE)
        } finally { secret.fill(0) }
    }
}
