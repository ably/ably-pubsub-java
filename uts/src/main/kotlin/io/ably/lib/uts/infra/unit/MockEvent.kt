package io.ably.lib.uts.infra.unit

import io.ably.lib.types.ProtocolMessage
import java.net.URL

/** Ordered log of everything that happened on a mock transport. Inspect via [MockWebSocket.events]. */
sealed class MockEvent {
    /** SDK initiated a WebSocket connection to [host]:[port]. */
    data class ConnectionAttempt(val host: String, val port: Int, val tls: Boolean) : MockEvent()
    /** WebSocket handshake completed (after [PendingConnection.respondWithSuccess]). */
    data object ConnectionEstablished : MockEvent()
    /** Test responded to a connection attempt with [PendingConnection.respondWithRefused]. */
    data object ConnectionRefused : MockEvent()
    /** Test responded to a connection attempt with [PendingConnection.respondWithTimeout]. */
    data object ConnectionTimeout : MockEvent()
    /** Test responded to a connection attempt with [PendingConnection.respondWithDnsError]. */
    data object DnsError : MockEvent()
    /** SDK made an HTTP request (recorded by [MockHttpClient]). */
    data class HttpRequest(val url: URL, val method: String) : MockEvent()
    /** Test delivered [message] to the SDK via [MockWebSocket.sendToClient]. */
    data class SentToClient(val message: ProtocolMessage) : MockEvent()
    /** Test called [MockWebSocket.simulateDisconnect] (abnormal close, code 1006). */
    data object Disconnected : MockEvent()
    /** SDK closed the WebSocket (code and reason from the SDK's close frame). */
    data class ClientClose(val code: Int, val reason: String) : MockEvent()
    /** SDK sent [message] to the server (decoded from text or binary frame). */
    data class MessageFromClient(val message: ProtocolMessage) : MockEvent()
}
