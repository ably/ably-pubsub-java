package io.ably.pubsub.push

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ably.pubsub.http.PubSubHttpClient
import io.ably.pubsub.types.AblyException
import io.ably.pubsub.types.Callback
import io.ably.pubsub.types.ErrorInfo
import io.ably.pubsub.uts.infra.unit.MockHttpClient
import io.ably.pubsub.uts.infra.unit.PendingRequest
import io.ably.pubsub.uts.infra.unit.TestHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

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
    fun `publish with a topic posts the payload with the topic and returns the message id`() {
        val payload = JsonObject().apply {
            add("data", JsonObject().apply { addProperty("homeScore", "2") })
            add("android", JsonObject().apply { addProperty("ttl", "120s") })
            addProperty("condition", "'ignored' in topics")
        }

        val messageId = client(body = mapOf("messageId" to "projects/p/messages/1"))
            .push.admin.fcmTopics.publish(FcmTopicTarget.topic("team-manutd"), payload)

        assertEquals("POST", request.method)
        assertEquals("/push/fcmTopics/messages", request.url.path)
        val expected = JsonObject().apply {
            add("data", JsonObject().apply { addProperty("homeScore", "2") })
            add("android", JsonObject().apply { addProperty("ttl", "120s") })
            addProperty("topic", "team-manutd")
        }
        assertEquals(expected, bodyJson())
        assertEquals("projects/p/messages/1", messageId)
        // the caller's payload is left as it was
        assertEquals("'ignored' in topics", payload.get("condition").asString)
        assertTrue(!payload.has("topic"))
    }

    @Test
    fun `publish with a condition posts the payload with the condition and returns the message id`() {
        val condition = "'team-manutd' in topics || 'team-liverpool' in topics"
        val payload = JsonObject().apply {
            add("data", JsonObject().apply { addProperty("homeScore", "2") })
            addProperty("topic", "ignored")
        }

        val messageId = client(body = mapOf("messageId" to "projects/p/messages/2"))
            .push.admin.fcmTopics.publish(FcmTopicTarget.condition(condition), payload)

        assertEquals("POST", request.method)
        assertEquals("/push/fcmTopics/messages", request.url.path)
        val expected = JsonObject().apply {
            add("data", JsonObject().apply { addProperty("homeScore", "2") })
            addProperty("condition", condition)
        }
        assertEquals(expected, bodyJson())
        assertEquals("projects/p/messages/2", messageId)
        // the caller's payload is left as it was
        assertEquals("ignored", payload.get("topic").asString)
        assertTrue(!payload.has("condition"))
    }

    @Test
    fun `publish returns null when the response has no message id`() {
        assertNull(client().push.admin.fcmTopics.publish(FcmTopicTarget.topic("team-manutd"), JsonObject().apply {
            add("data", JsonObject())
        }))
    }

    @Test
    fun `publish returns null when the response has no body`() {
        assertNull(client(status = 204, body = "").push.admin.fcmTopics.publish(FcmTopicTarget.topic("team-manutd"), JsonObject().apply {
            add("data", JsonObject())
        }))
    }

    @Test
    fun `list with an unexpected response shape raises AblyException without a fallback retry`() {
        val e = assertThrows(AblyException::class.java) {
            client(body = mapOf("items" to listOf("team-manutd"))).push.admin.fcmTopics.list("device-1")
        }
        assertEquals(50000, e.errorInfo.code)
        // the host answered, so the 500 must not send the request to a fallback host
        assertEquals(1, requests.size)
    }

    @Test
    fun `a malformed successful response reaches the async callback as an error`() {
        val result = CompletableFuture<ErrorInfo>()
        client(body = "{not json").push.admin.fcmTopics.publishAsync(
            FcmTopicTarget.topic("team-manutd"),
            JsonObject().apply { add("data", JsonObject()) },
            object : Callback<String> {
                override fun onSuccess(messageId: String?) {
                    result.completeExceptionally(AssertionError("expected an error, got $messageId"))
                }

                override fun onError(reason: ErrorInfo) {
                    result.complete(reason)
                }
            },
        )

        assertEquals(50000, result.get(5, TimeUnit.SECONDS).code)
        assertEquals(1, requests.size)
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
            { fcmTopics.publish(FcmTopicTarget.topic(""), JsonObject().apply { add("data", JsonObject()) }) },
            { fcmTopics.publish(FcmTopicTarget.condition(""), JsonObject().apply { add("data", JsonObject()) }) },
            { fcmTopics.publish(FcmTopicTarget.condition("'t' in topics"), JsonObject()) },
            { fcmTopics.publish(FcmTopicTarget.condition("'t' in topics"), null) },
            { fcmTopics.publish(null, JsonObject().apply { add("data", JsonObject()) }) },
            { fcmTopics.publish(FcmTopicTarget.condition("'t' in topics"), JsonObject().apply { addProperty("topic", "x") }) },
        )) {
            assertEquals(40000, assertThrows(AblyException::class.java) { call() }.errorInfo.code)
        }
        assertThrows(AblyException::class.java) { fcmTopics.get("t").subscribe("") }
        assertThrows(AblyException::class.java) { fcmTopics.get("t").subscribeChannels(arrayOf()) }
        assertThrows(AblyException::class.java) { fcmTopics.publish(FcmTopicTarget.topic("t"), JsonObject()) }
        assertThrows(AblyException::class.java) { fcmTopics.list(null) }
        assertTrue(requests.isEmpty())
    }
}
