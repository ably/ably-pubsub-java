package io.ably.pubsub.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.util.Side;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * The agent entries asserted here are what the platform reads to classify traffic on
 * MAU-priced accounts, so these tests are deliberately strict: if one fails, billing
 * classification is broken, not just a header.
 * <p>
 * The side entry is a versionless flag — a bare token on the wire, registered as such in the ably-common agents registry
 * — so the assertions also fail if a version (or any {@code /suffix}) reappears on it.
 */
public class PubSubDeviceTest {

    private static final String FAKE_KEY = "fakeAppId.fakeKeyId:fakeKeySecret";

    private static ClientOptions offlineOptions(String key) throws Exception {
        ClientOptions options = new ClientOptions(key);
        options.autoConnect = false;
        return options;
    }

    /** The stamped entry is present as a versionless flag, and the other side's is absent. */
    private static void assertDeviceFlag(Map<String, String> agents) {
        assertTrue("expected the device side flag", agents.containsKey(Side.DEVICE_AGENT_IDENTIFIER));
        assertNull("the side flag is versionless", agents.get(Side.DEVICE_AGENT_IDENTIFIER));
        assertFalse("a device client must not carry the server entry",
            agents.containsKey(Side.SERVER_AGENT_IDENTIFIER));
    }

    @Test
    public void client_stampsDeviceAgent() throws Exception {
        AblyRealtime client = PubSubDevice.clientBuilder(offlineOptions(FAKE_KEY)).build();
        assertDeviceFlag(client.options.agents);
    }

    @Test
    public void key_isCarriedThroughToTheClient() throws Exception {
        ClientOptions builtOptions = PubSubDevice.clientBuilder()
            .key(FAKE_KEY)
            .autoConnect(false)
            .build()
            .options;
        assertEquals(FAKE_KEY, builtOptions.key);
        assertNull(builtOptions.token);
        assertDeviceFlag(builtOptions.agents);
    }

    @Test
    public void callerAgentEntries_arePreserved_andCannotOverrideTheSideEntry() throws Exception {
        ClientOptions options = offlineOptions(FAKE_KEY);
        Map<String, String> callerAgents = new HashMap<>();
        callerAgents.put("some-sdk", "1.2.3");
        callerAgents.put(Side.DEVICE_AGENT_IDENTIFIER, "not-the-real-form");
        options.agents = callerAgents;

        AblyRealtime client = PubSubDevice.clientBuilder(options).build();
        assertEquals("1.2.3", client.options.agents.get("some-sdk"));
        // The stamp replaces the caller's value: the flag is present and back to versionless.
        assertDeviceFlag(client.options.agents);

        // the caller's own map is untouched: the stamp goes into a fresh one
        assertNotSame("the stamp must go into a copy, not the caller's map",
            callerAgents, client.options.agents);
        assertEquals("not-the-real-form", callerAgents.get(Side.DEVICE_AGENT_IDENTIFIER));
    }
}
