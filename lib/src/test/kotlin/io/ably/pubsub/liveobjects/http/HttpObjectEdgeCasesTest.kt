package io.ably.pubsub.liveobjects.http

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.ably.pubsub.http.HttpUtils
import io.ably.pubsub.liveobjects.ValueType
import io.ably.pubsub.types.AblyException
import io.ably.pubsub.types.Callback
import io.ably.pubsub.types.ErrorInfo
import io.ably.pubsub.types.Param
import io.ably.pubsub.uts.unit.objects.path
import io.ably.pubsub.uts.unit.objects.queryParams
import io.ably.pubsub.uts.unit.objects.respondWithBody
import io.ably.pubsub.uts.unit.objects.setupRestObject
import org.junit.Test
import java.math.BigInteger
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

/**
 * SDK-specific behaviour of `HttpObject` that the UTS specs don't cover: the `...Async` callback
 * variants, error types, empty and malformed responses, input the encoder must reject, number
 * types, and the shared `/time` request. The spec-derived tests (blocking API) are in
 * `io.ably.pubsub.uts.unit.objects`.
 */
class HttpObjectEdgeCasesTest {

    /** The outcome of an `...Async` call. */
    private class Outcome<T>(val result: T?, val error: ErrorInfo?)

    /** Runs an `...Async` call and waits for its callback. */
    private fun <T> awaitCallback(call: (Callback<T>) -> Unit): Outcome<T> {
        val latch = CountDownLatch(1)
        var outcome: Outcome<T>? = null
        call(object : Callback<T> {
            override fun onSuccess(result: T) {
                outcome = Outcome(result, null)
                latch.countDown()
            }

            override fun onError(reason: ErrorInfo?) {
                outcome = Outcome(null, reason)
                latch.countDown()
            }
        })
        assertTrue(latch.await(5, TimeUnit.SECONDS), "callback not invoked")
        return outcome!!
    }

    private fun <T> Outcome<T>.errorInfo(): ErrorInfo = error ?: fail("expected an error, got $result")

    //region Async variants

    @Test
    fun `async variants deliver results through the callback`() {
        val setup = setupRestObject("edge-async") { req ->
            when {
                req.path == "/time" -> req.respondWith(200, listOf(1700000000000L))
                req.method == "POST" -> req.respondWithBody(201, mapOf("messageId" to "m", "channel" to "c", "objectIds" to listOf("root")), false)
                else -> req.respondWithBody(200, mapOf("objectId" to "root", "counter" to mapOf("data" to mapOf("number" to 3L))), false)
            }
        }
        val obj = setup.channel.`object`

        assertIs<HttpLiveCounter>(awaitCallback<HttpObjectGetFullResult> { obj.getAsync(it) }.result)
        assertIs<JsonObject>(awaitCallback<JsonElement> { obj.getCompactAsync(it) }.result)
        assertEquals("m", awaitCallback<HttpObjectPublishResult> { obj.publishAsync(HttpObjectOperation.counterInc(1).withPath("c"), it) }.result?.messageId)
        assertEquals(listOf("root"), awaitCallback<HttpObjectPublishResult> { obj.publishAsync(listOf(HttpObjectOperation.counterInc(1).withPath("c")), it) }.result?.objectIds)
        val generated = awaitCallback<HttpObjectGenerateIdResult> { obj.generateObjectIdAsync(HttpObjectGenerateIdBody.counterCreate(1), it) }.result
        assertTrue(generated!!.objectId.endsWith("@1700000000000"))
    }

    @Test
    fun `async variants deliver local validation and server errors through the callback`() {
        val setup = setupRestObject("edge-async-errors") { req ->
            req.respondWith(404, mapOf("error" to mapOf("code" to 40400, "statusCode" to 404, "message" to "nope")))
        }
        val obj = setup.channel.`object`

        assertEquals(40400, awaitCallback<JsonElement> { obj.getCompactAsync(it) }.errorInfo().code)
        assertEquals(1, setup.capturedRequests.size)

        val invalid = HttpObjectOperation.counterInc(Double.NaN).withPath("c")
        assertEquals(40003, awaitCallback<HttpObjectPublishResult> { obj.publishAsync(invalid, it) }.errorInfo().code)
        assertEquals(40003, awaitCallback<HttpObjectGenerateIdResult> { obj.generateObjectIdAsync(HttpObjectGenerateIdBody.counterCreate(Double.NaN), it) }.errorInfo().code)
        assertEquals(1, setup.capturedRequests.size) // nothing sent for invalid input
    }

    @Test
    fun `async callbacks run on a background thread, including for invalid input and a known server time`() {
        val setup = setupRestObject("edge-async-thread") { req ->
            if (req.path == "/time") req.respondWith(200, listOf(1700000000000L))
        }
        val obj = setup.channel.`object`
        obj.generateObjectId(HttpObjectGenerateIdBody.counterCreate(1)) // server time is now known
        val testThread = Thread.currentThread()

        fun <T> callbackThread(call: (Callback<T>) -> Unit): Thread {
            val latch = CountDownLatch(1)
            var thread: Thread? = null
            call(object : Callback<T> {
                override fun onSuccess(result: T) { thread = Thread.currentThread(); latch.countDown() }
                override fun onError(reason: ErrorInfo?) { thread = Thread.currentThread(); latch.countDown() }
            })
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            return thread!!
        }

        assertTrue(callbackThread<HttpObjectGenerateIdResult> { obj.generateObjectIdAsync(HttpObjectGenerateIdBody.counterCreate(1), it) } != testThread)
        assertTrue(callbackThread<HttpObjectGenerateIdResult> { obj.generateObjectIdAsync(HttpObjectGenerateIdBody.counterCreate(Double.NaN), it) } != testThread)
        assertTrue(callbackThread<HttpObjectPublishResult> { obj.publishAsync(HttpObjectOperation.counterInc(Double.NaN), it) } != testThread)
    }

    @Test
    fun `an async callback runs on a background thread while a blocking call is fetching the time`() {
        val timeRequested = CountDownLatch(1)
        val asyncRegistered = CountDownLatch(1)
        val setup = setupRestObject("edge-async-thread-waiter") { req ->
            if (req.path == "/time") {
                timeRequested.countDown()
                asyncRegistered.await(5, TimeUnit.SECONDS) // hold the blocking fetch until the async call waits on it
                req.respondWith(200, listOf(1700000000000L))
            }
        }
        val obj = setup.channel.`object`
        val body = HttpObjectGenerateIdBody.counterCreate(1)

        var blockingThread: Thread? = null
        val blocking = Thread { obj.generateObjectId(body) }.apply { blockingThread = this; start() }
        assertTrue(timeRequested.await(5, TimeUnit.SECONDS))

        val latch = CountDownLatch(1)
        var callbackThread: Thread? = null
        obj.generateObjectIdAsync(body, object : Callback<HttpObjectGenerateIdResult> {
            override fun onSuccess(result: HttpObjectGenerateIdResult) { callbackThread = Thread.currentThread(); latch.countDown() }
            override fun onError(reason: ErrorInfo?) { callbackThread = Thread.currentThread(); latch.countDown() }
        })
        // Let the async call register as a waiter on the blocking fetch. If it hasn't by then, the
        // time is already known when it runs and the test passes vacuously; it can't fail spuriously.
        Thread.sleep(200)
        asyncRegistered.countDown()

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        blocking.join(5000)
        assertTrue(callbackThread != blockingThread, "the async callback ran on the blocking caller's thread")
        assertTrue(callbackThread != Thread.currentThread())
        assertEquals(1, setup.capturedRequests.count { it.path == "/time" })
    }

    //endregion

    //region Get params

    @Test
    fun `get params - objectId becomes the path segment, other params go in the query, compact is the method's`() {
        val setup = setupRestObject("edge-params") { it.respondWithBody(200, mapOf("objectId" to "root"), false) }
        val obj = setup.channel.`object`
        val params = arrayOf(
            Param(HttpObjectGetParams.OBJECT_ID, "map:abc@1"),
            Param(HttpObjectGetParams.PATH, "a.b"),
            Param("compact", "true"),
            Param("extra", "1"),
        )

        obj.get(params)
        obj.getCompact(params)

        val (full, compact) = setup.capturedRequests
        assertEquals("/channels/edge-params/object/" + HttpUtils.encodeURIComponent("map:abc@1"), full.path)
        assertEquals(mapOf("path" to "a.b", "extra" to "1", "compact" to "false"), full.queryParams)
        assertEquals("/channels/edge-params/object/" + HttpUtils.encodeURIComponent("map:abc@1"), compact.path)
        assertEquals(mapOf("path" to "a.b", "extra" to "1"), compact.queryParams)
    }

    @Test
    fun `get params - factories build the objectId and path params`() {
        assertEquals(listOf(Param("objectId", "map:abc@1")), HttpObjectGetParams.ofObjectId("map:abc@1").toList())
        assertEquals(listOf(Param("path", "a.b")), HttpObjectGetParams.ofPath("a.b").toList())
        assertEquals(
            listOf(Param("objectId", "map:abc@1"), Param("path", "a.b")),
            HttpObjectGetParams.of("map:abc@1", "a.b").toList(),
        )
    }

    //endregion

    //region Fallback hosts

    @Test
    fun `publish is not retried on a fallback host after a server error`() {
        val serverError = mapOf("error" to mapOf("code" to 50000, "statusCode" to 500, "message" to "boom"))
        val setup = setupRestObject("edge-no-fallback") { it.respondWith(500, serverError) }

        // Control: a GET to the same failing mock is retried on fallback hosts.
        assertFailsWith<AblyException> { setup.channel.`object`.getCompact() }
        val getRequests = setup.capturedRequests.size
        assertTrue(getRequests > 1, "expected the GET to be retried on fallback hosts, saw $getRequests request(s)")

        setup.capturedRequests.clear()
        val error = assertFailsWith<AblyException> { setup.channel.`object`.publish(HttpObjectOperation.counterInc(5).withPath("c")) }
        assertEquals(500, error.errorInfo.statusCode)
        assertEquals(1, setup.capturedRequests.size)

        setup.capturedRequests.clear()
        val asyncError = awaitCallback<HttpObjectPublishResult> { setup.channel.`object`.publishAsync(HttpObjectOperation.counterInc(5).withPath("c"), it) }
        assertEquals(500, asyncError.errorInfo().statusCode)
        assertEquals(1, setup.capturedRequests.size)
    }

    //endregion

    //region Errors and decoding

    @Test
    fun `null values are rejected with 40003, also through the async callback`() {
        val setup = setupRestObject("edge-null-values") { req ->
            if (req.path == "/time") req.respondWith(200, listOf(1700000000000L)) else req.respondWithBody(201, emptyMap<String, Any?>(), false)
        }
        @Suppress("UNCHECKED_CAST")
        val entries = mapOf<String, PublishObjectData?>("a" to null) as Map<String, PublishObjectData>
        val op = HttpObjectOperation.mapCreate(entries)
        val body = HttpObjectGenerateIdBody.mapCreate(entries)

        assertEquals(40003, assertFailsWith<AblyException> { setup.channel.`object`.publish(op) }.errorInfo.code)
        assertEquals(40003, awaitCallback<HttpObjectPublishResult> { setup.channel.`object`.publishAsync(op, it) }.errorInfo().code)
        assertEquals(40003, assertFailsWith<AblyException> { setup.channel.`object`.generateObjectId(body) }.errorInfo.code)
        assertEquals(40003, awaitCallback<HttpObjectGenerateIdResult> { setup.channel.`object`.generateObjectIdAsync(body, it) }.errorInfo().code)
        assertEquals(0, setup.capturedRequests.count { it.method == "POST" })
    }

    @Test
    fun `counter without a numeric value has a null count`() {
        val setup = setupRestObject("edge-counter-no-value") {
            it.respondWithBody(200, mapOf("objectId" to "counter:x@1", "counter" to mapOf("count" to 3L)), false)
        }

        val counter = assertIs<HttpLiveCounter>(setup.channel.`object`.get())

        assertNull(counter.count)
    }


    @Test
    fun `malformed leaf in a full response fails without a fallback retry`() {
        val cases = listOf(
            mapOf("json" to "{not json"),
            mapOf("bytes" to "not*base64!"),
        )
        for (leaf in cases) {
            val setup = setupRestObject("edge-malformed") { it.respondWithBody(200, leaf, false) }
            val params = HttpObjectGetParams.ofPath("leaf")

            val error = assertFailsWith<AblyException> { setup.channel.`object`.get(params) }
            assertEquals(50000, error.errorInfo.code)
            assertEquals(500, error.errorInfo.statusCode)
            assertEquals(50000, awaitCallback<HttpObjectGetFullResult> { setup.channel.`object`.getAsync(params, it) }.errorInfo().code)
            // one request per call: a decode error is not taken for a host failure and retried
            assertEquals(2, setup.capturedRequests.size)
        }
    }

    @Test
    fun `unexpected publish response fails without a fallback retry`() {
        val setup = setupRestObject("edge-publish-response") { it.respondWithBody(201, listOf("not", "a", "map"), false) }

        val error = assertFailsWith<AblyException> { setup.channel.`object`.publish(HttpObjectOperation.counterInc(1).withPath("c")) }

        assertEquals(50000, error.errorInfo.code)
        assertEquals(1, setup.capturedRequests.size)
    }

    @Test
    fun `full get with no response body is null, like compact`() {
        val setup = setupRestObject("edge-empty-body") { it.respondWith(200, ByteArray(0)) }

        assertNull(setup.channel.`object`.get())
        assertNull(setup.channel.`object`.getCompact())
    }

    @Test
    fun `publish with a null operation fails with 40003 and sends nothing`() {
        val setup = setupRestObject("edge-null-op") { it.respondWithBody(201, emptyMap<String, Any?>(), false) }

        val error = assertFailsWith<AblyException> { setup.channel.`object`.publish(listOf(HttpObjectOperation.counterInc(1), null)) }

        assertEquals(40003, error.errorInfo.code)
        assertEquals(0, setup.capturedRequests.size)
    }

    @Test
    fun `non-finite numbers are rejected with 40003 before any request`() {
        val setup = setupRestObject("edge-non-finite") { it.respondWithBody(201, emptyMap<String, Any?>(), false) }
        val operations = listOf(
            HttpObjectOperation.mapSet("k", PublishObjectData.number(Double.NaN)).withObjectId("root"),
            HttpObjectOperation.counterInc(Double.POSITIVE_INFINITY).withPath("c"),
            HttpObjectOperation.counterCreate(Double.NEGATIVE_INFINITY),
        )
        for (operation in operations) {
            assertEquals(40003, assertFailsWith<AblyException> { setup.channel.`object`.publish(operation) }.errorInfo.code)
        }
        val nanBody = HttpObjectGenerateIdBody.counterCreate(Double.NaN)
        assertEquals(40003, assertFailsWith<AblyException> { setup.channel.`object`.generateObjectId(nanBody) }.errorInfo.code)
        assertEquals(0, setup.capturedRequests.size)
    }

    @Test
    fun `integers beyond the long range keep their exact value for both protocols`() {
        val big = BigInteger.ONE.shiftLeft(63) // just beyond Long.MAX_VALUE; still fits msgpack uint64
        for (binary in listOf(false, true)) {
            val setup = setupRestObject("edge-big-int", useBinaryProtocol = binary) {
                it.respondWithBody(200, mapOf("big" to big, "small" to 7L), binary)
            }

            val result = assertIs<JsonObject>(setup.channel.`object`.getCompact())

            assertEquals(big, result["big"].asBigInteger, "useBinaryProtocol=$binary")
            assertEquals(7L, result["small"].asLong, "useBinaryProtocol=$binary")
        }
    }

    //endregion

    //region Type casts

    @Test
    fun `getType and as casts narrow full results`() {
        val body = mapOf(
            "objectId" to "root",
            "map" to mapOf(
                "semantics" to 0L,
                "entries" to mapOf(
                    "str" to mapOf("data" to mapOf("string" to "hello")),
                    "num" to mapOf("data" to mapOf("number" to 2.5)),
                    "bool" to mapOf("data" to mapOf("boolean" to true)),
                    "bin" to mapOf("data" to mapOf("bytes" to "AQID")),
                    "jobj" to mapOf("data" to mapOf("json" to "{\"a\":1}")),
                    "jarr" to mapOf("data" to mapOf("json" to "[1,2]")),
                    "jnum" to mapOf("data" to mapOf("json" to "5")),
                    "visits" to mapOf("data" to mapOf("objectId" to "counter:v@1", "counter" to mapOf("data" to mapOf("number" to 7L)))),
                    "future" to mapOf("data" to mapOf("objectId" to "future:f@1", "futureType" to emptyMap<String, Any?>())),
                ),
            ),
        )
        val setup = setupRestObject("edge-casts") { it.respondWithBody(200, body, false) }

        val root = assertNotNull(setup.channel.`object`.get())
        assertEquals(ValueType.LIVE_MAP, root.type)
        val map = root.asLiveMap()
        assertEquals("root", root.asLiveObject().objectId)
        fun entry(key: String) = map.entries.getValue(key).data

        assertEquals(ValueType.STRING, entry("str").type)
        assertEquals("hello", entry("str").asString())
        assertEquals("hello", entry("str").asObjectData().string)
        assertEquals(ValueType.NUMBER, entry("num").type)
        assertEquals(2.5, entry("num").asNumber())
        assertEquals(ValueType.BOOLEAN, entry("bool").type)
        assertTrue(entry("bool").asBoolean())
        assertEquals(ValueType.BINARY, entry("bin").type)
        assertEquals(listOf<Byte>(1, 2, 3), entry("bin").asBinary().toList())
        assertEquals(ValueType.JSON_OBJECT, entry("jobj").type)
        assertEquals(1, entry("jobj").asJsonObject()["a"].asInt)
        assertEquals(ValueType.JSON_ARRAY, entry("jarr").type)
        assertEquals(2, entry("jarr").asJsonArray().size())
        assertEquals(ValueType.UNKNOWN, entry("jnum").type)
        assertEquals(5, entry("jnum").asObjectData().json!!.asInt)
        assertEquals(ValueType.LIVE_COUNTER, entry("visits").type)
        assertEquals(7.0, entry("visits").asLiveCounter().count)
        assertEquals(ValueType.UNKNOWN, entry("future").type)
        assertEquals("future:f@1", entry("future").asLiveObject().objectId)
    }

    @Test
    fun `as casts fail fast on a type mismatch`() {
        val body = mapOf("objectId" to "counter:c@1", "counter" to mapOf("data" to mapOf("number" to 1L)))
        val setup = setupRestObject("edge-cast-mismatch") { it.respondWithBody(200, body, false) }
        val counter = assertNotNull(setup.channel.`object`.get())

        val error = assertFailsWith<IllegalStateException> { counter.asLiveMap() }
        assertEquals("Expected LIVE_MAP but the value is LIVE_COUNTER", error.message)
        assertFailsWith<IllegalStateException> { counter.asString() }
        assertFailsWith<IllegalStateException> { counter.asObjectData() }

        val leafSetup = setupRestObject("edge-cast-leaf") { it.respondWithBody(200, mapOf("string" to "x"), false) }
        val leaf = assertNotNull(leafSetup.channel.`object`.get())
        assertFailsWith<IllegalStateException> { leaf.asNumber() }
        assertFailsWith<IllegalStateException> { leaf.asLiveObject() }
        assertFailsWith<IllegalStateException> { leaf.asJsonObject() }
    }

    //endregion

    //region Server time

    @Test
    fun `concurrent first generateObjectIdAsync calls share one time request`() {
        val setup = setupRestObject("edge-time-shared") { req ->
            if (req.path == "/time") req.respondWithDelay(300.milliseconds, 200, listOf(1700000000000L))
        }
        val body = HttpObjectGenerateIdBody.counterCreate(1)
        val latch = CountDownLatch(5)
        val ids = Collections.synchronizedList(mutableListOf<String>())
        repeat(5) {
            setup.channel.`object`.generateObjectIdAsync(body, object : Callback<HttpObjectGenerateIdResult> {
                override fun onSuccess(result: HttpObjectGenerateIdResult) {
                    ids.add(result.objectId)
                    latch.countDown()
                }

                override fun onError(reason: ErrorInfo?) = fail("unexpected error $reason")
            })
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(5, ids.toSet().size)
        assertEquals(1, setup.capturedRequests.count { it.path == "/time" })
    }

    @Test
    fun `concurrent first blocking generateObjectId calls share one time request`() {
        val setup = setupRestObject("edge-time-shared-sync") { req ->
            if (req.path == "/time") req.respondWithDelay(300.milliseconds, 200, listOf(1700000000000L))
        }
        val body = HttpObjectGenerateIdBody.counterCreate(1)

        val threads = (1..5).map { Thread { setup.channel.`object`.generateObjectId(body) }.apply { start() } }
        threads.forEach { it.join(5000) }

        assertEquals(1, setup.capturedRequests.count { it.path == "/time" })
    }

    @Test
    fun `a throwing callback doesn't stop other waiters from being notified`() {
        val setup = setupRestObject("edge-time-throwing-waiter") { req ->
            if (req.path == "/time") req.respondWithDelay(300.milliseconds, 200, listOf(1700000000000L))
        }
        val body = HttpObjectGenerateIdBody.counterCreate(1)
        val second = CountDownLatch(1)

        setup.channel.`object`.generateObjectIdAsync(body, object : Callback<HttpObjectGenerateIdResult> {
            override fun onSuccess(result: HttpObjectGenerateIdResult) = throw IllegalStateException("caller bug")
            override fun onError(reason: ErrorInfo?) = Unit
        })
        setup.channel.`object`.generateObjectIdAsync(body, object : Callback<HttpObjectGenerateIdResult> {
            override fun onSuccess(result: HttpObjectGenerateIdResult) = second.countDown()
            override fun onError(reason: ErrorInfo?) = Unit
        })

        assertTrue(second.await(5, TimeUnit.SECONDS), "second waiter was not notified")
        assertEquals(1, setup.capturedRequests.count { it.path == "/time" })
    }

    @Test
    fun `a blocking and an async first call share one time request`() {
        val setup = setupRestObject("edge-time-mixed") { req ->
            if (req.path == "/time") req.respondWithDelay(300.milliseconds, 200, listOf(1700000000000L))
        }
        val body = HttpObjectGenerateIdBody.counterCreate(1)

        val blocking = Thread { setup.channel.`object`.generateObjectId(body) }.apply { start() }
        Thread.sleep(100) // let the blocking call start its request
        val async = awaitCallback<HttpObjectGenerateIdResult> { setup.channel.`object`.generateObjectIdAsync(body, it) }
        blocking.join(5000)

        assertNotNull(async.result)
        assertEquals(1, setup.capturedRequests.count { it.path == "/time" })
    }

    @Test
    fun `a failed time request fails generateObjectId and the next call retries`() {
        var timeRequests = 0
        val setup = setupRestObject("edge-time-retry") { req ->
            if (req.path == "/time") {
                timeRequests++
                if (timeRequests % 2 == 1) {
                    req.respondWith(401, mapOf("error" to mapOf("code" to 40100, "statusCode" to 401, "message" to "nope")))
                } else {
                    req.respondWith(200, listOf(1700000000000L))
                }
            }
        }
        val body = HttpObjectGenerateIdBody.counterCreate(1)

        // async: first call fails, second fetches again and succeeds
        assertEquals(401, awaitCallback<HttpObjectGenerateIdResult> { setup.channel.`object`.generateObjectIdAsync(body, it) }.errorInfo().statusCode)
        assertTrue(awaitCallback<HttpObjectGenerateIdResult> { setup.channel.`object`.generateObjectIdAsync(body, it) }.result!!.objectId.endsWith("@1700000000000"))
        assertEquals(2, timeRequests)
    }

    @Test
    fun `a failed blocking time request throws and the next call retries`() {
        var timeRequests = 0
        val setup = setupRestObject("edge-time-retry-sync") { req ->
            if (req.path == "/time") {
                timeRequests++
                if (timeRequests == 1) {
                    req.respondWith(401, mapOf("error" to mapOf("code" to 40100, "statusCode" to 401, "message" to "nope")))
                } else {
                    req.respondWith(200, listOf(1700000000000L))
                }
            }
        }
        val body = HttpObjectGenerateIdBody.counterCreate(1)

        assertEquals(401, assertFailsWith<AblyException> { setup.channel.`object`.generateObjectId(body) }.errorInfo.statusCode)
        assertTrue(setup.channel.`object`.generateObjectId(body).objectId.endsWith("@1700000000000"))
        assertEquals(2, timeRequests)
    }

    @Test
    fun `a malformed time response fails waiting generateObjectIdAsync calls and the next call retries`() {
        // 200 responses whose body can't be read as [time] make the /time response handler throw
        val malformed = ArrayDeque(listOf("[]", "[null]", "<html>captive portal</html>", ""))
        val setup = setupRestObject("edge-time-malformed") { req ->
            if (req.path == "/time") {
                val body = synchronized(malformed) { malformed.removeFirstOrNull() }
                if (body != null) req.respondWithDelay(100.milliseconds, 200, body) else req.respondWith(200, listOf(1700000000000L))
            }
        }
        val body = HttpObjectGenerateIdBody.counterCreate(1)

        repeat(4) {
            // two concurrent callers share each failing request; both must be called back
            val first = CountDownLatch(1)
            val errors = Collections.synchronizedList(mutableListOf<ErrorInfo?>())
            val callback = object : Callback<HttpObjectGenerateIdResult> {
                override fun onSuccess(result: HttpObjectGenerateIdResult) = fail("unexpected result $result")
                override fun onError(reason: ErrorInfo?) { errors.add(reason); first.countDown() }
            }
            setup.channel.`object`.generateObjectIdAsync(body, callback)
            setup.channel.`object`.generateObjectIdAsync(body, callback)
            assertTrue(first.await(5, TimeUnit.SECONDS), "waiting callers were not released")
            val deadline = System.currentTimeMillis() + 5000
            while (errors.size < 2 && System.currentTimeMillis() < deadline) Thread.sleep(10)
            assertEquals(2, errors.size)
            assertTrue(errors.all { it != null })
        }

        assertTrue(awaitCallback<HttpObjectGenerateIdResult> { setup.channel.`object`.generateObjectIdAsync(body, it) }.result!!.objectId.endsWith("@1700000000000"))
    }

    @Test
    fun `a malformed time response fails a blocking generateObjectId and the next call retries`() {
        var timeRequests = 0
        val setup = setupRestObject("edge-time-malformed-sync") { req ->
            if (req.path == "/time") {
                timeRequests++
                if (timeRequests == 1) req.respondWith(200, "[]") else req.respondWith(200, listOf(1700000000000L))
            }
        }
        val body = HttpObjectGenerateIdBody.counterCreate(1)

        assertFails { setup.channel.`object`.generateObjectId(body) }
        assertTrue(setup.channel.`object`.generateObjectId(body).objectId.endsWith("@1700000000000"))
        assertEquals(2, timeRequests)
    }

    //endregion
}
