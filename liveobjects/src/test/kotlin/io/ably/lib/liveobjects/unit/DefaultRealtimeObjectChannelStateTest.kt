package io.ably.lib.liveobjects.unit

import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.ROOT_OBJECT_ID
import io.ably.lib.liveobjects.message.WireObjectData
import io.ably.lib.liveobjects.value.livecounter.InternalLiveCounter
import io.ably.lib.liveobjects.value.livemap.InternalLiveMap
import io.ably.lib.liveobjects.value.livemap.LiveMapEntry
import io.ably.lib.realtime.ChannelState
import io.mockk.unmockkAll
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SDK-local regression guard — **NOT a UTS spec test**. No normative `RTO` point governs this
 * behaviour and it is not observable through the public API (access on a DETACHED channel throws per
 * RTO25b, and a re-attach re-syncs, replacing the pool regardless of whether detach cleared it), so it
 * is intentionally excluded from the shared `objects/unit` UTS suite — see the tracking note in the
 * spec's `objects_pool.md` / `realtime_object.md`. This test pins the ably-java implementation so a
 * future refactor can't silently drop the clear.
 *
 * Behaviour ([DefaultRealtimeObject.handleStateChange]): a channel transition to DETACHED or FAILED
 * clears all objects data **without emitting update events**; SUSPENDED **retains** the data (the
 * current data is unknown in DETACHED/FAILED, whereas a SUSPENDED channel may still recover).
 */
class DefaultRealtimeObjectChannelStateTest {

    private lateinit var ro: DefaultRealtimeObject

    @AfterTest
    fun tearDown() {
        ro.objectsPool.dispose()
        unmockkAll() // getMockAblyClientAdapter uses mockkStatic - clean up global state
    }

    /** A populated pool: the root map with one entry, plus a child counter carrying data. */
    private fun seedPool() {
        ro = DefaultRealtimeObject("test", getMockAblyClientAdapter())
        rootMap().data["name"] = LiveMapEntry(timeserial = "01", data = WireObjectData(string = "Alice"))
        ro.objectsPool.set("counter:x@1", InternalLiveCounter.zeroValue("counter:x@1", ro).apply { data.set(42.0) })
    }

    private fun rootMap(): InternalLiveMap = ro.objectsPool.get(ROOT_OBJECT_ID) as InternalLiveMap
    private fun childCounter(): InternalLiveCounter = ro.objectsPool.get("counter:x@1") as InternalLiveCounter

    /**
     * [DefaultRealtimeObject.handleStateChange] launches on the internal sequential scope; enqueue an
     * empty [DefaultRealtimeObject.asyncFuture] and await it to deterministically flush that scope
     * (FIFO, `limitedParallelism(1)`) so the state-change handler has run before we assert — no polling,
     * no flaky timing.
     */
    private suspend fun flush() = ro.asyncFuture { }.await()

    @Test
    fun `channel DETACHED clears objects data without emitting`() = runTest {
        seedPool()
        ro.handleStateChange(ChannelState.detached, false)
        flush()
        assertTrue(rootMap().data.isEmpty(), "root data must be cleared on DETACHED")
        assertEquals(0.0, childCounter().value(), "child counter data must be cleared on DETACHED")
    }

    @Test
    fun `channel FAILED clears objects data without emitting`() = runTest {
        seedPool()
        ro.handleStateChange(ChannelState.failed, false)
        flush()
        assertTrue(rootMap().data.isEmpty(), "root data must be cleared on FAILED")
        assertEquals(0.0, childCounter().value(), "child counter data must be cleared on FAILED")
    }

    @Test
    fun `channel SUSPENDED retains objects data`() = runTest {
        seedPool()
        ro.handleStateChange(ChannelState.suspended, false)
        flush()
        assertTrue(rootMap().data.containsKey("name"), "root data must be retained on SUSPENDED")
        assertEquals(42.0, childCounter().value(), "child counter data must be retained on SUSPENDED")
    }
}
