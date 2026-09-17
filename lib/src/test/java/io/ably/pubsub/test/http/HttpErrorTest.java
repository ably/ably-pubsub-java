package io.ably.pubsub.test.http;

import fi.iki.elonen.NanoHTTPD;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.util.Log;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import static org.junit.Assert.assertTrue;

public class HttpErrorTest extends ParameterizedTest {

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
     * Verify an href is logged when included in an error.href
     * Spec: TI5
     */
    @Test
    public void errorHrefWhenPresentInHref() {
        final Vector<String> logMessages = new Vector<String>();
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.environment = null;
            opts.tls = false;
            opts.port = server.getListeningPort();
            opts.restHost = "localhost";
            opts.logHandler = new Log.LogHandler() {
                @Override
                public void println(int severity, String tag, String msg, Throwable tr) {
                    logMessages.add(msg);
                }
            };
            PubSubHttpClient ably = new PubSubHttpClient(opts);

            /* make a call that will generate an error */
            ably.stats(new Param[]{new Param("message", "Test message"), new Param("href", href(12345))});
        } catch (AblyException e) {
            /* verify that the expected error message is present */
            assertTrue(logMessages.get(0).contains(href(12345)));
        }
    }

    /**
     * Verify an href is logged when included in an error.message
     * Spec: TI5
     */
    @Test
    public void errorHrefWhenPresentInMessage() {
        final Vector<String> logMessages = new Vector<String>();
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.environment = null;
            opts.tls = false;
            opts.port = server.getListeningPort();
            opts.restHost = "localhost";
            opts.logHandler = new Log.LogHandler() {
                @Override
                public void println(int severity, String tag, String msg, Throwable tr) {
                    logMessages.add(msg);
                }
            };
            PubSubHttpClient ably = new PubSubHttpClient(opts);

            /* make a call that will generate an error */
            ably.stats(new Param[]{new Param("message", "Test message. See " + href(12345))});
        } catch (AblyException e) {
            /* verify that the expected error message is present */
            assertTrue(logMessages.get(0).contains(href(12345)));
        }
    }

    /**
     * Verify an href is logged when derived from error.code
     * Spec: TI5
     */
    @Test
    public void errorHrefWhenCodePresent() {
        final Vector<String> logMessages = new Vector<String>();
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.environment = null;
            opts.tls = false;
            opts.port = server.getListeningPort();
            opts.restHost = "localhost";
            opts.logHandler = new Log.LogHandler() {
                @Override
                public void println(int severity, String tag, String msg, Throwable tr) {
                    logMessages.add(msg);
                }
            };
            PubSubHttpClient ably = new PubSubHttpClient(opts);

            /* make a call that will generate an error */
            ably.stats(new Param[]{new Param("message", "Test message"), new Param("code", "12345")});
        } catch (AblyException e) {
            /* verify that the expected error message is present */
            assertTrue(logMessages.get(0).contains(href(12345)));
        }
    }

    private static String href(int code) { return HREF_BASE + code; }
    private static final String HREF_BASE = "https://help.ably.io/error/";

    private static class SessionHandlerNanoHTTPD extends NanoHTTPD {
        Map<String, String> requestHeaders;
        Map<String, String> requestParams;

        SessionHandlerNanoHTTPD(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            requestHeaders = new HashMap<>(session.getHeaders());
            requestParams = new HashMap<>(session.getParms());
            String code = requestParams.get("code"),
                    href = requestParams.get("href"),
                    message = requestParams.get("message");

            StringBuilder responseBody = new StringBuilder().append("{\"error\":{");
            responseBody.append("\"message\":\"").append(message).append("\"");
            if(code != null) {
                responseBody.append(",\"code\":\"").append(code).append("\"");
            }
            if(href != null) {
                responseBody.append(",\"href\":\"").append(href).append("\"");
            }
            responseBody.append("}}");
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", responseBody.toString());
        }
    }
}
