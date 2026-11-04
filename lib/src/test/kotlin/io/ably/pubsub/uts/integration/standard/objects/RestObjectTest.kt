package io.ably.pubsub.uts.integration.standard.objects

import kotlin.test.assertNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ably.pubsub.http.Channel
import io.ably.pubsub.liveobjects.http.HttpLiveCounter
import io.ably.pubsub.liveobjects.http.HttpLiveMap
import io.ably.pubsub.liveobjects.http.HttpLiveObject
import io.ably.pubsub.liveobjects.http.HttpObjectData
import io.ably.pubsub.liveobjects.http.HttpObjectGenerateIdBody
import io.ably.pubsub.liveobjects.http.HttpObjectGetParams
import io.ably.pubsub.liveobjects.http.HttpObjectOperation
import io.ably.pubsub.liveobjects.http.PublishObjectData
import io.ably.pubsub.liveobjects.message.ObjectsMapSemantics
import io.ably.pubsub.types.AblyException
import io.ably.pubsub.types.Param
import io.ably.pubsub.uts.infra.integration.SandboxApp
import io.ably.pubsub.uts.infra.unit.TestHttpClient
import io.ably.pubsub.uts.unit.objects.fromJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Direct-sandbox integration test against the Ably Sandbox (`sandbox.realtime.ably-nonprod.net`,
 * via [SandboxApp.sandboxHost]) — no proxy, no fault injection. Provisions one throwaway
 * [SandboxApp] for the suite and sends real HTTP requests straight to the sandbox.
 *
 * End-to-end `HttpObject` (spec `RestObject`): `publish`, `get`/`getCompact` and `generateObjectId` against the
 * real service.
 *
 * Spec points: RSO2, RSO2g, RSO3, RSO3c, RSO4, RSO5, RSO5a1, RSO5c3, RSO5d-RSO5j, RSO6,
 * RSO9, RSO10, RSO14, RSO15. Source spec: `objects/integration/rest_object_test.md`.
 *
 * `objects/integration/RSL16/realtime-write-rest-read-0` writes through the realtime LiveObjects
 * plugin, so it lives in `:liveobjects` as `RestObjectRealtimeInteropTest`.
 *
 * Each test runs once per protocol variant (JSON / msgpack) per the spec's `PROTOCOL` dimension —
 * realised here with a `useBinaryProtocol` [ParameterizedTest] parameter.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RestObjectTest {

    private lateinit var app: SandboxApp

    @BeforeAll
    fun setUpAll() = runBlocking {
        app = SandboxApp.create()
    }

    @AfterAll
    fun tearDownAll() = runBlocking {
        if (::app.isInitialized) app.delete()
    }

    /** The spec's `create_rest_channel(test_name)`. */
    private fun createRestChannel(testName: String, useBinaryProtocol: Boolean): Channel {
        val client = TestHttpClient {
            key = app.defaultKey
            endpoint = SandboxApp.sandboxEndpoint
            this.useBinaryProtocol = useBinaryProtocol
        }
        return client.channels.get("objects-rest-$testName-" + UUID.randomUUID())
    }

    /**
     * The spec's `poll_until_success(condition)`: re-runs [condition] until it returns non-null,
     * treating a thrown error (e.g. not-found before a write is visible) as "not yet".
     */
    private suspend fun <T : Any> pollUntilSuccess(
        timeout: Duration = 20.seconds,
        interval: Duration = 250.milliseconds,
        condition: suspend () -> T?,
    ): T = withContext(Dispatchers.Default) {
        withTimeout(timeout) {
            var lastError: Throwable? = null
            while (true) {
                try {
                    condition()?.let { return@withTimeout it }
                } catch (e: AblyException) {
                    lastError = e
                }
                delay(interval)
            }
            @Suppress("UNREACHABLE_CODE")
            throw AssertionError("poll_until_success timed out", lastError)
        }
    }

    private fun json(text: String) = JsonParser.parseString(text)

    /**
     * `channel.object.getCompact(params)`: the compact result, converted from JSON to plain values
     * (maps, lists, strings, numbers, booleans) so specs' literals compare directly.
     */
    private fun Channel.compact(params: Array<Param>? = null): Any? = `object`.getCompact(params)?.let(::fromJson)

    /** Normalises every number in a compact value to `Double`, for numeric comparison. */
    private fun numbersAsDouble(value: Any?): Any? = when (value) {
        is Number -> value.toDouble()
        is Map<*, *> -> value.mapValues { numbersAsDouble(it.value) }
        is List<*> -> value.map { numbersAsDouble(it) }
        else -> value
    }

    /**
     * @UTS objects/integration/RSO3/mapset-primitives-roundtrip-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RSO3, RSO2 - mapSet primitives on the channel object, read back in compact and full modes`(useBinaryProtocol: Boolean) = runTest {
        val channel = createRestChannel("RSO3-primitives", useBinaryProtocol)

        val publishResult = channel.`object`.publish(
            listOf(
                HttpObjectOperation.mapSet("str", PublishObjectData.string("hello")).withObjectId("root"),
                HttpObjectOperation.mapSet("num", PublishObjectData.number(42.5)).withObjectId("root"),
                HttpObjectOperation.mapSet("bool", PublishObjectData.bool(true)).withObjectId("root"),
                HttpObjectOperation.mapSet("bin", PublishObjectData.bytes(byteArrayOf(0x01, 0x02, 0x03))).withObjectId("root"),
                HttpObjectOperation.mapSet("jobj", PublishObjectData.json(json("{\"a\":[1,2]}").asJsonObject)).withObjectId("root"),
                HttpObjectOperation.mapSet("jarr", PublishObjectData.json(json("[\"x\",1]").asJsonArray)).withObjectId("root"),
            ),
        )

        val compact = pollUntilSuccess {
            val c = channel.compact() as? Map<*, *>
            if (c?.get("jarr") != null) c else null
        }

        val full = channel.`object`.get()

        assertIs<String>(publishResult.messageId)
        assertTrue(publishResult.messageId.isNotEmpty())
        assertEquals(channel.name, publishResult.channel)
        assertIs<List<String>>(publishResult.objectIds)

        assertEquals("hello", compact["str"])
        assertEquals(42.5, compact["num"])
        assertEquals(true, compact["bool"])

        assertIs<HttpLiveMap>(full)
        assertEquals("root", full.objectId)
        assertEquals(ObjectsMapSemantics.LWW, full.semantics)
        fun leaf(key: String) = assertIs<HttpObjectData>(full.entries.getValue(key).data)
        assertEquals("hello", leaf("str").string)
        assertEquals(42.5, leaf("num").number)
        assertEquals(true, leaf("bool").boolean)
        assertIs<ByteArray>(leaf("bin").bytes)
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03), leaf("bin").bytes)
        assertEquals(json("{\"a\":[1,2]}"), leaf("jobj").json)
        assertEquals(json("[\"x\",1]"), leaf("jarr").json)
    }

    /**
     * @UTS objects/integration/RSO2g3/full-leaf-via-path-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RSO2g3 - full-mode get of a leaf value via path`(useBinaryProtocol: Boolean) = runTest {
        val channel = createRestChannel("RSO2g3-leaf", useBinaryProtocol)

        channel.`object`.publish(
            listOf(
                HttpObjectOperation.mapSet("bin", PublishObjectData.bytes(byteArrayOf(0xFF.toByte(), 0x00))).withObjectId("root"),
                HttpObjectOperation.mapSet("prefs", PublishObjectData.json(JsonObject().apply { addProperty("theme", "dark") })).withObjectId("root"),
            ),
        )

        val binLeaf = pollUntilSuccess {
            channel.`object`.get(HttpObjectGetParams.ofPath("bin"))
        }
        val jsonLeaf = pollUntilSuccess {
            channel.`object`.get(HttpObjectGetParams.ofPath("prefs"))
        }

        assertIs<HttpObjectData>(binLeaf)
        assertIs<ByteArray>(binLeaf.bytes)
        assertContentEquals(byteArrayOf(0xFF.toByte(), 0x00), binLeaf.bytes)
        // `encoding == null`: HttpObjectData has no encoding accessor (RSO9c1 by construction).
        assertNull(binLeaf.string)

        assertIs<HttpObjectData>(jsonLeaf)
        assertEquals(json("{\"theme\":\"dark\"}"), jsonLeaf.json)
        assertNull(jsonLeaf.string)
    }

    /**
     * @UTS objects/integration/RSO5i/counter-create-increment-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RSO5g2, RSO5i, RSO15 - counterCreate at a path, counterInc via path and objectId`(useBinaryProtocol: Boolean) = runTest {
        val channel = createRestChannel("RSO5i-counter", useBinaryProtocol)

        val createResult = channel.`object`.publish(HttpObjectOperation.counterCreate(10).withPath("visits"))

        val counterId = assertNotNull(createResult.objectIds.find { it.startsWith("counter:") })

        channel.`object`.publish(HttpObjectOperation.counterInc(5).withPath("visits"))
        channel.`object`.publish(HttpObjectOperation.counterInc(-3).withObjectId(counterId))

        pollUntilSuccess {
            val value = channel.compact(HttpObjectGetParams.ofPath("visits")) as? Number
            if (value?.toDouble() == 12.0) true else null
        }

        val full = channel.`object`.get(HttpObjectGetParams.ofObjectId(counterId))

        assertNotNull(counterId)

        assertIs<HttpLiveCounter>(full)
        assertEquals(counterId, full.objectId)
        assertEquals(12.0, full.count)
    }

    /**
     * @UTS objects/integration/RSO5j/standalone-map-create-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RSO5c3, RSO5j - standalone mapCreate returns a server-generated objectId`(useBinaryProtocol: Boolean) = runTest {
        val channel = createRestChannel("RSO5j-standalone", useBinaryProtocol)

        val result = channel.`object`.publish(
            HttpObjectOperation.mapCreate(
                ObjectsMapSemantics.LWW,
                linkedMapOf(
                    "name" to PublishObjectData.string("Alice"),
                    "age" to PublishObjectData.number(30),
                    "active" to PublishObjectData.bool(true),
                    "avatar" to PublishObjectData.bytes(byteArrayOf(0x0A, 0x0B)),
                    "prefs" to PublishObjectData.json(JsonObject().apply { addProperty("theme", "dark") }),
                    "tags" to PublishObjectData.json(json("[\"a\",\"b\"]").asJsonArray),
                ),
            ),
        )

        val mapId = result.objectIds[0]

        val full = pollUntilSuccess {
            channel.`object`.get(HttpObjectGetParams.ofObjectId(mapId))
        }

        assertEquals(1, result.objectIds.size)
        assertTrue(mapId.startsWith("map:"))

        assertIs<HttpLiveMap>(full)
        assertEquals(mapId, full.objectId)
        fun leaf(key: String) = assertIs<HttpObjectData>(full.entries.getValue(key).data)
        assertEquals("Alice", leaf("name").string)
        assertEquals(30.0, leaf("age").number)
        assertEquals(true, leaf("active").boolean)
        assertIs<ByteArray>(leaf("avatar").bytes)
        assertContentEquals(byteArrayOf(0x0A, 0x0B), leaf("avatar").bytes)
        assertEquals(json("{\"theme\":\"dark\"}"), leaf("prefs").json)
        assertEquals(json("[\"a\",\"b\"]"), leaf("tags").json)
    }

    /**
     * @UTS objects/integration/RSO5c3/map-create-at-path-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RSO5c3, RSO2g1 - mapCreate at a path is reachable from the channel object`(useBinaryProtocol: Boolean) = runTest {
        val channel = createRestChannel("RSO5c3-path", useBinaryProtocol)

        channel.`object`.publish(
            HttpObjectOperation.mapCreate(ObjectsMapSemantics.LWW, mapOf("nickname" to PublishObjectData.string("ally")))
                .withPath("profile"),
        )

        val rootFull = pollUntilSuccess {
            val r = channel.`object`.get() as? HttpLiveMap
            if (r != null && "profile" in r.entries) r else null
        }

        val profile = rootFull.entries.getValue("profile").data
        val nicknameViaPath = channel.compact(HttpObjectGetParams.ofPath("profile.nickname"))
        assertIs<HttpLiveMap>(profile)
        val nicknameViaId = channel.compact(HttpObjectGetParams.of(profile.objectId, "nickname"))

        assertTrue(profile.objectId.startsWith("map:"))
        assertEquals("ally", assertIs<HttpObjectData>(profile.entries.getValue("nickname").data).string)

        assertEquals("ally", nicknameViaPath)
        assertEquals("ally", nicknameViaId)
    }

    /**
     * @UTS objects/integration/RSO5e/mapset-nested-path-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RSO5e, RSO6 - mapSet targeted by a nested path`(useBinaryProtocol: Boolean) = runTest {
        val channel = createRestChannel("RSO5e-nested-path", useBinaryProtocol)

        channel.`object`.publish(HttpObjectOperation.mapCreate(ObjectsMapSemantics.LWW, emptyMap()).withPath("outer"))
        pollUntilSuccess { channel.compact(HttpObjectGetParams.ofPath("outer")) }

        channel.`object`.publish(HttpObjectOperation.mapCreate(ObjectsMapSemantics.LWW, emptyMap()).withPath("outer.inner"))
        pollUntilSuccess { channel.compact(HttpObjectGetParams.ofPath("outer.inner")) }

        channel.`object`.publish(
            listOf(
                HttpObjectOperation.mapSet("shallow", PublishObjectData.number(1)).withPath("outer"),
                HttpObjectOperation.mapSet("deep", PublishObjectData.string("value")).withPath("outer.inner"),
            ),
        )

        val compact = pollUntilSuccess {
            val c = numbersAsDouble(channel.compact()) as? Map<*, *>
            val outer = c?.get("outer") as? Map<*, *>
            if (outer?.get("shallow") == 1.0 && (outer["inner"] as? Map<*, *>)?.get("deep") == "value") c else null
        }

        // Numbers compared numerically (normalised to Double above).
        assertEquals(mapOf("shallow" to 1.0, "inner" to mapOf("deep" to "value")), compact["outer"])
    }

    /**
     * @UTS objects/integration/RSO5e/mapset-object-reference-1
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RSO5e, RSO7 - mapSet with an objectId reference to an existing object`(useBinaryProtocol: Boolean) = runTest {
        val channel = createRestChannel("RSO5e-object-ref", useBinaryProtocol)

        val createResult = channel.`object`.publish(HttpObjectOperation.counterCreate(5).withPath("first"))
        val counterId = createResult.objectIds.find { it.startsWith("counter:") }

        pollUntilSuccess {
            val value = channel.compact(HttpObjectGetParams.ofPath("first")) as? Number
            if (value?.toDouble() == 5.0) true else null
        }

        val setResult = channel.`object`.publish(
            HttpObjectOperation.mapSet("second", PublishObjectData.objectId(counterId!!)).withObjectId("root"),
        )

        val full = pollUntilSuccess {
            val r = channel.`object`.get() as? HttpLiveMap
            if (r != null && "second" in r.entries) r else null
        }

        val counter = channel.`object`.get(HttpObjectGetParams.ofObjectId(counterId))

        assertNotNull(counterId)

        assertIs<String>(setResult.messageId)
        assertTrue(setResult.messageId.isNotEmpty())
        assertEquals(channel.name, setResult.channel)
        assertIs<List<String>>(setResult.objectIds)

        // Each key holds the counter inlined (HttpLiveCounter) or as a bare reference (a plain
        // HttpLiveObject) - both are HttpLiveObject, so check only the objectId, per the spec.
        assertEquals(counterId, assertIs<HttpLiveObject>(full.entries.getValue("first").data).objectId)
        assertEquals(counterId, assertIs<HttpLiveObject>(full.entries.getValue("second").data).objectId)

        assertIs<HttpLiveCounter>(counter)
        assertEquals(5.0, counter.count)
    }

    /**
     * @UTS objects/integration/RSO5f/map-remove-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RSO5f - mapRemove via path and via objectId`(useBinaryProtocol: Boolean) = runTest {
        val channel = createRestChannel("RSO5f-remove", useBinaryProtocol)

        channel.`object`.publish(
            listOf(
                HttpObjectOperation.mapCreate(
                    ObjectsMapSemantics.LWW,
                    linkedMapOf("a" to PublishObjectData.number(1), "b" to PublishObjectData.number(2)),
                ).withPath("settings"),
                HttpObjectOperation.mapSet("keep", PublishObjectData.string("x")).withObjectId("root"),
                HttpObjectOperation.mapSet("drop", PublishObjectData.string("y")).withObjectId("root"),
            ),
        )

        channel.`object`.publish(
            listOf(
                HttpObjectOperation.mapRemove("a").withPath("settings"),
                HttpObjectOperation.mapRemove("drop").withObjectId("root"),
            ),
        )

        val compact = pollUntilSuccess {
            val c = channel.compact() as? Map<*, *>
            val settings = c?.get("settings") as? Map<*, *>
            if (c != null && "drop" !in c && settings != null && "a" !in settings) c else null
        }

        assertEquals("x", compact["keep"])
        assertFalse("drop" in compact)
        // Compared numerically: the server returns 2 over JSON and 2.0 over msgpack.
        assertEquals(mapOf("b" to 2.0), numbersAsDouble(compact["settings"]))
    }

    /**
     * @UTS objects/integration/RSO6/wildcard-path-passthrough-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RSO6 - wildcard path passed through to the server`(useBinaryProtocol: Boolean) = runTest {
        val channel = createRestChannel("RSO6-wildcard", useBinaryProtocol)

        channel.`object`.publish(HttpObjectOperation.mapCreate(ObjectsMapSemantics.LWW, emptyMap()).withPath("items"))
        pollUntilSuccess { channel.compact(HttpObjectGetParams.ofPath("items")) }

        channel.`object`.publish(
            listOf(
                HttpObjectOperation.mapCreate(ObjectsMapSemantics.LWW, emptyMap()).withPath("items.one"),
                HttpObjectOperation.mapCreate(ObjectsMapSemantics.LWW, emptyMap()).withPath("items.two"),
            ),
        )
        pollUntilSuccess {
            val i = channel.compact(HttpObjectGetParams.ofPath("items")) as? Map<*, *>
            if (i != null && "one" in i && "two" in i) true else null
        }

        channel.`object`.publish(HttpObjectOperation.mapSet("flag", PublishObjectData.bool(true)).withPath("items.*"))

        val items = pollUntilSuccess {
            val i = channel.compact(HttpObjectGetParams.ofPath("items")) as? Map<*, *>
            if ((i?.get("one") as? Map<*, *>)?.get("flag") == true && (i["two"] as? Map<*, *>)?.get("flag") == true) i else null
        }

        assertEquals(true, (items["one"] as Map<*, *>)["flag"])
        assertEquals(true, (items["two"] as Map<*, *>)["flag"])
    }

    /**
     * @UTS objects/integration/RSO4/generated-ids-batch-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RSO4, RSO5d, RSO5h - locally generated object IDs accepted in a single batch`(useBinaryProtocol: Boolean) = runTest {
        val channel = createRestChannel("RSO4-generated", useBinaryProtocol)

        val counter = channel.`object`.generateObjectId(HttpObjectGenerateIdBody.counterCreate(7))
        val map = channel.`object`.generateObjectId(
            HttpObjectGenerateIdBody.mapCreate(
                ObjectsMapSemantics.LWW,
                linkedMapOf(
                    "visits" to PublishObjectData.objectId(counter.objectId),
                    "label" to PublishObjectData.string("stats"),
                    "raw" to PublishObjectData.bytes(byteArrayOf(0x01)),
                ),
            ),
        )

        val result = channel.`object`.publish(
            listOf(
                HttpObjectOperation.counterCreateWithObjectId(counter),
                HttpObjectOperation.mapCreateWithObjectId(map),
                HttpObjectOperation.mapSet("stats", PublishObjectData.objectId(map.objectId)).withObjectId("root"),
            ),
        )

        val stats = pollUntilSuccess {
            channel.`object`.get(HttpObjectGetParams.ofPath("stats"))
        }

        assertTrue(counter.objectId.startsWith("counter:"))
        assertTrue(map.objectId.startsWith("map:"))
        assertContains(result.objectIds, counter.objectId)
        assertContains(result.objectIds, map.objectId)

        assertIs<HttpLiveMap>(stats)
        assertEquals(map.objectId, stats.objectId)
        assertEquals("stats", assertIs<HttpObjectData>(stats.entries.getValue("label").data).string)
        assertContentEquals(byteArrayOf(0x01), assertIs<HttpObjectData>(stats.entries.getValue("raw").data).bytes)

        val visits = stats.entries.getValue("visits").data
        assertIs<HttpLiveCounter>(visits)
        assertEquals(counter.objectId, visits.objectId)
        assertEquals(7.0, visits.count)
    }

    /**
     * @UTS objects/integration/RSO5a1/idempotent-operation-id-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RSO5a1 - operation id makes a publish idempotent`(useBinaryProtocol: Boolean) = runTest {
        val channel = createRestChannel("RSO5a1-idempotent", useBinaryProtocol)

        channel.`object`.publish(HttpObjectOperation.counterCreate(0).withPath("count"))
        pollUntilSuccess {
            val value = channel.compact(HttpObjectGetParams.ofPath("count")) as? Number
            if (value?.toDouble() == 0.0) true else null
        }

        val opId = "idem-" + UUID.randomUUID()

        channel.`object`.publish(HttpObjectOperation.counterInc(1).withId(opId).withPath("count"))
        channel.`object`.publish(HttpObjectOperation.counterInc(1).withId(opId).withPath("count"))

        // Marker write published after both increments; once it is visible, both increments have been processed
        channel.`object`.publish(HttpObjectOperation.mapSet("marker", PublishObjectData.bool(true)).withObjectId("root"))
        val compact = pollUntilSuccess {
            val c = channel.compact() as? Map<*, *>
            if (c?.get("marker") == true) c else null
        }

        assertEquals(1.0, (compact["count"] as Number).toDouble())
    }

    /**
     * @UTS objects/integration/RSO2h/unresolvable-target-error-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RSO2h - server error for an unresolvable target surfaced as ErrorInfo`(useBinaryProtocol: Boolean) = runTest {
        val channel = createRestChannel("RSO2h-errors", useBinaryProtocol)

        val error = assertFailsWith<AblyException> {
            channel.compact(
                HttpObjectGetParams.ofObjectId("map:doesNotExistAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA@1700000000000"),
            )
        }
        assertNotNull(error.errorInfo)
        assertTrue(error.errorInfo.statusCode in 400..499, "statusCode was ${error.errorInfo.statusCode}")

        val pathError = assertFailsWith<AblyException> {
            channel.compact(HttpObjectGetParams.ofPath("no.such.path"))
        }
        assertNotNull(pathError.errorInfo)
        assertTrue(pathError.errorInfo.statusCode in 400..499, "statusCode was ${pathError.errorInfo.statusCode}")
    }

    /**
     * @UTS objects/integration/RSO3/mapset-edge-values-roundtrip-1
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RSO3, RSO2 - edge-case leaf values round-trip without being dropped or altered`(useBinaryProtocol: Boolean) = runTest {
        val channel = createRestChannel("RSO3-edge-values", useBinaryProtocol)

        val maxExactInt = 9007199254740991L // 2^53 - 1

        channel.`object`.publish(
            listOf(
                HttpObjectOperation.mapSet("emptyStr", PublishObjectData.string("")).withObjectId("root"),
                HttpObjectOperation.mapSet("emptyBin", PublishObjectData.bytes(byteArrayOf())).withObjectId("root"),
                HttpObjectOperation.mapSet("zero", PublishObjectData.number(0)).withObjectId("root"),
                HttpObjectOperation.mapSet("maxInt", PublishObjectData.number(maxExactInt)).withObjectId("root"),
                HttpObjectOperation.mapSet("minInt", PublishObjectData.number(-maxExactInt)).withObjectId("root"),
                HttpObjectOperation.mapSet("no", PublishObjectData.bool(false)).withObjectId("root"),
            ),
        )

        val compact = pollUntilSuccess {
            val c = channel.compact() as? Map<*, *>
            if (c != null && "no" in c) c else null
        }

        val full = channel.`object`.get()

        // Numbers compared numerically: compact numbers arrive as Long over JSON and may be Double over msgpack.
        assertEquals("", compact["emptyStr"])
        assertEquals(0.0, numbersAsDouble(compact["zero"]))
        assertEquals(maxExactInt.toDouble(), numbersAsDouble(compact["maxInt"]))
        assertEquals(-maxExactInt.toDouble(), numbersAsDouble(compact["minInt"]))
        assertEquals(false, compact["no"])

        assertIs<HttpLiveMap>(full)
        fun leaf(key: String) = assertIs<HttpObjectData>(full.entries.getValue(key).data)
        assertEquals("", leaf("emptyStr").string)
        assertIs<ByteArray>(leaf("emptyBin").bytes)
        assertContentEquals(byteArrayOf(), leaf("emptyBin").bytes)
        assertEquals(0.0, leaf("zero").number)
        assertEquals(maxExactInt.toDouble(), leaf("maxInt").number)
        assertEquals(-maxExactInt.toDouble(), leaf("minInt").number)
        assertEquals(false, leaf("no").boolean)
    }

    /**
     * @UTS objects/integration/RSO5g2/standalone-counter-create-1
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RSO5g2, RSO5j - standalone counterCreate returns a server-generated objectId`(useBinaryProtocol: Boolean) = runTest {
        val channel = createRestChannel("RSO5g2-standalone", useBinaryProtocol)

        val result = channel.`object`.publish(HttpObjectOperation.counterCreate(42))

        val counterId = result.objectIds[0]

        val full = pollUntilSuccess {
            channel.`object`.get(HttpObjectGetParams.ofObjectId(counterId))
        }

        assertEquals(1, result.objectIds.size)
        assertTrue(counterId.startsWith("counter:"))

        assertIs<HttpLiveCounter>(full)
        assertEquals(counterId, full.objectId)
        assertEquals(42.0, full.count)
    }

    /**
     * @UTS objects/integration/RSO6/wildcard-remove-increment-1
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RSO6, RSO5f, RSO5i - wildcard path with mapRemove and counterInc`(useBinaryProtocol: Boolean) = runTest {
        val channel = createRestChannel("RSO6-wildcard-ops", useBinaryProtocol)

        channel.`object`.publish(
            listOf(
                HttpObjectOperation.mapCreate(ObjectsMapSemantics.LWW, emptyMap()).withPath("maps"),
                HttpObjectOperation.mapCreate(ObjectsMapSemantics.LWW, emptyMap()).withPath("counters"),
            ),
        )
        pollUntilSuccess {
            val c = channel.compact() as? Map<*, *>
            if (c != null && "maps" in c && "counters" in c) true else null
        }

        channel.`object`.publish(
            listOf(
                HttpObjectOperation.mapCreate(
                    ObjectsMapSemantics.LWW,
                    linkedMapOf("foo" to PublishObjectData.string("a"), "keep" to PublishObjectData.number(1)),
                ).withPath("maps.one"),
                HttpObjectOperation.mapCreate(
                    ObjectsMapSemantics.LWW,
                    linkedMapOf("foo" to PublishObjectData.string("b"), "keep" to PublishObjectData.number(2)),
                ).withPath("maps.two"),
                HttpObjectOperation.counterCreate(10).withPath("counters.c1"),
                HttpObjectOperation.counterCreate(20).withPath("counters.c2"),
            ),
        )
        pollUntilSuccess {
            val c = channel.compact() as? Map<*, *>
            val maps = c?.get("maps") as? Map<*, *>
            val counters = c?.get("counters") as? Map<*, *>
            if (maps != null && counters != null && "one" in maps && "two" in maps && "c1" in counters && "c2" in counters) true else null
        }

        channel.`object`.publish(
            listOf(
                HttpObjectOperation.mapRemove("foo").withPath("maps.*"),
                HttpObjectOperation.counterInc(5).withPath("counters.*"),
            ),
        )

        val compact = pollUntilSuccess {
            val c = numbersAsDouble(channel.compact()) as? Map<*, *>
            val maps = c?.get("maps") as? Map<*, *>
            val counters = c?.get("counters") as? Map<*, *>
            val one = maps?.get("one") as? Map<*, *>
            val two = maps?.get("two") as? Map<*, *>
            if (one != null && two != null && "foo" !in one && "foo" !in two &&
                counters?.get("c1") == 15.0 && counters["c2"] == 25.0
            ) c else null
        }

        // Numbers compared numerically (normalised to Double above).
        val maps = compact["maps"] as Map<*, *>
        val counters = compact["counters"] as Map<*, *>
        assertEquals(mapOf("keep" to 1.0), maps["one"])
        assertEquals(mapOf("keep" to 2.0), maps["two"])
        assertEquals(15.0, counters["c1"])
        assertEquals(25.0, counters["c2"])
    }
}
