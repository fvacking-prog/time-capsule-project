package com.timecapsule.server

import com.timecapsule.shared.AppVersionDto
import java.net.URI

object AppVersionConfig {
    private val sha256Regex = Regex("^[0-9a-f]{64}$")

    fun sanitizeHttpsUrl(raw: String?): String {
        val candidate = raw?.trim().orEmpty()
        if (candidate.isEmpty() || candidate.any { it.isWhitespace() }) {
            return ""
        }
        val uri = try {
            URI(candidate)
        } catch (_: Throwable) {
            return ""
        }
        val scheme = uri.scheme
        val host = uri.host
        if (!scheme.equals("https", ignoreCase = true) || host.isNullOrBlank()) {
            return ""
        }
        return candidate
    }

    fun sanitizeSha256(raw: String?): String {
        val normalized = raw?.trim()?.lowercase().orEmpty()
        return if (sha256Regex.matches(normalized)) normalized else ""
    }

    fun fromEnv(env: (String) -> String? = System::getenv): AppVersionDto {
        val versionCode = env("LATEST_VERSION_CODE")?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: 1
        val versionName = env("LATEST_VERSION_NAME")?.trim()?.takeIf { it.isNotEmpty() } ?: "1.0.0"
        val apkUrl = sanitizeHttpsUrl(env("LATEST_APK_URL"))
        val apkSha256 = sanitizeSha256(env("LATEST_APK_SHA256"))
        val changelog = env("LATEST_CHANGELOG")?.replace("\\n", "\n")?.trim().orEmpty()
        val minSupported = env("LATEST_MIN_SUPPORTED_VERSION_CODE")
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: 1

        return AppVersionDto(
            versionCode = versionCode,
            versionName = versionName,
            apkUrl = apkUrl,
            apkSha256 = apkSha256,
            changelog = changelog,
            minSupportedVersionCode = minSupported
        )
    }
}

