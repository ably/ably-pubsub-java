package io.ably.lib.uts.infra.unit

import io.ably.lib.debug.DebugOptions
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.rest.AblyRest
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
 * `uts.side` system property (uts/build.gradle.kts forwards it to the test JVM):
 *
 * - `core` (default): the core constructors, the entry shape of today's package.
 * - `server`: `io.ably.pubsub:server` — both client kinds via its side-stamping builders.
 *
 * There is no `device` mode, unlike ably-js's UTS: `io.ably.pubsub:device` is an Android
 * artifact, so its door cannot run on the JVM this suite uses; its stamping contract is
 * covered by the instrumentation tests in the device module instead.
 *
 * The builders only stamp the side-declaring agent entry and pass every other option
 * through — [DebugOptions] included: its `copy()` override keeps the mock hooks the suite
 * installs — so conformance must be identical whichever door constructed the client.
 * `SideModesTest` asserts each mode's stamp, so a broken seam cannot silently degrade the
 * server CI leg into a duplicate of the core leg.
 */
val utsSide: String = System.getProperty("uts.side").let { if (it.isNullOrEmpty()) "core" else it }

/**
 * Call at the top of any test whose client authenticates with a native Ably token.
 *
 * Realtime rejects a token-authenticated connection that declares the server side through the
 * agent entry alone: error 40167, "a connection or request may only declare itself as a server
 * via a signed x-ably-clientType token claim". The native Ably token format cannot carry that
 * claim yet, so until the platform supports it, native-token conformance runs on the core leg
 * only and is skipped (not failed) on the server leg. Key-auth tests are unaffected — on
 * API-key auth the agent entry is the accepted declaration — and JWT-based tests can carry the
 * claim already (see AblyJwt), so they run on every leg instead of calling this.
 */
fun assumeSideSupportsTokenAuth() = org.junit.jupiter.api.Assumptions.assumeTrue(
    utsSide == "core",
    "native-token conformance is skipped on the '$utsSide' leg: realtime requires a signed " +
        "x-ably-clientType token claim (40167) to declare the server side on token auth, " +
        "and the native token format cannot carry that claim yet",
)

fun TestRealtimeClient(block: ClientOptionsBuilder.() -> Unit): AblyRealtime {
    val options = ClientOptionsBuilder().apply(block)
    return when (utsSide) {
        "core" -> AblyRealtime(options)
        "server" -> PubSubServer.realtimeClientBuilder(options).build()
        else -> throw IllegalArgumentException("Unknown uts.side '$utsSide': use 'core' or 'server'")
    }
}

fun TestRestClient(block: ClientOptionsBuilder.() -> Unit): AblyRest {
    val options = ClientOptionsBuilder().apply(block)
    return when (utsSide) {
        "core" -> AblyRest(options)
        "server" -> PubSubServer.httpClientBuilder(options).build()
        else -> throw IllegalArgumentException("Unknown uts.side '$utsSide': use 'core' or 'server'")
    }
}
