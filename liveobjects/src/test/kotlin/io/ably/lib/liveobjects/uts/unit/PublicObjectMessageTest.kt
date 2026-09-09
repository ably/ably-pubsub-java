package io.ably.lib.liveobjects.uts.unit

import com.google.gson.JsonObject
import io.ably.lib.liveobjects.message.ObjectOperation
import io.ably.lib.liveobjects.message.ObjectOperationAction
import io.ably.lib.liveobjects.message.ObjectsMapSemantics
import io.ably.lib.liveobjects.message.WireCounterCreate
import io.ably.lib.liveobjects.message.WireCounterCreateWithObjectId
import io.ably.lib.liveobjects.message.WireCounterInc
import io.ably.lib.liveobjects.message.WireMapClear
import io.ably.lib.liveobjects.message.WireMapCreate
import io.ably.lib.liveobjects.message.WireMapCreateWithObjectId
import io.ably.lib.liveobjects.message.WireMapRemove
import io.ably.lib.liveobjects.message.WireMapSet
import io.ably.lib.liveobjects.message.WireObjectData
import io.ably.lib.liveobjects.message.WireObjectDelete
import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.message.WireObjectOperation
import io.ably.lib.liveobjects.message.WireObjectOperationAction
import io.ably.lib.liveobjects.message.WireObjectsMapEntry
import io.ably.lib.liveobjects.message.WireObjectsMapSemantics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Derived from UTS spec `objects/unit/public_object_message.md` — construction of
 * `PublicAPI::ObjectMessage` from an internal ObjectMessage (`PAOM1`–`PAOM3`) and of
 * `PublicAPI::ObjectOperation` from an internal ObjectOperation (`PAOOP1`–`PAOOP3`).
 *
 * Pure data-structure construction, no mocks. The spec's
 * `PublicObjectMessage.fromObjectMessage(source, channel)` maps to the module-local
 * `buildPublicObjectMessage(wireMessage, channelName)` helper (`objects-mapping.md` §11/§13);
 * the source ObjectMessage / ObjectOperation are the typed `Wire*` constructions (§17.10).
 */
class PublicObjectMessageTest {

    /**
     * The spec's `PublicObjectOperation.fromObjectOperation(source_operation)`: ably-java has no
     * standalone public-operation factory — the PublicAPI::ObjectOperation is derived from the
     * enclosing message per PAOM3d (which performs the PAOOP3 construction), so operation-only
     * tests wrap the source operation in a minimal ObjectMessage and read `operation` back.
     */
    private fun publicOperationFrom(sourceOperation: WireObjectOperation): ObjectOperation =
        buildPublicObjectMessage(WireObjectMessage(operation = sourceOperation), "test").operation

    /**
     * @UTS objects/unit/PAOM3/construction-all-fields-0
     */
    @Test
    fun `PAOM3 - construction copies all fields from source ObjectMessage`() {
        val extras = JsonObject().apply { addProperty("key", "value") }
        val source = WireObjectMessage(
            id = "msg-id-1",
            clientId = "client-1",
            connectionId = "conn-1",
            timestamp = 1_700_000_000_000L,
            serial = "01",
            serialTimestamp = 1_700_000_001_000L,
            siteCode = "site1",
            extras = extras,
            operation = WireObjectOperation(
                action = WireObjectOperationAction.MapSet,
                objectId = "map:abc@1000",
                mapSet = WireMapSet(key = "name", value = WireObjectData(string = "Alice")),
            ),
        )

        val publicMsg = buildPublicObjectMessage(source, "test-channel")

        assertEquals("msg-id-1", publicMsg.id)
        assertEquals("client-1", publicMsg.clientId)
        assertEquals("conn-1", publicMsg.connectionId)
        assertEquals(1_700_000_000_000L, publicMsg.timestamp)
        assertEquals("test-channel", publicMsg.channel)
        assertEquals("01", publicMsg.serial)
        assertEquals(1_700_000_001_000L, publicMsg.serialTimestamp)
        assertEquals("site1", publicMsg.siteCode)
        assertEquals(extras, publicMsg.extras)
        val operation = assertNotNull(publicMsg.operation)
        assertEquals(ObjectOperationAction.MAP_SET, operation.action)
        assertEquals("map:abc@1000", operation.objectId)
        assertEquals("name", assertNotNull(operation.mapSet).key)
    }

    /**
     * @UTS objects/unit/PAOM3/construction-optional-fields-missing-0
     */
    @Test
    fun `PAOM3 - construction with optional fields missing`() {
        val source = WireObjectMessage(
            operation = WireObjectOperation(
                action = WireObjectOperationAction.CounterInc,
                objectId = "counter:abc@1000",
                counterInc = WireCounterInc(number = 5.0),
            ),
        )

        val publicMsg = buildPublicObjectMessage(source, "my-channel")

        assertNull(publicMsg.id)
        assertNull(publicMsg.clientId)
        assertNull(publicMsg.connectionId)
        assertNull(publicMsg.timestamp)
        assertEquals("my-channel", publicMsg.channel)
        assertNull(publicMsg.serial)
        assertNull(publicMsg.serialTimestamp)
        assertNull(publicMsg.siteCode)
        assertNull(publicMsg.extras)
        val operation = assertNotNull(publicMsg.operation)
        assertEquals(ObjectOperationAction.COUNTER_INC, operation.action)
    }

    /**
     * @UTS objects/unit/PAOM3/channel-from-channel-name-0
     */
    @Test
    fun `PAOM3b - channel is set from channel name not from ObjectMessage`() {
        val source = WireObjectMessage(
            operation = WireObjectOperation(
                action = WireObjectOperationAction.ObjectDelete,
                objectId = "counter:abc@1000",
            ),
        )

        val publicMsg = buildPublicObjectMessage(source, "different-channel-name")

        assertEquals("different-channel-name", publicMsg.channel)
    }

    /**
     * @UTS objects/unit/PAOOP3/map-set-copies-fields-0
     */
    @Test
    fun `PAOOP3a - MAP_SET operation copies mapSet omits unrelated fields`() {
        val sourceOperation = WireObjectOperation(
            action = WireObjectOperationAction.MapSet,
            objectId = "map:abc@1000",
            mapSet = WireMapSet(key = "color", value = WireObjectData(string = "blue")),
        )

        val publicOp = publicOperationFrom(sourceOperation)

        assertEquals(ObjectOperationAction.MAP_SET, publicOp.action)
        assertEquals("map:abc@1000", publicOp.objectId)
        assertEquals("color", assertNotNull(publicOp.mapSet).key)
        assertEquals("blue", assertNotNull(publicOp.mapSet).value.string)
        assertNull(publicOp.mapCreate)
        assertNull(publicOp.mapRemove)
        assertNull(publicOp.counterCreate)
        assertNull(publicOp.counterInc)
        assertNull(publicOp.objectDelete)
        assertNull(publicOp.mapClear)
    }

    /**
     * @UTS objects/unit/PAOOP3/map-remove-copies-fields-0
     */
    @Test
    fun `PAOOP3a - MAP_REMOVE operation copies mapRemove omits unrelated fields`() {
        val sourceOperation = WireObjectOperation(
            action = WireObjectOperationAction.MapRemove,
            objectId = "map:abc@1000",
            mapRemove = WireMapRemove(key = "old-key"),
        )

        val publicOp = publicOperationFrom(sourceOperation)

        assertEquals(ObjectOperationAction.MAP_REMOVE, publicOp.action)
        assertEquals("map:abc@1000", publicOp.objectId)
        assertEquals("old-key", assertNotNull(publicOp.mapRemove).key)
        assertNull(publicOp.mapCreate)
        assertNull(publicOp.mapSet)
        assertNull(publicOp.counterCreate)
        assertNull(publicOp.counterInc)
        assertNull(publicOp.objectDelete)
        assertNull(publicOp.mapClear)
    }

    /**
     * @UTS objects/unit/PAOOP3/counter-inc-copies-fields-0
     */
    @Test
    fun `PAOOP3a - COUNTER_INC operation copies counterInc omits unrelated fields`() {
        val sourceOperation = WireObjectOperation(
            action = WireObjectOperationAction.CounterInc,
            objectId = "counter:abc@1000",
            counterInc = WireCounterInc(number = 42.0),
        )

        val publicOp = publicOperationFrom(sourceOperation)

        assertEquals(ObjectOperationAction.COUNTER_INC, publicOp.action)
        assertEquals("counter:abc@1000", publicOp.objectId)
        assertEquals(42.0, assertNotNull(publicOp.counterInc).number)
        assertNull(publicOp.mapCreate)
        assertNull(publicOp.mapSet)
        assertNull(publicOp.mapRemove)
        assertNull(publicOp.counterCreate)
        assertNull(publicOp.objectDelete)
        assertNull(publicOp.mapClear)
    }

    /**
     * @UTS objects/unit/PAOOP3/object-delete-copies-fields-0
     */
    @Test
    fun `PAOOP3a - OBJECT_DELETE operation copies objectDelete omits unrelated fields`() {
        val sourceOperation = WireObjectOperation(
            action = WireObjectOperationAction.ObjectDelete,
            objectId = "counter:abc@1000",
            objectDelete = WireObjectDelete,
        )

        val publicOp = publicOperationFrom(sourceOperation)

        assertEquals(ObjectOperationAction.OBJECT_DELETE, publicOp.action)
        assertEquals("counter:abc@1000", publicOp.objectId)
        assertNotNull(publicOp.objectDelete)
        assertNull(publicOp.mapCreate)
        assertNull(publicOp.mapSet)
        assertNull(publicOp.mapRemove)
        assertNull(publicOp.counterCreate)
        assertNull(publicOp.counterInc)
        assertNull(publicOp.mapClear)
    }

    /**
     * @UTS objects/unit/PAOOP3/map-clear-copies-fields-0
     */
    @Test
    fun `PAOOP3a - MAP_CLEAR operation copies mapClear omits unrelated fields`() {
        val sourceOperation = WireObjectOperation(
            action = WireObjectOperationAction.MapClear,
            objectId = "map:abc@1000",
            mapClear = WireMapClear,
        )

        val publicOp = publicOperationFrom(sourceOperation)

        assertEquals(ObjectOperationAction.MAP_CLEAR, publicOp.action)
        assertEquals("map:abc@1000", publicOp.objectId)
        assertNotNull(publicOp.mapClear)
        assertNull(publicOp.mapCreate)
        assertNull(publicOp.mapSet)
        assertNull(publicOp.mapRemove)
        assertNull(publicOp.counterCreate)
        assertNull(publicOp.counterInc)
        assertNull(publicOp.objectDelete)
    }

    /**
     * @UTS objects/unit/PAOOP3/map-create-direct-0
     */
    @Test
    fun `PAOOP3b1 - MAP_CREATE with mapCreate directly present`() {
        val sourceOperation = WireObjectOperation(
            action = WireObjectOperationAction.MapCreate,
            objectId = "map:new@2000",
            mapCreate = WireMapCreate(
                semantics = WireObjectsMapSemantics.LWW,
                entries = mapOf("key1" to WireObjectsMapEntry(data = WireObjectData(string = "val1"))),
            ),
        )

        val publicOp = publicOperationFrom(sourceOperation)

        assertEquals(ObjectOperationAction.MAP_CREATE, publicOp.action)
        assertEquals("map:new@2000", publicOp.objectId)
        val mapCreate = assertNotNull(publicOp.mapCreate)
        assertEquals(ObjectsMapSemantics.LWW, mapCreate.semantics)
        assertEquals("val1", assertNotNull(mapCreate.entries["key1"]?.data).string)
        assertNull(publicOp.counterCreate)
    }

    /**
     * @UTS objects/unit/PAOOP3/map-create-from-with-object-id-0
     */
    @Test
    fun `PAOOP3b2 - MAP_CREATE resolved from mapCreateWithObjectId`() {
        val derivedMapCreate = WireMapCreate(
            semantics = WireObjectsMapSemantics.LWW,
            entries = mapOf("x" to WireObjectsMapEntry(data = WireObjectData(number = 10.0))),
        )
        // NOTE: the spec's pseudo mapCreateWithObjectId lists { objectId, semantics, entries,
        // derivedFrom }; the ably-java wire type (MCRO2) carries { initialValue, nonce } plus the
        // local-only retained MapCreate as `derivedFrom` (RTLMV4j5) — the semantics/entries live on
        // derivedFrom, and objectId lives on the operation.
        val sourceOperation = WireObjectOperation(
            action = WireObjectOperationAction.MapCreate,
            objectId = "map:derived@3000",
            mapCreateWithObjectId = WireMapCreateWithObjectId(
                initialValue = """{"semantics":0,"entries":{"x":{"data":{"number":10}}}}""",
                nonce = "nonce-0123456789ab",
                derivedFrom = derivedMapCreate,
            ),
        )

        val publicOp = publicOperationFrom(sourceOperation)

        assertEquals(ObjectOperationAction.MAP_CREATE, publicOp.action)
        assertEquals("map:derived@3000", publicOp.objectId)
        val mapCreate = assertNotNull(publicOp.mapCreate)
        assertEquals(ObjectsMapSemantics.LWW, mapCreate.semantics)
        assertEquals(10.0, assertNotNull(mapCreate.entries["x"]?.data).number)
        assertNull(publicOp.counterCreate)
    }

    /**
     * @UTS objects/unit/PAOOP3/counter-create-from-with-object-id-0
     */
    @Test
    fun `PAOOP3c2 - COUNTER_CREATE resolved from counterCreateWithObjectId`() {
        val derivedCounterCreate = WireCounterCreate(count = 100.0)
        // NOTE: as for PAOOP3b2 above — the wire CounterCreateWithObjectId (CCRO2) carries
        // { initialValue, nonce, derivedFrom }; count lives on the retained derivedFrom.
        val sourceOperation = WireObjectOperation(
            action = WireObjectOperationAction.CounterCreate,
            objectId = "counter:derived@3000",
            counterCreateWithObjectId = WireCounterCreateWithObjectId(
                initialValue = """{"count":100}""",
                nonce = "nonce-0123456789ab",
                derivedFrom = derivedCounterCreate,
            ),
        )

        val publicOp = publicOperationFrom(sourceOperation)

        assertEquals(ObjectOperationAction.COUNTER_CREATE, publicOp.action)
        assertEquals("counter:derived@3000", publicOp.objectId)
        val counterCreate = assertNotNull(publicOp.counterCreate)
        assertEquals(100.0, counterCreate.count)
        assertNull(publicOp.mapCreate)
    }

    /**
     * @UTS objects/unit/PAOOP3/create-payloads-omitted-0
     */
    @Test
    fun `PAOOP3b3 PAOOP3c3 - create payloads omitted when neither variant is present`() {
        val sourceOperation = WireObjectOperation(
            action = WireObjectOperationAction.MapSet,
            objectId = "map:abc@1000",
            mapSet = WireMapSet(key = "k", value = WireObjectData(string = "v")),
        )

        val publicOp = publicOperationFrom(sourceOperation)

        assertNull(publicOp.mapCreate)
        assertNull(publicOp.counterCreate)
    }

    /**
     * @UTS objects/unit/PAOOP3/only-relevant-field-per-action-0
     */
    @Test
    fun `PAOOP3 - only the relevant operation field is present per action type`() {
        val sourceOperation = WireObjectOperation(
            action = WireObjectOperationAction.CounterCreate,
            objectId = "counter:new@2000",
            counterCreate = WireCounterCreate(count = 50.0),
        )

        val publicOp = publicOperationFrom(sourceOperation)

        assertEquals(ObjectOperationAction.COUNTER_CREATE, publicOp.action)
        assertEquals("counter:new@2000", publicOp.objectId)
        val counterCreate = assertNotNull(publicOp.counterCreate)
        assertEquals(50.0, counterCreate.count)
        assertNull(publicOp.mapCreate)
        assertNull(publicOp.mapSet)
        assertNull(publicOp.mapRemove)
        assertNull(publicOp.counterInc)
        assertNull(publicOp.objectDelete)
        assertNull(publicOp.mapClear)
    }
}
