// FILE: ModelsDto.kt
package com.timecapsule.shared

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val email: String? = null,
    val photoUrl: String? = null,
    val handle: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class CheckHandleResponse(
    val handle: String,
    val valid: Boolean,
    val available: Boolean,
    val reason: String? = null
)

@Serializable
data class UpdateHandleRequest(
    val handle: String
)

@Serializable
data class GoogleAuthRequest(
    val idToken: String
)

@Serializable
data class AuthResponse(
    val user: UserDto,
    val sessionToken: String
)

@Serializable
data class ParticipantStatusDto(
    val userId: String,
    val username: String,
    val distanceMeters: Double?,
    val lastSeenAt: Long?,
    val isNearbyAndRecent: Boolean,
    val openedAt: Long? = null,
    val archived: Boolean = false,
    val handle: String? = null,
    val avatarUrl: String? = null,
    val role: String = "MEMBER"
)

@Serializable
data class CapsuleStageDto(
    val id: String,
    val stageNumber: Int,
    val unlockDate: Long,
    val text: String,
    val isUnlocked: Boolean
)

@Serializable
data class CapsuleDto(
    val id: String,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val isGroupCapsule: Boolean,
    val isFullyUnlocked: Boolean,
    val stages: List<CapsuleStageDto>,
    val participants: List<ParticipantStatusDto>,
    val creatorId: String? = null,
    val inviteCode: String? = null,
    val kind: String = "MESSAGE",
    val recipient: String? = null,
    val tags: List<String> = emptyList(),
    val isGeoLocked: Boolean = true,
    val status: String = "SEALED",
    val openedAt: Long? = null,
    val archived: Boolean = false,
    val creatorHandle: String? = null,
    val isInbox: Boolean = false
)

@Serializable
data class CreateStageRequest(
    val stageNumber: Int = 1,
    val unlockDate: Long,
    val secretText: String
)

@Serializable
data class CreateCapsuleRequest(
    val creatorId: String = "",
    val title: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Double = 50.0,
    val isGroupCapsule: Boolean = false,
    val participantIds: List<String> = emptyList(),
    val stages: List<CreateStageRequest>,
    val kind: String = "MESSAGE",
    val recipient: String? = null,
    val tags: List<String> = emptyList(),
    val isGeoLocked: Boolean = true,
    val recipientHandles: List<String> = emptyList()
)

@Serializable
data class BlockUserRequest(
    val userId: String
)

@Serializable
data class CheckInRequest(
    val userId: String = "",
    val lat: Double,
    val lon: Double
)

@Serializable
data class JoinCapsuleRequest(
    val inviteCode: String
)

@Serializable
data class MessageResponse(
    val message: String
)
