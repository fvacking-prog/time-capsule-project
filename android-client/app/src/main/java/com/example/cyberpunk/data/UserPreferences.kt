package com.example.cyberpunk.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom

val Context.dataStore by preferencesDataStore(name = "user_preferences")

object UserPreferences {
    private val PIN_HASH_KEY = stringPreferencesKey("pin_hash")
    private val PIN_SALT_KEY = stringPreferencesKey("pin_salt")
    private val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")

    fun hasPinFlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            !prefs[PIN_HASH_KEY].isNullOrBlank()
        }
    }

    fun isNotificationsEnabledFlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[NOTIFICATIONS_ENABLED_KEY] ?: true
        }
    }

    suspend fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[NOTIFICATIONS_ENABLED_KEY] = enabled
        }
    }

    suspend fun setPin(context: Context, pin: String) {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        val salt = saltBytes.joinToString("") { "%02x".format(it) }
        val hash = hashPin(salt, pin)
        context.dataStore.edit { prefs ->
            prefs[PIN_SALT_KEY] = salt
            prefs[PIN_HASH_KEY] = hash
        }
    }

    suspend fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = context.dataStore.data.first()
        val salt = prefs[PIN_SALT_KEY] ?: return false
        val storedHash = prefs[PIN_HASH_KEY] ?: return false
        val computedHash = hashPin(salt, pin)
        return computedHash == storedHash
    }

    suspend fun clearPin(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(PIN_SALT_KEY)
            prefs.remove(PIN_HASH_KEY)
        }
    }

    private fun hashPin(salt: String, pin: String): String {
        val input = "$salt:$pin".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

