package com.timecapsule.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

class CapsuleNotFoundException(message: String) : RuntimeException(message)
class ParticipantNotFoundException(message: String) : RuntimeException(message)
class ForbiddenException(message: String) : RuntimeException(message)
class UnauthorizedException(message: String) : RuntimeException(message)
class ConflictException(message: String) : RuntimeException(message)

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val dataDir = java.io.File("./data")
    if (!dataDir.exists()) {
        dataDir.mkdirs()
    }

    val dbUrl = System.getenv("H2_URL") ?: "jdbc:h2:file:./data/timecapsule;DB_CLOSE_DELAY=-1"
    Database.connect(
        url = dbUrl,
        driver = "org.h2.Driver"
    )

    migrateAndSeedDatabase()

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
    }

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        )
    }

    install(StatusPages) {
        exception<BadRequestException> { call, _ ->
            call.respondText(
                text = "Invalid JSON payload",
                status = HttpStatusCode.BadRequest
            )
        }
        exception<ContentConvertException> { call, _ ->
            call.respondText(
                text = "Invalid JSON format",
                status = HttpStatusCode.BadRequest
            )
        }
        exception<SerializationException> { call, _ ->
            call.respondText(
                text = "Invalid JSON payload",
                status = HttpStatusCode.BadRequest
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respondText(
                text = cause.message ?: "Bad request",
                status = HttpStatusCode.BadRequest
            )
        }
        exception<UnauthorizedException> { call, cause ->
            call.respondText(
                text = cause.message ?: "Unauthorized",
                status = HttpStatusCode.Unauthorized
            )
        }
        exception<ForbiddenException> { call, cause ->
            call.respondText(
                text = cause.message ?: "Forbidden",
                status = HttpStatusCode.Forbidden
            )
        }
        exception<ConflictException> { call, cause ->
            call.respondText(
                text = cause.message ?: "Conflict",
                status = HttpStatusCode.Conflict
            )
        }
        exception<CapsuleNotFoundException> { call, cause ->
            call.respondText(
                text = cause.message ?: "Capsule not found",
                status = HttpStatusCode.NotFound
            )
        }
        exception<ParticipantNotFoundException> { call, cause ->
            call.respondText(
                text = cause.message ?: "Participant not found",
                status = HttpStatusCode.NotFound
            )
        }
        exception<NotFoundException> { call, cause ->
            call.respondText(
                text = cause.message ?: "Not found",
                status = HttpStatusCode.NotFound
            )
        }
        exception<Throwable> { call, _ ->
            call.respondText(
                text = "Internal server error",
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    routing {
        capsuleRoutes()
    }
}

fun migrateAndSeedDatabase() {
    transaction {
        SchemaUtils.createMissingTablesAndColumns(Users, Capsules, CapsuleStages, CapsuleParticipants, Sessions)

        // Migrating existing capsules created before invite_code was introduced
        val capsulesWithoutCode = Capsules
            .selectAll()
            .where { Capsules.inviteCode.isNull() }
            .toList()

        for (row in capsulesWithoutCode) {
            val cid = row[Capsules.id]
            val newCode = generateUniqueInviteCode()
            Capsules.update({ Capsules.id eq cid }) {
                it[inviteCode] = newCode
            }
        }
    }

    seedDemoData()
}

fun seedDemoData() {
    transaction {
        if (Users.selectAll().count() > 0L || Capsules.selectAll().count() > 0L) return@transaction

        Users.insert {
            it[id] = "user_maga"
            it[username] = "Maga"
            it[displayName] = "Maga"
            it[email] = "maga@demo.local"
            it[googleSub] = null
            it[photoUrl] = null
        }
        Users.insert {
            it[id] = "user_zainab"
            it[username] = "Zainab"
            it[displayName] = "Zainab"
            it[email] = "zainab@demo.local"
            it[googleSub] = null
            it[photoUrl] = null
        }

        val now = System.currentTimeMillis()
        val capsuleId = UUID.randomUUID().toString()

        Capsules.insert {
            it[id] = capsuleId
            it[creatorId] = "user_maga"
            it[title] = "Капсула Худжанд"
            it[latitude] = 40.2825
            it[longitude] = 69.6221
            it[radiusMeters] = 50.0
            it[createdAt] = now
            it[isGroupCapsule] = true
            it[inviteCode] = "KHJ001"
            it[kind] = "MESSAGE"
            it[recipient] = "Жителям Худжанда"
            it[tags] = "худжанд,история,будущее"
            it[isGeoLocked] = true
        }

        listOf("user_maga", "user_zainab").forEach { participantId ->
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

        val stage1Unlock = Instant.ofEpochMilli(now).minus(1, ChronoUnit.HOURS).toEpochMilli()
        val stage2Unlock = LocalDate.of(2027, 11, 16).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val stage3Unlock = LocalDate.of(2030, 11, 16).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val demoStages = listOf(
            Triple(1, stage1Unlock, "Этап 1: Привет из Худжанда! Первый этап капсулы времени успешно открыт."),
            Triple(2, stage2Unlock, "Этап 2: 16 ноября 2027 — проверяем наши цели спустя год!"),
            Triple(3, stage3Unlock, "Этап 3: 16 ноября 2030 — финальное послание в будущее!")
        )

        demoStages.forEach { (number, unlockMillis, plainText) ->
            CapsuleStages.insert {
                it[id] = UUID.randomUUID().toString()
                it[CapsuleStages.capsuleId] = capsuleId
                it[stageNumber] = number
                it[unlockDate] = unlockMillis
                it[encryptedText] = CryptoUtils.encrypt(plainText)
            }
        }
    }
}

