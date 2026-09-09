package io.ably.lib.uts.unit.realtime

import io.ably.lib.uts.infra.unit.*
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.types.RecoveryKeyContext
import io.ably.lib.uts.infra.awaitChannelState
import io.ably.lib.uts.infra.awaitState
import io.ably.lib.uts.infra.pollUntil
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class ConnectionRecoveryTest {

  /**
   * @UTS realtime/unit/RTN16g/recovery-key-structure-0
   */
  @Test
  fun `RTN16g, RTN16g1 - createRecoveryKey returns string with connectionKey, msgSerial, and channel and channelSerial pairs`() =
    runTest {
      val mock = MockWebSocket {
        onConnectionAttempt = { conn ->
          conn.respondWithSuccess(CONNECTED_MESSAGE.apply {
            connectionDetails = connectionDetails.apply {
              connectionKey = "key-abc-123"
            }
          })
        }
      }
      val client = TestRealtimeClient {
        autoConnect = false
        install(mock)
      }

      client.connect()
      awaitState(client, ConnectionState.connected)

      val channelA = client.channels.get("channel-alpha")
      val channelB = client.channels.get("channel-éàü-世界")

      channelA.attach()
      mock.sendToClient(ProtocolMessage().apply {
        action = ProtocolMessage.Action.attached
        channel = "channel-alpha"
        channelSerial = "serial-a-001"
      })
      awaitChannelState(channelA, ChannelState.attached)

      channelB.attach()
      mock.sendToClient(ProtocolMessage().apply {
        action = ProtocolMessage.Action.attached
        channel = "channel-éàü-世界"
        channelSerial = "serial-b-002"
      })
      awaitChannelState(channelB, ChannelState.attached)

      val recoveryKeyString = client.connection.createRecoveryKey()
      assertNotNull(recoveryKeyString)

      val recoveryKey = RecoveryKeyContext.decode(recoveryKeyString)
      assertNotNull(recoveryKey)
      assertEquals("key-abc-123", recoveryKey!!.connectionKey)
      assertEquals(0L, recoveryKey.msgSerial)
      assertNotNull(recoveryKey.channelSerials)
      assertEquals("serial-a-001", recoveryKey.channelSerials["channel-alpha"])
      // RTN16g1: Unicode channel name is correctly encoded in the serialized key
      assertEquals("serial-b-002", recoveryKey.channelSerials["channel-éàü-世界"])

      // Verify round-trip: re-encoding and re-decoding preserves the unicode name
      val reParsed = RecoveryKeyContext.decode(recoveryKey.encode())
      assertEquals("serial-b-002", reParsed!!.channelSerials["channel-éàü-世界"])

      client.close()
    }

  /**
   * @UTS realtime/unit/RTN16g2/recovery-key-null-inactive-0
   */
  @Test
  fun `RTN16g2 - createRecoveryKey returns null in inactive states and before first connect`() = runTest {
    // --- Part 1: INITIALIZED state (before connect) ---
    val mock = MockWebSocket {
      onConnectionAttempt = { conn ->
        conn.respondWithSuccess(ProtocolMessage().apply {
          action = ProtocolMessage.Action.connected
          connectionId = "connection-1"
          connectionDetails = ConnectionDetails {
            connectionKey = "key-1"
            maxIdleInterval = 15000L
            connectionStateTtl = 120000L
          }
        })
      }
    }
    val client = TestRealtimeClient {
      autoConnect = false
      install(mock)
    }

    // Before connecting — no connectionKey → null
    assertNull(client.connection.createRecoveryKey())

    client.connect()
    awaitState(client, ConnectionState.connected)
    assertNotNull(client.connection.createRecoveryKey())

    // --- CLOSING and CLOSED states ---
    // connection.close() sets key = null immediately (Connection.java:116)
    client.connection.close()
    assertNull(client.connection.createRecoveryKey())

    awaitState(client, ConnectionState.closing)
    assertNull(client.connection.createRecoveryKey())

    mock.sendToClientAndClose(ProtocolMessage().apply {
      action = ProtocolMessage.Action.closed
    })

    awaitState(client, ConnectionState.closed)
    assertNull(client.connection.createRecoveryKey())

    // --- FAILED state ---
    val mockFailed = MockWebSocket {
      onConnectionAttempt = { conn ->
        conn.respondWithSuccess(ProtocolMessage().apply {
          action = ProtocolMessage.Action.connected
          connectionId = "conn-f"
          connectionDetails = ConnectionDetails {
            connectionKey = "key-f"
            maxIdleInterval = 15000L
            connectionStateTtl = 120000L
          }
        })
      }
    }
    val clientFailed = TestRealtimeClient {
      autoConnect = false
      install(mockFailed)
    }

    clientFailed.connect()
    awaitState(clientFailed, ConnectionState.connected)

    // DEVIATION: spec uses error code=50000/statusCode=500, but SDK's isFatalError() requires
    // code 40000–49999 or statusCode < 500; 50000/500 is non-fatal so FAILED is never reached.
    // Using code=40000/statusCode=400. See deviations.md.
    // DEVIATION: spec uses send_to_client_and_close, but sending close(1000) fires a synchronous
    // DISCONNECTED action that preempts the async FAILED transition. Using sendToClient instead;
    // the SDK's FAILED handling calls clearTransport() itself. See deviations.md.
    // ErrorInfo constructor arg order: ErrorInfo(message, statusCode, code).
    mockFailed.sendToClient(ProtocolMessage().apply {
      action = ProtocolMessage.Action.error
      error = ErrorInfo("Fatal error", 400, 40000)
    })
    awaitState(clientFailed, ConnectionState.failed)
    assertNull(clientFailed.connection.createRecoveryKey())

    // --- SUSPENDED state (fake timers, short timeouts) ---
    // Initial connection uses awaitConnectionAttempt because reconnection attempts after
    // disconnect need different behavior (refused), and onConnectionAttempt handles all
    // attempts uniformly.
    val fakeClock = FakeClock()
    val mockSuspended = MockWebSocket()
    val clientSuspended = TestRealtimeClient {
      autoConnect = false
      disconnectedRetryTimeout = 300
      fallbackHosts = emptyArray()
      install(mockSuspended)
      enableFakeTimers(fakeClock)
    }

    launch {
      mockSuspended.awaitConnectionAttempt().respondWithSuccess(ProtocolMessage().apply {
        action = ProtocolMessage.Action.connected
        connectionId = "conn-s"
        connectionDetails = ConnectionDetails {
          connectionKey = "key-s"
          maxIdleInterval = 15000L
          connectionStateTtl = 800L
        }
      })
    }

    clientSuspended.connect()
    awaitState(clientSuspended, ConnectionState.connected)

    mockSuspended.simulateDisconnect()

    // Refuse all reconnection attempts after disconnect — refuseJob started here,
    // AFTER the initial connection succeeded, so it cannot intercept the first connect.
    val refuseJob = launch {
      repeat(10) {
        fakeClock.advance(2.seconds)
        val pendingConnection = mockSuspended.awaitConnectionAttempt()
        pendingConnection.respondWithRefused()
        if (clientSuspended.connection.state == ConnectionState.suspended) return@launch
      }
    }

    awaitState(clientSuspended, ConnectionState.suspended)
    refuseJob.cancel()

    assertNull(clientSuspended.connection.createRecoveryKey())
    clientSuspended.close()
  }

  /**
   * @UTS realtime/unit/RTN16k/recover-query-param-0
   */
  @Test
  fun `RTN16k - recover option adds recover query param to WebSocket URL`() = runTest {
    val recoveryKeyJson = RecoveryKeyContext("recovered-key-xyz", 5, emptyMap()).encode()

    // Written from onConnectionAttempt (SDK transport thread) and read from pollUntil/asserts
    // (coroutine dispatcher), so use a thread-safe list to avoid a visibility race / flaky reads.
    val capturedQueryParams = CopyOnWriteArrayList<Map<String, String>>()
    var connectAttempt = 0
    val mock = MockWebSocket {
      onConnectionAttempt = { conn ->
        capturedQueryParams += conn.queryParams
        val key = if (connectAttempt++ == 0) "new-key-after-recovery" else "resumed-key"
        conn.respondWithSuccess(ProtocolMessage().apply {
          action = ProtocolMessage.Action.connected
          connectionId = "recovered-conn-id"
          connectionDetails = ConnectionDetails {
            connectionKey = key
            maxIdleInterval = 15000L
            connectionStateTtl = 120000L
          }
        })
      }
    }
    val client = TestRealtimeClient {
      autoConnect = false
      recover = recoveryKeyJson
      install(mock)
    }

    client.connect()
    awaitState(client, ConnectionState.connected)

    mock.simulateDisconnect()
    // Wait for the reconnect's connection attempt to be captured, not just for the CONNECTED state:
    // right after simulateDisconnect() the state is still `connected`, so awaitState(connected) would
    // short-circuit before the second attempt is recorded (the transient-state race called out in
    // writing-test-specs.md). Gate on the second attempt actually arriving.
    pollUntil { capturedQueryParams.size >= 2 }
    awaitState(client, ConnectionState.connected)

    assertEquals("recovered-key-xyz", capturedQueryParams[0]["recover"])
    assertNull(capturedQueryParams[0]["resume"])
    assertEquals("new-key-after-recovery", capturedQueryParams[1]["resume"])
    assertNull(capturedQueryParams[1]["recover"])

    client.close()
  }

  /**
   * @UTS realtime/unit/RTN16f/recover-initializes-msgserial-0
   */
  @Test
  fun `RTN16f - recover option initializes msgSerial from recoveryKey`() = runTest {
    val recoveryKeyJson = RecoveryKeyContext(
      "old-key",
      42L,
      mapOf("test-channel" to "ch-serial-1")
    ).encode()

    val mock = MockWebSocket {
      onConnectionAttempt = { conn ->
        conn.respondWithSuccess(ProtocolMessage().apply {
          action = ProtocolMessage.Action.connected
          connectionId = "recovered-conn"
          connectionDetails = ConnectionDetails {
            connectionKey = "new-key"
            maxIdleInterval = 15000L
            connectionStateTtl = 120000L
          }
        })
      }
    }
    val client = TestRealtimeClient {
      autoConnect = false
      recover = recoveryKeyJson
      install(mock)
    }

    client.connect()
    awaitState(client, ConnectionState.connected)

    val testChannel = client.channels.get("test-channel")
    testChannel.attach()
    mock.sendToClient(ProtocolMessage().apply {
      action = ProtocolMessage.Action.attached
      channel = "test-channel"
      channelSerial = "ch-serial-updated"
    })
    awaitChannelState(testChannel, ChannelState.attached)

    // Verify msgSerial was initialized from the recoveryKey (RTN16f).
    // SDK deviation: ConnectionManager.onConnected() resets msgSerial = 0 when connection.id == null
    // (fresh client, ConnectionManager.java:1316), even when using the recover option.
    // Spec requires msgSerial to be preserved from the recovery key (42). See deviations.md.
    val currentRecoveryKey = RecoveryKeyContext.decode(client.connection.createRecoveryKey()!!)
    assertNotNull(currentRecoveryKey)
    if (System.getenv("RUN_DEVIATIONS") != null) {
      assertEquals(42L, currentRecoveryKey.msgSerial)
    } else {
      assertEquals(0L, currentRecoveryKey.msgSerial)
    }

    client.close()
  }

  /**
   * @UTS realtime/unit/RTN16f1/malformed-recovery-key-0
   */
  @Test
  fun `RTN16f1 - Malformed recoveryKey logs error and connects normally`() = runTest {
    // Written on the SDK transport thread and read from the coroutine dispatcher, so use an
    // AtomicReference to publish it safely across threads (avoids a visibility race).
    val capturedQueryParams = AtomicReference<Map<String, String>?>(null)
    val mock = MockWebSocket {
      onConnectionAttempt = { conn ->
        capturedQueryParams.set(conn.queryParams)
        conn.respondWithSuccess(ProtocolMessage().apply {
          action = ProtocolMessage.Action.connected
          connectionId = "fresh-conn"
          connectionDetails = ConnectionDetails {
            connectionKey = "fresh-key"
            maxIdleInterval = 15000L
            connectionStateTtl = 120000L
          }
        })
      }
    }
    val client = TestRealtimeClient {
      autoConnect = false
      recover = "this-is-not-valid-json!!!"
      install(mock)
    }

    client.connect()
    awaitState(client, ConnectionState.connected)

    assertEquals(ConnectionState.connected, client.connection.state)
    assertEquals("fresh-conn", client.connection.id)
    assertEquals("fresh-key", client.connection.key)
    assertNull(capturedQueryParams.get()!!["recover"])
    assertNull(capturedQueryParams.get()!!["resume"])
    assertEquals(1, mock.events.filterIsInstance<MockEvent.ConnectionAttempt>().size)

    client.close()
  }

  /**
   * @UTS realtime/unit/RTN16j/recover-channel-serials-0
   */
  @Test
  fun `RTN16j - recover option instantiates channels from recoveryKey with correct channelSerials`() = runTest {
    val recoveryKeyJson = RecoveryKeyContext(
      "old-key-abc",
      10L,
      mapOf(
        "channel-one" to "serial-1-abc",
        "channel-two" to "serial-2-def",
        "channel-üñîçöðé" to "serial-3-unicode"
      )
    ).encode()

    val mock = MockWebSocket {
      onConnectionAttempt = { conn ->
        conn.respondWithSuccess(ProtocolMessage().apply {
          action = ProtocolMessage.Action.connected
          connectionId = "recovered-conn"
          connectionDetails = ConnectionDetails {
            connectionKey = "new-key"
            maxIdleInterval = 15000L
            connectionStateTtl = 120000L
          }
        })
      }
    }
    val client = TestRealtimeClient {
      autoConnect = false
      recover = recoveryKeyJson
      install(mock)
    }

    client.connect()
    awaitState(client, ConnectionState.connected)

    // RTN16j: Channels from the recoveryKey are instantiated with their channelSerials
    val channelOne = client.channels.get("channel-one")
    val channelTwo = client.channels.get("channel-two")
    val channelUnicode = client.channels.get("channel-üñîçöðé")

    assertEquals("serial-1-abc", channelOne.properties.channelSerial)
    assertEquals("serial-2-def", channelTwo.properties.channelSerial)
    assertEquals("serial-3-unicode", channelUnicode.properties.channelSerial)

    // RTN16i: Channels are NOT automatically attached — should be in INITIALIZED state
    assertEquals(ChannelState.initialized, channelOne.state)
    assertEquals(ChannelState.initialized, channelTwo.state)
    assertEquals(ChannelState.initialized, channelUnicode.state)

    channelOne.attach()
    val attachMessage = mock.awaitNextMessageFromClient()
    assertEquals(ProtocolMessage.Action.attach, attachMessage.action)
    assertEquals("channel-one", attachMessage.channel)
    assertEquals("serial-1-abc", attachMessage.channelSerial)

    mock.sendToClient(ProtocolMessage().apply {
      action = ProtocolMessage.Action.attached
      channel = "channel-one"
      channelSerial = "serial-1-abc-updated"
    })
    awaitChannelState(channelOne, ChannelState.attached)

    client.close()
  }
}
