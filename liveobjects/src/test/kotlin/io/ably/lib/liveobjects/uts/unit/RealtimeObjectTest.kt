package io.ably.lib.liveobjects.uts.unit

import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.ROOT_OBJECT_ID
import io.ably.lib.liveobjects.instance.InstanceListener
import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.message.WireObjectOperationAction
import io.ably.lib.liveobjects.path.PathObject
import io.ably.lib.liveobjects.path.PathObjectListener
import io.ably.lib.liveobjects.path.PathObjectSubscriptionEvent
import io.ably.lib.liveobjects.path.PathObjectSubscriptionOptions
import io.ably.lib.liveobjects.path.types.LiveMapPathObject
import io.ably.lib.liveobjects.state.ObjectStateChange
import io.ably.lib.liveobjects.state.ObjectStateEvent
import io.ably.lib.liveobjects.value.LiveMapValue
import io.ably.lib.liveobjects.value.livecounter.InternalLiveCounter
import io.ably.lib.liveobjects.value.livemap.InternalLiveMap
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.AblyException
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ChannelOptions
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.types.PublishResult
import io.ably.lib.uts.infra.awaitChannelState
import io.ably.lib.uts.infra.pollUntil
import io.ably.lib.uts.infra.unit.ConnectionDetails
import io.ably.lib.uts.infra.unit.FakeClock
import io.ably.lib.uts.infra.unit.MockEvent
import io.ably.lib.uts.infra.unit.MockWebSocket
import io.ably.lib.uts.infra.unit.TestRealtimeClient
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

/**
 * Derived from UTS spec `objects/unit/realtime_object.md` — RealtimeObject behaviour
 * (`RTO2`, `RTO10`, `RTO15`, `RTO17`–`RTO20`, `RTO22`–`RTO27`): `get()` semantics, publish /
 * publishAndApply (driven through public mutations), sync-state events, access/write API
 * preconditions, the shared subscription register, tombstone GC, and the objects data
 * lifecycle on channel state transitions.
 *
 * Public-tier spec: uses only the public API plus the module-local `Helpers.kt`
 * (wire-level RTO15 assertions read the typed `Wire*` state off the captured OBJECT
 * ProtocolMessages, per the `captured_messages` mapping in `objects-mapping.md` §13).
 */
class RealtimeObjectTest {

    // ------------------------------------------------------------------
    // Per-test mock construction (the spec's inline MockWebSocket setups)
    // ------------------------------------------------------------------

    /**
     * The spec's CONNECTED response: `ConnectionDetails { connectionId: "conn-1", connectionKey:
     * "key-1", siteCode: "test-site", objectsGCGracePeriod: ... }`. `maxMessageSize` is set for
     * the same reason as in `Helpers.kt` (an unset value of 0 makes RTO15d reject every OBJECT
     * publish); `maxIdleInterval` keeps the fake-timer tests' 24h virtual advance below the
     * transport idle timeout.
     */
    private fun connectedMessage(
        includeSiteCode: Boolean = true,
        gcGracePeriod: Long = 86_400_000L,
    ): ProtocolMessage = ProtocolMessage(ProtocolMessage.Action.connected).apply {
        connectionId = "conn-1"
        connectionDetails = ConnectionDetails {
            connectionKey = "key-1"
            if (includeSiteCode) siteCode = SITE_CODE
            objectsGCGracePeriod = gcGracePeriod
            maxMessageSize = 65_536
            maxIdleInterval = 864_000_000L
        }
    }

    private fun attachedMessage(
        channelName: String?,
        channelSerial: String?,
        hasObjects: Boolean = true,
        modeFlags: List<ProtocolMessage.Flag> = emptyList(),
    ): ProtocolMessage = ProtocolMessage(ProtocolMessage.Action.attached).apply {
        this.channel = channelName
        this.channelSerial = channelSerial
        if (hasObjects) setFlag(ProtocolMessage.Flag.has_objects)
        modeFlags.forEach { setFlag(it) }
    }

    /**
     * A per-test MockWebSocket mirroring the spec's inline mocks: CONNECTED on connect, [onAttach]
     * for ATTACH, optional [onObject] for OBJECT publishes and — like the shared mock in
     * `helpers/standard_test_pool.md` — an outbound DETACH is answered with DETACHED.
     */
    private fun buildMockWebSocket(
        connected: ProtocolMessage,
        onAttach: (MockWebSocket, ProtocolMessage) -> Unit,
        onObject: ((MockWebSocket, ProtocolMessage) -> Unit)? = null,
    ): MockWebSocket {
        lateinit var mockWs: MockWebSocket
        mockWs = MockWebSocket {
            onConnectionAttempt = { conn -> conn.respondWithSuccess(connected) }
            onMessageFromClient = { msg ->
                when (msg.action) {
                    ProtocolMessage.Action.attach -> onAttach(mockWs, msg)
                    ProtocolMessage.Action.`object` -> onObject?.invoke(mockWs, msg)
                    ProtocolMessage.Action.detach -> mockWs.sendToClient(
                        ProtocolMessage(ProtocolMessage.Action.detached).apply { this.channel = msg.channel },
                    )
                    else -> Unit
                }
            }
        }
        return mockWs
    }

    /** ATTACH → ATTACHED (+ optional granted-mode flags) followed by the standard-pool sync. */
    private fun attachedWithSync(
        channelSerial: String = "sync1:",
        modeFlags: List<ProtocolMessage.Flag> = emptyList(),
    ): (MockWebSocket, ProtocolMessage) -> Unit = { mockWs, msg ->
        mockWs.sendToClient(attachedMessage(msg.channel, channelSerial, modeFlags = modeFlags))
        mockWs.sendToClient(buildObjectSyncMessage(msg.channel!!, channelSerial, STANDARD_POOL_OBJECTS))
    }

    /** The shared harness's OBJECT auto-ACK: one `ack_serial(msgSerial, i)` per state entry. */
    private val ackPerState: (MockWebSocket, ProtocolMessage) -> Unit = { mockWs, msg ->
        val serials = (msg.state?.indices ?: IntRange.EMPTY).map { ackSerial(msg.msgSerial, it) }
        mockWs.sendToClient(buildAckMessage(msg.msgSerial, serials))
    }

    private fun newClient(
        mockWs: MockWebSocket,
        echo: Boolean = true,
        fakeClock: FakeClock? = null,
    ): AblyRealtime = TestRealtimeClient {
        key = "fake:key"
        echoMessages = echo
        install(mockWs)
        fakeClock?.let { enableFakeTimers(it) }
    }

    private fun AblyRealtime.objectsChannel(
        name: String,
        modes: Array<ChannelMode> = arrayOf(ChannelMode.object_subscribe, ChannelMode.object_publish),
    ): Channel = channels.get(name, ChannelOptions().apply { this.modes = modes })

    /**
     * The spec's client-side detach against the shared synced-channel harness: the
     * `setup_synced_channel` mock (`Helpers.kt`, matching `helpers/standard_test_pool.md`)
     * answers the outbound DETACH with DETACHED, so this just detaches and awaits the state.
     */
    private suspend fun detachClientSide(channel: Channel) {
        channel.detach()
        awaitChannelState(channel, ChannelState.detached)
    }

    /**
     * Awaits the SYNCING transition after injecting an ATTACHED — the mock delivers messages
     * asynchronously, so the spec's implied ordering (the ATTACHED is processed before the next
     * step) is enforced via the public sync-state event.
     */
    private suspend fun sendAttachedAndAwaitSyncing(channel: Channel, mockWs: MockWebSocket, channelSerial: String) {
        val syncing = AtomicInteger(0)
        val sub = channel.`object`.on(ObjectStateEvent.SYNCING, ObjectStateChange.Listener { syncing.incrementAndGet() })
        mockWs.sendToClient(attachedMessage(channel.name, channelSerial))
        pollUntil(5.seconds) { syncing.get() >= 1 }
        sub.unsubscribe()
    }

    /** The fake-timers variant of the spec's `{ client, channel, root, mock_ws }` tuple. */
    private data class FakeTimersChannel(
        val client: AblyRealtime,
        val channel: Channel,
        val root: LiveMapPathObject,
        val mockWs: MockWebSocket,
        val fakeClock: FakeClock,
    )

    /**
     * The spec's `enable_fake_timers()` + `setup_synced_channel(...)`: the module `Helpers.kt`
     * setup has no fake-timer hook, so an equivalent local harness (same CONNECTED / ATTACH /
     * OBJECT behaviour as the shared mock) installs the [FakeClock] on the client.
     */
    private suspend fun setupSyncedChannelWithFakeTimers(
        channelName: String,
        gcGracePeriod: Long = 86_400_000L,
    ): FakeTimersChannel {
        val fakeClock = FakeClock()
        val mockWs = buildMockWebSocket(
            connected = connectedMessage(gcGracePeriod = gcGracePeriod),
            onAttach = attachedWithSync(),
            onObject = ackPerState,
        )
        val client = newClient(mockWs, fakeClock = fakeClock)
        val channel = client.objectsChannel(channelName)
        val root = channel.`object`.get().await()
        return FakeTimersChannel(client, channel, root, mockWs, fakeClock)
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    /**
     * @UTS objects/unit/RTO23/get-returns-path-object-0
     */
    @Test
    fun `RTO23 - get returns PathObject wrapping root`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        assertIs<PathObject>(root)
        assertEquals("", root.path()) // the spec's `root.path == []` — the root path is empty

        client.close()
    }

    /**
     * @UTS objects/unit/RTO23a/get-requires-subscribe-mode-0
     */
    @Test
    fun `RTO23a - get requires OBJECT_SUBSCRIBE mode`() = runTest {
        val mockWs = buildMockWebSocket(
            connected = connectedMessage(),
            onAttach = { mock, msg -> mock.sendToClient(attachedMessage(msg.channel, "sync1:")) },
        )
        val client = newClient(mockWs)
        val channel = client.objectsChannel("test", modes = arrayOf(ChannelMode.object_publish))

        val error = assertFailsWith<AblyException> { channel.`object`.get().await() }

        assertEquals(40024, error.errorInfo.code)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO23e/get-reattaches-detached-0
     */
    @Test
    fun `RTO23e - get re-attaches a DETACHED channel`() = runTest {
        val mockWs = buildMockWebSocket(
            connected = connectedMessage(),
            onAttach = attachedWithSync(),
        )
        val client = newClient(mockWs)
        val channel = client.objectsChannel("test", modes = arrayOf(ChannelMode.object_subscribe))

        // Attach and sync first, then detach.
        channel.`object`.get().await()
        channel.detach()
        awaitChannelState(channel, ChannelState.detached)

        // get() on a DETACHED channel triggers ensure-active-channel (RTL33b) -> implicit
        // re-attach -> resolves.
        val root = channel.`object`.get().await()

        assertIs<PathObject>(root)
        assertEquals("", root.path())
        assertEquals(ChannelState.attached, channel.state)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO23c/get-waits-for-synced-0
     */
    @Test
    fun `RTO23c - get waits for SYNCED state`() = runTest {
        val attachSent = AtomicInteger(0)
        val mockWs = buildMockWebSocket(
            connected = connectedMessage(),
            onAttach = { mock, msg ->
                attachSent.incrementAndGet()
                mock.sendToClient(attachedMessage(msg.channel, "sync1:cursor"))
            },
        )
        val client = newClient(mockWs)
        val channel = client.objectsChannel("test")

        val getFuture = channel.`object`.get()

        pollUntil(5.seconds) { attachSent.get() >= 1 }

        mockWs.sendToClient(buildObjectSyncMessage("test", "sync1:", STANDARD_POOL_OBJECTS))

        val root = getFuture.await()

        assertIs<PathObject>(root)
        assertEquals("", root.path())

        client.close()
    }

    /**
     * @UTS objects/unit/RTO15/publish-sends-object-pm-0
     */
    @Test
    fun `RTO15 - publish sends OBJECT ProtocolMessage`() = runTest {
        // The shared synced-channel harness captures every outgoing OBJECT publish in the mock's
        // event log (the spec's `captured_messages`); its auto-ACK serial scheme replaces the
        // spec's inline "serial-0" ACK — observationally equivalent for the wire assertions here.
        val (client, _, root, mockWs) = setupSyncedChannel("test")

        // Drive the internal publish (RTO15) through a public mutation — only the observable
        // wire behaviour is asserted here.
        root.get("score").asLiveCounter().increment(5).await()

        val captured = mockWs.capturedObjectMessages()
        assertEquals(1, captured.size)
        assertEquals(ProtocolMessage.Action.`object`, captured[0].action) // RTO15e1
        assertEquals("test", captured[0].channel) // RTO15e2
        val state = assertNotNull(captured[0].state)
        assertEquals(1, state.size)
        // RTO15e3 - the state entry is the encoded ObjectMessage for the driven mutation
        val operation = assertNotNull((state[0] as WireObjectMessage).operation)
        assertEquals(WireObjectOperationAction.CounterInc, operation.action)
        assertEquals("counter:score@1000", operation.objectId)
        assertEquals(5.0, assertNotNull(operation.counterInc).number)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO20/publish-and-apply-local-0
     */
    @Test
    fun `RTO20 - publishAndApply applies locally on ACK`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().increment(10).await()

        assertEquals(110.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTO20c/missing-site-code-0
     */
    @Test
    fun `RTO20c - publishAndApply logs error when siteCode missing`() = runTest {
        val mockWs = buildMockWebSocket(
            connected = connectedMessage(includeSiteCode = false),
            onAttach = attachedWithSync(),
            onObject = { mock, msg -> mock.sendToClient(buildAckMessage(msg.msgSerial, listOf("serial-0"))) },
        )
        val client = newClient(mockWs)
        val channel = client.objectsChannel("test")
        val root = channel.`object`.get().await()

        root.get("score").asLiveCounter().increment(10).await()

        // The ACK-based local apply is skipped (siteCode unavailable) — value unchanged.
        assertEquals(100.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTO20d1/null-serial-skipped-0
     */
    @Test
    fun `RTO20d1 - null serial in PublishResult is skipped`() = runTest {
        val mockWs = buildMockWebSocket(
            connected = connectedMessage(),
            onAttach = attachedWithSync(),
            onObject = { mock, msg ->
                // build_ack_message(msg.msgSerial, [null]) — a PublishResult whose single serial
                // is null.
                mock.sendToClient(
                    ProtocolMessage(ProtocolMessage.Action.ack).apply {
                        msgSerial = msg.msgSerial
                        count = 1
                        res = arrayOf(PublishResult(arrayOfNulls<String>(1)))
                    },
                )
            },
        )
        val client = newClient(mockWs)
        val channel = client.objectsChannel("test")
        val root = channel.`object`.get().await()

        root.get("score").asLiveCounter().increment(10).await()

        assertEquals(100.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTO20d4/empty-synthetic-list-skips-sync-wait-0
     */
    @Test
    fun `RTO20d4 - empty synthetic list skips the RTO20e sync wait`() = runTest {
        val mockWs = buildMockWebSocket(
            connected = connectedMessage(),
            onAttach = attachedWithSync(),
            onObject = { mock, msg ->
                // build_ack_message(msg.msgSerial, [null]) - the single serial is null, so per
                // RTO20d1 every synthetic ObjectMessage is skipped and the synthetic list is empty.
                mock.sendToClient(
                    ProtocolMessage(ProtocolMessage.Action.ack).apply {
                        msgSerial = msg.msgSerial
                        count = 1
                        res = arrayOf(PublishResult(arrayOfNulls<String>(1)))
                    },
                )
            },
        )
        val client = newClient(mockWs)
        val channel = client.objectsChannel("test")
        val root = channel.`object`.get().await()

        // Move the objects sync state back to SYNCING so a normal publishAndApply would park in the
        // RTO20e wait for SYNCED (cf. the RTO20e waits-for-synced case). No sync-completing message
        // is ever sent: if the RTO20e wait were performed this future would never resolve.
        sendAttachedAndAwaitSyncing(channel, mockWs, "sync2:cursor")

        // The synthetic list is empty, so there is nothing to apply locally and publishAndApply
        // completes without the RTO20e wait.
        root.get("score").asLiveCounter().increment(10).await()

        // Resolution despite the channel never reaching SYNCED proves the RTO20e wait was skipped;
        // nothing was applied locally, so the local value is unchanged.
        assertEquals(100.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTO20e/waits-for-synced-0
     */
    @Test
    fun `RTO20e - publishAndApply waits for SYNCED during SYNCING`() = runTest {
        val (client, channel, root, mockWs) = setupSyncedChannel("test")

        sendAttachedAndAwaitSyncing(channel, mockWs, "sync2:cursor")

        val incFuture = root.get("score").asLiveCounter().increment(10)

        // Per RTO20e the write must WAIT for the sync to reach SYNCED: while still SYNCING the
        // increment must not have applied yet.
        assertFalse(incFuture.isDone)
        assertEquals(100.0, root.get("score").asLiveCounter().value())

        mockWs.sendToClient(buildObjectSyncMessage("test", "sync2:", STANDARD_POOL_OBJECTS))

        incFuture.await()

        assertEquals(110.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTO20e1/fails-on-channel-detached-0
     */
    @Test
    fun `RTO20e1 - publishAndApply fails when channel enters DETACHED during sync wait`() = runTest {
        val (client, channel, root, mockWs) = setupSyncedChannel("test")

        sendAttachedAndAwaitSyncing(channel, mockWs, "sync2:cursor")

        val incFuture = root.get("score").asLiveCounter().increment(10)

        // The publish and its ACK complete against the mock; publishAndApply parks in the RTO20e
        // wait for SYNCED.

        // process_pending_events(): flush the sequential scope so increment()'s publishAndApply
        // has parked in the RTO20e sync wait before the injected DETACHED.
        (channel.`object` as DefaultRealtimeObject).asyncFuture { }.await()
        assertFalse(incFuture.isDone)

        // A client-side detach then moves the channel to DETACHED.
        detachClientSide(channel)

        val error = assertFailsWith<AblyException> { incFuture.await() }

        assertEquals(92008, error.errorInfo.code)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO20e1/fails-on-channel-failed-0
     */
    @Test
    fun `RTO20e1 - publishAndApply fails when channel enters FAILED during sync wait`() = runTest {
        val (client, channel, root, mockWs) = setupSyncedChannel("test")

        sendAttachedAndAwaitSyncing(channel, mockWs, "sync2:cursor")

        val incFuture = root.get("score").asLiveCounter().increment(10)

        // The publish and its ACK complete against the mock; publishAndApply parks in the RTO20e
        // wait for SYNCED.

        // process_pending_events(): flush the sequential scope so increment()'s publishAndApply
        // has parked in the RTO20e sync wait before the injected ERROR->FAILED.
        (channel.`object` as DefaultRealtimeObject).asyncFuture { }.await()
        assertFalse(incFuture.isDone)

        // Then the channel ERROR moves the channel to FAILED.
        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.error).apply {
                this.channel = "test"
                this.error = ErrorInfo("Channel failed", 400, 90000)
            },
        )

        val error = assertFailsWith<AblyException> { incFuture.await() }

        assertEquals(92008, error.errorInfo.code)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO23c1/fails-on-channel-detached-0
     *
     * RTO23c1 mirrors RTO20e1 (above): a get() parked in the RTO23c wait for SYNCED must be failed —
     * not orphaned — with the 92008 error when the channel enters DETACHED while waiting.
     */
    @Test
    fun `RTO23c1 - get fails when channel enters DETACHED during sync wait`() = runTest {
        val (client, channel, _, mockWs) = setupSyncedChannel("test")

        // Force a re-sync so the objects state is SYNCING again, then get() must park in the RTO23c wait.
        sendAttachedAndAwaitSyncing(channel, mockWs, "sync2:cursor")

        val getFuture = channel.`object`.get()

        // process_pending_events(): flush the sequential scope so get() has parked its sync waiter
        // before the injected DETACHED.
        (channel.`object` as DefaultRealtimeObject).asyncFuture { }.await()

        // While still SYNCING the get() cannot complete — it is parked waiting for SYNCED.
        assertFalse(getFuture.isDone)

        // A client-side detach then moves the channel to DETACHED.
        detachClientSide(channel)

        val error = assertFailsWith<AblyException> { getFuture.await() }

        assertEquals(92008, error.errorInfo.code)
        assertEquals(400, error.errorInfo.statusCode)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO23c1/fails-on-channel-suspended-0
     *
     * RTO23c1 — the SUSPENDED case, driven directly through the objects channel-state handler
     * (as RTO27 does) since the shared mock has no SUSPENDED trigger.
     */
    @Test
    fun `RTO23c1 - get fails when channel enters SUSPENDED during sync wait`() = runTest {
        val (client, channel, _, mockWs) = setupSyncedChannel("test")
        val ro = channel.`object` as DefaultRealtimeObject

        sendAttachedAndAwaitSyncing(channel, mockWs, "sync2:cursor")

        val getFuture = channel.`object`.get()

        // process_pending_events(): flush the sequential scope so get() has parked its sync waiter
        // before the injected SUSPENDED.
        ro.asyncFuture { }.await()
        assertFalse(getFuture.isDone)

        // RTO27b SUSPENDED retains objects data but RTO23c1 still fails any in-flight get() sync wait.
        ro.handleStateChange(ChannelState.suspended, false)
        ro.asyncFuture { }.await() // flush the sequential scope

        val error = assertFailsWith<AblyException> { getFuture.await() }

        assertEquals(92008, error.errorInfo.code)
        assertEquals(400, error.errorInfo.statusCode)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO23c1/fails-on-channel-failed-0
     *
     * RTO23c1 — the FAILED case additionally asserts the `cause`: it must be set to the channel's
     * errorReason (here the injected FAILED error) when that reason is present.
     */
    @Test
    fun `RTO23c1 - get fails with cause when channel enters FAILED during sync wait`() = runTest {
        val (client, channel, _, mockWs) = setupSyncedChannel("test")

        sendAttachedAndAwaitSyncing(channel, mockWs, "sync2:cursor")

        val getFuture = channel.`object`.get()

        // process_pending_events(): flush the sequential scope so get() has parked its sync waiter
        // before the injected ERROR->FAILED.
        (channel.`object` as DefaultRealtimeObject).asyncFuture { }.await()
        assertFalse(getFuture.isDone)

        // A channel ERROR moves the channel to FAILED and sets its errorReason.
        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.error).apply {
                this.channel = "test"
                this.error = ErrorInfo("Channel failed", 400, 90000)
            },
        )

        val error = assertFailsWith<AblyException> { getFuture.await() }

        assertEquals(92008, error.errorInfo.code)
        assertEquals(400, error.errorInfo.statusCode)
        // RTO23c1 - cause is set to the channel's errorReason.
        val cause = assertIs<AblyException>(error.cause)
        assertEquals(90000, cause.errorInfo.code)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO17/sync-state-events-0
     */
    @Test
    fun `RTO17 RTO18 - sync state events`() = runTest {
        val mockWs = buildMockWebSocket(
            connected = connectedMessage(),
            onAttach = { mock, msg -> mock.sendToClient(attachedMessage(msg.channel, "sync1:cursor")) },
        )
        val client = newClient(mockWs)
        val channel = client.objectsChannel("test")

        val events = mutableListOf<String>()
        channel.`object`.on(ObjectStateEvent.SYNCING, ObjectStateChange.Listener { events.add("SYNCING") })
        channel.`object`.on(ObjectStateEvent.SYNCED, ObjectStateChange.Listener { events.add("SYNCED") })

        val getFuture = channel.`object`.get()

        pollUntil(5.seconds) { events.size >= 1 }

        mockWs.sendToClient(buildObjectSyncMessage("test", "sync1:", STANDARD_POOL_OBJECTS))

        getFuture.await()

        // events CONTAINS_IN_ORDER ["SYNCING", "SYNCED"]
        val iterator = events.iterator()
        assertEquals("SYNCING", iterator.next())
        assertEquals("SYNCED", iterator.next())

        client.close()
    }

    /**
     * @UTS objects/unit/RTO18d/duplicate-listener-0
     *
     * ADAPTED (see deviations.md): RTO18d's default asserts the RTE4 reference behaviour — the same
     * listener registered twice fires **twice**. ably-java's core `EventEmitter` keys per-event
     * listeners by instance, so a duplicate registration de-duplicates and the listener fires **once**.
     * RTO18d's OPTIONAL note sanctions SDKs that de-duplicate asserting `call_count == 1`, which this
     * test pins (a regression that lost dedup would fire twice in the same emission and fail it).
     */
    @Test
    fun `RTO18d - duplicate listener registered twice fires once (ably-java de-duplicates)`() = runTest {
        val (client, channel, _, mockWs) = setupSyncedChannel("test")
        val callCount = AtomicInteger(0)
        val listener = ObjectStateChange.Listener { callCount.incrementAndGet() }
        channel.`object`.on(ObjectStateEvent.SYNCED, listener)
        channel.`object`.on(ObjectStateEvent.SYNCED, listener)

        mockWs.sendToClient(attachedMessage("test", "sync2:cursor"))
        mockWs.sendToClient(buildObjectSyncMessage("test", "sync2:", STANDARD_POOL_OBJECTS))

        // ably-java de-duplicates → the SYNCED emission invokes the (single) registration exactly once
        pollUntil(5.seconds) { callCount.get() >= 1 }

        assertEquals(1, callCount.get())

        client.close()
    }

    /**
     * @UTS objects/unit/RTO19/off-deregisters-0
     */
    @Test
    fun `RTO19 - off deregisters listener`() = runTest {
        val (client, channel, _, mockWs) = setupSyncedChannel("test")
        val callCount = AtomicInteger(0)
        val listener = ObjectStateChange.Listener { callCount.incrementAndGet() }
        val sub = channel.`object`.on(ObjectStateEvent.SYNCED, listener)
        // The spec's `sub.off()` — the ably-java Subscription method is unsubscribe().
        sub.unsubscribe()
        // NOTE: negative-assertion quiescence (helpers/standard_test_pool.md) — a still-subscribed
        // control listener provides a delivery to await on the same SYNCED emission before
        // asserting the deregistered listener did not fire.
        val control = AtomicInteger(0)
        channel.`object`.on(ObjectStateEvent.SYNCED, ObjectStateChange.Listener { control.incrementAndGet() })

        mockWs.sendToClient(attachedMessage("test", "sync2:cursor"))
        mockWs.sendToClient(buildObjectSyncMessage("test", "sync2:", STANDARD_POOL_OBJECTS))
        pollUntil(5.seconds) { control.get() >= 1 }

        assertEquals(0, callCount.get())

        client.close()
    }

    /**
     * @UTS objects/unit/RTO2/mode-enforcement-0
     */
    @Test
    fun `RTO2 - channel mode enforcement`() = runTest {
        val mockWs = buildMockWebSocket(
            connected = connectedMessage(),
            // ATTACHED grants only OBJECT_SUBSCRIBE (the spec's `modes: ["OBJECT_SUBSCRIBE"]`).
            onAttach = attachedWithSync(modeFlags = listOf(ProtocolMessage.Flag.object_subscribe)),
        )
        val client = newClient(mockWs)
        val channel = client.objectsChannel("test")
        val root = channel.`object`.get().await()

        val error = assertFailsWith<AblyException> { root.set("name", LiveMapValue.of("Bob")).await() }

        assertEquals(40024, error.errorInfo.code)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO23e/get-rejects-failed-0
     */
    @Test
    fun `RTO23e - get on a FAILED channel rejects with 90001`() = runTest {
        val mockWs = buildMockWebSocket(
            connected = connectedMessage(),
            onAttach = { mock, msg ->
                mock.sendToClient(
                    ProtocolMessage(ProtocolMessage.Action.error).apply {
                        this.channel = msg.channel
                        this.error = ErrorInfo("Channel error", 400, 90000)
                    },
                )
            },
        )
        val client = newClient(mockWs)
        val channel = client.objectsChannel("test", modes = arrayOf(ChannelMode.object_subscribe))

        // Trigger attach which will fail, putting channel into FAILED state.
        channel.attach()
        awaitChannelState(channel, ChannelState.failed)

        val error = assertFailsWith<AblyException> { channel.`object`.get().await() }

        assertEquals(90001, error.errorInfo.code)
        assertEquals(400, error.errorInfo.statusCode)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO25a/access-requires-subscribe-mode-0
     */
    @Test
    fun `RTO25a - access API precondition requires OBJECT_SUBSCRIBE mode`() = runTest {
        val mockWs = buildMockWebSocket(
            connected = connectedMessage(),
            onAttach = attachedWithSync(modeFlags = listOf(ProtocolMessage.Flag.object_publish)),
        )
        val client = newClient(mockWs)
        val channel = client.objectsChannel("test", modes = arrayOf(ChannelMode.object_publish))

        val error = assertFailsWith<AblyException> { channel.`object`.get().await() }

        assertEquals(40024, error.errorInfo.code)
        assertEquals(400, error.errorInfo.statusCode)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO25b/access-throws-detached-0
     */
    @Test
    fun `RTO25b - access API precondition throws on DETACHED channel`() = runTest {
        val (client, channel, root, mockWs) = setupSyncedChannel("test")

        // Detach the channel client-side after sync.
        detachClientSide(channel)

        val error = assertFailsWith<AblyException> { root.keys() }

        assertEquals(90001, error.errorInfo.code)
        assertEquals(400, error.errorInfo.statusCode)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO25b/access-throws-failed-0
     */
    @Test
    fun `RTO25b - access API precondition throws on FAILED channel`() = runTest {
        val (client, channel, root, mockWs) = setupSyncedChannel("test")

        // Force channel to FAILED state.
        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.error).apply {
                this.channel = "test"
                this.error = ErrorInfo("Channel error", 400, 90000)
            },
        )
        awaitChannelState(channel, ChannelState.failed)

        val error = assertFailsWith<AblyException> { root.keys() }

        assertEquals(90001, error.errorInfo.code)
        assertEquals(400, error.errorInfo.statusCode)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO26a/write-requires-publish-mode-0
     */
    @Test
    fun `RTO26a - write API precondition requires OBJECT_PUBLISH mode`() = runTest {
        val mockWs = buildMockWebSocket(
            connected = connectedMessage(),
            onAttach = attachedWithSync(modeFlags = listOf(ProtocolMessage.Flag.object_subscribe)),
        )
        val client = newClient(mockWs)
        val channel = client.objectsChannel("test", modes = arrayOf(ChannelMode.object_subscribe))
        val root = channel.`object`.get().await()

        val error = assertFailsWith<AblyException> { root.set("name", LiveMapValue.of("Bob")).await() }

        assertEquals(40024, error.errorInfo.code)
        assertEquals(400, error.errorInfo.statusCode)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO26b/write-throws-detached-0
     */
    @Test
    fun `RTO26b - write API precondition throws on DETACHED channel`() = runTest {
        val (client, channel, root, mockWs) = setupSyncedChannel("test")

        // Detach the channel client-side after sync.
        detachClientSide(channel)

        val error = assertFailsWith<AblyException> { root.set("name", LiveMapValue.of("Bob")).await() }

        assertEquals(90001, error.errorInfo.code)
        assertEquals(400, error.errorInfo.statusCode)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO26b/write-throws-failed-0
     */
    @Test
    fun `RTO26b - write API precondition throws on FAILED channel`() = runTest {
        val (client, channel, root, mockWs) = setupSyncedChannel("test")

        // Force channel to FAILED state.
        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.error).apply {
                this.channel = "test"
                this.error = ErrorInfo("Channel error", 400, 90000)
            },
        )
        awaitChannelState(channel, ChannelState.failed)

        val error = assertFailsWith<AblyException> { root.set("name", LiveMapValue.of("Bob")).await() }

        assertEquals(90001, error.errorInfo.code)
        assertEquals(400, error.errorInfo.statusCode)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO26c/write-throws-echo-disabled-0
     */
    @Test
    fun `RTO26c - write API precondition throws when echoMessages is false`() = runTest {
        val mockWs = buildMockWebSocket(
            connected = connectedMessage(),
            onAttach = attachedWithSync(),
        )
        val client = newClient(mockWs, echo = false)
        val channel = client.objectsChannel("test")
        val root = channel.`object`.get().await()

        val error = assertFailsWith<AblyException> { root.set("name", LiveMapValue.of("Bob")).await() }

        assertEquals(40000, error.errorInfo.code)
        assertEquals(400, error.errorInfo.statusCode)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO24a/single-register-instance-0
     */
    @Test
    fun `RTO24a - RealtimeObject maintains a single PathObjectSubscriptionRegister`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")

        val eventsRoot = mutableListOf<PathObjectSubscriptionEvent>()
        val eventsScore = mutableListOf<PathObjectSubscriptionEvent>()

        // Subscribe via root PathObject at path [].
        root.subscribe(PathObjectListener { event -> eventsRoot.add(event) })

        // Subscribe via a deeper PathObject at path ["score"].
        root.get("score").subscribe(PathObjectListener { event -> eventsScore.add(event) })

        // Trigger an update on the score counter. siteCode "remote" is absent from the pool's
        // siteTimeserials, so the op passes the newness check (RTLO4a) regardless of serial
        // ordering.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 5, "t:1", "remote"))),
        )
        pollUntil(5.seconds) { eventsScore.size >= 1 }
        pollUntil(5.seconds) { eventsRoot.size >= 1 }

        // Both subscriptions are managed by the same register and both fire.
        assertTrue(eventsRoot.size >= 1)
        assertTrue(eventsScore.size >= 1)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO24c1/coverage-prefix-depth-0
     */
    @Test
    fun `RTO24c1 - subscription coverage prefix match with depth constraint`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")

        val shallowEvents = mutableListOf<PathObjectSubscriptionEvent>()
        val deepEvents = mutableListOf<PathObjectSubscriptionEvent>()

        // Subscribe at root with depth 1 — per RTO24c2b this covers ONLY root's own path ([]),
        // NOT its children (a child like ["score"] is relativeDepth 1-0+1 = 2 > 1).
        root.subscribe(PathObjectListener { event -> shallowEvents.add(event) }, PathObjectSubscriptionOptions(1))

        // Subscribe at root with no depth limit — covers everything.
        root.subscribe(PathObjectListener { event -> deepEvents.add(event) })

        // Update root itself (a MAP_SET on root — candidate path [] is covered by depth 1).
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { deepEvents.size >= 1 }

        // Update a child of root (path ["score"], relativeDepth 2) — NOT covered by depth 1,
        // covered by deep.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 5, "t:2", "remote"))),
        )
        pollUntil(5.seconds) { deepEvents.size >= 2 }

        // Negative-assertion quiescence: the shallow listener fired exactly once on the FIRST
        // dispatch (the root self-update at []) and must NOT fire on the second (child ["score"])
        // dispatch. The deep listener is the control that fires on both; poll the shallow listener
        // too so its count isn't racing.
        pollUntil(5.seconds) { shallowEvents.size >= 1 }

        // Shallow subscription (depth 1) only sees the root self-update, not the child update.
        assertEquals(1, shallowEvents.size)
        // Deep subscription (no depth limit) sees both updates.
        assertTrue(deepEvents.size >= 2)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO10/gc-tombstoned-objects-0
     */
    @Test
    fun `RTO10 - GC removes tombstoned objects past grace period`() = runTest {
        val (client, _, root, mockWs, fakeClock) = setupSyncedChannelWithFakeTimers("test")

        // Tombstone stamped "now": only the ADVANCE_TIME below makes it GC-eligible.
        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildObjectDelete("counter:score@1000", "99", "site1", fakeClock.currentTimeMillis())),
            ),
        )

        fakeClock.advance(86_400_000L + 300_000L)

        pollUntil(5.seconds) { root.get("score").asLiveCounter().value() == null }
        assertNull(root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTO10c1b1/gc-root-never-removed-0
     */
    @Test
    fun `RTO10c1b1 - GC never removes the root object`() = runTest {
        val (client, _, root, mockWs, fakeClock) = setupSyncedChannelWithFakeTimers("test")

        // Rogue OBJECT_DELETE targeting the root object: rejected per RTLO4e10.
        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildObjectDelete("root", remoteSerial(0), "remote", fakeClock.currentTimeMillis())),
            ),
        )

        assertEquals("Alice", root.get("name").asString().value()) // root not tombstoned, data untouched

        fakeClock.advance(86_400_000L + 300_000L)

        // root must still be live: a subsequent operation still applies to the same root object
        // the client holds.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), remoteSerial(1), "remote"))),
        )
        pollUntil(5.seconds) { root.get("name").asString().value() == "Bob" }

        assertEquals("Bob", root.get("name").asString().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTO20/echo-dedup-0
     */
    @Test
    fun `RTO20 - echo deduplication via appliedOnAckSerials`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().increment(10).await()
        val scoreAfterApply = root.get("score").asLiveCounter().value()

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 10, ackSerial(0, 0), SITE_CODE))),
        )
        val scoreAfterEcho = root.get("score").asLiveCounter().value()

        assertEquals(110.0, scoreAfterApply)
        assertEquals(110.0, scoreAfterEcho)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO20f/ack-no-site-timeserials-update-0
     */
    @Test
    fun `RTO20f - apply-on-ACK does not update siteTimeserials`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().increment(10).await()
        assertEquals(110.0, root.get("score").asLiveCounter().value())

        // Send inbound COUNTER_INC from siteCode SITE_CODE with serial below_ack_serial(9): a
        // serial that is NOT the apply-on-ACK serial (so RTO9a3 echo dedup does not discard it)
        // yet sorts below ack_serial(0, 0). If LOCAL incorrectly set
        // siteTimeserials[SITE_CODE] = "t:1:0", this fails the newness check and value stays 110;
        // if LOCAL correctly left siteTimeserials untouched, SITE_CODE has no entry and the op
        // applies, reaching 120.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 10, belowAckSerial(9), SITE_CODE))),
        )
        pollUntil(5.seconds) { root.get("score").asLiveCounter().value() == 120.0 }

        assertEquals(120.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTO20/ack-after-echo-no-double-apply-0
     */
    @Test
    fun `RTO20 - ACK after echo does not double-apply`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannelNoAck("test")

        val incFuture = root.get("score").asLiveCounter().increment(10)

        // Wait for the publish to reach the transport before injecting the echo/ACK — an ACK
        // that arrives while no message is pending on the connection is discarded, and incFuture
        // would never complete.
        pollUntil(5.seconds) { mockWs.capturedObjectMessages().size >= 1 }

        // Send the echo BEFORE the ACK.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 10, ackSerial(0, 0), SITE_CODE))),
        )

        // Now send the ACK.
        mockWs.sendToClient(buildAckMessage(0, listOf(ackSerial(0, 0))))

        incFuture.await()

        assertEquals(110.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTO5c9-RTO20/ack-serials-cleared-on-resync-0
     */
    @Test
    fun `RTO5c9 RTO20 - appliedOnAckSerials cleared on re-sync`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().increment(10).await()
        assertEquals(110.0, root.get("score").asLiveCounter().value())

        // Trigger re-sync — appliedOnAckSerials should be cleared per RTO5c9. (The mock delivers
        // asynchronously, so the spec's post-resync `== 100` assertion is awaited.)
        mockWs.sendToClient(attachedMessage("test", "sync2:cursor"))
        mockWs.sendToClient(buildObjectSyncMessage("test", "sync2:", STANDARD_POOL_OBJECTS))
        pollUntil(5.seconds) { root.get("score").asLiveCounter().value() == 100.0 }
        assertEquals(100.0, root.get("score").asLiveCounter().value())

        // Replay the same serial (ack_serial(0, 0)) that was used for apply-on-ACK. If
        // appliedOnAckSerials was cleared, this applies normally. If NOT cleared, dedup (RTO9a3)
        // would reject it and score stays 100.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 10, ackSerial(0, 0), SITE_CODE))),
        )
        pollUntil(5.seconds) { root.get("score").asLiveCounter().value() == 110.0 }

        assertEquals(110.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTO20/subscription-fires-on-ack-apply-0
     */
    @Test
    fun `RTO20 - subscription fires on apply-on-ACK`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.get("score").subscribe(PathObjectListener { event -> events.add(event) })

        root.get("score").asLiveCounter().increment(10).await()

        pollUntil(5.seconds) { events.size >= 1 }
        assertTrue(events.size >= 1)
        assertEquals(110.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTO23/get-implicit-attach-0
     */
    @Test
    fun `RTO23 - get implicitly attaches channel`() = runTest {
        val mockWs = buildMockWebSocket(
            connected = connectedMessage(),
            onAttach = attachedWithSync(),
            onObject = ackPerState,
        )
        val client = newClient(mockWs)
        val channel = client.objectsChannel("test")

        assertEquals(ChannelState.initialized, channel.state)

        val root = channel.`object`.get().await()

        assertIs<PathObject>(root)
        assertEquals("", root.path())
        assertEquals(ChannelState.attached, channel.state)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO23d/get-resolves-immediately-synced-0
     */
    @Test
    fun `RTO23d - get resolves immediately when already SYNCED`() = runTest {
        val (client, channel, _, _) = setupSyncedChannel("test")

        val root2 = channel.`object`.get().await()

        assertIs<PathObject>(root2)
        assertEquals("", root2.path())

        client.close()
    }

    /**
     * @UTS objects/unit/RTO10b1/gc-grace-period-source-0
     */
    @Test
    fun `RTO10b1 - GC grace period from ConnectionDetails`() = runTest {
        val (client, _, root, mockWs, fakeClock) = setupSyncedChannelWithFakeTimers("test", gcGracePeriod = 5000L)

        // Tombstone stamped "now": after ADVANCE_TIME(6000) it is eligible under the 5000ms
        // server-provided grace but NOT under the 24h default, so this test fails if the
        // implementation ignores ConnectionDetails.objectsGCGracePeriod.
        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildObjectDelete("counter:score@1000", "99", "site1", fakeClock.currentTimeMillis())),
            ),
        )

        // Short grace period (5000ms) — advance past it.
        fakeClock.advance(5000L + 1000L)

        pollUntil(5.seconds) { root.get("score").asLiveCounter().value() == null }
        assertNull(root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTO17-RTO18/sync-event-sequences-0
     */
    @Test
    fun `RTO17 RTO18 - sync event sequences for all state transitions`() = runTest {
        // Scenario "initial attach": a genuine FIRST attach can only be observed on a fresh,
        // NON-synced channel with the SYNCING/SYNCED listeners registered BEFORE attach().
        run {
            val mockWs = buildMockWebSocket(
                connected = connectedMessage(),
                onAttach = attachedWithSync(),
            )
            val client = newClient(mockWs)
            val channel = client.objectsChannel("test")
            val events = mutableListOf<String>()
            channel.`object`.on(ObjectStateEvent.SYNCING, ObjectStateChange.Listener { events.add("SYNCING") })
            channel.`object`.on(ObjectStateEvent.SYNCED, ObjectStateChange.Listener { events.add("SYNCED") })

            channel.attach()
            pollUntil(5.seconds) { events.size >= 2 }

            assertEquals(listOf("SYNCING", "SYNCED"), events.toList())
            client.close()
        }

        // Scenario "re-sync on new ATTACHED".
        run {
            val (client, channel, _, mockWs) = setupSyncedChannel("test")
            val events = mutableListOf<String>()
            channel.`object`.on(ObjectStateEvent.SYNCING, ObjectStateChange.Listener { events.add("SYNCING") })
            channel.`object`.on(ObjectStateEvent.SYNCED, ObjectStateChange.Listener { events.add("SYNCED") })

            mockWs.sendToClient(attachedMessage("test", "sync3:cursor"))
            mockWs.sendToClient(buildObjectSyncMessage("test", "sync3:", STANDARD_POOL_OBJECTS))
            pollUntil(5.seconds) { events.size >= 2 }

            assertEquals(listOf("SYNCING", "SYNCED"), events.toList())
            client.close()
        }

        // Scenario "ATTACHED without HAS_OBJECTS": RTO4c transitions the (currently SYNCED) sync
        // state to SYNCING for ANY ATTACHED → emits SYNCING; RTO4b (no HAS_OBJECTS) then completes
        // the sync immediately via RTO4b4 → emits SYNCED.
        run {
            val (client, channel, _, mockWs) = setupSyncedChannel("test")
            val events = mutableListOf<String>()
            channel.`object`.on(ObjectStateEvent.SYNCING, ObjectStateChange.Listener { events.add("SYNCING") })
            channel.`object`.on(ObjectStateEvent.SYNCED, ObjectStateChange.Listener { events.add("SYNCED") })

            mockWs.sendToClient(attachedMessage("test", "sync4:", hasObjects = false))
            pollUntil(5.seconds) { events.size >= 2 }

            assertEquals(listOf("SYNCING", "SYNCED"), events.toList())
            client.close()
        }

        // NOTE: no "re-attach after detach" scenario — see the spec (realtime_object.md): at the
        // objects layer a detach emits no sync events and the re-attach drives the same onAttached
        // path as "re-sync on new ATTACHED" above, so it is redundant here.
    }

    /**
     * @UTS objects/unit/RTO27/clears-objects-data-on-detached-failed-0
     *
     * White-box mapping (objects-mapping.md §17): the abstract `channel.object` is the internal
     * [DefaultRealtimeObject], `channel.object.processChannelState(state)` →
     * [DefaultRealtimeObject.handleStateChange] `(state, false)`, and `channel.object.objectsPool` →
     * [DefaultRealtimeObject.objectsPool]. `handleStateChange` launches on the internal sequential
     * scope, so an empty [DefaultRealtimeObject.asyncFuture] is awaited to flush it (deviation S-2).
     */
    @Test
    fun `RTO27 - DETACHED and FAILED clear objects data without emitting`() = runTest {
        for (state in listOf(ChannelState.detached, ChannelState.failed)) {
            val (client, channel, _, _) = setupSyncedChannel("test")
            val ro = channel.`object` as DefaultRealtimeObject
            val pool = ro.objectsPool
            val root = pool.get(ROOT_OBJECT_ID) as InternalLiveMap
            val scoreCounter = pool.get("counter:score@1000") as InternalLiveCounter
            val profileMap = pool.get("map:profile@1000") as InternalLiveMap // nested map

            // Sanity: the synced pool carries the standard-pool data.
            assertTrue(root.data.containsKey("name"), "precondition: root has \"name\" ($state)")
            assertEquals(100.0, scoreCounter.value(), "precondition: counter value is 100 ($state)")
            assertTrue(profileMap.data.containsKey("email"), "precondition: nested map has \"email\" ($state)")

            // The clear must NOT emit update events (clearObjectsData(false)).
            val updates = AtomicInteger(0)
            root.subscribe(InstanceListener { updates.incrementAndGet() })

            ro.handleStateChange(state, false)
            ro.asyncFuture { }.await() // flush the sequential scope

            // RTO27a1: EVERY object's data is cleared — root, the counter, AND the nested map
            // (checked independently via the pool so a nested-object regression isn't hidden by the
            // root clear); the objects themselves remain in the pool.
            assertTrue(root.data.isEmpty(), "root data must be cleared on $state")
            assertNotNull(pool.get("counter:score@1000"), "counter must remain in the pool on $state")
            assertEquals(0.0, scoreCounter.value(), "counter data must be cleared on $state")
            assertNotNull(pool.get("map:profile@1000"), "nested map must remain in the pool on $state")
            assertTrue(profileMap.data.isEmpty(), "nested map data must be cleared on $state")
            assertEquals(0, updates.get(), "no update events must be emitted on $state")

            client.close()
        }
    }

    /**
     * @UTS objects/unit/RTO27/retains-objects-data-on-suspended-0
     */
    @Test
    fun `RTO27 - SUSPENDED retains objects data`() = runTest {
        val (client, channel, _, _) = setupSyncedChannel("test")
        val ro = channel.`object` as DefaultRealtimeObject
        val pool = ro.objectsPool
        val root = pool.get(ROOT_OBJECT_ID) as InternalLiveMap
        val scoreCounter = pool.get("counter:score@1000") as InternalLiveCounter
        val profileMap = pool.get("map:profile@1000") as InternalLiveMap // nested map

        assertTrue(root.data.containsKey("name"), "precondition: root has \"name\"")
        assertEquals(100.0, scoreCounter.value(), "precondition: counter value is 100")
        assertTrue(profileMap.data.containsKey("email"), "precondition: nested map has \"email\"")

        ro.handleStateChange(ChannelState.suspended, false)
        ro.asyncFuture { }.await() // flush the sequential scope

        // RTO27b: data retained unchanged — root, the counter, and the nested map.
        assertTrue(root.data.containsKey("name"), "root data must be retained on SUSPENDED")
        assertEquals(100.0, scoreCounter.value(), "counter data must be retained on SUSPENDED")
        assertTrue(profileMap.data.containsKey("email"), "nested map data must be retained on SUSPENDED")

        client.close()
    }
}
