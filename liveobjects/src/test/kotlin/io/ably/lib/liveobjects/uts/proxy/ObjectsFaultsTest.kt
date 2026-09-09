package io.ably.lib.liveobjects.uts.proxy

import io.ably.lib.liveobjects.path.PathObject
import io.ably.lib.liveobjects.value.LiveMapValue
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.types.AblyException
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ChannelOptions
import io.ably.lib.uts.infra.awaitChannelState
import io.ably.lib.uts.infra.awaitState
import io.ably.lib.uts.infra.integration.SandboxApp
import io.ably.lib.uts.infra.integration.proxy.ProxyManager
import io.ably.lib.uts.infra.integration.proxy.ProxySession
import io.ably.lib.uts.infra.integration.proxy.connectThroughProxy
import io.ably.lib.uts.infra.integration.proxy.wsFrameToClientRule
import io.ably.lib.uts.infra.pollUntil
import io.ably.lib.uts.infra.unit.TestRealtimeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import io.ably.lib.uts.infra.withRealTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Proxy integration test against Ably Sandbox endpoint.
 *
 * Uses the programmable uts-proxy to inject transport-level faults while the
 * SDK communicates with the real Ably backend. See
 * `uts/docs/proxy.md` for proxy infrastructure details.
 *
 * Exercises objects sync/mutation behaviour under faults: sync interrupted by disconnect and
 * re-synced on reconnect, mutations buffered during re-sync, server-initiated detach re-sync, and
 * an in-flight publishAndApply failing when the channel enters FAILED during the sync wait.
 *
 * Spec points: RTO5a2, RTO7, RTO8, RTO17, RTO20e, RTO20e1. Source spec:
 * `objects/integration/proxy/objects_faults.md`. Corresponding unit specs: `objects/unit/objects_pool.md`,
 * `objects/unit/realtime_object.md`.
 *
 * Proxy tests always use JSON (the proxy can only inspect text frames), which is the
 * `ClientOptionsBuilder` default.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ObjectsFaultsTest {

    private lateinit var app: SandboxApp

    @BeforeAll
    fun setUpAll() = runBlocking {
        ProxyManager.ensureProxy()
        app = SandboxApp.create()
    }

    @AfterAll
    fun tearDownAll() = runBlocking {
        if (::app.isInitialized) app.delete()
    }

    /**
     * @UTS objects/proxy/RTO5a2-RTO17/sync-interrupted-reconnect-0
     */
    @Test
    fun `RTO5a2, RTO17 - sync interrupted by disconnect, re-syncs on reconnect`() = runTest {
        val channelName = "objects-sync-interrupt-" + UUID.randomUUID()

        // Disconnect after first OBJECT_SYNC (action 20) frame to interrupt the sync.
        val session = ProxySession.create(
            rules = listOf(
                wsFrameToClientRule(action = mapOf("type" to "disconnect"), messageAction = 20, times = 1),
            ),
        )
        val client = proxyClient(session)
        try {
            client.connect()
            awaitState(client, ConnectionState.connected, 15.seconds)

            val channel = objectChannel(client, channelName)

            // First attach triggers sync; proxy disconnects mid-sync.
            channel.attach()
            awaitState(client, ConnectionState.disconnected, 15.seconds)

            // Client auto-reconnects; re-attach triggers a fresh sync.
            awaitState(client, ConnectionState.connected, 30.seconds)

            // get() waits for SYNCED — resolves only if the re-sync completes.
            val root = withRealTimeout(30.seconds) { channel.`object`.get().await() }

            assertIs<PathObject>(root)
            assertEquals("", root.path())
        } finally {
            closeClient(client)
            session.close()
        }
    }

    /**
     * @UTS objects/proxy/RTO7-RTO8/mutations-buffered-during-resync-0
     */
    @Test
    fun `RTO7, RTO8 - mutations during re-sync are buffered and applied`() = runTest {
        val channelName = "objects-buffer-resync-" + UUID.randomUUID()

        // Client A: direct connection (no proxy), publishes mutations.
        val clientA = directClient()
        // Client B: through the proxy, will be disconnected mid-test.
        val session = ProxySession.create(rules = emptyList())
        val clientB = proxyClient(session)
        try {
            clientA.connect()
            awaitState(clientA, ConnectionState.connected, 15.seconds)
            val rootA = withRealTimeout(15.seconds) { objectChannel(clientA, channelName).`object`.get().await() }

            // Set initial data
            rootA.set("key1", LiveMapValue.of("initial")).await()

            // Client B connects and syncs. The channel is fetched once and reused — re-calling
            // Channels.get() with mode options on an attached channel throws (use setOptions instead).
            val channelB = objectChannel(clientB, channelName)
            clientB.connect()
            awaitState(clientB, ConnectionState.connected, 15.seconds)
            var rootB = withRealTimeout(15.seconds) { channelB.`object`.get().await() }
            pollUntil(10.seconds) { rootB.get("key1").asString().value() == "initial" }

            // Disconnect client B
            session.triggerAction(mapOf("type" to "disconnect"))
            awaitState(clientB, ConnectionState.disconnected, 15.seconds)

            // While B is disconnected, A publishes a mutation
            rootA.set("key1", LiveMapValue.of("updated_during_disconnect")).await()

            // Client B reconnects and re-syncs; the mutation should be visible
            awaitState(clientB, ConnectionState.connected, 30.seconds)
            rootB = withRealTimeout(15.seconds) { channelB.`object`.get().await() }
            pollUntil(15.seconds) { rootB.get("key1").asString().value() == "updated_during_disconnect" }

            assertEquals("updated_during_disconnect", rootB.get("key1").asString().value())
        } finally {
            closeClient(clientA)
            closeClient(clientB)
            session.close()
        }
    }

    /**
     * @UTS objects/proxy/RTO17/server-detach-resync-0
     */
    @Test
    fun `RTO17 - server-initiated detach triggers re-sync on re-attach`() = runTest {
        val channelName = "objects-detach-resync-" + UUID.randomUUID()

        val session = ProxySession.create(rules = emptyList())
        val client = proxyClient(session)
        try {
            client.connect()
            awaitState(client, ConnectionState.connected, 15.seconds)

            val channel = objectChannel(client, channelName)
            var root = withRealTimeout(15.seconds) { channel.`object`.get().await() }

            // Set some data
            root.set("before_detach", LiveMapValue.of("hello")).await()
            assertEquals("hello", root.get("before_detach").asString().value())

            // Inject a server-initiated DETACHED (action 13) for the channel.
            session.triggerAction(
                mapOf(
                    "type" to "inject_to_client",
                    "message" to mapOf("action" to 13, "channel" to channelName),
                ),
            )

            // Client should auto-re-attach (RTL13a).
            awaitChannelState(channel, ChannelState.attached, 30.seconds)

            // Re-sync should restore the data.
            root = withRealTimeout(15.seconds) { channel.`object`.get().await() }
            pollUntil(15.seconds) { root.get("before_detach").asString().value() == "hello" }

            assertEquals("hello", root.get("before_detach").asString().value())
        } finally {
            closeClient(client)
            session.close()
        }
    }

    /**
     * @UTS objects/proxy/RTO20e/publish-fails-on-channel-failed-0
     */
    @Test
    fun `RTO20e - publishAndApply fails when channel enters FAILED during SYNCING`() = runTest {
        // RTO20e1 sequence: mutation in flight while SYNCING, then the channel enters FAILED
        // → 92008 (a mutation issued after FAILED would hit the RTO26b precondition, 90001).
        // Spec fixed in the local ably-specification checkout; see PROXY_UTS_SPEC_FIX.md until
        // merged upstream.
        val channelName = "objects-publish-failed-" + UUID.randomUUID()

        val session = ProxySession.create(rules = emptyList())
        val client = proxyClient(session)
        try {
            client.connect()
            awaitState(client, ConnectionState.connected, 15.seconds)

            val channel = objectChannel(client, channelName)
            val root = withRealTimeout(15.seconds) { channel.`object`.get().await() }

            // Re-enter SYNCING: inject an ATTACHED (action 11) carrying the HAS_OBJECTS flag
            // (bit 7 = 128). RTO4c starts a new sync on every ATTACHED protocol message, and since
            // the server never sent this ATTACHED, no OBJECT_SYNC follows — objects stay SYNCING.
            session.triggerAction(
                mapOf(
                    "type" to "inject_to_client",
                    "message" to mapOf("action" to 11, "channel" to channelName, "flags" to 128),
                ),
            )

            // Mutate while SYNCING: the publish + ACK complete against the real server (the channel
            // is still ATTACHED), then publishAndApply waits for a SYNCED that never comes (RTO20e).
            val pending = root.set("key", LiveMapValue.of("value"))

            // Wait for the server's ACK (action 1) so the operation is in the RTO20e sync-wait
            // rather than still publishing, then give the SDK a beat to enter the wait (real-time
            // delay — a bare delay() inside runTest is virtual and would skip instantly).
            pollUntil(10.seconds) {
                session.getLog().any {
                    it.type == "ws_frame" && it.direction == "server_to_client" &&
                        it.message?.get("action")?.asInt == 1
                }
            }
            withContext(Dispatchers.Default) { delay(500.milliseconds) }

            // The channel enters FAILED whilst the operation waits for SYNCED (RTO20e1).
            session.triggerAction(
                mapOf(
                    "type" to "inject_to_client",
                    "message" to mapOf(
                        "action" to 9,
                        "channel" to channelName,
                        "error" to mapOf("statusCode" to 400, "code" to 90000, "message" to "injected error"),
                    ),
                ),
            )
            awaitChannelState(channel, ChannelState.failed, 15.seconds)

            val error = assertFailsWith<AblyException> {
                withRealTimeout(15.seconds) { pending.await() }
            }
            assertEquals(92008, error.errorInfo.code)
            assertEquals(400, error.errorInfo.statusCode)
            // RTO20e1 - cause is the channel's errorReason, i.e. the injected 90000 channel error.
            val cause = assertIs<AblyException>(error.cause)
            assertEquals(90000, cause.errorInfo.code)
        } finally {
            closeClient(client)
            session.close()
        }
    }

    /**
     * @UTS objects/proxy/RTO5-RTO7/publish-during-sync-echo-after-0
     */
    @Test
    fun `RTO5, RTO7 - publish during sync, echo arrives after sync completes`() = runTest {
        val channelName = "objects-publish-during-sync-" + UUID.randomUUID()

        // Client A: direct, no proxy.
        val clientA = directClient()
        // Client B: through the proxy, with a delayed first OBJECT_SYNC to keep it SYNCING.
        val session = ProxySession.create(
            rules = listOf(
                wsFrameToClientRule(action = mapOf("type" to "delay", "delayMs" to 3000), messageAction = 20, times = 1),
            ),
        )
        val clientB = proxyClient(session)
        try {
            clientA.connect()
            awaitState(clientA, ConnectionState.connected, 15.seconds)
            val rootA = withRealTimeout(15.seconds) { objectChannel(clientA, channelName).`object`.get().await() }

            // Set up initial data
            rootA.set("existing", LiveMapValue.of("before")).await()

            // Start client B — stuck in SYNCING due to the delayed OBJECT_SYNC.
            clientB.connect()
            awaitState(clientB, ConnectionState.connected, 15.seconds)
            val channelB = objectChannel(clientB, channelName)
            channelB.attach()

            // While B is syncing, A publishes a mutation.
            rootA.set("existing", LiveMapValue.of("after")).await()

            // B's get() resolves once the delayed sync completes.
            val rootB = withRealTimeout(30.seconds) { channelB.`object`.get().await() }

            // The mutation from A should be visible (in sync data or as a buffered OBJECT).
            pollUntil(15.seconds) { rootB.get("existing").asString().value() == "after" }

            assertEquals("after", rootB.get("existing").asString().value())
        } finally {
            closeClient(clientA)
            closeClient(clientB)
            session.close()
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Common Cleanup (source spec): close the client and, when the connection was active
     * (connected/connecting/disconnected), await CLOSED so the transport is torn down before
     * the proxy session is deleted. Wrapped in runCatching so cleanup never masks a test failure.
     */
    private suspend fun closeClient(client: AblyRealtime) {
        val awaitClosed = client.connection.state in setOf(
            ConnectionState.connected, ConnectionState.connecting, ConnectionState.disconnected,
        )
        runCatching { client.close() }
        if (awaitClosed) runCatching { awaitState(client, ConnectionState.closed, 10.seconds) }
    }

    /** A realtime client routed through the proxy (localhost hop → nonprod sandbox upstream). */
    private fun proxyClient(session: ProxySession): AblyRealtime = TestRealtimeClient {
        key = app.defaultKey
        connectThroughProxy(session)
        autoConnect = false
    }

    /** A realtime client connected straight to the nonprod sandbox (no proxy). */
    private fun directClient(): AblyRealtime = TestRealtimeClient {
        key = app.defaultKey
        realtimeHost = SandboxApp.sandboxHost
        restHost = SandboxApp.sandboxHost
        autoConnect = false
    }

    /** A channel with the OBJECT_SUBSCRIBE + OBJECT_PUBLISH modes. */
    private fun objectChannel(client: AblyRealtime, name: String): Channel =
        client.channels.get(
            name,
            ChannelOptions().apply {
                modes = arrayOf(ChannelMode.object_subscribe, ChannelMode.object_publish)
            },
        )
}
