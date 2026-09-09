package io.ably.lib.uts.infra.unit

import java.net.URL
import kotlin.time.Duration


/**
 * An in-flight HTTP request that the test must resolve.
 *
 * Received via [MockHttpClient.awaitRequest] or [HttpMockConfig.onRequest].
 */
interface PendingRequest {
    val url: URL
    val method: String
    val headers: Map<String, List<String>>
    val body: ByteArray
    /** Complete the request with [status] and [body] (Map, String, or ByteArray). */
    fun respondWith(status: Int, body: Any, headers: Map<String, String> = emptyMap())
    /** Complete the request after [delay], useful for testing timeout behaviour. */
    fun respondWithDelay(delay: Duration, status: Int, body: Any)
    fun respondWithTimeout()
}
