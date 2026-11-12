package com.example.cyberpunk.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.cyberpunk.BuildConfig
import com.timecapsule.shared.AppVersionDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

enum class UpdateRequirement {
    UP_TO_DATE,
    OPTIONAL,
    MANDATORY,
    INVALID_METADATA
}

data class UpdateEvaluation(
    val requirement: UpdateRequirement,
    val isMandatory: Boolean,
    val hasDownloadableApk: Boolean
)

object AppUpdatePolicy {
    private val sha256Regex = Regex("^[0-9a-fA-F]{64}$")

    fun isHttpsUrlSafe(rawUrl: String?): Boolean {
        val url = rawUrl?.trim().orEmpty()
        if (url.isEmpty() || url.any { it.isWhitespace() }) return false
        val uri = try {
            URI(url)
        } catch (_: Throwable) {
            return false
        }
        return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }

    fun isValidSha256Hex(rawSha256: String?): Boolean {
        val hex = rawSha256?.trim().orEmpty()
        return sha256Regex.matches(hex)
    }

    fun computeSha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    fun computeSha256Hex(file: File): String {
        FileInputStream(file).use { fis ->
            return computeSha256Hex(fis)
        }
    }

    /**
     * Проверяет SHA-256 загруженного APK. При несовпадении или ошибке файл немедленно удаляется.
     */
    fun verifyApkSha256AndCleanup(file: File, expectedSha256: String): Boolean {
        val expected = expectedSha256.trim().lowercase()
        if (!file.exists() || !file.isFile || file.length() <= 0L || !isValidSha256Hex(expected)) {
            runCatching { if (file.exists()) file.delete() }
            return false
        }
        val actual = try {
            computeSha256Hex(file)
        } catch (_: Throwable) {
            runCatching { file.delete() }
            return false
        }
        val matches = actual.equals(expected, ignoreCase = true)
        if (!matches) {
            runCatching { file.delete() }
        }
        return matches
    }

    fun evaluate(info: AppVersionDto, currentVersionCode: Int): UpdateEvaluation {
        val isNewer = info.versionCode > currentVersionCode
        val isBelowMin = currentVersionCode < info.minSupportedVersionCode
        val validArtifact = isHttpsUrlSafe(info.apkUrl) && isValidSha256Hex(info.apkSha256)

        if (!isNewer && !isBelowMin) {
            return UpdateEvaluation(
                requirement = UpdateRequirement.UP_TO_DATE,
                isMandatory = false,
                hasDownloadableApk = false
            )
        }
        if (!validArtifact) {
            return UpdateEvaluation(
                requirement = if (isBelowMin) UpdateRequirement.MANDATORY else UpdateRequirement.INVALID_METADATA,
                isMandatory = isBelowMin,
                hasDownloadableApk = false
            )
        }
        return UpdateEvaluation(
            requirement = if (isBelowMin) UpdateRequirement.MANDATORY else UpdateRequirement.OPTIONAL,
            isMandatory = isBelowMin,
            hasDownloadableApk = true
        )
    }
}

object AppUpdateManager {
    private const val UPDATE_CHECK_TIMEOUT_MS = 5_000L
    private const val MAX_REDIRECTS = 5
    private const val CACHE_SUBDIR = "apk-updates"

    private val checkClient = HttpClient(OkHttp) {
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = UPDATE_CHECK_TIMEOUT_MS
            connectTimeoutMillis = UPDATE_CHECK_TIMEOUT_MS
            socketTimeoutMillis = UPDATE_CHECK_TIMEOUT_MS
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
            )
        }
        engine {
            config {
                connectTimeout(UPDATE_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                readTimeout(UPDATE_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
        }
    }

    suspend fun fetchLatestVersion(baseUrl: String = ApiConfig.BASE_URL): AppVersionDto {
        val normalized = ApiConfig.normalizeUrl(baseUrl)
        require(ApiConfig.isValidServerUrl(normalized, allowCleartext = BuildConfig.DEBUG)) {
            "Некорректный адрес сервера для проверки обновлений"
        }
        return checkClient.get("$normalized/api/v1/app/version").body()
    }

    fun updatesCacheDir(context: Context): File =
        File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }

    fun cleanupUpdateCache(context: Context) {
        val dir = File(context.cacheDir, CACHE_SUBDIR)
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
    }

    suspend fun downloadAndVerifyApk(
        context: Context,
        versionInfo: AppVersionDto,
        onProgress: (Float?) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val apkUrl = versionInfo.apkUrl.trim()
        require(AppUpdatePolicy.isHttpsUrlSafe(apkUrl)) {
            "Небезопасная или пустая ссылка на APK (требуется HTTPS)."
        }
        require(AppUpdatePolicy.isValidSha256Hex(versionInfo.apkSha256)) {
            "На сервере не указана корректная контрольная сумма SHA-256 для APK."
        }

        val cacheDir = updatesCacheDir(context)
        cleanupUpdateCache(context)

        val tmpFile = File(cacheDir, "download-tmp.apk")
        val finalFile = File(cacheDir, "timecapsule-update-v${versionInfo.versionCode}.apk")
        if (tmpFile.exists()) tmpFile.delete()
        if (finalFile.exists()) finalFile.delete()

        var currentUrl = apkUrl
        var connection: HttpsURLConnection? = null
        try {
            var redirectCount = 0
            while (true) {
                if (!AppUpdatePolicy.isHttpsUrlSafe(currentUrl)) {
                    throw SecurityException("Редирект на небезопасный (не HTTPS) URL запрещён.")
                }
                val conn = (URL(currentUrl).openConnection() as HttpsURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 10_000
                    readTimeout = 30_000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location").orEmpty()
                    conn.disconnect()
                    if (location.isBlank() || redirectCount >= MAX_REDIRECTS) {
                        throw RuntimeException("Слишком много перенаправлений при загрузке APK (HTTP $code).")
                    }
                    currentUrl = URI(currentUrl).resolve(location).toString()
                    redirectCount += 1
                    continue
                }
                connection = conn
                if (code !in 200..299) {
                    throw RuntimeException("Не удалось скачать обновление (HTTP $code).")
                }
                break
            }

            val activeConn = requireNotNull(connection)
            val totalBytes = activeConn.contentLengthLong
            onProgress(if (totalBytes > 0L) 0f else null)

            activeConn.inputStream.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    var lastReportedBytes = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0L) {
                            if (downloaded - lastReportedBytes >= 32_768L || downloaded == totalBytes) {
                                lastReportedBytes = downloaded
                                onProgress((downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                            }
                        } else {
                            onProgress(null)
                        }
                    }
                    output.flush()
                }
            }
        } catch (t: Throwable) {
            runCatching { if (tmpFile.exists()) tmpFile.delete() }
            runCatching { if (finalFile.exists()) finalFile.delete() }
            throw t
        } finally {
            runCatching { connection?.disconnect() }
        }

        if (!AppUpdatePolicy.verifyApkSha256AndCleanup(tmpFile, versionInfo.apkSha256)) {
            runCatching { if (finalFile.exists()) finalFile.delete() }
            throw SecurityException("Контрольная сумма SHA-256 загруженного APK не совпала. Файл удалён.")
        }

        if (!tmpFile.renameTo(finalFile)) {
            tmpFile.copyTo(finalFile, overwrite = true)
            tmpFile.delete()
            if (!AppUpdatePolicy.verifyApkSha256AndCleanup(finalFile, versionInfo.apkSha256)) {
                throw SecurityException("Контрольная сумма SHA-256 APK не подтверждена. Файл удалён.")
            }
        }
        onProgress(1f)
        finalFile
    }

    fun canInstallPackages(context: Context): Boolean = try {
        context.packageManager.canRequestPackageInstalls()
    } catch (_: Throwable) {
        false
    }

    fun openUnknownSourcesSettings(context: Context): Boolean {
        return try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            try {
                val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    fun launchInstaller(context: Context, apkFile: File): Result<Unit> = runCatching {
        require(apkFile.exists() && apkFile.isFile) {
            "Файл обновления не найден в кэше. Загрузите обновление повторно."
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

