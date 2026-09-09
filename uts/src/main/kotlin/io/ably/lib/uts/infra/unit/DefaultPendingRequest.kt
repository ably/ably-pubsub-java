package io.ably.lib.uts.infra.unit

import io.ably.lib.network.FailedConnectionException
import io.ably.lib.network.HttpBody
import io.ably.lib.network.HttpRequest
import io.ably.lib.network.HttpResponse
import io.ably.lib.util.Serialisation
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import java.net.SocketTimeoutException

internal class DefaultPendingRequest(
    private val request: HttpRequest,
    private val deferred: CompletableDeferred<HttpResponse>,
) : PendingRequest {
    override val url get() = request.url
    override val method get() = request.method
    override val headers: Map<String, List<String>> get() = request.headers ?: emptyMap()
    override val body get() = request.body?.content ?: ByteArray(0)

    override fun respondWith(status: Int, body: Any, headers: Map<String, String>) {
        val bytes = when (body) {
            is ByteArray -> body
            is String -> body.toByteArray(Charsets.UTF_8)
            else -> Serialisation.gson.toJson(body).toByteArray(Charsets.UTF_8)
        }
        // Derive the body content-type from a case-insensitive Content-Type header, defaulting to json.
        val contentType = headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value ?: "application/json"
        deferred.complete(
            HttpResponse.builder()
                .code(status)
                .message("")
                .body(HttpBody(contentType, bytes))
                .headers(headers.mapValues { listOf(it.value) })
                .build()
        )
    }

    override fun respondWithDelay(delay: Duration, status: Int, body: Any) {
        Thread {
            Thread.sleep(delay.inWholeMilliseconds)
            respondWith(status, body)
        }.apply { isDaemon = true }.start()
    }

    override fun respondWithTimeout() {
        deferred.completeExceptionally(FailedConnectionException(SocketTimeoutException("Connection timed out")))
    }
}
