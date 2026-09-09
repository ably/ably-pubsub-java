package io.ably.lib.uts.infra.unit

import io.ably.lib.debug.DebugOptions
import io.ably.lib.network.WebSocketEngineFactory
import io.ably.lib.network.WebSocketListener
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.types.ProtocolSerializer
import io.ably.lib.util.Serialisation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Collections
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Callbacks for [MockWebSocket]. All fields are optional.
 *
 * When a callback is set it receives the event immediately (synchronous, on the SDK's thread).
 * When a callback is null the event is queued for the corresponding `await*` method instead.
 * The two styles cannot be mixed for the same event type.
 */
class WebSocketMockConfig {
    /** Called for every connection attempt. Set to respond inline; leave null to use [MockWebSocket.awaitConnectionAttempt]. */
    var onConnectionAttempt: ((PendingConnection) -> Unit)? = null
    /** Called for every decoded protocol message from the SDK. Leave null to use [MockWebSocket.awaitNextMessageFromClient]. */
    var onMessageFromClient: ((ProtocolMessage) -> Unit)? = null
    /** Raw text frame callback — rarely needed; prefer [onMessageFromClient]. */
    var onTextDataFrame: ((String) -> Unit)? = null
    /** Raw binary frame callback — rarely needed; prefer [onMessageFromClient]. */
    var onBinaryDataFrame: ((ByteArray) -> Unit)? = null
}

/**
 * Fake WebSocket transport for SDK unit tests. Install via [installOn] or `TestRealtimeClient`.
 *
 * Two usage styles for connection/message handling (cannot mix per event type):
 * - **Callback** (`onConnectionAttempt`, `onMessageFromClient` in [WebSocketMockConfig]): handle
 *   inline, synchronously on the SDK thread. Preferred for single-behaviour setups.
 * - **Await** ([awaitConnectionAttempt], [awaitNextMessageFromClient]): suspend until the SDK
 *   triggers the event. Required when initial and reconnection attempts need different behaviour.
 */
class MockWebSocket(config: WebSocketMockConfig = WebSocketMockConfig()) {
    private val _events = Collections.synchronizedList(mutableListOf<MockEvent>())
    /** Snapshot of all events recorded since construction (or last [reset]). */
    val events: List<MockEvent> get() = _events.toList()

    @Volatile private var _pendingConnections = Channel<PendingConnection>(Channel.UNLIMITED)
    @Volatile private var _messagesFromClient = Channel<ProtocolMessage>(Channel.UNLIMITED)
    @Volatile private var _clientCloseEvents = Channel<MockEvent.ClientClose>(Channel.UNLIMITED)

    @Volatile private var activeListener: WebSocketListener? = null

    val engineFactory: WebSocketEngineFactory = MockWebSocketEngineFactory(
        onConnect = { pending ->
            _events.add(MockEvent.ConnectionAttempt(pending.host, pending.port, pending.tls))
            val tracked = EventTrackingPendingConnection(pending, _events)
            val handler = config.onConnectionAttempt
            if (handler != null) {
                handler(tracked)
            } else {
                _pendingConnections.trySend(tracked)
            }
        },
        onConnected = { listener ->
            _events.add(MockEvent.ConnectionEstablished)
            activeListener = listener
        },
        onTextFrame = { text ->
            config.onTextDataFrame?.invoke(text)
            val decoded = runCatching { Serialisation.gson.fromJson(text, ProtocolMessage::class.java) }.getOrNull()
            if (decoded != null) {
                _events.add(MockEvent.MessageFromClient(decoded))
                val handler = config.onMessageFromClient
                if (handler != null) {
                    handler(decoded)
                } else {
                    _messagesFromClient.trySend(decoded)
                }
            }
        },
        onBinaryFrame = { bytes ->
            config.onBinaryDataFrame?.invoke(bytes)
            // The SDK always sends byte[] frames (even for JSON encoding). Try JSON first
            // (useBinaryProtocol = false), fall back to msgpack (useBinaryProtocol = true).
            val decoded = runCatching {
                Serialisation.gson.fromJson(String(bytes, Charsets.UTF_8), ProtocolMessage::class.java)
            }.getOrElse {
                runCatching { ProtocolSerializer.readMsgpack(bytes) }.getOrNull()
            }
            if (decoded != null) {
                _events.add(MockEvent.MessageFromClient(decoded))
                val handler = config.onMessageFromClient
                if (handler != null) handler(decoded) else _messagesFromClient.trySend(decoded)
            }
        },
        onClientClose = { code, reason ->
            val event = MockEvent.ClientClose(code, reason)
            _events.add(event)
            // Match the other close paths: null the listener so post-close sendToClient fails fast.
            activeListener = null
            _clientCloseEvents.trySend(event)
        },
    )

    /** Wire this mock into [options] so the SDK uses it instead of a real WebSocket. */
    fun installOn(options: DebugOptions) {
        options.webSocketEngineFactory = engineFactory
    }

    /** Suspend until the SDK opens a new WebSocket connection. Only usable when [WebSocketMockConfig.onConnectionAttempt] is null. */
    suspend fun awaitConnectionAttempt(timeout: Duration = 5.seconds): PendingConnection =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(timeout) { _pendingConnections.receive() }
        }

    /** Suspend until the SDK sends a protocol message to the server. Only usable when [WebSocketMockConfig.onMessageFromClient] is null. */
    suspend fun awaitNextMessageFromClient(timeout: Duration = 5.seconds): ProtocolMessage =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(timeout) { _messagesFromClient.receive() }
        }

    /** Suspend until the SDK sends a WebSocket close frame. */
    suspend fun awaitClientClose(timeout: Duration = 5.seconds): MockEvent.ClientClose =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(timeout) { _clientCloseEvents.receive() }
        }

    /** Deliver [message] from the fake server to the SDK over the active connection. */
    fun sendToClient(message: ProtocolMessage) {
        val listener = checkNotNull(activeListener) { "No active WebSocket connection" }
        _events.add(MockEvent.SentToClient(message))
        listener.onMessage(Serialisation.gson.toJson(message))
    }

    /** Deliver [message] to the SDK then immediately close the connection with code 1000. */
    fun sendToClientAndClose(message: ProtocolMessage) {
        val listener = checkNotNull(activeListener) { "No active WebSocket connection" }
        _events.add(MockEvent.SentToClient(message))
        listener.onMessage(Serialisation.gson.toJson(message))
        activeListener = null
        listener.onClose(1000, "Normal closure")
    }

    /** Simulate an abnormal network drop (close code 1006). Triggers DISCONNECTED on the SDK. */
    fun simulateDisconnect() {
        val listener = checkNotNull(activeListener) { "No active WebSocket connection" }
        _events.add(MockEvent.Disconnected)
        activeListener = null
        listener.onClose(1006, "Abnormal closure")
    }

    /** Clear all queued events and pending channels. Call between tests when reusing a mock instance. */
    fun reset() {
        _pendingConnections.close()
        _messagesFromClient.close()
        _clientCloseEvents.close()
        _pendingConnections = Channel(Channel.UNLIMITED)
        _messagesFromClient = Channel(Channel.UNLIMITED)
        _clientCloseEvents = Channel(Channel.UNLIMITED)
        _events.clear()
        activeListener = null
    }
}

private class EventTrackingPendingConnection(
    private val inner: PendingConnection,
    private val events: MutableList<MockEvent>,
) : PendingConnection by inner {
    override fun respondWithRefused() {
        events.add(MockEvent.ConnectionRefused)
        inner.respondWithRefused()
    }

    override fun respondWithTimeout() {
        events.add(MockEvent.ConnectionTimeout)
        inner.respondWithTimeout()
    }

    override fun respondWithDnsError() {
        events.add(MockEvent.DnsError)
        inner.respondWithDnsError()
    }
}

fun MockWebSocket(init: WebSocketMockConfig.() -> Unit): MockWebSocket = MockWebSocket(config = WebSocketMockConfig().apply(init))

/** Pre-built CONNECTED message suitable for most unit tests. */
val CONNECTED_MESSAGE: ProtocolMessage
  get() = ProtocolMessage().apply {
    action = ProtocolMessage.Action.connected
    connectionId = "test-connection-id"
    connectionDetails = ConnectionDetails {
      connectionKey = "test-connection-key"
      connectionStateTtl = 120_000L
      maxIdleInterval = 15_000L
    }
  }

