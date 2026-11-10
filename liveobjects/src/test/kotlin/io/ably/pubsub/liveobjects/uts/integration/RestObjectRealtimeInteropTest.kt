package io.ably.pubsub.liveobjects.uts.integration

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
import io.ably.pubsub.uts.infra.integration.SandboxApp
import io.ably.pubsub.uts.infra.unit.TestHttpClient
import io.ably.pubsub.uts.infra.unit.TestRealtimeClient
import io.ably.pubsub.uts.infra.awaitState
import io.ably.pubsub.realtime.ConnectionState
import io.ably.pubsub.types.ChannelMode
import io.ably.pubsub.types.ChannelOptions
import io.ably.pubsub.liveobjects.value.LiveCounter
import io.ably.pubsub.liveobjects.value.LiveMap
import io.ably.pubsub.liveobjects.value.LiveMapValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
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
 * Realtime → REST interop for `HttpObject` (spec `RestObject`): objects written over a realtime
 * connection are read back with `channel.object.get()` on an HTTP channel. The rest of the spec's
 * tests need no realtime plugin and live in `:core`
 * (`io.ably.pubsub.uts.integration.standard.objects.RestObjectTest`).
 *
 * Spec points: RSL16, RSO2g. Source spec: `objects/integration/rest_object_test.md`
 * (test `objects/integration/RSL16/realtime-write-rest-read-0` only).
 *
 * Each test runs once per protocol variant (JSON / msgpack) per the spec's `PROTOCOL` dimension —
 * realised here with a `useBinaryProtocol` [ParameterizedTest] parameter.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RestObjectRealtimeInteropTest {

    private lateinit var app: SandboxApp

    @BeforeAll
    fun setUpAll() = runBlocking {
        app = SandboxApp.create()
    }

    @AfterAll
    fun tearDownAll() = runBlocking {
        if (::app.isInitialized) app.delete()
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
     * @UTS objects/integration/RSL16/realtime-write-rest-read-0
     */
    @ParameterizedTest(name = "useBinaryProtocol={0}")
    @ValueSource(booleans = [false, true])
    fun `RSL16, RSO2g - state written by a realtime client is readable via REST`(useBinaryProtocol: Boolean) = runTest {
        val channelName = "objects-rest-RSL16-interop-" + UUID.randomUUID()

        val realtime = TestRealtimeClient {
            key = app.defaultKey
            endpoint = SandboxApp.sandboxEndpoint
            autoConnect = false
            this.useBinaryProtocol = useBinaryProtocol
        }
        try {
            realtime.connect()
            awaitState(realtime, ConnectionState.connected, 15.seconds)

            val realtimeChannel = realtime.channels.get(
                channelName,
                ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe, ChannelMode.object_publish) },
            )
            val root = realtimeChannel.`object`.get().await()

            val rest = TestHttpClient {
                key = app.defaultKey
                endpoint = SandboxApp.sandboxEndpoint
                this.useBinaryProtocol = useBinaryProtocol
            }
            val channel = rest.channels.get(channelName)

            root.set("str", LiveMapValue.of("hello")).await()
            root.set("bin", LiveMapValue.of(byteArrayOf(0x01, 0x02, 0x03))).await()
            root.set("prefs", LiveMapValue.of(JsonObject().apply { addProperty("theme", "dark") })).await()
            root.set("visits", LiveMapValue.of(LiveCounter.create(7))).await()
            root.set("profile", LiveMapValue.of(LiveMap.create(mapOf("nickname" to LiveMapValue.of("ally"))))).await()

            val full = pollUntilSuccess {
                val r = channel.`object`.get() as? HttpLiveMap
                if (r != null && "profile" in r.entries) r else null
            }
            val compact = assertIs<JsonObject>(channel.`object`.getCompact())

            assertEquals("hello", compact["str"].asString)
            assertEquals(7.0, compact["visits"].asDouble)
            assertEquals("ally", compact["profile"].asJsonObject["nickname"].asString)

            assertEquals("root", full.objectId)
            fun leaf(key: String) = assertIs<HttpObjectData>(full.entries.getValue(key).data)
            assertEquals("hello", leaf("str").string)
            assertIs<ByteArray>(leaf("bin").bytes)
            assertContentEquals(byteArrayOf(0x01, 0x02, 0x03), leaf("bin").bytes)
            assertEquals(json("{\"theme\":\"dark\"}"), leaf("prefs").json)

            val visits = full.entries.getValue("visits").data
            assertIs<HttpLiveCounter>(visits)
            assertTrue(visits.objectId.startsWith("counter:"))
            assertEquals(7.0, visits.count)

            val profile = full.entries.getValue("profile").data
            assertIs<HttpLiveMap>(profile)
            assertTrue(profile.objectId.startsWith("map:"))
            assertEquals("ally", assertIs<HttpObjectData>(profile.entries.getValue("nickname").data).string)
        } finally {
            realtime.close()
        }
    }
}
