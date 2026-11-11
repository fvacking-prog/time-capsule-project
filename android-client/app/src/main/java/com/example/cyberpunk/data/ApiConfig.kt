package com.example.cyberpunk.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.cyberpunk.BuildConfig
import com.timecapsule.shared.UserDto
import kotlinx.coroutines.flow.first
import java.net.URI

private val Context.authDataStore by preferencesDataStore(name = "time_capsule_auth_datastore")

object ApiConfig {
    private const val PREFS_NAME = "time_capsule_prefs"
    private const val KEY_BASE_URL = "base_url"

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
        val token = prefs[DS_SESSION_TOKEN]?.takeIf { it.isNotBlank() } ?: return null
        val userId = prefs[DS_USER_ID]?.takeIf { it.isNotBlank() } ?: return null
        val username = prefs[DS_USERNAME]?.takeIf { it.isNotBlank() } ?: userId
        val email = prefs[DS_EMAIL]?.takeIf { it.isNotBlank() }
        val photoUrl = prefs[DS_PHOTO_URL]?.takeIf { it.isNotBlank() }
        val handle = prefs[DS_USER_HANDLE]?.takeIf { it.isNotBlank() }
        val avatarUrl = prefs[DS_USER_AVATAR_URL]?.takeIf { it.isNotBlank() } ?: photoUrl

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
        context.authDataStore.edit { prefs ->
            prefs[DS_SESSION_TOKEN] = token
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
