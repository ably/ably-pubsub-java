package io.ably.pubsub.uts.unit.realtime

import io.ably.pubsub.uts.infra.unit.*
import io.ably.pubsub.realtime.ChannelState
import io.ably.pubsub.realtime.ConnectionState
import io.ably.pubsub.types.AblyException
import io.ably.pubsub.types.ProtocolMessage
import io.ably.pubsub.uts.infra.awaitChannelState
import io.ably.pubsub.uts.infra.awaitState
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
    val client = TestRealtimeClient {
      autoConnect = false
      install(mockWs)
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
    client.close()
  }

  /**
   * @UTS realtime/unit/RTS4e/release-attached-fails-0
   */
  @Test
  fun `RTS4e - release of an attached channel fails`() = runTest {
    val channelName = "test-RTS4e-attached-${UUID.randomUUID()}"
    val capturedDetachMessages = CopyOnWriteArrayList<ProtocolMessage>()
    lateinit var mockWs: MockWebSocket
    mockWs = MockWebSocket {
      onConnectionAttempt = { conn -> conn.respondWithSuccess(CONNECTED_MESSAGE) }
      onMessageFromClient = { msg ->
        when (msg.action) {
          ProtocolMessage.Action.attach ->
            mockWs.sendToClient(ProtocolMessage(ProtocolMessage.Action.attached, msg.channel))
          ProtocolMessage.Action.detach -> capturedDetachMessages.add(msg)
          else -> Unit
        }
      }
    }
    val client = TestRealtimeClient {
      autoConnect = false
      install(mockWs)
    }
    val channel = client.channels.get(channelName)

    client.connect()
    awaitState(client, ConnectionState.connected)

    channel.attach()
    awaitChannelState(channel, ChannelState.attached)

    val error = assertFailsWith<AblyException> { client.channels.release(channelName) }.errorInfo

    assertEquals(90011, error.code)
    assertEquals(400, error.statusCode)
    assertEquals(ChannelState.attached, channel.state)
    assertTrue(client.channels.containsKey(channelName))
    assertSame(channel, client.channels.get(channelName))
    assertEquals(0, capturedDetachMessages.size)
    client.close()
  }
}
