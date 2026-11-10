// FILE: CryptoUtils.kt
package com.timecapsule.server

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128
    private const val KEY_LENGTH_BYTES = 32

    // 32-байтный фиксированный демо-ключ в Base64 ("0123456789abcdef0123456789abcdef")
    private const val DEMO_KEY_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="

    private val secureRandom = SecureRandom()

    private val secretKey: SecretKeySpec by lazy {
        val base64Key = System.getenv("CAPSULE_AES_KEY")
            ?.takeIf { it.isNotBlank() }
            ?: DEMO_KEY_BASE64

        val keyBytes = Base64.getDecoder().decode(base64Key)
        require(keyBytes.size == KEY_LENGTH_BYTES) {
            "AES-256 key must be exactly $KEY_LENGTH_BYTES bytes, got ${keyBytes.size}"
        }
        SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plain: String): String {
        val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))

        val cipherBytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(token: String): String {
        val combined = Base64.getDecoder().decode(token)
        require(combined.size > IV_LENGTH_BYTES) { "Invalid encrypted token" }

        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val cipherBytes = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))

        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }
}