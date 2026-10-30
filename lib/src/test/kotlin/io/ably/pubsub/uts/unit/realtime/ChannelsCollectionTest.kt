package io.ably.pubsub.uts.unit.realtime

import io.ably.pubsub.uts.infra.unit.*
import io.ably.pubsub.realtime.ChannelState
import io.ably.pubsub.realtime.ConnectionState
import io.ably.pubsub.types.AblyException
import io.ably.pubsub.types.ErrorInfo
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

  /**
   * SDK-local coverage of RTS4d for a state the UTS spec does not test yet; **NOT a UTS spec test**.
   */
  @Test
  fun `RTS4d - release removes a failed channel`() = runTest {
    val channelName = "test-RTS4d-failed-${UUID.randomUUID()}"
    lateinit var mockWs: MockWebSocket
    mockWs = MockWebSocket {
      onConnectionAttempt = { conn -> conn.respondWithSuccess(CONNECTED_MESSAGE) }
      onMessageFromClient = { msg ->
        if (msg.action == ProtocolMessage.Action.attach) {
          // ErrorInfo constructor arg order: ErrorInfo(message, statusCode, code).
          mockWs.sendToClient(ProtocolMessage(ProtocolMessage.Action.error, msg.channel).apply {
            error = ErrorInfo("Attach refused", 400, 40160)
          })
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
    awaitChannelState(channel, ChannelState.failed)

    client.channels.release(channelName)

    assertFalse(client.channels.containsKey(channelName))
    client.close()
  }

  /**
   * SDK-local coverage of RTS4e for a state the UTS spec does not test yet; **NOT a UTS spec test**.
   */
  @Test
  fun `RTS4e - release of an attaching channel fails`() = runTest {
    val channelName = "test-RTS4e-attaching-${UUID.randomUUID()}"
    // The mock never answers the ATTACH, so the channel stays ATTACHING.
    val mockWs = MockWebSocket {
      onConnectionAttempt = { conn -> conn.respondWithSuccess(CONNECTED_MESSAGE) }
    }
    val client = TestRealtimeClient {
      autoConnect = false
      install(mockWs)
    }
    val channel = client.channels.get(channelName)

    client.connect()
    awaitState(client, ConnectionState.connected)

    channel.attach()
    awaitChannelState(channel, ChannelState.attaching)

    val error = assertFailsWith<AblyException> { client.channels.release(channelName) }.errorInfo

    assertEquals(90011, error.code)
    assertEquals(400, error.statusCode)
    assertEquals(ChannelState.attaching, channel.state)
    assertSame(channel, client.channels.get(channelName))
    client.close()
  }

  /**
   * SDK-local coverage of RTS4e for a state the UTS spec does not test yet; **NOT a UTS spec test**.
   */
  @Test
  fun `RTS4e - release of a detaching channel fails`() = runTest {
    val channelName = "test-RTS4e-detaching-${UUID.randomUUID()}"
    lateinit var mockWs: MockWebSocket
    // The mock answers the ATTACH but never the DETACH, so the channel stays DETACHING.
    mockWs = MockWebSocket {
      onConnectionAttempt = { conn -> conn.respondWithSuccess(CONNECTED_MESSAGE) }
      onMessageFromClient = { msg ->
        if (msg.action == ProtocolMessage.Action.attach) {
          mockWs.sendToClient(ProtocolMessage(ProtocolMessage.Action.attached, msg.channel))
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
    awaitChannelState(channel, ChannelState.detaching)

    val error = assertFailsWith<AblyException> { client.channels.release(channelName) }.errorInfo

    assertEquals(90011, error.code)
    assertEquals(400, error.statusCode)
    assertEquals(ChannelState.detaching, channel.state)
    assertSame(channel, client.channels.get(channelName))
    client.close()
  }

  /**
   * SDK-local coverage of RTS4e for a state the UTS spec does not test yet; **NOT a UTS spec test**.
   */
  @Test
  fun `RTS4e - release of a suspended channel fails`() = runTest {
    val channelName = "test-RTS4e-suspended-${UUID.randomUUID()}"
    lateinit var mockWs: MockWebSocket
    // A server-sent DETACHED while ATTACHING moves the channel to SUSPENDED (RTL13b); see deviations.md.
    mockWs = MockWebSocket {
      onConnectionAttempt = { conn -> conn.respondWithSuccess(CONNECTED_MESSAGE) }
      onMessageFromClient = { msg ->
        if (msg.action == ProtocolMessage.Action.attach) {
          mockWs.sendToClient(ProtocolMessage(ProtocolMessage.Action.detached, msg.channel))
        }
      }
    }
    val client = TestRealtimeClient {
      autoConnect = false
      channelRetryTimeout = 600_000
      install(mockWs)
    }
    val channel = client.channels.get(channelName)

    client.connect()
    awaitState(client, ConnectionState.connected)

    channel.attach()
    awaitChannelState(channel, ChannelState.suspended)

    val error = assertFailsWith<AblyException> { client.channels.release(channelName) }.errorInfo

    assertEquals(90011, error.code)
    assertEquals(400, error.statusCode)
    assertEquals(ChannelState.suspended, channel.state)
    assertSame(channel, client.channels.get(channelName))
    client.close()
  }
}
