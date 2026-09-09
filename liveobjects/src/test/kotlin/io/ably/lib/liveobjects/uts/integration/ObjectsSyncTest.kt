package io.ably.lib.liveobjects.uts.integration

import io.ably.lib.liveobjects.path.PathObject
import io.ably.lib.liveobjects.value.LiveMapValue
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ChannelOptions
import io.ably.lib.uts.infra.awaitChannelState
import io.ably.lib.uts.infra.awaitState
import io.ably.lib.uts.infra.integration.SandboxApp
import io.ably.lib.uts.infra.pollUntil
import io.ably.lib.uts.infra.unit.TestRealtimeClient
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

/**
 * Direct-sandbox integration test against the Ably Sandbox
 * (`sandbox.realtime.ably-nonprod.net`, via [SandboxApp.sandboxHost]) — no proxy, no
 * fault injection. Provisions one throwaway [SandboxApp] for the suite.
 *
 * Verifies the objects sync sequence against the real server: attach with HAS_OBJECTS, receive
 * OBJECT_SYNC, reach SYNCED; two-client convergence; and re-attach re-syncing the pool.
 *
 * Spec points: RTO4, RTO5, RTO17. Source spec: `objects/integration/objects_sync_test.md`.
 *
 * Each test runs once per protocol variant (JSON / msgpack) per the spec's `PROTOCOL` dimension —
 * realised here with a `useBinaryProtocol` [ParameterizedTest] parameter.
 *
 * > **Translate-only:** `channel.object.get()` resolves only once the SDK's OBJECT_SYNC processing
 * > + `RealtimeObject.get()` land, so these compile now and run once the LiveObjects engine is
 * > implemented.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ObjectsSyncTest {

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
     * @UTS objects/integration/RTO4-RTO5/attach-sync-get-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RTO4, RTO5 - attach triggers sync, get() resolves after SYNCED`(useBinaryProtocol: Boolean) = runTest {
        val channelName = "objects-sync-" + UUID.randomUUID()
        val client = newClient(useBinaryProtocol)
        try {
            client.connect()
            awaitState(client, ConnectionState.connected, 15.seconds)

            val root = objectChannel(client, channelName).`object`.get().await()

            assertIs<PathObject>(root)
            assertEquals("", root.path())
        } finally {
            client.close()
        }
    }

    /**
     * @UTS objects/integration/RTO5-RTO17/two-clients-sync-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RTO5, RTO17 - two clients sync same channel with pre-existing data`(useBinaryProtocol: Boolean) = runTest {
        val channelName = "objects-two-sync-" + UUID.randomUUID()
        val clientA = newClient(useBinaryProtocol)
        val clientB = newClient(useBinaryProtocol)
        try {
            clientA.connect()
            awaitState(clientA, ConnectionState.connected, 15.seconds)
            clientB.connect()
            awaitState(clientB, ConnectionState.connected, 15.seconds)

            // Client A creates data
            val rootA = objectChannel(clientA, channelName).`object`.get().await()
            rootA.set("key1", LiveMapValue.of("value1")).await()

            // Client B attaches and syncs — should see the data
            val rootB = objectChannel(clientB, channelName).`object`.get().await()
            pollUntil(10.seconds) { rootB.get("key1").asString().value() == "value1" }

            assertEquals("value1", rootB.get("key1").asString().value())
        } finally {
            clientA.close()
            clientB.close()
        }
    }

    /**
     * @UTS objects/integration/RTO17/reattach-resyncs-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RTO17 - re-attach re-syncs object pool`(useBinaryProtocol: Boolean) = runTest {
        val channelName = "objects-reattach-" + UUID.randomUUID()
        val client = newClient(useBinaryProtocol)
        try {
            client.connect()
            awaitState(client, ConnectionState.connected, 15.seconds)

            val channel = objectChannel(client, channelName)
            var root = channel.`object`.get().await()

            // Set some data
            root.set("before_detach", LiveMapValue.of("hello")).await()
            assertEquals("hello", root.get("before_detach").asString().value())

            // Detach and re-attach
            channel.detach()
            awaitChannelState(channel, ChannelState.detached)
            channel.attach()
            awaitChannelState(channel, ChannelState.attached)

            // Re-sync should restore data
            root = channel.`object`.get().await()
            pollUntil(10.seconds) { root.get("before_detach").asString().value() == "hello" }

            assertEquals("hello", root.get("before_detach").asString().value())
        } finally {
            client.close()
        }
    }

    /**
     * @UTS objects/integration/RTO4/attach-subscribe-only-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RTO4 - attach without OBJECT_SUBSCRIBE still resolves get() with empty pool`(useBinaryProtocol: Boolean) = runTest {
        val channelName = "objects-subscribe-only-" + UUID.randomUUID()
        val client = newClient(useBinaryProtocol)
        try {
            client.connect()
            awaitState(client, ConnectionState.connected, 15.seconds)

            val root = objectChannel(client, channelName, ChannelMode.object_subscribe).`object`.get().await()

            assertIs<PathObject>(root)
            assertEquals(0L, root.size())
        } finally {
            client.close()
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

    /** A channel with the object modes (defaults to OBJECT_SUBSCRIBE + OBJECT_PUBLISH). */
    private fun objectChannel(client: AblyRealtime, name: String, vararg modes: ChannelMode): Channel =
        client.channels.get(
            name,
            ChannelOptions().apply {
                this.modes = if (modes.isEmpty()) {
                    arrayOf(ChannelMode.object_subscribe, ChannelMode.object_publish)
                } else {
                    arrayOf(*modes)
                }
            },
        )
}
