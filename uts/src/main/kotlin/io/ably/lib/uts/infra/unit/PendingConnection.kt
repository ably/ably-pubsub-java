package io.ably.lib.uts.infra.unit

import io.ably.lib.types.ProtocolMessage
import java.net.URLDecoder

internal fun parseQueryString(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrEmpty()) return emptyMap()
    return rawQuery.split("&").mapNotNull { pair ->
        val idx = pair.indexOf('=')
        if (idx < 0) null
        else URLDecoder.decode(pair.substring(0, idx), "UTF-8") to
                URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
    }.toMap()
}

/**
 * A WebSocket or HTTP connection attempt that the test must resolve.
 *
 * Received via [MockWebSocket.awaitConnectionAttempt] or [WebSocketMockConfig.onConnectionAttempt].
 */
interface PendingConnection {
    val host: String
    val port: Int
    val tls: Boolean
    /** URL query parameters decoded from the connection URL (e.g. `key`, `recover`, `resume`, `format`). */
    val queryParams: Map<String, String>
    /** Open the connection without sending any initial message. */
    fun respondWithSuccess()
    /** Open the connection and immediately deliver [message] to the SDK (e.g. CONNECTED). */
    fun respondWithSuccess(message: ProtocolMessage)
    fun respondWithRefused()
    fun respondWithTimeout()
    fun respondWithDnsError()
}
