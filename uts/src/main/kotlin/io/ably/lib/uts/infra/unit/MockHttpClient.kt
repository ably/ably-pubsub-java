package io.ably.lib.uts.infra.unit

import io.ably.lib.debug.DebugOptions
import io.ably.lib.network.HttpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Callbacks for [MockHttpClient]. Both fields are optional.
 *
 * When a callback is set it is invoked synchronously; when null the event queues for the
 * corresponding `await*` method.
 */
class HttpMockConfig {
    /** Called for every TCP connection attempt. Leave null to use [MockHttpClient.awaitConnectionAttempt]. */
    var onConnectionAttempt: ((PendingConnection) -> Unit)? = null
    /** Called for every HTTP request. Leave null to use [MockHttpClient.awaitRequest]. */
    var onRequest: ((PendingRequest) -> Unit)? = null
}

/**
 * Fake HTTP engine for SDK unit tests. Install via [installOn] or `TestRestClient`/`TestRealtimeClient`.
 */
class MockHttpClient(private val config: HttpMockConfig = HttpMockConfig()) {
    @Volatile private var _pendingConnections = Channel<PendingConnection>(Channel.UNLIMITED)
    @Volatile private var _pendingRequests = Channel<PendingRequest>(Channel.UNLIMITED)

    val engine: HttpEngine = MockHttpEngine(
        onConnect = { conn ->
            val handler = config.onConnectionAttempt
            if (handler != null) handler(conn) else _pendingConnections.trySend(conn)
        },
        onRequest = { pending ->
            val handler = config.onRequest
            if (handler != null) handler(pending) else _pendingRequests.trySend(pending)
        },
    )

    /** Wire this mock into [options] so the SDK uses it instead of a real HTTP client. */
    fun installOn(options: DebugOptions) {
        options.httpEngine = engine
    }

    /** Suspend until the SDK opens a TCP connection. Only usable when [HttpMockConfig.onConnectionAttempt] is null. */
    suspend fun awaitConnectionAttempt(timeout: Duration = 5.seconds): PendingConnection =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(timeout) { _pendingConnections.receive() }
        }

    /** Suspend until the SDK makes an HTTP request. Only usable when [HttpMockConfig.onRequest] is null. */
    suspend fun awaitRequest(timeout: Duration = 5.seconds): PendingRequest =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(timeout) { _pendingRequests.receive() }
        }

    /** Clear all queued pending connections and requests. Call between tests when reusing a mock instance. */
    fun reset() {
        _pendingConnections.close()
        _pendingRequests.close()
        _pendingConnections = Channel(Channel.UNLIMITED)
        _pendingRequests = Channel(Channel.UNLIMITED)
    }
}

fun MockHttpClient(init: HttpMockConfig.() -> Unit) = MockHttpClient(config = HttpMockConfig().apply(init))
