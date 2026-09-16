package com.ably.pubsub

import app.cash.turbine.test
import com.ably.EmbeddedServer
import com.ably.createAblyRest
import com.ably.createAblyRealtime
import com.ably.json
import com.ably.waitFor
import io.ably.lib.BuildConfig
import io.ably.lib.realtime.RealtimeClient
import io.ably.lib.rest.RestClient
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test
import kotlin.test.assertEquals

class SdkWrapperAgentHeaderTest {

  @Test
  fun `should use additional agents in Realtime wrapper SDK client calls`() = runTest {
    val realtimeClient = createRealtimeClient()

    val wrapperSdkClient =
      realtimeClient.createWrapperSdkProxy(WrapperSdkProxyOptions(agents = mapOf("chat-android" to "0.1.0")))

    server.servedRequests.test {
      wrapperSdkClient.time()
      assertEquals(
        setOf("ably-pubsub-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}", "chat-android/0.1.0"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }

    server.servedRequests.test {
      realtimeClient.time()
      assertEquals(
        setOf("ably-pubsub-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }

    server.servedRequests.test {
      wrapperSdkClient.request("/time")
      assertEquals(
        setOf("ably-pubsub-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}", "chat-android/0.1.0"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }
  }

  @Test
  fun `should use additional agents in Rest wrapper SDK client calls`() = runTest {
    val restClient = createRealtimeClient()

    val wrapperSdkClient =
      restClient.createWrapperSdkProxy(WrapperSdkProxyOptions(agents = mapOf("chat-android" to "0.1.0")))

    server.servedRequests.test {
      wrapperSdkClient.time()
      assertEquals(
        setOf("ably-pubsub-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}", "chat-android/0.1.0"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }

    server.servedRequests.test {
      restClient.time()
      assertEquals(
        setOf("ably-pubsub-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }

    server.servedRequests.test {
      wrapperSdkClient.request("/time")
      assertEquals(
        setOf("ably-pubsub-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}", "chat-android/0.1.0"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }
  }

  @Test
  fun `should use additional agents in Rest wrapper SDK channel calls`() = runTest {
    val restClient = createRestClient()

    val wrapperSdkClient =
      restClient.createWrapperSdkProxy(WrapperSdkProxyOptions(agents = mapOf("chat-android" to "0.1.0")))

    server.servedRequests.test {
      wrapperSdkClient.channels.get("test").history()
      assertEquals(
        setOf("ably-pubsub-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}", "chat-android/0.1.0"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }

    server.servedRequests.test {
      restClient.channels.get("test").history()
      assertEquals(
        setOf("ably-pubsub-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }

    server.servedRequests.test {
      wrapperSdkClient.channels.get("test").presence.history()
      assertEquals(
        setOf("ably-pubsub-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}", "chat-android/0.1.0"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }
  }

  @Test
  fun `should use additional agents in Realtime wrapper SDK channel calls`() = runTest {
    val realtimeClient = createRealtimeClient()

    val wrapperSdkClient =
      realtimeClient.createWrapperSdkProxy(WrapperSdkProxyOptions(agents = mapOf("chat-android" to "0.1.0")))

    server.servedRequests.test {
      wrapperSdkClient.channels.get("test").history()
      assertEquals(
        setOf("ably-pubsub-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}", "chat-android/0.1.0"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }

    server.servedRequests.test {
      realtimeClient.channels.get("test").history()
      assertEquals(
        setOf("ably-pubsub-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }

    server.servedRequests.test {
      wrapperSdkClient.channels.get("test").presence.history()
      assertEquals(
        setOf("ably-pubsub-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}", "chat-android/0.1.0"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }
  }

  companion object {

    private const val PORT = 27332
    private lateinit var server: EmbeddedServer

    @JvmStatic
    @BeforeAll
    fun setUp() = runTest {
      server = EmbeddedServer(PORT) {
        when (it.path) {
          "/time" -> json("[1739551931167]")
          else -> json("[]")
        }
      }
      server.start()
      waitFor { server.wasStarted() }
    }

    @JvmStatic
    @AfterAll
    fun tearDown() {
      server.stop()
    }

    private fun createRealtimeClient(): RealtimeClient = RealtimeClient(createAblyRealtime(PORT))

    private fun createRestClient(): RestClient = RestClient(createAblyRest(PORT))
  }
}
