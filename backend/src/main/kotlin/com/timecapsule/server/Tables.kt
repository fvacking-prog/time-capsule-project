// FILE: Tables.kt
package com.timecapsule.server

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Users : Table("users") {
    val id = varchar("id", 36)
    val username = varchar("username", 128)
    val handle = varchar("handle", 32).uniqueIndex().nullable()
    val googleSub = varchar("google_sub", 128).uniqueIndex().nullable()
    val email = varchar("email", 255).nullable()
    val displayName = varchar("display_name", 128).nullable()
    val photoUrl = varchar("photo_url", 512).nullable()

    override val primaryKey = PrimaryKey(id)
}

// Выбрана таблица Sessions вместо JWT: проще и надёжнее в рамках монолита с H2/Exposed,
// не требует хранения/ротации секретов подписи JWT и позволяет мгновенно отзывать сессионные токены.
object Sessions : Table("sessions") {
    val token = varchar("token", 64)
    val userId = varchar("user_id", 36).references(Users.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")

    override val primaryKey = PrimaryKey(token)
}

object Capsules : Table("capsules") {
    val id = varchar("id", 36)
    val creatorId = varchar("creator_id", 36).references(Users.id, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 255)
    val latitude = double("latitude")
    val longitude = double("longitude")
    val radiusMeters = double("radius_meters")
    val createdAt = long("created_at")
    val isGroupCapsule = bool("is_group_capsule")
    val inviteCode = varchar("invite_code", 8).nullable().uniqueIndex()
    val kind = varchar("kind", 32).default("MESSAGE")
    val recipient = varchar("recipient", 255).nullable()
    val tags = varchar("tags", 500).default("")
    val isGeoLocked = bool("is_geo_locked").default(true)

    override val primaryKey = PrimaryKey(id)
}

object CapsuleStages : Table("capsule_stages") {
    val id = varchar("id", 36)
    val capsuleId = varchar("capsule_id", 36).references(Capsules.id, onDelete = ReferenceOption.CASCADE)
    val stageNumber = integer("stage_number")
    val unlockDate = long("unlock_date")
    val encryptedText = text("encrypted_text")

    override val primaryKey = PrimaryKey(id)
}

object CapsuleParticipants : Table("capsule_participants") {
    val capsuleId = varchar("capsule_id", 36).references(Capsules.id, onDelete = ReferenceOption.CASCADE)
    val userId = varchar("user_id", 36).references(Users.id, onDelete = ReferenceOption.CASCADE)
    val lastLat = double("last_lat").nullable()
    val lastLon = double("last_lon").nullable()
    val lastSeenAt = long("last_seen_at").nullable()
    val openedAt = long("opened_at").nullable()
    val archived = bool("archived").default(false)
    val role = varchar("role", 16).default("MEMBER")

    override val primaryKey = PrimaryKey(capsuleId, userId)
}

object Blocks : Table("blocks") {
    val blockerId = varchar("blocker_id", 36).references(Users.id, onDelete = ReferenceOption.CASCADE)
    val blockedUserId = varchar("blocked_user_id", 36).references(Users.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(blockerId, blockedUserId)
}

object CapsuleMedia : Table("capsule_media") {
    val id = varchar("id", 36)
    val capsuleId = varchar("capsule_id", 36).references(Capsules.id, onDelete = ReferenceOption.CASCADE)
    val kind = varchar("kind", 16) // "PHOTO" or "VOICE"
    val mime = varchar("mime", 64) // "image/jpeg" or "audio/mp4"
    val sizeBytes = long("size_bytes")
    val durationMs = long("duration_ms").nullable()
    val encryptedBytes = binary("encrypted_bytes")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
