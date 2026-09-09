package io.ably.lib.liveobjects.integration.setup

import io.ably.lib.liveobjects.integration.helpers.RestObjects
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Channel
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ChannelOptions
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.UUID

@RunWith(Parameterized::class)
abstract class IntegrationTest {
  @Parameterized.Parameter
  lateinit var testParams: String

  @JvmField
  @Rule
  val timeout: Timeout = Timeout.seconds(15)

  private val realtimeClients = mutableMapOf<String, AblyRealtime>()

  /**
   * Retrieves a realtime channel for the specified channel name and client ID
   * If a client with the given clientID does not exist, a new client is created using the provided options.
   *
   * @param channelName Name of the channel
   * @param clientId The ID of the client to use or create. Defaults to "client1".
   * @return The realtime channel in the INITIALIZED state.
   * @throws Exception If the client fails to connect.
   */
  internal suspend fun getRealtimeChannel(channelName: String, clientId: String = "client1"): Channel {
    val client = realtimeClients.getOrPut(clientId) {
      sandbox.createRealtimeClient {
        this.clientId = clientId
        useBinaryProtocol = testParams == "msgpack_protocol"
      }. apply { ensureConnected() }
    }
    val channelOpts = ChannelOptions().apply {
      modes = arrayOf(ChannelMode.object_publish, ChannelMode.object_subscribe)
    }
    return client.channels.get(channelName, channelOpts)
  }

  /**
   * Generates a unique channel name for testing purposes.
   * This is mainly to avoid channel name/state/history collisions across tests in same file.
   */
  internal fun generateChannelName(): String {
    return "test-channel-${UUID.randomUUID()}"
  }

  @After
  fun afterEach() {
    for (ablyRealtime in realtimeClients.values) {
      for ((channelName, channel) in ablyRealtime.channels.entrySet()) {
        channel.off()
        ablyRealtime.channels.release(channelName)
      }
      ablyRealtime.close()
    }
    realtimeClients.clear()
  }

  companion object {
    private lateinit var sandbox: Sandbox
    internal lateinit var restObjects: RestObjects

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Iterable<String> {
      return listOf("msgpack_protocol", "json_protocol")
    }

    @JvmStatic
    @BeforeClass
    @Throws(Exception::class)
    fun setUpBeforeClass() {
      runBlocking {
        sandbox = Sandbox.createInstance()
        restObjects = sandbox.createRestObjects()
      }
    }

    @JvmStatic
    @AfterClass
    @Throws(Exception::class)
    fun tearDownAfterClass(): Unit = runBlocking {
      if (::sandbox.isInitialized) sandbox.delete()
    }
  }
}
