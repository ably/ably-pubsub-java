package io.ably.lib.liveobjects.uts.unit

import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.ObjectsOperationSource
import io.ably.lib.liveobjects.message.WireMapCreate
import io.ably.lib.liveobjects.message.WireObjectsMapSemantics
import io.ably.lib.liveobjects.unit.getMockAblyClientAdapter
import io.ably.lib.liveobjects.value.ObjectUpdate
import io.ably.lib.liveobjects.value.livecounter.InternalLiveCounter
import io.ably.lib.liveobjects.value.livemap.InternalLiveMap
import io.ably.lib.liveobjects.value.livemap.LiveMapEntry
import io.ably.lib.liveobjects.value.livemap.LiveMapManager
import io.ably.lib.liveobjects.value.livemap.MapChange
import io.ably.lib.liveobjects.value.livemap.isEntryOrRefTombstoned
import io.ably.lib.liveobjects.value.noOp
import io.ably.lib.util.Clock
import io.ably.lib.util.SystemClock
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
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
 * Derived from UTS spec `objects/unit/internal_live_map.md` — the `InternalLiveMap` LWW-map
 * CRDT data structure: set/remove/clear operations (`RTLM7`–`RTLM9`, `RTLM24`), create
 * operations (`RTLM16`, `RTLM23`), data replacement during sync (`RTLM6`), tombstoning
 * (`RTLM15e`, `RTLO4e`, `RTLO5`), GC (`RTLM19`), diff calculation (`RTLM22`) and
 * parentReferences maintenance (`RTLM7a3`, `RTLM7g2`, `RTLM8a3`, `RTLM24e1c`, `RTLO4e9`).
 *
 * Internal-graph spec: asserts on the internal CRDT graph, so it lives in `:liveobjects`'s own
 * test source set — symbol map in `.claude/skills/uts-to-kotlin/references/objects-mapping.md`
 * §17 (instantiation §17.1, map §17.4, update object §17.5).
 *
 * The op path (`applyObject`) returns the `ObjectUpdate` per the spec contract (RTLC9g/RTLM7f), so
 * op-path tests assert the returned update directly, exactly like the sync path (`applyObjectSync`).
 */
class InternalLiveMapTest {

    private lateinit var ro: DefaultRealtimeObject

    @BeforeTest
    fun setUp() {
        // §17.1 - internal classes have no public constructors; build them against a
        // DefaultRealtimeObject backed by the mocked adapter. The spec's `pool` is ro.objectsPool.
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
     * @UTS objects/unit/RTLM4/zero-value-0
     */
    @Test
    fun `RTLM4 - zero-value InternalLiveMap`() {
        val map = InternalLiveMap.zeroValue("root", ro)

        assertTrue(map.data.isEmpty())
        assertNull(map.clearTimeserial)
        assertFalse(map.isTombstoned)
        assertFalse(map.createOperationIsMerged)
        assertEquals(emptyMap(), map.siteTimeserials)
    }

    /**
     * @UTS objects/unit/RTLM7/map-set-new-entry-0
     */
    @Test
    fun `RTLM7 - MAP_SET creates new entry`() {
        val map = InternalLiveMap.zeroValue("root", ro)

        val msg = buildMapSet("root", "name", dataString("Alice"), "01", "site1")
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(dataString("Alice"), map.data["name"]?.data)
        assertEquals("01", map.data["name"]?.timeserial)
        assertEquals(false, map.data["name"]?.isTombstoned)
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        assertEquals(mapOf("name" to MapChange.Updated), mapUpdate.update) // update.update == { "name": "updated" }
        assertEquals(msg, mapUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLM7/map-set-update-entry-0
     */
    @Test
    fun `RTLM7 - MAP_SET updates existing entry`() {
        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["name"] = LiveMapEntry(timeserial = "01", data = dataString("Alice"))

        val msg = buildMapSet("root", "name", dataString("Bob"), "02", "site1")
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(dataString("Bob"), map.data["name"]?.data)
        assertEquals("02", map.data["name"]?.timeserial)
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        assertEquals(mapOf("name" to MapChange.Updated), mapUpdate.update) // update.update == { "name": "updated" }
        assertEquals(msg, mapUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLM9/lww-reject-stale-0
     */
    @Test
    fun `RTLM9 - LWW rejects stale serial on existing entry`() {
        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["name"] = LiveMapEntry(timeserial = "05", data = dataString("Alice"))

        val msg = buildMapSet("root", "name", dataString("Bob"), "03", "site1")
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(dataString("Alice"), map.data["name"]?.data)
        assertIs<ObjectUpdate.NoOp>(update) // update.noop == true
    }

    /**
     * @UTS objects/unit/RTLM9/lww-reject-equal-0
     */
    @Test
    fun `RTLM9 - LWW rejects equal serial`() {
        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["name"] = LiveMapEntry(timeserial = "05", data = dataString("Alice"))

        val msg = buildMapSet("root", "name", dataString("Bob"), "05", "site1")
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(dataString("Alice"), map.data["name"]?.data)
        assertIs<ObjectUpdate.NoOp>(update) // update.noop == true
    }

    /**
     * @UTS objects/unit/RTLM9b/both-empty-reject-0
     */
    @Test
    fun `RTLM9b - both serials empty rejects operation`() {
        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["name"] = LiveMapEntry(timeserial = "", data = dataString("Alice"))

        val msg = buildMapSet("root", "name", dataString("Bob"), "", "site1")
        val result = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(dataString("Alice"), map.data["name"]?.data)
        // The op's ObjectMessage.serial is empty, so the OBJECT-level gate (RTLO4a3, via
        // canApplyOperation) rejects it before the entry-level RTLM9b comparison, and
        // applyOperation returns a noop (RTLM15b).
        assertTrue(result.noOp)
    }

    /**
     * @UTS objects/unit/RTLM9d/missing-entry-serial-allows-0
     */
    @Test
    fun `RTLM9d - missing entry serial allows operation`() {
        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["name"] = LiveMapEntry(timeserial = null, data = dataString("Alice"))

        val msg = buildMapSet("root", "name", dataString("Bob"), "01", "site1")
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(dataString("Bob"), map.data["name"]?.data)
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        assertEquals(mapOf("name" to MapChange.Updated), mapUpdate.update) // update.update == { "name": "updated" }
        assertEquals(msg, mapUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLM7h/map-set-clear-timeserial-floor-0
     */
    @Test
    fun `RTLM7h - MAP_SET rejected when serial not greater than clearTimeserial`() {
        val map = InternalLiveMap.zeroValue("root", ro)
        map.clearTimeserial = "05"

        val msg = buildMapSet("root", "name", dataString("Alice"), "03", "site1")
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertFalse(map.data.containsKey("name"))
        assertIs<ObjectUpdate.NoOp>(update) // update.noop == true
    }

    /**
     * @UTS objects/unit/RTLM7g/map-set-objectid-creates-zero-value-0
     */
    @Test
    fun `RTLM7g - MAP_SET with objectId creates zero-value object`() {
        // pool wiring is implicit via ro (§17.1) - the map creates the zero-value child in ro.objectsPool
        val map = InternalLiveMap.zeroValue("root", ro)

        val msg = buildMapSet("root", "score", dataObjectId("counter:new@2000"), "01", "site1")
        map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        val created = ro.objectsPool.get("counter:new@2000")
        assertNotNull(created) // "counter:new@2000" IN pool
        val counter = assertIs<InternalLiveCounter>(created)
        assertEquals(0.0, counter.data.get())
    }

    /**
     * @UTS objects/unit/RTLM8/map-remove-existing-0
     */
    @Test
    fun `RTLM8 - MAP_REMOVE tombstones existing entry`() {
        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["name"] = LiveMapEntry(timeserial = "01", data = dataString("Alice"))

        val msg = buildMapRemove("root", "name", "02", "site1", 1_700_000_000_000L)
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertNull(map.data["name"]?.data)
        assertEquals(true, map.data["name"]?.isTombstoned)
        assertEquals("02", map.data["name"]?.timeserial)
        assertEquals(1_700_000_000_000L, map.data["name"]?.tombstonedAt)
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        assertEquals(mapOf("name" to MapChange.Removed), mapUpdate.update) // update.update == { "name": "removed" }
        assertEquals(msg, mapUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLM8/map-remove-nonexistent-0
     */
    @Test
    fun `RTLM8 - MAP_REMOVE creates tombstoned entry if not exists`() {
        val map = InternalLiveMap.zeroValue("root", ro)

        val msg = buildMapRemove("root", "ghost", "01", "site1", 1_700_000_000_000L)
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(true, map.data["ghost"]?.isTombstoned)
        assertEquals(1_700_000_000_000L, map.data["ghost"]?.tombstonedAt)
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        assertEquals(mapOf("ghost" to MapChange.Removed), mapUpdate.update) // update.update == { "ghost": "removed" }
        assertEquals(msg, mapUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLM8g/map-remove-clear-timeserial-floor-0
     */
    @Test
    fun `RTLM8g - MAP_REMOVE rejected when serial not greater than clearTimeserial`() {
        val map = InternalLiveMap.zeroValue("root", ro)
        map.clearTimeserial = "05"
        map.data["name"] = LiveMapEntry(timeserial = "04", data = dataString("Alice"))

        val msg = buildMapRemove("root", "name", "03", "site1", 1_700_000_000_000L)
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(dataString("Alice"), map.data["name"]?.data)
        assertEquals(false, map.data["name"]?.isTombstoned)
        assertIs<ObjectUpdate.NoOp>(update) // update.noop == true
    }

    /**
     * @UTS objects/unit/RTLM24/map-clear-basic-0
     */
    @Test
    fun `RTLM24 - MAP_CLEAR sets clearTimeserial and removes older entries`() {
        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["old"] = LiveMapEntry(timeserial = "02", data = dataString("old"))
        map.data["new"] = LiveMapEntry(timeserial = "06", data = dataString("new"))
        map.data["same"] = LiveMapEntry(timeserial = "04", data = dataString("same"))

        val msg = buildMapClear("root", "04", "site1")
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals("04", map.clearTimeserial)
        assertFalse(map.data.containsKey("old"))
        // RTLM24e1: an entry is removed only if the clear serial is lexicographically GREATER
        // than the entry's timeserial. "same" has timeserial "04" == clear serial "04" -> KEPT.
        assertTrue(map.data.containsKey("same"))
        assertTrue(map.data.containsKey("new"))
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        assertEquals(mapOf("old" to MapChange.Removed), mapUpdate.update) // update.update == { "old": "removed" }
        assertEquals(msg, mapUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLM24c/map-clear-stale-0
     */
    @Test
    fun `RTLM24c - MAP_CLEAR rejected when clearTimeserial is already greater`() {
        val map = InternalLiveMap.zeroValue("root", ro)
        map.clearTimeserial = "10"

        val msg = buildMapClear("root", "05", "site1")
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals("10", map.clearTimeserial)
        assertIs<ObjectUpdate.NoOp>(update) // update.noop == true
    }

    /**
     * @UTS objects/unit/RTLM16/map-create-merge-0
     */
    @Test
    fun `RTLM16 - MAP_CREATE merges entries`() {
        val map = InternalLiveMap.zeroValue("map:test@1000", ro)

        val msg = buildMapCreate(
            "map:test@1000",
            WireMapCreate(
                semantics = WireObjectsMapSemantics.LWW,
                entries = mapOf(
                    "name" to mapEntry(dataString("Alice"), timeserial = "01"),
                    "removed_key" to tombstonedMapEntry(timeserial = "01", serialTimestamp = 1_700_000_000_000L),
                ),
            ),
            "02", "site1",
        )
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(dataString("Alice"), map.data["name"]?.data)
        assertEquals(true, map.data["removed_key"]?.isTombstoned)
        assertTrue(map.createOperationIsMerged)
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        // update.update == { "name": "updated", "removed_key": "removed" }
        assertEquals(mapOf("name" to MapChange.Updated, "removed_key" to MapChange.Removed), mapUpdate.update)
        assertEquals(msg, mapUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLM16b/map-create-already-merged-0
     */
    @Test
    fun `RTLM16b - MAP_CREATE noop when already merged`() {
        val map = InternalLiveMap.zeroValue("map:test@1000", ro)
        map.createOperationIsMerged = true
        map.siteTimeserials["site1"] = "00"

        val msg = buildMapCreate(
            "map:test@1000",
            WireMapCreate(
                semantics = WireObjectsMapSemantics.LWW,
                entries = mapOf("name" to mapEntry(dataString("Bob"), timeserial = "01")),
            ),
            "01", "site1",
        )
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertFalse(map.data.containsKey("name"))
        assertIs<ObjectUpdate.NoOp>(update) // update.noop == true
    }

    /**
     * @UTS objects/unit/RTLM15c/channel-source-updates-serials-0
     */
    @Test
    fun `RTLM15c - CHANNEL source updates siteTimeserials`() {
        val map = InternalLiveMap.zeroValue("root", ro)

        val msg = buildMapSet("root", "x", dataNumber(1), "01", "site1")
        map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals("01", map.siteTimeserials["site1"])
    }

    /**
     * @UTS objects/unit/RTLM15e/tombstoned-reject-ops-0
     */
    @Test
    fun `RTLM15e - operations on tombstoned map are rejected`() {
        val map = InternalLiveMap.zeroValue("root", ro)
        map.isTombstoned = true

        val msg = buildMapSet("root", "x", dataNumber(1), "01", "site1")
        val result = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertTrue(result.noOp) // tombstoned -> rejected, nothing applied
        assertTrue(map.data.isEmpty())
    }

    /**
     * @UTS objects/unit/RTLO5/object-delete-tombstones-map-0
     */
    @Test
    fun `RTLO5 - OBJECT_DELETE tombstones map`() {
        // Uses a non-root map: an OBJECT_DELETE targeting root is rejected per RTLO4e10
        val map = InternalLiveMap.zeroValue("map:test@1000", ro)
        map.data["name"] = LiveMapEntry(timeserial = "01", data = dataString("Alice"))
        map.data["age"] = LiveMapEntry(timeserial = "01", data = dataNumber(30))
        map.siteTimeserials["site1"] = "00"

        val msg = buildObjectDelete("map:test@1000", "01", "site1", 1_700_000_000_000L)
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertTrue(map.isTombstoned)
        assertTrue(map.data.isEmpty())
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        // update.update == { "name": "removed", "age": "removed" }
        assertEquals(mapOf("name" to MapChange.Removed, "age" to MapChange.Removed), mapUpdate.update)
        assertTrue(mapUpdate.tombstone) // update.tombstone == true
        assertEquals(msg, mapUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLO5/tombstone-empty-map-emits-update-0
     */
    @Test
    fun `RTLO5 - OBJECT_DELETE on a map with no non-tombstoned entries still emits a non-noop tombstone update`() {
        // Uses a non-root map: an OBJECT_DELETE targeting root is rejected per RTLO4e10.
        // RTLM22c: every entry is already tombstoned, so the tombstone diff (RTLM22b considers only
        // non-tombstoned entries) has no changed keys - but the empty-diff noop exception must NOT be
        // applied for a tombstone diff; the empty update is still delivered to drive the RTLO4b4c3c
        // listener teardown. Complements object-delete-tombstones-map-0 (live entries).
        val map = InternalLiveMap.zeroValue("map:test@1000", ro)
        map.data["name"] = LiveMapEntry(isTombstoned = true, tombstonedAt = 1_600_000_000_000L, timeserial = "01", data = dataString("Alice"))
        map.data["age"] = LiveMapEntry(isTombstoned = true, tombstonedAt = 1_600_000_000_000L, timeserial = "01", data = dataNumber(30))
        map.siteTimeserials["site1"] = "00"

        val msg = buildObjectDelete("map:test@1000", "01", "site1", 1_700_000_000_000L)
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertTrue(map.isTombstoned) // map.isTombstone == true
        assertTrue(map.data.isEmpty()) // map.data == {}
        // update.noop == false: a typed MapUpdate (RTLO4e5/RTLM22c carve-out), not a NoOp
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        assertFalse(mapUpdate.noOp) // update.noop == false
        assertTrue(mapUpdate.tombstone) // update.tombstone == true (RTLO4e6)
        assertEquals(emptyMap<String, MapChange>(), mapUpdate.update) // update.update == {}
        assertEquals(msg, mapUpdate.objectMessage) // update.objectMessage == msg (RTLO4e7)
    }

    /**
     * @UTS objects/unit/RTLO4e10/object-delete-root-noop-0
     */
    @Test
    fun `RTLO4e10 - OBJECT_DELETE targeting root is rejected`() {
        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["name"] = LiveMapEntry(timeserial = "01", data = dataString("Alice"))
        map.siteTimeserials["site1"] = "00"

        val msg = buildObjectDelete("root", "01", "site1", 1_700_000_000_000L)
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertFalse(map.isTombstoned)
        assertEquals("Alice", map.data["name"]?.data?.string) // data untouched
        assertIs<ObjectUpdate.NoOp>(update) // update.noop == true
    }

    /**
     * @UTS objects/unit/RTLM14/tombstone-check-objectid-ref-0
     */
    @Test
    fun `RTLM14 - tombstoned entry check includes objectId reference`() {
        val tombstonedCounter = InternalLiveCounter.zeroValue("counter:dead@1000", ro)
        tombstonedCounter.isTombstoned = true
        ro.objectsPool.set("counter:dead@1000", tombstonedCounter)

        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["alive"] = LiveMapEntry(timeserial = "01", data = dataString("ok"))
        map.data["dead_entry"] = LiveMapEntry(isTombstoned = true, timeserial = "01", data = null)
        map.data["dead_ref"] = LiveMapEntry(timeserial = "01", data = dataObjectId("counter:dead@1000"))

        // isTombstoned(entry) -> entry.isEntryOrRefTombstoned(pool) (§17.4)
        assertFalse(map.data["alive"]!!.isEntryOrRefTombstoned(ro.objectsPool))
        assertTrue(map.data["dead_entry"]!!.isEntryOrRefTombstoned(ro.objectsPool))
        assertTrue(map.data["dead_ref"]!!.isEntryOrRefTombstoned(ro.objectsPool))
    }

    /**
     * @UTS objects/unit/RTLM6/replace-data-basic-0
     */
    @Test
    fun `RTLM6 - replaceData sets data from ObjectState`() {
        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["old"] = LiveMapEntry(timeserial = "01", data = dataString("old"))
        map.createOperationIsMerged = true

        val stateMsg = buildObjectState(
            "root", mapOf("site2" to "05"),
            map = mapState(
                entries = mapOf("new" to mapEntry(dataString("new"), timeserial = "04")),
                clearTimeserial = "03",
            ),
        )
        val update = map.applyObjectSync(stateMsg)

        assertEquals(mapOf("site2" to "05"), map.siteTimeserials)
        assertFalse(map.createOperationIsMerged)
        assertEquals("03", map.clearTimeserial)
        assertFalse(map.data.containsKey("old"))
        assertEquals(dataString("new"), map.data["new"]?.data)
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        assertEquals(mapOf("old" to MapChange.Removed, "new" to MapChange.Updated), mapUpdate.update)
        assertEquals(stateMsg, mapUpdate.objectMessage)
    }

    /**
     * @UTS objects/unit/RTLM6c1/replace-data-tombstoned-entries-0
     */
    @Test
    fun `RTLM6c1 - replaceData sets tombstonedAt on tombstoned entries`() {
        val map = InternalLiveMap.zeroValue("root", ro)

        val stateMsg = buildObjectState(
            "root", mapOf("site1" to "01"),
            map = mapState(
                entries = mapOf("dead" to tombstonedMapEntry(timeserial = "01", serialTimestamp = 1_700_000_050_000L)),
            ),
        )
        map.applyObjectSync(stateMsg)

        assertEquals(1_700_000_050_000L, map.data["dead"]?.tombstonedAt)
    }

    /**
     * @UTS objects/unit/RTLM6d/replace-data-with-create-op-0
     */
    @Test
    fun `RTLM6d - replaceData with createOp merges initial entries`() {
        val map = InternalLiveMap.zeroValue("map:test@1000", ro)

        val stateMsg = buildObjectState(
            "map:test@1000", mapOf("site1" to "01"),
            map = mapState(entries = mapOf("from_sync" to mapEntry(dataString("synced"), timeserial = "01"))),
            createOp = mapCreateOp(entries = mapOf("from_create" to mapEntry(dataString("created"), timeserial = "00"))),
        )
        map.applyObjectSync(stateMsg)

        assertEquals(dataString("synced"), map.data["from_sync"]?.data)
        assertEquals(dataString("created"), map.data["from_create"]?.data)
        assertTrue(map.createOperationIsMerged)
    }

    /**
     * @UTS objects/unit/RTLM6f/replace-data-tombstone-flag-0
     */
    @Test
    fun `RTLM6f - replaceData with tombstone flag tombstones map`() {
        // Uses a non-root map: tombstoning root is rejected per RTLO4e10
        val map = InternalLiveMap.zeroValue("map:test@1000", ro)
        map.data["name"] = LiveMapEntry(timeserial = "01", data = dataString("Alice"))

        val stateMsg = buildObjectState(
            "map:test@1000", mapOf("site1" to "01"),
            map = mapState(entries = emptyMap()),
            tombstone = true,
        )
        val update = map.applyObjectSync(stateMsg)

        assertTrue(map.isTombstoned)
        assertTrue(map.data.isEmpty())
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        assertEquals(mapOf("name" to MapChange.Removed), mapUpdate.update)
        assertTrue(mapUpdate.tombstone)
        assertEquals(stateMsg, mapUpdate.objectMessage)
    }

    /**
     * @UTS objects/unit/RTLO4e10/replace-data-tombstone-root-noop-0
     */
    @Test
    fun `RTLO4e10 - replaceData with tombstone flag targeting root is rejected`() {
        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["name"] = LiveMapEntry(timeserial = "01", data = dataString("Alice"))

        val stateMsg = buildObjectState(
            "root", mapOf("site1" to "01"),
            map = mapState(entries = emptyMap()),
            tombstone = true,
        )
        val update = map.applyObjectSync(stateMsg)

        assertFalse(map.isTombstoned)
        assertEquals("Alice", map.data["name"]?.data?.string) // data untouched
        assertIs<ObjectUpdate.NoOp>(update)
    }

    /**
     * @UTS objects/unit/RTLM19/gc-tombstoned-entries-0
     */
    @Test
    fun `RTLM19 - GC removes tombstoned entries past grace period`() {
        val map = InternalLiveMap.zeroValue("root", ro)
        val gracePeriod = 86_400_000L
        val now = 1_700_100_000_000L

        map.data["recent_dead"] = LiveMapEntry(isTombstoned = true, tombstonedAt = now - 1000, timeserial = "01", data = null)
        map.data["old_dead"] = LiveMapEntry(isTombstoned = true, tombstonedAt = now - gracePeriod - 1, timeserial = "01", data = null)
        map.data["alive"] = LiveMapEntry(timeserial = "01", data = dataString("ok"))

        // DEVIATION S-3 (see deviations.md): the SDK has no `now` parameter on the GC entry
        // point - gcTombstonedEntries(grace, now) maps to onGCInterval(grace) with the current
        // time read from the clock, so the clock is stubbed to the spec's fixed `now`.
        mockkStatic(SystemClock::class)
        val fixedClock = mockk<Clock> { every { currentTimeMillis() } returns now }
        every { SystemClock.clockFrom(any()) } returns fixedClock

        map.onGCInterval(gracePeriod)

        assertTrue(map.data.containsKey("recent_dead"))
        assertFalse(map.data.containsKey("old_dead"))
        assertTrue(map.data.containsKey("alive"))
    }

    /**
     * @UTS objects/unit/RTLM22/diff-calculation-0
     */
    @Test
    fun `RTLM22 - diff between two data states`() {
        val previousData = mapOf(
            "removed" to LiveMapEntry(timeserial = "01", data = dataString("gone")),
            "changed" to LiveMapEntry(timeserial = "01", data = dataString("old")),
            "unchanged" to LiveMapEntry(timeserial = "01", data = dataString("same")),
            "was_dead" to LiveMapEntry(isTombstoned = true, timeserial = "01", data = null),
        )
        val newData = mapOf(
            "added" to LiveMapEntry(timeserial = "02", data = dataString("new")),
            "changed" to LiveMapEntry(timeserial = "02", data = dataString("new_val")),
            "unchanged" to LiveMapEntry(timeserial = "01", data = dataString("same")),
            "now_dead" to LiveMapEntry(isTombstoned = true, timeserial = "02", data = null),
        )

        // InternalLiveMap.diff(prev, new) -> LiveMapManager(map).calculateUpdateFromDataDiff (§17.4)
        val manager = LiveMapManager(InternalLiveMap.zeroValue("root", ro))
        val update = manager.calculateUpdateFromDataDiff(previousData, newData)

        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        assertEquals(MapChange.Removed, mapUpdate.update["removed"])
        assertEquals(MapChange.Updated, mapUpdate.update["added"])
        assertEquals(MapChange.Updated, mapUpdate.update["changed"])
        assertFalse("unchanged" in mapUpdate.update)
        assertFalse("was_dead" in mapUpdate.update)
        assertFalse("now_dead" in mapUpdate.update)
    }

    /**
     * @UTS objects/unit/RTLM22c/empty-diff-is-noop-0
     */
    @Test
    fun `RTLM22c - empty diff is a no-op`() {
        // RTLM22c: as an exception to RTLM22b, the non-tombstoned entries before and after are
        // identical under the RTLM22b comparison (same key `name`, same `data`; only `timeserial`
        // differs, which is not compared), so no key changed and the diff returns a no-op update
        // (RTLO4b4b), never delivered to subscribers (RTLO4b4c1).
        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["name"] = LiveMapEntry(timeserial = "01", data = dataString("alice"))

        val stateMsg = buildObjectState(
            "root", mapOf("site1" to "02"),
            map = mapState(entries = mapOf("name" to mapEntry(dataString("alice"), timeserial = "02"))),
        )
        val update = map.applyObjectSync(stateMsg)

        assertIs<ObjectUpdate.NoOp>(update) // update.noop == true
        assertEquals(dataString("alice"), map.data["name"]?.data)
    }

    /**
     * @UTS objects/unit/RTLM15d4/unsupported-action-0
     */
    @Test
    fun `RTLM15d4 - unsupported action is discarded`() {
        val map = InternalLiveMap.zeroValue("root", ro)

        val msg = buildCounterInc("root", 5, "01", "site1")
        val result = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertTrue(result.noOp) // unsupported action -> discarded, nothing applied
    }

    /**
     * @UTS objects/unit/RTLM6i/replace-data-resets-clear-timeserial-0
     */
    @Test
    fun `RTLM6i - replaceData without clearTimeserial resets to null`() {
        val map = InternalLiveMap.zeroValue("root", ro)
        map.clearTimeserial = "05"
        map.data["x"] = LiveMapEntry(timeserial = "03", data = dataNumber(1))

        val stateMsg = buildObjectState(
            "root", mapOf("site1" to "01"),
            map = mapState(entries = mapOf("y" to mapEntry(dataNumber(2), timeserial = "01"))),
        )
        map.applyObjectSync(stateMsg)

        assertNull(map.clearTimeserial)
        assertTrue(map.data.containsKey("y"))
    }

    /**
     * @UTS objects/unit/RTLM14c/tombstoned-ref-yields-null-0
     */
    @Test
    fun `RTLM14c - MAP_SET referencing tombstoned objectId yields null value`() {
        val tombstonedCounter = InternalLiveCounter.zeroValue("counter:dead@1000", ro)
        tombstonedCounter.isTombstoned = true
        ro.objectsPool.set("counter:dead@1000", tombstonedCounter)

        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["ref"] = LiveMapEntry(timeserial = "01", data = dataObjectId("counter:dead@1000"))

        // The entry itself is not tombstoned, but the referenced object is
        assertEquals(false, map.data["ref"]?.isTombstoned)
        // size() must NOT count this entry because RTLM14c makes it tombstoned
        assertEquals(0L, map.size())
        // get() must return null for the value
        assertNull(map.get("ref"))
    }

    /**
     * @UTS objects/unit/RTLM7/map-set-revives-tombstoned-0
     */
    @Test
    fun `RTLM7 - MAP_SET revives tombstoned entry`() {
        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["name"] = LiveMapEntry(isTombstoned = true, tombstonedAt = 1_700_000_000_000L, timeserial = "01", data = null)

        val msg = buildMapSet("root", "name", dataString("Alice"), "02", "site1")
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(dataString("Alice"), map.data["name"]?.data)
        assertEquals(false, map.data["name"]?.isTombstoned)
        assertNull(map.data["name"]?.tombstonedAt)
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        assertEquals(mapOf("name" to MapChange.Updated), mapUpdate.update) // update.update == { "name": "updated" }
        assertEquals(msg, mapUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLM24/map-clear-preserves-newer-0
     */
    @Test
    fun `RTLM24 - MAP_CLEAR preserves entries with newer serial`() {
        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["before"] = LiveMapEntry(timeserial = "03", data = dataString("a"))
        map.data["after"] = LiveMapEntry(timeserial = "07", data = dataString("b"))
        map.data["no_ts"] = LiveMapEntry(timeserial = null, data = dataString("c"))

        val msg = buildMapClear("root", "05", "site1")
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertFalse(map.data.containsKey("before"))
        assertFalse(map.data.containsKey("no_ts"))
        assertEquals(dataString("b"), map.data["after"]?.data)
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        // "before"/"no_ts" IN update.update (removed); "after" NOT IN update.update
        assertEquals(MapChange.Removed, mapUpdate.update["before"])
        assertEquals(MapChange.Removed, mapUpdate.update["no_ts"])
        assertFalse("after" in mapUpdate.update)
        assertEquals(msg, mapUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLM7a3/map-set-overwrite-objectid-parent-refs-0
     */
    @Test
    fun `RTLM7a3 - MAP_SET overwrites entry referencing LiveObject updates parentReferences`() {
        val oldCounter = InternalLiveCounter.zeroValue("counter:old@1000", ro)
        val newCounter = InternalLiveCounter.zeroValue("counter:new@2000", ro)
        ro.objectsPool.set("counter:old@1000", oldCounter)
        ro.objectsPool.set("counter:new@2000", newCounter)

        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["ref"] = LiveMapEntry(timeserial = "01", data = dataObjectId("counter:old@1000"))
        // Simulate existing parentReference
        oldCounter.parentReferences["root"] = mutableSetOf("ref")

        val msg = buildMapSet("root", "ref", dataObjectId("counter:new@2000"), "02", "site1")
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(dataObjectId("counter:new@2000"), map.data["ref"]?.data)
        // removeParentReference was called on the old child
        assertTrue("root" !in oldCounter.parentReferences || "ref" !in oldCounter.parentReferences["root"]!!)
        // addParentReference was called on the new child
        assertTrue("root" in newCounter.parentReferences)
        assertTrue("ref" in newCounter.parentReferences["root"]!!)
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        assertEquals(mapOf("ref" to MapChange.Updated), mapUpdate.update) // update.update == { "ref": "updated" }
        assertEquals(msg, mapUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLM7g2/map-set-new-entry-add-parent-ref-0
     */
    @Test
    fun `RTLM7g2 - MAP_SET new entry referencing LiveObject adds parentReference`() {
        val childCounter = InternalLiveCounter.zeroValue("counter:child@1000", ro)
        ro.objectsPool.set("counter:child@1000", childCounter)

        val map = InternalLiveMap.zeroValue("root", ro)

        val msg = buildMapSet("root", "score", dataObjectId("counter:child@1000"), "01", "site1")
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(dataObjectId("counter:child@1000"), map.data["score"]?.data)
        assertTrue("root" in childCounter.parentReferences)
        assertTrue("score" in childCounter.parentReferences["root"]!!)
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        assertEquals(msg, mapUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLM7/map-set-primitive-no-parent-refs-0
     */
    @Test
    fun `RTLM7 - MAP_SET with non-LiveObject value does not affect parentReferences`() {
        val oldCounter = InternalLiveCounter.zeroValue("counter:old@1000", ro)
        ro.objectsPool.set("counter:old@1000", oldCounter)

        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["ref"] = LiveMapEntry(timeserial = "01", data = dataObjectId("counter:old@1000"))
        oldCounter.parentReferences["root"] = mutableSetOf("ref")

        val msg = buildMapSet("root", "ref", dataString("plain_value"), "02", "site1")
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(dataString("plain_value"), map.data["ref"]?.data)
        // removeParentReference was called on old child (entry previously had objectId);
        // no addParentReference call because the new value is a primitive
        assertTrue("root" !in oldCounter.parentReferences || "ref" !in oldCounter.parentReferences["root"]!!)
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        assertEquals(mapOf("ref" to MapChange.Updated), mapUpdate.update) // update.update == { "ref": "updated" }
        assertEquals(msg, mapUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLM8a3/map-remove-objectid-parent-refs-0
     */
    @Test
    fun `RTLM8a3 - MAP_REMOVE entry referencing LiveObject removes parentReference`() {
        val childCounter = InternalLiveCounter.zeroValue("counter:child@1000", ro)
        ro.objectsPool.set("counter:child@1000", childCounter)

        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["score"] = LiveMapEntry(timeserial = "01", data = dataObjectId("counter:child@1000"))
        childCounter.parentReferences["root"] = mutableSetOf("score")

        val msg = buildMapRemove("root", "score", "02", "site1", 1_700_000_000_000L)
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(true, map.data["score"]?.isTombstoned)
        // removeParentReference was called on the child
        assertTrue("root" !in childCounter.parentReferences || "score" !in childCounter.parentReferences["root"]!!)
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        assertEquals(mapOf("score" to MapChange.Removed), mapUpdate.update) // update.update == { "score": "removed" }
        assertEquals(msg, mapUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLM8/map-remove-primitive-no-parent-refs-0
     */
    @Test
    fun `RTLM8 - MAP_REMOVE entry with non-LiveObject value needs no parentReference calls`() {
        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["name"] = LiveMapEntry(timeserial = "01", data = dataString("Alice"))

        val msg = buildMapRemove("root", "name", "02", "site1", 1_700_000_000_000L)
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(true, map.data["name"]?.isTombstoned)
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        assertEquals(mapOf("name" to MapChange.Removed), mapUpdate.update) // update.update == { "name": "removed" }
        assertEquals(msg, mapUpdate.objectMessage) // update.objectMessage == msg
        // No parentReference calls needed - test passes without errors
    }

    /**
     * @UTS objects/unit/RTLM24e1c/map-clear-parent-refs-0
     */
    @Test
    fun `RTLM24e1c - MAP_CLEAR removes parent references for cleared entries`() {
        val counterA = InternalLiveCounter.zeroValue("counter:a@1000", ro)
        val counterB = InternalLiveCounter.zeroValue("counter:b@1000", ro)
        ro.objectsPool.set("counter:a@1000", counterA)
        ro.objectsPool.set("counter:b@1000", counterB)

        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["ref_a"] = LiveMapEntry(timeserial = "02", data = dataObjectId("counter:a@1000"))
        map.data["ref_b"] = LiveMapEntry(timeserial = "02", data = dataObjectId("counter:b@1000"))
        map.data["primitive"] = LiveMapEntry(timeserial = "02", data = dataString("hello"))
        map.data["newer"] = LiveMapEntry(timeserial = "09", data = dataString("kept"))
        counterA.parentReferences["root"] = mutableSetOf("ref_a")
        counterB.parentReferences["root"] = mutableSetOf("ref_b")

        val msg = buildMapClear("root", "05", "site1")
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        // ref_a and ref_b removed (timeserial "02" < "05"), newer kept (timeserial "09" > "05")
        assertFalse(map.data.containsKey("ref_a"))
        assertFalse(map.data.containsKey("ref_b"))
        assertFalse(map.data.containsKey("primitive"))
        assertTrue(map.data.containsKey("newer"))
        // removeParentReference was called on both child counters
        assertTrue("root" !in counterA.parentReferences || "ref_a" !in counterA.parentReferences["root"]!!)
        assertTrue("root" !in counterB.parentReferences || "ref_b" !in counterB.parentReferences["root"]!!)
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        // update.update == { "ref_a": "removed", "ref_b": "removed", "primitive": "removed" }
        assertEquals(
            mapOf("ref_a" to MapChange.Removed, "ref_b" to MapChange.Removed, "primitive" to MapChange.Removed),
            mapUpdate.update,
        )
        assertEquals(msg, mapUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLO4e9/tombstone-map-parent-refs-0
     */
    @Test
    fun `RTLO4e9 - tombstoning InternalLiveMap removes parent references for all entries`() {
        val childCounter = InternalLiveCounter.zeroValue("counter:child@1000", ro)
        val childMap = InternalLiveMap.zeroValue("map:child@1000", ro)
        ro.objectsPool.set("counter:child@1000", childCounter)
        ro.objectsPool.set("map:child@1000", childMap)

        // Uses a non-root map: tombstoning root is rejected per RTLO4e10
        val map = InternalLiveMap.zeroValue("map:test@1000", ro)
        map.data["counter_ref"] = LiveMapEntry(timeserial = "01", data = dataObjectId("counter:child@1000"))
        map.data["map_ref"] = LiveMapEntry(timeserial = "01", data = dataObjectId("map:child@1000"))
        map.data["name"] = LiveMapEntry(timeserial = "01", data = dataString("Alice"))
        map.siteTimeserials["site1"] = "00"
        childCounter.parentReferences["map:test@1000"] = mutableSetOf("counter_ref")
        childMap.parentReferences["map:test@1000"] = mutableSetOf("map_ref")

        val msg = buildObjectDelete("map:test@1000", "01", "site1", 1_700_000_000_000L)
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertTrue(map.isTombstoned)
        assertTrue(map.data.isEmpty())
        // removeParentReference was called on both children
        assertTrue(
            "map:test@1000" !in childCounter.parentReferences ||
                "counter_ref" !in childCounter.parentReferences["map:test@1000"]!!,
        )
        assertTrue(
            "map:test@1000" !in childMap.parentReferences ||
                "map_ref" !in childMap.parentReferences["map:test@1000"]!!,
        )
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        // update.update == { "counter_ref": "removed", "map_ref": "removed", "name": "removed" }
        assertEquals(
            mapOf("counter_ref" to MapChange.Removed, "map_ref" to MapChange.Removed, "name" to MapChange.Removed),
            mapUpdate.update,
        )
        assertTrue(mapUpdate.tombstone) // update.tombstone == true
        assertEquals(msg, mapUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLM7a3/map-set-replace-objectid-both-refs-0
     */
    @Test
    fun `RTLM7a3 - MAP_SET overwriting LiveObject with LiveObject calls both remove and add`() {
        val oldMap = InternalLiveMap.zeroValue("map:old@1000", ro)
        val newMap = InternalLiveMap.zeroValue("map:new@2000", ro)
        ro.objectsPool.set("map:old@1000", oldMap)
        ro.objectsPool.set("map:new@2000", newMap)

        val map = InternalLiveMap.zeroValue("root", ro)
        map.data["child"] = LiveMapEntry(timeserial = "01", data = dataObjectId("map:old@1000"))
        oldMap.parentReferences["root"] = mutableSetOf("child")

        val msg = buildMapSet("root", "child", dataObjectId("map:new@2000"), "02", "site1")
        val update = map.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(dataObjectId("map:new@2000"), map.data["child"]?.data)
        // Old child no longer references root
        assertTrue("root" !in oldMap.parentReferences || "child" !in oldMap.parentReferences["root"]!!)
        // New child references root
        assertTrue("root" in newMap.parentReferences)
        assertTrue("child" in newMap.parentReferences["root"]!!)
        val mapUpdate = assertIs<ObjectUpdate.MapUpdate>(update)
        assertEquals(mapOf("child" to MapChange.Updated), mapUpdate.update) // update.update == { "child": "updated" }
        assertEquals(msg, mapUpdate.objectMessage) // update.objectMessage == msg
    }
}
