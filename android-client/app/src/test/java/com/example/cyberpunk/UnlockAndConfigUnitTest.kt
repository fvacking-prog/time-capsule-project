package com.example.cyberpunk

import com.example.cyberpunk.data.ApiConfig
import com.example.cyberpunk.data.CapsuleUnlockScheduler
import com.example.cyberpunk.data.GeoHelper
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
}

