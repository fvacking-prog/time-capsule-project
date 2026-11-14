package com.example.cyberpunk.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.cyberpunk.BuildConfig
import com.timecapsule.shared.UserDto
import kotlinx.coroutines.flow.first
import java.net.URI
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.authDataStore by preferencesDataStore(name = "time_capsule_auth_datastore")

object ApiConfig {
    private const val PREFS_NAME = "time_capsule_prefs"
    private const val KEY_BASE_URL = "base_url"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "timecapsule_session_token_aes_key"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    const val ENCRYPTED_TOKEN_PREFIX = "enc_v1:"

    private val DS_SESSION_TOKEN = stringPreferencesKey("session_token")
    private val DS_USER_ID = stringPreferencesKey("user_id")
    private val DS_USERNAME = stringPreferencesKey("username")
    private val DS_EMAIL = stringPreferencesKey("email")
    private val DS_PHOTO_URL = stringPreferencesKey("photo_url")
    private val DS_USER_HANDLE = stringPreferencesKey("user_handle")
    private val DS_USER_AVATAR_URL = stringPreferencesKey("user_avatar_url")

    val DEFAULT_BASE_URL: String
        get() = normalizeUrl(BuildConfig.API_BASE_URL)

    @Volatile
    var BASE_URL: String = DEFAULT_BASE_URL
        private set

    @Volatile
    var sessionToken: String? = null
        private set

    fun normalizeUrl(rawUrl: String): String =
        rawUrl.trim().trimEnd('/').replace(Regex("^https://", RegexOption.IGNORE_CASE), "https://")
            .replace(Regex("^http://", RegexOption.IGNORE_CASE), "http://")

    fun isValidServerUrl(rawUrl: String, allowCleartext: Boolean = BuildConfig.DEBUG): Boolean {
        val normalized = normalizeUrl(rawUrl)
        if (normalized.isBlank() || normalized.any { it.isWhitespace() }) return false
        val uri = try {
            URI(normalized)
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase() ?: return false
        if (uri.host.isNullOrBlank()) return false
        return when (scheme) {
            "https" -> true
            "http" -> allowCleartext
            else -> false
        }
    }

    internal fun resolveInitialUrl(
        buildConfigUrl: String,
        persistedUrl: String?,
        isDebug: Boolean
    ): String {
        val base = normalizeUrl(buildConfigUrl)
        if (!isDebug) return base
        val candidate = persistedUrl?.let(::normalizeUrl)
        return if (candidate != null && isValidServerUrl(candidate, allowCleartext = true)) {
            candidate
        } else {
            base
        }
    }

    /**
     * Шифрует токен сессии с помощью переданного AES-ключа (AES/GCM/NoPadding)
     * и возвращает строку с префиксом `enc_v1:`.
     */
    internal fun encryptTokenWithKey(plainToken: String, secretKey: SecretKey): String {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plainToken.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return ENCRYPTED_TOKEN_PREFIX + Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Расшифровывает сохранённое значение токена или определяет, что токен хранится
     * в старом открытом формате и требует прозрачной миграции.
     * Возвращает Pair(plainToken, needsMigration).
     */
    internal fun decryptOrMigrateTokenWithKey(
        storedValue: String,
        secretKey: SecretKey
    ): Pair<String, Boolean>? {
        val trimmed = storedValue.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith(ENCRYPTED_TOKEN_PREFIX)) {
            // Прозрачная миграция со старого открытого токена
            return trimmed to true
        }
        return try {
            val encoded = trimmed.removePrefix(ENCRYPTED_TOKEN_PREFIX)
            val combined = Base64.getDecoder().decode(encoded)
            if (combined.size <= GCM_IV_LENGTH_BYTES) return null
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val ciphertext = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            val plainBytes = cipher.doFinal(ciphertext)
            String(plainBytes, Charsets.UTF_8) to false
        } catch (_: Throwable) {
            null
        }
    }

    private fun getOrCreateKeystoreSecretKey(): SecretKey? = try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = (keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existingKey != null) {
            existingKey
        } else {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    } catch (_: Throwable) {
        null
    }

    fun init(context: Context) {
        if (BuildConfig.DEBUG) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            BASE_URL = resolveInitialUrl(
                buildConfigUrl = BuildConfig.API_BASE_URL,
                persistedUrl = prefs.getString(KEY_BASE_URL, null),
                isDebug = true
            )
        } else {
            BASE_URL = DEFAULT_BASE_URL
        }
    }

    fun saveBaseUrl(context: Context, url: String) {
        if (!BuildConfig.DEBUG) return
        val normalized = normalizeUrl(url).ifBlank { DEFAULT_BASE_URL }
        require(isValidServerUrl(normalized, allowCleartext = true)) {
            "Некорректный адрес сервера: $url"
        }
        BASE_URL = normalized
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, normalized)
            .apply()
    }

    fun resetBaseUrl(context: Context) {
        if (!BuildConfig.DEBUG) return
        BASE_URL = DEFAULT_BASE_URL
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_BASE_URL)
            .apply()
    }

    suspend fun loadSession(context: Context): Pair<UserDto, String>? {
        val prefs = context.authDataStore.data.first()
        val rawStoredToken = prefs[DS_SESSION_TOKEN]?.takeIf { it.isNotBlank() } ?: return null
        val userId = prefs[DS_USER_ID]?.takeIf { it.isNotBlank() } ?: return null
        val username = prefs[DS_USERNAME]?.takeIf { it.isNotBlank() } ?: userId
        val email = prefs[DS_EMAIL]?.takeIf { it.isNotBlank() }
        val photoUrl = prefs[DS_PHOTO_URL]?.takeIf { it.isNotBlank() }
        val handle = prefs[DS_USER_HANDLE]?.takeIf { it.isNotBlank() }
        val avatarUrl = prefs[DS_USER_AVATAR_URL]?.takeIf { it.isNotBlank() } ?: photoUrl

        val keystoreKey = getOrCreateKeystoreSecretKey()
        val token = if (keystoreKey != null) {
            val (resolvedToken, needsMigration) = decryptOrMigrateTokenWithKey(rawStoredToken, keystoreKey)
                ?: return null
            if (needsMigration) {
                runCatching {
                    val encrypted = encryptTokenWithKey(resolvedToken, keystoreKey)
                    context.authDataStore.edit { editPrefs ->
                        editPrefs[DS_SESSION_TOKEN] = encrypted
                    }
                }
            }
            resolvedToken
        } else {
            if (rawStoredToken.startsWith(ENCRYPTED_TOKEN_PREFIX)) return null
            rawStoredToken
        }

        sessionToken = token
        val user = UserDto(
            id = userId,
            username = username,
            email = email,
            photoUrl = photoUrl,
            handle = handle,
            avatarUrl = avatarUrl
        )
        return user to token
    }

    suspend fun saveSession(context: Context, user: UserDto, token: String) {
        sessionToken = token
        val resolvedAvatar = user.avatarUrl ?: user.photoUrl
        val keystoreKey = getOrCreateKeystoreSecretKey()
        val storedTokenValue = if (keystoreKey != null) {
            runCatching { encryptTokenWithKey(token, keystoreKey) }.getOrDefault(token)
        } else {
            token
        }
        context.authDataStore.edit { prefs ->
            prefs[DS_SESSION_TOKEN] = storedTokenValue
            prefs[DS_USER_ID] = user.id
            prefs[DS_USERNAME] = user.username
            if (user.email != null) {
                prefs[DS_EMAIL] = user.email
            } else {
                prefs.remove(DS_EMAIL)
            }
            if (user.photoUrl != null) {
                prefs[DS_PHOTO_URL] = user.photoUrl
            } else {
                prefs.remove(DS_PHOTO_URL)
            }
            if (user.handle != null) {
                prefs[DS_USER_HANDLE] = user.handle
            } else {
                prefs.remove(DS_USER_HANDLE)
            }
            if (resolvedAvatar != null) {
                prefs[DS_USER_AVATAR_URL] = resolvedAvatar
            } else {
                prefs.remove(DS_USER_AVATAR_URL)
            }
        }
    }

    suspend fun clearSession(context: Context) {
        sessionToken = null
        context.authDataStore.edit { prefs ->
            prefs.remove(DS_SESSION_TOKEN)
            prefs.remove(DS_USER_ID)
            prefs.remove(DS_USERNAME)
            prefs.remove(DS_EMAIL)
            prefs.remove(DS_PHOTO_URL)
            prefs.remove(DS_USER_HANDLE)
            prefs.remove(DS_USER_AVATAR_URL)
        }
    }
}
