package io.ably.lib.liveobjects.unit

import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.ObjectsOperationSource
import io.ably.lib.liveobjects.message.WireObjectData
import io.ably.lib.liveobjects.message.WireObjectDelete
import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.message.WireObjectOperation
import io.ably.lib.liveobjects.message.WireObjectOperationAction
import io.ably.lib.liveobjects.message.WireObjectState
import io.ably.lib.liveobjects.message.WireObjectsMap
import io.ably.lib.liveobjects.message.WireObjectsMapSemantics
import io.ably.lib.liveobjects.value.ObjectUpdate
import io.ably.lib.liveobjects.value.livemap.InternalLiveMap
import io.ably.lib.liveobjects.value.livemap.LiveMapEntry
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Derived from UTS `objects/unit/internal_live_map.md` — the RTLO4e10 root-tombstone guard.
 *
 * Lives in the `:liveobjects` module's own test source because these tests assert on the
 * internal CRDT graph (`InternalLiveMap`, `WireObjectMessage`, `ObjectUpdate.NoOp`), which is
 * not on the `uts` module's compile classpath (see uts-to-kotlin `objects-mapping.md` §13).
 * The channel-level complement is `RTO10c1b1` in the uts module's `RealtimeObjectTest`.
 */
class LiveObjectTombstoneTest {

  private lateinit var realtimeObject: DefaultRealtimeObject

  private fun rootMapWithNameEntry(): InternalLiveMap {
    realtimeObject = DefaultRealtimeObject("test", getMockAblyClientAdapter())
    val map = InternalLiveMap.zeroValue("root", realtimeObject)
    map.data["name"] = LiveMapEntry(timeserial = "01", data = WireObjectData(string = "Alice"))
    map.siteTimeserials["site1"] = "00"
    return map
  }

  @After
  fun tearDown() {
    realtimeObject.objectsPool.dispose() // the pool init starts a real GC coroutine
    unmockkAll() // getMockAblyClientAdapter uses mockkStatic - clean up global state
  }

  /**
   * @UTS objects/unit/RTLO4e10/object-delete-root-noop-0
   */
  @Test
  fun `RTLO4e10 - OBJECT_DELETE targeting root is rejected`() {
    val map = rootMapWithNameEntry()

    val msg = WireObjectMessage(
      serial = "01",
      siteCode = "site1",
      serialTimestamp = 1_700_000_000_000L,
      operation = WireObjectOperation(
        action = WireObjectOperationAction.ObjectDelete,
        objectId = "root",
        objectDelete = WireObjectDelete,
      ),
    )

    map.applyObject(msg, ObjectsOperationSource.CHANNEL)

    assertFalse(map.isTombstoned)
    assertEquals("Alice", map.data["name"]?.data?.string) // data untouched
  }

  /**
   * @UTS objects/unit/RTLO4e10/replace-data-tombstone-root-noop-0
   */
  @Test
  fun `RTLO4e10 - replaceData with tombstone flag targeting root is rejected`() {
    val map = rootMapWithNameEntry()

    val stateMsg = WireObjectMessage(
      objectState = WireObjectState(
        objectId = "root",
        siteTimeserials = mapOf("site1" to "01"),
        tombstone = true,
        map = WireObjectsMap(semantics = WireObjectsMapSemantics.LWW, entries = emptyMap()),
      ),
    )

    val update = map.applyObjectSync(stateMsg)

    assertFalse(map.isTombstoned)
    assertEquals("Alice", map.data["name"]?.data?.string) // data untouched
    assertEquals(ObjectUpdate.NoOp, update)
  }
}
