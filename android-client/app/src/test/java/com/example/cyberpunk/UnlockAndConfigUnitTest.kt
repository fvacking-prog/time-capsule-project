package com.example.cyberpunk

import com.example.cyberpunk.data.ApiConfig
import com.example.cyberpunk.data.AppUpdateManager
import com.example.cyberpunk.data.AppUpdatePolicy
import com.example.cyberpunk.data.CapsuleUnlockScheduler
import com.example.cyberpunk.data.ColdStartRetry
import com.example.cyberpunk.data.GeoHelper
import com.example.cyberpunk.data.LocationProvider
import com.example.cyberpunk.data.MediaHelper
import com.example.cyberpunk.data.PinAttemptState
import com.example.cyberpunk.data.PinCheckResult
import com.example.cyberpunk.data.SessionExpiredException
import com.example.cyberpunk.data.UpdateRequirement
import com.example.cyberpunk.data.UserPreferences
import com.timecapsule.shared.AppVersionDto
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.nio.file.Files
import javax.crypto.KeyGenerator
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import com.example.cyberpunk.data.HandleLocalValidation
import com.example.cyberpunk.data.HandleValidator
import com.example.cyberpunk.data.UnlockDateTimeHelper
import com.timecapsule.shared.CapsuleDto
import com.timecapsule.shared.CapsuleStageDto
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnlockAndConfigUnitTest {

    @Test
    fun `validateMinimumLead enforces strictly at least plus 1 minute`() {
        val now = 1_700_000_000_000L
        assertFalse(UnlockDateTimeHelper.validateMinimumLead(now - 1_000L, now))
        assertFalse(UnlockDateTimeHelper.validateMinimumLead(now + 59_999L, now))
        assertTrue(UnlockDateTimeHelper.validateMinimumLead(now + 60_000L, now))
        assertTrue(UnlockDateTimeHelper.validateMinimumLead(now + 120_000L, now))
    }

    @Test
    fun `mergePickedUtcDateWithExistingTime preserves time for future date and advances past time today`() {
        val zone = ZoneId.of("UTC")
        // now = 2026-11-11T12:20:30Z
        val nowMillis = ZonedDateTime.of(2026, 11, 11, 12, 20, 30, 0, ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val existingMillis = ZonedDateTime.of(2026, 11, 18, 9, 15, 0, 0, ZoneOffset.UTC)
            .toInstant().toEpochMilli()

        // Selecting today in DatePicker (09:15 < 12:22) clamps to earliest valid (12:22:00)
        val todayUtc = ZonedDateTime.of(2026, 11, 11, 0, 0, 0, 0, ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val mergedToday = UnlockDateTimeHelper.mergePickedUtcDateWithExistingTime(
            utcDateMillis = todayUtc,
            existingMillis = existingMillis,
            nowMillis = nowMillis,
            zoneId = zone
        )
        val mergedTodayZdt = Instant.ofEpochMilli(mergedToday).atZone(zone)
        assertEquals(2026, mergedTodayZdt.year)
        assertEquals(11, mergedTodayZdt.monthValue)
        assertEquals(11, mergedTodayZdt.dayOfMonth)
        assertEquals(12, mergedTodayZdt.hour)
        assertEquals(22, mergedTodayZdt.minute)
        assertTrue(UnlockDateTimeHelper.validateMinimumLead(mergedToday, nowMillis))

        // Selecting future day preserves existing 09:15 time
        val futureUtc = ZonedDateTime.of(2026, 11, 20, 0, 0, 0, 0, ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val mergedFuture = UnlockDateTimeHelper.mergePickedUtcDateWithExistingTime(
            utcDateMillis = futureUtc,
            existingMillis = existingMillis,
            nowMillis = nowMillis,
            zoneId = zone
        )
        val mergedFutureZdt = Instant.ofEpochMilli(mergedFuture).atZone(zone)
        assertEquals(20, mergedFutureZdt.dayOfMonth)
        assertEquals(9, mergedFutureZdt.hour)
        assertEquals(15, mergedFutureZdt.minute)
    }

    @Test
    fun `applyTimeOfDay updates hours and minutes cleanly`() {
        val zone = ZoneId.of("UTC")
        val base = ZonedDateTime.of(2026, 11, 12, 10, 0, 0, 0, ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val updated = UnlockDateTimeHelper.applyTimeOfDay(base, hour = 18, minute = 45, zoneId = zone)
        val zdt = Instant.ofEpochMilli(updated).atZone(zone)
        assertEquals(12, zdt.dayOfMonth)
        assertEquals(18, zdt.hour)
        assertEquals(45, zdt.minute)
        assertEquals(0, zdt.second)
    }

    @Test
    fun `ApiConfig rejects cleartext in release and accepts it only when allowCleartext is true`() {
        assertTrue(ApiConfig.isValidServerUrl("https://api.timecapsule.example.com/", allowCleartext = false))
        assertFalse(ApiConfig.isValidServerUrl("http://10.0.2.2:8080", allowCleartext = false))
        assertFalse(ApiConfig.isValidServerUrl("http://api.example.com", allowCleartext = false))
        assertFalse(ApiConfig.isValidServerUrl("", allowCleartext = false))
        assertFalse(ApiConfig.isValidServerUrl("ftp://api.example.com", allowCleartext = true))

        assertTrue(ApiConfig.isValidServerUrl("http://10.0.2.2:8080", allowCleartext = true))
        assertEquals("https://api.example.com", ApiConfig.normalizeUrl("  HTTPS://api.example.com/  "))
    }

    @Test
    fun `ApiConfig resolveInitialUrl ignores persisted override in release and uses it in debug`() {
        val releaseResolved = ApiConfig.resolveInitialUrl(
            buildConfigUrl = "https://prod.timecapsule.example.com",
            persistedUrl = "http://10.0.2.2:8080",
            isDebug = false
        )
        assertEquals("https://prod.timecapsule.example.com", releaseResolved)

        val debugResolved = ApiConfig.resolveInitialUrl(
            buildConfigUrl = "http://10.0.2.2:8080",
            persistedUrl = "http://192.168.1.42:8080/",
            isDebug = true
        )
        assertEquals("http://192.168.1.42:8080", debugResolved)
    }

    @Test
    fun `GeoHelper haversineMeters calculates distance accurately`() {
        assertEquals(0.0, GeoHelper.haversineMeters(40.2825, 69.6221, 40.2825, 69.6221), 1e-6)
        val oneDeg = GeoHelper.haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue(abs(oneDeg - 111_194.9266) < 0.5)
    }

    @Test
    fun `HandleValidator enforces format, lowercase normalization, and reserved names`() {
        assertEquals("alice_99", HandleValidator.sanitizeInput("  @Alice_99 "))
        assertEquals(HandleLocalValidation.VALID, HandleValidator.validateLocal("@Alice_99"))
        assertEquals(HandleLocalValidation.INVALID_FORMAT, HandleValidator.validateLocal("abc"))
        assertEquals(HandleLocalValidation.INVALID_FORMAT, HandleValidator.validateLocal("a".repeat(25)))
        assertEquals(HandleLocalValidation.INVALID_FORMAT, HandleValidator.validateLocal("alice-bob"))
        assertEquals(HandleLocalValidation.RESERVED, HandleValidator.validateLocal("admin"))
        assertEquals(HandleLocalValidation.RESERVED, HandleValidator.validateLocal("@Support"))
        assertEquals(HandleLocalValidation.RESERVED, HandleValidator.validateLocal("timecapsule"))
    }

    @Test
    fun `CapsuleUnlockScheduler plans notifications only for future locked non-archived capsules`() {
        val now = 1_700_000_000_000L
        val lockedInbox = CapsuleDto(
            id = "cap_locked_inbox",
            title = "Inbox future",
            latitude = 0.0,
            longitude = 0.0,
            radiusMeters = 50.0,
            isGroupCapsule = false,
            isFullyUnlocked = false,
            stages = listOf(CapsuleStageDto("s1", 1, now + 120_000L, "[ЗАШИФРОВАНО]", false)),
            participants = emptyList(),
            status = "SEALED",
            creatorHandle = "alice",
            isInbox = true
        )
        val openedCap = lockedInbox.copy(id = "cap_opened", status = "OPENED")
        val archivedCap = lockedInbox.copy(id = "cap_arch", archived = true, status = "ARCHIVED")
        val pastUnlocked = lockedInbox.copy(
            id = "cap_ready",
            status = "READY",
            isFullyUnlocked = true,
            stages = listOf(CapsuleStageDto("s2", 1, now - 10_000L, "Open", true))
        )

        val disabledPlans = CapsuleUnlockScheduler.computePlans(listOf(lockedInbox), notificationsEnabled = false, nowMillis = now)
        assertTrue(disabledPlans.isEmpty())

        val plans = CapsuleUnlockScheduler.computePlans(
            capsules = listOf(lockedInbox, openedCap, archivedCap, pastUnlocked),
            notificationsEnabled = true,
            nowMillis = now
        )
        assertEquals(1, plans.size)
        assertEquals("cap_locked_inbox", plans.first().capsuleId)
        assertEquals(120_000L, plans.first().delayMillis)
        assertEquals("alice", plans.first().creatorHandle)
        assertTrue(plans.first().isInbox)
    }

    @Test
    fun `ColdStartRetry retries up to 3 times with increasing backoff and skips non-retryable errors`() = runBlocking {
        val recordedDelays = mutableListOf<Long>()
        val retryAttempts = mutableListOf<Int>()
        var calls = 0

        val result = ColdStartRetry.execute(
            delayFn = { ms -> recordedDelays.add(ms) },
            onRetry = { attempt, _ -> retryAttempts.add(attempt) }
        ) {
            calls += 1
            if (calls <= 3) {
                if (calls == 1) throw ConnectException("Connection refused")
                throw SocketTimeoutException("Cold start timeout")
            }
            "WOKEN_UP"
        }

        assertEquals("WOKEN_UP", result)
        assertEquals(4, calls)
        assertEquals(listOf(1, 2, 3), retryAttempts)
        assertEquals(listOf(3_000L, 6_000L, 12_000L), recordedDelays)
        assertTrue(recordedDelays[0] < recordedDelays[1] && recordedDelays[1] < recordedDelays[2])

        // Exhausting all 3 retries rethrows cleanly
        var exhaustCalls = 0
        var thrown: Throwable? = null
        try {
            ColdStartRetry.execute<String>(delayFn = {}) {
                exhaustCalls += 1
                throw SocketTimeoutException("Still sleeping")
            }
        } catch (t: Throwable) {
            thrown = t
        }
        assertEquals(4, exhaustCalls)
        assertTrue(thrown is SocketTimeoutException)

        // Non-retryable 401/IllegalStateException fails on first attempt without delay
        var nonRetryCalls = 0
        val nonRetryDelays = mutableListOf<Long>()
        try {
            ColdStartRetry.execute<String>(delayFn = { nonRetryDelays.add(it) }) {
                nonRetryCalls += 1
                throw SessionExpiredException("HTTP 401")
            }
        } catch (_: SessionExpiredException) {
        }
        assertEquals(1, nonRetryCalls)
        assertTrue(nonRetryDelays.isEmpty())
    }

    @Test
    fun `AppUpdatePolicy enforces https url, verifies SHA-256 deleting mismatched APK, and marks mandatory updates`() {
        assertTrue(AppUpdatePolicy.isHttpsUrlSafe("https://github.com/org/repo/releases/download/v2/app.apk"))
        assertFalse(AppUpdatePolicy.isHttpsUrlSafe("http://github.com/org/repo/app.apk"))
        assertFalse(AppUpdatePolicy.isHttpsUrlSafe("file:///sdcard/app.apk"))
        assertFalse(AppUpdatePolicy.isHttpsUrlSafe(""))

        val tmpFile = File.createTempFile("timecapsule_apk_test", ".apk")
        try {
            tmpFile.writeBytes("fake-apk-payload-v2".toByteArray())
            val validSha = AppUpdatePolicy.computeSha256Hex(tmpFile)
            assertEquals(64, validSha.length)
            assertTrue(AppUpdatePolicy.isValidSha256Hex(validSha))

            // Matching hash preserves file
            assertTrue(AppUpdatePolicy.verifyApkSha256AndCleanup(tmpFile, validSha.uppercase()))
            assertTrue(tmpFile.exists())

            // Mismatched hash MUST delete file and return false
            val wrongSha = "0".repeat(64)
            assertFalse(AppUpdatePolicy.verifyApkSha256AndCleanup(tmpFile, wrongSha))
            assertFalse(tmpFile.exists())
        } finally {
            tmpFile.delete()
        }

        val optionalInfo = AppVersionDto(
            versionCode = 2,
            versionName = "1.1.0",
            apkUrl = "https://github.com/org/repo/releases/latest/download/timecapsule-release.apk",
            apkSha256 = "c".repeat(64),
            changelog = "Bugfixes",
            minSupportedVersionCode = 1
        )
        val optEval = AppUpdatePolicy.evaluate(optionalInfo, currentVersionCode = 1)
        assertEquals(UpdateRequirement.OPTIONAL, optEval.requirement)
        assertFalse(optEval.isMandatory)
        assertTrue(optEval.hasDownloadableApk)

        val mandatoryInfo = optionalInfo.copy(versionCode = 3, minSupportedVersionCode = 2)
        val mandEval = AppUpdatePolicy.evaluate(mandatoryInfo, currentVersionCode = 1)
        assertEquals(UpdateRequirement.MANDATORY, mandEval.requirement)
        assertTrue(mandEval.isMandatory)
        assertTrue(mandEval.hasDownloadableApk)

        val upToDateEval = AppUpdatePolicy.evaluate(optionalInfo.copy(versionCode = 1), currentVersionCode = 1)
        assertEquals(UpdateRequirement.UP_TO_DATE, upToDateEval.requirement)
        assertFalse(upToDateEval.isMandatory)
    }

    @Test
    fun `UserPreferences migrates legacy SHA-256 PIN to PBKDF2WithHmacSHA256 on first valid input`() {
        val legacySalt = "0123456789abcdef0123456789abcdef"
        val pin = "4829"
        val legacyHash = UserPreferences.hashPinLegacySha256(legacySalt, pin)
        assertFalse(legacyHash.startsWith(UserPreferences.PBKDF2_PREFIX))

        // Wrong PIN fails without migration
        val wrongOutcome = UserPreferences.verifyAndMigratePinRecord("9999", legacySalt, legacyHash)
        assertFalse(wrongOutcome.matched)
        assertNull(wrongOutcome.migratedHash)

        // Correct PIN matches and produces migrated PBKDF2 hash + new 16-byte salt
        val migratedOutcome = UserPreferences.verifyAndMigratePinRecord(pin, legacySalt, legacyHash)
        assertTrue(migratedOutcome.matched)
        val newSalt = assertNotNull(migratedOutcome.migratedSaltHex)
        val newHash = assertNotNull(migratedOutcome.migratedHash)
        assertEquals(32, newSalt.length)
        assertTrue(newHash.startsWith(UserPreferences.PBKDF2_PREFIX))

        // Subsequent verification with the migrated PBKDF2 hash succeeds without re-migrating
        val pbkdf2Outcome = UserPreferences.verifyAndMigratePinRecord(pin, newSalt, newHash)
        assertTrue(pbkdf2Outcome.matched)
        assertNull(pbkdf2Outcome.migratedHash)
        assertFalse(UserPreferences.verifyAndMigratePinRecord("0000", newSalt, newHash).matched)
    }

    @Test
    fun `UserPreferences enforces 30s cooldown after 5 failed PIN attempts`() {
        val startMillis = 1_700_000_000_000L
        var state = PinAttemptState()

        // First 4 failed attempts decrement attemptsLeft from 4 down to 1
        for (attempt in 1..4) {
            val (nextState, result) = UserPreferences.evaluatePinAttempt(state, isPinCorrect = false, nowMillis = startMillis)
            state = nextState
            assertTrue(result is PinCheckResult.Invalid)
            assertEquals(5 - attempt, result.attemptsLeft)
            assertEquals(attempt, state.failedAttempts)
        }

        // 5th failed attempt triggers 30s cooldown
        val (lockedState, fifthResult) = UserPreferences.evaluatePinAttempt(state, isPinCorrect = false, nowMillis = startMillis)
        state = lockedState
        assertTrue(fifthResult is PinCheckResult.LockedOut)
        assertEquals(30, fifthResult.remainingSeconds)
        assertEquals(startMillis + 30_000L, fifthResult.lockoutUntilMillis)

        // Even correct PIN is rejected while cooldown is active (e.g. 10s later)
        val (_, duringCooldown) = UserPreferences.evaluatePinAttempt(
            state,
            isPinCorrect = true,
            nowMillis = startMillis + 10_000L
        )
        assertTrue(duringCooldown is PinCheckResult.LockedOut)
        assertEquals(20, duringCooldown.remainingSeconds)

        // After 30s cooldown expires, valid PIN succeeds and resets state
        val (resetState, afterCooldown) = UserPreferences.evaluatePinAttempt(
            state,
            isPinCorrect = true,
            nowMillis = startMillis + 30_001L
        )
        assertTrue(afterCooldown is PinCheckResult.Success)
        assertEquals(0, resetState.failedAttempts)
        assertEquals(0L, resetState.lockoutUntilMillis)
    }

    @Test
    fun `CapsuleUnlockScheduler defers quiet hours 23_00-08_00 to 08_00 and calculates 1-year anniversary`() {
        val zone = ZoneId.of("UTC")

        // 23:30 on Nov 14 -> deferred to 08:00 on Nov 15
        val lateNight = ZonedDateTime.of(2026, 11, 14, 23, 30, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        val deferredFrom23 = CapsuleUnlockScheduler.applyQuietHours(lateNight, quietHoursEnabled = true, zoneId = zone)
        val zdt23 = Instant.ofEpochMilli(deferredFrom23).atZone(zone)
        assertEquals(15, zdt23.dayOfMonth)
        assertEquals(8, zdt23.hour)
        assertEquals(0, zdt23.minute)

        // 03:15 on Nov 15 -> deferred to 08:00 on Nov 15
        val earlyMorning = ZonedDateTime.of(2026, 11, 15, 3, 15, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        val deferredFrom03 = CapsuleUnlockScheduler.applyQuietHours(earlyMorning, quietHoursEnabled = true, zoneId = zone)
        val zdt03 = Instant.ofEpochMilli(deferredFrom03).atZone(zone)
        assertEquals(15, zdt03.dayOfMonth)
        assertEquals(8, zdt03.hour)
        assertEquals(0, zdt03.minute)

        // Daytime 14:45 -> unchanged
        val daytime = ZonedDateTime.of(2026, 11, 15, 14, 45, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(daytime, CapsuleUnlockScheduler.applyQuietHours(daytime, quietHoursEnabled = true, zoneId = zone))

        // Quiet hours disabled -> 23:30 unchanged
        assertEquals(lateNight, CapsuleUnlockScheduler.applyQuietHours(lateNight, quietHoursEnabled = false, zoneId = zone))

        // Anniversary calculation: 1 year after 2026-11-14 23:30 with quiet hours enabled -> 2027-11-15 08:00
        val anniversaryMillis = CapsuleUnlockScheduler.computeAnniversaryTriggerMillis(
            openedAtMillis = lateNight,
            quietHoursEnabled = true,
            zoneId = zone
        )
        val annivZdt = Instant.ofEpochMilli(anniversaryMillis).atZone(zone)
        assertEquals(2027, annivZdt.year)
        assertEquals(11, annivZdt.monthValue)
        assertEquals(15, annivZdt.dayOfMonth)
        assertEquals(8, annivZdt.hour)
    }

    @Test
    fun `ApiConfig encrypts session_token with AES-GCM and transparently migrates plaintext token`() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val secretKey = keyGen.generateKey()

        val rawPlainToken = "legacy_plain_bearer_token_12345"
        val (migratedToken, needsMigration) = assertNotNull(
            ApiConfig.decryptOrMigrateTokenWithKey(rawPlainToken, secretKey)
        )
        assertEquals(rawPlainToken, migratedToken)
        assertTrue(needsMigration)

        val encrypted = ApiConfig.encryptTokenWithKey(rawPlainToken, secretKey)
        assertTrue(encrypted.startsWith(ApiConfig.ENCRYPTED_TOKEN_PREFIX))
        assertFalse(encrypted.contains(rawPlainToken))

        val (decryptedToken, needsMigrationAfter) = assertNotNull(
            ApiConfig.decryptOrMigrateTokenWithKey(encrypted, secretKey)
        )
        assertEquals(rawPlainToken, decryptedToken)
        assertFalse(needsMigrationAfter)
    }

    @Test
    fun `LocationProvider rejects lastKnownLocation older than 2 minutes and cache cleanups remove stale files`() {
        val now = 1_700_000_000_000L
        assertTrue(LocationProvider.isLocationFresh(now - 60_000L, now))
        assertTrue(LocationProvider.isLocationFresh(now - 120_000L, now))
        assertFalse(LocationProvider.isLocationFresh(now - 120_001L, now))
        assertFalse(LocationProvider.isLocationFresh(0L, now))

        val tempDir = Files.createTempDirectory("tc_cache_test").toFile()
        try {
            val staleFile = File(tempDir, "old_photo.jpg").apply {
                writeText("old")
                setLastModified(now - MediaHelper.MAX_TEMP_FILE_AGE_MS - 5_000L)
            }
            val freshFile = File(tempDir, "new_photo.jpg").apply {
                writeText("new")
                setLastModified(now - 60_000L)
            }
            val removed = MediaHelper.cleanupStaleFilesInDir(tempDir, nowMillis = now)
            assertEquals(1, removed)
            assertFalse(staleFile.exists())
            assertTrue(freshFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }

        val apkDir = Files.createTempDirectory("tc_apk_test").toFile()
        try {
            val installedApk = File(apkDir, "timecapsule-update-v3.apk").apply { writeText("apk3") }
            val futureApk = File(apkDir, "timecapsule-update-v4.apk").apply { writeText("apk4") }
            val removedApks = AppUpdateManager.cleanupInstalledApksInDir(apkDir, currentVersionCode = 3)
            assertEquals(1, removedApks)
            assertFalse(installedApk.exists())
            assertTrue(futureApk.exists())
        } finally {
            apkDir.deleteRecursively()
        }
    }
}

