package com.example.cyberpunk.data

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import java.io.EOFException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException

object ColdStartRetry {
    const val DEFAULT_MAX_RETRIES = 3
    val DEFAULT_BACKOFF_DELAYS_MS = longArrayOf(3_000L, 6_000L, 12_000L)

    fun isRetryableError(t: Throwable): Boolean {
        if (t is CancellationException || t is SessionExpiredException) return false
        if (t is ResponseException) {
            val status = t.response.status
            return status == HttpStatusCode.BadGateway ||
                status == HttpStatusCode.ServiceUnavailable ||
                status == HttpStatusCode.GatewayTimeout
        }
        return t is SocketTimeoutException ||
            t is io.ktor.client.network.sockets.SocketTimeoutException ||
            t is HttpRequestTimeoutException ||
            t is ConnectTimeoutException ||
            t is ConnectException ||
            t is SocketException ||
            t is EOFException ||
            t is InterruptedIOException
    }

    suspend fun <T> execute(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        backoffDelaysMs: LongArray = DEFAULT_BACKOFF_DELAYS_MS,
        delayFn: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
        onRetry: (attempt: Int, delayMillis: Long) -> Unit = { _, _ -> },
        block: suspend () -> T
    ): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (attempt >= maxRetries || !isRetryableError(t)) {
                    throw t
                }
                val delayMs = backoffDelaysMs.getOrElse(attempt) {
                    backoffDelaysMs.lastOrNull() ?: 3_000L
                }
                attempt += 1
                onRetry(attempt, delayMs)
                delayFn(delayMs)
            }
        }
    }
}

