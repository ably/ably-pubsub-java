package io.ably.pubsub.device

import com.sun.net.httpserver.HttpServer
import io.ably.pubsub.types.AblyException
import io.ably.pubsub.types.ClientOptions
import io.ably.pubsub.util.Side
import org.junit.Assert
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The agent entries asserted here are what the platform reads to classify traffic on
 * MAU-priced accounts, so these tests are deliberately strict: if one fails, billing
 * classification is broken, not just a header.
 *
 *
 * The side entry is a versionless flag — a bare token on the wire, registered as such in the ably-common agents registry
 * — so the assertions also fail if a version (or any `/suffix`) reappears on it.
 */
class PubSubDeviceTest {
  @Test
  @Throws(Exception::class)
  fun client_stampsDeviceAgent() {
    val client = PubSubDevice.clientBuilder().key(FAKE_KEY).autoConnect(false).build()
    assertDeviceFlag(client.options.agents)
  }

  @Test
  @Throws(Exception::class)
  fun keyString_isAcceptedAndDisambiguatedAsKey() {
    val builtOptions = PubSubDevice.clientBuilder().key(FAKE_KEY).autoConnect(false).build().options
    Assert.assertEquals(FAKE_KEY, builtOptions.key)
    Assert.assertNull(builtOptions.token)
    assertDeviceFlag(builtOptions.agents)
  }

  @Test
  @Throws(Exception::class)
  fun callerAgentEntries_arePreserved_andCannotOverrideTheSideEntry() {
    val callerAgents: Map<String, String> = mapOf(
      "some-sdk" to "1.2.3",
      Side.DEVICE_AGENT_IDENTIFIER to "not-the-real-form",
    )

    val client = PubSubDevice.clientBuilder().key(FAKE_KEY).autoConnect(false).agents(callerAgents).build()
    Assert.assertEquals("1.2.3", client.options.agents.get("some-sdk"))
    // The stamp replaces the caller's value: the flag is present and back to versionless.
    assertDeviceFlag(client.options.agents)

    Assert.assertEquals("not-the-real-form", callerAgents.get(Side.DEVICE_AGENT_IDENTIFIER))
  }

  @Test
  fun sideFlags_arePinnedToTheirWireLiterals() {
    // Literal strings, not the constants: the platform classifies traffic by these exact tokens.
    val agents = PubSubDevice.clientBuilder().key(FAKE_KEY).autoConnect(false).build().options.agents
    Assert.assertTrue(agents.containsKey("ably-pubsub-device"))
    Assert.assertFalse(agents.containsKey("ably-pubsub-server"))
  }

  @Test
  fun callerSuppliedServerEntry_isStripped() {
    val callerAgents: Map<String, String?> = mapOf("ably-pubsub-server" to null, "some-sdk" to "1.2.3")
    val agents = PubSubDevice.clientBuilder().key(FAKE_KEY).autoConnect(false)
      .agents(callerAgents).build().options.agents
    Assert.assertFalse(agents.containsKey("ably-pubsub-server"))
    Assert.assertTrue(agents.containsKey("ably-pubsub-device"))
    Assert.assertEquals("1.2.3", agents["some-sdk"])
    Assert.assertTrue("the caller's map is not modified", callerAgents.containsKey("ably-pubsub-server"))
  }

  @Test
  fun reusingABuilder_doesNotAffectAnEarlierClient() {
    val builder = PubSubDevice.clientBuilder().key(FAKE_KEY).autoConnect(false)
    val first = builder.build()

    builder.key("other.key:secret").agents(mapOf("later-sdk" to "9")).tls(false)
    val second = builder.build()

    Assert.assertEquals(FAKE_KEY, first.options.key)
    Assert.assertTrue(first.options.tls)
    Assert.assertFalse(first.options.agents.containsKey("later-sdk"))
    Assert.assertEquals("other.key:secret", second.options.key)
    Assert.assertTrue(second.options.agents.containsKey("later-sdk"))
  }

  @Test
  fun changingPassedOptionsAfterBuild_doesNotAffectTheClient() {
    val options = ClientOptions(FAKE_KEY)
    options.autoConnect = false
    val client = PubSubDevice.clientBuilder(options).build()

    options.clientId = "changed"
    options.agents = HashMap()

    Assert.assertNotSame(options, client.options)
    Assert.assertNull(client.options.clientId)
    assertDeviceFlag(client.options.agents)
  }

  @Test
  fun noAuthParameters_getTheCoreConstructorsOwnError() {
    try {
      PubSubDevice.clientBuilder().build()
      Assert.fail("expected the core's initialization error")
    } catch (e: AblyException) {
      Assert.assertEquals(40000, e.errorInfo.code)
    }
  }

  /** Wire-level: the Ably-Agent header actually sent over HTTP carries the device flag as a bare token. */
  @Test
  fun httpRequests_carryTheDeviceAgentHeaderOnTheWire() {
    val observed = AtomicReference<String?>()
    val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    httpServer.createContext("/time") { exchange ->
      observed.set(exchange.requestHeaders.getFirst("Ably-Agent"))
      val body = "[1234567890000]".toByteArray(Charsets.UTF_8)
      exchange.responseHeaders.add("Content-Type", "application/json")
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.write(body)
      exchange.close()
    }
    httpServer.start()
    try {
      val client = PubSubDevice.clientBuilder()
        .key(FAKE_KEY)
        .autoConnect(false)
        .tls(false)
        .endpoint("127.0.0.1")
        .port(httpServer.address.port)
        .build()
      client.time()

      val header = observed.get()
      Assert.assertNotNull("no Ably-Agent header observed", header)
      Assert.assertTrue("missing bare device flag in: $header", header!!.split(" ").contains("ably-pubsub-device"))
      Assert.assertFalse("side flag must be versionless in: $header", header.contains("ably-pubsub-device/"))
      Assert.assertFalse("must not carry the server flag: $header", header.contains("ably-pubsub-server"))
      Assert.assertTrue("missing core base identifier in: $header", header.contains("ably-pubsub-java/"))
    } finally {
      httpServer.stop(0)
    }
  }

  /** Wire-level: the `agent` query param of the WebSocket upgrade request carries the device flag. */
  @Test
  fun webSocketConnections_carryTheDeviceAgentParamOnTheWire() {
    ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
      val requestLine = AtomicReference<String?>()
      val seen = CountDownLatch(1)
      val acceptor = Thread {
        try {
          listener.accept().use { socket ->
            requestLine.set(socket.getInputStream().bufferedReader(Charsets.UTF_8).readLine())
          }
        } catch (ignored: Exception) {
          // the test fails on the missing request line
        } finally {
          seen.countDown()
        }
      }
      acceptor.isDaemon = true
      acceptor.start()

      val client = PubSubDevice.clientBuilder()
        .key(FAKE_KEY)
        .tls(false)
        .endpoint("127.0.0.1")
        .port(listener.localPort)
        .build()
      try {
        Assert.assertTrue("no WebSocket upgrade request seen", seen.await(15, TimeUnit.SECONDS))
      } finally {
        client.close()
      }

      val line = requestLine.get()
      Assert.assertNotNull("no request line observed", line)
      val query = if (line!!.contains("?")) line.substring(line.indexOf('?') + 1, line.lastIndexOf(' ')) else ""
      val agent = query.split("&")
        .firstOrNull { it.startsWith("agent=") }
        ?.let { URLDecoder.decode(it.removePrefix("agent="), "UTF-8") }
      Assert.assertNotNull("no agent query param in: $line", agent)
      Assert.assertTrue("missing core base identifier in: $agent", agent!!.contains("ably-pubsub-java/"))
      Assert.assertTrue("missing bare device flag in: $agent", agent.split(" ").contains("ably-pubsub-device"))
      Assert.assertFalse("must not carry the server flag: $agent", agent.contains("ably-pubsub-server"))
    }
  }

  companion object {
    private const val FAKE_KEY = "fakeAppId.fakeKeyId:fakeKeySecret"

    /** The stamped entry is present as a versionless flag, and the other side's is absent.  */
    private fun assertDeviceFlag(agents: MutableMap<String?, String?>) {
      Assert.assertTrue("expected the device side flag", agents.containsKey(Side.DEVICE_AGENT_IDENTIFIER))
      Assert.assertNull("the side flag is versionless", agents.get(Side.DEVICE_AGENT_IDENTIFIER))
      Assert.assertFalse(
        "a device client must not carry the server entry",
        agents.containsKey(Side.SERVER_AGENT_IDENTIFIER)
      )
    }
  }
}
