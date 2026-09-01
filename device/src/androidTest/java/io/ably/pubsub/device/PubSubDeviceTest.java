package io.ably.pubsub.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.types.ClientOptions;
import io.ably.pubsub.internal.Side;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * The agent entries asserted here are what the platform reads to classify traffic on
 * MAU-priced accounts, so these tests are deliberately strict: if one fails, billing
 * classification is broken, not just a header.
 */
public class PubSubDeviceTest {

    private static final String FAKE_KEY = "fakeAppId.fakeKeyId:fakeKeySecret";

    private static ClientOptions offlineOptions(String key) throws Exception {
        ClientOptions options = new ClientOptions(key);
        options.autoConnect = false;
        return options;
    }

    @Test
    public void client_stampsDeviceAgent() throws Exception {
        AblyRealtime client = PubSubDevice.clientBuilder(offlineOptions(FAKE_KEY)).build();
        assertEquals(BuildConfig.VERSION, client.options.agents.get(Side.DEVICE_AGENT_IDENTIFIER));
    }

    @Test
    public void keyString_isAcceptedAndDisambiguatedAsKey() throws Exception {
        ClientOptions builtOptions = PubSubDevice.clientBuilder(FAKE_KEY).build().options;
        assertEquals(FAKE_KEY, builtOptions.key);
        assertNull(builtOptions.token);
        assertEquals(BuildConfig.VERSION, builtOptions.agents.get(Side.DEVICE_AGENT_IDENTIFIER));
    }

    @Test
    public void callerAgentEntries_arePreserved_andCannotOverrideTheSideEntry() throws Exception {
        ClientOptions options = offlineOptions(FAKE_KEY);
        Map<String, String> callerAgents = new HashMap<>();
        callerAgents.put("some-sdk", "1.2.3");
        callerAgents.put(Side.DEVICE_AGENT_IDENTIFIER, "not-the-real-version");
        options.agents = callerAgents;

        AblyRealtime client = PubSubDevice.clientBuilder(options).build();
        assertEquals("1.2.3", client.options.agents.get("some-sdk"));
        assertEquals(BuildConfig.VERSION, client.options.agents.get(Side.DEVICE_AGENT_IDENTIFIER));

        // the caller's own map is untouched
        assertTrue(options.agents == callerAgents);
        assertEquals("not-the-real-version", callerAgents.get(Side.DEVICE_AGENT_IDENTIFIER));
        assertFalse(callerAgents.containsValue(BuildConfig.VERSION));
    }
}
