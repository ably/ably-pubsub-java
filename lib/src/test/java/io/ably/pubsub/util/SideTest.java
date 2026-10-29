package io.ably.pubsub.util;

import io.ably.pubsub.types.ClientOptions;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SideTest {

    @Test
    public void identifiers_arePinnedToTheirWireLiterals() {
        // Literal strings on purpose: the platform classifies traffic by these exact tokens.
        assertEquals("ably-pubsub-server", Side.SERVER_AGENT_IDENTIFIER);
        assertEquals("ably-pubsub-device", Side.DEVICE_AGENT_IDENTIFIER);
    }

    @Test
    public void injectSideAgent_removesTheOppositeSideAndKeepsOtherEntries() {
        ClientOptions options = new ClientOptions();
        options.agents = new HashMap<>();
        options.agents.put("ably-pubsub-server", "x");
        options.agents.put("some-sdk", "1.2.3");

        Side.injectSideAgent(options, Side.DEVICE_AGENT_IDENTIFIER);

        assertTrue(options.agents.containsKey("ably-pubsub-device"));
        assertNull(options.agents.get("ably-pubsub-device"));
        assertFalse(options.agents.containsKey("ably-pubsub-server"));
        assertEquals("1.2.3", options.agents.get("some-sdk"));
    }

    @Test
    public void injectSideAgent_removesTheDeviceEntryOnAServerClient() {
        ClientOptions options = new ClientOptions();
        Map<String, String> callerAgents = new HashMap<>();
        callerAgents.put("ably-pubsub-device", null);
        options.agents = callerAgents;

        Side.injectSideAgent(options, Side.SERVER_AGENT_IDENTIFIER);

        assertTrue(options.agents.containsKey("ably-pubsub-server"));
        assertFalse(options.agents.containsKey("ably-pubsub-device"));
        assertTrue("the caller's own map is not modified", callerAgents.containsKey("ably-pubsub-device"));
    }
}
