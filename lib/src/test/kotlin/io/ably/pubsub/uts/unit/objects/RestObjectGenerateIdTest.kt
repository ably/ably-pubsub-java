package io.ably.pubsub.uts.unit.objects

import com.google.gson.JsonParser
import io.ably.pubsub.liveobjects.http.HttpObjectGenerateIdBody
import io.ably.pubsub.liveobjects.http.HttpObjectOperation
import io.ably.pubsub.liveobjects.http.PublishObjectData
import io.ably.pubsub.liveobjects.message.ObjectsMapSemantics
import io.ably.pubsub.types.AblyException
import kotlinx.coroutines.test.runTest
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `RestObject#generateObjectId` — ably-java's `HttpObject.generateObjectId`. Spec
 * `RestObjectGenerateIdBody(mapCreate: ...)` / `(counterCreate: ...)` are the
 * `HttpObjectGenerateIdBody.mapCreate` / `.counterCreate` factories.
 *
 * Source spec: `objects/unit/rest_object_generate_id.md`. Spec points: RSO4, RSO4a1-RSO4k, RSO5d,
 * RSO5h, RSO12.
 */
class RestObjectGenerateIdTest {

    private val serverTime = 1700000000000L

    /** The spec's `setup_rest_object(channel_name, use_binary_protocol)`. */
    private fun setup(channelName: String, useBinaryProtocol: Boolean = false) =
        setupRestObject(channelName, useBinaryProtocol) { req ->
            if (req.path == "/time") {
                req.respondWith(200, listOf(serverTime))
            } else {
                req.respondWith(201, mapOf("messageId" to "msg-id-1", "channel" to channelName, "objectIds" to emptyList<String>()))
            }
        }

    private fun parseInitialValue(initialValue: String): Map<*, *> = assertIs<Map<*, *>>(parseJson(initialValue))

    /**
     * @UTS objects/unit/RSO4d/map-type-objectid-0
     */
    @Test
    fun `RSO4d - map create body yields a map objectId`() = runTest {
        val setup = setup("test-RSO4d-map-" + randomId())

        val result = setup.channel.`object`.generateObjectId(
            HttpObjectGenerateIdBody.mapCreate(ObjectsMapSemantics.LWW, emptyMap()),
        )

        assertTrue(result.objectId.startsWith("map:"))
    }

    /**
     * @UTS objects/unit/RSO4d/counter-type-objectid-1
     */
    @Test
    fun `RSO4d - counter create body yields a counter objectId`() = runTest {
        val setup = setup("test-RSO4d-counter-" + randomId())

        val result = setup.channel.`object`.generateObjectId(HttpObjectGenerateIdBody.counterCreate(5))

        assertTrue(result.objectId.startsWith("counter:"))
    }

    /**
     * @UTS objects/unit/RSO4h/objectid-derivation-0
     */
    @Test
    fun `RSO4g, RSO4h - objectId derived per RTO14 from initialValue, nonce and server time`() = runTest {
        val setup = setup("test-RSO4h-" + randomId())

        val result = setup.channel.`object`.generateObjectId(HttpObjectGenerateIdBody.counterCreate(42))

        val timeRequests = setup.capturedRequests.filter { it.path == "/time" }
        assertEquals(1, timeRequests.size)
        assertEquals("GET", timeRequests[0].method)

        val expectedHash = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest((result.initialValue + ":" + result.nonce).toByteArray(Charsets.UTF_8)),
        )
        assertEquals("counter:$expectedHash@$serverTime", result.objectId)
    }

    /**
     * @UTS objects/unit/RSO4e2/counter-initial-value-0
     */
    @Test
    fun `RSO4e2 - counter initialValue is the JSON representation of the counter create body`() = runTest {
        val setup = setup("test-RSO4e2-" + randomId())

        val result = setup.channel.`object`.generateObjectId(HttpObjectGenerateIdBody.counterCreate(42))

        assertEquals(mapOf("count" to 42L), parseJson(result.initialValue))
    }

    /**
     * @UTS objects/unit/RSO4e1/map-initial-value-0
     */
    @Test
    fun `RSO4e1 - map initialValue encodes semantics and entry data with JSON protocol rules`() = runTest {
        val setup = setup("test-RSO4e1-" + randomId())

        val result = setup.channel.`object`.generateObjectId(
            HttpObjectGenerateIdBody.mapCreate(
                ObjectsMapSemantics.LWW,
                linkedMapOf(
                    "name" to PublishObjectData.string("Alice"),
                    "age" to PublishObjectData.number(30),
                    "active" to PublishObjectData.bool(true),
                    "avatar" to PublishObjectData.bytes(byteArrayOf(0x01, 0x02, 0x03)),
                    "prefs" to PublishObjectData.json(JsonParser.parseString("{\"theme\":\"dark\"}").asJsonObject),
                    "visits" to PublishObjectData.objectId("counter:h@1"),
                ),
            ),
        )

        val initialValue = parseInitialValue(result.initialValue)
        assertEquals(0L, initialValue["semantics"])

        val entries = assertIs<Map<*, *>>(initialValue["entries"])
        assertEquals(setOf("name", "age", "active", "avatar", "prefs", "visits"), entries.keys)
        assertEquals(mapOf("data" to mapOf("string" to "Alice")), entries["name"])
        assertEquals(mapOf("data" to mapOf("number" to 30L)), entries["age"])
        assertEquals(mapOf("data" to mapOf("boolean" to true)), entries["active"])
        assertEquals(mapOf("data" to mapOf("bytes" to "AQID")), entries["avatar"])
        val prefsData = assertIs<Map<*, *>>(assertIs<Map<*, *>>(entries["prefs"])["data"])
        assertEquals(setOf("json"), prefsData.keys)
        assertEquals(mapOf("theme" to "dark"), parseJson(prefsData["json"] as String))
        assertEquals(mapOf("data" to mapOf("objectId" to "counter:h@1")), entries["visits"])
    }

    /**
     * @UTS objects/unit/RSO4e/json-regardless-of-protocol-0
     */
    @Test
    fun `RSO4e - initialValue uses JSON encoding regardless of the client protocol`() = runTest {
        val createBody = HttpObjectGenerateIdBody.mapCreate(
            ObjectsMapSemantics.LWW,
            mapOf("avatar" to PublishObjectData.bytes(byteArrayOf(0x01, 0x02, 0x03))),
        )
        val jsonChannel = setup("test-RSO4e-json-" + randomId()).channel
        val binaryChannel = setup("test-RSO4e-binary-" + randomId(), useBinaryProtocol = true).channel

        val jsonResult = jsonChannel.`object`.generateObjectId(createBody)
        val binaryResult = binaryChannel.`object`.generateObjectId(createBody)

        assertIs<String>(binaryResult.initialValue)
        assertEquals(jsonResult.initialValue, binaryResult.initialValue)
        val avatarData = assertIs<Map<*, *>>(
            assertIs<Map<*, *>>(assertIs<Map<*, *>>(parseInitialValue(binaryResult.initialValue)["entries"])["avatar"])["data"],
        )
        assertEquals("AQID", avatarData["bytes"])
    }

    /**
     * @UTS objects/unit/RSO4e1a/unrecognized-semantics-error-0
     */
    @Test
    fun `RSO4e1a - unrecognized mapCreate semantics rejected`() = runTest {
        val setup = setup("test-RSO4e1a-" + randomId())

        // The closed enum's only unrecognized value is UNKNOWN (spec "not-a-semantics").
        val error = assertFailsWith<AblyException> {
            setup.channel.`object`.generateObjectId(
                HttpObjectGenerateIdBody.mapCreate(ObjectsMapSemantics.UNKNOWN, emptyMap()),
            )
        }

        assertEquals(40003, error.errorInfo.code)
        assertEquals(400, error.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RSO4c/missing-create-body-error-0
     */
    @Test
    fun `RSO4c - missing create body rejected`() = runTest {
        // Adapted (T-4): HttpObjectGenerateIdBody's factories always set a body, so an empty body is
        // unrepresentable; the reachable equivalent is a null body, which Java callers can pass.
        // Kotlin can't pass that null, so call through reflection. See deviations.md.
        val setup = setup("test-RSO4c-" + randomId())
        val generateObjectId = setup.channel.`object`.javaClass
            .getMethod("generateObjectId", HttpObjectGenerateIdBody::class.java)

        val error = assertFailsWith<java.lang.reflect.InvocationTargetException> {
            generateObjectId.invoke(setup.channel.`object`, *arrayOf<Any?>(null))
        }.targetException

        assertIs<AblyException>(error)
        assertEquals(400, error.errorInfo.statusCode)
        assertEquals(40003, error.errorInfo.code)
        assertEquals(0, setup.capturedRequests.size)
    }

    /**
     * @UTS objects/unit/RSO4f/nonce-generated-0
     */
    @Test
    fun `RSO4f - nonce is a string of at least 16 characters`() = runTest {
        val setup = setup("test-RSO4f-" + randomId())

        val result = setup.channel.`object`.generateObjectId(HttpObjectGenerateIdBody.counterCreate(0))

        assertTrue(result.nonce.length >= 16)
    }

    /**
     * @UTS objects/unit/RSO4j/unique-per-call-0
     */
    @Test
    fun `RSO4j - repeated calls produce different objectId and nonce`() = runTest {
        val setup = setup("test-RSO4j-" + randomId())
        val createBody = HttpObjectGenerateIdBody.counterCreate(1)

        val first = setup.channel.`object`.generateObjectId(createBody)
        val second = setup.channel.`object`.generateObjectId(createBody)

        assertNotEquals(first.nonce, second.nonce)
        assertNotEquals(first.objectId, second.objectId)
    }

    /**
     * @UTS objects/unit/RSO4k/initial-value-deterministic-0
     */
    @Test
    fun `RSO4k - initialValue is deterministic for a given create body`() = runTest {
        val setup = setup("test-RSO4k-" + randomId())
        val createBody = HttpObjectGenerateIdBody.mapCreate(
            ObjectsMapSemantics.LWW,
            linkedMapOf(
                "a" to PublishObjectData.string("x"),
                "b" to PublishObjectData.json(JsonParser.parseString("{\"k\":[1,2]}").asJsonObject),
            ),
        )

        val first = setup.channel.`object`.generateObjectId(createBody)
        val second = setup.channel.`object`.generateObjectId(createBody)

        assertEquals(first.initialValue, second.initialValue)
    }

    /**
     * @UTS objects/unit/RSO4b/generated-id-publish-roundtrip-0
     */
    @Test
    fun `RSO4b, RSO5d, RSO5h - generated values publish verbatim in WithObjectId operations`() = runTest {
        val channelName = "test-RSO4b-" + randomId()
        val setup = setup(channelName)

        val counter = setup.channel.`object`.generateObjectId(HttpObjectGenerateIdBody.counterCreate(3))
        val map = setup.channel.`object`.generateObjectId(
            HttpObjectGenerateIdBody.mapCreate(
                ObjectsMapSemantics.LWW,
                mapOf("visits" to PublishObjectData.objectId(counter.objectId)),
            ),
        )

        setup.channel.`object`.publish(
            listOf(
                HttpObjectOperation.counterCreateWithObjectId(counter.objectId, counter.initialValue, counter.nonce),
                HttpObjectOperation.mapCreateWithObjectId(map.objectId, map.initialValue, map.nonce),
                HttpObjectOperation.mapSet("stats", PublishObjectData.objectId(map.objectId)).withObjectId("root"),
            ),
        )

        val publishRequests = setup.capturedRequests.filter { it.method == "POST" }
        assertEquals(1, publishRequests.size)
        assertEquals("/channels/" + encodeUriComponent(channelName) + "/object", publishRequests[0].path)

        val body = assertIs<List<*>>(parseJson(publishRequests[0].body))
        assertEquals(
            mapOf(
                "objectId" to counter.objectId,
                "counterCreateWithObjectId" to mapOf("initialValue" to counter.initialValue, "nonce" to counter.nonce),
            ),
            body[0],
        )
        assertEquals(
            mapOf(
                "objectId" to map.objectId,
                "mapCreateWithObjectId" to mapOf("initialValue" to map.initialValue, "nonce" to map.nonce),
            ),
            body[1],
        )
        assertEquals(mapOf("objectId" to map.objectId), assertIs<Map<*, *>>(assertIs<Map<*, *>>(body[2])["mapSet"])["value"])
    }
}
