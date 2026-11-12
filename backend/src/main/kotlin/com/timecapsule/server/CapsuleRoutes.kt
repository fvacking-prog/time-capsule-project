package com.timecapsule.server

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.timecapsule.shared.AppVersionDto
import com.timecapsule.shared.AuthResponse
import com.timecapsule.shared.BlockUserRequest
import com.timecapsule.shared.CapsuleDto
import com.timecapsule.shared.CapsuleStageDto
import com.timecapsule.shared.CheckHandleResponse
import com.timecapsule.shared.CheckInRequest
import com.timecapsule.shared.CreateCapsuleRequest
import com.timecapsule.shared.GoogleAuthRequest
import com.timecapsule.shared.MessageResponse
import com.timecapsule.shared.ParticipantStatusDto
import com.timecapsule.shared.UpdateHandleRequest
import com.timecapsule.shared.UserDto
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
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

internal fun isUserBlockedBy(blockerId: String, blockedUserId: String): Boolean {
    return Blocks
        .selectAll()
        .where { (Blocks.blockerId eq blockerId) and (Blocks.blockedUserId eq blockedUserId) }
        .count() > 0L
}

internal fun getBlockedUserIdsFor(blockerId: String): Set<String> {
    return Blocks
        .selectAll()
        .where { Blocks.blockerId eq blockerId }
        .map { it[Blocks.blockedUserId] }
        .toSet()
}

fun canUserAccessCapsule(capsuleId: String, userId: String): Boolean {
    val capsuleRow = Capsules
        .selectAll()
        .where { Capsules.id eq capsuleId }
        .singleOrNull()
        ?: return false

    val creatorId = capsuleRow[Capsules.creatorId]
    if (creatorId == userId) return true

    if (isUserBlockedBy(blockerId = userId, blockedUserId = creatorId)) {
        return false
    }

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

    val candidateIds = (participantCapsuleIds + creatorCapsuleIds).distinct()
    if (candidateIds.isEmpty()) return emptyList()

    val blockedCreators = getBlockedUserIdsFor(userId)
    if (blockedCreators.isEmpty()) return candidateIds

    return Capsules
        .selectAll()
        .where {
            (Capsules.id inList candidateIds) and
                ((Capsules.creatorId eq userId) or (Capsules.creatorId notInList blockedCreators))
        }
        .map { it[Capsules.id] }
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

    val creatorId = capsuleRow[Capsules.creatorId]
    if (creatorId != userId && isUserBlockedBy(blockerId = userId, blockedUserId = creatorId)) {
        throw CapsuleNotFoundException("Капсула с кодом приглашения '$cleanCode' не найдена")
    }

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
            it[CapsuleParticipants.role] = "MEMBER"
        }
        Capsules.update({ Capsules.id eq capsuleId }) {
            it[isGroupCapsule] = true
        }
    }

    return buildCapsuleDto(capsuleId, now, requestingUserId = userId)
}

object AuthRateLimiter {
    private val attempts = java.util.concurrent.ConcurrentHashMap<String, MutableList<Long>>()
    private const val MAX_ATTEMPTS = 20
    private const val WINDOW_MILLIS = 60_000L

    fun checkOrThrow(clientIp: String, route: String = "auth") {
        val key = "$clientIp:$route"
        val now = System.currentTimeMillis()
        val list = attempts.computeIfAbsent(key) { java.util.Collections.synchronizedList(mutableListOf()) }
        synchronized(list) {
            list.removeIf { now - it > WINDOW_MILLIS }
            if (list.size >= MAX_ATTEMPTS) {
                throw TooManyRequestsException("Слишком много попыток авторизации. Повторите через 60 секунд.")
            }
            list.add(now)
        }
    }

    fun clear() {
        attempts.clear()
    }
}

internal var healthCheckFn: () -> Boolean = DatabaseConfig::isDatabaseHealthy
internal var appVersionProviderFn: () -> AppVersionDto = { AppVersionConfig.fromEnv() }

fun Route.capsuleRoutes() {
    get("/health") {
        if (healthCheckFn()) {
            call.respondText("ok", status = io.ktor.http.HttpStatusCode.OK)
        } else {
            call.respondText("database_unavailable", status = io.ktor.http.HttpStatusCode.ServiceUnavailable)
        }
    }

    get("/api/v1/app/version") {
        call.respond(appVersionProviderFn())
    }

    route("/api/v1/auth") {
        post("/google") {
            val clientIp = call.request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
                ?: call.request.local.remoteHost
            AuthRateLimiter.checkOrThrow(clientIp, "google")

            val request = call.receive<GoogleAuthRequest>()
            val rawIdToken = request.idToken.trim()
            if (rawIdToken.isEmpty()) {
                throw IllegalArgumentException("idToken must not be blank")
            }

            val verified = googleTokenVerifierFn(rawIdToken)
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
                        it[handle] = null
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
                val userDto = Users
                    .selectAll()
                    .where { Users.id eq userId }
                    .single()
                    .toUserDto(includeEmail = true)
                AuthResponse(user = userDto, sessionToken = token)
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
            row.toUserDto(includeEmail = true)
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
            Blocks.deleteWhere { (blockerId eq authenticatedUserId) or (blockedUserId eq authenticatedUserId) }
            Sessions.deleteWhere { userId eq authenticatedUserId }
        }
        call.respond(MessageResponse("Все данные пользователя успешно удалены"))
    }

    route("/api/v1/blocks") {
        get {
            val currentUserId = call.requireAuthenticatedUserId()
            val blockedUsers = transaction {
                (Blocks innerJoin Users)
                    .selectAll()
                    .where { Blocks.blockerId eq currentUserId }
                    .orderBy(Blocks.createdAt to SortOrder.DESC)
                    .map { it.toUserDto(includeEmail = false) }
            }
            call.respond(blockedUsers)
        }

        post {
            val currentUserId = call.requireAuthenticatedUserId()
            val request = call.receive<BlockUserRequest>()
            val targetUserId = request.userId.trim()
            require(targetUserId.isNotEmpty() && targetUserId.length <= MAX_ID_LENGTH) {
                "Некорректный идентификатор пользователя"
            }
            require(targetUserId != currentUserId) {
                "Нельзя заблокировать самого себя"
            }

            transaction {
                val targetExists = Users.selectAll().where { Users.id eq targetUserId }.count() > 0L
                if (!targetExists) {
                    throw ParticipantNotFoundException("Пользователь '$targetUserId' не найден")
                }
                val alreadyBlocked = Blocks
                    .selectAll()
                    .where { (Blocks.blockerId eq currentUserId) and (Blocks.blockedUserId eq targetUserId) }
                    .count() > 0L
                if (!alreadyBlocked) {
                    Blocks.insert {
                        it[blockerId] = currentUserId
                        it[blockedUserId] = targetUserId
                        it[createdAt] = System.currentTimeMillis()
                    }
                }
            }
            call.respond(MessageResponse("Отправитель заблокирован"))
        }

        delete("/{userId}") {
            val currentUserId = call.requireAuthenticatedUserId()
            val targetUserId = call.parameters["userId"]?.trim().orEmpty()
            require(targetUserId.isNotEmpty()) { "Не указан userId" }
            transaction {
                Blocks.deleteWhere {
                    (Blocks.blockerId eq currentUserId) and (Blocks.blockedUserId eq targetUserId)
                }
            }
            call.respond(MessageResponse("Пользователь разблокирован"))
        }
    }

    route("/api/v1/users") {
        get("/check-handle") {
            val currentUserId = call.requireAuthenticatedUserId()
            val rawHandle = call.request.queryParameters["handle"].orEmpty()
            val response = checkHandleAvailability(rawHandle, currentUserId)
            call.respond(response)
        }

        put("/me/handle") {
            val currentUserId = call.requireAuthenticatedUserId()
            val request = call.receive<UpdateHandleRequest>()
            val updated = updateUserHandle(currentUserId, request.handle)
            call.respond(updated)
        }

        get {
            val currentUserId = call.requireAuthenticatedUserId()
            val query = call.request.queryParameters["query"].orEmpty()
            val users = searchUsersByQuery(currentUserId, query)
            call.respond(users)
        }
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

                val resolvedHandleRecipients = request.recipientHandles
                    .map { HandleUtils.normalizeHandle(it) }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .map { normalizedHandle ->
                        val userRow = Users
                            .selectAll()
                            .where { Users.handle eq normalizedHandle }
                            .singleOrNull()
                            ?: throw ParticipantNotFoundException("Пользователь @$normalizedHandle не найден")
                        userRow[Users.id]
                    }

                val targetRecipientIds = (resolvedHandleRecipients + request.participantIds)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it != authenticatedUserId }
                    .distinct()

                if (!request.isGeoLocked && targetRecipientIds.size > MAX_PERSONAL_RECIPIENTS) {
                    throw IllegalArgumentException(
                        "Личная капсула может иметь не более $MAX_PERSONAL_RECIPIENTS получателей"
                    )
                }

                val creatorExists = Users.selectAll().where { Users.id eq authenticatedUserId }.count() > 0L
                if (!creatorExists) {
                    throw ParticipantNotFoundException("Registered user '$authenticatedUserId' not found")
                }

                val recipientUsersById = if (targetRecipientIds.isEmpty()) {
                    emptyMap()
                } else {
                    Users.selectAll()
                        .where { Users.id inList targetRecipientIds }
                        .associateBy { it[Users.id] }
                }

                for (uid in targetRecipientIds) {
                    if (!recipientUsersById.containsKey(uid)) {
                        throw ParticipantNotFoundException("Registered user '$uid' not found")
                    }
                }

                if (targetRecipientIds.isNotEmpty()) {
                    val hasBlockedSender = Blocks
                        .selectAll()
                        .where {
                            (Blocks.blockerId inList targetRecipientIds) and
                                (Blocks.blockedUserId eq authenticatedUserId)
                        }
                        .count() > 0L
                    if (hasBlockedSender) {
                        throw ForbiddenException("Невозможно отправить капсулу: получатель заблокировал вас")
                    }

                    val dayAgo = now - ONE_DAY_MILLIS
                    val sentLast24h = (Capsules innerJoin CapsuleParticipants)
                        .select(Capsules.id)
                        .where {
                            (Capsules.creatorId eq authenticatedUserId) and
                                (Capsules.createdAt greaterEq dayAgo) and
                                (CapsuleParticipants.userId neq authenticatedUserId)
                        }
                        .withDistinct()
                        .count()
                    if (sentLast24h >= MAX_DAILY_SENT_CAPSULES) {
                        throw TooManyRequestsException(
                            "Превышен суточный лимит отправки капсул (не более $MAX_DAILY_SENT_CAPSULES в сутки). Попробуйте позже."
                        )
                    }
                }

                val generatedInviteCode = generateUniqueInviteCode()
                val isGeo = request.isGeoLocked
                val finalKind = if (request.kind.isNotBlank()) request.kind.uppercase() else "MESSAGE"
                val cleanTags = request.tags.map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }.take(5)
                val derivedRecipientLabel = request.recipient?.trim()?.takeIf { it.isNotEmpty() }
                    ?: if (targetRecipientIds.isNotEmpty()) {
                        targetRecipientIds.mapNotNull { uid ->
                            val row = recipientUsersById[uid] ?: return@mapNotNull null
                            val h = row[Users.handle]
                            if (!h.isNullOrBlank()) "@$h" else row[Users.displayName]?.takeIf { it.isNotBlank() } ?: row[Users.username]
                        }.joinToString(", ").take(255).ifBlank { null }
                    } else {
                        null
                    }

                Capsules.insert {
                    it[id] = capsuleId
                    it[creatorId] = authenticatedUserId
                    it[title] = request.title.trim()
                    it[latitude] = if (isGeo) request.latitude else 0.0
                    it[longitude] = if (isGeo) request.longitude else 0.0
                    it[radiusMeters] = if (isGeo) request.radiusMeters else 50.0
                    it[createdAt] = now
                    it[isGroupCapsule] = if (isGeo) (request.isGroupCapsule || targetRecipientIds.isNotEmpty()) else false
                    it[inviteCode] = generatedInviteCode
                    it[kind] = finalKind
                    it[recipient] = derivedRecipientLabel
                    it[tags] = cleanTags.joinToString(",")
                    it[isGeoLocked] = isGeo
                }

                CapsuleParticipants.insert {
                    it[CapsuleParticipants.capsuleId] = capsuleId
                    it[userId] = authenticatedUserId
                    it[lastLat] = null
                    it[lastLon] = null
                    it[lastSeenAt] = null
                    it[openedAt] = null
                    it[archived] = false
                    it[role] = "CREATOR"
                }

                val targetRole = if (isGeo) "MEMBER" else "RECIPIENT"
                for (participantId in targetRecipientIds) {
                    CapsuleParticipants.insert {
                        it[CapsuleParticipants.capsuleId] = capsuleId
                        it[userId] = participantId
                        it[lastLat] = null
                        it[lastLon] = null
                        it[lastSeenAt] = null
                        it[openedAt] = null
                        it[archived] = false
                        it[role] = targetRole
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
                    (CapsuleParticipants.capsuleId eq capsuleId) and
                        (CapsuleParticipants.userId eq authenticatedUserId) and
                        CapsuleParticipants.openedAt.isNull()
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

internal data class VerifiedGoogleProfile(
    val googleSub: String,
    val email: String,
    val displayName: String,
    val photoUrl: String?
)

private val googleTransport = NetHttpTransport()
private val googleJsonFactory = GsonFactory.getDefaultInstance()

internal var googleTokenVerifierFn: (String) -> VerifiedGoogleProfile = ::verifyGoogleIdToken

internal fun verifyGoogleIdToken(idTokenString: String): VerifiedGoogleProfile {
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
            Sessions.deleteWhere { Sessions.token eq token }
            throw UnauthorizedException("Session token has expired")
        }

        sessionRow[Sessions.userId]
    }
}

internal fun ResultRow.toUserDto(includeEmail: Boolean): UserDto {
    val rawEmail = this[Users.email]
    val display = this[Users.displayName]?.takeIf { it.isNotBlank() } ?: this[Users.username]
    val photo = this[Users.photoUrl]
    return UserDto(
        id = this[Users.id],
        username = display,
        email = if (includeEmail) rawEmail else null,
        photoUrl = photo,
        handle = this[Users.handle],
        avatarUrl = photo
    )
}

internal fun checkHandleAvailability(rawHandle: String, currentUserId: String? = null): CheckHandleResponse {
    val normalized = HandleUtils.normalizeHandle(rawHandle)
    when (HandleUtils.validate(normalized)) {
        HandleValidationStatus.INVALID_FORMAT -> {
            return CheckHandleResponse(
                handle = normalized,
                valid = false,
                available = false,
                reason = "INVALID_FORMAT"
            )
        }
        HandleValidationStatus.RESERVED -> {
            return CheckHandleResponse(
                handle = normalized,
                valid = false,
                available = false,
                reason = "RESERVED"
            )
        }
        HandleValidationStatus.OK -> Unit
    }

    val takenByOther = transaction {
        if (currentUserId != null) {
            Users.selectAll()
                .where { (Users.handle eq normalized) and (Users.id neq currentUserId) }
                .count() > 0L
        } else {
            Users.selectAll()
                .where { Users.handle eq normalized }
                .count() > 0L
        }
    }

    return CheckHandleResponse(
        handle = normalized,
        valid = true,
        available = !takenByOther,
        reason = if (takenByOther) "TAKEN" else "OK"
    )
}

internal fun updateUserHandle(userId: String, rawHandle: String): UserDto {
    val normalized = HandleUtils.normalizeHandle(rawHandle)
    when (HandleUtils.validate(normalized)) {
        HandleValidationStatus.INVALID_FORMAT ->
            throw IllegalArgumentException("Никнейм должен содержать 4–24 символа: строчные латинские буквы, цифры или _")
        HandleValidationStatus.RESERVED ->
            throw IllegalArgumentException("Никнейм @$normalized зарезервирован")
        HandleValidationStatus.OK -> Unit
    }

    try {
        return transaction {
            val takenByOther = Users.selectAll()
                .where { (Users.handle eq normalized) and (Users.id neq userId) }
                .count() > 0L
            if (takenByOther) {
                throw ConflictException("Никнейм @$normalized уже занят")
            }

            val updatedCount = Users.update({ Users.id eq userId }) {
                it[handle] = normalized
            }
            if (updatedCount == 0) {
                throw UnauthorizedException("User not found for session")
            }

            Users.selectAll()
                .where { Users.id eq userId }
                .single()
                .toUserDto(includeEmail = true)
        }
    } catch (e: ConflictException) {
        throw e
    } catch (e: Exception) {
        if (HandleUtils.isUniqueConstraintViolation(e)) {
            throw ConflictException("Никнейм @$normalized уже занят")
        }
        throw e
    }
}

internal fun searchUsersByQuery(currentUserId: String, rawQuery: String): List<UserDto> {
    val trimmed = rawQuery.trim()
    val isHandleSearch = trimmed.startsWith("@")
    val cleanQuery = trimmed.removePrefix("@").trim()
    val cleanLower = cleanQuery.lowercase()

    return transaction {
        Users.selectAll()
            .where { (Users.id neq currentUserId) and Users.handle.isNotNull() }
            .toList()
            .filter { row ->
                val userHandle = row[Users.handle]?.trim()?.takeIf { it.isNotEmpty() } ?: return@filter false
                if (cleanQuery.isEmpty()) {
                    true
                } else if (isHandleSearch) {
                    userHandle.startsWith(cleanLower)
                } else {
                    val displayName = row[Users.displayName]?.trim().orEmpty()
                    val username = row[Users.username].trim()
                    userHandle.startsWith(cleanLower) ||
                        displayName.contains(cleanQuery, ignoreCase = true) ||
                        username.contains(cleanQuery, ignoreCase = true)
                }
            }
            .sortedBy { it[Users.handle] }
            .take(20)
            .map { row -> row.toUserDto(includeEmail = false) }
    }
}

private const val MAX_ID_LENGTH = 36
private const val MAX_TITLE_LENGTH = 255
private const val MAX_STAGE_TEXT_LENGTH = 5000
private const val MAX_STAGES_COUNT = 50
private const val MAX_PARTICIPANTS_COUNT = 100
private const val MAX_PERSONAL_RECIPIENTS = 10
private const val MAX_DAILY_SENT_CAPSULES = 30L
private const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L
private const val MIN_GEO_RADIUS_METERS = 20.0
private const val MAX_GEO_RADIUS_METERS = 500.0

internal fun validateCreateCapsuleRequest(request: CreateCapsuleRequest, authenticatedUserId: String) {
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
        require(request.radiusMeters.isFinite() && request.radiusMeters in MIN_GEO_RADIUS_METERS..MAX_GEO_RADIUS_METERS) {
            "Радиус гео-капсулы должен быть в диапазоне от 20 до 500 метров"
        }
    }
    require(
        request.participantIds.size <= MAX_PARTICIPANTS_COUNT &&
            request.participantIds.all { it.isNotBlank() && it.length <= MAX_ID_LENGTH }
    ) {
        "Invalid participantIds"
    }
    require(
        request.recipientHandles.size <= MAX_PERSONAL_RECIPIENTS &&
            request.recipientHandles.all { HandleUtils.normalizeHandle(it).isNotEmpty() }
    ) {
        "Некорректный список получателей (максимум $MAX_PERSONAL_RECIPIENTS)"
    }
    if (!request.isGeoLocked) {
        val nonSelfCount = request.participantIds.count { it.trim() != authenticatedUserId }
        require(nonSelfCount <= MAX_PERSONAL_RECIPIENTS && request.recipientHandles.size <= MAX_PERSONAL_RECIPIENTS) {
            "Личная капсула может иметь не более $MAX_PERSONAL_RECIPIENTS получателей"
        }
    }
    require(request.stages.isNotEmpty() && request.stages.size <= MAX_STAGES_COUNT) {
        "Stages count must be between 1 and $MAX_STAGES_COUNT"
    }
    val stageNumbers = request.stages.map { it.stageNumber }
    require(stageNumbers.all { it > 0 } && stageNumbers.distinct().size == stageNumbers.size) {
        "Stage numbers must be positive and unique"
    }
    val now = System.currentTimeMillis()
    val minUnlockDate = now + 50_000L
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

    val creatorId = capsuleRow[Capsules.creatorId]
    val creatorRow = Users
        .selectAll()
        .where { Users.id eq creatorId }
        .singleOrNull()
    val creatorHandle = creatorRow?.get(Users.handle)

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
                archived = row[CapsuleParticipants.archived],
                handle = row[Users.handle],
                avatarUrl = row[Users.photoUrl],
                role = row[CapsuleParticipants.role]
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

    val isFullyUnlocked = stages.isNotEmpty() && stages.any { it.isUnlocked } && allParticipantsNearbyAndRecent

    val isMember = requestingUserId != null && (
        creatorId == requestingUserId ||
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
    val isInbox = requestingUserId != null &&
        creatorId != requestingUserId &&
        (myParticipant?.role == "RECIPIENT" || (!isGeoLocked && myParticipant != null))

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
        creatorId = creatorId,
        inviteCode = inviteCode,
        kind = kind,
        recipient = recipient,
        tags = tags,
        isGeoLocked = isGeoLocked,
        status = status,
        openedAt = myOpenedAt,
        archived = myArchived,
        creatorHandle = creatorHandle,
        isInbox = isInbox
    )
}
