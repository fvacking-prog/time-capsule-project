package com.timecapsule.server

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.timecapsule.shared.AuthResponse
import com.timecapsule.shared.CapsuleDto
import com.timecapsule.shared.CapsuleStageDto
import com.timecapsule.shared.CheckInRequest
import com.timecapsule.shared.CreateCapsuleRequest
import com.timecapsule.shared.DemoAuthRequest
import com.timecapsule.shared.GoogleAuthRequest
import com.timecapsule.shared.MessageResponse
import com.timecapsule.shared.ParticipantStatusDto
import com.timecapsule.shared.UserDto
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.SecureRandom
import java.util.UUID

private const val SESSION_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000L
private val sessionRandom = SecureRandom()

fun generateUniqueInviteCode(): String {
    val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    while (true) {
        val sb = StringBuilder(6)
        for (i in 0 until 6) {
            sb.append(chars[sessionRandom.nextInt(chars.length)])
        }
        val code = sb.toString()
        val exists = Capsules.selectAll().where { Capsules.inviteCode eq code }.count() > 0L
        if (!exists) return code
    }
}

fun canUserAccessCapsule(capsuleId: String, userId: String): Boolean {
    val isCreator = Capsules
        .selectAll()
        .where { (Capsules.id eq capsuleId) and (Capsules.creatorId eq userId) }
        .count() > 0L
    if (isCreator) return true

    val isParticipant = CapsuleParticipants
        .selectAll()
        .where { (CapsuleParticipants.capsuleId eq capsuleId) and (CapsuleParticipants.userId eq userId) }
        .count() > 0L
    return isParticipant
}

fun getVisibleCapsuleIds(userId: String): List<String> {
    val participantCapsuleIds = CapsuleParticipants
        .selectAll()
        .where { CapsuleParticipants.userId eq userId }
        .map { it[CapsuleParticipants.capsuleId] }

    val creatorCapsuleIds = Capsules
        .selectAll()
        .where { Capsules.creatorId eq userId }
        .map { it[Capsules.id] }

    return (participantCapsuleIds + creatorCapsuleIds).distinct()
}

fun joinCapsuleByInviteCode(
    rawInviteCode: String,
    userId: String,
    now: Long = System.currentTimeMillis()
): CapsuleDto {
    val cleanCode = rawInviteCode.trim()
    if (cleanCode.isEmpty()) {
        throw CapsuleNotFoundException("Код приглашения не может быть пустым")
    }

    val upperCode = cleanCode.uppercase()
    val capsuleRow = Capsules
        .selectAll()
        .where { Capsules.inviteCode eq upperCode }
        .singleOrNull()
        ?: Capsules.selectAll().where { Capsules.inviteCode.isNotNull() }.firstOrNull {
            it[Capsules.inviteCode]?.trim()?.equals(cleanCode, ignoreCase = true) == true
        }
        ?: throw CapsuleNotFoundException("Капсула с кодом приглашения '$cleanCode' не найдена")

    val capsuleId = capsuleRow[Capsules.id]
    val alreadyParticipant = CapsuleParticipants
        .selectAll()
        .where { (CapsuleParticipants.capsuleId eq capsuleId) and (CapsuleParticipants.userId eq userId) }
        .count() > 0L

    if (!alreadyParticipant) {
        CapsuleParticipants.insert {
            it[CapsuleParticipants.capsuleId] = capsuleId
            it[CapsuleParticipants.userId] = userId
            it[CapsuleParticipants.lastLat] = null
            it[CapsuleParticipants.lastLon] = null
            it[CapsuleParticipants.lastSeenAt] = null
            it[CapsuleParticipants.openedAt] = null
            it[CapsuleParticipants.archived] = false
        }
        Capsules.update({ Capsules.id eq capsuleId }) {
            it[isGroupCapsule] = true
        }
    }

    return buildCapsuleDto(capsuleId, now, requestingUserId = userId)
}

fun Route.capsuleRoutes() {
    get("/health") {
        call.respondText("ok")
    }

    route("/api/v1/auth") {
        post("/google") {
            val request = call.receive<GoogleAuthRequest>()
            val rawIdToken = request.idToken.trim()
            if (rawIdToken.isEmpty()) {
                throw IllegalArgumentException("idToken must not be blank")
            }

            val verified = verifyGoogleIdToken(rawIdToken)
            val authResponse = transaction {
                val now = System.currentTimeMillis()
                val existingRow = Users
                    .selectAll()
                    .where { Users.googleSub eq verified.googleSub }
                    .singleOrNull()

                val userId = if (existingRow == null) {
                    val newId = UUID.randomUUID().toString()
                    Users.insert {
                        it[id] = newId
                        it[username] = verified.displayName
                        it[googleSub] = verified.googleSub
                        it[email] = verified.email
                        it[displayName] = verified.displayName
                        it[photoUrl] = verified.photoUrl
                    }
                    newId
                } else {
                    val uid = existingRow[Users.id]
                    Users.update({ Users.id eq uid }) {
                        it[username] = verified.displayName
                        it[email] = verified.email
                        it[displayName] = verified.displayName
                        it[photoUrl] = verified.photoUrl
                    }
                    uid
                }

                val token = createSessionToken(userId, now)
                val userDto = UserDto(
                    id = userId,
                    username = verified.displayName,
                    email = verified.email,
                    photoUrl = verified.photoUrl
                )
                AuthResponse(user = userDto, sessionToken = token)
            }
            call.respond(authResponse)
        }

        // ТОЛЬКО ДЛЯ ДЕМО НА ЗАЩИТЕ ПРОЕКТА
        post("/demo") {
            if (!isDemoModeEnabled()) {
                throw ForbiddenException("Demo mode is disabled")
            }
            val bodyRequest = runCatching { call.receiveNullable<DemoAuthRequest>() }.getOrNull()
            val requestedUserId = (bodyRequest?.userId?.takeIf { it.isNotBlank() }
                ?: call.request.queryParameters["userId"]?.takeIf { it.isNotBlank() })
                ?.trim()
                ?: throw IllegalArgumentException("userId is required for demo login")

            if (requestedUserId != "user_maga" && requestedUserId != "user_zainab") {
                throw IllegalArgumentException("Only demo users (user_maga, user_zainab) are allowed for demo login")
            }

            val authResponse = transaction {
                val userRow = Users
                    .selectAll()
                    .where { Users.id eq requestedUserId }
                    .singleOrNull()
                    ?: throw ParticipantNotFoundException("Demo user '$requestedUserId' not found")

                val now = System.currentTimeMillis()
                val token = createSessionToken(requestedUserId, now)
                AuthResponse(
                    user = userRow.toUserDto(maskUserEmail = false),
                    sessionToken = token
                )
            }
            call.respond(authResponse)
        }
    }

    get("/api/v1/me") {
        val currentUserId = call.requireAuthenticatedUserId()
        val userDto = transaction {
            val row = Users
                .selectAll()
                .where { Users.id eq currentUserId }
                .singleOrNull()
                ?: throw UnauthorizedException("User not found for session")
            row.toUserDto(maskUserEmail = false)
        }
        call.respond(userDto)
    }

    delete("/api/v1/me/data") {
        val authenticatedUserId = call.requireAuthenticatedUserId()
        transaction {
            val ownedCapsuleIds = Capsules
                .selectAll()
                .where { Capsules.creatorId eq authenticatedUserId }
                .map { it[Capsules.id] }

            if (ownedCapsuleIds.isNotEmpty()) {
                CapsuleStages.deleteWhere { capsuleId inList ownedCapsuleIds }
                CapsuleParticipants.deleteWhere { capsuleId inList ownedCapsuleIds }
                Capsules.deleteWhere { id inList ownedCapsuleIds }
            }

            CapsuleParticipants.deleteWhere { userId eq authenticatedUserId }
            Sessions.deleteWhere { userId eq authenticatedUserId }
        }
        call.respond(MessageResponse("Все данные пользователя успешно удалены"))
    }

    get("/api/v1/users") {
        val currentUserId = call.requireAuthenticatedUserId()
        val query = call.request.queryParameters["query"]?.trim().orEmpty()
        val users = transaction {
            val allRows = Users.selectAll().toList()
            allRows
                .filter { row ->
                    if (query.isEmpty()) {
                        true
                    } else {
                        val name = row[Users.displayName] ?: row[Users.username]
                        val username = row[Users.username]
                        val email = row[Users.email].orEmpty()
                        name.contains(query, ignoreCase = true) ||
                            username.contains(query, ignoreCase = true) ||
                            email.contains(query, ignoreCase = true)
                    }
                }
                .take(50)
                .map { row ->
                    val isSelf = row[Users.id] == currentUserId
                    row.toUserDto(maskUserEmail = !isSelf)
                }
        }
        call.respond(users)
    }

    route("/api/v1/capsules") {
        get {
            val currentUserId = call.requireAuthenticatedUserId()
            val capsules = transaction {
                val now = System.currentTimeMillis()
                val visibleIds = getVisibleCapsuleIds(currentUserId)
                visibleIds.map { id -> buildCapsuleDto(id, now, requestingUserId = currentUserId) }
            }
            call.respond(capsules)
        }

        get("/{id}") {
            val currentUserId = call.requireAuthenticatedUserId()
            val capsuleId = call.parameters["id"] ?: throw CapsuleNotFoundException("Missing capsule id")
            val capsule = transaction {
                if (!canUserAccessCapsule(capsuleId, currentUserId)) {
                    throw CapsuleNotFoundException("Capsule '$capsuleId' not found")
                }
                buildCapsuleDto(capsuleId, System.currentTimeMillis(), requestingUserId = currentUserId)
            }
            call.respond(capsule)
        }

        post {
            val authenticatedUserId = call.requireAuthenticatedUserId()
            val request = call.receive<CreateCapsuleRequest>()
            validateCreateCapsuleRequest(request, authenticatedUserId)
            val capsule = transaction {
                val now = System.currentTimeMillis()
                val capsuleId = UUID.randomUUID().toString()
                val allParticipantIds = (listOf(authenticatedUserId) + request.participantIds).distinct()

                for (uid in allParticipantIds) {
                    val userExists = Users.selectAll().where { Users.id eq uid }.count() > 0L
                    if (!userExists) {
                        throw ParticipantNotFoundException("Registered user '$uid' not found")
                    }
                }

                val generatedInviteCode = generateUniqueInviteCode()
                val isGeo = request.isGeoLocked
                val finalKind = if (request.kind.isNotBlank()) request.kind.uppercase() else "MESSAGE"
                val cleanTags = request.tags.map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }.take(5)

                Capsules.insert {
                    it[id] = capsuleId
                    it[creatorId] = authenticatedUserId
                    it[title] = request.title.trim()
                    it[latitude] = if (isGeo) request.latitude else 0.0
                    it[longitude] = if (isGeo) request.longitude else 0.0
                    it[radiusMeters] = if (isGeo) request.radiusMeters else 50.0
                    it[createdAt] = now
                    it[isGroupCapsule] = if (isGeo) (request.isGroupCapsule || allParticipantIds.size > 1) else false
                    it[inviteCode] = generatedInviteCode
                    it[kind] = finalKind
                    it[recipient] = request.recipient?.trim()?.takeIf { it.isNotEmpty() }
                    it[tags] = cleanTags.joinToString(",")
                    it[isGeoLocked] = isGeo
                }

                for (participantId in allParticipantIds) {
                    CapsuleParticipants.insert {
                        it[CapsuleParticipants.capsuleId] = capsuleId
                        it[userId] = participantId
                        it[lastLat] = null
                        it[lastLon] = null
                        it[lastSeenAt] = null
                        it[openedAt] = null
                        it[archived] = false
                    }
                }

                for (stage in request.stages) {
                    CapsuleStages.insert {
                        it[id] = UUID.randomUUID().toString()
                        it[CapsuleStages.capsuleId] = capsuleId
                        it[stageNumber] = stage.stageNumber
                        it[unlockDate] = stage.unlockDate
                        it[encryptedText] = CryptoUtils.encrypt(stage.secretText)
                    }
                }

                buildCapsuleDto(capsuleId, now, requestingUserId = authenticatedUserId)
            }
            call.respond(capsule)
        }

        post("/join") {
            val authenticatedUserId = call.requireAuthenticatedUserId()
            val request = call.receive<com.timecapsule.shared.JoinCapsuleRequest>()
            val capsule = transaction {
                joinCapsuleByInviteCode(
                    rawInviteCode = request.inviteCode,
                    userId = authenticatedUserId
                )
            }
            call.respond(capsule)
        }

        post("/{id}/check-in") {
            val authenticatedUserId = call.requireAuthenticatedUserId()
            val capsuleId = call.parameters["id"] ?: throw CapsuleNotFoundException("Missing capsule id")
            val request = call.receive<CheckInRequest>()
            validateCheckInRequest(request, authenticatedUserId)
            val capsule = transaction {
                if (!canUserAccessCapsule(capsuleId, authenticatedUserId)) {
                    throw CapsuleNotFoundException("Capsule '$capsuleId' not found")
                }

                val now = System.currentTimeMillis()
                val updated = CapsuleParticipants.update({
                    (CapsuleParticipants.capsuleId eq capsuleId) and (CapsuleParticipants.userId eq authenticatedUserId)
                }) {
                    it[lastLat] = request.lat
                    it[lastLon] = request.lon
                    it[lastSeenAt] = now
                }

                if (updated == 0) {
                    throw ParticipantNotFoundException("Participant '$authenticatedUserId' not found in capsule '$capsuleId'")
                }

                buildCapsuleDto(capsuleId, now, requestingUserId = authenticatedUserId)
            }
            call.respond(capsule)
        }

        // ТОЛЬКО ДЛЯ ДЕМО НА ЗАЩИТЕ ПРОЕКТА
        post("/{id}/demo-force-checkin") {
            val authenticatedUserId = call.requireAuthenticatedUserId()
            if (!isDemoModeEnabled()) {
                throw ForbiddenException("Demo mode is disabled")
            }
            val capsuleId = call.parameters["id"] ?: throw CapsuleNotFoundException("Missing capsule id")
            val capsule = transaction {
                if (!canUserAccessCapsule(capsuleId, authenticatedUserId)) {
                    throw CapsuleNotFoundException("Capsule '$capsuleId' not found")
                }

                val capsuleRow = Capsules
                    .selectAll()
                    .where { Capsules.id eq capsuleId }
                    .singleOrNull()
                    ?: throw CapsuleNotFoundException("Capsule '$capsuleId' not found")

                val centerLat = capsuleRow[Capsules.latitude]
                val centerLon = capsuleRow[Capsules.longitude]
                val now = System.currentTimeMillis()

                CapsuleParticipants.update({ CapsuleParticipants.capsuleId eq capsuleId }) {
                    it[lastLat] = centerLat
                    it[lastLon] = centerLon
                    it[lastSeenAt] = now
                }

                buildCapsuleDto(capsuleId, now, requestingUserId = authenticatedUserId)
            }
            call.respond(capsule)
        }

        post("/{id}/open") {
            val authenticatedUserId = call.requireAuthenticatedUserId()
            val capsuleId = call.parameters["id"] ?: throw CapsuleNotFoundException("Missing capsule id")
            val capsule = transaction {
                if (!canUserAccessCapsule(capsuleId, authenticatedUserId)) {
                    throw CapsuleNotFoundException("Capsule '$capsuleId' not found")
                }
                val now = System.currentTimeMillis()
                val currentDto = buildCapsuleDto(capsuleId, now, requestingUserId = authenticatedUserId)
                if (!currentDto.isFullyUnlocked) {
                    throw ConflictException("Капсула ещё не разблокирована. Открытие невозможно до наступления условий.")
                }

                CapsuleParticipants.update({
                    (CapsuleParticipants.capsuleId eq capsuleId) and (CapsuleParticipants.userId eq authenticatedUserId)
                }) {
                    it[openedAt] = now
                }

                buildCapsuleDto(capsuleId, now, requestingUserId = authenticatedUserId)
            }
            call.respond(capsule)
        }

        post("/{id}/archive") {
            val authenticatedUserId = call.requireAuthenticatedUserId()
            val capsuleId = call.parameters["id"] ?: throw CapsuleNotFoundException("Missing capsule id")
            val capsule = transaction {
                if (!canUserAccessCapsule(capsuleId, authenticatedUserId)) {
                    throw CapsuleNotFoundException("Capsule '$capsuleId' not found")
                }
                CapsuleParticipants.update({
                    (CapsuleParticipants.capsuleId eq capsuleId) and (CapsuleParticipants.userId eq authenticatedUserId)
                }) {
                    it[archived] = true
                }
                buildCapsuleDto(capsuleId, System.currentTimeMillis(), requestingUserId = authenticatedUserId)
            }
            call.respond(capsule)
        }

        post("/{id}/unarchive") {
            val authenticatedUserId = call.requireAuthenticatedUserId()
            val capsuleId = call.parameters["id"] ?: throw CapsuleNotFoundException("Missing capsule id")
            val capsule = transaction {
                if (!canUserAccessCapsule(capsuleId, authenticatedUserId)) {
                    throw CapsuleNotFoundException("Capsule '$capsuleId' not found")
                }
                CapsuleParticipants.update({
                    (CapsuleParticipants.capsuleId eq capsuleId) and (CapsuleParticipants.userId eq authenticatedUserId)
                }) {
                    it[archived] = false
                }
                buildCapsuleDto(capsuleId, System.currentTimeMillis(), requestingUserId = authenticatedUserId)
            }
            call.respond(capsule)
        }
    }
}

private data class VerifiedGoogleProfile(
    val googleSub: String,
    val email: String,
    val displayName: String,
    val photoUrl: String?
)

private val googleTransport = NetHttpTransport()
private val googleJsonFactory = GsonFactory.getDefaultInstance()

private fun verifyGoogleIdToken(idTokenString: String): VerifiedGoogleProfile {
    val webClientId = (System.getenv("GOOGLE_WEB_CLIENT_ID") ?: System.getProperty("GOOGLE_WEB_CLIENT_ID"))
        ?.trim()
        .orEmpty()
    if (webClientId.isEmpty()) {
        throw UnauthorizedException("GOOGLE_WEB_CLIENT_ID is not configured on the server")
    }

    val verifier = GoogleIdTokenVerifier.Builder(googleTransport, googleJsonFactory)
        .setAudience(listOf(webClientId))
        .setIssuers(listOf("https://accounts.google.com", "accounts.google.com"))
        .build()

    val googleIdToken = runCatching { verifier.verify(idTokenString) }.getOrNull()
        ?: throw UnauthorizedException("Invalid or expired Google ID token")

    val payload = googleIdToken.payload
    if (!googleIdToken.verifyAudience(listOf(webClientId))) {
        throw UnauthorizedException("Google ID token audience mismatch")
    }
    if (!googleIdToken.verifyIssuer(listOf("https://accounts.google.com", "accounts.google.com"))) {
        throw UnauthorizedException("Google ID token issuer mismatch")
    }
    if (payload.emailVerified != true) {
        throw UnauthorizedException("Google account email is not verified")
    }

    val sub = payload.subject?.trim().orEmpty()
    val email = payload.email?.trim().orEmpty()
    if (sub.isEmpty() || email.isEmpty()) {
        throw UnauthorizedException("Google ID token is missing subject or email")
    }

    val displayName = (payload["name"] as? String)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: email.substringBefore('@').ifEmpty { "Google User" }

    val photoUrl = (payload["picture"] as? String)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    return VerifiedGoogleProfile(
        googleSub = sub,
        email = email,
        displayName = displayName.take(128),
        photoUrl = photoUrl?.take(512)
    )
}

internal fun createSessionToken(userId: String, now: Long = System.currentTimeMillis()): String {
    val bytes = ByteArray(32)
    sessionRandom.nextBytes(bytes)
    val token = bytes.joinToString("") { "%02x".format(it) }
    Sessions.insert {
        it[Sessions.token] = token
        it[Sessions.userId] = userId
        it[Sessions.createdAt] = now
        it[Sessions.expiresAt] = now + SESSION_TTL_MILLIS
    }
    return token
}

internal fun ApplicationCall.requireAuthenticatedUserId(): String {
    val authHeader = request.header(HttpHeaders.Authorization)?.trim().orEmpty()
    if (!authHeader.startsWith("Bearer ", ignoreCase = true)) {
        throw UnauthorizedException("Missing or invalid Authorization Bearer header")
    }
    val token = authHeader.substringAfter(' ').trim()
    if (token.isEmpty() || token.length > 64) {
        throw UnauthorizedException("Invalid session token")
    }

    val now = System.currentTimeMillis()
    return transaction {
        val sessionRow = Sessions
            .selectAll()
            .where { Sessions.token eq token }
            .singleOrNull()
            ?: throw UnauthorizedException("Invalid session token")

        val expiresAt = sessionRow[Sessions.expiresAt]
        if (now > expiresAt) {
            throw UnauthorizedException("Session token has expired")
        }

        sessionRow[Sessions.userId]
    }
}

internal fun maskEmail(email: String?): String? {
    val trimmed = email?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val atIndex = trimmed.indexOf('@')
    if (atIndex <= 0 || atIndex == trimmed.lastIndex) {
        return "***"
    }
    val local = trimmed.substring(0, atIndex)
    val domain = trimmed.substring(atIndex + 1)
    val maskedLocal = if (local.length <= 2) {
        "${local.first()}***"
    } else {
        "${local.first()}***${local.last()}"
    }
    return "$maskedLocal@$domain"
}

private fun ResultRow.toUserDto(maskUserEmail: Boolean): UserDto {
    val rawEmail = this[Users.email]
    val display = this[Users.displayName]?.takeIf { it.isNotBlank() } ?: this[Users.username]
    return UserDto(
        id = this[Users.id],
        username = display,
        email = if (maskUserEmail) maskEmail(rawEmail) else rawEmail,
        photoUrl = this[Users.photoUrl]
    )
}

private const val MAX_ID_LENGTH = 36
private const val MAX_TITLE_LENGTH = 255
private const val MAX_STAGE_TEXT_LENGTH = 5000
private const val MAX_STAGES_COUNT = 50
private const val MAX_PARTICIPANTS_COUNT = 100
private const val MAX_RADIUS_METERS = 100_000.0

private fun isDemoModeEnabled(): Boolean {
    val flag = System.getenv("DEMO_MODE") ?: System.getProperty("DEMO_MODE")
    return flag?.equals("true", ignoreCase = true) == true
}

private fun validateCreateCapsuleRequest(request: CreateCapsuleRequest, authenticatedUserId: String) {
    require(authenticatedUserId.isNotBlank() && authenticatedUserId.length <= MAX_ID_LENGTH) {
        "Invalid creatorId"
    }
    val trimmedTitle = request.title.trim()
    require(trimmedTitle.isNotEmpty() && trimmedTitle.length <= MAX_TITLE_LENGTH) {
        "Title must be between 1 and $MAX_TITLE_LENGTH characters"
    }
    if (request.isGeoLocked) {
        require(GeoUtils.isValidCoordinate(request.latitude, request.longitude)) {
            "Invalid capsule coordinates"
        }
        require(request.radiusMeters.isFinite() && request.radiusMeters in 0.1..MAX_RADIUS_METERS) {
            "radiusMeters must be between 0.1 and $MAX_RADIUS_METERS"
        }
    }
    require(
        request.participantIds.size <= MAX_PARTICIPANTS_COUNT &&
            request.participantIds.all { it.isNotBlank() && it.length <= MAX_ID_LENGTH }
    ) {
        "Invalid participantIds"
    }
    require(request.stages.isNotEmpty() && request.stages.size <= MAX_STAGES_COUNT) {
        "Stages count must be between 1 and $MAX_STAGES_COUNT"
    }
    val stageNumbers = request.stages.map { it.stageNumber }
    require(stageNumbers.all { it > 0 } && stageNumbers.distinct().size == stageNumbers.size) {
        "Stage numbers must be positive and unique"
    }
    val now = System.currentTimeMillis()
    val minUnlockDate = if (isDemoModeEnabled()) now - 10_000L else now + 50_000L
    require(request.stages.all { it.unlockDate >= minUnlockDate }) {
        "Дата открытия должна быть в будущем (минимум +1 минута)"
    }
    require(request.stages.all { it.secretText.isNotBlank() && it.secretText.length <= MAX_STAGE_TEXT_LENGTH }) {
        "Stage secretText must be between 1 and $MAX_STAGE_TEXT_LENGTH characters"
    }
    require(request.recipient == null || request.recipient.length <= 255) {
        "Recipient must not exceed 255 characters"
    }
    require(request.tags.size <= 5) {
        "Tags count must be at most 5"
    }
    require(request.tags.all { it.trim().length in 1..30 }) {
        "Each tag must be between 1 and 30 characters"
    }
}

private fun validateCheckInRequest(request: CheckInRequest, authenticatedUserId: String) {
    if (request.userId.isNotBlank() && request.userId != authenticatedUserId) {
        throw ForbiddenException("Cannot check in on behalf of another user")
    }
    require(GeoUtils.isValidCoordinate(request.lat, request.lon)) {
        "Invalid check-in coordinates"
    }
}

fun buildCapsuleDto(
    capsuleId: String,
    now: Long = System.currentTimeMillis(),
    requestingUserId: String? = null
): CapsuleDto {
    val capsuleRow = Capsules
        .selectAll()
        .where { Capsules.id eq capsuleId }
        .singleOrNull()
        ?: throw CapsuleNotFoundException("Capsule '$capsuleId' not found")

    val capsuleLat = capsuleRow[Capsules.latitude]
    val capsuleLon = capsuleRow[Capsules.longitude]
    val radiusMeters = capsuleRow[Capsules.radiusMeters]
    val isGeoLocked = capsuleRow[Capsules.isGeoLocked]
    val kind = capsuleRow[Capsules.kind]
    val recipient = capsuleRow[Capsules.recipient]
    val rawTags = capsuleRow[Capsules.tags]
    val tags = if (rawTags.isBlank()) emptyList() else rawTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val participants = (CapsuleParticipants innerJoin Users)
        .selectAll()
        .where { CapsuleParticipants.capsuleId eq capsuleId }
        .map { row ->
            val lastLat = row[CapsuleParticipants.lastLat]
            val lastLon = row[CapsuleParticipants.lastLon]
            val lastSeenAt = row[CapsuleParticipants.lastSeenAt]

            val distanceMeters = if (lastLat != null && lastLon != null) {
                GeoUtils.haversineMeters(lastLat, lastLon, capsuleLat, capsuleLon)
            } else {
                null
            }

            val isNearbyAndRecent = if (!isGeoLocked) {
                true
            } else {
                GeoUtils.isNearbyAndRecent(
                    lastLat = lastLat,
                    lastLon = lastLon,
                    lastSeenAt = lastSeenAt,
                    capsuleLat = capsuleLat,
                    capsuleLon = capsuleLon,
                    radiusMeters = radiusMeters,
                    now = now
                )
            }

            ParticipantStatusDto(
                userId = row[CapsuleParticipants.userId],
                username = row[Users.displayName]?.takeIf { it.isNotBlank() } ?: row[Users.username],
                distanceMeters = distanceMeters,
                lastSeenAt = lastSeenAt,
                isNearbyAndRecent = isNearbyAndRecent,
                openedAt = row[CapsuleParticipants.openedAt],
                archived = row[CapsuleParticipants.archived]
            )
        }

    val allParticipantsNearbyAndRecent = if (!isGeoLocked) {
        true
    } else {
        participants.isNotEmpty() && participants.all { it.isNearbyAndRecent }
    }

    val stages = CapsuleStages
        .selectAll()
        .where { CapsuleStages.capsuleId eq capsuleId }
        .orderBy(CapsuleStages.stageNumber to SortOrder.ASC)
        .map { row ->
            val unlockDate = row[CapsuleStages.unlockDate]
            val encryptedText = row[CapsuleStages.encryptedText]
            val isUnlocked = now >= unlockDate && allParticipantsNearbyAndRecent
            val text = GeoUtils.stageText(
                encryptedText = encryptedText,
                unlockDate = unlockDate,
                now = now,
                allParticipantsNearbyAndRecent = allParticipantsNearbyAndRecent
            )

            CapsuleStageDto(
                id = row[CapsuleStages.id],
                stageNumber = row[CapsuleStages.stageNumber],
                unlockDate = unlockDate,
                text = text,
                isUnlocked = isUnlocked
            )
        }

    val isFullyUnlocked = if (!isGeoLocked) {
        stages.isNotEmpty() && stages.any { now >= it.unlockDate }
    } else {
        allParticipantsNearbyAndRecent
    }

    val isMember = requestingUserId != null && (
        capsuleRow[Capsules.creatorId] == requestingUserId ||
        participants.any { it.userId == requestingUserId }
    )
    val inviteCode = if (requestingUserId == null || isMember) {
        capsuleRow[Capsules.inviteCode]
    } else {
        null
    }

    val myParticipant = if (requestingUserId != null) {
        participants.firstOrNull { it.userId == requestingUserId }
    } else {
        null
    }
    val myOpenedAt = myParticipant?.openedAt
    val myArchived = myParticipant?.archived ?: false

    val status = when {
        myArchived -> "ARCHIVED"
        myOpenedAt != null -> "OPENED"
        isFullyUnlocked -> "READY"
        else -> "SEALED"
    }

    return CapsuleDto(
        id = capsuleRow[Capsules.id],
        title = capsuleRow[Capsules.title],
        latitude = capsuleLat,
        longitude = capsuleLon,
        radiusMeters = radiusMeters,
        isGroupCapsule = capsuleRow[Capsules.isGroupCapsule],
        isFullyUnlocked = isFullyUnlocked,
        stages = stages,
        participants = participants,
        creatorId = capsuleRow[Capsules.creatorId],
        inviteCode = inviteCode,
        kind = kind,
        recipient = recipient,
        tags = tags,
        isGeoLocked = isGeoLocked,
        status = status,
        openedAt = myOpenedAt,
        archived = myArchived
    )
}
