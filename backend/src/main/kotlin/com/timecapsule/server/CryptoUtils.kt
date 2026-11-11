// FILE: CryptoUtils.kt
package com.timecapsule.server

import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private val logger = LoggerFactory.getLogger(CryptoUtils::class.java)

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128
    private const val KEY_LENGTH_BYTES = 32

    private val secureRandom = SecureRandom()

    internal fun resolveConfiguredKey(): String? {
        return System.getenv("CAPSULE_AES_KEY")?.trim()?.takeIf { it.isNotEmpty() }
            ?: System.getProperty("CAPSULE_AES_KEY")?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Проверяет и декодирует 32-байтный ключ AES-256 в формате Base64.
     * Бросает исключение с понятным описанием при отсутствии или неверном формате ключа.
     */
    fun parseAesKey(rawKey: String?): SecretKeySpec {
        val normalized = rawKey?.trim()
        if (normalized.isNullOrEmpty()) {
            val errMsg = "КРИТИЧЕСКАЯ ОШИБКА: Переменная окружения CAPSULE_AES_KEY не задана. Задайте 32-байтный ключ AES-256 в формате Base64 (например, через ./generate-aes-key.sh), иначе сервер не может шифровать и расшифровывать капсулы."
            logger.error(errMsg)
            throw IllegalStateException(errMsg)
        }

        val keyBytes = try {
            Base64.getDecoder().decode(normalized)
        } catch (e: Exception) {
            val errMsg = "КРИТИЧЕСКАЯ ОШИБКА: Невозможно декодировать CAPSULE_AES_KEY из Base64: ${e.message}"
            logger.error(errMsg)
            throw IllegalArgumentException(errMsg, e)
        }

        if (keyBytes.size != KEY_LENGTH_BYTES) {
            val errMsg = "КРИТИЧЕСКАЯ ОШИБКА: Ключ AES-256 в CAPSULE_AES_KEY должен быть ровно $KEY_LENGTH_BYTES байт (получено ${keyBytes.size})."
            logger.error(errMsg)
            throw IllegalArgumentException(errMsg)
        }

        return SecretKeySpec(keyBytes, "AES")
    }

    fun validateCryptoConfig(rawKey: String? = resolveConfiguredKey()) {
        parseAesKey(rawKey)
        logger.info("CAPSULE_AES_KEY успешно загружен и верифицирован ($KEY_LENGTH_BYTES байт).")
    }

    private val secretKey: SecretKeySpec by lazy {
        parseAesKey(resolveConfiguredKey())
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
