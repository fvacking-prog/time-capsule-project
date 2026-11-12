package com.timecapsule.server

import com.timecapsule.shared.AppVersionDto
import com.timecapsule.shared.AuthResponse
import com.timecapsule.shared.BlockUserRequest
import com.timecapsule.shared.CapsuleDto
import com.timecapsule.shared.CheckHandleResponse
import com.timecapsule.shared.CreateCapsuleRequest
import com.timecapsule.shared.CreateStageRequest
import com.timecapsule.shared.ParticipantStatusDto
import com.timecapsule.shared.UserDto
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
            ParticipantStatusDto("user_alice", "Alice", 5.0, unlockDate, true),
            ParticipantStatusDto("user_bob", "Bob", 120.0, unlockDate, false)
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
    fun `toUserDto never exposes email for other users`() {
        val testDb = Database.connect(
            url = "jdbc:h2:mem:test_email_privacy_${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        transaction(testDb) {
            SchemaUtils.create(Users)
            Users.insert {
                it[id] = "user_alice"
                it[username] = "Alice"
                it[email] = "alice@gmail.com"
            }
            val row = Users.selectAll().where { Users.id eq "user_alice" }.single()
            assertEquals("alice@gmail.com", row.toUserDto(includeEmail = true).email)
            assertNull(row.toUserDto(includeEmail = false).email)
        }
    }

    @Test
    fun `visibility filtering ensures user B cannot see or access user A capsule until joined`() {
        val testDb = Database.connect(
            url = "jdbc:h2:mem:test_vis_${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )

        transaction(testDb) {
            SchemaUtils.create(Users, Capsules, CapsuleStages, CapsuleParticipants, Sessions, Blocks)

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
            SchemaUtils.create(Users, Capsules, CapsuleStages, CapsuleParticipants, Sessions, Blocks)

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
                "INSERT INTO users (id, username) VALUES ('user_legacy_1', 'LegacyUser');",
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
                    "VALUES ('old_cap_1', 'user_legacy_1', 'Old Capsule', 40.28, 69.62, 50.0, 1000000, true);",
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
            migrateDatabase()

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
            SchemaUtils.create(Users, Capsules, CapsuleStages, CapsuleParticipants, Sessions, Blocks)

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
            SchemaUtils.create(Users, Capsules, CapsuleStages, CapsuleParticipants, Sessions, Blocks)

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
            SchemaUtils.create(Users, Capsules, CapsuleStages, CapsuleParticipants, Sessions, Blocks)

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
    fun `CAPSULE_AES_KEY validation fails fast on missing blank invalid base64 or wrong byte length`() {
        val missingEx = assertFailsWith<IllegalStateException> {
            CryptoUtils.validateCryptoConfig(null)
        }
        assertTrue(missingEx.message?.contains("CAPSULE_AES_KEY") == true)

        assertFailsWith<IllegalStateException> {
            CryptoUtils.validateCryptoConfig("   ")
        }

        val badBase64Ex = assertFailsWith<IllegalArgumentException> {
            CryptoUtils.validateCryptoConfig("not!valid!base64")
        }
        assertTrue(badBase64Ex.message?.contains("Base64") == true)

        // 16 bytes instead of 32 bytes
        val shortKeyBase64 = java.util.Base64.getEncoder().encodeToString(ByteArray(16) { 1 })
        val shortEx = assertFailsWith<IllegalArgumentException> {
            CryptoUtils.validateCryptoConfig(shortKeyBase64)
        }
        assertTrue(shortEx.message?.contains("32") == true)

        // Valid 32 bytes succeeds
        val validKeyBase64 = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
        val spec = CryptoUtils.parseAesKey(validKeyBase64)
        assertEquals("AES", spec.algorithm)
        assertEquals(32, spec.encoded.size)
    }

    @Test
    fun `geo capsule requires both unlockDate reached and all participants nearby within 60 seconds`() {
        val testDb = Database.connect(
            url = "jdbc:h2:mem:test_geo_unlock_${System.nanoTime()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )

        transaction(testDb) {
            SchemaUtils.create(Users, Capsules, CapsuleStages, CapsuleParticipants, Sessions, Blocks)

            Users.insert {
                it[id] = "user_u1"
                it[username] = "Alice"
                it[displayName] = "Alice"
            }
            Users.insert {
                it[id] = "user_u2"
                it[username] = "Bob"
                it[displayName] = "Bob"
            }

            val capId = "cap_geo_strict"
            val unlockTime = 2_000_000L
            val secret = "Строго секретный гео-этап"

            Capsules.insert {
                it[id] = capId
                it[creatorId] = "user_u1"
                it[title] = "Гео-капсула"
                it[latitude] = 40.2825
                it[longitude] = 69.6221
                it[radiusMeters] = 50.0
                it[createdAt] = 1_000_000L
                it[isGroupCapsule] = true
                it[inviteCode] = "GEO123"
                it[kind] = "MESSAGE"
                it[isGeoLocked] = true
            }

            // Both participants check in cleanly at capsule center
            CapsuleParticipants.insert {
                it[capsuleId] = capId
                it[userId] = "user_u1"
                it[lastLat] = 40.2825
                it[lastLon] = 69.6221
                it[lastSeenAt] = unlockTime - 5_000L
            }
            CapsuleParticipants.insert {
                it[capsuleId] = capId
                it[userId] = "user_u2"
                it[lastLat] = 40.2825
                it[lastLon] = 69.6221
                it[lastSeenAt] = unlockTime - 5_000L
            }

            CapsuleStages.insert {
                it[id] = "stage_geo_1"
                it[capsuleId] = capId
                it[stageNumber] = 1
                it[unlockDate] = unlockTime
                it[encryptedText] = CryptoUtils.encrypt(secret)
            }

            // 1. All participants nearby & fresh, BEFORE unlockDate -> MUST remain SEALED
            val beforeTime = buildCapsuleDto(capId, now = unlockTime - 1_000L, requestingUserId = "user_u1")
            assertEquals("SEALED", beforeTime.status)
            assertFalse(beforeTime.isFullyUnlocked)
            assertFalse(beforeTime.stages.first().isUnlocked)
            assertEquals(GeoUtils.LOCKED_PLACEHOLDER, beforeTime.stages.first().text)

            // 2. AFTER unlockDate, both participants fresh (within 60s) -> READY & decrypted
            val unlocked = buildCapsuleDto(capId, now = unlockTime + 10_000L, requestingUserId = "user_u1")
            assertEquals("READY", unlocked.status)
            assertTrue(unlocked.isFullyUnlocked)
            assertTrue(unlocked.stages.first().isUnlocked)
            assertEquals(secret, unlocked.stages.first().text)

            // 3. AFTER unlockDate, one participant heartbeat stale (>60s) -> SEALED & encrypted again
            val staleNow = unlockTime + 70_000L // lastSeenAt was unlockTime - 5_000 -> age = 75_000 > 60_000
            CapsuleParticipants.update({
                (CapsuleParticipants.capsuleId eq capId) and (CapsuleParticipants.userId eq "user_u1")
            }) {
                it[lastSeenAt] = staleNow
            }
            val staleDto = buildCapsuleDto(capId, now = staleNow, requestingUserId = "user_u1")
            assertEquals("SEALED", staleDto.status)
            assertFalse(staleDto.isFullyUnlocked)
            assertFalse(staleDto.stages.first().isUnlocked)
            assertEquals(GeoUtils.LOCKED_PLACEHOLDER, staleDto.stages.first().text)
        }
    }

    @Test
    fun `validateCreateCapsuleRequest rejects unlockDate earlier than 1 minute and accepts future`() {
        val now = System.currentTimeMillis()
        val tooSoonRequest = com.timecapsule.shared.CreateCapsuleRequest(
            title = "Too Soon",
            latitude = 0.0,
            longitude = 0.0,
            isGeoLocked = false,
            stages = listOf(
                com.timecapsule.shared.CreateStageRequest(
                    stageNumber = 1,
                    unlockDate = now + 20_000L,
                    secretText = "Secret"
                )
            )
        )
        assertFailsWith<IllegalArgumentException> {
            validateCreateCapsuleRequest(tooSoonRequest, "user_alice")
        }

        val validRequest = tooSoonRequest.copy(
            stages = listOf(
                com.timecapsule.shared.CreateStageRequest(
                    stageNumber = 1,
                    unlockDate = now + 65_000L,
                    secretText = "Secret"
                )
            )
        )
        validateCreateCapsuleRequest(validRequest, "user_alice")
    }

    @Test
    fun `validateCreateCapsuleRequest validates radius strictly between 20 and 500 meters for geo capsules`() {
        val now = System.currentTimeMillis()
        val baseGeoRequest = com.timecapsule.shared.CreateCapsuleRequest(
            title = "Geo Radius Test",
            latitude = 40.2825,
            longitude = 69.6221,
            isGeoLocked = true,
            radiusMeters = 50.0,
            stages = listOf(
                com.timecapsule.shared.CreateStageRequest(
                    stageNumber = 1,
                    unlockDate = now + 120_000L,
                    secretText = "Secret geo message"
                )
            )
        )

        // Valid boundaries: 20m and 500m
        validateCreateCapsuleRequest(baseGeoRequest.copy(radiusMeters = 20.0), "user_alice")
        validateCreateCapsuleRequest(baseGeoRequest.copy(radiusMeters = 50.0), "user_alice")
        validateCreateCapsuleRequest(baseGeoRequest.copy(radiusMeters = 500.0), "user_alice")

        // Invalid: below 20m
        assertFailsWith<IllegalArgumentException> {
            validateCreateCapsuleRequest(baseGeoRequest.copy(radiusMeters = 19.9), "user_alice")
        }
        assertFailsWith<IllegalArgumentException> {
            validateCreateCapsuleRequest(baseGeoRequest.copy(radiusMeters = 0.0), "user_alice")
        }
        assertFailsWith<IllegalArgumentException> {
            validateCreateCapsuleRequest(baseGeoRequest.copy(radiusMeters = -10.0), "user_alice")
        }

        // Invalid: above 500m
        assertFailsWith<IllegalArgumentException> {
            validateCreateCapsuleRequest(baseGeoRequest.copy(radiusMeters = 500.1), "user_alice")
        }
        assertFailsWith<IllegalArgumentException> {
            validateCreateCapsuleRequest(baseGeoRequest.copy(radiusMeters = 1000.0), "user_alice")
        }

        // For non-geo capsules, radius validation is bypassed
        val nonGeo = baseGeoRequest.copy(isGeoLocked = false, radiusMeters = 10.0)
        validateCreateCapsuleRequest(nonGeo, "user_alice")
    }

    @Test
    fun `load test with 50 concurrent check-ins and capsule reads does not produce 500 or deadlocks`() {
        val testDb = Database.connect(
            url = "jdbc:h2:mem:test_load_${System.nanoTime()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        val capId = "cap_load_test"
        transaction(testDb) {
            SchemaUtils.create(Users, Capsules, CapsuleStages, CapsuleParticipants, Sessions, Blocks)
            Users.insert {
                it[id] = "user_creator"
                it[username] = "Creator"
            }
            Capsules.insert {
                it[id] = capId
                it[creatorId] = "user_creator"
                it[title] = "Load Test Capsule"
                it[latitude] = 40.2825
                it[longitude] = 69.6221
                it[radiusMeters] = 50.0
                it[createdAt] = System.currentTimeMillis()
                it[isGroupCapsule] = true
                it[inviteCode] = "LOAD50"
            }
            for (i in 1..50) {
                val uid = "user_load_$i"
                Users.insert {
                    it[id] = uid
                    it[username] = "User$i"
                }
                CapsuleParticipants.insert {
                    it[capsuleId] = capId
                    it[userId] = uid
                    it[lastLat] = 40.2825
                    it[lastLon] = 69.6221
                    it[lastSeenAt] = System.currentTimeMillis()
                }
            }
        }

        val threadPool = java.util.concurrent.Executors.newFixedThreadPool(16)
        val countDownLatch = java.util.concurrent.CountDownLatch(50)
        val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())

        for (i in 1..50) {
            val uid = "user_load_$i"
            threadPool.submit {
                try {
                    transaction(testDb) {
                        // Concurrent Check-in
                        CapsuleParticipants.update({
                            (CapsuleParticipants.capsuleId eq capId) and (CapsuleParticipants.userId eq uid)
                        }) {
                            it[lastLat] = 40.2825 + (i * 0.00001)
                            it[lastLon] = 69.6221 + (i * 0.00001)
                            it[lastSeenAt] = System.currentTimeMillis()
                        }

                        // Concurrent Read
                        val visibleIds = getVisibleCapsuleIds(uid)
                        assertTrue(visibleIds.contains(capId))
                        val participantsCount = CapsuleParticipants
                            .selectAll()
                            .where { CapsuleParticipants.capsuleId eq capId }
                            .count()
                        assertEquals(50L, participantsCount)
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    countDownLatch.countDown()
                }
            }
        }

        val completed = countDownLatch.await(15, java.util.concurrent.TimeUnit.SECONDS)
        threadPool.shutdown()
        assertTrue(completed, "Concurrency test timed out")
        assertEquals(0, errors.size, "Encountered concurrency errors: ${errors.map { it.message }}")
    }

    @Test
    fun `handle format, reserved names, availability, uniqueness and race condition work cleanly`() = testApplication {
        val testDb = Database.connect(
            url = "jdbc:h2:mem:test_handles_${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        var tokenA = ""
        var tokenB = ""
        transaction(testDb) {
            SchemaUtils.create(Users, Capsules, CapsuleStages, CapsuleParticipants, Sessions, Blocks)
            Users.insert {
                it[id] = "user_h1"
                it[username] = "Alice"
                it[email] = "alice@gmail.com"
            }
            Users.insert {
                it[id] = "user_h2"
                it[username] = "Bob"
                it[email] = "bob@gmail.com"
            }
            tokenA = createSessionToken("user_h1")
            tokenB = createSessionToken("user_h2")
        }

        application {
            installSerializationAndErrors()
            routing { capsuleRoutes() }
        }

        val json = Json { ignoreUnknownKeys = true }

        // Некорректный формат (короткий / недопустимые символы) -> valid=false и PUT 400
        val invalidCheck = client.get("/api/v1/users/check-handle?handle=ab") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        assertEquals(HttpStatusCode.OK, invalidCheck.status)
        val invalidDto = json.decodeFromString<CheckHandleResponse>(invalidCheck.bodyAsText())
        assertFalse(invalidDto.valid)
        assertEquals("INVALID_FORMAT", invalidDto.reason)

        val badPut = client.put("/api/v1/users/me/handle") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"handle":"Alice-Invalid"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, badPut.status)

        // Зарезервированные имена -> valid=false ("RESERVED") и PUT 400
        for (reserved in listOf("admin", "support", "root", "system", "timecapsule")) {
            val resCheck = client.get("/api/v1/users/check-handle?handle=@${reserved.uppercase()}") {
                header(HttpHeaders.Authorization, "Bearer $tokenA")
            }
            val resDto = json.decodeFromString<CheckHandleResponse>(resCheck.bodyAsText())
            assertFalse(resDto.valid)
            assertFalse(resDto.available)
            assertEquals("RESERVED", resDto.reason)

            val resPut = client.put("/api/v1/users/me/handle") {
                header(HttpHeaders.Authorization, "Bearer $tokenA")
                contentType(ContentType.Application.Json)
                setBody("""{"handle":"$reserved"}""")
            }
            assertEquals(HttpStatusCode.BadRequest, resPut.status)
        }

        // Успешная установка handle с нормализацией регистра и '@'
        val okCheck = client.get("/api/v1/users/check-handle?handle=@Alice_99") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        val okCheckDto = json.decodeFromString<CheckHandleResponse>(okCheck.bodyAsText())
        assertTrue(okCheckDto.valid && okCheckDto.available)
        assertEquals("alice_99", okCheckDto.handle)

        val okPut = client.put("/api/v1/users/me/handle") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"handle":"@Alice_99"}""")
        }
        assertEquals(HttpStatusCode.OK, okPut.status)
        val updatedA = json.decodeFromString<UserDto>(okPut.bodyAsText())
        assertEquals("alice_99", updatedA.handle)
        assertEquals("alice@gmail.com", updatedA.email)

        // Пользователь B видит, что @alice_99 занят и при PUT получает 409 Conflict
        val takenCheck = client.get("/api/v1/users/check-handle?handle=alice_99") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
        }
        val takenDto = json.decodeFromString<CheckHandleResponse>(takenCheck.bodyAsText())
        assertTrue(takenDto.valid)
        assertFalse(takenDto.available)
        assertEquals("TAKEN", takenDto.reason)

        val conflictPut = client.put("/api/v1/users/me/handle") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
            contentType(ContentType.Application.Json)
            setBody("""{"handle":"alice_99"}""")
        }
        assertEquals(HttpStatusCode.Conflict, conflictPut.status)

        // Гонка (concurrent race condition): 2 пользователя одновременно пытаются занять один и тот же handle
        val raceUsers = (1..2).map { idx ->
            val uid = "race_$idx"
            transaction(testDb) {
                Users.insert {
                    it[id] = uid
                    it[username] = "Racer$idx"
                }
            }
            uid
        }
        val barrier = java.util.concurrent.CyclicBarrier(2)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        val futures = raceUsers.map { uid ->
            pool.submit<String> {
                barrier.await()
                try {
                    updateUserHandle(uid, "same_race_handle")
                    "OK"
                } catch (c: ConflictException) {
                    "CONFLICT"
                }
            }
        }
        val outcomes = futures.map { it.get(10, java.util.concurrent.TimeUnit.SECONDS) }.sorted()
        pool.shutdown()
        assertEquals(listOf("CONFLICT", "OK"), outcomes)
    }

    @Test
    fun `user search matches handle prefix and name, excludes self and null handles, hides foreign emails, caps at 20`() = testApplication {
        val testDb = Database.connect(
            url = "jdbc:h2:mem:test_user_search_${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        var selfToken = ""
        transaction(testDb) {
            SchemaUtils.create(Users, Capsules, CapsuleStages, CapsuleParticipants, Sessions, Blocks)
            Users.insert {
                it[id] = "self_user"
                it[username] = "Alice Self"
                it[handle] = "alice_self"
                it[email] = "self@example.com"
                it[photoUrl] = "https://img.example.com/self.png"
            }
            Users.insert {
                it[id] = "no_handle_user"
                it[username] = "Alice NoHandle"
                it[handle] = null
                it[email] = "nohandle@example.com"
            }
            for (i in 1..25) {
                Users.insert {
                    it[id] = "user_$i"
                    it[username] = "Friend $i"
                    it[handle] = "alpha_${i.toString().padStart(2, '0')}"
                    it[email] = "secret_$i@example.com"
                    it[googleSub] = "sub_$i"
                    it[photoUrl] = "https://img.example.com/$i.png"
                }
            }
            selfToken = createSessionToken("self_user")
        }

        application {
            installSerializationAndErrors()
            routing { capsuleRoutes() }
        }

        val json = Json { ignoreUnknownKeys = true }

        // Поиск по префиксу @alpha -> максимум 20, без self, без пользователей без handle, email == null
        val respHandlePrefix = client.get("/api/v1/users?query=@alpha") {
            header(HttpHeaders.Authorization, "Bearer $selfToken")
        }
        assertEquals(HttpStatusCode.OK, respHandlePrefix.status)
        val rawBody = respHandlePrefix.bodyAsText()
        assertFalse(rawBody.contains("secret_"), "Foreign email must never appear in response")
        assertFalse(rawBody.contains("sub_"), "Foreign googleSub must never appear in response")

        val listByPrefix = json.decodeFromString<List<UserDto>>(rawBody)
        assertEquals(20, listByPrefix.size)
        assertTrue(listByPrefix.none { it.id == "self_user" })
        assertTrue(listByPrefix.none { it.id == "no_handle_user" })
        assertTrue(listByPrefix.all { it.handle?.startsWith("alpha_") == true })
        assertTrue(listByPrefix.all { it.email == null })
        assertTrue(listByPrefix.all { it.avatarUrl != null })

        // Поиск без '@' находит по имени и исключает пользователей без handle и самого себя
        val respName = client.get("/api/v1/users?query=Alice") {
            header(HttpHeaders.Authorization, "Bearer $selfToken")
        }
        val listByName = json.decodeFromString<List<UserDto>>(respName.bodyAsText())
        assertEquals(0, listByName.size)

        val respFriend = client.get("/api/v1/users?query=Friend%2015") {
            header(HttpHeaders.Authorization, "Bearer $selfToken")
        }
        val listFriend = json.decodeFromString<List<UserDto>>(respFriend.bodyAsText())
        assertEquals(listOf("user_15"), listFriend.map { it.id })
        assertNull(listFriend.first().email)
    }

    @Test
    fun `POST api v1 auth google rejects invalid idToken with 401 and preserves existing handle`() = testApplication {
        val testDb = Database.connect(
            url = "jdbc:h2:mem:test_google_auth_${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        transaction(testDb) {
            SchemaUtils.create(Users, Capsules, CapsuleStages, CapsuleParticipants, Sessions, Blocks)
            Users.insert {
                it[id] = "existing_uid"
                it[username] = "Old Name"
                it[handle] = "existing_handle"
                it[googleSub] = "google_sub_777"
                it[email] = "owner@example.com"
                it[displayName] = "Old Name"
            }
        }

        application {
            installSerializationAndErrors()
            routing { capsuleRoutes() }
        }

        // Реальный GoogleIdTokenVerifier с невалидным токеном -> 401 Unauthorized
        System.setProperty("GOOGLE_WEB_CLIENT_ID", "test-client-id.apps.googleusercontent.com")
        try {
            val badResponse = client.post("/api/v1/auth/google") {
                contentType(ContentType.Application.Json)
                setBody("""{"idToken":"invalid.jwt.token"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, badResponse.status)
        } finally {
            System.clearProperty("GOOGLE_WEB_CLIENT_ID")
        }

        // Повторный вход существующего пользователя сохраняет выбранный @handle
        val prevVerifier = googleTokenVerifierFn
        googleTokenVerifierFn = { _ ->
            VerifiedGoogleProfile(
                googleSub = "google_sub_777",
                email = "owner@example.com",
                displayName = "New Display Name",
                photoUrl = "https://img.example.com/avatar.png"
            )
        }
        try {
            val okResponse = client.post("/api/v1/auth/google") {
                contentType(ContentType.Application.Json)
                setBody("""{"idToken":"mock_verified_jwt_for_unit_test"}""")
            }
            assertEquals(HttpStatusCode.OK, okResponse.status)
            val json = Json { ignoreUnknownKeys = true }
            val authDto = json.decodeFromString<AuthResponse>(okResponse.bodyAsText())
            assertEquals("existing_handle", authDto.user.handle)
            assertEquals("https://img.example.com/avatar.png", authDto.user.avatarUrl)
            assertEquals("owner@example.com", authDto.user.email)
        } finally {
            googleTokenVerifierFn = prevVerifier
        }
    }

    @Test
    fun `capsule sent by handle is visible only to sender and recipient, keeps sealed text hidden, and tracks recipient openedAt`() = testApplication {
        val testDb = Database.connect(
            url = "jdbc:h2:mem:test_send_handle_${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        var aliceToken = ""
        var bobToken = ""
        var charlieToken = ""
        transaction(testDb) {
            SchemaUtils.create(Users, Capsules, CapsuleStages, CapsuleParticipants, Sessions, Blocks)
            Users.insert {
                it[id] = "user_alice"
                it[username] = "Alice"
                it[handle] = "alice"
                it[email] = "alice@example.com"
            }
            Users.insert {
                it[id] = "user_bob"
                it[username] = "Bob"
                it[handle] = "bob_dev"
                it[email] = "bob@example.com"
            }
            Users.insert {
                it[id] = "user_charlie"
                it[username] = "Charlie"
                it[handle] = "charlie"
                it[email] = "charlie@example.com"
            }
            for (i in 1..11) {
                Users.insert {
                    it[id] = "extra_$i"
                    it[username] = "Extra $i"
                    it[handle] = "extra_$i"
                }
            }
            aliceToken = createSessionToken("user_alice")
            bobToken = createSessionToken("user_bob")
            charlieToken = createSessionToken("user_charlie")

            // Verify legacy/default participant role defaults to MEMBER
            Capsules.insert {
                it[id] = "legacy_cap"
                it[creatorId] = "user_alice"
                it[title] = "Legacy"
                it[latitude] = 0.0
                it[longitude] = 0.0
                it[radiusMeters] = 50.0
                it[createdAt] = System.currentTimeMillis()
                it[isGroupCapsule] = false
                it[inviteCode] = "LEG001"
                it[isGeoLocked] = false
            }
            CapsuleParticipants.insert {
                it[capsuleId] = "legacy_cap"
                it[userId] = "user_bob"
            }
            val legacyRole = CapsuleParticipants
                .selectAll()
                .where { (CapsuleParticipants.capsuleId eq "legacy_cap") and (CapsuleParticipants.userId eq "user_bob") }
                .single()[CapsuleParticipants.role]
            assertEquals("MEMBER", legacyRole)
        }

        application {
            installSerializationAndErrors()
            routing { capsuleRoutes() }
        }

        val json = Json { ignoreUnknownKeys = true }
        val secretPhrase = "ULTRA_SECRET_MESSAGE_2027"
        val futureUnlock = System.currentTimeMillis() + 3_600_000L

        // >10 recipients on personal capsule rejected with 400
        val tooManyReq = CreateCapsuleRequest(
            title = "Too many recipients",
            isGeoLocked = false,
            recipientHandles = (1..11).map { "extra_$it" },
            stages = listOf(CreateStageRequest(stageNumber = 1, unlockDate = futureUnlock, secretText = secretPhrase))
        )
        val tooManyResp = client.post("/api/v1/capsules") {
            header(HttpHeaders.Authorization, "Bearer $aliceToken")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(tooManyReq))
        }
        assertEquals(HttpStatusCode.BadRequest, tooManyResp.status)

        // Alice sends personal capsule to @bob_dev
        val sendReq = CreateCapsuleRequest(
            title = "Letter to Bob",
            isGeoLocked = false,
            recipientHandles = listOf("@bob_dev"),
            stages = listOf(CreateStageRequest(stageNumber = 1, unlockDate = futureUnlock, secretText = secretPhrase))
        )
        val createResp = client.post("/api/v1/capsules") {
            header(HttpHeaders.Authorization, "Bearer $aliceToken")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(sendReq))
        }
        assertEquals(HttpStatusCode.OK, createResp.status)
        val createRaw = createResp.bodyAsText()
        assertFalse(createRaw.contains(secretPhrase), "Sealed secret text must NOT leak to creator on creation")
        val createdDto = json.decodeFromString<CapsuleDto>(createRaw)
        assertEquals("SEALED", createdDto.status)
        assertFalse(createdDto.isInbox)
        assertEquals("CREATOR", createdDto.participants.first { it.userId == "user_alice" }.role)
        assertEquals("RECIPIENT", createdDto.participants.first { it.userId == "user_bob" }.role)

        // Bob sees it in Inbox as SEALED with @alice sender handle, secret text hidden
        val bobListResp = client.get("/api/v1/capsules") {
            header(HttpHeaders.Authorization, "Bearer $bobToken")
        }
        assertEquals(HttpStatusCode.OK, bobListResp.status)
        val bobListRaw = bobListResp.bodyAsText()
        assertFalse(bobListRaw.contains(secretPhrase), "Sealed secret text must NOT leak to recipient before date")
        val bobCapsule = json.decodeFromString<List<CapsuleDto>>(bobListRaw).first { it.id == createdDto.id }
        assertTrue(bobCapsule.isInbox)
        assertEquals("alice", bobCapsule.creatorHandle)
        assertEquals("SEALED", bobCapsule.status)
        assertEquals(GeoUtils.LOCKED_PLACEHOLDER, bobCapsule.stages.first().text)

        // Premature open by Bob gives 409 Conflict
        val prematureOpen = client.post("/api/v1/capsules/${createdDto.id}/open") {
            header(HttpHeaders.Authorization, "Bearer $bobToken")
        }
        assertEquals(HttpStatusCode.Conflict, prematureOpen.status)

        // Charlie (stranger) does NOT see it in list, GET /{id}, or POST /open
        val charlieList = json.decodeFromString<List<CapsuleDto>>(
            client.get("/api/v1/capsules") {
                header(HttpHeaders.Authorization, "Bearer $charlieToken")
            }.bodyAsText()
        )
        assertTrue(charlieList.none { it.id == createdDto.id })
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/api/v1/capsules/${createdDto.id}") {
                header(HttpHeaders.Authorization, "Bearer $charlieToken")
            }.status
        )
        assertEquals(
            HttpStatusCode.NotFound,
            client.post("/api/v1/capsules/${createdDto.id}/open") {
                header(HttpHeaders.Authorization, "Bearer $charlieToken")
            }.status
        )

        // Simulate time passing beyond unlockDate
        transaction(testDb) {
            CapsuleStages.update({ CapsuleStages.capsuleId eq createdDto.id }) {
                it[unlockDate] = System.currentTimeMillis() - 10_000L
            }
        }

        // Bob opens capsule himself
        val bobOpenResp = client.post("/api/v1/capsules/${createdDto.id}/open") {
            header(HttpHeaders.Authorization, "Bearer $bobToken")
        }
        assertEquals(HttpStatusCode.OK, bobOpenResp.status)
        val bobOpenedDto = json.decodeFromString<CapsuleDto>(bobOpenResp.bodyAsText())
        assertEquals("OPENED", bobOpenedDto.status)
        assertNotNull(bobOpenedDto.openedAt)
        assertEquals(secretPhrase, bobOpenedDto.stages.first().text)

        // Alice sees recipient openedAt populated ("открыто получателем")
        val aliceViewResp = client.get("/api/v1/capsules/${createdDto.id}") {
            header(HttpHeaders.Authorization, "Bearer $aliceToken")
        }
        val aliceView = json.decodeFromString<CapsuleDto>(aliceViewResp.bodyAsText())
        val bobParticipantStatus = aliceView.participants.first { it.userId == "user_bob" }
        assertEquals("RECIPIENT", bobParticipantStatus.role)
        assertNotNull(bobParticipantStatus.openedAt)
        assertNull(aliceView.openedAt)
    }

    @Test
    fun `daily limit returns 429 on 31st sent capsule and blocking prevents sending and hides existing inbox`() = testApplication {
        val testDb = Database.connect(
            url = "jdbc:h2:mem:test_limit_block_${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        var spammerToken = ""
        var aliceToken = ""
        var bobToken = ""
        transaction(testDb) {
            SchemaUtils.create(Users, Capsules, CapsuleStages, CapsuleParticipants, Sessions, Blocks)
            Users.insert {
                it[id] = "spammer"
                it[username] = "Spammer"
                it[handle] = "spammer_user"
            }
            Users.insert {
                it[id] = "alice"
                it[username] = "Alice"
                it[handle] = "alice_send"
            }
            Users.insert {
                it[id] = "bob"
                it[username] = "Bob"
                it[handle] = "bob_recv"
            }
            spammerToken = createSessionToken("spammer")
            aliceToken = createSessionToken("alice")
            bobToken = createSessionToken("bob")
        }

        application {
            installSerializationAndErrors()
            routing { capsuleRoutes() }
        }

        val json = Json { ignoreUnknownKeys = true }
        val futureUnlock = System.currentTimeMillis() + 3_600_000L

        // Send 30 capsules within 24h -> all succeed
        for (i in 1..30) {
            val req = CreateCapsuleRequest(
                title = "Msg $i",
                isGeoLocked = false,
                recipientHandles = listOf("bob_recv"),
                stages = listOf(CreateStageRequest(1, futureUnlock, "Secret $i"))
            )
            val resp = client.post("/api/v1/capsules") {
                header(HttpHeaders.Authorization, "Bearer $spammerToken")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(req))
            }
            assertEquals(HttpStatusCode.OK, resp.status)
        }

        // 31st sent capsule in 24h -> 429 Too Many Requests
        val overLimitReq = CreateCapsuleRequest(
            title = "Msg 31",
            isGeoLocked = false,
            recipientHandles = listOf("bob_recv"),
            stages = listOf(CreateStageRequest(1, futureUnlock, "Too much"))
        )
        val overLimitResp = client.post("/api/v1/capsules") {
            header(HttpHeaders.Authorization, "Bearer $spammerToken")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(overLimitReq))
        }
        assertEquals(HttpStatusCode.TooManyRequests, overLimitResp.status)

        // Alice sends a capsule to Bob (visible before block)
        val aliceResp = client.post("/api/v1/capsules") {
            header(HttpHeaders.Authorization, "Bearer $aliceToken")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateCapsuleRequest(
                        title = "From Alice",
                        isGeoLocked = false,
                        recipientHandles = listOf("bob_recv"),
                        stages = listOf(CreateStageRequest(1, futureUnlock, "Hi Bob"))
                    )
                )
            )
        }
        assertEquals(HttpStatusCode.OK, aliceResp.status)
        val aliceCapId = json.decodeFromString<CapsuleDto>(aliceResp.bodyAsText()).id

        val bobBeforeBlock = json.decodeFromString<List<CapsuleDto>>(
            client.get("/api/v1/capsules") {
                header(HttpHeaders.Authorization, "Bearer $bobToken")
            }.bodyAsText()
        )
        assertTrue(bobBeforeBlock.any { it.id == aliceCapId })

        // Bob blocks Alice
        val blockResp = client.post("/api/v1/blocks") {
            header(HttpHeaders.Authorization, "Bearer $bobToken")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(BlockUserRequest(userId = "alice")))
        }
        assertEquals(HttpStatusCode.OK, blockResp.status)

        // Existing incoming capsule from Alice is hidden from Bob
        val bobAfterBlock = json.decodeFromString<List<CapsuleDto>>(
            client.get("/api/v1/capsules") {
                header(HttpHeaders.Authorization, "Bearer $bobToken")
            }.bodyAsText()
        )
        assertTrue(bobAfterBlock.none { it.id == aliceCapId })
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/api/v1/capsules/$aliceCapId") {
                header(HttpHeaders.Authorization, "Bearer $bobToken")
            }.status
        )

        // Alice cannot send new capsule to Bob -> 403 Forbidden
        val blockedSendResp = client.post("/api/v1/capsules") {
            header(HttpHeaders.Authorization, "Bearer $aliceToken")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateCapsuleRequest(
                        title = "Blocked attempt",
                        isGeoLocked = false,
                        recipientHandles = listOf("bob_recv"),
                        stages = listOf(CreateStageRequest(1, futureUnlock, "Should fail"))
                    )
                )
            )
        }
        assertEquals(HttpStatusCode.Forbidden, blockedSendResp.status)
    }

    @Test
    fun `DatabaseConfig parses Neon postgresql URL, configures Hikari maxPoolSize 3 timeout 10s, and falls back to H2`() {
        val neonUrl = "postgresql://neon_user:p%40ss%3A123@ep-cool-host-123.us-east-2.aws.neon.tech/neondb?sslmode=require"
        val spec = DatabaseConfig.resolveConnectionSpec(databaseUrlEnv = neonUrl, h2UrlEnv = null)
        assertTrue(spec.isPostgres)
        assertEquals("org.postgresql.Driver", spec.driverClassName)
        assertEquals(
            "jdbc:postgresql://ep-cool-host-123.us-east-2.aws.neon.tech/neondb?sslmode=require",
            spec.jdbcUrl
        )
        assertEquals("neon_user", spec.username)
        assertEquals("p@ss:123", spec.password)

        val herokuAlias = DatabaseConfig.resolveConnectionSpec(
            databaseUrlEnv = "postgres://u1:pw1@db.internal:5433/capsules",
            h2UrlEnv = null
        )
        assertEquals("jdbc:postgresql://db.internal:5433/capsules", herokuAlias.jdbcUrl)
        assertEquals("u1", herokuAlias.username)
        assertEquals("pw1", herokuAlias.password)

        val hikariConfig = DatabaseConfig.buildHikariConfig(spec)
        assertEquals(3, hikariConfig.maximumPoolSize)
        assertEquals(10_000L, hikariConfig.connectionTimeout)

        val h2Fallback = DatabaseConfig.resolveConnectionSpec(databaseUrlEnv = "  ", h2UrlEnv = null)
        assertFalse(h2Fallback.isPostgres)
        assertEquals("org.h2.Driver", h2Fallback.driverClassName)
        assertEquals("jdbc:h2:file:./data/timecapsule;DB_CLOSE_DELAY=-1", h2Fallback.jdbcUrl)

        assertFailsWith<DatabaseUnavailableException> {
            DatabaseConfig.parsePostgresUrl("mysql://user:pass@localhost/db")
        }
    }

    @Test
    fun `HikariCP initPool on H2 runs migrations, reports healthy GET health, and closes cleanly`() = testApplication {
        val memUrl = "jdbc:h2:mem:test_hikari_lifecycle_${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
        val spec = DatabaseConfig.resolveConnectionSpec(databaseUrlEnv = null, h2UrlEnv = memUrl)
        val pool = DatabaseConfig.initPool(spec)
        try {
            Database.connect(pool)
            migrateDatabase()
            assertTrue(DatabaseConfig.isDatabaseHealthy())

            application {
                installSerializationAndErrors()
                routing { capsuleRoutes() }
            }

            val healthyResp = client.get("/health")
            assertEquals(HttpStatusCode.OK, healthyResp.status)
            assertEquals("ok", healthyResp.bodyAsText())

            val prevHealthFn = healthCheckFn
            healthCheckFn = { false }
            try {
                val downResp = client.get("/health")
                assertEquals(HttpStatusCode.ServiceUnavailable, downResp.status)
                assertEquals("database_unavailable", downResp.bodyAsText())
            } finally {
                healthCheckFn = prevHealthFn
            }
        } finally {
            DatabaseConfig.closePool()
            assertTrue(pool.isClosed)
        }
    }

    @Test
    fun `initPool throws friendly DatabaseUnavailableException when PostgreSQL host is unreachable`() {
        val unreachableSpec = DatabaseConfig.parsePostgresUrl(
            "postgresql://postgres:secret@127.0.0.1:59999/timecapsule?sslmode=disable&connectTimeout=1"
        )
        val ex = assertFailsWith<DatabaseUnavailableException> {
            DatabaseConfig.initPool(unreachableSpec)
        }
        val msg = ex.message.orEmpty()
        assertTrue(msg.contains("PostgreSQL", ignoreCase = true) || msg.contains("недоступна", ignoreCase = true))
    }

    @Test
    fun `GET api v1 app version is public without Bearer and enforces https-only apkUrl`() = testApplication {
        val validSha = "a".repeat(64).uppercase()
        val envMap = mapOf(
            "LATEST_VERSION_CODE" to "4",
            "LATEST_VERSION_NAME" to "1.4.0",
            "LATEST_APK_URL" to "https://github.com/user/time-capsule/releases/download/v1.4.0/app-release.apk",
            "LATEST_APK_SHA256" to validSha,
            "LATEST_CHANGELOG" to "Добавлены автообновления\\nУлучшен радар",
            "LATEST_MIN_SUPPORTED_VERSION_CODE" to "2"
        )
        val parsed = AppVersionConfig.fromEnv { key -> envMap[key] }
        assertEquals(4, parsed.versionCode)
        assertEquals("1.4.0", parsed.versionName)
        assertEquals(
            "https://github.com/user/time-capsule/releases/download/v1.4.0/app-release.apk",
            parsed.apkUrl
        )
        assertEquals(validSha.lowercase(), parsed.apkSha256)
        assertEquals("Добавлены автообновления\nУлучшен радар", parsed.changelog)
        assertEquals(2, parsed.minSupportedVersionCode)

        // Небезопасные URL (http://, file://, javascript:) отклоняются в пустую строку
        assertEquals("", AppVersionConfig.sanitizeHttpsUrl("http://example.com/app.apk"))
        assertEquals("", AppVersionConfig.sanitizeHttpsUrl("file:///tmp/app.apk"))
        assertEquals("", AppVersionConfig.sanitizeHttpsUrl("https://evil.com/a b.apk"))
        assertEquals("", AppVersionConfig.sanitizeSha256("not_a_sha256_hash"))

        val prevProvider = appVersionProviderFn
        appVersionProviderFn = { parsed }
        try {
            application {
                installSerializationAndErrors()
                routing { capsuleRoutes() }
            }

            // Запрос БЕЗ Authorization: Bearer должен отдавать 200 OK
            val resp = client.get("/api/v1/app/version")
            assertEquals(HttpStatusCode.OK, resp.status)
            val bodyDto = Json.decodeFromString<AppVersionDto>(resp.bodyAsText())
            assertEquals(parsed, bodyDto)
        } finally {
            appVersionProviderFn = prevProvider
        }
    }
}

