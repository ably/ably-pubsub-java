package io.ably.pubsub.server;

import com.sun.net.httpserver.HttpServer;
import io.ably.pubsub.realtime.PubSubRealtimeClient;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.util.Side;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The agent entries asserted here are what the platform reads to classify traffic (and, on
 * MAU-priced accounts, what earns the server exemption), so these tests are deliberately
 * strict: if one fails, billing classification is broken, not just a header.
 * <p>
 * The side entry is a versionless flag — a bare token on the wire, registered as such in the ably-common agents registry
 * — so the assertions also fail if a version (or any {@code /suffix}) reappears on it.
 */
public class PubSubServerTest {

    private static final String FAKE_KEY = "fakeAppId.fakeKeyId:fakeKeySecret";
    private static final String FAKE_TOKEN = "fakeTokenString";

    /**
     * The stamped entry is present as a versionless flag, and the other side's is absent.
     */
    private static void assertServerFlag(Map<String, String> agents) {
        assertTrue("expected the server side flag", agents.containsKey(Side.SERVER_AGENT_IDENTIFIER));
        assertNull("the side flag is versionless", agents.get(Side.SERVER_AGENT_IDENTIFIER));
        assertFalse("a server client must not carry the device entry",
            agents.containsKey(Side.DEVICE_AGENT_IDENTIFIER));
    }

    @Test
    public void httpClient_stampsServerAgent() throws AblyException {
        PubSubHttpClient client = PubSubServer.httpClientBuilder().key(FAKE_KEY).build();
        assertServerFlag(client.options.agents);
    }

    @Test
    public void realtimeClient_stampsServerAgent() throws AblyException {
        PubSubRealtimeClient client = PubSubServer
            .realtimeClientBuilder()
            .key(FAKE_KEY)
            .autoConnect(false)
            .build();
        assertServerFlag(client.options.agents);
    }

    @Test
    public void key_isCarriedAsKeyAlone() throws AblyException {
        PubSubHttpClient client = PubSubServer.httpClientBuilder().key(FAKE_KEY).build();
        assertEquals(FAKE_KEY, client.options.key);
        assertNull(client.options.token);
        assertServerFlag(client.options.agents);
    }

    @Test
    public void token_isCarriedAsTokenAlone() throws AblyException {
        PubSubHttpClient client = PubSubServer.httpClientBuilder().token(FAKE_TOKEN).build();
        assertEquals(FAKE_TOKEN, client.options.token);
        assertNull(client.options.key);
        assertServerFlag(client.options.agents);
    }

    @Test
    public void callerAgentEntries_arePreserved() throws AblyException {
        Map<String, String> agents = new HashMap<>();
        agents.put("some-sdk", "1.2.3");
        PubSubHttpClient client = PubSubServer
            .httpClientBuilder()
            .key(FAKE_KEY)
            .agents(agents)
            .build();
        assertEquals("1.2.3", client.options.agents.get("some-sdk"));
        assertServerFlag(client.options.agents);
    }

    @Test
    public void callerCannotOverrideTheSideEntry() throws AblyException {
        Map<String, String> agents = new HashMap<>();
        agents.put(Side.SERVER_AGENT_IDENTIFIER, "not-the-real-form");
        PubSubHttpClient client = PubSubServer
            .httpClientBuilder()
            .key(FAKE_KEY)
            .agents(agents)
            .build();
        // The stamp replaces the caller's value: the flag is present and back to versionless.
        assertServerFlag(client.options.agents);
    }

    @Test
    public void callersAgentsMap_isNotMutated() throws AblyException {
        Map<String, String> callerAgents = new HashMap<>();
        callerAgents.put("some-sdk", "1.2.3");
        PubSubHttpClient client = PubSubServer
            .httpClientBuilder()
            .key(FAKE_KEY)
            .agents(callerAgents)
            .build();
        assertNotSame("the stamp must go into a copy, not the caller's map",
            callerAgents, client.options.agents);
        assertEquals(1, callerAgents.size());
        assertFalse(callerAgents.containsKey(Side.SERVER_AGENT_IDENTIFIER));
    }

    @Test
    public void noAuthParameters_getTheCoreConstructorsOwnError() {
        try {
            PubSubServer.httpClientBuilder().build();
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
            PubSubHttpClient client = PubSubServer
                .httpClientBuilder()
                .key(FAKE_KEY)
                .tls(false)
                .endpoint("127.0.0.1")
                .port(httpServer.getAddress().getPort())
                .build();
            client.time();

            String agentHeader = observedAgentHeader.get();
            assertNotNull("no Ably-Agent header observed", agentHeader);
            List<String> tokens = Arrays.asList(agentHeader.split(" "));
            // The flag must be present as a bare token: `name/anything` means the
            // versionless stamp regressed (the registry entry is versionless).
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

    @Test
    public void sideFlags_arePinnedToTheirWireLiterals() throws AblyException {
        // Literal strings, not the constants: the platform classifies traffic by these exact tokens.
        PubSubHttpClient client = PubSubServer.httpClientBuilder().key(FAKE_KEY).build();
        assertTrue(client.options.agents.containsKey("ably-pubsub-server"));
        assertFalse(client.options.agents.containsKey("ably-pubsub-device"));
    }

    @Test
    public void callerSuppliedDeviceEntry_isStripped() throws AblyException {
        Map<String, String> agents = new HashMap<>();
        agents.put("ably-pubsub-device", null);
        agents.put("some-sdk", "1.2.3");
        PubSubHttpClient http = PubSubServer.httpClientBuilder().key(FAKE_KEY).agents(agents).build();
        PubSubRealtimeClient realtime = PubSubServer
            .realtimeClientBuilder().key(FAKE_KEY).autoConnect(false).agents(agents).build();
        for (Map<String, String> built : Arrays.asList(http.options.agents, realtime.options.agents)) {
            assertFalse(built.containsKey("ably-pubsub-device"));
            assertTrue(built.containsKey("ably-pubsub-server"));
            assertEquals("1.2.3", built.get("some-sdk"));
        }
        assertTrue("the caller's map is not modified", agents.containsKey("ably-pubsub-device"));
    }

    @Test
    public void reusingABuilder_doesNotAffectAnEarlierClient() throws AblyException {
        PubSubServer.HttpClientBuilder builder = PubSubServer.httpClientBuilder().key(FAKE_KEY);
        PubSubHttpClient first = builder.build();

        Map<String, String> later = new HashMap<>();
        later.put("later-sdk", "9");
        builder.key("other.key:secret").agents(later).tls(false);
        PubSubHttpClient second = builder.build();

        assertEquals(FAKE_KEY, first.options.key);
        assertTrue(first.options.tls);
        assertFalse(first.options.agents.containsKey("later-sdk"));
        assertEquals("other.key:secret", second.options.key);
        assertTrue(second.options.agents.containsKey("later-sdk"));
    }

    @Test
    public void changingPassedOptionsAfterBuild_doesNotAffectTheClient() throws Exception {
        ClientOptions options = new ClientOptions(FAKE_KEY);
        options.autoConnect = false;
        PubSubRealtimeClient client = PubSubServer.realtimeClientBuilder(options).build();

        options.clientId = "changed";
        options.agents = new HashMap<>();

        assertNotSame(options, client.options);
        assertNull(client.options.clientId);
        assertServerFlag(client.options.agents);
    }

    /**
     * Wire-level assertion for the realtime door: the {@code agent} query parameter of the WebSocket
     * upgrade request carries the base identifier and the server flag, and not the device flag.
     */
    @Test
    public void webSocketConnections_carryTheServerAgentParamOnTheWire() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            AtomicReference<String> requestLine = new AtomicReference<>();
            CountDownLatch seen = new CountDownLatch(1);
            Thread acceptor = new Thread(() -> {
                try (Socket socket = listener.accept()) {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    requestLine.set(reader.readLine());
                } catch (Exception ignored) {
                    // the test fails on the missing request line
                } finally {
                    seen.countDown();
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            PubSubRealtimeClient client = PubSubServer
                .realtimeClientBuilder()
                .key(FAKE_KEY)
                .tls(false)
                .endpoint("127.0.0.1")
                .port(listener.getLocalPort())
                .build();
            try {
                assertTrue("no WebSocket upgrade request seen", seen.await(15, TimeUnit.SECONDS));
            } finally {
                client.close();
            }

            String line = requestLine.get();
            assertNotNull("no request line observed", line);
            String agent = null;
            String query = line.contains("?") ? line.substring(line.indexOf('?') + 1, line.lastIndexOf(' ')) : "";
            for (String pair : query.split("&")) {
                if (pair.startsWith("agent=")) {
                    agent = URLDecoder.decode(pair.substring("agent=".length()), "UTF-8");
                }
            }
            assertNotNull("no agent query param in: " + line, agent);
            assertTrue("missing core base identifier in: " + agent, agent.contains("ably-pubsub-java/"));
            assertTrue("missing bare server flag in: " + agent,
                Arrays.asList(agent.split(" ")).contains("ably-pubsub-server"));
            assertFalse("must not carry the device flag: " + agent, agent.contains("ably-pubsub-device"));
        }
    }
}
