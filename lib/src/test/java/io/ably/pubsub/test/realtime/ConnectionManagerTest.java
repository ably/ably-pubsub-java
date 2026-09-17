package io.ably.pubsub.test.realtime;

import io.ably.pubsub.debug.DebugOptions;
import io.ably.pubsub.realtime.RealtimeClient;
import io.ably.pubsub.realtime.Channel;
import io.ably.pubsub.realtime.ChannelEvent;
import io.ably.pubsub.realtime.ChannelState;
import io.ably.pubsub.realtime.ChannelStateListener;
import io.ably.pubsub.realtime.Connection;
import io.ably.pubsub.realtime.ConnectionEvent;
import io.ably.pubsub.realtime.ConnectionState;
import io.ably.pubsub.realtime.ConnectionStateListener;
import io.ably.pubsub.http.Auth.AuthMethod;
import io.ably.pubsub.test.common.Helpers;
import io.ably.pubsub.test.common.Helpers.ChannelWaiter;
import io.ably.pubsub.test.common.Helpers.ConnectionWaiter;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.test.util.EmptyPlatformAgentProvider;
import io.ably.pubsub.test.util.MockWebsocketFactory;
import io.ably.pubsub.transport.ConnectionManager;
import io.ably.pubsub.transport.Defaults;
import io.ably.pubsub.transport.Hosts;
import io.ably.pubsub.transport.ITransport;
import io.ably.pubsub.transport.WebSocketTransport;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.ProtocolMessage;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by gokhanbarisaker on 3/9/16.
 */
public class ConnectionManagerTest extends ParameterizedTest {

    @Rule
    public Timeout testTimeout = Timeout.seconds(60);

    /**
     * <p>
     * Verifies that ably connects to default host,
     * when everything is fine.
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void connectionmanager_fallback_none() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        try (RealtimeClient ably = new RealtimeClient(opts)) {
            ConnectionManager connectionManager = ably.connection.connectionManager;

            new Helpers.ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);

            /* Verify that,
             *   - connectionManager is connected
             *   - connectionManager is connected to the host without any fallback
             */
            assertThat(connectionManager.getConnectionState().state, is(ConnectionState.connected));
            assertThat(connectionManager.getHost(), is(equalTo(opts.environment + "-realtime.ably.io")));
        }
    }

    /**
     * <p>
     * Verifies that fallback behaviour doesn't apply, when the default
     * custom endpoint is being used
     * </p>
     * <p>
     * Spec: RTN17b
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void connectionmanager_fallback_none_customhost() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        opts.realtimeHost = "un.reachable.host.example.com";
        opts.environment = null;
        try(RealtimeClient ably = new RealtimeClient(opts)) {
            ConnectionManager connectionManager = ably.connection.connectionManager;

            new Helpers.ConnectionWaiter(ably.connection).waitFor(ConnectionState.disconnected);

            /* Verify that,
             *   - connectionManager is disconnected
             *   - connectionManager's last host did not have any fallback
             */
            assertThat(connectionManager.getConnectionState().state, is(ConnectionState.disconnected));
            assertThat(connectionManager.getHost(), is(equalTo(opts.realtimeHost)));
        }
    }

    /**
     * <p>
     * Verifies that the {@code ConnectionManager} first checks if an internet connection is
     * available by issuing a GET request to https://internet-up.ably-realtime.com/is-the-internet-up.txt
     * , when In the case of an error necessitating use of an alternative host (see RTN17d).
     * </p>
     * <p>
     * Spec: RTN17c
     * </p>
     *
     * @throws AblyException
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void connectionmanager_fallback_none_withoutconnection() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        opts.realtimeHost = "un.reachable.host";
        opts.environment = null;
        opts.autoConnect = false;
        try(RealtimeClient ably = new RealtimeClient(opts)) {
            Connection connection = Mockito.mock(Connection.class);
            final ConnectionManager.Channels channels = Mockito.mock(ConnectionManager.Channels.class);

            ConnectionManager connectionManager = new ConnectionManager(ably, connection, channels, new EmptyPlatformAgentProvider(), null) {
                @Override
                protected boolean checkConnectivity() {
                    return false;
                }
            };

            connectionManager.connect();

            new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.disconnected);

            /* Verify that,
             *   - connectionManager is disconnected
             *   - connectionManager did not apply any fallback behavior
             */
            assertThat(connectionManager.getConnectionState().state, is(ConnectionState.disconnected));
            assertThat(connectionManager.getHost(), is(equalTo(opts.realtimeHost)));

            connectionManager.close();
        }
    }

    /**
     * <p>
     * Verifies that fallback behaviour is applied and HTTP client is using same fallback
     * endpoint, when the default realtime.ably.io endpoint is being used and has not been
     * overriden, and a fallback is applied
     * </p>
     * <p>
     * Spec: RTN17b, RTN17c
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void connectionmanager_default_fallback_applied() throws AblyException {
        DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
        fillInOptions(opts);

        final Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, opts);
        final String primaryHost = hosts.getPrimaryHost();

        /* clear the environment override, so we trigger default fallback behaviour */
        opts.environment = null;

        /* set up mock transport */
        MockWebsocketFactory mockTransport = new MockWebsocketFactory();
        opts.transportFactory = mockTransport;

        /* ensure that all connection attempts ultimately resolve to the primary host */
        mockTransport.setHostTransform(new MockWebsocketFactory.HostTransform() {
            @Override
            public String transformHost(String givenHost) {
                return primaryHost;
            }
        });

        /* set up a filter on a mock transport to fail connections to the primary host */
        mockTransport.failConnect(new MockWebsocketFactory.HostFilter() {
            @Override
            public boolean matches(String hostname) {
                return hostname.equals(primaryHost);
            }
        });

        try (RealtimeClient ably = new RealtimeClient(opts)) {
            ConnectionManager connectionManager = ably.connection.connectionManager;

            new Helpers.ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);

            /* Verify that,
             *   - connectionManager is connected
             *   - connectionManager's last host was a fallback host
             */
            assertThat(connectionManager.getConnectionState().state, is(ConnectionState.connected));
            assertThat(connectionManager.getHost(), is(not(equalTo(primaryHost))));
        }
    }

    /**
     * Verify that when environment is overridden, no fallback is used by default
     *
     * <p>
     * Spec: RTN17b
     * </p>
     */
    @Test
    public void connectionmanager_default_endpoint_no_fallback() throws AblyException {
        DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
        fillInOptions(opts);

        final Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, opts);
        final String primaryHost = hosts.getPrimaryHost();

        MockWebsocketFactory mockTransport = new MockWebsocketFactory();
        opts.transportFactory = mockTransport;

        /* ensure that all connection attempts ultimately resolve to the primary host */
        mockTransport.setHostTransform(new MockWebsocketFactory.HostTransform() {
            @Override
            public String transformHost(String givenHost) {
                return primaryHost;
            }
        });

        /* set up a filter on a mock transport to fail connections to the primary host */
        mockTransport.failConnect(new MockWebsocketFactory.HostFilter() {
            @Override
            public boolean matches(String hostname) {
                return hostname.equals(primaryHost);
            }
        });

        try (RealtimeClient ably = new RealtimeClient(opts)) {
            ConnectionManager connectionManager = ably.connection.connectionManager;

            System.out.println("waiting for disconnected");
            new Helpers.ConnectionWaiter(ably.connection).waitFor(ConnectionState.disconnected);
            System.out.println("got disconnected");

            /* Verify that,
             *   - connectionManager is disconnected
             *   - connectionManager's last host was the primary host
             */
            assertThat(connectionManager.getConnectionState().state, is(ConnectionState.disconnected));
            assertThat(connectionManager.getHost(), is(equalTo(primaryHost)));
        }
    }

    /**
     * Verify that when environment is overridden and fallback specified, the fallback is used
     *
     * <p>
     * Spec: RTN17b
     * </p>
     */
    @Test
    public void connectionmanager_default_endpoint_explicit_fallback() throws AblyException {
        DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
        fillInOptions(opts);

        final Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, opts);
        final String primaryHost = hosts.getPrimaryHost();

        opts.fallbackHosts = new String[]{"fallback 1", "fallback 2"};

        MockWebsocketFactory mockTransport = new MockWebsocketFactory();
        opts.transportFactory = mockTransport;

        /* ensure that all connection attempts ultimately resolve to the primary host */
        mockTransport.setHostTransform(new MockWebsocketFactory.HostTransform() {
            @Override
            public String transformHost(String givenHost) {
                return primaryHost;
            }
        });

        /* set up a filter on a mock transport to fail connections to the primary host */
        mockTransport.failConnect(new MockWebsocketFactory.HostFilter() {
            @Override
            public boolean matches(String hostname) {
                return hostname.equals(primaryHost);
            }
        });

        try (RealtimeClient ably = new RealtimeClient(opts)) {
            ConnectionManager connectionManager = ably.connection.connectionManager;

            System.out.println("waiting for connected");
            new Helpers.ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
            System.out.println("got connected");

            /* Verify that,
             *   - connectionManager is connected
             *   - connectionManager's last host was a fallback host
             */
            assertThat(connectionManager.getConnectionState().state, is(ConnectionState.connected));
            assertThat(connectionManager.getHost(), is(not(equalTo(primaryHost))));
        }
    }

    /**
     * Test that default fallback happens with a non-default host if
     * fallbackHostsUseDefault is set.
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void connectionmanager_reconnect_default_fallback() throws AblyException {
        DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
        fillInOptions(opts);

        opts.fallbackHostsUseDefault = true;

        final Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, opts);
        final String primaryHost = hosts.getPrimaryHost();

        MockWebsocketFactory mockTransport = new MockWebsocketFactory();
        opts.transportFactory = mockTransport;

        /* ensure that all connection attempts ultimately resolve to the primary host */
        mockTransport.setHostTransform(new MockWebsocketFactory.HostTransform() {
            @Override
            public String transformHost(String givenHost) {
                return primaryHost;
            }
        });

        /* set up a filter on a mock transport to fail connections to the primary host */
        mockTransport.failConnect(new MockWebsocketFactory.HostFilter() {
            @Override
            public boolean matches(String hostname) {
                return hostname.equals(primaryHost);
            }
        });

        try (RealtimeClient ably = new RealtimeClient(opts)) {
            ConnectionManager connectionManager = ably.connection.connectionManager;

            System.out.println("waiting for connected");
            new Helpers.ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
            System.out.println("got connected");
            ably.close();

            /* Verify that,
             *   - connectionManager is connected
             *   - connectionManager's last host was a fallback host
             */
            assertThat(connectionManager.getConnectionState().state, is(ConnectionState.connected));
            assertThat(connectionManager.getHost(), is(not(equalTo(opts.realtimeHost))));
        }
    }

    /**
     * Connect, and then perform a close() from the calling ConnectionManager context;
     * verify that the closed state is reached, and the connectionmanager thread has exited
     */
    @Test
    public void close_from_connectionmanager() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        final RealtimeClient ably = new RealtimeClient(opts);
        final Thread[] threadContainer = new Thread[1];
        ably.connection.on(ConnectionEvent.connected, new ConnectionStateListener() {
            @Override
            public void onConnectionStateChanged(ConnectionStateChange state) {
                ably.close();
                threadContainer[0] = Thread.currentThread();
            }
        });

        /* wait for cm thread to exit */
        try {
            Thread.sleep(2000L);
        } catch(InterruptedException ignored) {}

        assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
        Thread.State cmThreadState = threadContainer[0].getState();
        assertEquals("Verify cm thread has exited", cmThreadState, Thread.State.TERMINATED);
    }

    /**
     * (RTN12f) Close while in connecting state
     */
    @Test
    public void connectionmanager_close_while_connecting() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        final RealtimeClient ably = new RealtimeClient(opts);
        ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
        ConnectionManager connectionManager = ably.connection.connectionManager;
        ably.close();

        connectionWaiter.waitFor(ConnectionState.closed);
        assertEquals("Previous state was closing", ConnectionState.closing, connectionWaiter.lastStateChange().previous);
        assertEquals(1 , connectionWaiter.getCount(ConnectionState.connecting));
        assertEquals(0 , connectionWaiter.getCount(ConnectionState.connected));
        assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
        assertThat("fallback hasn't been invoked", connectionManager.getHost(), is(equalTo(opts.environment + "-realtime.ably.io")));
    }

    /**
     * Connect, and then perform a close();
     * verify that the closed state is reached, and immediately
     * reconnect; verify that it reconnects successfully
     */
    @Test
    public void connectionmanager_restart_race() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        final RealtimeClient ably = new RealtimeClient(opts);
        ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

        ably.connection.once(ConnectionEvent.connected, new ConnectionStateListener() {
            @Override
            public void onConnectionStateChanged(ConnectionStateChange state) {
                ably.close();
            }
        });

        connectionWaiter.waitFor(ConnectionState.closed);
        assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
        connectionWaiter.reset();

        /* reconnect */
        ably.connect();

        /* verify the connection is reestablished */
        connectionWaiter.waitFor(ConnectionState.connected);
        assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);

        /* close the connection */
        ably.close();
        connectionWaiter.waitFor(ConnectionState.closed);
        assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
    }

    /**
     * Connect, and then perform a close() from the calling ConnectionManager context;
     * verify that the closed state is reached, and the connectionmanager thread has exited
     */
    @Test
    public void open_from_dedicated_thread() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        opts.autoConnect = false;
        final RealtimeClient ably = new RealtimeClient(opts);
        final Thread[] threadContainer = new Thread[1];
        ably.connection.on(ConnectionEvent.connected, new ConnectionStateListener() {
            @Override
            public void onConnectionStateChanged(ConnectionStateChange state) {
                threadContainer[0] = Thread.currentThread();
            }
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(new Runnable() {
            public void run() {
                try {
                    ably.connection.connect();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        });

        ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

        connectionWaiter.waitFor(ConnectionState.connected);
        assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);
        assertSame("Not expecting token auth", ably.auth.getAuthMethod(), AuthMethod.basic);

        ably.close();
        connectionWaiter.waitFor(ConnectionState.closed);
        assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);

        /* wait for cm thread to exit */
        try {
            Thread.sleep(2000L);
        } catch(InterruptedException ignored) {}

        Thread.State cmThreadState = threadContainer[0].getState();
        assertEquals("Verify cm thread has exited", cmThreadState, Thread.State.TERMINATED);
    }

    /**
     * Connect, and then perform a close() from the calling ConnectionManager context;
     * verify that the closed state is reached, and the connectionmanager thread has exited
     */
    @Test
    public void close_from_dedicated_thread() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        opts.autoConnect = false;
        final RealtimeClient ably = new RealtimeClient(opts);
        final Thread[] threadContainer = new Thread[1];
        ably.connection.on(ConnectionEvent.connected, new ConnectionStateListener() {
            @Override
            public void onConnectionStateChanged(ConnectionStateChange state) {
                threadContainer[0] = Thread.currentThread();
            }
        });

        ably.connection.connect();
        final ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
        connectionWaiter.waitFor(ConnectionState.connected);
        assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(new Runnable() {
            public void run() {
                try {
                    ably.close();
                    connectionWaiter.waitFor(ConnectionState.closed);
                    assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);

                    /* wait for cm thread to exit */
                    try {
                        Thread.sleep(2000L);
                    } catch(InterruptedException ignored) {}

                    Thread.State cmThreadState = threadContainer[0].getState();
                    assertEquals("Verify cm thread has exited", cmThreadState, Thread.State.TERMINATED);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        });
    }

    /**
     * Connect and then verify that the connection manager has the default value for connectionStateTtl;
     */
    @Test
    public void connection_details_has_ttl() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        opts.autoConnect = false;
        try (RealtimeClient ably = new RealtimeClient(opts)) {
            Helpers.MutableConnectionManager connectionManager = new Helpers.MutableConnectionManager(ably);

            // connStateTtl set to default value
            long connStateTtl = connectionManager.getField("connectionStateTtl");
            assertEquals(Defaults.connectionStateTtl, connStateTtl);

            connectionManager.setField("connectionStateTtl", 8000L);
            long oldConnStateTtl = connectionManager.getField("connectionStateTtl");
            assertEquals(8000L, oldConnStateTtl);

            ably.connect();
            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
            long newConnStateTtl = connectionManager.getField("connectionStateTtl");
            // connStateTtl set by server to 120s
            assertEquals(120000L, newConnStateTtl);
        }
    }

    /**
     * RTN23
     */
    @Test
    public void connection_is_closed_after_max_idle_interval() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        opts.realtimeRequestTimeout = 2000;
        try(RealtimeClient ably = new RealtimeClient(opts)) {

            // The original max idle interval we receive from the server is 15s.
            // We should wait for this, plus a tiny bit extra (as we set the new idle interval to be very low
            // after connecting) to make sure that the connection is disconnected
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);

            // When we connect, we set the max idle interval to be very small
            Helpers.MutableConnectionManager connectionManager = new Helpers.MutableConnectionManager(ably);
            connectionManager.setField("maxIdleInterval", 500L);

            assertTrue(connectionWaiter.waitFor(ConnectionState.disconnected, 1, 25000));
        }
    }

    /**
     * RTN15g1, RTN15g2. Connect, disconnect, reconnect after (ttl + idle interval) period has passed,
     * check that the connection is a new one;
     */
    @Test
    public void connection_has_new_id_when_reconnecting_after_statettl_plus_idleinterval_has_passed() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        opts.realtimeRequestTimeout = 2000L;
        try(RealtimeClient ably = new RealtimeClient(opts)) {
            /* We want this greater than newTtl + newIdleInterval */
            final long waitInDisconnectedState = 3000L;

            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            final String firstConnectionId = ably.connection.id;

            Helpers.MutableConnectionManager connectionManager = new Helpers.MutableConnectionManager(ably);
            connectionManager.setField("connectionStateTtl", 1000L);
            connectionManager.setField("maxIdleInterval", 1000L);

            connectionManager.disconnectAndSuppressRetries();
            connectionWaiter.waitFor(ConnectionState.disconnected);
            assertEquals("Disconnected state was not reached", ConnectionState.disconnected, ably.connection.state);

            /* Wait for the connection to go stale, then reconnect */
            try {
                Thread.sleep(waitInDisconnectedState);
            } catch (InterruptedException ignored) {
            }
            ably.connection.connect();
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Connected state was not reached", ConnectionState.connected, ably.connection.state);

            /* Verify the connection is new */
            assertNotNull(ably.connection.id);
            assertNotEquals("Connection has the same id", firstConnectionId, ably.connection.id);
        }
    }

    /**
     * RTN15g1, RTN15g2. Connect, disconnect, reconnect before (ttl + idle interval) period has passed,
     * check that the connection is the same;
     */
    @Test
    public void connection_has_same_id_when_reconnecting_before_statettl_plus_idleinterval_has_passed() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        try(RealtimeClient ably = new RealtimeClient(opts)) {
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            String firstConnectionId = ably.connection.id;
            ably.connection.connectionManager.requestState(ConnectionState.disconnected);

            /* Wait for a connected state after the disconnection triggered above */
            connectionWaiter.waitFor(ConnectionState.connected);

            String secondConnectionId = ably.connection.id;
            assertNotNull(secondConnectionId);
            assertEquals("connection has the same id", firstConnectionId, secondConnectionId);
        }
    }

    /**
     * RTN15g3. Connect, attach some channels, disconnect, reconnect after (ttl + idle interval) period has passed,
     * check that the client reconnects with a different connection and that the channels attached during the first
     * connection are correctly reattached;
     */
    @Test
    public void channels_are_reattached_after_reconnecting_when_statettl_plus_idleinterval_has_passed() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        try(RealtimeClient ably = new RealtimeClient(opts)) {
            /* We want this greater than newTtl + newIdleInterval */
            final long waitInDisconnectedState = 3000L;
            final ChannelState[] expectedAttachedChannelHistory = new ChannelState[]{
                ChannelState.attaching, ChannelState.attached, ChannelState.attaching, ChannelState.attached};

            final ChannelState[] expectedSuspendedChannelHistory =  new ChannelState[]{
                ChannelState.attaching, ChannelState.attached};

                ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            final String firstConnectionId = ably.connection.id;

            Helpers.MutableConnectionManager connectionManager = new Helpers.MutableConnectionManager(ably);
            connectionManager.setField("connectionStateTtl", 1000L);
            connectionManager.setField("maxIdleInterval", 1000L);

            /* Prepare channels */
            final Channel attachedChannel = ably.channels.get("test-reattach-after-ttl" + testParams.name);
            ChannelWaiter attachedChannelWaiter = new Helpers.ChannelWaiter(attachedChannel);

            final Channel suspendedChannel = ably.channels.get("test-reattach-suspended-after-ttl" + testParams.name);
            suspendedChannel.state = ChannelState.suspended;
            ChannelWaiter suspendedChannelWaiter = new Helpers.ChannelWaiter(suspendedChannel);

            /* attach first channel and wait for it to be attached */
            attachedChannel.attach();
            attachedChannelWaiter.waitFor(ChannelState.attached);

            connectionManager.disconnectAndSuppressRetries();
            connectionWaiter.waitFor(ConnectionState.disconnected);
            assertEquals("Disconnected state was not reached", ConnectionState.disconnected, ably.connection.state);

            /* Wait for the connection to go stale, then reconnect */
            try {
                Thread.sleep(waitInDisconnectedState);
            } catch (InterruptedException ignored) {
            }
            ably.connection.connect();
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Connected state was not reached", ConnectionState.connected, ably.connection.state);

            /* Verify the connection is new */
            assertNotNull(ably.connection.id);
            assertNotEquals("Connection has the same id", firstConnectionId, ably.connection.id);

            /* Verify that the attached channel is reattached with resumed false */
            attachedChannel.once(ChannelEvent.attached, new ChannelStateListener() {
                @Override
                public void onChannelStateChanged(ChannelStateChange stateChange) {
                    assertFalse("Resumed is true and should be false", stateChange.resumed);
                }
            });

            /* Wait for both channels to reattach and verify state histories match the expected ones */
            attachedChannelWaiter.waitFor(ChannelState.attached);
            suspendedChannelWaiter.waitFor(ChannelState.attached);
            assertTrue("Attached channel histories do not match",
                attachedChannelWaiter.hasFinalStates(expectedAttachedChannelHistory));

            assertTrue("Suspended channel histories do not match",
                suspendedChannelWaiter.hasFinalStates(expectedSuspendedChannelHistory));
        }
    }

    /**
     * <p>
     * Verifies that the {@code ConnectionManager} enters the disconnected state and sets the suspend timer
     * upon unavailable transport.
     * </p>
     * <p>
     * Spec: RTN15g
     * </p>
     */
    @Test
    public void connection_manager_enters_disconnected_state_on_transport_failure() throws AblyException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        try(RealtimeClient ably = new RealtimeClient(opts)) {
            ConnectionManager connectionManager = ably.connection.connectionManager;
            connectionManager.connect();

            new Helpers.ConnectionManagerWaiter(ably.connection.connectionManager).waitFor(ConnectionState.connected);

            // Here, we "fake" being online for 2 minutes - the suspendTime is set by onConnected and the default is 2 minutes
            Field suspendTimeField = connectionManager.getClass().getDeclaredField("suspendTime");
            suspendTimeField.setAccessible(true);
            suspendTimeField.set(connectionManager, System.currentTimeMillis() - 10);

            // We also have to grab the "real" transport to pass the superseded test
            Field transportField = connectionManager.getClass().getDeclaredField("transport");
            transportField.setAccessible(true);

            connectionManager.onTransportUnavailable((ITransport) transportField.get(connectionManager), new ErrorInfo());
            new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.disconnected);

            assertTrue((long) suspendTimeField.get(connectionManager) >= System.currentTimeMillis());

            connectionManager.close();
        }
    }

    /**
     * <p>
     * Verifies that the {@code ConnectionManager} enters the suspended state if the transport is unavailable and the
     * timer has been exceeded.
     * </p>
     * <p>
     * Spec: RTN15g, RTN14d
     * </p>
     */
    @Test
    public void connection_manager_enters_suspended_state_on_transport_failure_after_already_being_disconnected_for_2_minutes() throws AblyException, NoSuchFieldException, IllegalAccessException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        try(RealtimeClient ably = new RealtimeClient(opts)) {
            ConnectionManager connectionManager = ably.connection.connectionManager;
            connectionManager.connect();
            new Helpers.ConnectionManagerWaiter(ably.connection.connectionManager).waitFor(ConnectionState.connected);

            // Here, we "fake" being disconnected beyond the suspend timer
            Class<?> connectionManagerClass = Class.forName("io.ably.pubsub.transport.ConnectionManager");
            Class<?> disconnectedState = Class.forName("io.ably.pubsub.transport.ConnectionManager$Disconnected");
            Constructor<?> disconnectedStateCtor = disconnectedState.getDeclaredConstructor(connectionManagerClass);
            disconnectedStateCtor.setAccessible(true);
            Field connectionStateField = connectionManager.getClass().getDeclaredField("currentState");
            connectionStateField.setAccessible(true);
            connectionStateField.set(connectionManager, disconnectedStateCtor.newInstance(connectionManager));

            Field suspendTimeField = connectionManager.getClass().getDeclaredField("suspendTime");
            suspendTimeField.setAccessible(true);
            suspendTimeField.set(connectionManager, System.currentTimeMillis() - 5000);

            // We also have to grab the "real" transport to pass the superseded test
            Field transportField = connectionManager.getClass().getDeclaredField("transport");
            transportField.setAccessible(true);

            connectionManager.onTransportUnavailable((ITransport) transportField.get(connectionManager), new ErrorInfo());

            new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.suspended);

            connectionManager.close();
        }
    }

    /**
     * <p>
     * Verifies that the {@code ConnectionManager} sends a close protocol message when closed.
     * </p>
     * <p>
     * Spec: RTN12
     * </p>
     */
    @Test
    public void connection_manager_sends_close_message_on_closed() throws AblyException, NoSuchFieldException, IllegalAccessException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, InterruptedException {
        DebugOptions opts = createOptions(testVars.keys[0].keyStr);
        opts.transportFactory = new ObservedWebsocketTransport.Factory();

        // Connect
        try(RealtimeClient ably = new RealtimeClient(opts)) {
            ConnectionManager connectionManager = ably.connection.connectionManager;
            connectionManager.connect();
            // Wait for connected status
            while (connectionManager.getConnectionState().state != ConnectionState.connected) {
                Thread.sleep(100);
            }

            connectionManager.close();

            long checkStartTime = System.currentTimeMillis();
            while (true) {
                if (System.currentTimeMillis() > checkStartTime + 5000) {
                    fail("Protocol message not sent");
                }

                boolean found = false;
                for (int i = 0; i < ObservedWebsocketTransport.messages.size(); i++) {
                    if (ObservedWebsocketTransport.messages.get(i).action.equals(ProtocolMessage.Action.close)) {
                        found = true;
                        break;
                    }
                }

                if (found) {
                    break;
                }

                Thread.sleep(100);
            }
        }
    }
}

// Create a transport we can observe and a factory for it
class ObservedWebsocketTransport extends WebSocketTransport
{
    public static ArrayList<ProtocolMessage> messages = new ArrayList<>();

    public static class Factory implements ITransport.Factory {
        @Override
        public ObservedWebsocketTransport getTransport(TransportParams params, ConnectionManager connectionManager) {
            return new ObservedWebsocketTransport(params, connectionManager);
        }
    }

    protected ObservedWebsocketTransport(TransportParams params, ConnectionManager connectionManager) {
        super(params, connectionManager);
    }

    @Override
    public void send(ProtocolMessage msg) throws AblyException {
        messages.add(msg);
        super.send(msg);
    }
}
