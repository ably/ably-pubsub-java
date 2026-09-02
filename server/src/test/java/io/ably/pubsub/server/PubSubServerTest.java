package io.ably.pubsub.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.sun.net.httpserver.HttpServer;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.pubsub.internal.Side;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * The agent entries asserted here are what the platform reads to classify traffic (and, on
 * MAU-priced accounts, what earns the server exemption), so these tests are deliberately
 * strict: if one fails, billing classification is broken, not just a header.
 * <p>
 * The side entry is a versionless flag — a bare token on the wire, per ably/ably-common#361
 * — so the assertions also fail if a version (or any {@code /suffix}) reappears on it.
 */
public class PubSubServerTest {

    private static final String FAKE_KEY = "fakeAppId.fakeKeyId:fakeKeySecret";
    private static final String FAKE_TOKEN = "fakeTokenString";

    private static ClientOptions offlineOptions(String key) throws AblyException {
        ClientOptions options = new ClientOptions(key);
        options.autoConnect = false;
        return options;
    }

    /** The stamped entry is present as a versionless flag, and the other side's is absent. */
    private static void assertServerFlag(Map<String, String> agents) {
        assertTrue("expected the server side flag", agents.containsKey(Side.SERVER_AGENT_IDENTIFIER));
        assertNull("the side flag is versionless", agents.get(Side.SERVER_AGENT_IDENTIFIER));
        assertFalse("a server client must not carry the device entry",
            agents.containsKey(Side.DEVICE_AGENT_IDENTIFIER));
    }

    @Test
    public void httpClient_stampsServerAgent() throws AblyException {
        AblyRest client = PubSubServer.httpClientBuilder(offlineOptions(FAKE_KEY)).build();
        assertServerFlag(client.options.agents);
    }

    @Test
    public void realtimeClient_stampsServerAgent() throws AblyException {
        AblyRealtime client = PubSubServer.realtimeClientBuilder(offlineOptions(FAKE_KEY)).build();
        assertServerFlag(client.options.agents);
    }

    @Test
    public void keyString_isAcceptedAndDisambiguatedAsKey() throws AblyException {
        AblyRest client = PubSubServer.httpClientBuilder(FAKE_KEY).build();
        assertEquals(FAKE_KEY, client.options.key);
        assertNull(client.options.token);
        assertServerFlag(client.options.agents);
    }

    @Test
    public void tokenString_isAcceptedAndDisambiguatedAsToken() throws AblyException {
        AblyRest client = PubSubServer.httpClientBuilder(FAKE_TOKEN).build();
        assertEquals(FAKE_TOKEN, client.options.token);
        assertNull(client.options.key);
        assertServerFlag(client.options.agents);
    }

    @Test
    public void callerAgentEntries_arePreserved() throws AblyException {
        ClientOptions options = offlineOptions(FAKE_KEY);
        options.agents = new HashMap<>();
        options.agents.put("some-sdk", "1.2.3");
        AblyRest client = PubSubServer.httpClientBuilder(options).build();
        assertEquals("1.2.3", client.options.agents.get("some-sdk"));
        assertServerFlag(client.options.agents);
    }

    @Test
    public void callerCannotOverrideTheSideEntry() throws AblyException {
        ClientOptions options = offlineOptions(FAKE_KEY);
        options.agents = new HashMap<>();
        options.agents.put(Side.SERVER_AGENT_IDENTIFIER, "not-the-real-form");
        AblyRest client = PubSubServer.httpClientBuilder(options).build();
        // The stamp replaces the caller's value: the flag is present and back to versionless.
        assertServerFlag(client.options.agents);
    }

    @Test
    public void callersOptionsObject_isNotMutated() throws AblyException {
        ClientOptions options = offlineOptions(FAKE_KEY);
        Map<String, String> callerAgents = new HashMap<>();
        callerAgents.put("some-sdk", "1.2.3");
        options.agents = callerAgents;
        PubSubServer.httpClientBuilder(options).build();
        assertTrue(options.agents == callerAgents);
        assertEquals(1, callerAgents.size());
        assertFalse(callerAgents.containsKey(Side.SERVER_AGENT_IDENTIFIER));
    }

    @Test
    public void nullOptions_getTheCoreConstructorsOwnError() {
        try {
            PubSubServer.httpClientBuilder((ClientOptions) null).build();
            fail("expected the core's initialization error");
        } catch (AblyException e) {
            assertEquals(40000, e.errorInfo.code);
        }
    }

    /**
     * Wire-level assertion: the Ably-Agent header actually sent over HTTP carries the
     * side-declaring flag as a bare token alongside the core's base identifier. This is the
     * value billing classification reads.
     */
    @Test
    public void httpRequests_carryTheServerAgentHeaderOnTheWire() throws Exception {
        AtomicReference<String> observedAgentHeader = new AtomicReference<>();
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/time", exchange -> {
            observedAgentHeader.set(exchange.getRequestHeaders().getFirst("Ably-Agent"));
            byte[] body = "[1234567890000]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        httpServer.start();
        try {
            ClientOptions options = offlineOptions(FAKE_KEY);
            options.tls = false;
            options.restHost = "127.0.0.1";
            options.port = httpServer.getAddress().getPort();
            AblyRest client = PubSubServer.httpClientBuilder(options).build();
            client.time();

            String agentHeader = observedAgentHeader.get();
            assertNotNull("no Ably-Agent header observed", agentHeader);
            List<String> tokens = Arrays.asList(agentHeader.split(" "));
            // The flag must be present as a bare token: `name/anything` means the
            // versionless stamp regressed (see ably/ably-common#361).
            assertTrue("missing bare side flag in: " + agentHeader,
                tokens.contains(Side.SERVER_AGENT_IDENTIFIER));
            assertFalse("side flag must be versionless in: " + agentHeader,
                agentHeader.contains(Side.SERVER_AGENT_IDENTIFIER + "/"));
            assertTrue("missing core base identifier in: " + agentHeader,
                agentHeader.contains("ably-pubsub-java/"));
        } finally {
            httpServer.stop(0);
        }
    }
}
