package com.timecapsule.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class DbConnectionSpec(
    val jdbcUrl: String,
    val driverClassName: String,
    val username: String?,
    val password: String?,
    val isPostgres: Boolean
)

class DatabaseUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

object DatabaseConfig {
    const val MAX_POOL_SIZE = 3
    const val CONNECTION_TIMEOUT_MS = 10_000L

    @Volatile
    private var activeDataSource: HikariDataSource? = null

    @Volatile
    private var activeSpec: DbConnectionSpec? = null

    fun resolveConnectionSpec(
        databaseUrlEnv: String? = System.getenv("DATABASE_URL"),
        h2UrlEnv: String? = System.getenv("H2_URL")
    ): DbConnectionSpec {
        val rawDbUrl = databaseUrlEnv?.trim().orEmpty()
        if (rawDbUrl.isNotEmpty()) {
            return parsePostgresUrl(rawDbUrl)
        }
        val h2Url = h2UrlEnv?.trim()?.takeIf { it.isNotEmpty() }
            ?: "jdbc:h2:file:./data/timecapsule;DB_CLOSE_DELAY=-1"
        return DbConnectionSpec(
            jdbcUrl = h2Url,
            driverClassName = "org.h2.Driver",
            username = "sa",
            password = "",
            isPostgres = false
        )
    }

    fun parsePostgresUrl(rawUrl: String): DbConnectionSpec {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            throw DatabaseUnavailableException("DATABASE_URL задан пустой строкой.")
        }

        if (trimmed.startsWith("jdbc:postgresql://", ignoreCase = true)) {
            val cleanUri = URI(trimmed.removePrefix("jdbc:"))
            val host = cleanUri.host
                ?: throw DatabaseUnavailableException("Некорректный DATABASE_URL: отсутствует хост PostgreSQL.")
            val portPart = if (cleanUri.port > 0) ":${cleanUri.port}" else ""
            val pathPart = cleanUri.rawPath.orEmpty()
            val queryPart = cleanUri.rawQuery?.takeIf { it.isNotBlank() }?.let { "?$it" } ?: ""
            val (user, pass) = extractUserInfo(cleanUri.rawUserInfo)
            return DbConnectionSpec(
                jdbcUrl = "jdbc:postgresql://$host$portPart$pathPart$queryPart",
                driverClassName = "org.postgresql.Driver",
                username = user,
                password = pass,
                isPostgres = true
            )
        }

        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            throw DatabaseUnavailableException("Некорректный синтаксис DATABASE_URL: ${e.message}", e)
        }

        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme != "postgresql" && scheme != "postgres") {
            throw DatabaseUnavailableException(
                "Неподдерживаемая схема DATABASE_URL '$scheme'. Ожидается 'postgresql://...' или 'postgres://...'."
            )
        }

        val host = uri.host?.takeIf { it.isNotBlank() }
            ?: throw DatabaseUnavailableException("Некорректный DATABASE_URL: отсутствует хост базы данных.")

        val portPart = if (uri.port > 0) ":${uri.port}" else ""
        val pathPart = uri.rawPath.orEmpty()
        if (pathPart.isBlank() || pathPart == "/") {
            throw DatabaseUnavailableException("Некорректный DATABASE_URL: не указано имя базы данных в пути URL.")
        }
        val queryPart = uri.rawQuery?.takeIf { it.isNotBlank() }?.let { "?$it" } ?: ""
        val (user, pass) = extractUserInfo(uri.rawUserInfo)

        val jdbcUrl = "jdbc:postgresql://$host$portPart$pathPart$queryPart"
        return DbConnectionSpec(
            jdbcUrl = jdbcUrl,
            driverClassName = "org.postgresql.Driver",
            username = user,
            password = pass,
            isPostgres = true
        )
    }

    private fun extractUserInfo(rawUserInfo: String?): Pair<String?, String?> {
        if (rawUserInfo.isNullOrEmpty()) return null to null
        val colonIdx = rawUserInfo.indexOf(':')
        return if (colonIdx < 0) {
            URLDecoder.decode(rawUserInfo, StandardCharsets.UTF_8) to null
        } else {
            val user = URLDecoder.decode(rawUserInfo.substring(0, colonIdx), StandardCharsets.UTF_8)
            val pass = URLDecoder.decode(rawUserInfo.substring(colonIdx + 1), StandardCharsets.UTF_8)
            user to pass
        }
    }

    fun buildHikariConfig(spec: DbConnectionSpec): HikariConfig {
        return HikariConfig().apply {
            jdbcUrl = spec.jdbcUrl
            driverClassName = spec.driverClassName
            if (!spec.username.isNullOrEmpty()) {
                username = spec.username
            }
            if (spec.password != null) {
                password = spec.password
            }
            maximumPoolSize = MAX_POOL_SIZE
            minimumIdle = 1
            connectionTimeout = CONNECTION_TIMEOUT_MS
            validationTimeout = 5_000L
            idleTimeout = 300_000L
            maxLifetime = 1_800_000L
            connectionTestQuery = "SELECT 1"
            poolName = if (spec.isPostgres) "TimeCapsulePostgresPool" else "TimeCapsuleH2Pool"
        }
    }

    fun initPool(spec: DbConnectionSpec): HikariDataSource {
        if (!spec.isPostgres && spec.jdbcUrl.startsWith("jdbc:h2:file:", ignoreCase = true)) {
            val dataDir = File("./data")
            if (!dataDir.exists()) {
                dataDir.mkdirs()
            }
        }

        val dataSource = try {
            HikariDataSource(buildHikariConfig(spec))
        } catch (e: Throwable) {
            val rootCause = generateSequence(e) { it.cause }.lastOrNull() ?: e
            val rawMsg = "${e.message.orEmpty()} ${rootCause.message.orEmpty()}"
            if (!spec.isPostgres && (rawMsg.contains("Database may be already in use") || rawMsg.contains("90020") || rawMsg.contains("file is locked"))) {
                throw DatabaseUnavailableException(
                    "ОШИБКА: Файловая база H2 уже заблокирована другим процессом (проверьте запущенные процессы или lsof ./data/timecapsule.mv.db).",
                    e
                )
            }
            val dbKind = if (spec.isPostgres) "PostgreSQL" else "H2"
            throw DatabaseUnavailableException(
                "КРИТИЧЕСКАЯ ОШИБКА: База данных $dbKind недоступна (${spec.jdbcUrl}). Проверьте DATABASE_URL и сетевую доступность: ${rootCause.message ?: e.message}",
                e
            )
        }

        activeDataSource = dataSource
        activeSpec = spec
        return dataSource
    }

    fun isDatabaseHealthy(): Boolean {
        val ds = activeDataSource
        if (ds != null) {
            if (ds.isClosed) return false
            return try {
                ds.connection.use { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.queryTimeout = 3
                        stmt.execute("SELECT 1")
                    }
                }
                true
            } catch (_: Throwable) {
                false
            }
        }
        val db = TransactionManager.defaultDatabase ?: return false
        return try {
            transaction(db) {
                exec("SELECT 1") {}
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun closePool() {
        val ds = activeDataSource
        val spec = activeSpec
        activeDataSource = null
        activeSpec = null
        if (ds != null && !ds.isClosed) {
            try {
                if (spec?.isPostgres == false) {
                    ds.connection.use { conn ->
                        conn.createStatement().use { stmt ->
                            stmt.execute("SHUTDOWN")
                        }
                    }
                }
            } catch (_: Throwable) {
            }
            try {
                ds.close()
            } catch (_: Throwable) {
            }
        }
    }
}

