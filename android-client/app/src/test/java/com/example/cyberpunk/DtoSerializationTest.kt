package com.example.cyberpunk

import com.timecapsule.shared.BlockUserRequest
import com.timecapsule.shared.CapsuleDto
import com.timecapsule.shared.CapsuleStageDto
import com.timecapsule.shared.CheckHandleResponse
import com.timecapsule.shared.CheckInRequest
import com.timecapsule.shared.CreateCapsuleRequest
import com.timecapsule.shared.CreateStageRequest
import com.timecapsule.shared.JoinCapsuleRequest
import com.timecapsule.shared.MessageResponse
import com.timecapsule.shared.ParticipantStatusDto
import com.timecapsule.shared.UpdateHandleRequest
import com.timecapsule.shared.UserDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DtoSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testUserDtoSerialization() {
        val user = UserDto(
            id = "user_123",
            username = "TestUser",
            email = "test@example.com",
            photoUrl = "https://example.com/photo.png",
            handle = "test_user",
            avatarUrl = "https://example.com/photo.png"
        )
        val encoded = json.encodeToString(user)
        val decoded = json.decodeFromString<UserDto>(encoded)
        assertEquals(user, decoded)

        // Обратная совместимость при отсутствии handle и avatarUrl
        val legacyJson = """{"id":"u_old","username":"Legacy"}"""
        val decodedLegacy = json.decodeFromString<UserDto>(legacyJson)
        assertEquals(null, decodedLegacy.handle)
        assertEquals(null, decodedLegacy.avatarUrl)

        val checkResp = CheckHandleResponse(handle = "alice_99", valid = true, available = true, reason = "OK")
        assertEquals(checkResp, json.decodeFromString(json.encodeToString(checkResp)))

        val updateReq = UpdateHandleRequest(handle = "alice_99")
        assertEquals(updateReq, json.decodeFromString(json.encodeToString(updateReq)))
    }

    @Test
    fun testCapsuleDtoSerialization() {
        val stage = CapsuleStageDto(
            id = "s_1",
            stageNumber = 1,
            unlockDate = 1794300000000L,
            text = "Секретное послание",
            isUnlocked = true
        )
        val participant = ParticipantStatusDto(
            userId = "u_1",
            username = "Alice",
            distanceMeters = 12.5,
            lastSeenAt = 1794300010000L,
            isNearbyAndRecent = true,
            openedAt = 1794300020000L,
            archived = false,
            handle = "alice_99",
            avatarUrl = "https://example.com/a.png",
            role = "RECIPIENT"
        )
        val capsule = CapsuleDto(
            id = "cap_1",
            title = "Капсула 2026",
            latitude = 40.2825,
            longitude = 69.6221,
            radiusMeters = 50.0,
            isGroupCapsule = true,
            isFullyUnlocked = true,
            stages = listOf(stage),
            participants = listOf(participant),
            creatorId = "u_1",
            inviteCode = "ABC123",
            kind = "MESSAGE",
            recipient = "Для друзей",
            tags = listOf("память", "худжанд"),
            isGeoLocked = true,
            status = "OPENED",
            openedAt = 1794300020000L,
            archived = false,
            creatorHandle = "sender_alice",
            isInbox = true
        )

        val encoded = json.encodeToString(capsule)
        assertTrue(encoded.contains("Капсула 2026"))
        assertTrue(encoded.contains("OPENED"))

        val decoded = json.decodeFromString<CapsuleDto>(encoded)
        assertEquals(capsule, decoded)
    }

    @Test
    fun testCreateCapsuleRequestSerialization() {
        val req = CreateCapsuleRequest(
            creatorId = "u_1",
            title = "Новая капсула",
            latitude = 40.0,
            longitude = 69.0,
            radiusMeters = 30.0,
            isGroupCapsule = false,
            participantIds = listOf("u_1"),
            stages = listOf(CreateStageRequest(stageNumber = 1, unlockDate = 1794300000000L, secretText = "Секрет")),
            kind = "MESSAGE",
            recipient = "Дочери",
            tags = listOf("семья"),
            isGeoLocked = false,
            recipientHandles = listOf("bob_99")
        )
        val encoded = json.encodeToString(req)
        val decoded = json.decodeFromString<CreateCapsuleRequest>(encoded)
        assertEquals(req, decoded)

        val blockReq = BlockUserRequest(userId = "u_spam")
        assertEquals(blockReq, json.decodeFromString(json.encodeToString(blockReq)))
    }

    @Test
    fun testCheckInAndJoinSerialization() {
        val checkIn = CheckInRequest(userId = "u_1", lat = 40.1, lon = 69.1)
        val checkInEncoded = json.encodeToString(checkIn)
        val checkInDecoded = json.decodeFromString<CheckInRequest>(checkInEncoded)
        assertEquals(checkIn, checkInDecoded)

        val join = JoinCapsuleRequest(inviteCode = "KHJ001")
        val joinEncoded = json.encodeToString(join)
        val joinDecoded = json.decodeFromString<JoinCapsuleRequest>(joinEncoded)
        assertEquals(join, joinDecoded)

        val msg = MessageResponse(message = "Успешно")
        val msgEncoded = json.encodeToString(msg)
        val msgDecoded = json.decodeFromString<MessageResponse>(msgEncoded)
        assertEquals(msg, msgDecoded)
    }
}

