package io.ably.lib.liveobjects.uts.unit

import com.google.gson.JsonParser
import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.message.WireMapCreate
import io.ably.lib.liveobjects.message.WireObjectData
import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.message.WireObjectOperationAction
import io.ably.lib.liveobjects.message.WireObjectsMapSemantics
import io.ably.lib.liveobjects.unit.getMockAblyClientAdapter
import io.ably.lib.liveobjects.value.LiveCounter
import io.ably.lib.liveobjects.value.LiveMap
import io.ably.lib.liveobjects.value.LiveMapValue
import io.ably.lib.liveobjects.value.livecounter.DefaultLiveCounter
import io.ably.lib.liveobjects.value.livemap.DefaultLiveMap
import io.ably.lib.types.AblyException
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Derived from UTS spec `objects/unit/value_types.md` — the `LiveCounter` / `LiveMap` creation
 * value types (`RTLCV1`–`RTLCV4`, `RTLMV1`–`RTLMV4`): immutable blueprints created via the
 * static `create` factories, evaluated into v6 `*CreateWithObjectId` ObjectMessages.
 *
 * The `create` surface is public API (`objects-mapping.md` §6); the evaluation half is
 * internal/wire-level (§13), so the spec's `evaluate(vt)` maps to the internal evaluation entry
 * points ([DefaultLiveCounter.createCounterCreateMessage] / [DefaultLiveMap.createMapCreateMessages],
 * RTLCV4h / RTLMV4k) called against a §17.1 `DefaultRealtimeObject`. Several "invalid input"
 * cases are rejected at compile time by the typed signatures — see the T-4/T-6 entries in
 * `deviations.md`.
 */
class ValueTypesTest {

    private lateinit var ro: DefaultRealtimeObject

    @BeforeTest
    fun setUp() {
        // §17.1 instantiation — evaluation needs a DefaultRealtimeObject for the RTO14/RTO16
        // objectId derivation. Teardown per S-4 (see deviations.md).
        ro = DefaultRealtimeObject("test", getMockAblyClientAdapter())
    }

    @AfterTest
    fun tearDown() {
        ro.objectsPool.dispose()
        unmockkAll()
    }

    /**
     * The spec's `evaluate(vt)` for a counter value type — the internal RTLCV4 evaluation
     * (`createCounterCreateMessage`), which produces the single COUNTER_CREATE message (RTLCV4h).
     */
    private suspend fun evaluate(vt: LiveCounter): List<WireObjectMessage> =
        listOf((vt as DefaultLiveCounter).createCounterCreateMessage(ro))

    /**
     * The spec's `evaluate(vt)` for a map value type — the internal RTLMV4 evaluation
     * (`createMapCreateMessages`), returning `[nested creates..., MAP_CREATE]` depth-first (RTLMV4k).
     */
    private suspend fun evaluate(vt: LiveMap): List<WireObjectMessage> =
        (vt as DefaultLiveMap).createMapCreateMessages(ro)

    /**
     * @UTS objects/unit/RTLCV3/create-with-count-0
     */
    @Test
    fun `RTLCV3 - LiveCounter create with initial count`() {
        val vt = LiveCounter.create(42)

        assertIs<LiveCounter>(vt)
        // DEVIATION T-6: the retained count has no public accessor (RTLCV3d) — asserted via the
        // module-internal DefaultLiveCounter.initialCount. See deviations.md.
        assertEquals(42.0, (vt as DefaultLiveCounter).initialCount.toDouble())
    }

    /**
     * @UTS objects/unit/RTLCV3/create-default-zero-0
     */
    @Test
    fun `RTLCV3 - LiveCounter create defaults to 0`() {
        val vt = LiveCounter.create()

        // DEVIATION T-6: retained count read via the module-internal field — see deviations.md.
        assertEquals(0.0, (vt as DefaultLiveCounter).initialCount.toDouble())
    }

    /**
     * @UTS objects/unit/RTLCV3c/no-validation-at-create-0
     */
    @Test
    fun `RTLCV3c - no validation at creation time`() {
        // DEVIATION T-4: the spec's invalid input `LiveCounter.create("not_a_number")` is not
        // expressible — `create(@NotNull Number)` rejects a String at compile time. The
        // runtime-reachable invalid input (a non-finite Number, RTLCV4a) exercises the same
        // "no validation at creation" behaviour. See deviations.md.
        val vt = LiveCounter.create(Double.NaN)

        assertIs<LiveCounter>(vt) // does not throw
    }

    /**
     * @UTS objects/unit/RTLCV4/evaluate-generates-message-0
     */
    @Test
    fun `RTLCV4 - evaluation generates COUNTER_CREATE ObjectMessage`() = runTest {
        val vt = LiveCounter.create(42)
        val messages = evaluate(vt)

        assertEquals(1, messages.size)
        val msg = messages[0]
        val operation = assertNotNull(msg.operation)
        assertEquals(WireObjectOperationAction.CounterCreate, operation.action)
        assertTrue(operation.objectId.startsWith("counter:"))
        assertTrue(operation.objectId.contains("@"))
        val counterCreateWithObjectId = assertNotNull(operation.counterCreateWithObjectId)
        assertNotNull(counterCreateWithObjectId.nonce)
        assertTrue(counterCreateWithObjectId.nonce.length >= 16)
        assertNotNull(counterCreateWithObjectId.initialValue)
    }

    /**
     * @UTS objects/unit/RTLCV4g5/retains-local-counter-create-0
     */
    @Test
    fun `RTLCV4g5 - evaluation retains local CounterCreate`() = runTest {
        val vt = LiveCounter.create(42)
        val messages = evaluate(vt)

        val msg = messages[0]
        // The retained CounterCreate (RTLCV4g5, spec `operation.counterCreate`) is carried as the
        // local-only `derivedFrom` on the wire CounterCreateWithObjectId (@Transient, CCRO2).
        val retained = assertNotNull(assertNotNull(msg.operation).counterCreateWithObjectId?.derivedFrom)
        assertEquals(42.0, retained.count)
    }

    /**
     * @UTS objects/unit/RTLCV4a/evaluate-validates-count-0
     */
    @Test
    fun `RTLCV4a - evaluation validates count type`() = runTest {
        // DEVIATION T-4: the spec's `LiveCounter.create("not_a_number")` input is compile-time
        // blocked; the runtime-reachable RTLCV4a input ("not a Number OR not finite") is a
        // non-finite Number, which the same validation rejects at evaluation time. See deviations.md.
        val vt = LiveCounter.create(Double.NaN)

        val error = assertFailsWith<AblyException> { evaluate(vt) }

        assertEquals(40003, error.errorInfo.code)
    }

    /**
     * @UTS objects/unit/RTLCV4/evaluate-zero-count-0
     */
    @Test
    fun `RTLCV4 - evaluation with count 0`() = runTest {
        val vt = LiveCounter.create(0)
        val messages = evaluate(vt)

        val msg = messages[0]
        val retained = assertNotNull(assertNotNull(msg.operation).counterCreateWithObjectId?.derivedFrom)
        assertEquals(0.0, retained.count)
    }

    /**
     * @UTS objects/unit/RTLMV3/create-with-entries-0
     */
    @Test
    fun `RTLMV3 - LiveMap create with entries`() {
        val vt = LiveMap.create(
            mapOf(
                "name" to LiveMapValue.of("Alice"),
                "age" to LiveMapValue.of(30),
            ),
        )

        assertIs<LiveMap>(vt)
        // DEVIATION T-6: the retained entries have no public accessor (RTLMV3d) — asserted via
        // the module-internal DefaultLiveMap.entries. See deviations.md.
        val entries = (vt as DefaultLiveMap).entries
        assertEquals("Alice", assertNotNull(entries["name"]).asString)
        assertEquals(30.0, assertNotNull(entries["age"]).asNumber.toDouble())
    }

    /**
     * @UTS objects/unit/RTLMV3/create-no-entries-0
     */
    @Test
    fun `RTLMV3 - LiveMap create with no entries`() {
        val vt = LiveMap.create()

        assertIs<LiveMap>(vt)
    }

    /**
     * @UTS objects/unit/RTLMV4/evaluate-generates-message-0
     */
    @Test
    fun `RTLMV4 - evaluation generates MAP_CREATE ObjectMessage`() = runTest {
        val vt = LiveMap.create(mapOf("name" to LiveMapValue.of("Alice")))
        val messages = evaluate(vt)

        assertEquals(1, messages.size)
        val msg = messages[0]
        val operation = assertNotNull(msg.operation)
        assertEquals(WireObjectOperationAction.MapCreate, operation.action)
        assertTrue(operation.objectId.startsWith("map:"))
        val mapCreateWithObjectId = assertNotNull(operation.mapCreateWithObjectId)
        assertTrue(mapCreateWithObjectId.nonce.length >= 16)
        assertNotNull(mapCreateWithObjectId.initialValue)
    }

    /**
     * @UTS objects/unit/RTLMV4j5/retains-local-map-create-0
     */
    @Test
    fun `RTLMV4j5 - evaluation retains local MapCreate`() = runTest {
        val vt = LiveMap.create(mapOf("name" to LiveMapValue.of("Alice")))
        val messages = evaluate(vt)

        val msg = messages[0]
        // The retained MapCreate (RTLMV4j5, spec `operation.mapCreate`) is carried as the
        // local-only `derivedFrom` on the wire MapCreateWithObjectId (@Transient, MCRO2).
        val retained = assertNotNull(assertNotNull(msg.operation).mapCreateWithObjectId?.derivedFrom)
        assertEquals(WireObjectsMapSemantics.LWW, retained.semantics)
        assertEquals("Alice", assertNotNull(retained.entries["name"]?.data).string)
    }

    /**
     * @UTS objects/unit/RTLMV4d/entry-value-types-0
     */
    @Test
    fun `RTLMV4d - entry value type mapping`() = runTest {
        val jsonArr = JsonParser.parseString("""[1, 2, 3]""").asJsonArray
        val jsonObj = JsonParser.parseString("""{"key": "value"}""").asJsonObject
        val vt = LiveMap.create(
            mapOf(
                "str" to LiveMapValue.of("hello"),
                "num" to LiveMapValue.of(42),
                "bool" to LiveMapValue.of(true),
                "json_arr" to LiveMapValue.of(jsonArr),
                "json_obj" to LiveMapValue.of(jsonObj),
            ),
        )
        val messages = evaluate(vt)

        val msg = messages[0]
        val entries = assertNotNull(assertNotNull(msg.operation).mapCreateWithObjectId?.derivedFrom).entries
        assertEquals("hello", assertNotNull(entries["str"]?.data).string)
        assertEquals(42.0, assertNotNull(entries["num"]?.data).number)
        assertEquals(true, assertNotNull(entries["bool"]?.data).boolean)
        assertEquals(jsonArr, assertNotNull(entries["json_arr"]?.data).json)
        assertEquals(jsonObj, assertNotNull(entries["json_obj"]?.data).json)
    }

    /**
     * @UTS objects/unit/RTLMV4d1/nested-value-types-0
     */
    @Test
    fun `RTLMV4d1 RTLMV4d2 - nested value types produce depth-first ObjectMessages`() = runTest {
        val innerCounter = LiveCounter.create(10)
        val innerMap = LiveMap.create(mapOf("nested_count" to LiveMapValue.of(innerCounter)))
        val outer = LiveMap.create(mapOf("child" to LiveMapValue.of(innerMap)))
        val messages = evaluate(outer)

        assertEquals(3, messages.size)
        assertEquals(WireObjectOperationAction.CounterCreate, assertNotNull(messages[0].operation).action)
        assertTrue(assertNotNull(messages[0].operation).objectId.startsWith("counter:"))
        assertEquals(WireObjectOperationAction.MapCreate, assertNotNull(messages[1].operation).action)
        assertTrue(assertNotNull(messages[1].operation).objectId.startsWith("map:"))
        assertEquals(WireObjectOperationAction.MapCreate, assertNotNull(messages[2].operation).action)
        assertTrue(assertNotNull(messages[2].operation).objectId.startsWith("map:"))

        val innerCounterId = assertNotNull(messages[0].operation).objectId
        val innerMapId = assertNotNull(messages[1].operation).objectId

        val innerMapCreate = assertNotNull(assertNotNull(messages[1].operation).mapCreateWithObjectId?.derivedFrom)
        assertEquals(innerCounterId, assertNotNull(innerMapCreate.entries["nested_count"]?.data).objectId)
        val outerMapCreate = assertNotNull(assertNotNull(messages[2].operation).mapCreateWithObjectId?.derivedFrom)
        assertEquals(innerMapId, assertNotNull(outerMapCreate.entries["child"]?.data).objectId)
    }

    /**
     * @UTS objects/unit/RTLMV4a/evaluate-validates-entries-0
     */
    @Test
    fun `RTLMV4a - evaluation validates entries type`() {
        // DEVIATION T-4: `LiveMap.create(null)` is not expressible — `create(@NotNull Map<String,
        // LiveMapValue>)` rejects null (and any non-Dict) at compile time, so the RTLMV4a runtime
        // validation is unreachable in ably-java. No runtime assertion. See deviations.md.
    }

    /**
     * @UTS objects/unit/RTLMV4b/evaluate-validates-keys-0
     */
    @Test
    fun `RTLMV4b - evaluation validates key types`() {
        // DEVIATION T-4: a non-String map key (`{ 123: "value" }`) cannot be constructed against
        // `Map<String, LiveMapValue>` — compile-time blocked, per the spec's own
        // language-applicability note. No runtime assertion. See deviations.md.
    }

    /**
     * @UTS objects/unit/RTLMV4c/evaluate-validates-values-0
     */
    @Test
    fun `RTLMV4c - evaluation validates value types`() {
        // DEVIATION T-4: an unsupported entry value (`{ "fn": some_function }`) is not expressible —
        // the closed LiveMapValue union has no function/unsupported-type member, so the RTLMV4c
        // 40013 validation is unreachable in ably-java. No runtime assertion. See deviations.md.
    }

    /**
     * @UTS objects/unit/RTLMV4e2/empty-entries-0
     */
    @Test
    fun `RTLMV4e2 - empty entries produces MapCreate with empty entries`() = runTest {
        val vt = LiveMap.create()
        val messages = evaluate(vt)

        val msg = messages[0]
        val retained = assertNotNull(assertNotNull(msg.operation).mapCreateWithObjectId?.derivedFrom)
        assertEquals(emptyMap(), retained.entries)
    }

    /**
     * @UTS objects/unit/RTLMV4d/map-set-all-types-table-0
     */
    @Test
    fun `RTLMV4d - table-driven MAP_SET value type mapping`() = runTest {
        val scenarios: List<Pair<LiveMapValue, (WireObjectData) -> Unit>> = listOf(
            LiveMapValue.of("hello") to { data -> assertEquals("hello", data.string) },
            LiveMapValue.of(42) to { data -> assertEquals(42.0, data.number) },
            LiveMapValue.of(3.14) to { data -> assertEquals(3.14, data.number) },
            LiveMapValue.of(0) to { data -> assertEquals(0.0, data.number) },
            LiveMapValue.of(-1) to { data -> assertEquals(-1.0, data.number) },
            LiveMapValue.of(true) to { data -> assertEquals(true, data.boolean) },
            LiveMapValue.of(false) to { data -> assertEquals(false, data.boolean) },
            LiveMapValue.of(JsonParser.parseString("""[1, "a", null]""").asJsonArray) to { data ->
                assertEquals(JsonParser.parseString("""[1, "a", null]"""), data.json)
            },
            LiveMapValue.of(JsonParser.parseString("""{"k": "v"}""").asJsonObject) to { data ->
                assertEquals(JsonParser.parseString("""{"k": "v"}"""), data.json)
            },
            LiveMapValue.of(byteArrayOf(1, 2, 3)) to { data -> assertEquals("AQID", data.bytes) },
        )

        for ((input, verify) in scenarios) {
            val vt = LiveMap.create(mapOf("test_key" to input))
            val messages = evaluate(vt)
            val retained: WireMapCreate =
                assertNotNull(assertNotNull(messages[0].operation).mapCreateWithObjectId?.derivedFrom)
            verify(assertNotNull(retained.entries["test_key"]?.data))
        }
    }
}
