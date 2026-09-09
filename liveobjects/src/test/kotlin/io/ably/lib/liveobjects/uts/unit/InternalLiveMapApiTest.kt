package io.ably.lib.liveobjects.uts.unit

import com.google.gson.JsonParser
import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.message.WireObjectOperationAction
import io.ably.lib.liveobjects.value.LiveCounter
import io.ably.lib.liveobjects.value.LiveMap
import io.ably.lib.liveobjects.value.LiveMapValue
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.uts.infra.unit.MockEvent
import io.ably.lib.uts.infra.unit.MockWebSocket
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Derived from UTS spec `objects/unit/internal_live_map_api.md` — the public map read/write
 * surface (`RTLM5`, `RTLM10`–`RTLM12`, `RTLM20`–`RTLM21`, `RTLMV4`, `RTLCV4`): key reads,
 * size/entries/keys, `set`/`remove` wire messages (including value-type evaluation into
 * `*_CREATE` + `MAP_SET` sequences) and local apply-on-ACK.
 *
 * Public-tier spec: uses only the public API plus the module-local `Helpers.kt`. The spec's
 * hand-rolled capturing mock maps to the standard `setupSyncedChannel` transport plus the
 * mock's recorded `MessageFromClient` event log as the spec's `captured_messages` — see
 * [capturedObjectMessages].
 *
 * Note: evaluating a LiveCounter/LiveMap value type derives its objectId from server time
 * (RTO16), which the SDK fetches via REST GET /time. The shared setupSyncedChannel installs a
 * MockHttpClient (Helpers.kt) that answers /time locally, so these unit tests stay hermetic —
 * no real network request is made, per the UTS unit-tier no-network contract.
 */
class InternalLiveMapApiTest {

    /**
     * The spec's `captured_messages`: every OBJECT ProtocolMessage the client sent, read from
     * the mock transport's event log.
     */
    private fun capturedObjectMessages(mockWs: MockWebSocket): List<ProtocolMessage> =
        mockWs.events.filterIsInstance<MockEvent.MessageFromClient>()
            .map { it.message }
            .filter { it.action == ProtocolMessage.Action.`object` }

    /**
     * @UTS objects/unit/RTLM5/get-string-value-0
     */
    @Test
    fun `RTLM5 - get returns resolved value from InternalLiveMap`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        assertEquals("Alice", root.get("name").asString().value())
        assertEquals(30.0, root.get("age").asNumber().value()?.toDouble())
        assertEquals(true, root.get("active").asBoolean().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTLM5/get-nonexistent-key-0
     */
    @Test
    fun `RTLM5 - get returns null for non-existent key`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        assertNull(root.get("nonexistent").asString().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTLM5/get-objectid-reference-0
     */
    @Test
    fun `RTLM5 - get resolves objectId to LiveObject`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        assertEquals(100.0, root.get("score").asLiveCounter().value())
        assertEquals("alice@example.com", root.get("profile").asLiveMap().get("email").asString().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTLM10/size-non-tombstoned-0
     */
    @Test
    fun `RTLM10 - size returns non-tombstoned entry count`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        assertEquals(7L, root.size())

        client.close()
    }

    /**
     * @UTS objects/unit/RTLM11/entries-yields-pairs-0
     */
    @Test
    fun `RTLM11 - entries yields key-value pairs`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val entries = mutableListOf<String>()
        for ((key, _) in root.entries()) {
            entries.add(key)
        }

        assertContains(entries, "name")
        assertContains(entries, "age")
        assertContains(entries, "active")
        assertContains(entries, "score")
        assertContains(entries, "profile")
        assertContains(entries, "data")
        assertContains(entries, "avatar")
        assertEquals(7, entries.size)

        client.close()
    }

    /**
     * @UTS objects/unit/RTLM12/keys-0
     */
    @Test
    fun `RTLM12 - keys yields only keys`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val keys = root.keys().toList()

        assertEquals(7, keys.size)
        assertContains(keys, "name")

        client.close()
    }

    /**
     * @UTS objects/unit/RTLM20/set-sends-map-set-0
     */
    @Test
    fun `RTLM20 - set sends MAP_SET message with v6 format`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")

        root.set("name", LiveMapValue.of("Bob")).await()

        val capturedMessages = capturedObjectMessages(mockWs)
        assertEquals(1, capturedMessages.size)
        val objMsg = assertNotNull(capturedMessages[0].state)[0] as WireObjectMessage
        val op = assertNotNull(objMsg.operation)
        assertEquals(WireObjectOperationAction.MapSet, op.action)
        assertEquals("root", op.objectId)
        assertEquals("name", assertNotNull(op.mapSet).key)
        assertEquals("Bob", assertNotNull(op.mapSet).value.string)

        client.close()
    }

    /**
     * @UTS objects/unit/RTLM20/set-value-types-0
     */
    @Test
    fun `RTLM20 - set with different value types`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")

        root.set("num_key", LiveMapValue.of(42)).await()
        root.set("bool_key", LiveMapValue.of(false)).await()
        root.set("json_key", LiveMapValue.of(JsonParser.parseString("""{"nested": true}""").asJsonObject)).await()

        val capturedMessages = capturedObjectMessages(mockWs)
        val mapSet0 = assertNotNull((assertNotNull(capturedMessages[0].state)[0] as WireObjectMessage).operation?.mapSet)
        val mapSet1 = assertNotNull((assertNotNull(capturedMessages[1].state)[0] as WireObjectMessage).operation?.mapSet)
        val mapSet2 = assertNotNull((assertNotNull(capturedMessages[2].state)[0] as WireObjectMessage).operation?.mapSet)
        assertEquals(42.0, mapSet0.value.number)
        assertEquals(false, mapSet1.value.boolean)
        assertEquals(JsonParser.parseString("""{"nested": true}"""), mapSet2.value.json)

        client.close()
    }

    /**
     * @UTS objects/unit/RTLM20e7g/set-counter-value-type-0
     */
    @Test
    fun `RTLM20e7g - set with LiveCounter generates COUNTER_CREATE and MAP_SET`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")

        root.set("new_counter", LiveMapValue.of(LiveCounter.create(50))).await()

        val capturedMessages = capturedObjectMessages(mockWs)
        assertEquals(1, capturedMessages.size)
        val state = assertNotNull(capturedMessages[0].state)
        assertEquals(2, state.size)
        val createOp = assertNotNull((state[0] as WireObjectMessage).operation)
        assertEquals(WireObjectOperationAction.CounterCreate, createOp.action)
        assertTrue(createOp.objectId.startsWith("counter:"))
        val setOp = assertNotNull((state[1] as WireObjectMessage).operation)
        assertEquals(WireObjectOperationAction.MapSet, setOp.action)
        assertEquals(createOp.objectId, assertNotNull(setOp.mapSet).value.objectId)

        client.close()
    }

    /**
     * @UTS objects/unit/RTLM20e7g/set-map-value-type-0
     */
    @Test
    fun `RTLM20e7g - set with LiveMap generates nested CREATE messages and MAP_SET`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")

        root.set("nested_map", LiveMapValue.of(LiveMap.create(mapOf("key1" to LiveMapValue.of("value1"))))).await()

        val capturedMessages = capturedObjectMessages(mockWs)
        assertEquals(1, capturedMessages.size)
        val state = assertNotNull(capturedMessages[0].state)
        assertEquals(2, state.size)
        val createOp = assertNotNull((state[0] as WireObjectMessage).operation)
        assertEquals(WireObjectOperationAction.MapCreate, createOp.action)
        assertTrue(createOp.objectId.startsWith("map:"))
        val setOp = assertNotNull((state[1] as WireObjectMessage).operation)
        assertEquals(WireObjectOperationAction.MapSet, setOp.action)
        assertEquals("nested_map", assertNotNull(setOp.mapSet).key)
        assertEquals(createOp.objectId, assertNotNull(setOp.mapSet).value.objectId)

        client.close()
    }

    /**
     * @UTS objects/unit/RTLM20h1/set-nested-value-types-0
     */
    @Test
    fun `RTLM20h1 - set with nested LiveMap containing LiveCounter`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")

        root.set(
            "stats",
            LiveMapValue.of(
                LiveMap.create(
                    mapOf(
                        "count" to LiveMapValue.of(LiveCounter.create(0)),
                        "label" to LiveMapValue.of("test"),
                    ),
                ),
            ),
        ).await()

        val capturedMessages = capturedObjectMessages(mockWs)
        assertEquals(1, capturedMessages.size)
        val state = assertNotNull(capturedMessages[0].state)
        // Expect: COUNTER_CREATE, MAP_CREATE, MAP_SET (depth-first, then the MAP_SET at root)
        assertEquals(3, state.size)
        val counterCreateOp = assertNotNull((state[0] as WireObjectMessage).operation)
        assertEquals(WireObjectOperationAction.CounterCreate, counterCreateOp.action)
        assertTrue(counterCreateOp.objectId.startsWith("counter:"))
        val mapCreateOp = assertNotNull((state[1] as WireObjectMessage).operation)
        assertEquals(WireObjectOperationAction.MapCreate, mapCreateOp.action)
        assertTrue(mapCreateOp.objectId.startsWith("map:"))
        val setOp = assertNotNull((state[2] as WireObjectMessage).operation)
        assertEquals(WireObjectOperationAction.MapSet, setOp.action)
        assertEquals("stats", assertNotNull(setOp.mapSet).key)
        assertEquals(mapCreateOp.objectId, assertNotNull(setOp.mapSet).value.objectId)

        client.close()
    }

    /**
     * @UTS objects/unit/RTLM21/remove-sends-map-remove-0
     */
    @Test
    fun `RTLM21 - remove sends MAP_REMOVE message`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")

        root.remove("name").await()

        val capturedMessages = capturedObjectMessages(mockWs)
        val objMsg = assertNotNull(capturedMessages[0].state)[0] as WireObjectMessage
        val op = assertNotNull(objMsg.operation)
        assertEquals(WireObjectOperationAction.MapRemove, op.action)
        assertEquals("root", op.objectId)
        assertEquals("name", assertNotNull(op.mapRemove).key)

        client.close()
    }

    /**
     * @UTS objects/unit/RTLM20/set-applies-locally-0
     */
    @Test
    fun `RTLM20 - set applies locally after ACK`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        root.set("name", LiveMapValue.of("Bob")).await()

        assertEquals("Bob", root.get("name").asString().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTLM20/set-invalid-values-table-0
     */
    @Test
    fun `RTLM20 - Table-driven invalid set value types`() {
        // DEVIATION (see deviations.md): the spec's invalid values (function / undefined /
        // symbol) are JavaScript-only values with no Kotlin/Java equivalent, and `set` accepts
        // only the closed `LiveMapValue` union — unsupported value types are rejected at
        // compile time, so the runtime 40013 validation (RTLMV4c) is not expressible.
    }

    /**
     * @UTS objects/unit/RTLM20/set-bytes-value-0
     */
    @Test
    fun `RTLM20 - set with bytes value type`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")

        root.set("binary_data", LiveMapValue.of(byteArrayOf(1, 2, 3))).await()

        val capturedMessages = capturedObjectMessages(mockWs)
        val objMsg = assertNotNull(capturedMessages[0].state)[0] as WireObjectMessage
        assertEquals("AQID", assertNotNull(assertNotNull(objMsg.operation).mapSet).value.bytes)

        client.close()
    }
}
