package io.ably.lib.uts.unit.realtime

import io.ably.lib.uts.infra.unit.*
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.util.Log
import io.ably.lib.uts.infra.awaitChannelState
import io.ably.lib.uts.infra.awaitState
import kotlinx.coroutines.test.runTest
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

class ChannelsCollectionTest {

  /**
   * @UTS realtime/unit/RTS4c/release-nonexistent-noop-0
   */
  @Test
  fun `RTS4c - release on non-existent channel is no-op`() {
    val channelName = "test-RTS4c-nonexistent-${UUID.randomUUID()}"
    val client = TestRealtimeClient { autoConnect = false }

    client.channels.release(channelName)

    assertFalse(client.channels.containsKey(channelName))
    client.close()
  }

  /**
   * @UTS realtime/unit/RTS4d/release-removes-channel-0
   */
  @Test
  fun `RTS4d - release removes an initialized channel`() {
    val channelName = "test-RTS4d-${UUID.randomUUID()}"
    val client = TestRealtimeClient { autoConnect = false }

    val channel = client.channels.get(channelName)
    assertEquals(ChannelState.initialized, channel.state)
    assertTrue(client.channels.containsKey(channelName))

    client.channels.release(channelName)

    assertFalse(client.channels.containsKey(channelName))
    client.close()
  }

  /**
   * @UTS realtime/unit/RTS4d/release-after-detach-1
   */
  @Test
  fun `RTS4d - release removes a channel once detached`() = runTest {
    val channelName = "test-RTS4d-detached-${UUID.randomUUID()}"
    val warnings = CopyOnWriteArrayList<String>()
    val client = TestRealtimeClient {
      autoConnect = false
      install(attachDetachMock())
      logHandler = Log.LogHandler { severity, _, msg, _ -> if (severity == Log.WARN) warnings.add(msg) }
    }
    val channel = client.channels.get(channelName)

    client.connect()
    awaitState(client, ConnectionState.connected)

    channel.attach()
    awaitChannelState(channel, ChannelState.attached)
    channel.detach()
    awaitChannelState(channel, ChannelState.detached)

    client.channels.release(channelName)

    assertFalse(client.channels.containsKey(channelName))
    assertTrue(warnings.none { it.contains("channels.release()") })
    client.close()
  }

  @Test
  fun `RTS4b - release of an attached channel logs a deprecation warning and still releases it`() = runTest {
    val channelName = "test-RTS4b-attached-${UUID.randomUUID()}"
    val warnings = CopyOnWriteArrayList<String>()
    val client = TestRealtimeClient {
      autoConnect = false
      install(attachDetachMock())
      logHandler = Log.LogHandler { severity, _, msg, _ -> if (severity == Log.WARN) warnings.add(msg) }
    }
    val channel = client.channels.get(channelName)

    client.connect()
    awaitState(client, ConnectionState.connected)

    channel.attach()
    awaitChannelState(channel, ChannelState.attached)

    client.channels.release(channelName)

    assertFalse(client.channels.containsKey(channelName))
    assertEquals(ChannelState.detached, channel.state)
    val deprecationWarnings = warnings.filter { it.contains("channels.release()") }
    assertEquals(1, deprecationWarnings.size)
    assertContains(deprecationWarnings[0], "attached state is deprecated")
    client.close()
  }

  private fun attachDetachMock(): MockWebSocket {
    lateinit var mockWs: MockWebSocket
    mockWs = MockWebSocket {
      onConnectionAttempt = { conn -> conn.respondWithSuccess(CONNECTED_MESSAGE) }
      onMessageFromClient = { msg ->
        when (msg.action) {
          ProtocolMessage.Action.attach ->
            mockWs.sendToClient(ProtocolMessage(ProtocolMessage.Action.attached, msg.channel))
          ProtocolMessage.Action.detach ->
            mockWs.sendToClient(ProtocolMessage(ProtocolMessage.Action.detached, msg.channel))
          else -> Unit
        }
      }
    }
    return mockWs
  }
}
