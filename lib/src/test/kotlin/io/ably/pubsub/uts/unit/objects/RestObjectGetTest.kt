package io.ably.pubsub.uts.unit.objects

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.ably.pubsub.liveobjects.HttpObject
import io.ably.pubsub.liveobjects.http.HttpLiveCounter
import io.ably.pubsub.liveobjects.http.HttpLiveMap
import io.ably.pubsub.liveobjects.http.HttpLiveObject
import io.ably.pubsub.liveobjects.http.HttpObjectData
import io.ably.pubsub.liveobjects.http.HttpObjectGetParams
import io.ably.pubsub.liveobjects.message.ObjectsMapSemantics
import io.ably.pubsub.types.AblyException
import kotlinx.coroutines.test.runTest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * `RestChannel#object` and `RestObject#get` — spec `RestObject` is ably-java's [HttpObject], reached
 * as `` channel.`object` `` on an HTTP channel.
 *
 * Source spec: `objects/unit/rest_object_get.md`. Spec points: RSL16, RSL16b, RSO1, RSO1a, RSO2,
 * RSO2a1-RSO2h, RSO6, RSO8, RSO9, RSO9a-RSO9d, RSO14, RSO15, RSO16.
 */
class RestObjectGetTest {

    /** The spec's `setup_rest_object(channel_name, response_body, use_binary_protocol)`. */
    private fun setup(channelName: String, responseBody: Any?, useBinaryProtocol: Boolean = false) =
        setupRestObject(channelName, useBinaryProtocol) { req ->
            req.respondWithBody(200, responseBody, useBinaryProtocol)
        }

    /**
     * @UTS objects/unit/RSO1a/one-instance-per-channel-0
     */
    @Test
    fun `RSO1a - one RestObject instance per RestChannel`() = runTest {
        val (client, channel) = setup("test-RSO1a-a-" + randomId(), emptyMap<String, Any?>()).let { it.client to it.channel }
        val otherChannel = client.channels.get("test-RSO1a-b-" + randomId())

        assertSame(channel.`object`, channel.`object`)
        assertNotSame(channel.`object`, otherChannel.`object`)
    }

    /**
     * @UTS objects/unit/RSL16b/missing-plugin-error-0
     */
    @Test
    fun `RSL16b - accessing channel object without the Objects plugin`() = runTest {
        // DEVIATION (not applicable, per the spec's own note; see deviations.md "RSL16b"): HttpObject
        // is built into the HTTP client, so there is no plugin to omit and no 40019 error. Assert
        // instead that a client configured without any plugin gets a working `channel.object`.
        val setup = setupRestObject("test-RSL16b-" + randomId()) { req -> req.respondWith(200, emptyMap<String, Any?>()) }

        assertIs<HttpObject>(setup.channel.`object`)
        setup.channel.`object`.getCompact()
        assertEquals(1, setup.capturedRequests.size)
    }

    /**
     * @UTS objects/unit/RSO2c/get-default-endpoint-0
     */
    @Test
    fun `RSO2c - get with no params sends GET to channel object endpoint`() = runTest {
        val channelName = "test-RSO2c-default-" + randomId()
        val setup = setup(channelName, emptyMap<String, Any?>())

        setup.channel.`object`.getCompact()

        assertEquals(1, setup.capturedRequests.size)
        val request = setup.capturedRequests[0]
        assertEquals("GET", request.method)
        assertEquals("/channels/" + encodeUriComponent(channelName) + "/object", request.path)
        assertFalse("path" in request.queryParams)
        assertFalse("compact" in request.queryParams)
    }

    /**
     * @UTS objects/unit/RSO2c/get-objectid-path-segment-0
     */
    @Test
    fun `RSO2c - get with objectId appends percent-encoded path segment`() = runTest {
        val channelName = "test-RSO2c-objectid-" + randomId()
        val testCases = listOf(
            "root",
            "map:QWsI5HQnz7AbhLgn9U2xlA2wq0Ax7HHYbJtJDF9mVqg@1700000000000",
            "counter:a/b c?d#e@1700000000000",
        )

        for (objectId in testCases) {
            val setup = setup(channelName, emptyMap<String, Any?>())

            setup.channel.`object`.getCompact(HttpObjectGetParams.ofObjectId(objectId))

            assertEquals(1, setup.capturedRequests.size)
            val request = setup.capturedRequests[0]
            assertEquals("GET", request.method)
            assertEquals(
                "/channels/" + encodeUriComponent(channelName) + "/object/" + encodeUriComponent(objectId),
                request.path,
            )
        }
    }

    /**
     * @UTS objects/unit/RSO2d/path-compact-query-params-0
     */
    @Test
    fun `RSO2d - path and compact sent as query parameters`() = runTest {
        val channelName = "test-RSO2d-" + randomId()
        // DEVIATION (adapted, see deviations.md "RSO2a1c"): there is no `compact` param; `compact: false`
        // is `get`, and `compact: true` is `getCompact`, which leaves it unset (the server default).
        data class Case(val call: (HttpObject) -> Unit, val expectedPathSuffix: String, val expectedQuery: Map<String, String>)
        val testCases = listOf(
            Case({ it.getCompact(HttpObjectGetParams.ofPath("a.b.c")) }, "/object", mapOf("path" to "a.b.c")),
            Case({ it.get() }, "/object", mapOf("compact" to "false")),
            Case({ it.getCompact() }, "/object", emptyMap()),
            Case(
                { it.get(HttpObjectGetParams.of("map:abc@1", "x.y")) },
                "/object/" + encodeUriComponent("map:abc@1"),
                mapOf("path" to "x.y", "compact" to "false"),
            ),
        )

        for (testCase in testCases) {
            val setup = setup(channelName, emptyMap<String, Any?>())

            testCase.call(setup.channel.`object`)

            val request = setup.capturedRequests[0]
            assertEquals("/channels/" + encodeUriComponent(channelName) + testCase.expectedPathSuffix, request.path)
            assertEquals(testCase.expectedQuery, request.queryParams)
        }
    }

    /**
     * @UTS objects/unit/RSO6/get-path-verbatim-0
     */
    @Test
    fun `RSO6 - get passes path to the server verbatim`() = runTest {
        val channelName = "test-RSO6-get-" + randomId()
        val testCases = listOf(
            "items.*.count",
            "a\\.b.c",
            ".a..b.",
            "",
            "with space/and?query&chars=1#frag",
            "ünïcödé.键",
        )

        for (path in testCases) {
            val setup = setup(channelName, emptyMap<String, Any?>())

            setup.channel.`object`.getCompact(HttpObjectGetParams.ofPath(path))

            assertEquals(1, setup.capturedRequests.size)
            // queryParams holds URL-decoded values: the decoded value must equal the input exactly
            assertEquals(path, setup.capturedRequests[0].queryParams["path"])
        }
    }

    /**
     * @UTS objects/unit/RSO2f/compact-returned-verbatim-0
     */
    @Test
    fun `RSO2f, RSO8 - compact response returned verbatim`() = runTest {
        val compactBody = mapOf(
            "name" to "Alice",
            "age" to 30L,
            "active" to true,
            "visits" to 7L,
            "avatar" to "AQID",
            "prefs" to "{\"theme\":\"dark\"}",
            "tags" to "[\"a\",\"b\"]",
            "nested" to mapOf("inner" to mapOf("deep" to 1L)),
            "empty" to emptyMap<String, Any?>(),
            "cycle" to mapOf("objectId" to "map:cycleHash@1700000000000"),
        )
        val setup = setup("test-RSO2f-" + randomId(), compactBody)

        // DEVIATION (adapted, see deviations.md "RSO2a1c"): `getCompact()` is the compact format, so the
        // spec's explicit `compact: true` call is the same call. The result is a JsonElement.
        val result = assertIs<JsonObject>(setup.channel.`object`.getCompact())

        assertEquals(compactBody, fromJson(result))

        // String values are not Base64- or JSON-decoded
        assertEquals(JsonPrimitive("AQID"), result["avatar"])
        assertEquals(JsonPrimitive("{\"theme\":\"dark\"}"), result["prefs"])
    }

    /**
     * @UTS objects/unit/RSO2f/compact-scalar-verbatim-1
     */
    @Test
    fun `RSO2f - compact scalar response returned verbatim`() = runTest {
        val testCases = listOf<Any>(42L, -1.5, "hello", false)

        for (responseBody in testCases) {
            val setup = setup("test-RSO2f-scalar-" + randomId(), responseBody)

            val result = assertIs<JsonPrimitive>(setup.channel.`object`.getCompact(HttpObjectGetParams.ofPath("some.key")))

            assertEquals(responseBody, fromJson(result))
        }
    }

    /**
     * @UTS objects/unit/RSO2e/compact-msgpack-decoded-0
     */
    @Test
    fun `RSO2e, RSO2f - compact msgpack response decoded per RSC8 and returned verbatim`() = runTest {
        val compactBody = mapOf(
            "name" to "Alice",
            "count" to 5L,
            "avatar" to byteArrayOf(0x01, 0x02, 0x03),
        )
        val setup = setup("test-RSO2e-msgpack-" + randomId(), compactBody, useBinaryProtocol = true)

        val result = assertIs<JsonObject>(setup.channel.`object`.getCompact())

        // DEVIATION (adapted, see deviations.md "RSO2e"): spec expects Accept == "application/x-msgpack";
        // every binary-protocol request in ably-java sends msgpack first with a JSON fallback.
        assertEquals("application/x-msgpack,application/json", setup.capturedRequests[0].header("Accept"))

        assertEquals("Alice", result["name"].asString)
        assertEquals(5L, result["count"].asLong)
        // DEVIATION (adapted, see deviations.md "RSO2a1c"): the compact result is JSON, so binary
        // values arrive as Base64 strings, as over the JSON protocol, rather than as raw bytes.
        assertEquals("AQID", result["avatar"].asString)
    }

    /**
     * @UTS objects/unit/RSO2g1/full-map-recursive-decode-0
     */
    @Test
    fun `RSO2g1, RSO2g2, RSO2g3, RSO9a, RSO14, RSO16 - full map response decoded recursively`() = runTest {
        val fullBody = mapOf(
            "objectId" to "root",
            "map" to mapOf(
                "semantics" to "LWW",
                "entries" to mapOf(
                    "str" to mapOf("data" to mapOf("string" to "hello")),
                    "num" to mapOf("data" to mapOf("number" to 42L)),
                    "bool" to mapOf("data" to mapOf("boolean" to true)),
                    "bin" to mapOf("data" to mapOf("bytes" to Base64.getEncoder().encodeToString(byteArrayOf(0x01, 0x02, 0x03)))),
                    "jobj" to mapOf("data" to mapOf("json" to "{\"a\":1}")),
                    "jarr" to mapOf("data" to mapOf("json" to "[1,2,3]")),
                    "visits" to mapOf(
                        "data" to mapOf(
                            "objectId" to "counter:visitsHash@1700000000000",
                            "counter" to mapOf("data" to mapOf("number" to 7L)),
                        ),
                    ),
                    "profile" to mapOf(
                        "data" to mapOf(
                            "objectId" to "map:profileHash@1700000000000",
                            "map" to mapOf(
                                "semantics" to "LWW",
                                "entries" to mapOf("nickname" to mapOf("data" to mapOf("string" to "ally"))),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val setup = setup("test-RSO2g1-" + randomId(), fullBody)

        val result = setup.channel.`object`.get()

        assertIs<HttpLiveMap>(result)
        assertEquals("root", result.objectId)
        assertEquals(ObjectsMapSemantics.LWW, result.semantics)
        assertEquals(setOf("str", "num", "bool", "bin", "jobj", "jarr", "visits", "profile"), result.entries.keys)

        // Leaves (RSO2g3, RSO9c)
        fun leaf(key: String) = assertIs<HttpObjectData>(result.entries.getValue(key).data)
        assertEquals("hello", leaf("str").string)
        assertEquals(42.0, leaf("num").number)
        assertEquals(true, leaf("bool").boolean)
        assertIs<ByteArray>(leaf("bin").bytes)
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03), leaf("bin").bytes)
        assertEquals(mapOf("a" to 1L), fromJson(leaf("jobj").json!!))
        assertEquals(listOf(1L, 2L, 3L), fromJson(leaf("jarr").json!!))
        assertNull(leaf("jobj").string)

        // Nested counter (RSO2g2, RSO15)
        val visits = result.entries.getValue("visits").data
        assertIs<HttpLiveCounter>(visits)
        assertEquals("counter:visitsHash@1700000000000", visits.objectId)
        assertEquals(7.0, visits.count)

        // Nested map, decoded recursively (RSO2g1, RSO16)
        val profile = result.entries.getValue("profile").data
        assertIs<HttpLiveMap>(profile)
        assertEquals("map:profileHash@1700000000000", profile.objectId)
        assertEquals(ObjectsMapSemantics.LWW, profile.semantics)
        assertEquals("ally", assertIs<HttpObjectData>(profile.entries.getValue("nickname").data).string)
    }

    /**
     * @UTS objects/unit/RSO2g1/full-empty-values-decode-1
     */
    @Test
    fun `RSO2g1, RSO2g3, RSO9c - empty map and empty leaf values in a full response`() = runTest {
        val fullBody = mapOf(
            "objectId" to "root",
            "map" to mapOf(
                "semantics" to "LWW",
                "entries" to mapOf(
                    "emptyMap" to mapOf(
                        "data" to mapOf(
                            "objectId" to "map:emptyHash@1700000000000",
                            "map" to mapOf("semantics" to "LWW", "entries" to emptyMap<String, Any?>()),
                        ),
                    ),
                    "emptyStr" to mapOf("data" to mapOf("string" to "")),
                    "emptyBin" to mapOf("data" to mapOf("bytes" to "")),
                    "zero" to mapOf("data" to mapOf("number" to 0L)),
                    "no" to mapOf("data" to mapOf("boolean" to false)),
                ),
            ),
        )
        val setup = setup("test-RSO2g1-empty-" + randomId(), fullBody)

        val result = setup.channel.`object`.get()

        assertIs<HttpLiveMap>(result)
        assertEquals(setOf("emptyMap", "emptyStr", "emptyBin", "zero", "no"), result.entries.keys)

        val emptyMap = result.entries.getValue("emptyMap").data
        assertIs<HttpLiveMap>(emptyMap)
        assertEquals("map:emptyHash@1700000000000", emptyMap.objectId)
        assertEquals(ObjectsMapSemantics.LWW, emptyMap.semantics)
        assertEquals(emptyMap(), emptyMap.entries)

        fun leaf(key: String) = assertIs<HttpObjectData>(result.entries.getValue(key).data)
        assertEquals("", leaf("emptyStr").string)
        assertIs<ByteArray>(leaf("emptyBin").bytes)
        assertContentEquals(byteArrayOf(), leaf("emptyBin").bytes)
        assertEquals(0.0, leaf("zero").number)
        assertEquals(false, leaf("no").boolean)
    }

    /**
     * @UTS objects/unit/RSO2g2/full-counter-0
     */
    @Test
    fun `RSO2g2, RSO9b, RSO15 - full counter response`() = runTest {
        val fullBody = mapOf(
            "objectId" to "counter:abcHash@1700000000000",
            "counter" to mapOf("data" to mapOf("number" to -3.5)),
        )
        val setup = setup("test-RSO2g2-" + randomId(), fullBody)

        val result = setup.channel.`object`.get(
            HttpObjectGetParams.ofObjectId("counter:abcHash@1700000000000"),
        )

        assertIs<HttpLiveCounter>(result)
        assertEquals("counter:abcHash@1700000000000", result.objectId)
        assertEquals(-3.5, result.count)
    }

    /**
     * @UTS objects/unit/RSO2g3/full-leaf-decode-json-0
     */
    @Test
    fun `RSO2g3, RSO9c - full leaf response decoded per OD5 (JSON protocol)`() = runTest {
        data class Case(val responseBody: Map<String, Any?>, val expectedField: String, val expectedValue: Any?)
        val testCases = listOf(
            Case(mapOf("string" to "hello"), "string", "hello"),
            Case(mapOf("number" to 3.25), "number", 3.25),
            Case(mapOf("boolean" to false), "boolean", false),
            Case(mapOf("bytes" to "AQID"), "bytes", byteArrayOf(0x01, 0x02, 0x03)),
            Case(mapOf("json" to "{\"k\":\"v\"}"), "json", mapOf("k" to "v")),
            Case(mapOf("json" to "[1,\"a\",null]"), "json", listOf(1L, "a", null)),
        )

        for (testCase in testCases) {
            val setup = setup("test-RSO2g3-" + randomId(), testCase.responseBody)

            val result = setup.channel.`object`.get(HttpObjectGetParams.ofPath("leaf"))

            assertIs<HttpObjectData>(result)
            assertLeafField(result, testCase.expectedField, testCase.expectedValue)
            assertNull(result.objectId)
            // `encoding == null`: HttpObjectData has no encoding accessor (RSO9c1 holds by construction).
            if (testCase.expectedField == "bytes") assertIs<ByteArray>(result.bytes)
        }
    }

    /**
     * @UTS objects/unit/RSO2g3/full-leaf-decode-msgpack-1
     */
    @Test
    fun `RSO2g3, RSO9c2 - full leaf response decoded per OD5 (MessagePack protocol)`() = runTest {
        data class Case(val responseBody: Map<String, Any?>, val expectedField: String, val expectedValue: Any?)
        val testCases = listOf(
            Case(mapOf("bytes" to byteArrayOf(0xFF.toByte(), 0x00)), "bytes", byteArrayOf(0xFF.toByte(), 0x00)),
            Case(mapOf("json" to "{\"k\":[1,2]}"), "json", mapOf("k" to listOf(1L, 2L))),
            Case(mapOf("string" to "plain"), "string", "plain"),
        )

        for (testCase in testCases) {
            val setup = setup("test-RSO2g3-msgpack-" + randomId(), testCase.responseBody, useBinaryProtocol = true)

            val result = setup.channel.`object`.get(HttpObjectGetParams.ofPath("leaf"))

            assertIs<HttpObjectData>(result)
            assertLeafField(result, testCase.expectedField, testCase.expectedValue)
            // `encoding == null`: HttpObjectData has no encoding accessor (RSO9c1 holds by construction).
        }
    }

    /**
     * @UTS objects/unit/RSO2g4/unrecognized-object-passthrough-0
     */
    @Test
    fun `RSO2g4, RSO9d - unrecognized object shape passed through`() = runTest {
        val fullBody = mapOf(
            "objectId" to "root",
            "map" to mapOf(
                "semantics" to "LWW",
                "entries" to mapOf(
                    "known" to mapOf("data" to mapOf("string" to "ok")),
                    "future" to mapOf(
                        "data" to mapOf(
                            "objectId" to "future:xyzHash@1700000000000",
                            "futureType" to mapOf("someField" to 1L),
                        ),
                    ),
                ),
            ),
        )
        val topLevelBody = mapOf(
            "objectId" to "future:topHash@1700000000000",
            "futureType" to mapOf("someField" to 2L),
        )

        val setup = setup("test-RSO2g4-entry-" + randomId(), fullBody)
        val mapResult = setup.channel.`object`.get()

        val setup2 = setup("test-RSO2g4-top-" + randomId(), topLevelBody)
        val topResult = setup2.channel.`object`.get()

        assertIs<HttpLiveMap>(mapResult)
        assertEquals("ok", assertIs<HttpObjectData>(mapResult.entries.getValue("known").data).string)

        val futureEntry = assertIs<HttpLiveObject>(mapResult.entries.getValue("future").data)
        assertEquals("future:xyzHash@1700000000000", futureEntry.objectId)
        assertEquals(mapOf("someField" to 1L), fromJson(futureEntry.unrecognizedFields["futureType"]))

        assertIs<HttpLiveObject>(topResult)
        assertEquals("future:topHash@1700000000000", topResult.objectId)
        assertEquals(mapOf("someField" to 2L), fromJson(topResult.unrecognizedFields["futureType"]))
    }

    /**
     * @UTS objects/unit/RSO2h/no-validation-server-error-0
     */
    @Test
    fun `RSO2h - get performs no client-side validation and surfaces server errors`() = runTest {
        val channelName = "test-RSO2h-" + randomId()
        val setup = setupRestObject(channelName) { req ->
            req.respondWith(
                404,
                mapOf("error" to mapOf("code" to 40400, "statusCode" to 404, "message" to "Object not found")),
            )
        }

        val error = assertFailsWith<AblyException> {
            setup.channel.`object`.get(
                HttpObjectGetParams.of("not-a-valid-object-id", "..*.."),
            )
        }

        assertEquals(1, setup.capturedRequests.size)
        assertEquals(
            "/channels/" + encodeUriComponent(channelName) + "/object/" + encodeUriComponent("not-a-valid-object-id"),
            setup.capturedRequests[0].path,
        )
        assertEquals("..*..", setup.capturedRequests[0].queryParams["path"])

        assertEquals(40400, error.errorInfo.code)
        assertEquals(404, error.errorInfo.statusCode)
    }

    /** `result[expected_field] == expected_value` over the typed leaf getters. */
    private fun assertLeafField(result: HttpObjectData, field: String, expected: Any?) {
        when (field) {
            "string" -> assertEquals(expected, result.string)
            "number" -> assertEquals(expected, result.number)
            "boolean" -> assertEquals(expected, result.boolean)
            "bytes" -> assertContentEquals(expected as ByteArray, result.bytes)
            "json" -> assertEquals(expected, fromJson(result.json!!))
            else -> error("Unknown leaf field $field")
        }
    }
}
