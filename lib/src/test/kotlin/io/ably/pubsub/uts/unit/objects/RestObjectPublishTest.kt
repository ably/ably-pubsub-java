package io.ably.pubsub.uts.unit.objects

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ably.pubsub.liveobjects.http.HttpObjectOperation
import io.ably.pubsub.liveobjects.http.PublishObjectData
import io.ably.pubsub.liveobjects.message.ObjectsMapSemantics
import io.ably.pubsub.types.AblyException
import io.ably.pubsub.uts.infra.unit.ClientOptionsBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * `RestObject#publish` — ably-java's `HttpObject.publish`. Spec `RestObjectOperation(field: Body(...))`
 * constructions become the `HttpObjectOperation` factories plus `withObjectId` / `withPath` /
 * `withId`; spec `"LWW"` is `ObjectsMapSemantics.LWW`; `RestObjectMapEntry(data: x)` is the entry
 * value `x` itself (`Map<String, PublishObjectData>`); `PublishObjectData(boolean: b)` is
 * `PublishObjectData.bool(b)`.
 *
 * Source spec: `objects/unit/rest_object_publish.md`. Spec points: RSO3, RSO3a1-RSO3h, RSO5,
 * RSO5a-RSO5i, RSO6, RSO7, RSO10, RSO13.
 */
class RestObjectPublishTest {

    private val defaultPublishResponse = mapOf(
        "messageId" to "msg-id-1",
        "channel" to "ignored-by-tests",
        "objectIds" to emptyList<String>(),
    )

    /** The spec's `setup_rest_object(channel_name, use_binary_protocol, client_options)`. */
    private fun setup(
        channelName: String,
        useBinaryProtocol: Boolean = false,
        clientOptions: ClientOptionsBuilder.() -> Unit = {},
    ) = setupRestObject(channelName, useBinaryProtocol, clientOptions) { req ->
        req.respondWithBody(201, defaultPublishResponse, useBinaryProtocol)
    }

    /** `parse_json(request.body)` of the first captured request, as a list of wire operations. */
    private fun RestObjectSetup.jsonBody(): List<Map<*, *>> =
        assertIs<List<*>>(parseJson(capturedRequests[0].body)).map { assertIs<Map<*, *>>(it) }

    private fun Map<*, *>.obj(key: String): Map<*, *> = assertIs<Map<*, *>>(this[key])

    /**
     * @UTS objects/unit/RSO3e/single-op-post-0
     */
    @Test
    fun `RSO3e - single operation sent as POST with a one-element JSON array body`() = runTest {
        val channelName = "test-RSO3e-single-" + randomId()
        val setup = setup(channelName)

        setup.channel.`object`.publish(
            HttpObjectOperation.mapSet("greeting", PublishObjectData.string("hello")).withObjectId("root"),
        )

        assertEquals(1, setup.capturedRequests.size)
        val request = setup.capturedRequests[0]
        assertEquals("POST", request.method)
        assertEquals("/channels/" + encodeUriComponent(channelName) + "/object", request.path)
        assertEquals("application/json", request.header("Content-Type"))

        val body = parseJson(request.body)
        assertIs<List<*>>(body)
        assertEquals(1, body.size)
        val op = assertIs<Map<*, *>>(body[0])
        assertEquals("root", op["objectId"])
        assertEquals(mapOf("key" to "greeting", "value" to mapOf("string" to "hello")), op["mapSet"])
    }

    /**
     * @UTS objects/unit/RSO3e/msgpack-body-1
     */
    @Test
    fun `RSO3e - request body encoded as MessagePack with the binary protocol`() = runTest {
        val channelName = "test-RSO3e-msgpack-" + randomId()
        val setup = setup(channelName, useBinaryProtocol = true)

        setup.channel.`object`.publish(HttpObjectOperation.counterInc(1).withPath("visits"))

        val request = setup.capturedRequests[0]
        assertEquals("POST", request.method)
        assertEquals("application/x-msgpack", request.header("Content-Type"))

        val body = msgpackDecode(request.body)
        assertIs<List<*>>(body)
        assertEquals(1, body.size)
        val op = assertIs<Map<*, *>>(body[0])
        assertEquals("visits", op["path"])
        assertEquals(mapOf("number" to 1L), op["counterInc"])
    }

    /**
     * @UTS objects/unit/RSO3c/batch-single-request-0
     */
    @Test
    fun `RSO3c - array of operations sent in a single request, in order`() = runTest {
        val setup = setup("test-RSO3c-" + randomId())

        setup.channel.`object`.publish(
            listOf(
                HttpObjectOperation.mapSet("x", PublishObjectData.number(1)).withPath("a"),
                HttpObjectOperation.mapRemove("old").withObjectId("root"),
                HttpObjectOperation.counterInc(-2).withPath("score"),
            ),
        )

        assertEquals(1, setup.capturedRequests.size)

        val body = setup.jsonBody()
        assertEquals(3, body.size)
        assertEquals("x", body[0].obj("mapSet")["key"])
        assertEquals(mapOf("key" to "old"), body[1]["mapRemove"])
        assertEquals(mapOf("number" to -2L), body[2]["counterInc"])
    }

    /**
     * @UTS objects/unit/RSO3h/no-batch-size-limit-0
     */
    @Test
    fun `RSO3h - no client-side limit on batch size`() = runTest {
        val setup = setup("test-RSO3h-" + randomId())
        val ops = (0..999).map { i ->
            HttpObjectOperation.mapSet("k$i", PublishObjectData.number(i)).withObjectId("root")
        }

        setup.channel.`object`.publish(ops)

        assertEquals(1, setup.capturedRequests.size)
        val body = setup.jsonBody()
        assertEquals(1000, body.size)
        assertEquals("k999", body[999].obj("mapSet")["key"])
    }

    /**
     * @UTS objects/unit/RSO10/publish-result-0
     */
    @Test
    fun `RSO3b, RSO10 - publish result decoded from the response`() = runTest {
        val channelName = "test-RSO10-" + randomId()
        val setup = setupRestObject(channelName) { req ->
            req.respondWith(
                201,
                mapOf(
                    "messageId" to "abc123:0",
                    "channel" to channelName,
                    "objectIds" to listOf("map:newHash@1700000000000", "root"),
                ),
            )
        }

        val result = setup.channel.`object`.publish(
            HttpObjectOperation.mapCreate(ObjectsMapSemantics.LWW, emptyMap()).withPath("profile"),
        )

        assertEquals("abc123:0", result.messageId)
        assertEquals(channelName, result.channel)
        assertEquals(listOf("map:newHash@1700000000000", "root"), result.objectIds)
    }

    /**
     * @UTS objects/unit/RSO3d4/op-wire-shape-no-action-0
     */
    @Test
    fun `RSO3d4, RSO5c-RSO5i - wire shape of each operation type, without an action field`() = runTest {
        val channelName = "test-RSO3d4-" + randomId()
        val testCases = listOf(
            HttpObjectOperation.mapCreate(ObjectsMapSemantics.LWW, emptyMap()).withPath("m") to
                mapOf("path" to "m", "mapCreate" to mapOf("semantics" to 0L, "entries" to emptyMap<String, Any?>())),
            HttpObjectOperation.mapCreateWithObjectId("map:h@1", "{\"semantics\":0,\"entries\":{}}", "nonce-0123456789ab") to
                mapOf(
                    "objectId" to "map:h@1",
                    "mapCreateWithObjectId" to mapOf(
                        "initialValue" to "{\"semantics\":0,\"entries\":{}}",
                        "nonce" to "nonce-0123456789ab",
                    ),
                ),
            HttpObjectOperation.mapSet("k", PublishObjectData.bool(true)).withObjectId("root") to
                mapOf("objectId" to "root", "mapSet" to mapOf("key" to "k", "value" to mapOf("boolean" to true))),
            HttpObjectOperation.mapRemove("k").withPath("a.b") to
                mapOf("path" to "a.b", "mapRemove" to mapOf("key" to "k")),
            HttpObjectOperation.counterCreate(10) to
                mapOf("counterCreate" to mapOf("count" to 10L)),
            HttpObjectOperation.counterCreateWithObjectId("counter:h@1", "{\"count\":10}", "nonce-0123456789ab") to
                mapOf(
                    "objectId" to "counter:h@1",
                    "counterCreateWithObjectId" to mapOf("initialValue" to "{\"count\":10}", "nonce" to "nonce-0123456789ab"),
                ),
            HttpObjectOperation.counterInc(-5).withObjectId("counter:h@1") to
                mapOf("objectId" to "counter:h@1", "counterInc" to mapOf("number" to -5L)),
        )

        for ((operation, expectedWireObject) in testCases) {
            val setup = setup(channelName)

            setup.channel.`object`.publish(operation)

            val wireOp = setup.jsonBody()[0]
            assertEquals(expectedWireObject, wireOp)
            assertFalse("action" in wireOp)
        }
    }

    /**
     * @UTS objects/unit/RSO3d3/targeting-fields-verbatim-0
     */
    @Test
    fun `RSO3d3 - id, objectId and path preserved verbatim`() = runTest {
        val setup = setup("test-RSO3d3-" + randomId())

        setup.channel.`object`.publish(
            listOf(
                HttpObjectOperation.mapRemove("k").withId("my-op-id:0").withObjectId("map:Ab-_9@1700000000000"),
                HttpObjectOperation.counterInc(1).withId("  spaced id ").withPath("a\\.b.*.c"),
            ),
        )

        val body = setup.jsonBody()

        assertEquals("my-op-id:0", body[0]["id"])
        assertEquals("map:Ab-_9@1700000000000", body[0]["objectId"])
        assertFalse("path" in body[0])

        assertEquals("  spaced id ", body[1]["id"])
        assertEquals("a\\.b.*.c", body[1]["path"])
        assertFalse("objectId" in body[1])
    }

    /**
     * @UTS objects/unit/RSO6/publish-path-verbatim-0
     */
    @Test
    fun `RSO6 - publish passes path to the server verbatim`() = runTest {
        val channelName = "test-RSO6-publish-" + randomId()
        val testCases = listOf("items.*", "a\\.b.c", ".a..b.", "", "ünïcödé.键")

        for (path in testCases) {
            val setup = setup(channelName)

            setup.channel.`object`.publish(
                HttpObjectOperation.mapSet("k", PublishObjectData.string("v")).withPath(path),
            )

            assertEquals(1, setup.capturedRequests.size)
            assertEquals(path, setup.jsonBody()[0]["path"])
        }
    }

    /**
     * @UTS objects/unit/RSO3d1/mapcreate-semantics-encoded-0
     */
    @Test
    fun `RSO3d1 - mapCreate semantics encoded per OMP2`() = runTest {
        val setup = setup("test-RSO3d1-" + randomId())

        setup.channel.`object`.publish(HttpObjectOperation.mapCreate(ObjectsMapSemantics.LWW, emptyMap()))

        assertEquals(0L, setup.jsonBody()[0].obj("mapCreate")["semantics"])
    }

    /**
     * @UTS objects/unit/RSO3d1/unrecognized-semantics-error-1
     */
    @Test
    fun `RSO3d1 - unrecognized mapCreate semantics rejected`() = runTest {
        val setup = setup("test-RSO3d1-invalid-" + randomId())

        // The closed enum's only unrecognized value is UNKNOWN (spec "not-a-semantics").
        val error = assertFailsWith<AblyException> {
            setup.channel.`object`.publish(
                HttpObjectOperation.mapCreate(ObjectsMapSemantics.UNKNOWN, emptyMap()),
            )
        }

        assertEquals(40003, error.errorInfo.code)
        assertEquals(400, error.errorInfo.statusCode)
        assertEquals(0, setup.capturedRequests.size)
    }

    /**
     * @UTS objects/unit/RSO3d2/mapset-value-encoded-json-0
     */
    @Test
    fun `RSO3d2, RSO7 - mapSet value encoded per OD4 (JSON protocol)`() = runTest {
        val channelName = "test-RSO3d2-json-" + randomId()
        val testCases = listOf(
            PublishObjectData.string("hello") to mapOf("string" to "hello"),
            PublishObjectData.number(1.5) to mapOf("number" to 1.5),
            PublishObjectData.bool(false) to mapOf("boolean" to false),
            PublishObjectData.bytes(byteArrayOf(0x01, 0x02, 0x03)) to mapOf("bytes" to "AQID"),
            PublishObjectData.json(JsonParser.parseString("{\"a\":[1,2]}").asJsonObject) to null,
            PublishObjectData.json(JsonParser.parseString("[1,\"x\"]").asJsonArray) to null,
            PublishObjectData.objectId("counter:h@1") to mapOf("objectId" to "counter:h@1"),
        )

        for ((value, expectedWireValue) in testCases) {
            val setup = setup(channelName)

            setup.channel.`object`.publish(HttpObjectOperation.mapSet("k", value).withObjectId("root"))

            val wireValue = setup.jsonBody()[0].obj("mapSet").obj("value")
            val json = value.json
            if (json != null) {
                assertEquals(setOf("json"), wireValue.keys)
                val string = assertIs<String>(wireValue["json"])
                assertEquals(fromJson(json), parseJson(string))
            } else {
                assertEquals<Any?>(expectedWireValue, wireValue)
            }
        }
    }

    /**
     * @UTS objects/unit/RSO3d2/mapset-value-encoded-msgpack-1
     */
    @Test
    fun `RSO3d2 - mapSet value encoded per OD4 (MessagePack protocol)`() = runTest {
        val setup = setup("test-RSO3d2-msgpack-" + randomId(), useBinaryProtocol = true)

        setup.channel.`object`.publish(
            listOf(
                HttpObjectOperation.mapSet("bin", PublishObjectData.bytes(byteArrayOf(0x01, 0x02, 0x03))).withObjectId("root"),
                HttpObjectOperation.mapSet("obj", PublishObjectData.json(JsonObject().apply { addProperty("a", 1) })).withObjectId("root"),
            ),
        )

        val body = assertIs<List<*>>(msgpackDecode(setup.capturedRequests[0].body))
        val value0 = assertIs<Map<*, *>>(assertIs<Map<*, *>>(assertIs<Map<*, *>>(body[0])["mapSet"])["value"])
        val value1 = assertIs<Map<*, *>>(assertIs<Map<*, *>>(assertIs<Map<*, *>>(body[1])["mapSet"])["value"])

        assertIs<ByteArray>(value0["bytes"])
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03), value0["bytes"] as ByteArray)

        assertEquals(setOf("json"), value1.keys)
        assertEquals(mapOf("a" to 1L), parseJson(assertIs<String>(value1["json"])))
    }

    /**
     * @UTS objects/unit/RSO3d2/mapcreate-entries-encoded-2
     */
    @Test
    fun `RSO3d2, RSO13 - mapCreate entries encoded per OD4`() = runTest {
        val setup = setup("test-RSO3d2-entries-" + randomId())

        setup.channel.`object`.publish(
            HttpObjectOperation.mapCreate(
                ObjectsMapSemantics.LWW,
                linkedMapOf(
                    "name" to PublishObjectData.string("Alice"),
                    "age" to PublishObjectData.number(30),
                    "active" to PublishObjectData.bool(true),
                    "avatar" to PublishObjectData.bytes(byteArrayOf(0x01, 0x02, 0x03)),
                    "prefs" to PublishObjectData.json(JsonObject().apply { addProperty("theme", "dark") }),
                    "visits" to PublishObjectData.objectId("counter:h@1"),
                ),
            ).withPath("profile"),
        )

        val entries = setup.jsonBody()[0].obj("mapCreate").obj("entries")

        assertEquals(mapOf("data" to mapOf("string" to "Alice")), entries["name"])
        assertEquals(mapOf("data" to mapOf("number" to 30L)), entries["age"])
        assertEquals(mapOf("data" to mapOf("boolean" to true)), entries["active"])
        assertEquals(mapOf("data" to mapOf("bytes" to "AQID")), entries["avatar"])
        val prefsData = entries.obj("prefs").obj("data")
        assertEquals(setOf("json"), prefsData.keys)
        assertEquals(mapOf("theme" to "dark"), parseJson(prefsData["json"] as String))
        assertEquals(mapOf("data" to mapOf("objectId" to "counter:h@1")), entries["visits"])
    }

    /**
     * @UTS objects/unit/RSO3f/no-auto-generated-id-0
     */
    @Test
    fun `RSO3f - no auto-generated operation id, even with idempotentRestPublishing`() = runTest {
        val setup = setup("test-RSO3f-" + randomId(), clientOptions = { idempotentRestPublishing = true })

        setup.channel.`object`.publish(
            listOf(
                HttpObjectOperation.counterInc(1).withPath("a"),
                HttpObjectOperation.counterInc(1).withId("caller-id").withPath("b"),
            ),
        )

        val body = setup.jsonBody()
        assertFalse("id" in body[0])
        assertEquals("caller-id", body[1]["id"])
    }

    /**
     * @UTS objects/unit/RSO5b1/no-body-field-error-0
     */
    @Test
    fun `RSO5b1 - operation with no body field rejected before any request`() = runTest {
        // Adapted (T-4): the factories always set a body, but a null passed to one from Java, which
        // doesn't enforce @NotNull, leaves none. Kotlin can't pass that null, so call through reflection.
        val setup = setup("test-RSO5b1-none-" + randomId())
        val noBody = HttpObjectOperation::class.java
            .getMethod("mapCreate", HttpObjectOperation.MapCreate::class.java)
            .invoke(null, *arrayOf<Any?>(null)) as HttpObjectOperation

        val error = assertFailsWith<AblyException> { setup.channel.`object`.publish(noBody.withObjectId("root")) }

        assertEquals(400, error.errorInfo.statusCode)
        assertEquals(40003, error.errorInfo.code)
        assertEquals(0, setup.capturedRequests.size)
    }

    /**
     * @UTS objects/unit/RSO5b1/multiple-body-fields-error-1
     */
    @Test
    fun `RSO5b1 - operation with multiple body fields rejected, no partial batch sent`() = runTest {
        // DEVIATION T-4 (not applicable, per the spec's own note): no HttpObjectOperation factory
        // or `with*` method sets a second body, so an operation with both mapSet and mapRemove is
        // unrepresentable. See deviations.md.
        val setup = setup("test-RSO5b1-multi-" + randomId())
        assertEquals(0, setup.capturedRequests.size)
    }

    /**
     * @UTS objects/unit/RSO3g/no-targeting-validation-0
     */
    @Test
    fun `RSO3g - no client-side validation of targeting, server error surfaced`() = runTest {
        val setup = setupRestObject("test-RSO3g-" + randomId()) { req ->
            req.respondWith(
                400,
                mapOf("error" to mapOf("code" to 40000, "statusCode" to 400, "message" to "Invalid operation targeting")),
            )
        }
        val testCases = listOf(
            HttpObjectOperation.mapSet("k", PublishObjectData.string("v")).withObjectId("root").withPath("a"),
            HttpObjectOperation.mapRemove("k"),
            HttpObjectOperation.counterCreate(1).withObjectId("counter:h@1"),
        )

        for (operation in testCases) {
            setup.capturedRequests.clear()

            val error = assertFailsWith<AblyException> { setup.channel.`object`.publish(operation) }

            assertEquals(1, setup.capturedRequests.size)
            assertEquals(40000, error.errorInfo.code)
            assertEquals(400, error.errorInfo.statusCode)
        }
    }
}
