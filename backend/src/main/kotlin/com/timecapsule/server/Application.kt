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
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory

class CapsuleNotFoundException(message: String) : RuntimeException(message)
class ParticipantNotFoundException(message: String) : RuntimeException(message)
class ForbiddenException(message: String) : RuntimeException(message)
class UnauthorizedException(message: String) : RuntimeException(message)
class ConflictException(message: String) : RuntimeException(message)
class TooManyRequestsException(message: String) : RuntimeException(message)

fun main() {
    try {
        CryptoUtils.validateCryptoConfig()
    } catch (e: Throwable) {
        System.err.println(e.message ?: "КРИТИЧЕСКАЯ ОШИБКА: Неверная конфигурация CAPSULE_AES_KEY.")
        exitProcess(1)
    }
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val logger = LoggerFactory.getLogger("Application")
    CryptoUtils.validateCryptoConfig()

    // Проверка доступности классов GoogleIdTokenVerifier при старте
    try {
        GoogleIdTokenVerifier.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance()
        ).build()
        logger.info("GoogleIdTokenVerifier classes verified successfully.")
    } catch (t: Throwable) {
        logger.warn("GoogleIdTokenVerifier init check note: ${t.message}")
    }

    val dataDir = java.io.File("./data")
    if (!dataDir.exists()) {
        dataDir.mkdirs()
    }

    val dbUrl = System.getenv("H2_URL") ?: "jdbc:h2:file:./data/timecapsule;DB_CLOSE_DELAY=-1"
    Database.connect(
        url = dbUrl,
        driver = "org.h2.Driver"
    )

    // JVM Shutdown hook для корректного освобождения блокировки файла БД
    Runtime.getRuntime().addShutdownHook(Thread {
        try {
            transaction {
                exec("SHUTDOWN") {}
            }
        } catch (_: Throwable) {}
    })

    try {
        migrateDatabase()
    } catch (e: Exception) {
        val rootCause = generateSequence(e as Throwable) { it.cause }.lastOrNull() ?: e
        val msg = "${e.message} ${rootCause.message}"
        if (msg.contains("Database may be already in use") || msg.contains("90020") || msg.contains("file is locked") || msg.contains("База данных уже используется")) {
            System.err.println("================================================================================")
            System.err.println("ОШИБКА: База данных ./data/timecapsule заблокирована другим процессом.")
            System.err.println("Возможно, сервер Time Capsule уже запущен (проверьте lsof ./data/timecapsule.mv.db).")
            System.err.println("Завершите старый процесс перед повторным запуском.")
            System.err.println("================================================================================")
            throw IllegalStateException("База данных ./data/timecapsule заблокирована другим процессом. Завершите старый процесс.", e)
        }
        throw e
    }

    // Фоновая периодическая очистка просроченных сессий раз в 60 секунд
    launch(Dispatchers.IO) {
        while (isActive) {
            delay(60_000L)
            try {
                transaction {
                    val now = System.currentTimeMillis()
                    Sessions.deleteWhere { Sessions.expiresAt less now }
                }
            } catch (_: Exception) {}
        }
    }

    // Ограничение максимального размера входящего тела запроса (10 МБ)
    intercept(ApplicationCallPipeline.Plugins) {
        val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (contentLength != null && contentLength > 10 * 1024 * 1024) {
            call.respondText(
                text = "Payload Too Large (максимум 10 МБ)",
                status = HttpStatusCode.PayloadTooLarge
            )
            finish()
        }
    }

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
        exception<TooManyRequestsException> { call, cause ->
            call.response.headers.append(HttpHeaders.RetryAfter, "60")
            call.respondText(
                text = cause.message ?: "Too many requests",
                status = HttpStatusCode.TooManyRequests
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

fun migrateDatabase() {
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
}

