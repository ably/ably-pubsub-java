package io.ably.pubsub.uts.integration.standard

import io.ably.pubsub.realtime.RealtimeClient
import io.ably.pubsub.realtime.Channel
import io.ably.pubsub.realtime.ChannelState
import io.ably.pubsub.realtime.ConnectionState
import io.ably.pubsub.http.PubSubHttpClient
import io.ably.pubsub.types.AblyException
import io.ably.pubsub.types.Callback
import io.ably.pubsub.types.ErrorInfo
import io.ably.pubsub.types.Message
import io.ably.pubsub.types.PaginatedResult
import io.ably.pubsub.types.PublishResult
import io.ably.pubsub.uts.infra.awaitChannelState
import io.ably.pubsub.uts.infra.awaitState
import io.ably.pubsub.uts.infra.integration.SandboxApp
import io.ably.pubsub.uts.infra.pollUntil
import io.ably.pubsub.uts.infra.unit.TestRealtimeClient
import io.ably.pubsub.uts.infra.unit.TestHttpClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.Collections
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Acceptance test for the **direct-sandbox integration infrastructure itself** ([SandboxApp] +
 * [TestRealtimeClient] / [TestHttpClient] wired straight to the sandbox) — this is
 * NOT derived from a UTS spec, so it carries no `@UTS` marker. It is the permanent teaching example
 * for `uts/README.md` §10 and the reference shape a future integration-tier UTS test should take.
 *
 * It proves the middle tier end-to-end: sandbox provisioning → a real realtime client connecting
 * straight to the sandbox over TLS (basic key auth, RSA1) → an attach/subscribe/publish round-trip
 * → a REST `history()` read of the same messages → teardown.
 *
 * Runs once per protocol variant (the UTS `PROTOCOL` dimension): `false` = JSON, `true` = msgpack.
 *
 * Needs outbound network (the Ably sandbox). Run with:
 * ```
 * ./gradlew :uts:runUtsIntegrationTests
 * ```
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationInfraSmokeTest {

    private lateinit var app: SandboxApp

    @BeforeAll
    fun setUpAll() = runBlocking {
        app = SandboxApp.create()
    }

    @AfterAll
    fun tearDownAll() = runBlocking {
        if (::app.isInitialized) app.delete()
    }

    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `sandbox infra works end to end`(useBinaryProtocol: Boolean) = runTest {
        // Assert provisioning (cocoa parity).
        assertTrue(app.defaultKey.startsWith(app.appId + "."))
        assertTrue(app.keys.isNotEmpty())

        val channelName = "smoke-int-${UUID.randomUUID()}"
        val client = newRealtimeClient(useBinaryProtocol)
        val rest = newHttpClient(useBinaryProtocol)
        try {
            // State-transition sequence, not just the final state: collect before connect.
            val states = Collections.synchronizedList(mutableListOf<ConnectionState>())
            client.connection.on { states.add(it.current) }

            client.connect()
            awaitState(client, ConnectionState.connected, 15.seconds)
            assertTrue(states.contains(ConnectionState.connecting))
            assertEquals(ConnectionState.connected, states.last())

            val channel = client.channels.get(channelName)
            channel.attach()
            awaitChannelState(channel, ChannelState.attached, 15.seconds)

            val received = Collections.synchronizedList(mutableListOf<Message>())
            channel.subscribe { received.add(it) }

            // Three publishes, each ack-awaited.
            channel.awaitPublish("event1", "data1")
            channel.awaitPublish("event2", "data2")
            channel.awaitPublish("event3", "data3")

            pollUntil(15.seconds) { received.size == 3 }
            assertEquals(listOf("event1", "event2", "event3"), received.map { it.name })
            assertEquals(listOf("data1", "data2", "data3"), received.map { it.data })

            // REST half of the infra: read history until all three appear, assert newest-first.
            var history: PaginatedResult<Message>? = null
            pollUntil(15.seconds, 500.milliseconds) {
                val result = rest.channels.get(channelName).history(null)
                history = result
                result.items().size == 3
            }
            val items = history!!.items()
            assertEquals(3, items.size)
            assertEquals("event3", items[0].name)
            assertEquals("data3", items[0].data)
            assertEquals("event2", items[1].name)
            assertEquals("data2", items[1].data)
            assertEquals("event1", items[2].name)
            assertEquals("data1", items[2].data)
        } finally {
            client.close()
            rest.close()
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** A realtime client wired straight to the nonprod sandbox (no proxy). */
    private fun newRealtimeClient(useBinaryProtocol: Boolean): RealtimeClient = TestRealtimeClient {
        key = app.defaultKey
        realtimeHost = SandboxApp.sandboxHost
        restHost = SandboxApp.sandboxHost
        this.useBinaryProtocol = useBinaryProtocol
        autoConnect = false
    }

    /** A REST client wired straight to the nonprod sandbox (the REST half of the infra). */
    private fun newHttpClient(useBinaryProtocol: Boolean): PubSubHttpClient = TestHttpClient {
        key = app.defaultKey
        restHost = SandboxApp.sandboxHost
        this.useBinaryProtocol = useBinaryProtocol
    }

    /** Publishes a message and suspends until the server confirms delivery (or errors). */
    private suspend fun Channel.awaitPublish(name: String, data: Any?): PublishResult =
        suspendCancellableCoroutine { cont ->
            publish(name, data, object : Callback<PublishResult> {
                override fun onSuccess(result: PublishResult) {
                    if (cont.isActive) cont.resume(result)
                }

                override fun onError(reason: ErrorInfo) {
                    if (cont.isActive) cont.resumeWithException(AblyException.fromErrorInfo(reason))
                }
            })
        }
}
