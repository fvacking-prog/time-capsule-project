package com.timecapsule.server

import java.sql.SQLException

enum class HandleValidationStatus {
    OK,
    INVALID_FORMAT,
    RESERVED
}

object HandleUtils {
    val HANDLE_REGEX = Regex("^[a-z0-9_]{4,24}$")
    val RESERVED_HANDLES = setOf("admin", "support", "root", "system", "timecapsule")

    fun normalizeHandle(raw: String): String =
        raw.trim().removePrefix("@").lowercase()

    fun validate(normalizedHandle: String): HandleValidationStatus {
        if (!HANDLE_REGEX.matches(normalizedHandle)) {
            return HandleValidationStatus.INVALID_FORMAT
        }
        if (normalizedHandle in RESERVED_HANDLES) {
            return HandleValidationStatus.RESERVED
        }
        return HandleValidationStatus.OK
    }

    fun isUniqueConstraintViolation(throwable: Throwable): Boolean {
        return generateSequence(throwable) { it.cause }.any { cause ->
            val sqlState = (cause as? SQLException)?.sqlState
            val errorCode = (cause as? SQLException)?.errorCode
            val msg = cause.message?.lowercase().orEmpty()
            sqlState == "23505" ||
                errorCode == 23505 ||
                msg.contains("unique") ||
                msg.contains("duplicate key") ||
                msg.contains("constraint")
        }
    }
}

