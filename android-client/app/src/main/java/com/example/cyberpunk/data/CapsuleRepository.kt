package com.example.cyberpunk.data

import com.example.cyberpunk.BuildConfig
import com.timecapsule.shared.AuthResponse
import com.timecapsule.shared.BlockUserRequest
import com.timecapsule.shared.CapsuleDto
import com.timecapsule.shared.CheckHandleResponse
import com.timecapsule.shared.CheckInRequest
import com.timecapsule.shared.CreateCapsuleRequest
import com.timecapsule.shared.GoogleAuthRequest
import com.timecapsule.shared.JoinCapsuleRequest
import com.timecapsule.shared.MessageResponse
import com.timecapsule.shared.UpdateHandleRequest
import com.timecapsule.shared.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class SessionExpiredException(message: String = "HTTP 401 Unauthorized") : RuntimeException(message)

interface CapsuleRepository {
    suspend fun authGoogle(idToken: String): AuthResponse
    suspend fun getMe(): UserDto
    suspend fun checkHandle(handle: String): CheckHandleResponse
    suspend fun updateHandle(handle: String): UserDto
    suspend fun searchUsers(query: String = ""): List<UserDto>
    suspend fun getCapsules(): List<CapsuleDto>
    suspend fun getCapsule(id: String): CapsuleDto
    suspend fun downloadCapsuleMedia(capsuleId: String, mediaId: String): ByteArray
    suspend fun createCapsule(request: CreateCapsuleRequest): CapsuleDto
    suspend fun checkIn(capsuleId: String, request: CheckInRequest): CapsuleDto
    suspend fun joinCapsule(inviteCode: String): CapsuleDto
    suspend fun openCapsule(capsuleId: String): CapsuleDto
    suspend fun archiveCapsule(capsuleId: String): CapsuleDto
    suspend fun unarchiveCapsule(capsuleId: String): CapsuleDto
    suspend fun blockUser(userId: String): MessageResponse
    suspend fun unblockUser(userId: String): MessageResponse
    suspend fun getBlockedUsers(): List<UserDto>
    suspend fun deleteMyData(): MessageResponse
    suspend fun checkHealth(baseUrl: String = ApiConfig.BASE_URL): Boolean
}

class RemoteCapsuleRepository(
    private val onWakeUpStateChange: ((Boolean) -> Unit)? = null
) : CapsuleRepository {
    private val client = HttpClient(OkHttp) {
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000L
            connectTimeoutMillis = 15_000L
            socketTimeoutMillis = 15_000L
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
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(15, TimeUnit.SECONDS)
            }
        }
    }

    private fun endpoint(path: String): String {
        val base = ApiConfig.BASE_URL.trim().trimEnd('/')
        if (!BuildConfig.DEBUG) {
            require(ApiConfig.isValidServerUrl(base, allowCleartext = false)) {
                "В release-сборке требуется HTTPS-адрес сервера"
            }
        }
        return "$base$path"
    }

    private fun HttpRequestBuilder.attachBearerToken() {
        val token = ApiConfig.sessionToken
        if (!token.isNullOrBlank()) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    private suspend inline fun <T> executeWithAuthCheck(crossinline block: suspend () -> T): T {
        var didTriggerWakeUp = false
        try {
            return ColdStartRetry.execute(
                onRetry = { _, _ ->
                    didTriggerWakeUp = true
                    onWakeUpStateChange?.invoke(true)
                }
            ) {
                block()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.Unauthorized) {
                throw SessionExpiredException("HTTP 401 Unauthorized")
            }
            val errorText = try {
                e.response.bodyAsText().trim()
            } catch (_: Throwable) {
                ""
            }
            if (errorText.isNotEmpty()) {
                throw RuntimeException(errorText, e)
            } else {
                throw RuntimeException("Ошибка сервера: HTTP ${e.response.status.value}", e)
            }
        } catch (e: java.net.UnknownHostException) {
            throw RuntimeException("Не удалось найти сервер. Проверьте подключение и адрес сервера.", e)
        } catch (e: java.net.ConnectException) {
            throw RuntimeException("Сервер недоступен. Подождите немного или проверьте адрес сервера.", e)
        } catch (e: java.net.SocketTimeoutException) {
            throw RuntimeException("Сервер долго не отвечает (возможно, просыпается). Попробуйте снова.", e)
        } catch (e: io.ktor.client.network.sockets.SocketTimeoutException) {
            throw RuntimeException("Сервер долго не отвечает (возможно, просыпается). Попробуйте снова.", e)
        } catch (e: io.ktor.client.plugins.HttpRequestTimeoutException) {
            throw RuntimeException("Сервер долго не отвечает (возможно, просыпается). Попробуйте снова.", e)
        } catch (e: io.ktor.client.network.sockets.ConnectTimeoutException) {
            throw RuntimeException("Сервер долго не отвечает (возможно, просыпается). Попробуйте снова.", e)
        } finally {
            if (didTriggerWakeUp) {
                onWakeUpStateChange?.invoke(false)
            }
        }
    }

    override suspend fun authGoogle(idToken: String): AuthResponse {
        return executeWithAuthCheck {
            client.post(endpoint("/api/v1/auth/google")) {
                contentType(ContentType.Application.Json)
                setBody(GoogleAuthRequest(idToken = idToken))
            }.body()
        }
    }

    override suspend fun getMe(): UserDto {
        return executeWithAuthCheck {
            client.get(endpoint("/api/v1/me")) {
                attachBearerToken()
            }.body()
        }
    }

    override suspend fun checkHandle(handle: String): CheckHandleResponse {
        return executeWithAuthCheck {
            client.get(endpoint("/api/v1/users/check-handle")) {
                attachBearerToken()
                parameter("handle", handle)
            }.body()
        }
    }

    override suspend fun updateHandle(handle: String): UserDto {
        return executeWithAuthCheck {
            client.put(endpoint("/api/v1/users/me/handle")) {
                attachBearerToken()
                contentType(ContentType.Application.Json)
                setBody(UpdateHandleRequest(handle = handle))
            }.body()
        }
    }

    override suspend fun searchUsers(query: String): List<UserDto> {
        return executeWithAuthCheck {
            client.get(endpoint("/api/v1/users")) {
                attachBearerToken()
                if (query.isNotBlank()) {
                    parameter("query", query)
                }
            }.body()
        }
    }

    override suspend fun getCapsules(): List<CapsuleDto> {
        return executeWithAuthCheck {
            client.get(endpoint("/api/v1/capsules")) {
                attachBearerToken()
            }.body()
        }
    }

    override suspend fun getCapsule(id: String): CapsuleDto {
        return executeWithAuthCheck {
            client.get(endpoint("/api/v1/capsules/$id")) {
                attachBearerToken()
            }.body()
        }
    }

    override suspend fun downloadCapsuleMedia(capsuleId: String, mediaId: String): ByteArray {
        return executeWithAuthCheck {
            client.get(endpoint("/api/v1/capsules/$capsuleId/media/$mediaId")) {
                attachBearerToken()
            }.body()
        }
    }

    override suspend fun createCapsule(request: CreateCapsuleRequest): CapsuleDto {
        return executeWithAuthCheck {
            client.post(endpoint("/api/v1/capsules")) {
                attachBearerToken()
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        }
    }

    override suspend fun checkIn(capsuleId: String, request: CheckInRequest): CapsuleDto {
        return executeWithAuthCheck {
            client.post(endpoint("/api/v1/capsules/$capsuleId/check-in")) {
                attachBearerToken()
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        }
    }

    override suspend fun joinCapsule(inviteCode: String): CapsuleDto {
        return executeWithAuthCheck {
            client.post(endpoint("/api/v1/capsules/join")) {
                attachBearerToken()
                contentType(ContentType.Application.Json)
                setBody(JoinCapsuleRequest(inviteCode = inviteCode.trim().uppercase()))
            }.body()
        }
    }

    override suspend fun openCapsule(capsuleId: String): CapsuleDto {
        return executeWithAuthCheck {
            client.post(endpoint("/api/v1/capsules/$capsuleId/open")) {
                attachBearerToken()
                contentType(ContentType.Application.Json)
            }.body()
        }
    }

    override suspend fun archiveCapsule(capsuleId: String): CapsuleDto {
        return executeWithAuthCheck {
            client.post(endpoint("/api/v1/capsules/$capsuleId/archive")) {
                attachBearerToken()
                contentType(ContentType.Application.Json)
            }.body()
        }
    }

    override suspend fun unarchiveCapsule(capsuleId: String): CapsuleDto {
        return executeWithAuthCheck {
            client.post(endpoint("/api/v1/capsules/$capsuleId/unarchive")) {
                attachBearerToken()
                contentType(ContentType.Application.Json)
            }.body()
        }
    }

    override suspend fun blockUser(userId: String): MessageResponse {
        return executeWithAuthCheck {
            client.post(endpoint("/api/v1/blocks")) {
                attachBearerToken()
                contentType(ContentType.Application.Json)
                setBody(BlockUserRequest(userId = userId))
            }.body()
        }
    }

    override suspend fun unblockUser(userId: String): MessageResponse {
        return executeWithAuthCheck {
            client.delete(endpoint("/api/v1/blocks/$userId")) {
                attachBearerToken()
            }.body()
        }
    }

    override suspend fun getBlockedUsers(): List<UserDto> {
        return executeWithAuthCheck {
            client.get(endpoint("/api/v1/blocks")) {
                attachBearerToken()
            }.body()
        }
    }

    override suspend fun deleteMyData(): MessageResponse {
        return executeWithAuthCheck {
            client.delete(endpoint("/api/v1/me/data")) {
                attachBearerToken()
            }.body()
        }
    }

    override suspend fun checkHealth(baseUrl: String): Boolean {
        val normalized = ApiConfig.normalizeUrl(baseUrl)
        if (!BuildConfig.DEBUG && !ApiConfig.isValidServerUrl(normalized, allowCleartext = false)) {
            return false
        }
        val target = "$normalized/health"
        var didTriggerWakeUp = false
        return try {
            ColdStartRetry.execute(
                onRetry = { _, _ ->
                    didTriggerWakeUp = true
                    onWakeUpStateChange?.invoke(true)
                }
            ) {
                val response = client.get(target)
                response.status.isSuccess() && response.bodyAsText().trim().equals("ok", ignoreCase = true)
            }
        } finally {
            if (didTriggerWakeUp) {
                onWakeUpStateChange?.invoke(false)
            }
        }
    }
}
