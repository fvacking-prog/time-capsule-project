package com.example.cyberpunk.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

val Context.dataStore by preferencesDataStore(name = "user_preferences")

data class PinMigrationOutcome(
    val matched: Boolean,
    val migratedSaltHex: String? = null,
    val migratedHash: String? = null
)

data class PinAttemptState(
    val failedAttempts: Int = 0,
    val lockoutUntilMillis: Long = 0L
)

sealed class PinCheckResult {
    data object Success : PinCheckResult()
    data class Invalid(val attemptsLeft: Int) : PinCheckResult()
    data class LockedOut(val remainingSeconds: Int, val lockoutUntilMillis: Long) : PinCheckResult()
}

object UserPreferences {
    const val PBKDF2_ITERATIONS = 60_000
    const val PBKDF2_KEY_BITS = 256
    const val PBKDF2_PREFIX = "pbkdf2_v1:"
    const val MAX_PIN_ATTEMPTS = 5
    const val PIN_COOLDOWN_MILLIS = 30_000L

    private val PIN_HASH_KEY = stringPreferencesKey("pin_hash")
    private val PIN_SALT_KEY = stringPreferencesKey("pin_salt")
    private val PIN_FAILED_ATTEMPTS_KEY = intPreferencesKey("pin_failed_attempts")
    private val PIN_LOCKOUT_UNTIL_KEY = longPreferencesKey("pin_lockout_until_millis")
    private val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
    private val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
    private val QUIET_HOURS_ENABLED_KEY = booleanPreferencesKey("quiet_hours_enabled")
    private val FIRST_SEAL_NOTIF_PROMPTED_KEY = booleanPreferencesKey("first_seal_notif_prompted")

    fun hasPinFlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            !prefs[PIN_HASH_KEY].isNullOrBlank()
        }
    }

    fun isBiometricEnabledFlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[BIOMETRIC_ENABLED_KEY] ?: false
        }
    }

    fun isNotificationsEnabledFlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[NOTIFICATIONS_ENABLED_KEY] ?: true
        }
    }

    fun isQuietHoursEnabledFlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[QUIET_HOURS_ENABLED_KEY] ?: false
        }
    }

    fun hasPromptedNotificationOnSealFlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[FIRST_SEAL_NOTIF_PROMPTED_KEY] ?: false
        }
    }

    suspend fun setBiometricEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[BIOMETRIC_ENABLED_KEY] = enabled
        }
    }

    suspend fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[NOTIFICATIONS_ENABLED_KEY] = enabled
        }
    }

    suspend fun setQuietHoursEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[QUIET_HOURS_ENABLED_KEY] = enabled
        }
    }

    suspend fun setPromptedNotificationOnSeal(context: Context) {
        context.dataStore.edit { prefs ->
            prefs[FIRST_SEAL_NOTIF_PROMPTED_KEY] = true
        }
    }

    internal fun generateSaltHex(): String {
        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)
        return saltBytes.joinToString("") { "%02x".format(it) }
    }

    internal fun hashPinPbkdf2(saltHex: String, pin: String): String {
        val spec = PBEKeySpec(
            pin.toCharArray(),
            saltHex.toByteArray(Charsets.UTF_8),
            PBKDF2_ITERATIONS,
            PBKDF2_KEY_BITS
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = factory.generateSecret(spec).encoded
        spec.clearPassword()
        val hex = derived.joinToString("") { "%02x".format(it) }
        return "$PBKDF2_PREFIX$hex"
    }

    internal fun hashPinLegacySha256(saltHex: String, pin: String): String {
        val input = "$saltHex:$pin".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Проверяет PIN по новой схеме PBKDF2 или старой SHA-256 (с подготовкой данных для прозрачной миграции).
     */
    internal fun verifyAndMigratePinRecord(
        pin: String,
        saltHex: String,
        storedHash: String
    ): PinMigrationOutcome {
        if (storedHash.startsWith(PBKDF2_PREFIX)) {
            val computed = hashPinPbkdf2(saltHex, pin)
            val matched = MessageDigest.isEqual(
                computed.toByteArray(Charsets.UTF_8),
                storedHash.toByteArray(Charsets.UTF_8)
            )
            return PinMigrationOutcome(matched = matched)
        }

        // Прозрачная проверка старого SHA-256 хеша и миграция на PBKDF2 при успехе
        val legacyComputed = hashPinLegacySha256(saltHex, pin)
        val matchedLegacy = MessageDigest.isEqual(
            legacyComputed.toByteArray(Charsets.UTF_8),
            storedHash.toByteArray(Charsets.UTF_8)
        )
        if (!matchedLegacy) {
            return PinMigrationOutcome(matched = false)
        }
        val newSalt = generateSaltHex()
        val newPbkdf2Hash = hashPinPbkdf2(newSalt, pin)
        return PinMigrationOutcome(
            matched = true,
            migratedSaltHex = newSalt,
            migratedHash = newPbkdf2Hash
        )
    }

    /**
     * Чистая функция расчёта кулдауна (30 секунд после 5 неверных попыток).
     */
    internal fun evaluatePinAttempt(
        currentState: PinAttemptState,
        isPinCorrect: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<PinAttemptState, PinCheckResult> {
        if (nowMillis < currentState.lockoutUntilMillis) {
            val remainingSec = ((currentState.lockoutUntilMillis - nowMillis + 999L) / 1000L).toInt().coerceAtLeast(1)
            return currentState to PinCheckResult.LockedOut(
                remainingSeconds = remainingSec,
                lockoutUntilMillis = currentState.lockoutUntilMillis
            )
        }

        if (isPinCorrect) {
            return PinAttemptState(failedAttempts = 0, lockoutUntilMillis = 0L) to PinCheckResult.Success
        }

        val nextFailed = currentState.failedAttempts + 1
        return if (nextFailed >= MAX_PIN_ATTEMPTS) {
            val lockoutUntil = nowMillis + PIN_COOLDOWN_MILLIS
            PinAttemptState(failedAttempts = 0, lockoutUntilMillis = lockoutUntil) to PinCheckResult.LockedOut(
                remainingSeconds = (PIN_COOLDOWN_MILLIS / 1000L).toInt(),
                lockoutUntilMillis = lockoutUntil
            )
        } else {
            PinAttemptState(failedAttempts = nextFailed, lockoutUntilMillis = 0L) to PinCheckResult.Invalid(
                attemptsLeft = MAX_PIN_ATTEMPTS - nextFailed
            )
        }
    }

    suspend fun getLockoutUntilMillis(context: Context): Long {
        val prefs = context.dataStore.data.first()
        return prefs[PIN_LOCKOUT_UNTIL_KEY] ?: 0L
    }

    suspend fun setPin(context: Context, pin: String) {
        val salt = generateSaltHex()
        val hash = hashPinPbkdf2(salt, pin)
        context.dataStore.edit { prefs ->
            prefs[PIN_SALT_KEY] = salt
            prefs[PIN_HASH_KEY] = hash
            prefs[PIN_FAILED_ATTEMPTS_KEY] = 0
            prefs[PIN_LOCKOUT_UNTIL_KEY] = 0L
        }
    }

    suspend fun verifyPinWithCooldown(
        context: Context,
        pin: String,
        nowMillis: Long = System.currentTimeMillis()
    ): PinCheckResult {
        val prefs = context.dataStore.data.first()
        val currentState = PinAttemptState(
            failedAttempts = prefs[PIN_FAILED_ATTEMPTS_KEY] ?: 0,
            lockoutUntilMillis = prefs[PIN_LOCKOUT_UNTIL_KEY] ?: 0L
        )
        if (nowMillis < currentState.lockoutUntilMillis) {
            val remainingSec = ((currentState.lockoutUntilMillis - nowMillis + 999L) / 1000L).toInt().coerceAtLeast(1)
            return PinCheckResult.LockedOut(
                remainingSeconds = remainingSec,
                lockoutUntilMillis = currentState.lockoutUntilMillis
            )
        }

        val salt = prefs[PIN_SALT_KEY] ?: return PinCheckResult.Invalid(attemptsLeft = MAX_PIN_ATTEMPTS)
        val storedHash = prefs[PIN_HASH_KEY] ?: return PinCheckResult.Invalid(attemptsLeft = MAX_PIN_ATTEMPTS)

        val migrationOutcome = verifyAndMigratePinRecord(pin, salt, storedHash)
        val (nextState, result) = evaluatePinAttempt(currentState, migrationOutcome.matched, nowMillis)

        context.dataStore.edit { editPrefs ->
            editPrefs[PIN_FAILED_ATTEMPTS_KEY] = nextState.failedAttempts
            editPrefs[PIN_LOCKOUT_UNTIL_KEY] = nextState.lockoutUntilMillis
            if (migrationOutcome.matched && migrationOutcome.migratedSaltHex != null && migrationOutcome.migratedHash != null) {
                editPrefs[PIN_SALT_KEY] = migrationOutcome.migratedSaltHex
                editPrefs[PIN_HASH_KEY] = migrationOutcome.migratedHash
            }
        }
        return result
    }

    suspend fun verifyPin(context: Context, pin: String): Boolean {
        return verifyPinWithCooldown(context, pin) is PinCheckResult.Success
    }

    suspend fun resetPinLockout(context: Context) {
        context.dataStore.edit { prefs ->
            prefs[PIN_FAILED_ATTEMPTS_KEY] = 0
            prefs[PIN_LOCKOUT_UNTIL_KEY] = 0L
        }
    }

    suspend fun clearPin(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(PIN_SALT_KEY)
            prefs.remove(PIN_HASH_KEY)
            prefs.remove(PIN_FAILED_ATTEMPTS_KEY)
            prefs.remove(PIN_LOCKOUT_UNTIL_KEY)
            prefs.remove(BIOMETRIC_ENABLED_KEY)
        }
    }
}
