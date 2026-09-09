package io.ably.lib.liveobjects.uts.integration

import io.ably.lib.liveobjects.path.PathObject
import io.ably.lib.liveobjects.path.PathObjectListener
import io.ably.lib.liveobjects.path.PathObjectSubscriptionEvent
import io.ably.lib.liveobjects.value.LiveCounter
import io.ably.lib.liveobjects.value.LiveMap
import io.ably.lib.liveobjects.value.LiveMapValue
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ChannelOptions
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
import java.util.Collections
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

/**
 * Direct-sandbox integration test against the Ably Sandbox
 * (`sandbox.realtime.ably-nonprod.net`, via [SandboxApp.sandboxHost]) — no proxy, no
 * fault injection. Provisions one throwaway [SandboxApp] for the suite and connects real realtime
 * clients straight to the sandbox.
 *
 * End-to-end LiveObjects lifecycle: connect, sync, create/mutate objects via [PathObject], and
 * verify propagation to a second client.
 *
 * Spec points: RTO23, RTPO15, RTPO17. Source spec: `objects/integration/objects_lifecycle_test.md`.
 *
 * Each test runs once per protocol variant (JSON / msgpack) per the spec's `PROTOCOL` dimension —
 * realised here with a `useBinaryProtocol` [ParameterizedTest] parameter.
 *
 * > **Translate-only:** `channel.object.get()` resolves only once the SDK's OBJECT_SYNC processing
 * > + `RealtimeObject.get()` land, so these compile now and run once the LiveObjects engine is
 * > implemented.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ObjectsLifecycleTest {

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
     * @UTS objects/integration/RTO23-RTPO15/set-primitive-propagates-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RTO23, RTPO15 - set primitive via PathObject, second client reads it`(useBinaryProtocol: Boolean) = runTest {
        val channelName = "objects-lifecycle-" + UUID.randomUUID()
        val clientA = newClient(useBinaryProtocol)
        val clientB = newClient(useBinaryProtocol)
        try {
            clientA.connect()
            awaitState(clientA, ConnectionState.connected, 15.seconds)
            clientB.connect()
            awaitState(clientB, ConnectionState.connected, 15.seconds)

            val channelA = objectChannel(clientA, channelName)
            val channelB = objectChannel(clientB, channelName)

            val rootA = channelA.`object`.get().await()
            val rootB = channelB.`object`.get().await()

            // Client A sets a value
            rootA.set("greeting", LiveMapValue.of("hello")).await()

            // Client B subscribes and waits for the update
            val eventsB = Collections.synchronizedList(mutableListOf<PathObjectSubscriptionEvent>())
            rootB.subscribe(PathObjectListener { event -> eventsB.add(event) })
            pollUntil(10.seconds) { rootB.get("greeting").asString().value() == "hello" }

            assertEquals("hello", rootB.get("greeting").asString().value())
        } finally {
            clientA.close()
            clientB.close()
        }
    }

    /**
     * @UTS objects/integration/RTPO15/set-counter-value-type-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RTPO15 - set with LiveCounterValueType, second client reads counter`(useBinaryProtocol: Boolean) = runTest {
        val channelName = "objects-counter-create-" + UUID.randomUUID()
        val clientA = newClient(useBinaryProtocol)
        val clientB = newClient(useBinaryProtocol)
        try {
            clientA.connect()
            awaitState(clientA, ConnectionState.connected, 15.seconds)
            clientB.connect()
            awaitState(clientB, ConnectionState.connected, 15.seconds)

            val rootA = objectChannel(clientA, channelName).`object`.get().await()
            val rootB = objectChannel(clientB, channelName).`object`.get().await()

            rootA.set("my_counter", LiveMapValue.of(LiveCounter.create(42))).await()
            pollUntil(10.seconds) { rootB.get("my_counter").asLiveCounter().value() == 42.0 }

            assertEquals(42.0, rootB.get("my_counter").asLiveCounter().value())
            assertNotNull(rootB.get("my_counter").instance())
        } finally {
            clientA.close()
            clientB.close()
        }
    }

    /**
     * @UTS objects/integration/RTPO17/increment-propagates-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RTPO17 - increment counter, second client sees updated value`(useBinaryProtocol: Boolean) = runTest {
        val channelName = "objects-increment-" + UUID.randomUUID()
        val clientA = newClient(useBinaryProtocol)
        val clientB = newClient(useBinaryProtocol)
        try {
            clientA.connect()
            awaitState(clientA, ConnectionState.connected, 15.seconds)
            clientB.connect()
            awaitState(clientB, ConnectionState.connected, 15.seconds)

            val rootA = objectChannel(clientA, channelName).`object`.get().await()
            val rootB = objectChannel(clientB, channelName).`object`.get().await()

            // Create a counter first
            rootA.set("hits", LiveMapValue.of(LiveCounter.create(0))).await()
            pollUntil(10.seconds) { rootB.get("hits").asLiveCounter().value() == 0.0 }

            // Increment it
            rootA.get("hits").asLiveCounter().increment(10).await()
            pollUntil(10.seconds) { rootB.get("hits").asLiveCounter().value() == 10.0 }

            assertEquals(10.0, rootA.get("hits").asLiveCounter().value())
            assertEquals(10.0, rootB.get("hits").asLiveCounter().value())
        } finally {
            clientA.close()
            clientB.close()
        }
    }

    /**
     * @UTS objects/integration/RTPO15/set-map-value-type-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RTPO15 - set with LiveMapValueType, second client reads nested map`(useBinaryProtocol: Boolean) = runTest {
        val channelName = "objects-map-create-" + UUID.randomUUID()
        val clientA = newClient(useBinaryProtocol)
        val clientB = newClient(useBinaryProtocol)
        try {
            clientA.connect()
            awaitState(clientA, ConnectionState.connected, 15.seconds)
            clientB.connect()
            awaitState(clientB, ConnectionState.connected, 15.seconds)

            val rootA = objectChannel(clientA, channelName).`object`.get().await()
            val rootB = objectChannel(clientB, channelName).`object`.get().await()

            rootA.set(
                "settings",
                LiveMapValue.of(
                    LiveMap.create(
                        mapOf(
                            "theme" to LiveMapValue.of("dark"),
                            "fontSize" to LiveMapValue.of(14),
                        ),
                    ),
                ),
            ).await()
            pollUntil(10.seconds) {
                rootB.get("settings").asLiveMap().get("theme").asString().value() == "dark"
            }

            assertEquals("dark", rootB.get("settings").asLiveMap().get("theme").asString().value())
            assertEquals(14.0, rootB.get("settings").asLiveMap().get("fontSize").asNumber().value()?.toDouble())
        } finally {
            clientA.close()
            clientB.close()
        }
    }

    /**
     * @UTS objects/integration/RTO23/get-returns-path-object-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RTO23 - get() waits for sync and returns PathObject`(useBinaryProtocol: Boolean) = runTest {
        val channelName = "objects-get-root-" + UUID.randomUUID()
        val client = newClient(useBinaryProtocol)
        try {
            client.connect()
            awaitState(client, ConnectionState.connected, 15.seconds)

            val root = objectChannel(client, channelName).`object`.get().await()

            assertIs<PathObject>(root)
            assertEquals("", root.path())
            assertEquals(0L, root.size())
        } finally {
            client.close()
        }
    }

    /**
     * @UTS objects/integration/RTPO15/rest-provisioned-data-sync-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RTPO15 - client syncs pre-existing data provisioned via REST`(useBinaryProtocol: Boolean) = runTest {
        val channelName = "objects-rest-provision-" + UUID.randomUUID()

        // Provision data via REST before any realtime client connects (see Helpers.kt).
        // Both provisionObjectsViaRest and the realtime client below target the same nonprod
        // sandbox host (SandboxApp.sandboxHost), so the provisioned data is visible
        // to the client once the SDK's OBJECT_SYNC + RealtimeObject.get() land.
        provisionObjectsViaRest(
            app.defaultKey,
            channelName,
            listOf(mapSetOp("provisioned", valueString("from_rest"), objectId = "root")),
        )

        val client = newClient(useBinaryProtocol)
        try {
            client.connect()
            awaitState(client, ConnectionState.connected, 15.seconds)

            val root = objectChannel(client, channelName).`object`.get().await()

            assertEquals("from_rest", root.get("provisioned").asString().value())
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
