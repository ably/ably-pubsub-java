package io.ably.pubsub.liveobjects.unit

import io.ably.pubsub.liveobjects.*
import io.ably.pubsub.liveobjects.adapter.AblyClientAdapter
import io.ably.pubsub.liveobjects.clientError
import io.ably.pubsub.liveobjects.connectionManager
import io.ably.pubsub.liveobjects.sendAsync
import io.ably.pubsub.realtime.Channel
import io.ably.pubsub.realtime.ChannelState
import io.ably.pubsub.realtime.CompletionListener
import io.ably.pubsub.realtime.ConnectionEvent
import io.ably.pubsub.realtime.ConnectionStateListener
import io.ably.pubsub.types.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.test.assertFailsWith

class HelpersTest {

  // sendAsync
  @Test
  fun testSendAsyncShouldQueueAccordingToClientOptions() = runTest {
    val adapter = getMockAblyClientAdapter()
    val connManager = adapter.connectionManager
    val clientOptions = ClientOptions().apply { queueMessages = false }

    every { adapter.clientOptions } returns clientOptions

    every { connManager.send(any(), any(), any()) } answers {
      val listener = thirdArg<Callback<PublishResult>>()
      listener.onSuccess(PublishResult(null))
    }

    val pm = ProtocolMessage(ProtocolMessage.Action.message)
    adapter.sendAsync(pm)
    verify(exactly = 1) { connManager.send(any(), false, any()) }

    clientOptions.queueMessages = true
    adapter.sendAsync(pm)
    verify(exactly = 1) { connManager.send(any(), true, any()) }
  }

  @Test
  fun testSendAsyncErrorPropagatesAblyException() = runTest {
    val adapter = getMockAblyClientAdapter()
    val connManager = adapter.connectionManager
    val clientOptions = ClientOptions()

    every { adapter.clientOptions } returns clientOptions

    every { connManager.send(any(), any(), any()) } answers {
      val listener = thirdArg<Callback<PublishResult>>()
      listener.onError(clientError("boom").errorInfo)
    }

    val ex = assertFailsWith<AblyException> {
      adapter.sendAsync(ProtocolMessage(ProtocolMessage.Action.message))
    }
    assertEquals(400, ex.errorInfo.statusCode)
    assertEquals(40000, ex.errorInfo.code)
  }

  @Test
  fun testOnGCGracePeriodImmediateInvokesBlock() {
    val adapter = getMockAblyClientAdapter()
    val connManager = adapter.connectionManager
    connManager.setPrivateField("objectsGCGracePeriod", 123L)

    var value: Long? = null
    adapter.onGCGracePeriodUpdated { v -> value = v }

    assertEquals(123L, value)
    verify(exactly = 1) { adapter.connection.on(ConnectionEvent.connected, any<ConnectionStateListener>()) }
  }

  @Test
  fun testOnGCGracePeriodDeferredInvokesOnConnectedWithValue() {
    val adapter = getMockAblyClientAdapter()
    val connManager = adapter.connectionManager
    val connection = adapter.connection

    var value: Long? = null
    every { connection.on(ConnectionEvent.connected, any<ConnectionStateListener>()) } answers {
      val listener = secondArg<ConnectionStateListener>()
      connManager.setPrivateField("objectsGCGracePeriod", 456L)
      listener.onConnectionStateChanged(mockk(relaxed = true))
    }

    adapter.onGCGracePeriodUpdated { v -> value = v }

    assertEquals(456L, value)
    verify(exactly = 1) { connection.on(ConnectionEvent.connected, any<ConnectionStateListener>()) }
  }

  @Test
  fun testOnGCGracePeriodDeferredInvokesOnConnectedWithNull() {
    val adapter = getMockAblyClientAdapter()
    val connection = adapter.connection

    var value: Long? = null
    every { connection.on(ConnectionEvent.connected, any<ConnectionStateListener>()) } answers {
      val listener = secondArg<ConnectionStateListener>()
      listener.onConnectionStateChanged(mockk(relaxed = true))
    }

    adapter.onGCGracePeriodUpdated { v -> value = v }

    assertNull(value)
    verify(exactly = 1) { connection.on(ConnectionEvent.connected, any<ConnectionStateListener>()) }
  }

  @Test
  fun testSendAsyncThrowsWhenConnectionManagerThrows() = runTest {
    val adapter = getMockAblyClientAdapter()
    val connManager = adapter.connectionManager
    val clientOptions = ClientOptions()

    every { adapter.clientOptions } returns clientOptions

    every { connManager.send(any(), any(), any()) } throws RuntimeException("send failed hard")

    val ex = assertFailsWith<RuntimeException> {
      adapter.sendAsync(ProtocolMessage(ProtocolMessage.Action.message))
    }
    assertEquals("send failed hard", ex.message)
  }

  // attachAsync
  @Test
  fun testAttachAsyncSuccess() = runTest {
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    every { channel.attach(any()) } answers {
      val listener = firstArg<CompletionListener>()
      listener.onSuccess()
    }

    adapter.attachAsync("ch")
    verify(exactly = 1) { channel.attach(any()) }
  }

  @Test
  fun testAttachAsyncError() = runTest {
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    every { channel.attach(any()) } answers {
      val listener = firstArg<CompletionListener>()
      listener.onError(serverError("attach failed").errorInfo)
    }

    val ex = assertFailsWith<AblyException> { adapter.attachAsync("ch") }
    assertEquals(50000, ex.errorInfo.code)
    assertEquals(500, ex.errorInfo.statusCode)
  }

  // getChannelModes
  @Test
  fun testGetChannelModesPrefersChannelModes() {
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    every { channel.modes } returns arrayOf(ChannelMode.object_publish)
    every { channel.options } returns ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe) }

    val modes = adapter.getChannelModes("ch")
    assertArrayEquals(arrayOf(ChannelMode.object_publish), modes)
  }

  @Test
  fun testGetChannelModesFallsBackToOptions() {
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    every { channel.modes } returns emptyArray()
    every { channel.options } returns ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe) }

    val modes = adapter.getChannelModes("ch")
    assertArrayEquals(arrayOf(ChannelMode.object_subscribe), modes)
  }

  @Test
  fun testGetChannelModesReturnsNullWhenNoModes() {
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    every { channel.modes } returns null
    every { channel.options } returns ChannelOptions().apply { modes = null }

    val modes = adapter.getChannelModes("ch")
    assertNull(modes)
  }

  @Test
  fun testGetChannelModesIgnoresEmptyModes() {
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    every { channel.modes } returns emptyArray()
    every { channel.options } returns ChannelOptions().apply { modes = null }

    val modes = adapter.getChannelModes("ch")
    assertNull(modes)
  }

  // setChannelSerial
  @Test
  fun testSetChannelSerialSetsWhenObjectActionAndNonEmpty() {
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    val props = ChannelProperties()
    channel.properties = props
    every { adapter.getChannel("ch") } returns channel

    val msg = ProtocolMessage(ProtocolMessage.Action.`object`)
    msg.channelSerial = "abc:123"

    adapter.setChannelSerial("ch", msg)
    assertEquals("abc:123", props.channelSerial)
  }

  @Test
  fun testSetChannelSerialNoOpForNonObjectActionOrEmpty() {
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    val props = ChannelProperties()
    channel.properties = props
    every { adapter.getChannel("ch") } returns channel

    // Non-object action
    val msg1 = ProtocolMessage(ProtocolMessage.Action.message)
    msg1.channelSerial = "abc"
    adapter.setChannelSerial("ch", msg1)
    assertNull(props.channelSerial)

    // Object action but empty serial
    val msg2 = ProtocolMessage(ProtocolMessage.Action.`object`)
    msg2.channelSerial = ""
    adapter.setChannelSerial("ch", msg2)
    assertNull(props.channelSerial)
  }

  // ensureAttached
  @Test
  fun testEnsureAttachedFromInitializedAttaches() = runTest {
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)

    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.initialized

    val attachCalled = slot<CompletionListener>()
    every { channel.attach(capture(attachCalled)) } answers {
      attachCalled.captured.onSuccess()
    }

    adapter.ensureAttached("ch")
    verify(exactly = 1) { channel.attach(any()) }
  }

  @Test
  fun testEnsureAttachedWhenAlreadyAttachedReturns() = runTest {
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.attached

    adapter.ensureAttached("ch")
    // no attach call
    verify(exactly = 0) { channel.attach(any()) }
  }

  @Test
  fun testEnsureAttachedWhenSuspendedReturns() = runTest {
    // RTL33a - SUSPENDED is already usable; no attach performed
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.suspended

    adapter.ensureAttached("ch")
    verify(exactly = 0) { channel.attach(any()) }
  }

  @Test
  fun testEnsureAttachedFromDetachedAttaches() = runTest {
    // RTL33b - DETACHED triggers an implicit attach
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.detached

    val attachCalled = slot<CompletionListener>()
    every { channel.attach(capture(attachCalled)) } answers {
      attachCalled.captured.onSuccess()
    }

    adapter.ensureAttached("ch")
    verify(exactly = 1) { channel.attach(any()) }
  }

  @Test
  fun testEnsureAttachedFromDetachingAttaches() = runTest {
    // RTL33b - DETACHING triggers an implicit attach
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.detaching

    val attachCalled = slot<CompletionListener>()
    every { channel.attach(capture(attachCalled)) } answers {
      attachCalled.captured.onSuccess()
    }

    adapter.ensureAttached("ch")
    verify(exactly = 1) { channel.attach(any()) }
  }

  @Test
  fun testEnsureAttachedFromAttachingAttaches() = runTest {
    // RTL33b - ATTACHING triggers attach; Channel#attach resolves the in-flight case (RTL4h)
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.attaching

    val attachCalled = slot<CompletionListener>()
    every { channel.attach(capture(attachCalled)) } answers {
      attachCalled.captured.onSuccess()
    }

    adapter.ensureAttached("ch")
    verify(exactly = 1) { channel.attach(any()) }
  }

  @Test
  fun testEnsureAttachedPropagatesAttachFailure() = runTest {
    // RTL33b1 - the ErrorInfo that failed the implicit attach is propagated unchanged
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.detached

    val attachCalled = slot<CompletionListener>()
    every { channel.attach(capture(attachCalled)) } answers {
      attachCalled.captured.onError(clientError("attach failed").errorInfo)
    }

    val ex = assertFailsWith<AblyException> { adapter.ensureAttached("ch") }
    // RTL33b1 - propagated unchanged: original code kept, not rewrapped as ChannelStateError (90001)
    assertEquals(ObjectErrorCode.BadRequest.code, ex.errorInfo.code)
    assertTrue(ex.errorInfo.message.contains("attach failed"))
    verify(exactly = 1) { channel.attach(any()) }
  }

  @Test
  fun testEnsureAttachedThrowsForFailedState() = runTest {
    // RTL33c - FAILED throws with code 90001 and statusCode 400
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.failed

    val ex = assertFailsWith<AblyException> { adapter.ensureAttached("ch") }
    assertEquals(ObjectErrorCode.ChannelStateError.code, ex.errorInfo.code)
    assertEquals(ObjectHttpStatusCode.BadRequest.code, ex.errorInfo.statusCode)
    verify(exactly = 0) { channel.attach(any()) }
  }

  // throwIfInvalidAccessApiConfiguration
  @Test
  fun testThrowIfInvalidAccessApiConfigurationStateDetached() {
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.detached

    val ex = assertFailsWith<AblyException> { adapter.throwIfInvalidAccessApiConfiguration("ch") }
    assertEquals(ObjectErrorCode.ChannelStateError.code, ex.errorInfo.code)
  }

  @Test
  fun testThrowIfInvalidAccessApiConfigurationMissingMode() {
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.attached
    every { channel.modes } returns emptyArray()
    every { channel.options } returns ChannelOptions().apply { modes = null }

    val ex = assertFailsWith<AblyException> { adapter.throwIfInvalidAccessApiConfiguration("ch") }
    assertEquals(ObjectErrorCode.ChannelModeRequired.code, ex.errorInfo.code)
    assertTrue(ex.errorInfo.message.contains("object_subscribe"))
  }

  // throwIfInvalidWriteApiConfiguration
  @Test
  fun testThrowIfInvalidWriteApiConfigurationEchoDisabled() {
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val clientOptions = ClientOptions().apply { echoMessages = false }
    every { adapter.clientOptions } returns clientOptions

    val ex = assertFailsWith<AblyException> { adapter.throwIfInvalidWriteApiConfiguration("ch") }
    assertEquals(ObjectErrorCode.BadRequest.code, ex.errorInfo.code)
    assertTrue(ex.errorInfo.message.contains("echoMessages"))
  }

  @Test
  fun testThrowIfInvalidWriteApiConfigurationInvalidState() {
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    every { adapter.clientOptions } returns ClientOptions()
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.suspended

    val ex = assertFailsWith<AblyException> { adapter.throwIfInvalidWriteApiConfiguration("ch") }
    assertEquals(ObjectErrorCode.ChannelStateError.code, ex.errorInfo.code)
  }

  @Test
  fun testThrowIfInvalidWriteApiConfigurationMissingMode() {
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    every { adapter.clientOptions } returns ClientOptions()
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.attached
    every { channel.modes } returns emptyArray()
    every { channel.options } returns ChannelOptions().apply { modes = null }

    val ex = assertFailsWith<AblyException> { adapter.throwIfInvalidWriteApiConfiguration("ch") }
    assertEquals(ObjectErrorCode.ChannelModeRequired.code, ex.errorInfo.code)
    assertTrue(ex.errorInfo.message.contains("object_publish"))
  }

  // throwIfUnpublishableState
  @Test
  fun testThrowIfUnpublishableStateInactiveConnection() {
    val adapter = getMockAblyClientAdapter()
    val connManager = adapter.connectionManager
    every { connManager.isActive } returns false
    every { connManager.stateErrorInfo } returns serverError("not active").errorInfo

    val ex = assertFailsWith<AblyException> { adapter.throwIfUnpublishableState("ch") }
    assertEquals(500, ex.errorInfo.statusCode)
    assertEquals(50000, ex.errorInfo.code)
  }

  @Test
  fun testThrowIfUnpublishableStateChannelFailed() {
    val adapter = getMockAblyClientAdapter()
    val connManager = adapter.connectionManager
    every { connManager.isActive } returns true
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.failed

    val ex = assertFailsWith<AblyException> { adapter.throwIfUnpublishableState("ch") }
    assertEquals(ObjectErrorCode.ChannelStateError.code, ex.errorInfo.code)
  }

  @Test
  fun testAccessConfigThrowsWhenRequiredModeMissing() {
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.attached
    // No modes anywhere
    every { channel.modes } returns null
    every { channel.options } returns ChannelOptions().apply { modes = null }

    val ex = assertFailsWith<AblyException> { adapter.throwIfInvalidAccessApiConfiguration("ch") }
    assertEquals(ObjectErrorCode.ChannelModeRequired.code, ex.errorInfo.code)
    assertTrue(ex.errorInfo.message.contains("object_subscribe"))
  }

  @Test
  fun testWriteConfigThrowsWhenRequiredModeMissing() {
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    every { adapter.clientOptions } returns ClientOptions() // echo enabled
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.attached
    every { channel.modes } returns emptyArray()
    every { channel.options } returns ChannelOptions().apply { modes = null }

    val ex = assertFailsWith<AblyException> { adapter.throwIfInvalidWriteApiConfiguration("ch") }
    assertEquals(ObjectErrorCode.ChannelModeRequired.code, ex.errorInfo.code)
    assertTrue(ex.errorInfo.message.contains("object_publish"))
  }

  @Test
  fun testAccessConfigThrowsOnInvalidChannelState() {
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.detached

    val ex = assertFailsWith<AblyException> { adapter.throwIfInvalidAccessApiConfiguration("ch") }
    assertEquals(ObjectErrorCode.ChannelStateError.code, ex.errorInfo.code)
  }

  @Test
  fun testWriteConfigThrowsOnInvalidChannelStates() {
    val adapter = mockk<AblyClientAdapter>(relaxed = true)
    every { adapter.clientOptions } returns ClientOptions()
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel

    // Suspended should be rejected for write config
    channel.state = ChannelState.suspended
    val ex1 = assertFailsWith<AblyException> { adapter.throwIfInvalidWriteApiConfiguration("ch") }
    assertEquals(ObjectErrorCode.ChannelStateError.code, ex1.errorInfo.code)

    // Failed should also be rejected
    channel.state = ChannelState.failed
    val ex2 = assertFailsWith<AblyException> { adapter.throwIfInvalidWriteApiConfiguration("ch") }
    assertEquals(ObjectErrorCode.ChannelStateError.code, ex2.errorInfo.code)
  }

}
