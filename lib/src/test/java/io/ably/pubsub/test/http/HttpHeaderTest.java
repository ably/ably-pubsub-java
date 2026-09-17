package io.ably.pubsub.test.http;

import fi.iki.elonen.NanoHTTPD;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.http.Channel;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static io.ably.pubsub.transport.Defaults.ABLY_AGENT_VERSION;

/**
 * Created by VOstopolets on 8/17/16.
 */
public class HttpHeaderTest extends ParameterizedTest {

    private static SessionHandlerNanoHTTPD server;

    @BeforeClass
    public static void setUp() throws IOException {
        /* Create custom RouterNanoHTTPD class for getting session object */
        server = new SessionHandlerNanoHTTPD(27331);
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);

        /* wait for server to start */
        while (!server.wasStarted()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @AfterClass
    public static void tearDown() {
        server.stop();
    }

    /**
     * The header Ably-Agent: [lib]/[version]
     * should be included in all REST requests to the Ably endpoint
     * see {@link io.ably.pubsub.transport.Defaults#ABLY_AGENT_PARAM}
     * <p>
     * Spec: RSC7d, G4, RSA7e2
     * </p>
     */
    @Test
    public void header_lib_channel_publish() {
        try {
            /* Init values for local server */
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.environment = null;
            opts.tls = false;
            opts.port = server.getListeningPort();
            opts.restHost = "localhost";
            PubSubHttpClient ably = new PubSubHttpClient(opts);

            /* Publish message */
            String messageName = "test message";
            String messageData = String.valueOf(System.currentTimeMillis());

            Channel channel = ably.channels.get("test");
            channel.publish(messageName, messageData);

            /* Get last headers */
            Map<String, String> headers = server.getHeaders();
            String expectedAblyAgentHeader = ABLY_AGENT_VERSION + " jre/" + System.getProperty("java.version");

            /* Check header
             * This test should not directly validate version against Defaults.ABLY_VERSION, Defaults.ABLY_LIB_VERSION,
             * Defaults.ABLY_VERSION_HEADER, nor Defaults.ABLY_LIB_HEADER, as ultimately these headers have been derived
             * from those values.
             */
            Assert.assertNotNull("Expected headers", headers);
            Assert.assertEquals(headers.get("x-ably-version"), "6");
            Assert.assertEquals(headers.get("ably-agent"), expectedAblyAgentHeader);
            // RSA7e2
            Assert.assertNull("Shouldn't include 'x-ably-clientid' if `clientId` is not specified", headers.get("x-ably-clientid"));
        } catch (AblyException e) {
            e.printStackTrace();
            Assert.fail("header_lib_channel_publish: Unexpected exception");
        }
    }

    /**
     * The header `X-Ably-ClientId`
     * should be included in all REST requests to the Ably endpoint
     * if {@link ClientOptions#clientId} is specified
     * <p>
     * Spec: RSA7e2
     * </p>
     */
    @Test
    public void header_client_id_on_channel_publish() {
        try {
            /* Init values for local server */
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.environment = null;
            opts.tls = false;
            opts.port = server.getListeningPort();
            opts.restHost = "localhost";
            opts.clientId = "test client";
            PubSubHttpClient ably = new PubSubHttpClient(opts);

            /* Publish message */
            String messageName = "test message";
            String messageData = String.valueOf(System.currentTimeMillis());

            Channel channel = ably.channels.get("test");
            channel.publish(messageName, messageData);

            /* Get last headers */
            Map<String, String> headers = server.getHeaders();
            Assert.assertEquals(headers.get("x-ably-clientid"), /* Base64Coder.encodeString("test client") */ "dGVzdCBjbGllbnQ=");
        } catch (AblyException e) {
            e.printStackTrace();
            Assert.fail("header_client_id_on_channel_publish: Unexpected exception");
        }
    }

    private static class SessionHandlerNanoHTTPD extends NanoHTTPD {
        Map<String, String> requestHeaders;

        SessionHandlerNanoHTTPD(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            requestHeaders = new HashMap<>(session.getHeaders());
            int contentLength = Integer.parseInt(requestHeaders.get("content-length"));
            try {
                session.getInputStream().read(new byte[contentLength]);
            } catch (IOException e) {}
            return newFixedLengthResponse("Ignored response");
        }

        public Map<String, String> getHeaders() {
            return requestHeaders;
        }
    }
}
