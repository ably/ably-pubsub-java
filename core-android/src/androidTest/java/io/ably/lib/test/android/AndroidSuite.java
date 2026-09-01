package io.ably.lib.test.android;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.test.common.Setup;
import io.ably.lib.transport.Defaults;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;

/**
 * Tests specific for Android
 */
public class AndroidSuite {
    private static SessionHandlerNanoHTTPD server;

    @BeforeClass
    public static void setUp() throws IOException {
        /* Create custom RouterNanoHTTPD class for getting session object */
        server = new SessionHandlerNanoHTTPD(27333);
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);

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

    @Test
    public void android_http_header_test() {
        try {
            /* Init values for local server */
            Setup.TestVars testVars = Setup.getTestVars();
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            opts.tls = false;
            opts.port = server.getListeningPort();
            opts.restHost = "localhost";
            AblyRest ably = new AblyRest(opts);

            ably.time();

            Map<String, String> headers = server.getHeaders();

            assertNotNull("Verify ably server was reached", headers);
            String header = headers.get(Defaults.ABLY_AGENT_HEADER.toLowerCase(Locale.ROOT));
            assertTrue("Verify correct library header was passed to the server", header != null && header.startsWith("android"));
        }
        catch (AblyException e) {
            e.printStackTrace();
            fail();
        }

    }

    private static class SessionHandlerNanoHTTPD extends RouterNanoHTTPD {
        public Map<String, String> headers;

        public SessionHandlerNanoHTTPD(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            headers = new HashMap<>(session.getHeaders());
            return newFixedLengthResponse(String.format(Locale.US, "[%d]", System.currentTimeMillis()));
        }

        public Map<String, String> getHeaders() {
            return headers;
        }
    }

}
