package io.ably.pubsub.uts.infra.unit

import io.ably.pubsub.debug.DebugOptions
import io.ably.pubsub.realtime.PubSubRealtimeClient
import io.ably.pubsub.realtime.RealtimeClientFactory
import io.ably.pubsub.http.HttpClientFactory
import io.ably.pubsub.http.PubSubHttpClient
import io.ably.pubsub.server.PubSubServer

class ClientOptionsBuilder : DebugOptions("appId.keyId:keySecret") {
    init {
        useBinaryProtocol = false
    }

    fun install(mock: MockWebSocket) = mock.installOn(this)
    fun install(mock: MockHttpClient) = mock.installOn(this)

    fun enableFakeTimers(fakeClock: FakeClock) {
        clock = fakeClock
    }
}

/**
 * Which package's entry points the suite constructs clients through, selected by the
 * `uts.side` system property (the :uts, :core and :liveobjects test tasks forward it to the test JVM):
 *
 * - `core` (default): the core construction seam ([RealtimeClientFactory] / [HttpClientFactory]),
 *   which declares no side.
 * - `server`: `io.ably.pubsub:server` — both client kinds via its side-stamping builders.
 *
 * There is no `device` mode, unlike ably-js's UTS: `io.ably.pubsub:device` is not wired into
 * this suite's construction seam. Its stamping contract is covered by the device module's own
 * tests (`jvmTest` and the instrumentation tests in emulate.yml).
 *
 * The builders only stamp the side-declaring agent entry and pass every other option
 * through (`build()` snapshots the options via `copy()`, and [DebugOptions]'s `copy()`
 * override keeps the mock hooks the suite installs), so conformance must be identical whichever door constructed the client.
 * `SideModesTest` asserts each mode's stamp, so a broken seam cannot silently degrade the
 * server CI leg into a duplicate of the core leg.
 */
val utsSide: String = System.getProperty("uts.side").let { if (it.isNullOrEmpty()) "core" else it }

fun TestRealtimeClient(block: ClientOptionsBuilder.() -> Unit): PubSubRealtimeClient {
    val options = ClientOptionsBuilder().apply(block)
    return when (utsSide) {
        "core" -> RealtimeClientFactory.create(options)
        "server" -> PubSubServer.realtimeClientBuilder(options).build()
        else -> throw IllegalArgumentException("Unknown uts.side '$utsSide': use 'core' or 'server'")
    }
}

fun TestHttpClient(block: ClientOptionsBuilder.() -> Unit): PubSubHttpClient {
    val options = ClientOptionsBuilder().apply(block)
    return when (utsSide) {
        "core" -> HttpClientFactory.create(options)
        "server" -> PubSubServer.httpClientBuilder(options).build()
        else -> throw IllegalArgumentException("Unknown uts.side '$utsSide': use 'core' or 'server'")
    }
}
