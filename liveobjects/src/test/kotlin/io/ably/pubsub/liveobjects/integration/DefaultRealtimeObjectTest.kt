package io.ably.pubsub.liveobjects.integration

import io.ably.pubsub.liveobjects.assertWaiter
import io.ably.pubsub.liveobjects.integration.setup.IntegrationTest
import io.ably.pubsub.realtime.ChannelState
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Basic integration tests for the path-based LiveObjects implementation.
 *
 * These exercise the sandbox setup/teardown (see [IntegrationTest]) together with the
 * realtime connection and channel lifecycle against a real sandbox app. The path-based
 * public Objects API (`channel.object`) is not yet wired to the plugin on this branch -
 * it currently resolves to the `RealtimeObject.Unavailable` null-object guard - so these
 * tests assert connectivity and the always-present `object` accessor rather than object
 * functionality. Functional object tests will be added as the implementation lands.
 */
class DefaultRealtimeObjectTest : IntegrationTest() {

  @Test
  fun testRealtimeChannelAttachesOnSandbox() = runTest {
    val channelName = generateChannelName()
    val channel = getRealtimeChannel(channelName)
    assertNotNull(channel)

    channel.attach()
    assertWaiter { channel.state == ChannelState.attached }
    assertEquals(ChannelState.attached, channel.state)
  }

  @Test
  fun testRealtimeChannelExposesObjectAccessor() = runTest {
    val channelName = generateChannelName()
    val channel = getRealtimeChannel(channelName)
    // `channel.object` is always non-null (null-object guard) even without the plugin installed.
    assertNotNull(channel.`object`)
  }
}
