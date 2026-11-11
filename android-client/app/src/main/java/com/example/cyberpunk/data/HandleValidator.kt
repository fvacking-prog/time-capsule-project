package com.example.cyberpunk.data

enum class HandleLocalValidation {
    EMPTY,
    VALID,
    INVALID_FORMAT,
    RESERVED
}

object HandleValidator {
    val HANDLE_REGEX = Regex("^[a-z0-9_]{4,24}$")
    val RESERVED_HANDLES = setOf("admin", "support", "root", "system", "timecapsule")

    fun sanitizeInput(raw: String): String =
        raw.trimStart().removePrefix("@").lowercase().filter { !it.isWhitespace() }.take(24)

    fun validateLocal(raw: String): HandleLocalValidation {
        val clean = raw.trim().removePrefix("@").lowercase()
        if (clean.isEmpty()) return HandleLocalValidation.EMPTY
        if (!HANDLE_REGEX.matches(clean)) return HandleLocalValidation.INVALID_FORMAT
        if (clean in RESERVED_HANDLES) return HandleLocalValidation.RESERVED
        return HandleLocalValidation.VALID
    }
}

