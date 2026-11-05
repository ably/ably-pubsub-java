package io.ably.pubsub.push

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ably.pubsub.http.PubSubHttpClient
import io.ably.pubsub.types.AblyException
import io.ably.pubsub.uts.infra.unit.MockHttpClient
import io.ably.pubsub.uts.infra.unit.PendingRequest
import io.ably.pubsub.uts.infra.unit.TestHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Checks the requests the push-admin FCM topics API sends, against a mock HTTP engine.
 */
class AdminFcmTopicsTest {

    private val requests = CopyOnWriteArrayList<PendingRequest>()

    /** A client whose every request is recorded and answered with [status] and [body]. */
    private fun client(status: Int = 200, body: Any = emptyMap<String, Any>()): PubSubHttpClient {
        val mock = MockHttpClient {
            onConnectionAttempt = { it.respondWithSuccess() }
            onRequest = { request ->
                requests.add(request)
                request.respondWith(status, body)
            }
        }
        return TestHttpClient { install(mock) }
    }

    private val request get() = requests.single()

    private fun bodyJson(): JsonObject = JsonParser.parseString(String(request.body)).asJsonObject

    @Test
    fun `subscribe posts to the device subscription path`() {
        client(body = mapOf("topic" to "team-manutd", "deviceId" to "device-1"))
            .push.admin.fcmTopics.get("team-manutd").subscribe("device-1")

        assertEquals("POST", request.method)
        assertEquals("/push/fcmTopics/team-manutd/subscriptions/device-1", request.url.path)
    }

    @Test
    fun `unsubscribe deletes the device subscription path`() {
        client(status = 204, body = "").push.admin.fcmTopics.get("team-manutd").unsubscribe("device-1")

        assertEquals("DELETE", request.method)
        assertEquals("/push/fcmTopics/team-manutd/subscriptions/device-1", request.url.path)
    }

    @Test
    fun `topic and device id are percent-encoded in the path`() {
        // FCM topic names may contain %, which the server requires sent as %25.
        client().push.admin.fcmTopics.get("50%off").subscribe("device/1")

        // java.net.URL.path is the raw, still-encoded path.
        assertEquals("/push/fcmTopics/50%25off/subscriptions/device%2F1", request.url.path)
    }

    @Test
    fun `subscribeClient posts the clientId to the topic subscriptions`() {
        client(status = 202, body = mapOf("topic" to "team-manutd"))
            .push.admin.fcmTopics.get("team-manutd").subscribeClient("client-1")

        assertEquals("POST", request.method)
        assertEquals("/push/fcmTopics/team-manutd/subscriptions", request.url.path)
        assertEquals("client-1", bodyJson().get("clientId").asString)
        assertTrue(!bodyJson().has("channels"))
    }

    @Test
    fun `subscribeChannels posts the channels to the topic subscriptions`() {
        client(status = 202, body = mapOf("topic" to "team-manutd"))
            .push.admin.fcmTopics.get("team-manutd").subscribeChannels(arrayOf("a", "b"))

        assertEquals("/push/fcmTopics/team-manutd/subscriptions", request.url.path)
        val channels = bodyJson().getAsJsonArray("channels").map { it.asString }
        assertEquals(listOf("a", "b"), channels)
    }

    @Test
    fun `unsubscribeClient deletes the topic subscriptions with a clientId param`() {
        client(status = 202, body = mapOf("topic" to "team-manutd"))
            .push.admin.fcmTopics.get("team-manutd").unsubscribeClient("client-1")

        assertEquals("DELETE", request.method)
        assertEquals("/push/fcmTopics/team-manutd/subscriptions", request.url.path)
        assertTrue(request.url.query.split("&").contains("clientId=client-1"))
    }

    @Test
    fun `list gets the device's topics`() {
        val topics = client(body = listOf("team-manutd", "alerts-goals")).push.admin.fcmTopics.list("device-1")

        assertEquals("GET", request.method)
        assertEquals("/push/fcmTopics", request.url.path)
        assertTrue(request.url.query.split("&").contains("deviceId=device-1"))
        assertArrayEquals(arrayOf("team-manutd", "alerts-goals"), topics)
    }

    @Test
    fun `publish posts the payload to the topic messages and returns the message id`() {
        val payload = JsonObject().apply {
            add("data", JsonObject().apply { addProperty("homeScore", "2") })
            add("android", JsonObject().apply { addProperty("ttl", "120s") })
        }

        val messageId = client(body = mapOf("topic" to "team-manutd", "messageId" to "projects/p/messages/1"))
            .push.admin.fcmTopics.publish("team-manutd", payload)

        assertEquals("POST", request.method)
        assertEquals("/push/fcmTopics/team-manutd/messages", request.url.path)
        assertEquals(payload, bodyJson())
        assertEquals("projects/p/messages/1", messageId)
    }

    @Test
    fun `publish returns null when the response has no message id`() {
        assertNull(client().push.admin.fcmTopics.get("team-manutd").publish(JsonObject().apply {
            add("data", JsonObject())
        }))
    }

    @Test
    fun `publish returns null when the response has no body`() {
        assertNull(client(status = 204, body = "").push.admin.fcmTopics.publish("team-manutd", JsonObject().apply {
            add("data", JsonObject())
        }))
    }

    @Test
    fun `list with an unexpected response shape raises AblyException`() {
        val e = assertThrows(AblyException::class.java) {
            client(body = mapOf("items" to listOf("team-manutd"))).push.admin.fcmTopics.list("device-1")
        }
        assertEquals(50000, e.errorInfo.code)
    }

    @Test
    fun `list returns an empty array when the response has no body`() {
        assertArrayEquals(arrayOf<String>(), client(status = 204, body = "").push.admin.fcmTopics.list("device-1"))
    }

    @Test
    fun `server errors are raised as AblyException`() {
        val error = mapOf("error" to mapOf("code" to 40183, "statusCode" to 400, "message" to "needs a service account"))
        val e = assertThrows(AblyException::class.java) {
            client(status = 400, body = error).push.admin.fcmTopics.get("team-manutd").subscribe("device-1")
        }
        assertEquals(40183, e.errorInfo.code)
    }

    @Test
    fun `empty arguments fail without a request`() {
        val fcmTopics = client().push.admin.fcmTopics
        for (call in listOf<() -> Unit>(
            { fcmTopics.get("").subscribe("device-1") },
            { fcmTopics.get(null).unsubscribeClient("client-1") },
            { fcmTopics.publish("", JsonObject().apply { add("data", JsonObject()) }) },
        )) {
            assertEquals(40000, assertThrows(AblyException::class.java) { call() }.errorInfo.code)
        }
        assertThrows(AblyException::class.java) { fcmTopics.get("t").subscribe("") }
        assertThrows(AblyException::class.java) { fcmTopics.get("t").subscribeChannels(arrayOf()) }
        assertThrows(AblyException::class.java) { fcmTopics.publish("t", JsonObject()) }
        assertThrows(AblyException::class.java) { fcmTopics.list(null) }
        assertTrue(requests.isEmpty())
    }
}
