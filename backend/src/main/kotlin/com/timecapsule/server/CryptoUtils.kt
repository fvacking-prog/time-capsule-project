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

    // 32-байтный фиксированный демо-ключ в Base64 ("0123456789abcdef0123456789abcdef")
    const val DEMO_KEY_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="

    private val secureRandom = SecureRandom()

    fun isDemoMode(): Boolean {
        val flag = System.getenv("DEMO_MODE") ?: System.getProperty("DEMO_MODE")
        // Режим Демо активен по умолчанию, пока явно не задано DEMO_MODE=false
        return flag?.equals("false", ignoreCase = true) != true
    }

    fun validateCryptoConfig() {
        val isDemo = isDemoMode()
        val customKey = System.getenv("CAPSULE_AES_KEY")?.trim()

        if (!isDemo && customKey.isNullOrEmpty()) {
            val errMsg = "КРИТИЧЕСКАЯ ОШИБКА: Сервер запущен в боевом режиме (DEMO_MODE=false), но переменная окружения CAPSULE_AES_KEY не задана. Задайте 32-байтный Base64 ключ AES-256 для шифрования данных."
            logger.error(errMsg)
            throw IllegalStateException(errMsg)
        }

        if (customKey.isNullOrEmpty()) {
            logger.warn("ВНИМАНИЕ: DEMO_MODE=true. Используется демонстрационный ключ AES-256. В продакшене обязательно задайте CAPSULE_AES_KEY!")
        } else {
            val keyBytes = try {
                Base64.getDecoder().decode(customKey)
            } catch (e: Exception) {
                val errMsg = "КРИТИЧЕСКАЯ ОШИБКА: Невозможно декодировать CAPSULE_AES_KEY из Base64: ${e.message}"
                logger.error(errMsg)
                throw IllegalArgumentException(errMsg, e)
            }
            if (keyBytes.size != KEY_LENGTH_BYTES) {
                val errMsg = "КРИТИЧЕСКАЯ ОШИБКА: Ключ AES-256 должен быть ровно $KEY_LENGTH_BYTES байт (получено ${keyBytes.size})."
                logger.error(errMsg)
                throw IllegalArgumentException(errMsg)
            }
            logger.info("CAPSULE_AES_KEY успешно загружен и верифицирован ($KEY_LENGTH_BYTES байт).")
        }
    }

    private val secretKey: SecretKeySpec by lazy {
        val isDemo = isDemoMode()
        val customKey = System.getenv("CAPSULE_AES_KEY")?.trim()

        val base64Key = if (!customKey.isNullOrEmpty()) {
            customKey
        } else if (isDemo) {
            DEMO_KEY_BASE64
        } else {
            throw IllegalStateException("CAPSULE_AES_KEY не задан при DEMO_MODE=false")
        }

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
