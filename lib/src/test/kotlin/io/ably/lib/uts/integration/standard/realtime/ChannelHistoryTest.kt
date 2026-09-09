package io.ably.lib.uts.integration.standard.realtime

import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.types.AblyException
import io.ably.lib.types.Callback
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.Message
import io.ably.lib.types.PaginatedResult
import io.ably.lib.types.PublishResult
import io.ably.lib.uts.infra.awaitChannelState
import io.ably.lib.uts.infra.awaitState
import io.ably.lib.uts.infra.integration.SandboxApp
import io.ably.lib.uts.infra.pollUntil
import io.ably.lib.uts.infra.unit.TestRealtimeClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Direct-sandbox integration test against the Ably Sandbox
 * (`sandbox.realtime.ably-nonprod.net`, via [SandboxApp.sandboxHost]) — no proxy, no
 * fault injection. Provisions one throwaway [SandboxApp] for the suite and connects real realtime
 * clients straight to the sandbox.
 *
 * Verifies that messages published by one realtime client are available in the history retrieved by
 * a separate client (cross-client durability).
 *
 * Spec points: RTL10d. Source spec: `realtime/integration/channel_history_test.md`.
 *
 * The test runs once per protocol variant (JSON / msgpack) per the spec's `PROTOCOL` dimension —
 * realised here with a `useBinaryProtocol` [ParameterizedTest] parameter.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChannelHistoryTest {

    private lateinit var app: SandboxApp

    @BeforeAll
    fun setUpAll() = runBlocking {
        app = SandboxApp.create()
    }

    @AfterAll
    fun tearDownAll() = runBlocking {
        if (::app.isInitialized) app.delete()
    }

    /**
     * @UTS realtime/integration/RTL10d/history-cross-client-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RTL10d - history contains messages published by another client`(useBinaryProtocol: Boolean) = runTest {
        val channelName = "history-RTL10d-" + UUID.randomUUID()
        val publisher = newClient(useBinaryProtocol)
        val subscriber = newClient(useBinaryProtocol)
        try {
            publisher.connect()
            subscriber.connect()
            awaitState(publisher, ConnectionState.connected, 10.seconds)
            awaitState(subscriber, ConnectionState.connected, 10.seconds)

            val pubChannel = publisher.channels.get(channelName)
            val subChannel = subscriber.channels.get(channelName)

            pubChannel.attach()
            subChannel.attach()
            awaitChannelState(pubChannel, ChannelState.attached, 10.seconds)
            awaitChannelState(subChannel, ChannelState.attached, 10.seconds)

            // Publish messages from the publisher client and await delivery confirmation.
            pubChannel.awaitPublish("event1", "data1")
            pubChannel.awaitPublish("event2", "data2")
            pubChannel.awaitPublish("event3", "data3")

            // Retrieve history from the subscriber client, polling until all messages appear.
            var history: PaginatedResult<Message>? = null
            pollUntil(10.seconds, 500.milliseconds) {
                val result = subChannel.history(null)
                history = result
                result.items().size == 3
            }

            val items = history!!.items()
            assertEquals(3, items.size)

            // Default order is backwards (newest first).
            assertEquals("event3", items[0].name)
            assertEquals("data3", items[0].data)

            assertEquals("event2", items[1].name)
            assertEquals("data2", items[1].data)

            assertEquals("event1", items[2].name)
            assertEquals("data1", items[2].data)
        } finally {
            publisher.close()
            subscriber.close()
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** A realtime client wired straight to the nonprod sandbox (no proxy). */
    private fun newClient(useBinaryProtocol: Boolean): AblyRealtime = TestRealtimeClient {
        key = app.defaultKey
        realtimeHost = SandboxApp.sandboxHost
        restHost = SandboxApp.sandboxHost
        this.useBinaryProtocol = useBinaryProtocol
        autoConnect = false
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
