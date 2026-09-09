package io.ably.lib.liveobjects.uts.unit

import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.ObjectsOperationSource
import io.ably.lib.liveobjects.message.WireCounterCreate
import io.ably.lib.liveobjects.message.WireCounterInc
import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.message.WireObjectOperation
import io.ably.lib.liveobjects.message.WireObjectOperationAction
import io.ably.lib.liveobjects.message.WireObjectsCounter
import io.ably.lib.liveobjects.unit.getMockAblyClientAdapter
import io.ably.lib.liveobjects.value.ObjectUpdate
import io.ably.lib.liveobjects.value.livecounter.InternalLiveCounter
import io.ably.lib.liveobjects.value.noOp
import io.mockk.unmockkAll
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Derived from UTS spec `objects/unit/internal_live_counter.md` — the `InternalLiveCounter`
 * CRDT data structure: increment/create operations (`RTLC7`–`RTLC9`, `RTLC16`), data
 * replacement during sync (`RTLC6`, `RTLC14`), tombstoning (`RTLO4e`, `RTLO5`, `RTLO6`,
 * `RTLC7e`), and serial-based newness checks (`RTLO4a`, `RTLC7c`).
 *
 * Internal-graph spec: asserts on the internal CRDT graph, so it lives in `:liveobjects`'s own
 * test source set — symbol map in `.claude/skills/uts-to-kotlin/references/objects-mapping.md`
 * §17 (instantiation §17.1, counter §17.3, update object §17.5).
 *
 * The op path (`applyObject`) returns the `ObjectUpdate` per the spec contract (RTLC9g/RTLM7f), so
 * op-path tests assert the returned update directly, exactly like the sync path (`applyObjectSync`).
 */
class InternalLiveCounterTest {

    private lateinit var ro: DefaultRealtimeObject

    @BeforeTest
    fun setUp() {
        // §17.1 - internal classes have no public constructors; build them against a
        // DefaultRealtimeObject backed by the mocked adapter.
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
     * @UTS objects/unit/RTLC4/zero-value-0
     */
    @Test
    fun `RTLC4 - zero-value InternalLiveCounter`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)

        assertEquals(0.0, counter.data.get())
        assertEquals("counter:abc@1000", counter.objectId)
        assertFalse(counter.isTombstoned)
        assertNull(counter.tombstonedAt)
        assertFalse(counter.createOperationIsMerged)
        assertEquals(emptyMap(), counter.siteTimeserials)
    }

    /**
     * @UTS objects/unit/RTLC9/counter-inc-basic-0
     */
    @Test
    fun `RTLC9 - COUNTER_INC adds number to data`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)

        val msg = buildCounterInc("counter:abc@1000", 5, "01", "site1")
        val update = counter.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(5.0, counter.data.get())
        val counterUpdate = assertIs<ObjectUpdate.CounterUpdate>(update) // update.noop == false
        assertEquals(5.0, counterUpdate.amount) // update.update.amount == 5
        assertEquals(msg, counterUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLC9/counter-inc-negative-0
     */
    @Test
    fun `RTLC9 - COUNTER_INC with negative number`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)
        counter.data.set(10.0)
        counter.siteTimeserials["site1"] = "00"

        val msg = buildCounterInc("counter:abc@1000", -3, "01", "site1")
        val update = counter.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(7.0, counter.data.get())
        val counterUpdate = assertIs<ObjectUpdate.CounterUpdate>(update)
        assertEquals(-3.0, counterUpdate.amount) // update.update.amount == -3
        assertEquals(msg, counterUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLC9/counter-inc-missing-number-0
     */
    @Test
    fun `RTLC9 - COUNTER_INC with missing number is noop`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)
        counter.data.set(10.0)

        val msg = WireObjectMessage(
            serial = "01",
            siteCode = "site1",
            operation = WireObjectOperation(
                action = WireObjectOperationAction.CounterInc,
                objectId = "counter:abc@1000",
                counterInc = WireCounterInc(number = null),
            ),
        )
        val update = counter.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(10.0, counter.data.get())
        assertIs<ObjectUpdate.NoOp>(update) // update.noop == true
    }

    /**
     * @UTS objects/unit/RTLC9/counter-inc-accumulate-0
     */
    @Test
    fun `RTLC9 - multiple COUNTER_INC operations accumulate`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)

        counter.applyObject(buildCounterInc("counter:abc@1000", 10, "01", "site1"), ObjectsOperationSource.CHANNEL)
        counter.applyObject(buildCounterInc("counter:abc@1000", 20, "02", "site1"), ObjectsOperationSource.CHANNEL)
        counter.applyObject(buildCounterInc("counter:abc@1000", -5, "01", "site2"), ObjectsOperationSource.CHANNEL)

        assertEquals(25.0, counter.data.get())
    }

    /**
     * @UTS objects/unit/RTLC8/counter-create-merge-0
     */
    @Test
    fun `RTLC8 - COUNTER_CREATE merges initial count`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)

        val msg = buildCounterCreate("counter:abc@1000", WireCounterCreate(count = 42.0), "01", "site1")
        val update = counter.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(42.0, counter.data.get())
        assertTrue(counter.createOperationIsMerged)
        val counterUpdate = assertIs<ObjectUpdate.CounterUpdate>(update)
        assertEquals(42.0, counterUpdate.amount) // update.update.amount == 42
        assertEquals(msg, counterUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLC8/counter-create-already-merged-0
     */
    @Test
    fun `RTLC8 - COUNTER_CREATE noop when already merged`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)
        counter.data.set(42.0)
        counter.createOperationIsMerged = true
        counter.siteTimeserials["site1"] = "00"

        val msg = buildCounterCreate("counter:abc@1000", WireCounterCreate(count = 99.0), "01", "site1")
        val update = counter.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(42.0, counter.data.get())
        assertIs<ObjectUpdate.NoOp>(update) // update.noop == true
    }

    /**
     * @UTS objects/unit/RTLC16/counter-create-no-count-0
     */
    @Test
    fun `RTLC16 - COUNTER_CREATE with missing count is noop`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)

        val msg = buildCounterCreate("counter:abc@1000", WireCounterCreate(count = null), "01", "site1")
        val update = counter.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(0.0, counter.data.get())
        assertTrue(counter.createOperationIsMerged)
        assertIs<ObjectUpdate.NoOp>(update) // update.noop == true
    }

    /**
     * @UTS objects/unit/RTLO4a/apply-empty-site-serial-0
     */
    @Test
    fun `RTLO4a - canApplyOperation allows when siteSerial is empty`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)

        val msg = buildCounterInc("counter:abc@1000", 5, "01", "site1")
        val result = counter.applyObject(msg, ObjectsOperationSource.CHANNEL)

        // ASSERT result IS NOT false -> a real (non-noop) update was returned
        assertFalse(result.noOp)
        assertEquals(5.0, counter.data.get())
    }

    /**
     * @UTS objects/unit/RTLO4a/reject-stale-serial-0
     */
    @Test
    fun `RTLO4a - canApplyOperation rejects stale serial`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)
        counter.siteTimeserials["site1"] = "05"
        counter.data.set(10.0)

        val msg = buildCounterInc("counter:abc@1000", 99, "03", "site1")
        val result = counter.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertTrue(result.noOp) // rejected -> nothing applied
        assertEquals(10.0, counter.data.get())
    }

    /**
     * @UTS objects/unit/RTLO4a/reject-equal-serial-0
     */
    @Test
    fun `RTLO4a - canApplyOperation rejects equal serial`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)
        counter.siteTimeserials["site1"] = "05"
        counter.data.set(10.0)

        val msg = buildCounterInc("counter:abc@1000", 99, "05", "site1")
        val result = counter.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertTrue(result.noOp) // rejected -> nothing applied
        assertEquals(10.0, counter.data.get())
    }

    /**
     * @UTS objects/unit/RTLO4a/warn-invalid-serial-0
     */
    @Test
    fun `RTLO4a - canApplyOperation warns on empty serial or siteCode`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)

        val msgNoSerial = buildCounterInc("counter:abc@1000", 5, "", "site1")
        val result1 = counter.applyObject(msgNoSerial, ObjectsOperationSource.CHANNEL)

        val msgNoSite = buildCounterInc("counter:abc@1000", 5, "01", "")
        val result2 = counter.applyObject(msgNoSite, ObjectsOperationSource.CHANNEL)

        assertEquals(0.0, counter.data.get())
        assertTrue(result1.noOp)
        assertTrue(result2.noOp)
    }

    /**
     * @UTS objects/unit/RTLC7c/channel-source-updates-serials-0
     */
    @Test
    fun `RTLC7c - CHANNEL source updates siteTimeserials`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)

        val msg = buildCounterInc("counter:abc@1000", 5, "01", "site1")
        counter.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals("01", counter.siteTimeserials["site1"])
    }

    /**
     * @UTS objects/unit/RTLC7c/local-source-no-serial-update-0
     */
    @Test
    fun `RTLC7c - LOCAL source does not update siteTimeserials`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)

        val msg = buildCounterInc("counter:abc@1000", 5, "01", "site1")
        counter.applyObject(msg, ObjectsOperationSource.LOCAL)

        assertEquals(emptyMap(), counter.siteTimeserials)
        assertEquals(5.0, counter.data.get())
    }

    /**
     * @UTS objects/unit/RTLC7g/apply-returns-true-0
     */
    @Test
    fun `RTLC7g - applyOperation returns true on success`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)

        val msg = buildCounterInc("counter:abc@1000", 5, "01", "site1")
        val result = counter.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertFalse(result.noOp) // result == true -> a real (non-noop) update was returned
    }

    /**
     * @UTS objects/unit/RTLO5/object-delete-tombstones-0
     */
    @Test
    fun `RTLO5 - OBJECT_DELETE tombstones counter`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)
        counter.data.set(42.0)
        counter.siteTimeserials["site1"] = "00"

        val msg = buildObjectDelete("counter:abc@1000", "01", "site1", 1_700_000_000_000L)
        val update = counter.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertTrue(counter.isTombstoned)
        assertEquals(0.0, counter.data.get())
        assertEquals(1_700_000_000_000L, counter.tombstonedAt)
        val counterUpdate = assertIs<ObjectUpdate.CounterUpdate>(update)
        assertEquals(-42.0, counterUpdate.amount) // update.update.amount == -42
        assertTrue(counterUpdate.tombstone) // update.tombstone == true
        assertEquals(msg, counterUpdate.objectMessage) // update.objectMessage == msg
    }

    /**
     * @UTS objects/unit/RTLO5/tombstone-zero-value-counter-emits-update-0
     */
    @Test
    fun `RTLO5 - OBJECT_DELETE on an already-zero counter still emits a non-noop tombstone update`() {
        // RTLC14c: the tombstone diff (previousData 0, newData 0) is a zero delta, but the zero-delta
        // noop exception must NOT be applied for a tombstone diff - the update is still delivered to
        // drive the RTLO4b4c3c listener teardown. Complements object-delete-tombstones-0 (populated).
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)
        counter.data.set(0.0)
        counter.siteTimeserials["site1"] = "00"

        val msg = buildObjectDelete("counter:abc@1000", "01", "site1", 1_700_000_000_000L)
        val update = counter.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertTrue(counter.isTombstoned) // counter.isTombstone == true
        assertEquals(0.0, counter.data.get()) // counter.data == 0
        // update.noop == false: a typed CounterUpdate (RTLO4e5/RTLC14c carve-out), not a NoOp
        val counterUpdate = assertIs<ObjectUpdate.CounterUpdate>(update)
        assertFalse(counterUpdate.noOp) // update.noop == false
        assertTrue(counterUpdate.tombstone) // update.tombstone == true (RTLO4e6)
        assertEquals(0.0, counterUpdate.amount) // update.update.amount == 0
        assertEquals(msg, counterUpdate.objectMessage) // update.objectMessage == msg (RTLO4e7)
    }

    /**
     * @UTS objects/unit/RTLC7e/tombstoned-reject-ops-0
     */
    @Test
    fun `RTLC7e - operations on tombstoned counter are rejected`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)
        counter.isTombstoned = true
        counter.tombstonedAt = 1_700_000_000_000L

        val msg = buildCounterInc("counter:abc@1000", 5, "01", "site1")
        val result = counter.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertTrue(result.noOp) // tombstoned -> rejected, nothing applied
        assertEquals(0.0, counter.data.get())
    }

    /**
     * @UTS objects/unit/RTLO6/tombstoned-at-from-serial-timestamp-0
     */
    @Test
    fun `RTLO6 - tombstonedAt from serialTimestamp`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)

        val msg = buildObjectDelete("counter:abc@1000", "01", "site1", 1_700_000_050_000L)
        counter.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertEquals(1_700_000_050_000L, counter.tombstonedAt)
    }

    /**
     * @UTS objects/unit/RTLO6/tombstoned-at-local-clock-0
     */
    @Test
    fun `RTLO6 - tombstonedAt from local clock when no serialTimestamp`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)
        val beforeTime = System.currentTimeMillis()

        val msg = buildObjectDelete("counter:abc@1000", "01", "site1")
        counter.applyObject(msg, ObjectsOperationSource.CHANNEL)

        val afterTime = System.currentTimeMillis()
        assertTrue(counter.tombstonedAt!! >= beforeTime)
        assertTrue(counter.tombstonedAt!! <= afterTime)
    }

    /**
     * @UTS objects/unit/RTLC7d3/unsupported-action-0
     */
    @Test
    fun `RTLC7d3 - unsupported action is discarded`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)

        val msg = buildMapSet("counter:abc@1000", "x", dataString("y"), "01", "site1")
        val result = counter.applyObject(msg, ObjectsOperationSource.CHANNEL)

        assertTrue(result.noOp) // unsupported action -> discarded, nothing applied
        assertEquals(0.0, counter.data.get())
    }

    /**
     * @UTS objects/unit/RTLC6/replace-data-basic-0
     */
    @Test
    fun `RTLC6 - replaceData sets data from ObjectState`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)
        counter.data.set(10.0)
        counter.createOperationIsMerged = true
        counter.siteTimeserials["site1"] = "00"

        val stateMsg = buildObjectState("counter:abc@1000", mapOf("site2" to "05"), counter = counterState(50))
        val update = counter.applyObjectSync(stateMsg)

        assertEquals(50.0, counter.data.get())
        assertEquals(mapOf("site2" to "05"), counter.siteTimeserials)
        assertFalse(counter.createOperationIsMerged)
        val counterUpdate = assertIs<ObjectUpdate.CounterUpdate>(update)
        assertEquals(40.0, counterUpdate.amount)
        assertEquals(stateMsg, counterUpdate.objectMessage)
    }

    /**
     * @UTS objects/unit/RTLC6/replace-data-with-create-op-0
     */
    @Test
    fun `RTLC6 - replaceData with createOp merges initial value`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)

        val stateMsg = buildObjectState(
            "counter:abc@1000", mapOf("site1" to "01"),
            counter = counterState(100),
            createOp = counterCreateOp(50),
        )
        val update = counter.applyObjectSync(stateMsg)

        assertEquals(150.0, counter.data.get())
        assertTrue(counter.createOperationIsMerged)
        val counterUpdate = assertIs<ObjectUpdate.CounterUpdate>(update)
        assertEquals(150.0, counterUpdate.amount)
        assertEquals(stateMsg, counterUpdate.objectMessage)
    }

    /**
     * @UTS objects/unit/RTLC6e/replace-data-tombstoned-noop-0
     */
    @Test
    fun `RTLC6e - replaceData on tombstoned counter is noop`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)
        counter.isTombstoned = true
        counter.tombstonedAt = 1_700_000_000_000L
        counter.data.set(0.0)

        val stateMsg = buildObjectState("counter:abc@1000", mapOf("site1" to "01"), counter = counterState(999))
        val update = counter.applyObjectSync(stateMsg)

        assertEquals(0.0, counter.data.get())
        assertIs<ObjectUpdate.NoOp>(update)
    }

    /**
     * @UTS objects/unit/RTLC6f/replace-data-tombstone-flag-0
     */
    @Test
    fun `RTLC6f - replaceData with tombstone flag tombstones counter`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)
        counter.data.set(30.0)

        val stateMsg = buildObjectState(
            "counter:abc@1000", mapOf("site1" to "01"),
            counter = counterState(0),
            tombstone = true,
        )
        val update = counter.applyObjectSync(stateMsg)

        assertTrue(counter.isTombstoned)
        assertEquals(0.0, counter.data.get())
        val counterUpdate = assertIs<ObjectUpdate.CounterUpdate>(update)
        assertEquals(-30.0, counterUpdate.amount)
        assertTrue(counterUpdate.tombstone)
        assertEquals(stateMsg, counterUpdate.objectMessage)
    }

    /**
     * @UTS objects/unit/RTLC6/replace-data-missing-count-0
     */
    @Test
    fun `RTLC6 - replaceData with missing counter count defaults to 0`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)
        counter.data.set(42.0)

        val stateMsg = buildObjectState(
            "counter:abc@1000", mapOf("site1" to "01"),
            counter = WireObjectsCounter(count = null),
        )
        val update = counter.applyObjectSync(stateMsg)

        assertEquals(0.0, counter.data.get())
        val counterUpdate = assertIs<ObjectUpdate.CounterUpdate>(update)
        assertEquals(-42.0, counterUpdate.amount)
        assertEquals(stateMsg, counterUpdate.objectMessage)
    }

    /**
     * @UTS objects/unit/RTLC14/diff-calculation-0
     */
    @Test
    fun `RTLC14 - diff calculation`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)
        counter.data.set(20.0)

        val stateMsg = buildObjectState("counter:abc@1000", mapOf("site1" to "01"), counter = counterState(75))
        val update = counter.applyObjectSync(stateMsg)

        val counterUpdate = assertIs<ObjectUpdate.CounterUpdate>(update)
        assertEquals(55.0, counterUpdate.amount)
        assertEquals(stateMsg, counterUpdate.objectMessage)
    }

    /**
     * @UTS objects/unit/RTLC14c/zero-delta-diff-is-noop-0
     */
    @Test
    fun `RTLC14c - zero-delta diff is a no-op`() {
        // RTLC14c: as an exception to RTLC14b, when newData equals previousData the computed delta
        // is 0, so the diff returns a no-op update (RTLO4b4b) - never delivered to subscribers
        // (RTLO4b4c1); at the internal tier the flake-free proxy for "no event fires" is the no-op.
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)
        counter.data.set(100.0)

        val stateMsg = buildObjectState("counter:abc@1000", mapOf("site1" to "01"), counter = counterState(100))
        val update = counter.applyObjectSync(stateMsg)

        assertIs<ObjectUpdate.NoOp>(update) // update.noop == true
        assertEquals(100.0, counter.data.get())
    }

    /**
     * @UTS objects/unit/RTLC8/create-then-inc-0
     */
    @Test
    fun `RTLC8 - COUNTER_CREATE then COUNTER_INC accumulates`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)

        counter.applyObject(
            buildCounterCreate("counter:abc@1000", WireCounterCreate(count = 100.0), "01", "site1"),
            ObjectsOperationSource.CHANNEL,
        )
        counter.applyObject(
            buildCounterInc("counter:abc@1000", 25, "02", "site1"),
            ObjectsOperationSource.CHANNEL,
        )

        assertEquals(125.0, counter.data.get())
        assertTrue(counter.createOperationIsMerged)
    }

    /**
     * @UTS objects/unit/RTLO3/live-object-init-properties-0
     */
    @Test
    fun `RTLO3 - LiveObject properties initialized correctly`() {
        val counter = InternalLiveCounter.zeroValue("counter:test@2000", ro)

        assertEquals("counter:test@2000", counter.objectId)
        assertEquals(emptyMap(), counter.siteTimeserials)
        assertFalse(counter.createOperationIsMerged)
        assertFalse(counter.isTombstoned)
        assertNull(counter.tombstonedAt)
    }
}
