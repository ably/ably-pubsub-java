package io.ably.pubsub.device

import io.ably.lib.util.Side
import org.junit.Assert
import org.junit.Test

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
