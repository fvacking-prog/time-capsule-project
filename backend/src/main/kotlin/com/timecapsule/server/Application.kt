package com.timecapsule.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.deleteWhere
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
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
class PayloadTooLargeException(message: String) : RuntimeException(message)

fun main() {
    try {
        CryptoUtils.validateCryptoConfig()
    } catch (e: Throwable) {
        System.err.println(e.message ?: "КРИТИЧЕСКАЯ ОШИБКА: Неверная конфигурация CAPSULE_AES_KEY.")
        exitProcess(1)
    }
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    try {
        embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
            .start(wait = true)
    } catch (e: Throwable) {
        System.err.println(
            "КРИТИЧЕСКАЯ ОШИБКА ЗАПУСКА СЕРВЕРА: ${e.message ?: e.javaClass.simpleName}"
        )
        DatabaseConfig.closePool()
        exitProcess(1)
    }
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

    val dbSpec = DatabaseConfig.resolveConnectionSpec()
    logger.info(
        "Подключение к БД: engine={}, url={}, maxPoolSize={}, connectionTimeoutMs={}",
        if (dbSpec.isPostgres) "PostgreSQL" else "H2",
        dbSpec.jdbcUrl,
        DatabaseConfig.MAX_POOL_SIZE,
        DatabaseConfig.CONNECTION_TIMEOUT_MS
    )

    val dataSource = try {
        DatabaseConfig.initPool(dbSpec)
    } catch (e: DatabaseUnavailableException) {
        System.err.println("================================================================================")
        System.err.println(e.message)
        System.err.println("================================================================================")
        throw e
    }
    Database.connect(dataSource)

    // JVM Shutdown hook и подписка остановки Ktor для корректного закрытия пула HikariCP
    Runtime.getRuntime().addShutdownHook(Thread {
        DatabaseConfig.closePool()
    })
    monitor.subscribe(ApplicationStopped) {
        DatabaseConfig.closePool()
    }

    try {
        migrateDatabase()
    } catch (e: Throwable) {
        DatabaseConfig.closePool()
        val rootCause = generateSequence(e) { it.cause }.lastOrNull() ?: e
        val msg = "КРИТИЧЕСКАЯ ОШИБКА МИГРАЦИИ БД: Не удалось создать или обновить таблицы (${rootCause.message ?: e.message})."
        System.err.println(msg)
        throw DatabaseUnavailableException(msg, e)
    }

    // Очистка просроченных сессий при старте и раз в 6 часов
    try {
        purgeExpiredSessions()
    } catch (_: Exception) {}

    launch(Dispatchers.IO) {
        while (isActive) {
            delay(SESSION_CLEANUP_INTERVAL_MS)
            try {
                purgeExpiredSessions()
            } catch (_: Exception) {}
        }
    }

    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val status = call.response.status()?.value ?: 0
            val method = call.request.httpMethod.value
            val path = call.request.path()
            "$method $path -> $status"
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

    installSerializationAndErrors()

    routing {
        capsuleRoutes()
    }
}

fun Application.installSerializationAndErrors() {
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
        exception<PayloadTooLargeException> { call, cause ->
            call.respondText(
                text = cause.message ?: "Payload Too Large",
                status = HttpStatusCode.PayloadTooLarge
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
        exception<Throwable> { call, cause ->
            LoggerFactory.getLogger("StatusPages")
                .error("Unhandled error on ${call.request.httpMethod.value} ${call.request.path()}", cause)
            call.respondText(
                text = "Internal server error",
                status = HttpStatusCode.InternalServerError
            )
        }
    }
}

internal const val SESSION_CLEANUP_INTERVAL_MS = 6L * 60L * 60L * 1000L // 6 hours

fun purgeExpiredSessions(now: Long = System.currentTimeMillis()): Int {
    return transaction {
        Sessions.deleteWhere { Sessions.expiresAt lessEq now }
    }
}

fun migrateDatabase() {
    transaction {
        SchemaUtils.createMissingTablesAndColumns(Users, Capsules, CapsuleStages, CapsuleParticipants, Sessions, Blocks, CapsuleMedia)

        val explicitIndexes = listOf(
            "CREATE INDEX IF NOT EXISTS idx_capsules_creator_id ON capsules (creator_id)",
            "CREATE INDEX IF NOT EXISTS idx_capsule_stages_capsule_id ON capsule_stages (capsule_id)",
            "CREATE INDEX IF NOT EXISTS idx_capsule_participants_user_id ON capsule_participants (user_id)",
            "CREATE INDEX IF NOT EXISTS idx_capsule_media_capsule_id ON capsule_media (capsule_id)",
            "CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions (user_id)",
            "CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions (expires_at)",
            "CREATE INDEX IF NOT EXISTS idx_blocks_blocked_user_id ON blocks (blocked_user_id)"
        )
        for (sql in explicitIndexes) {
            exec(sql)
        }

        // Migrating existing group capsules created before invite_code was introduced
        val capsulesWithoutCode = Capsules
            .selectAll()
            .where { Capsules.inviteCode.isNull() and (Capsules.isGroupCapsule eq true) }
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

