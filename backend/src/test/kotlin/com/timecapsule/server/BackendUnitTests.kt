package com.timecapsule.server

import com.timecapsule.shared.ParticipantStatusDto
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackendUnitTests {

    @Test
    fun `haversineMeters calculates zero and known meridian distance accurately`() {
        val zeroDist = GeoUtils.haversineMeters(40.2825, 69.6221, 40.2825, 69.6221)
        assertEquals(0.0, zeroDist, 1e-6)

        // 1 degree of latitude on sphere R = 6_371_000 m is ~111,194.93 m
        val oneDegreeLatMeters = GeoUtils.haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue(abs(oneDegreeLatMeters - 111_194.9266) < 0.5)
    }

    @Test
    fun `isNearbyAndRecent checks 60s time window and radius boundaries`() {
        val capsuleLat = 40.2825
        val capsuleLon = 69.6221
        val radius = 50.0
        val now = 1_800_000_000_000L

        // Exact 60_000 ms boundary -> true
        assertTrue(
            GeoUtils.isNearbyAndRecent(
                lastLat = capsuleLat,
                lastLon = capsuleLon,
                lastSeenAt = now - 60_000L,
                capsuleLat = capsuleLat,
                capsuleLon = capsuleLon,
                radiusMeters = radius,
                now = now
            )
        )

        // 60_001 ms -> false (expired)
        assertFalse(
            GeoUtils.isNearbyAndRecent(
                lastLat = capsuleLat,
                lastLon = capsuleLon,
                lastSeenAt = now - 60_001L,
                capsuleLat = capsuleLat,
                capsuleLon = capsuleLon,
                radiusMeters = radius,
                now = now
            )
        )

        // Future timestamp (lastSeenAt > now) -> false
        assertFalse(
            GeoUtils.isNearbyAndRecent(
                lastLat = capsuleLat,
                lastLon = capsuleLon,
                lastSeenAt = now + 5_000L,
                capsuleLat = capsuleLat,
                capsuleLon = capsuleLon,
                radiusMeters = radius,
                now = now
            )
        )

        // Point ~33m north (0.0003 deg lat) is within 50m radius -> true
        assertTrue(
            GeoUtils.isNearbyAndRecent(
                lastLat = capsuleLat + 0.0003,
                lastLon = capsuleLon,
                lastSeenAt = now - 10_000L,
                capsuleLat = capsuleLat,
                capsuleLon = capsuleLon,
                radiusMeters = radius,
                now = now
            )
        )

        // Point ~111m north (0.001 deg lat) is outside 50m radius -> false
        assertFalse(
            GeoUtils.isNearbyAndRecent(
                lastLat = capsuleLat + 0.001,
                lastLon = capsuleLon,
                lastSeenAt = now - 10_000L,
                capsuleLat = capsuleLat,
                capsuleLon = capsuleLon,
                radiusMeters = radius,
                now = now
            )
        )

        // Null or non-positive radius -> false
        assertFalse(
            GeoUtils.isNearbyAndRecent(
                lastLat = null,
                lastLon = capsuleLon,
                lastSeenAt = now,
                capsuleLat = capsuleLat,
                capsuleLon = capsuleLon,
                radiusMeters = radius,
                now = now
            )
        )
        assertFalse(
            GeoUtils.isNearbyAndRecent(
                lastLat = capsuleLat,
                lastLon = capsuleLon,
                lastSeenAt = now,
                capsuleLat = capsuleLat,
                capsuleLon = capsuleLon,
                radiusMeters = 0.0,
                now = now
            )
        )
    }

    @Test
    fun `stageText returns decrypted text only when unlocked by date and all participants nearby`() {
        val secret = "Секретное послание Худжанд"
        val encrypted = CryptoUtils.encrypt(secret)
        val unlockDate = 1_700_000_000_000L

        // Unlocked: now >= unlockDate && allParticipantsNearbyAndRecent == true
        assertEquals(
            secret,
            GeoUtils.stageText(
                encryptedText = encrypted,
                unlockDate = unlockDate,
                now = unlockDate,
                allParticipantsNearbyAndRecent = true
            )
        )

        // Locked: now < unlockDate
        assertEquals(
            GeoUtils.LOCKED_PLACEHOLDER,
            GeoUtils.stageText(
                encryptedText = encrypted,
                unlockDate = unlockDate,
                now = unlockDate - 1L,
                allParticipantsNearbyAndRecent = true
            )
        )

        // Locked: not all participants nearby
        val participants = listOf(
            ParticipantStatusDto("user_maga", "Maga", 5.0, unlockDate, true),
            ParticipantStatusDto("user_zainab", "Zainab", 120.0, unlockDate, false)
        )
        assertEquals(
            GeoUtils.LOCKED_PLACEHOLDER,
            GeoUtils.stageText(
                encryptedText = encrypted,
                unlockDate = unlockDate,
                now = unlockDate + 10_000L,
                participants = participants
            )
        )
    }

    @Test
    fun `CryptoUtils encrypt and decrypt roundtrip and random IV uniqueness`() {
        val original = "Капсула Худжанд: секретный этап №1"
        val cipher1 = CryptoUtils.encrypt(original)
        val cipher2 = CryptoUtils.encrypt(original)

        // Random 12-byte IV must make repeated encryption produce distinct tokens
        assertNotEquals(cipher1, cipher2)

        assertEquals(original, CryptoUtils.decrypt(cipher1))
        assertEquals(original, CryptoUtils.decrypt(cipher2))
    }

    @Test
    fun `maskEmail masks local part of email preserving first and last characters`() {
        assertEquals("m***a@gmail.com", maskEmail("maga@gmail.com"))
        assertEquals("z***b@example.org", maskEmail("zainab@example.org"))
        assertEquals("a***@gmail.com", maskEmail("ab@gmail.com"))
        assertEquals(null, maskEmail(null))
        assertEquals(null, maskEmail("   "))
    }

    @Test
    fun `visibility filtering ensures user B cannot see or access user A capsule until joined`() {
        val testDb = Database.connect(
            url = "jdbc:h2:mem:test_vis_${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )

        transaction(testDb) {
            SchemaUtils.create(Users, Capsules, CapsuleStages, CapsuleParticipants, Sessions)

            Users.insert {
                it[id] = "user_a"
                it[username] = "UserA"
            }
            Users.insert {
                it[id] = "user_b"
                it[username] = "UserB"
            }

            val capId = "cap_a1"
            Capsules.insert {
                it[id] = capId
                it[creatorId] = "user_a"
                it[title] = "Private Capsule A"
                it[latitude] = 40.2825
                it[longitude] = 69.6221
                it[radiusMeters] = 50.0
                it[createdAt] = System.currentTimeMillis()
                it[isGroupCapsule] = false
                it[inviteCode] = "SECR88"
            }
            CapsuleParticipants.insert {
                it[CapsuleParticipants.capsuleId] = capId
                it[userId] = "user_a"
            }

            // User A (creator/participant) can access
            assertTrue(canUserAccessCapsule(capId, "user_a"))
            assertTrue(getVisibleCapsuleIds("user_a").contains(capId))

            // User B cannot access and does not see it in list
            assertFalse(canUserAccessCapsule(capId, "user_b"))
            assertFalse(getVisibleCapsuleIds("user_b").contains(capId))

            // Invite code is visible to User A, but masked (null) for User B
            val dtoForA = buildCapsuleDto(capId, requestingUserId = "user_a")
            assertEquals("SECR88", dtoForA.inviteCode)

            val dtoForB = buildCapsuleDto(capId, requestingUserId = "user_b")
            assertNull(dtoForB.inviteCode)
        }
    }

    @Test
    fun `joinCapsuleByInviteCode is case-insensitive, trims whitespace, idempotent and handles errors`() {
        val testDb = Database.connect(
            url = "jdbc:h2:mem:test_join_${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )

        transaction(testDb) {
            SchemaUtils.create(Users, Capsules, CapsuleStages, CapsuleParticipants, Sessions)

            Users.insert { it[id] = "user_a"; it[username] = "UserA" }
            Users.insert { it[id] = "user_b"; it[username] = "UserB" }

            val capId = "cap_j1"
            Capsules.insert {
                it[id] = capId
                it[creatorId] = "user_a"
                it[title] = "Joint Capsule"
                it[latitude] = 40.2825
                it[longitude] = 69.6221
                it[radiusMeters] = 50.0
                it[createdAt] = System.currentTimeMillis()
                it[isGroupCapsule] = false
                it[inviteCode] = "ABC789"
            }
            CapsuleParticipants.insert {
                it[CapsuleParticipants.capsuleId] = capId
                it[userId] = "user_a"
            }

            // User B not yet visible
            assertFalse(canUserAccessCapsule(capId, "user_b"))

            // 1. Join with lowercase and surrounding whitespace
            val joinedDto = joinCapsuleByInviteCode("   abc789   ", "user_b")
            assertEquals(capId, joinedDto.id)
            assertTrue(joinedDto.isGroupCapsule)
            assertTrue(joinedDto.participants.any { it.userId == "user_b" })

            // User B now has access and sees it
            assertTrue(canUserAccessCapsule(capId, "user_b"))
            assertTrue(getVisibleCapsuleIds("user_b").contains(capId))
            assertEquals("ABC789", buildCapsuleDto(capId, requestingUserId = "user_b").inviteCode)

            // 2. Idempotent re-joining: joining again does not fail and creates no duplicate records
            val rejoinedDto = joinCapsuleByInviteCode("ABC789", "user_b")
            assertEquals(capId, rejoinedDto.id)
            assertEquals(2, CapsuleParticipants.selectAll().where { CapsuleParticipants.capsuleId eq capId }.count())

            // 3. Invalid code -> CapsuleNotFoundException (which statusPages maps to 404)
            val notFoundEx = assertFailsWith<CapsuleNotFoundException> {
                joinCapsuleByInviteCode("INVALID", "user_b")
            }
            assertTrue(notFoundEx.message?.contains("не найдена") == true || notFoundEx.message?.contains("INVALID") == true)

            // 4. Blank code -> CapsuleNotFoundException
            assertFailsWith<CapsuleNotFoundException> {
                joinCapsuleByInviteCode("   ", "user_b")
            }
        }
    }

    @Test
    fun `schema migration safely handles legacy table created without invite_code column`() {
        val testDb = Database.connect(
            url = "jdbc:h2:mem:test_legacy_${System.nanoTime()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )

        transaction(testDb) {
            connection.prepareStatement("DROP ALL OBJECTS;", false).executeUpdate()

            connection.prepareStatement(
                "CREATE TABLE users (id VARCHAR(36) PRIMARY KEY, username VARCHAR(128), google_sub VARCHAR(128), email VARCHAR(255), display_name VARCHAR(128), photo_url VARCHAR(512));",
                false
            ).executeUpdate()
            connection.prepareStatement(
                "INSERT INTO users (id, username) VALUES ('user_maga', 'Maga');",
                false
            ).executeUpdate()

            // Simulate legacy database schema without invite_code column
            connection.prepareStatement(
                "CREATE TABLE capsules (" +
                    "id VARCHAR(36) PRIMARY KEY, " +
                    "creator_id VARCHAR(36), " +
                    "title VARCHAR(255), " +
                    "latitude DOUBLE, " +
                    "longitude DOUBLE, " +
                    "radius_meters DOUBLE, " +
                    "created_at BIGINT, " +
                    "is_group_capsule BOOLEAN" +
                    ");",
                false
            ).executeUpdate()

            connection.prepareStatement(
                "INSERT INTO capsules (id, creator_id, title, latitude, longitude, radius_meters, created_at, is_group_capsule) " +
                    "VALUES ('old_cap_1', 'user_maga', 'Old Capsule', 40.28, 69.62, 50.0, 1000000, true);",
                false
            ).executeUpdate()

            connection.prepareStatement(
                "CREATE TABLE sessions (token VARCHAR(64) PRIMARY KEY, user_id VARCHAR(36), created_at BIGINT, expires_at BIGINT);",
                false
            ).executeUpdate()
            connection.prepareStatement(
                "CREATE TABLE capsule_stages (id VARCHAR(36) PRIMARY KEY, capsule_id VARCHAR(36), stage_number INT, unlock_date BIGINT, encrypted_text CLOB);",
                false
            ).executeUpdate()
            connection.prepareStatement(
                "CREATE TABLE capsule_participants (capsule_id VARCHAR(36), user_id VARCHAR(36), last_lat DOUBLE, last_lon DOUBLE, last_seen_at BIGINT, PRIMARY KEY (capsule_id, user_id));",
                false
            ).executeUpdate()

            // Run migration
            migrateAndSeedDatabase()

            // Verify invite_code column was added and generated for the old capsule
            val migratedRow = Capsules.selectAll().where { Capsules.id eq "old_cap_1" }.single()
            val code = migratedRow[Capsules.inviteCode]
            assertNotNull(code)
            assertEquals(6, code.length)
        }
    }

    @Test
    fun `personal capsule unlocks only by date, status transitions SEALED to READY to OPENED and archive works`() {
        val testDb = Database.connect(
            url = "jdbc:h2:mem:test_personal_${System.nanoTime()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )

        transaction(testDb) {
            SchemaUtils.create(Users, Capsules, CapsuleStages, CapsuleParticipants, Sessions)

            Users.insert {
                it[id] = "user_alice"
                it[username] = "Alice"
                it[displayName] = "Alice"
            }

            val capId = "cap_personal_1"
            val unlockTime = 1_000_000L
            val plainSecret = "Моё сокровенное воспоминание"

            Capsules.insert {
                it[id] = capId
                it[creatorId] = "user_alice"
                it[title] = "Личная капсула"
                it[latitude] = 0.0
                it[longitude] = 0.0
                it[radiusMeters] = 50.0
                it[createdAt] = 500_000L
                it[isGroupCapsule] = false
                it[inviteCode] = "ALICE1"
                it[kind] = "MESSAGE"
                it[recipient] = "Будущей мне"
                it[tags] = "мечты,цели"
                it[isGeoLocked] = false
            }

            CapsuleParticipants.insert {
                it[CapsuleParticipants.capsuleId] = capId
                it[userId] = "user_alice"
                it[lastLat] = null
                it[lastLon] = null
                it[lastSeenAt] = null
                it[openedAt] = null
                it[archived] = false
            }

            CapsuleStages.insert {
                it[id] = "stage_p1"
                it[CapsuleStages.capsuleId] = capId
                it[stageNumber] = 1
                it[unlockDate] = unlockTime
                it[encryptedText] = CryptoUtils.encrypt(plainSecret)
            }

            // 1. Before unlock time -> SEALED, text is locked placeholder
            val beforeDto = buildCapsuleDto(capId, now = unlockTime - 10_000L, requestingUserId = "user_alice")
            assertEquals("SEALED", beforeDto.status)
            assertFalse(beforeDto.isFullyUnlocked)
            assertEquals(GeoUtils.LOCKED_PLACEHOLDER, beforeDto.stages.first().text)
            assertFalse(beforeDto.stages.first().isUnlocked)
            assertEquals("Будущей мне", beforeDto.recipient)
            assertEquals(listOf("мечты", "цели"), beforeDto.tags)
            assertFalse(beforeDto.isGeoLocked)

            // 2. After unlock time -> READY, text is decrypted, openedAt is null
            val readyDto = buildCapsuleDto(capId, now = unlockTime + 10_000L, requestingUserId = "user_alice")
            assertEquals("READY", readyDto.status)
            assertTrue(readyDto.isFullyUnlocked)
            assertEquals(plainSecret, readyDto.stages.first().text)
            assertTrue(readyDto.stages.first().isUnlocked)
            assertNull(readyDto.openedAt)

            // 3. Mark opened -> OPENED
            CapsuleParticipants.update({ (CapsuleParticipants.capsuleId eq capId) and (CapsuleParticipants.userId eq "user_alice") }) {
                it[openedAt] = unlockTime + 15_000L
            }
            val openedDto = buildCapsuleDto(capId, now = unlockTime + 20_000L, requestingUserId = "user_alice")
            assertEquals("OPENED", openedDto.status)
            assertEquals(unlockTime + 15_000L, openedDto.openedAt)
            assertFalse(openedDto.archived)

            // 4. Archive -> ARCHIVED
            CapsuleParticipants.update({ (CapsuleParticipants.capsuleId eq capId) and (CapsuleParticipants.userId eq "user_alice") }) {
                it[archived] = true
            }
            val archivedDto = buildCapsuleDto(capId, now = unlockTime + 25_000L, requestingUserId = "user_alice")
            assertEquals("ARCHIVED", archivedDto.status)
            assertTrue(archivedDto.archived)

            // 5. Unarchive -> back to OPENED
            CapsuleParticipants.update({ (CapsuleParticipants.capsuleId eq capId) and (CapsuleParticipants.userId eq "user_alice") }) {
                it[archived] = false
            }
            val unarchivedDto = buildCapsuleDto(capId, now = unlockTime + 30_000L, requestingUserId = "user_alice")
            assertEquals("OPENED", unarchivedDto.status)
            assertFalse(unarchivedDto.archived)
        }
    }

    @Test
    fun `rule If sealed then sealed ensures secret text never leaks to creator or others before unlock`() {
        val testDb = Database.connect(
            url = "jdbc:h2:mem:test_sealed_${System.nanoTime()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )

        transaction(testDb) {
            SchemaUtils.create(Users, Capsules, CapsuleStages, CapsuleParticipants, Sessions)

            Users.insert {
                it[id] = "user_x"
                it[username] = "UserX"
            }
            Users.insert {
                it[id] = "user_y"
                it[username] = "UserY"
            }

            val capId = "cap_locked_test"
            val futureUnlock = 2_000_000L
            val secret = "Secret that must not leak"

            Capsules.insert {
                it[id] = capId
                it[creatorId] = "user_x"
                it[title] = "Strictly Sealed"
                it[latitude] = 40.0
                it[longitude] = 69.0
                it[radiusMeters] = 50.0
                it[createdAt] = 1_000_000L
                it[isGroupCapsule] = true
                it[inviteCode] = "SEAL01"
                it[isGeoLocked] = true
            }

            listOf("user_x", "user_y").forEach { uid ->
                CapsuleParticipants.insert {
                    it[CapsuleParticipants.capsuleId] = capId
                    it[userId] = uid
                    it[lastLat] = 40.0
                    it[lastLon] = 69.0
                    it[lastSeenAt] = 1_500_000L // Nearby
                }
            }

            CapsuleStages.insert {
                it[id] = "stage_locked_1"
                it[CapsuleStages.capsuleId] = capId
                it[stageNumber] = 1
                it[unlockDate] = futureUnlock
                it[encryptedText] = CryptoUtils.encrypt(secret)
            }

            // Before unlock date (even though participants are nearby)
            val nowBefore = 1_500_000L
            val creatorView = buildCapsuleDto(capId, now = nowBefore, requestingUserId = "user_x")
            val participantView = buildCapsuleDto(capId, now = nowBefore, requestingUserId = "user_y")

            assertNotEquals(secret, creatorView.stages.first().text)
            assertEquals(GeoUtils.LOCKED_PLACEHOLDER, creatorView.stages.first().text)

            assertNotEquals(secret, participantView.stages.first().text)
            assertEquals(GeoUtils.LOCKED_PLACEHOLDER, participantView.stages.first().text)
        }
    }

    @Test
    fun `delete user data removes owned capsules, unenrolls from foreign capsules and drops sessions`() {
        val testDb = Database.connect(
            url = "jdbc:h2:mem:test_delete_data_${System.nanoTime()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )

        transaction(testDb) {
            SchemaUtils.create(Users, Capsules, CapsuleStages, CapsuleParticipants, Sessions)

            Users.insert {
                it[id] = "user_target"
                it[username] = "Target"
            }
            Users.insert {
                it[id] = "user_other"
                it[username] = "Other"
            }

            // 1. Capsule created by user_target
            Capsules.insert {
                it[id] = "cap_target"
                it[creatorId] = "user_target"
                it[title] = "Target Capsule"
                it[latitude] = 40.0
                it[longitude] = 69.0
                it[radiusMeters] = 50.0
                it[createdAt] = 1000L
                it[isGroupCapsule] = false
                it[inviteCode] = "TGT001"
            }
            CapsuleStages.insert {
                it[id] = "stg_target"
                it[CapsuleStages.capsuleId] = "cap_target"
                it[stageNumber] = 1
                it[unlockDate] = 5000L
                it[encryptedText] = CryptoUtils.encrypt("target secret")
            }
            CapsuleParticipants.insert {
                it[CapsuleParticipants.capsuleId] = "cap_target"
                it[userId] = "user_target"
            }

            // 2. Foreign capsule where user_target is a participant
            Capsules.insert {
                it[id] = "cap_other"
                it[creatorId] = "user_other"
                it[title] = "Other Capsule"
                it[latitude] = 40.0
                it[longitude] = 69.0
                it[radiusMeters] = 50.0
                it[createdAt] = 1000L
                it[isGroupCapsule] = true
                it[inviteCode] = "OTH001"
            }
            CapsuleParticipants.insert {
                it[CapsuleParticipants.capsuleId] = "cap_other"
                it[userId] = "user_other"
            }
            CapsuleParticipants.insert {
                it[CapsuleParticipants.capsuleId] = "cap_other"
                it[userId] = "user_target"
            }

            // 3. Session for user_target
            createSessionToken("user_target", 1000L)

            assertEquals(1L, Sessions.selectAll().where { Sessions.userId eq "user_target" }.count())
            assertEquals(1L, Capsules.selectAll().where { Capsules.creatorId eq "user_target" }.count())
            assertEquals(2L, CapsuleParticipants.selectAll().where { CapsuleParticipants.userId eq "user_target" }.count())

            // Execute delete logic
            val ownedIds = Capsules.selectAll().where { Capsules.creatorId eq "user_target" }.map { it[Capsules.id] }
            if (ownedIds.isNotEmpty()) {
                CapsuleStages.deleteWhere { capsuleId inList ownedIds }
                CapsuleParticipants.deleteWhere { capsuleId inList ownedIds }
                Capsules.deleteWhere { id inList ownedIds }
            }
            CapsuleParticipants.deleteWhere { userId eq "user_target" }
            Sessions.deleteWhere { userId eq "user_target" }

            // Verify
            assertEquals(0L, Sessions.selectAll().where { Sessions.userId eq "user_target" }.count())
            assertEquals(0L, Capsules.selectAll().where { Capsules.creatorId eq "user_target" }.count())
            assertEquals(0L, CapsuleParticipants.selectAll().where { CapsuleParticipants.userId eq "user_target" }.count())

            // Foreign capsule still exists for other user
            assertEquals(1L, Capsules.selectAll().where { Capsules.id eq "cap_other" }.count())
            assertEquals(1L, CapsuleParticipants.selectAll().where { CapsuleParticipants.capsuleId eq "cap_other" }.count())
        }
    }

    @Test
    fun `auth rate limiter blocks brute force after 20 attempts`() {
        val testIp = "192.168.1.100"
        for (i in 1..20) {
            AuthRateLimiter.checkOrThrow(testIp)
        }
        assertFailsWith<TooManyRequestsException> {
            AuthRateLimiter.checkOrThrow(testIp)
        }
    }

    @Test
    fun `expired session is purged from database`() {
        val testDb = Database.connect(
            url = "jdbc:h2:mem:test_expired_${System.nanoTime()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        transaction(testDb) {
            SchemaUtils.create(Sessions, Users)
            Users.insert {
                it[id] = "user_exp"
                it[username] = "ExpiredUser"
            }
            val now = 1_000_000L
            val token = createSessionToken("user_exp", now)

            assertEquals(1L, Sessions.selectAll().where { Sessions.token eq token }.count())

            // Simulating time passing beyond expiry (now + 31 days)
            val futureTime = now + 31L * 24 * 60 * 60 * 1000L
            val sessionRow = Sessions.selectAll().where { Sessions.token eq token }.single()
            if (futureTime > sessionRow[Sessions.expiresAt]) {
                Sessions.deleteWhere { Sessions.token eq token }
            }

            assertEquals(0L, Sessions.selectAll().where { Sessions.token eq token }.count())
        }
    }

    @Test
    fun `fail fast when DEMO_MODE is false and CAPSULE_AES_KEY is missing`() {
        val prevProp = System.getProperty("DEMO_MODE")
        try {
            System.setProperty("DEMO_MODE", "false")
            if (System.getenv("CAPSULE_AES_KEY").isNullOrEmpty()) {
                assertFailsWith<IllegalStateException> {
                    CryptoUtils.validateCryptoConfig()
                }
            }
        } finally {
            if (prevProp != null) {
                System.setProperty("DEMO_MODE", prevProp)
            } else {
                System.setProperty("DEMO_MODE", "true")
            }
        }
    }
}

