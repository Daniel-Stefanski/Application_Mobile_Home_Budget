package com.example.homebudget.utils.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordSecurity {
    private const val SALT_SIZE_BYTES = 16
    private const val HASH_ITERATIONS = 120_000
    private const val HASH_KEY_LENGTH_BITS = 256
    private val secureRandom = SecureRandom()

    data class Credentials(
        val hash: String,
        val salt: String
    )

    fun createCredentials(password: String): Credentials {
        val saltBytes = ByteArray(SALT_SIZE_BYTES)
        secureRandom.nextBytes(saltBytes)
        val salt = Base64.getEncoder().encodeToString(saltBytes)
        val hash = hashPassword(password, saltBytes)
        return Credentials(hash = hash, salt = salt)
    }

    fun verify(password: String, storedHash: String, storedSalt: String): Boolean {
        if (storedHash.isBlank() || storedSalt.isBlank()) return false

        return try {
            val saltBytes = Base64.getDecoder().decode(storedSalt)
            val candidateHash = hashPassword(password, saltBytes)
            MessageDigest.isEqual(
                candidateHash.toByteArray(Charsets.UTF_8),
                storedHash.toByteArray(Charsets.UTF_8)
            )
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun hashPassword(password: String, saltBytes: ByteArray): String {
        val spec = PBEKeySpec(
            password.toCharArray(),
            saltBytes,
            HASH_ITERATIONS,
            HASH_KEY_LENGTH_BITS
        )

        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hashBytes = factory.generateSecret(spec).encoded
            Base64.getEncoder().encodeToString(hashBytes)
        } finally {
            spec.clearPassword()
        }
    }
}
