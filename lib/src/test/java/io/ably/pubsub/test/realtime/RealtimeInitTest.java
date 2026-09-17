package io.ably.pubsub.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import io.ably.pubsub.realtime.RealtimeClient;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.transport.Defaults;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.util.Log;
import io.ably.pubsub.util.Log.LogHandler;

public class RealtimeInitTest extends ParameterizedTest {

    /**
     * Init library with a key only
     */
    @Test
    public void init_key_string() {
        RealtimeClient ably = null;
        try {
            ably = new RealtimeClient(testVars.keys[0].keyStr);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null) ably.close();
        }
    }

    /**
     * Init library with a key in options
     */
    @Test
    public void init_key_opts() {
        RealtimeClient ably = null;
        try {
            ably = new RealtimeClient(new ClientOptions(testVars.keys[0].keyStr));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init1: Unexpected exception instantiating library");
        } finally {
            if(ably != null) ably.close();
        }
    }

    /**
     * Init library with key string
     */
    @Test
    public void init_key() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            ably = new RealtimeClient(opts);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init2: Unexpected exception instantiating library");
        } finally {
            if(ably != null) ably.close();
        }
    }

    /**
     * Init library with specified host
     */
    @Test
    public void init_host() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            String hostExpected = "some.other.host";
            opts.restHost = hostExpected;
            ably = new RealtimeClient(opts);
            assertEquals("Unexpected host mismatch", hostExpected, ably.httpCore.getPrimaryHost());
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init4: Unexpected exception instantiating library");
        } finally {
            if(ably != null) ably.close();
        }
    }

    /**
     * Init library with specified port
     */
    @Test
    public void init_port() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            opts.port = 9998;
            opts.tlsPort = 9999;
            ably = new RealtimeClient(opts);
            assertEquals("Unexpected port mismatch", Defaults.getPort(opts), opts.tlsPort);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init5: Unexpected exception instantiating library");
        } finally {
            if(ably != null) ably.close();
        }
    }

    /**
     * Verify encrypted defaults to true
     */
    @Test
    public void init_default_secure() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            ably = new RealtimeClient(opts);
            assertEquals("Unexpected port mismatch", Defaults.getPort(opts), Defaults.TLS_PORT);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init6: Unexpected exception instantiating library");
        } finally {
            if(ably != null) ably.close();
        }
    }

    /**
     * Verify encrypted can be set to false
     */
    @Test
    public void init_insecure() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            opts.tls = false;
            ably = new RealtimeClient(opts);
            assertEquals("Unexpected scheme mismatch", Defaults.getPort(opts), Defaults.PORT);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init7: Unexpected exception instantiating library");
        } finally {
            if(ably != null) ably.close();
        }
    }

    /**
     * Init with log handler; check called
     */
    private boolean init8_logCalled;
    @Test
    public void init_log_handler() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            opts.logHandler = new LogHandler() {
                @Override
                public void println(int severity, String tag, String msg, Throwable tr) {
                    init8_logCalled = true;
                    System.out.println(msg);
                }
            };
            opts.logLevel = Log.VERBOSE;
            ably = new RealtimeClient(opts);
            assertTrue("Log handler not called", init8_logCalled);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init8: Unexpected exception instantiating library");
        } finally {
            if(ably != null) ably.close();
        }
    }

    /**
     * Init with log handler; check not called if logLevel == NONE
     */
    private boolean init9_logCalled;
    @Test
    public void init_log_level() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            opts.logHandler = new LogHandler() {
                @Override
                public void println(int severity, String tag, String msg, Throwable tr) {
                    init9_logCalled = true;
                    System.out.println(msg);
                }
            };
            opts.logLevel = Log.NONE;
            ably = new RealtimeClient(opts);
            assertFalse("Log handler incorrectly called", init9_logCalled);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init9: Unexpected exception instantiating library");
        } finally {
            if(ably != null) ably.close();
        }
    }
}
