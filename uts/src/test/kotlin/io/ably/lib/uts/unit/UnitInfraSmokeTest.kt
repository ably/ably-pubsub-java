package io.ably.lib.uts.unit

import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.uts.infra.awaitChannelState
import io.ably.lib.uts.infra.awaitState
import io.ably.lib.uts.infra.unit.CONNECTED_MESSAGE
import io.ably.lib.uts.infra.unit.ConnectionDetails
import io.ably.lib.uts.infra.unit.FakeClock
import io.ably.lib.uts.infra.unit.MockEvent
import io.ably.lib.uts.infra.unit.MockHttpClient
import io.ably.lib.uts.infra.unit.MockWebSocket
import io.ably.lib.uts.infra.unit.PendingConnection
import io.ably.lib.uts.infra.unit.TestRealtimeClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Acceptance test for the **unit-tier mock-transport infrastructure itself** ([MockWebSocket],
 * [MockHttpClient], [FakeClock], [TestRealtimeClient]) — NOT derived from a UTS spec, so it carries
 * no `@UTS` marker and must never trip the spec-parity tooling. It is the permanent teaching example
 * for `uts/README.md` §9 and the reference shape a future unit-tier UTS test should take.
 *
 * It proves the mock chain end-to-end: driving a real SDK through a fake WebSocket
 * (connect → attach → publish → disconnect → FakeClock-driven reconnect → refuse → SUSPENDED) and a
 * fake HTTP engine (token auth over `authUrl`).
 *
 * Hermetic — no network. Run with:
 * ```
 * ./gradlew :uts:runUtsUnitTests
 * ```
 */
class UnitInfraSmokeTest {

    /**
     * Full transport lifecycle over the fake WebSocket. Uses the **await style**
     * ([MockWebSocket.awaitConnectionAttempt]) throughout: the initial connect, the FakeClock-driven
     * reconnect and the refuse→SUSPENDED branch all need per-attempt control, which the callback
     * style cannot provide (a single `onConnectionAttempt` handler answers every attempt uniformly,
     * and the two styles cannot be mixed on one mock). The callback style is demonstrated in the
     * HTTP-auth test below.
     */
    @Test
    fun `unit infra drives the full mock-WebSocket connection lifecycle`() = runTest {
        val fakeClock = FakeClock()
        // Unicode channel name for the round-trip (out on the ATTACH frame, in on ATTACHED).
        val channelName = "smoke-üñîçöðé-${UUID.randomUUID()}"
        val mock = MockWebSocket()
        val client = TestRealtimeClient {
            autoConnect = false
            disconnectedRetryTimeout = 300
            fallbackHosts = emptyArray()
            install(mock)
            enableFakeTimers(fakeClock)
        }

        try {
            // 2. Connect (await style). Capture the PendingConnection so we can assert query params.
            val firstConnection = CompletableDeferred<PendingConnection>()
            launch {
                val conn = mock.awaitConnectionAttempt()
                firstConnection.complete(conn)
                conn.respondWithSuccess(CONNECTED_MESSAGE)
            }
            client.connect()
            awaitState(client, ConnectionState.connected)

            // Query params on the captured connection (README §9.3's technique).
            val conn = firstConnection.await()
            assertEquals("json", conn.queryParams["format"])
            assertNotNull(conn.queryParams["key"])
            // ConnectionDetails fixture round-tripped: CONNECTED_MESSAGE's id reached the client.
            assertEquals("test-connection-id", client.connection.id)
            // Event ordering: attempt, then established.
            assertTrue(mock.events[0] is MockEvent.ConnectionAttempt)
            assertTrue(mock.events[1] is MockEvent.ConnectionEstablished)

            // 3. Server-initiated ATTACHED, with the outbound ATTACH frame asserted (Unicode out).
            val ch = client.channels.get(channelName)
            ch.attach()
            val attachFrame = mock.awaitNextMessageFromClient()
            assertEquals(ProtocolMessage.Action.attach, attachFrame.action)
            assertEquals(channelName, attachFrame.channel)
            mock.sendToClient(ProtocolMessage().apply {
                action = ProtocolMessage.Action.attached
                channel = channelName
                channelSerial = "serial-1"
            })
            awaitChannelState(ch, ChannelState.attached)
            // channelSerial round-tripped in.
            assertEquals("serial-1", ch.properties.channelSerial)

            // 4. Publish — assert the full MESSAGE protocol frame, not just its arrival.
            ch.publish("event", "payload")
            val publishFrame = mock.awaitNextMessageFromClient()
            assertEquals(ProtocolMessage.Action.message, publishFrame.action)
            assertEquals(channelName, publishFrame.channel)
            assertEquals("event", publishFrame.messages[0].name)
            assertEquals("payload", publishFrame.messages[0].data)

            // 5. Disconnect: we reach DISCONNECTED and the drop is recorded. We deliberately do NOT
            // snapshot the ConnectionAttempt count here. FakeClock.waitOn(target, timeout) performs a
            // real `target.wait(timeout)`, so the disconnected-retry (~disconnectedRetryTimeout ms of
            // wall-clock, with backoff/jitter) eventually fires on its own even without an advance() —
            // on a loaded CI runner it can beat this line, making a "still exactly 1 attempt"
            // assertion inherently racy. advance() only wins the race sooner; it is not a hard gate.
            // Ownership of attempt #2 therefore belongs to step 6, which gates on it deterministically
            // via awaitConnectionAttempt() (buffered, so it cannot be missed).
            mock.simulateDisconnect()
            awaitState(client, ConnectionState.disconnected)
            assertTrue(mock.events.any { it is MockEvent.Disconnected })

            // 6. FakeClock-driven reconnect: the advance demonstrably drives the transition. Respond
            // with a short-TTL CONNECTED so the SUSPENDED branch below suspends quickly.
            val reconnectJob = launch {
                fakeClock.advance(2.seconds)
                mock.awaitConnectionAttempt().respondWithSuccess(shortLivedConnected())
            }
            awaitState(client, ConnectionState.connected)
            reconnectJob.join()
            assertEquals(2, mock.events.filterIsInstance<MockEvent.ConnectionAttempt>().size)

            // 7. Await-style refuse branch → SUSPENDED (README §9.2's centerpiece).
            mock.simulateDisconnect()
            val refuseJob = launch {
                repeat(20) {
                    fakeClock.advance(2.seconds)
                    mock.awaitConnectionAttempt().respondWithRefused()
                    if (client.connection.state == ConnectionState.suspended) return@launch
                }
            }
            awaitState(client, ConnectionState.suspended)
            refuseJob.cancel()
            assertNull(client.connection.createRecoveryKey())
        } finally {
            // 8. Teardown always runs.
            client.close()
        }
    }

    /**
     * The fake HTTP engine exercised for real via token auth. Uses the **callback style**
     * ([io.ably.lib.uts.infra.unit.WebSocketMockConfig.onConnectionAttempt]) for the WebSocket, and
     * the **await style** ([MockHttpClient.awaitRequest]) for the auth HTTP request.
     *
     * Trap: [MockEvent.HttpRequest] is declared but never emitted by the HTTP mock — asserting on
     * `events.filterIsInstance<MockEvent.HttpRequest>()` would silently pass on an empty list. We
     * assert via [MockHttpClient.awaitRequest] / the [io.ably.lib.uts.infra.unit.PendingRequest]
     * instead.
     */
    @Test
    fun `unit infra serves a token-auth HTTP request through the mock engine`() = runTest {
        val now = System.currentTimeMillis()
        // A TokenDetails JSON (the "issued" field makes the SDK treat it as TokenDetails, so no
        // second HTTP round-trip to exchange a TokenRequest is needed).
        val tokenJson =
            """{"token":"fake-token-abc","keyName":"appId.keyId","issued":$now,""" +
                """"expires":${now + 3_600_000L},"capability":"{\"*\":[\"*\"]}"}"""

        val mockWs = MockWebSocket { onConnectionAttempt = { it.respondWithSuccess(CONNECTED_MESSAGE) } }
        // Callback answers the HTTP TCP connect; the request itself is handled await-style below.
        val mockHttp = MockHttpClient { onConnectionAttempt = { it.respondWithSuccess() } }
        val client = TestRealtimeClient {
            authUrl = "https://auth.example.test/token"
            install(mockWs)
            install(mockHttp)
            autoConnect = false
        }

        try {
            val captured = CompletableDeferred<Pair<String, String>>()
            launch {
                val request = mockHttp.awaitRequest()
                captured.complete(request.method to request.url.path)
                request.respondWith(200, tokenJson, mapOf("Content-Type" to "application/json"))
            }

            client.connect()
            awaitState(client, ConnectionState.connected)

            val (method, path) = captured.await()
            assertEquals("GET", method)
            assertEquals("/token", path)
            assertEquals(ConnectionState.connected, client.connection.state)
        } finally {
            client.close()
        }
    }

    /**
     * Infra acceptance for the [FakeClock] run-to-quiescence Guarantee: a single [FakeClock.advance]
     * runs *all* work due within the advanced interval, including cascades — work scheduled by fired
     * work (a zero-delay reschedule) and a timer created mid-advance. Exercises [FakeClock] directly
     * (no SDK), which is what the Guarantee is about: `advance` alone must reach quiescence.
     */
    @Test
    fun `FakeClock advance runs cascaded work to quiescence in one call`() {
        val fakeClock = FakeClock()

        var rescheduleRuns = 0
        var newTimerTaskRan = false

        val timer = fakeClock.newTimer("cascade")
        // A task that reschedules ITSELF at zero delay a fixed number of times: each reschedule is due
        // immediately at the current (already-advanced) virtual time, so a single-pass advance would
        // fire only the first. Quiescence requires re-scanning until nothing more fires.
        lateinit var task: () -> Unit
        task = {
            rescheduleRuns++
            if (rescheduleRuns < 3) {
                timer.schedule(object : java.util.TimerTask() {
                    override fun run() = task()
                }, 0L)
            }
        }
        timer.schedule(object : java.util.TimerTask() {
            override fun run() {
                task()
                // Also create a BRAND-NEW timer mid-advance whose task is due within the interval — it
                // must be picked up by a later re-scan round (advance snapshots timers each round).
                val laterTimer = fakeClock.newTimer("cascade-created-mid-advance")
                laterTimer.schedule(object : java.util.TimerTask() {
                    override fun run() { newTimerTaskRan = true }
                }, 0L)
            }
        }, 10L)

        // ONE advance across the 10ms due point must run the initial task, both self-reschedules, and
        // the task on the timer created mid-advance.
        fakeClock.advance(10L)

        assertEquals(3, rescheduleRuns, "zero-delay reschedules should all fire within one advance")
        assertTrue(newTimerTaskRan, "a timer created mid-advance and due in-interval should fire in the same advance")
    }

    /** A CONNECTED with a short connectionStateTtl so a subsequent disconnect suspends quickly. */
    private fun shortLivedConnected(): ProtocolMessage = ProtocolMessage().apply {
        action = ProtocolMessage.Action.connected
        connectionId = "reconnected-id"
        connectionDetails = ConnectionDetails {
            connectionKey = "reconnected-key"
            connectionStateTtl = 800L
            maxIdleInterval = 15_000L
        }
    }
}
