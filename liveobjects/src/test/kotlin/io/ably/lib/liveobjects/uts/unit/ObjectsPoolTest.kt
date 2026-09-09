package io.ably.lib.liveobjects.uts.unit

import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.ObjectsOperationSource
import io.ably.lib.liveobjects.ObjectsState
import io.ably.lib.liveobjects.ROOT_OBJECT_ID
import io.ably.lib.liveobjects.instance.InstanceListener
import io.ably.lib.liveobjects.instance.InstanceSubscriptionEvent
import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.message.WireObjectOperation
import io.ably.lib.liveobjects.message.WireObjectOperationAction
import io.ably.lib.liveobjects.unit.getMockAblyClientAdapter
import io.ably.lib.liveobjects.value.livecounter.InternalLiveCounter
import io.ably.lib.liveobjects.value.livemap.InternalLiveMap
import io.ably.lib.liveobjects.value.livemap.LiveMapEntry
import io.mockk.unmockkAll
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Derived from UTS spec `objects/unit/objects_pool.md` — the `ObjectsPool` data structure and
 * sync state machine (`RTO3`–`RTO9`): ATTACHED handling (`RTO4`), OBJECT_SYNC sequences
 * (`RTO5`), zero-value object creation (`RTO6`), operation buffering (`RTO7`, `RTO8`),
 * OBJECT message application (`RTO9`) and the post-sync parent-reference rebuild (`RTO5c10`).
 *
 * Internal-graph spec: asserts on the internal pool/sync state, so it lives in `:liveobjects`'s
 * own test source set — symbol map in
 * `.claude/skills/uts-to-kotlin/references/objects-mapping.md` §17 (pool & sync §17.6).
 * The spec's monolithic `pool` splits across three classes: `ro.objectsPool` (storage),
 * `ro.objectsManager` (sync/apply logic) and `ro` itself (state, ack-serials — the spec's
 * `realtime_object`).
 *
 * DEVIATION S-2 (see deviations.md): the spec's synchronous
 * `pool.processAttached(ProtocolMessage(action: ATTACHED, ...))` maps to the async
 * `handleStateChange`; these tests drive the manager steps of its attached branch
 * synchronously instead ([processAttached] below).
 */
class ObjectsPoolTest {

    private lateinit var ro: DefaultRealtimeObject

    @BeforeTest
    fun setUp() {
        // §17.1 - the spec's `pool = ObjectsPool()` / `realtime_object = RealtimeObject(pool: pool)`:
        // the pool ("root" auto-created per RTO3b) is created inside DefaultRealtimeObject.
        ro = DefaultRealtimeObject("test", getMockAblyClientAdapter())
    }

    @AfterTest
    fun tearDown() {
        // DEVIATION S-4 (see deviations.md): ObjectsPool.init starts a real GC coroutine +
        // adapter subscription - dispose it; unmockkAll clears the mockkStatic global state.
        ro.objectsPool.dispose()
        unmockkAll()
    }

    /**
     * The spec's `pool.processAttached(ProtocolMessage(action: ATTACHED, channelSerial: ..., flags: ...))`.
     * DEVIATION S-2 (see deviations.md): `handleStateChange(ChannelState.attached, hasObjects)` runs
     * asynchronously on the sequential scope, so these tests replay its attached branch synchronously:
     * RTO4d clear buffer, RTO4c start a new sync, and for HAS_OBJECTS=0 the RTO4b immediate completion.
     */
    private fun processAttached(hasObjects: Boolean, target: DefaultRealtimeObject = ro) {
        target.objectsManager.clearBufferedObjectOperations() // RTO4d
        target.objectsManager.startNewSync(null) // RTO4c
        if (!hasObjects) {
            target.objectsPool.resetToInitialPool(true) // RTO4b1, RTO4b2, RTO4b2a
            target.objectsManager.clearSyncObjectsPool() // RTO4b3
            target.objectsManager.endSync() // RTO4b4
        }
    }

    /** The spec's `pool.processObjectSync(build_object_sync_message(channel, serial, msgs))` (§17.6). */
    private fun processObjectSync(channelSerial: String, messages: List<WireObjectMessage>) =
        ro.objectsManager.handleObjectSyncMessages(messages, channelSerial)

    /** RTO5a5 - an OBJECT_SYNC with no channelSerial (the single-message sync). */
    private fun processObjectSyncNoSerial(messages: List<WireObjectMessage>) =
        ro.objectsManager.handleObjectSyncMessages(messages, null)

    /** The spec's `pool.processObjectMessage(build_object_message(channel, msgs))` (§17.6). */
    private fun processObjectMessage(messages: List<WireObjectMessage>) =
        ro.objectsManager.handleObjectMessages(messages)

    /** Captures the instance-subscription events emitted by the pool's root map. */
    private fun subscribeRootEvents(): MutableList<InstanceSubscriptionEvent> {
        val events = mutableListOf<InstanceSubscriptionEvent>()
        val root = ro.objectsPool.get(ROOT_OBJECT_ID) as InternalLiveMap
        root.subscribe(InstanceListener { event -> events.add(event) })
        return events
    }

    private fun rootMap(): InternalLiveMap = ro.objectsPool.get(ROOT_OBJECT_ID) as InternalLiveMap

    /**
     * @UTS objects/unit/RTO3/pool-init-root-0
     */
    @Test
    fun `RTO3 - ObjectsPool initialization with root InternalLiveMap`() {
        assertNotNull(ro.objectsPool.get("root"))
        val root = assertIs<InternalLiveMap>(ro.objectsPool.get("root"))
        assertTrue(root.data.isEmpty())
        assertEquals("root", root.objectId)
    }

    /**
     * @UTS objects/unit/RTO4/attached-has-objects-syncing-0
     */
    @Test
    fun `RTO4a - ATTACHED with HAS_OBJECTS flag starts SYNCING`() {
        processAttached(hasObjects = true)

        assertEquals(ObjectsState.Syncing, ro.state)
    }

    /**
     * @UTS objects/unit/RTO4b/attached-no-objects-synced-0
     */
    @Test
    fun `RTO4b - ATTACHED without HAS_OBJECTS clears pool and goes to SYNCED`() {
        ro.objectsPool.set("counter:abc@1000", InternalLiveCounter.zeroValue("counter:abc@1000", ro))
        rootMap().data["name"] = LiveMapEntry(timeserial = "01", data = dataString("Alice"))

        val updates = subscribeRootEvents()
        processAttached(hasObjects = false)

        assertEquals(ObjectsState.Synced, ro.state)
        assertNull(ro.objectsPool.get("counter:abc@1000"))
        assertNotNull(ro.objectsPool.get("root"))
        assertTrue(rootMap().data.isEmpty())
        assertTrue(updates.size >= 1)
        // DEVIATION S-1 (see deviations.md): updates[0].update == { "name": "removed" } is not
        // exposed on the subscription event - the cleared root data above covers it.
        // RTO4b2a - the update is emitted without populating objectMessage
        assertNull(updates[0].message)
    }

    /**
     * @UTS objects/unit/RTO4b2a/reset-of-empty-root-emits-no-update-0
     */
    @Test
    fun `RTO4b2a - ATTACHED without HAS_OBJECTS on an already-empty root emits no update`() {
        // Complements RTO4b/attached-no-objects-synced-0 (populated root). Here root is already
        // empty, so the RTO4b2 reset removes no keys: the LiveMapUpdate has no changed keys and, per
        // RTLM22c/RTLO4b4b, collapses to a no-op that must NOT be delivered to root subscribers.
        ro.objectsPool.set("counter:abc@1000", InternalLiveCounter.zeroValue("counter:abc@1000", ro))
        // root is already empty (zero-value InternalLiveMap per RTLM4c) - nothing to seed.
        assertTrue(rootMap().data.isEmpty())

        val updates = subscribeRootEvents()
        processAttached(hasObjects = false)

        assertEquals(ObjectsState.Synced, ro.state)
        assertNull(ro.objectsPool.get("counter:abc@1000")) // RTO4b1: non-root objects are still removed
        assertNotNull(ro.objectsPool.get("root"))
        assertTrue(rootMap().data.isEmpty())
        // RTO4b2a: no keys were removed, so the empty update collapses to a no-op and is not delivered.
        assertEquals(0, updates.size)

        // Liveness control: prove the subscription wiring is live — a reset that DOES remove a key
        // still emits, so updates.size == 0 above reflects the empty-root collapse and not a dead
        // subscription. Mirrors RTO4b/attached-no-objects-synced-0 on a SECOND pool. Emission at the
        // ObjectsPool tier is synchronous (see that case), so no polling is required.
        val ro2 = DefaultRealtimeObject("test", getMockAblyClientAdapter())
        try {
            val root2 = ro2.objectsPool.get(ROOT_OBJECT_ID) as InternalLiveMap
            root2.data["name"] = LiveMapEntry(timeserial = "01", data = dataString("Alice"))
            val control = mutableListOf<InstanceSubscriptionEvent>()
            root2.subscribe(InstanceListener { event -> control.add(event) })

            processAttached(hasObjects = false, target = ro2)

            assertTrue(control.size >= 1)
            // DEVIATION S-1 (see deviations.md): control[0].update == { "name": "removed" } is not
            // exposed on the subscription event — the cleared root2 data below covers the removal.
            assertTrue(root2.data.isEmpty())
        } finally {
            ro2.objectsPool.dispose() // DEVIATION S-4: dispose the second pool's GC coroutine
        }
    }

    /**
     * @UTS objects/unit/RTO5/sync-complete-sequence-0
     */
    @Test
    fun `RTO5 - OBJECT_SYNC complete sequence`() {
        processAttached(hasObjects = true)

        processObjectSync(
            "sync1:",
            listOf(
                buildObjectState(
                    "root", mapOf("aaa" to POOL_SERIAL),
                    map = mapState(mapOf("name" to mapEntry(dataString("Alice"), timeserial = POOL_SERIAL))),
                    createOp = mapCreateOp(),
                ),
                buildObjectState(
                    "counter:abc@1000", mapOf("aaa" to POOL_SERIAL),
                    counter = counterState(0),
                    createOp = counterCreateOp(42),
                ),
            ),
        )

        assertEquals(ObjectsState.Synced, ro.state)
        assertNotNull(ro.objectsPool.get("root"))
        assertNotNull(ro.objectsPool.get("counter:abc@1000"))
        assertEquals(dataString("Alice"), rootMap().data["name"]?.data)
        assertEquals(42.0, (ro.objectsPool.get("counter:abc@1000") as InternalLiveCounter).data.get())
    }

    /**
     * @UTS objects/unit/RTO5a2/new-sequence-discards-old-0
     */
    @Test
    fun `RTO5a2 - new sync sequence discards previous`() {
        processAttached(hasObjects = true)
        processObjectSync(
            "seq1:more",
            listOf(
                buildObjectState("counter:old@1000", mapOf("aaa" to POOL_SERIAL), counter = counterState(10)),
            ),
        )

        processObjectSync(
            "seq2:",
            listOf(
                buildObjectState("root", mapOf("aaa" to POOL_SERIAL), map = mapState(emptyMap()), createOp = mapCreateOp()),
                buildObjectState("counter:new@1000", mapOf("aaa" to POOL_SERIAL), counter = counterState(99)),
            ),
        )

        assertEquals(ObjectsState.Synced, ro.state)
        assertNull(ro.objectsPool.get("counter:old@1000"))
        assertNotNull(ro.objectsPool.get("counter:new@1000"))
    }

    /**
     * @UTS objects/unit/RTO5a6/malformed-channel-serial-treated-as-absent-0
     *
     * A present-but-malformed channelSerial (no `:` separator, so it cannot be split into
     * `<sequence id>:<cursor value>`) is handled as if the channelSerial were absent (RTO5a5): its
     * messages are applied and the sync ends (SYNCED). A warning is logged.
     */
    @Test
    fun `RTO5a6 - malformed channelSerial is treated as absent, applying data and ending sync`() {
        processAttached(hasObjects = true)
        assertEquals(ObjectsState.Syncing, ro.state)

        // "malformedserialnocolon" lacks the ':' separator, so it cannot be parsed per RTO5a1; RTO5a6
        // says to treat it as absent (RTO5a5).
        processObjectSync(
            "malformedserialnocolon",
            listOf(
                buildObjectState("counter:new@1000", mapOf("aaa" to POOL_SERIAL), counter = counterState(99)),
            ),
        )

        // Treated as absent (RTO5a5): the message was applied and the sync ended (SYNCED).
        assertEquals(ObjectsState.Synced, ro.state)
        assertNotNull(ro.objectsPool.get("counter:new@1000"))
    }

    /**
     * @UTS objects/unit/RTO5a5/absent-channel-serial-0
     *
     * An OBJECT_SYNC with no channelSerial is a valid single-message sync: its data is applied and the
     * sync ends (SYNCED). This is the baseline the RTO5a6 malformed case defers to.
     */
    @Test
    fun `RTO5a5 - absent channelSerial applies data and ends sync`() {
        processAttached(hasObjects = true)

        processObjectSyncNoSerial(
            listOf(
                buildObjectState("counter:new@1000", mapOf("aaa" to POOL_SERIAL), counter = counterState(99)),
            ),
        )

        assertEquals(ObjectsState.Synced, ro.state)
        assertNotNull(ro.objectsPool.get("counter:new@1000"))
    }

    /**
     * @UTS objects/unit/RTO5f2a/partial-map-merge-0
     */
    @Test
    fun `RTO5f2a - partial object state merge for maps`() {
        processAttached(hasObjects = true)

        processObjectSync(
            "sync1:more",
            listOf(
                buildObjectState(
                    "root", mapOf("aaa" to POOL_SERIAL),
                    map = mapState(mapOf("name" to mapEntry(dataString("Alice"), timeserial = POOL_SERIAL))),
                ),
            ),
        )
        processObjectSync(
            "sync1:",
            listOf(
                buildObjectState(
                    "root", mapOf("aaa" to POOL_SERIAL),
                    map = mapState(mapOf("age" to mapEntry(dataNumber(30), timeserial = POOL_SERIAL))),
                    createOp = mapCreateOp(),
                ),
            ),
        )

        assertEquals(dataString("Alice"), rootMap().data["name"]?.data)
        assertEquals(dataNumber(30), rootMap().data["age"]?.data)
    }

    /**
     * @UTS objects/unit/RTO5c2/remove-absent-objects-0
     */
    @Test
    fun `RTO5c2 - sync completion removes objects not in sync`() {
        val oldCounter = InternalLiveCounter.zeroValue("counter:old@1000", ro)
        oldCounter.data.set(99.0)
        ro.objectsPool.set("counter:old@1000", oldCounter)
        processAttached(hasObjects = true)

        processObjectSync(
            "sync1:",
            listOf(
                buildObjectState("root", mapOf("aaa" to POOL_SERIAL), map = mapState(emptyMap()), createOp = mapCreateOp()),
            ),
        )

        assertNull(ro.objectsPool.get("counter:old@1000"))
        assertNotNull(ro.objectsPool.get("root"))
    }

    /**
     * @UTS objects/unit/RTO5c9/clear-applied-on-ack-serials-0
     */
    @Test
    fun `RTO5c9 - sync completion clears appliedOnAckSerials`() {
        ro.appliedOnAckSerials.addAll(setOf("serial-1", "serial-2"))
        processAttached(hasObjects = true)

        processObjectSync(
            "sync1:",
            listOf(
                buildObjectState("root", mapOf("aaa" to POOL_SERIAL), map = mapState(emptyMap()), createOp = mapCreateOp()),
            ),
        )

        assertEquals(emptySet(), ro.appliedOnAckSerials)
    }

    /**
     * @UTS objects/unit/RTO8a/buffer-during-syncing-0
     */
    @Test
    fun `RTO8a - OBJECT messages buffered during SYNCING`() {
        processAttached(hasObjects = true)

        processObjectMessage(listOf(buildCounterInc("counter:abc@1000", 5, "01", "site1")))

        assertEquals(ObjectsState.Syncing, ro.state)
        assertEquals(1, ro.objectsManager.bufferedObjectOperations.size)
        assertNull(ro.objectsPool.get("counter:abc@1000"))
    }

    /**
     * @UTS objects/unit/RTO5c6/apply-buffered-on-sync-0
     */
    @Test
    fun `RTO5c6 - buffered operations applied on sync completion`() {
        processAttached(hasObjects = true)
        processObjectMessage(listOf(buildCounterInc("counter:abc@1000", 10, "02", "site1")))

        processObjectSync(
            "sync1:",
            listOf(
                buildObjectState("root", mapOf("aaa" to POOL_SERIAL), map = mapState(emptyMap()), createOp = mapCreateOp()),
                buildObjectState(
                    "counter:abc@1000", mapOf("aaa" to POOL_SERIAL),
                    counter = counterState(0),
                    createOp = counterCreateOp(100),
                ),
            ),
        )

        assertEquals(110.0, (ro.objectsPool.get("counter:abc@1000") as InternalLiveCounter).data.get())
        assertEquals(0, ro.objectsManager.bufferedObjectOperations.size)
    }

    /**
     * @UTS objects/unit/RTO9a1/null-operation-warning-0
     */
    @Test
    fun `RTO9a1 - null operation is discarded with warning`() {
        ro.state = ObjectsState.Synced

        processObjectMessage(listOf(WireObjectMessage(serial = "01", siteCode = "site1", operation = null)))

        assertEquals(1, ro.objectsPool.all().size)
    }

    /**
     * @UTS objects/unit/RTO9a3/dedup-applied-on-ack-0
     */
    @Test
    fun `RTO9a3 - appliedOnAckSerials deduplication`() {
        ro.state = ObjectsState.Synced
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)
        counter.data.set(10.0)
        ro.objectsPool.set("counter:abc@1000", counter)
        ro.appliedOnAckSerials.add("echo-serial-1")

        processObjectMessage(listOf(buildCounterInc("counter:abc@1000", 5, "echo-serial-1", "site1")))

        assertEquals(10.0, counter.data.get())
        assertFalse("echo-serial-1" in ro.appliedOnAckSerials)
    }

    /**
     * @UTS objects/unit/RTO9a2a4/local-source-adds-serial-0
     */
    @Test
    fun `RTO9a2a4 - LOCAL source adds serial to appliedOnAckSerials`() {
        ro.state = ObjectsState.Synced
        ro.objectsPool.set("counter:abc@1000", InternalLiveCounter.zeroValue("counter:abc@1000", ro))

        ro.objectsManager.applyObjectMessages(
            listOf(buildCounterInc("counter:abc@1000", 5, "local-serial-1", "test-site")),
            ObjectsOperationSource.LOCAL,
        )

        assertTrue("local-serial-1" in ro.appliedOnAckSerials)
        assertEquals(5.0, (ro.objectsPool.get("counter:abc@1000") as InternalLiveCounter).data.get())
    }

    /**
     * @UTS objects/unit/RTO9a2b/unsupported-action-warning-0
     */
    @Test
    fun `RTO9a2b - unsupported action is discarded with warning`() {
        ro.state = ObjectsState.Synced

        processObjectMessage(
            listOf(
                WireObjectMessage(
                    serial = "01",
                    siteCode = "site1",
                    operation = WireObjectOperation(
                        action = WireObjectOperationAction.Unknown,
                        objectId = "counter:abc@1000",
                    ),
                ),
            ),
        )

        assertEquals(1, ro.objectsPool.all().size)
    }

    /**
     * @UTS objects/unit/RTO6/zero-value-from-prefix-0
     */
    @Test
    fun `RTO6 - zero-value object creation from objectId prefix`() {
        ro.state = ObjectsState.Synced

        processObjectMessage(listOf(buildCounterInc("counter:new@2000", 5, "01", "site1")))
        processObjectMessage(listOf(buildMapSet("map:new@2000", "key", dataString("val"), "01", "site1")))

        assertNotNull(ro.objectsPool.get("counter:new@2000"))
        val counter = assertIs<InternalLiveCounter>(ro.objectsPool.get("counter:new@2000"))
        assertEquals(5.0, counter.data.get())

        assertNotNull(ro.objectsPool.get("map:new@2000"))
        val map = assertIs<InternalLiveMap>(ro.objectsPool.get("map:new@2000"))
        assertEquals(dataString("val"), map.data["key"]?.data)
    }

    /**
     * @UTS objects/unit/RTO5d/null-object-skipped-0
     */
    @Test
    fun `RTO5d - OBJECT_SYNC with null object field is skipped`() {
        processAttached(hasObjects = true)

        processObjectSync(
            "sync1:",
            listOf(
                WireObjectMessage(), // ObjectMessage(object: null)
                buildObjectState("root", mapOf("aaa" to POOL_SERIAL), map = mapState(emptyMap()), createOp = mapCreateOp()),
            ),
        )

        assertEquals(ObjectsState.Synced, ro.state)
    }

    /**
     * @UTS objects/unit/RTO5f3/unsupported-type-skipped-0
     */
    @Test
    fun `RTO5f3 - OBJECT_SYNC with unsupported object type is skipped`() {
        processAttached(hasObjects = true)

        processObjectSync(
            "sync1:",
            listOf(
                buildObjectState("root", mapOf("aaa" to POOL_SERIAL), map = mapState(emptyMap()), createOp = mapCreateOp()),
                // ObjectMessage(object: { objectId: "unknown:xyz@1000", siteTimeserials: {} }) - no map/counter
                buildObjectState("unknown:xyz@1000", emptyMap()),
            ),
        )

        assertEquals(ObjectsState.Synced, ro.state)
        assertNull(ro.objectsPool.get("unknown:xyz@1000"))
    }

    /**
     * @UTS objects/unit/RTO5e/object-sync-transitions-syncing-0
     */
    @Test
    fun `RTO5e - OBJECT_SYNC transitions to SYNCING`() {
        processObjectSync(
            "sync1:more",
            listOf(
                buildObjectState("root", mapOf("aaa" to POOL_SERIAL), map = mapState(emptyMap())),
            ),
        )

        assertEquals(ObjectsState.Syncing, ro.state)
    }

    /**
     * @UTS objects/unit/RTO5c7/sync-emits-updates-0
     */
    @Test
    fun `RTO5c7 - sync completion emits updates for existing objects`() {
        rootMap().data["name"] = LiveMapEntry(timeserial = "01", data = dataString("Old"))

        val updates = subscribeRootEvents()
        processAttached(hasObjects = true)

        processObjectSync(
            "sync1:",
            listOf(
                buildObjectState(
                    "root", mapOf("aaa" to POOL_SERIAL),
                    map = mapState(mapOf("name" to mapEntry(dataString("New"), timeserial = POOL_SERIAL))),
                    createOp = mapCreateOp(),
                ),
            ),
        )

        assertTrue(updates.size >= 1)
        // DEVIATION S-1 (see deviations.md): updates[0].update["name"] == "updated" is not
        // exposed on the subscription event - the replaced entry data below covers it.
        assertEquals(dataString("New"), rootMap().data["name"]?.data)
    }

    /**
     * @UTS objects/unit/RTO5f2b/partial-counter-error-0
     */
    @Test
    fun `RTO5f2b - partial counter state logs error`() {
        processAttached(hasObjects = true)

        processObjectSync(
            "sync1:more",
            listOf(
                buildObjectState("counter:abc@1000", mapOf("aaa" to POOL_SERIAL), counter = counterState(10)),
            ),
        )
        processObjectSync(
            "sync1:",
            listOf(
                buildObjectState("root", mapOf("aaa" to POOL_SERIAL), map = mapState(emptyMap()), createOp = mapCreateOp()),
                buildObjectState("counter:abc@1000", mapOf("aaa" to POOL_SERIAL), counter = counterState(5)),
            ),
        )

        assertEquals(10.0, (ro.objectsPool.get("counter:abc@1000") as InternalLiveCounter).data.get())
    }

    /**
     * @UTS objects/unit/RTO4d/attached-clears-buffer-0
     */
    @Test
    fun `RTO4d - ATTACHED clears buffered operations`() {
        processAttached(hasObjects = true)

        processObjectMessage(listOf(buildCounterInc("counter:abc@1000", 5, "01", "site1")))
        assertEquals(1, ro.objectsManager.bufferedObjectOperations.size)

        processAttached(hasObjects = true)

        assertEquals(0, ro.objectsManager.bufferedObjectOperations.size)
    }

    /**
     * @UTS objects/unit/RTO4-RTO5/attached-during-syncing-resets-0
     */
    @Test
    fun `RTO4 RTO5 - ATTACHED during SYNCING resets sync`() {
        processAttached(hasObjects = true)
        processObjectSync(
            "sync1:more",
            listOf(
                buildObjectState("counter:old@1000", mapOf("aaa" to POOL_SERIAL), counter = counterState(10)),
            ),
        )
        assertEquals(ObjectsState.Syncing, ro.state)

        processAttached(hasObjects = true)

        processObjectSync(
            "sync2:",
            listOf(
                buildObjectState("root", mapOf("aaa" to POOL_SERIAL), map = mapState(emptyMap()), createOp = mapCreateOp()),
                buildObjectState("counter:new@1000", mapOf("aaa" to POOL_SERIAL), counter = counterState(99)),
            ),
        )

        assertEquals(ObjectsState.Synced, ro.state)
        assertNull(ro.objectsPool.get("counter:old@1000"))
        assertNotNull(ro.objectsPool.get("counter:new@1000"))
    }

    /**
     * @UTS objects/unit/RTO5-RTO7/new-sync-keeps-buffer-0
     */
    @Test
    fun `RTO5 RTO7 - new OBJECT_SYNC sequence does NOT clear buffer`() {
        processAttached(hasObjects = true)

        processObjectMessage(listOf(buildCounterInc("counter:abc@1000", 5, "01", "site1")))
        assertEquals(1, ro.objectsManager.bufferedObjectOperations.size)

        processObjectSync(
            "seq2:",
            listOf(
                buildObjectState("root", mapOf("aaa" to POOL_SERIAL), map = mapState(emptyMap()), createOp = mapCreateOp()),
                buildObjectState(
                    "counter:abc@1000", mapOf("aaa" to POOL_SERIAL),
                    counter = counterState(0),
                    createOp = counterCreateOp(100),
                ),
            ),
        )

        assertEquals(ObjectsState.Synced, ro.state)
        assertEquals(105.0, (ro.objectsPool.get("counter:abc@1000") as InternalLiveCounter).data.get())
    }

    /**
     * @UTS objects/unit/RTO7-RTO8/buffer-without-attached-0
     */
    @Test
    fun `RTO7 RTO8 - OBJECT messages buffered even without preceding ATTACHED`() {
        assertEquals(ObjectsState.Initialized, ro.state)

        processObjectMessage(listOf(buildCounterInc("counter:abc@1000", 5, "01", "site1")))

        assertEquals(1, ro.objectsManager.bufferedObjectOperations.size)
    }

    /**
     * @UTS objects/unit/RTO5c-RTLM23/sync-clear-timeserial-hides-create-entries-0
     */
    @Test
    fun `RTO5c RTLM23 - sync with clearTimeserial hides initial createOp entries`() {
        processAttached(hasObjects = true)

        processObjectSync(
            "sync1:",
            listOf(
                buildObjectState(
                    "root", mapOf("aaa" to POOL_SERIAL),
                    map = mapState(entries = emptyMap(), clearTimeserial = "05"),
                    createOp = mapCreateOp(
                        entries = mapOf(
                            "old_key" to mapEntry(dataString("old"), timeserial = "03"),
                            "new_key" to mapEntry(dataString("new"), timeserial = "07"),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(ObjectsState.Synced, ro.state)
        assertFalse(rootMap().data.containsKey("old_key"))
        assertEquals(dataString("new"), rootMap().data["new_key"]?.data)
    }

    /**
     * @UTS objects/unit/RTO5c10/sync-rebuilds-parent-refs-0
     */
    @Test
    fun `RTO5c10 - sync completion rebuilds parentReferences`() {
        processAttached(hasObjects = true)

        processObjectSync(
            "sync1:",
            listOf(
                buildObjectState(
                    "root", mapOf("aaa" to POOL_SERIAL),
                    map = mapState(
                        linkedMapOf(
                            "score" to mapEntry(dataObjectId("counter:score@1000"), timeserial = POOL_SERIAL),
                            "profile" to mapEntry(dataObjectId("map:profile@1000"), timeserial = POOL_SERIAL),
                            "name" to mapEntry(dataString("Alice"), timeserial = POOL_SERIAL),
                        ),
                    ),
                    createOp = mapCreateOp(),
                ),
                buildObjectState(
                    "counter:score@1000", mapOf("aaa" to POOL_SERIAL),
                    counter = counterState(0),
                    createOp = counterCreateOp(100),
                ),
                buildObjectState(
                    "map:profile@1000", mapOf("aaa" to POOL_SERIAL),
                    map = mapState(
                        linkedMapOf(
                            "nested_counter" to mapEntry(dataObjectId("counter:nested@1000"), timeserial = POOL_SERIAL),
                        ),
                    ),
                    createOp = mapCreateOp(),
                ),
                buildObjectState(
                    "counter:nested@1000", mapOf("aaa" to POOL_SERIAL),
                    counter = counterState(0),
                    createOp = counterCreateOp(5),
                ),
            ),
        )

        // root is not referenced by any parent
        assertEquals(emptyMap(), ro.objectsPool.get("root")!!.parentReferences)

        // counter:score@1000 is referenced by root at key "score"
        assertEquals<Map<String, Set<String>>>(
            mapOf("root" to setOf("score")),
            ro.objectsPool.get("counter:score@1000")!!.parentReferences,
        )

        // map:profile@1000 is referenced by root at key "profile"
        assertEquals<Map<String, Set<String>>>(
            mapOf("root" to setOf("profile")),
            ro.objectsPool.get("map:profile@1000")!!.parentReferences,
        )

        // counter:nested@1000 is referenced by map:profile@1000 at key "nested_counter"
        assertEquals<Map<String, Set<String>>>(
            mapOf("map:profile@1000" to setOf("nested_counter")),
            ro.objectsPool.get("counter:nested@1000")!!.parentReferences,
        )

        // Primitive-valued entries ("name") do not appear in any parentReferences - covered by
        // the exact-equality assertions above (no extra entries anywhere).
    }

    /**
     * @UTS objects/unit/RTO5c10/resync-rebuilds-parent-refs-0
     */
    @Test
    fun `RTO5c10 - re-sync rebuilds parentReferences with new tree structure`() {
        processAttached(hasObjects = true)

        // First sync: counter:abc@1000 is a child of root
        processObjectSync(
            "sync1:",
            listOf(
                buildObjectState(
                    "root", mapOf("aaa" to POOL_SERIAL),
                    map = mapState(
                        linkedMapOf("counter_key" to mapEntry(dataObjectId("counter:abc@1000"), timeserial = POOL_SERIAL)),
                    ),
                    createOp = mapCreateOp(),
                ),
                buildObjectState(
                    "counter:abc@1000", mapOf("aaa" to POOL_SERIAL),
                    counter = counterState(0),
                    createOp = counterCreateOp(10),
                ),
            ),
        )

        // Verify first sync parentReferences
        assertEquals<Map<String, Set<String>>>(
            mapOf("root" to setOf("counter_key")),
            ro.objectsPool.get("counter:abc@1000")!!.parentReferences,
        )

        // Second sync: counter:abc@1000 is now a child of map:wrapper@1000, not root
        processAttached(hasObjects = true)
        processObjectSync(
            "sync2:",
            listOf(
                buildObjectState(
                    "root", mapOf("aaa" to remoteSerial(0)),
                    map = mapState(
                        linkedMapOf("wrapper" to mapEntry(dataObjectId("map:wrapper@1000"), timeserial = remoteSerial(0))),
                    ),
                    createOp = mapCreateOp(),
                ),
                buildObjectState(
                    "map:wrapper@1000", mapOf("aaa" to remoteSerial(0)),
                    map = mapState(
                        linkedMapOf("moved_counter" to mapEntry(dataObjectId("counter:abc@1000"), timeserial = remoteSerial(0))),
                    ),
                    createOp = mapCreateOp(),
                ),
                buildObjectState(
                    "counter:abc@1000", mapOf("aaa" to remoteSerial(0)),
                    counter = counterState(0),
                    createOp = counterCreateOp(20),
                ),
            ),
        )

        assertEquals(ObjectsState.Synced, ro.state)

        // root is not referenced by any parent
        assertEquals(emptyMap(), ro.objectsPool.get("root")!!.parentReferences)

        // map:wrapper@1000 is now a child of root at key "wrapper"
        assertEquals<Map<String, Set<String>>>(
            mapOf("root" to setOf("wrapper")),
            ro.objectsPool.get("map:wrapper@1000")!!.parentReferences,
        )

        // counter:abc@1000 is now a child of map:wrapper@1000, NOT of root
        assertEquals<Map<String, Set<String>>>(
            mapOf("map:wrapper@1000" to setOf("moved_counter")),
            ro.objectsPool.get("counter:abc@1000")!!.parentReferences,
        )
    }

    /**
     * @UTS objects/unit/RTO5c10/empty-sync-parent-refs-0
     */
    @Test
    fun `RTO5c10 - empty sync leaves root with empty parentReferences`() {
        // First, do a normal sync to populate parentReferences
        processAttached(hasObjects = true)
        processObjectSync(
            "sync1:",
            listOf(
                buildObjectState(
                    "root", mapOf("aaa" to POOL_SERIAL),
                    map = mapState(
                        linkedMapOf("child" to mapEntry(dataObjectId("counter:child@1000"), timeserial = POOL_SERIAL)),
                    ),
                    createOp = mapCreateOp(),
                ),
                buildObjectState(
                    "counter:child@1000", mapOf("aaa" to POOL_SERIAL),
                    counter = counterState(0),
                    createOp = counterCreateOp(1),
                ),
            ),
        )

        // Verify parentReferences are populated after first sync
        assertEquals<Map<String, Set<String>>>(
            mapOf("root" to setOf("child")),
            ro.objectsPool.get("counter:child@1000")!!.parentReferences,
        )

        // Empty sync: ATTACHED without HAS_OBJECTS
        processAttached(hasObjects = false)

        assertEquals(ObjectsState.Synced, ro.state)

        // counter:child@1000 was removed from pool (RTO4b1)
        assertNull(ro.objectsPool.get("counter:child@1000"))

        // root exists with empty data and empty parentReferences
        assertNotNull(ro.objectsPool.get("root"))
        assertTrue(rootMap().data.isEmpty())
        assertEquals(emptyMap(), ro.objectsPool.get("root")!!.parentReferences)
    }
}
