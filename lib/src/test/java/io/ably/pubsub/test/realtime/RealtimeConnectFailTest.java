package io.ably.pubsub.test.realtime;

import io.ably.pubsub.debug.DebugOptions;
import io.ably.pubsub.realtime.RealtimeClient;
import io.ably.pubsub.realtime.RealtimeClientFactory;
import io.ably.pubsub.realtime.Channel;
import io.ably.pubsub.realtime.ChannelState;
import io.ably.pubsub.realtime.ConnectionEvent;
import io.ably.pubsub.realtime.ConnectionState;
import io.ably.pubsub.realtime.ConnectionStateListener;
import io.ably.pubsub.http.HttpClientFactory;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.http.Auth;
import io.ably.pubsub.http.Auth.TokenCallback;
import io.ably.pubsub.http.Auth.TokenDetails;
import io.ably.pubsub.http.Auth.TokenParams;
import io.ably.pubsub.test.common.Helpers;
import io.ably.pubsub.test.common.Helpers.ConnectionWaiter;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.test.util.MockWebsocketFactory;
import io.ably.pubsub.transport.Defaults;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.ProtocolMessage;
import io.ably.pubsub.types.PublishResult;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static io.ably.pubsub.test.common.Helpers.assertTimeoutBetween;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RealtimeConnectFailTest extends ParameterizedTest {

    @Rule
    public Timeout testTimeout = Timeout.seconds(300);

    /**
     * Verify that the connection enters the failed state, after attempting
     * to connect with invalid app
     * Spec: RTN4f
     */
    @Test
    public void connect_fail_notfound_error() throws AblyException {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions("not_an_app.invalid_key_id:invalid_key_value");
            ably = RealtimeClientFactory.create(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

            ErrorInfo fail = connectionWaiter.waitFor(ConnectionState.failed);
            assertEquals("Verify failed state is reached", ConnectionState.failed, ably.connection.state);
            // assertEquals("Verify correct error code is given", 404, fail.statusCode);
        } finally {
            ably.close();
        }
    }

    /**
     * Verify that the connection enters the failed state, after attempting
     * to connect with invalid key
     */
    @Test
    public void connect_fail_authorized_error() throws AblyException {
        RealtimeClient ably = null;
        try {
            String keyId = testVars.keys[0].keyName.split("\\.")[1];
            ClientOptions opts = createOptions(testVars.appId + "." + keyId + ":invalid_key_value");
            ably = RealtimeClientFactory.create(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

            ErrorInfo fail = connectionWaiter.waitFor(ConnectionState.failed);
            assertEquals("Verify failed state is reached", ConnectionState.failed, ably.connection.state);
            assertEquals("Verify correct error code is given", 401, fail.statusCode);
        } finally {
            ably.close();
        }
    }

    /**
     * Verify that the connection enters the disconnected state, after attempting
     * to connect to a non-existent ws host
     */
    @Test
    public void connect_fail_disconnected() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        opts.realtimeHost = "non.existent.host";
        opts.environment = null;
        RealtimeClient ably = RealtimeClientFactory.create(opts);
        ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

        connectionWaiter.waitFor(ConnectionState.disconnected);
        assertEquals("Verify disconnected state is reached", ConnectionState.disconnected, ably.connection.state);
        ably.close();
        connectionWaiter.waitFor(ConnectionState.closed);
        assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
    }

    /**
     * Verify that the connection enters the suspended state, after multiple attempts
     * to connect to a non-existent ws host
     */
    @Test
    public void connect_fail_suspended() {
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.realtimeHost = "non.existent.host";
            opts.environment = null;
            RealtimeClient ably = RealtimeClientFactory.create(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

            connectionWaiter.waitFor(ConnectionState.suspended);
            /* Wait 1s to force bug where it changes to disconnected right after
             * changing to suspended. Without this it fails only intermittently
             * when that bug is present. */
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {}
            assertEquals("Verify suspended state is reached", ConnectionState.suspended, ably.connection.state);
            assertTrue("Verify multiple connect attempts", connectionWaiter.getCount(ConnectionState.connecting) > 1);
            assertTrue("Verify multiple connect attempts", connectionWaiter.getCount(ConnectionState.disconnected) > 1);
            ably.close();
            connectionWaiter.waitFor(ConnectionState.closed);
            assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        }
    }

    @Test
    public void connect_after_suspend_should_clean_msg_serial() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        opts.disconnectedRetryTimeout = Integer.MAX_VALUE;
        opts.suspendedRetryTimeout = Integer.MAX_VALUE;
        try (RealtimeClient ably = RealtimeClientFactory.create(opts)) {
            ConnectionWaiter waiter = new ConnectionWaiter(ably.connection);
            waiter.waitFor(ConnectionState.connecting);
            ably.connection.connectionManager.requestState(ConnectionState.suspended);
            waiter.waitFor(ConnectionState.suspended);
            ably.connection.connectionManager.msgSerial = 100;
            assertEquals("Verify suspended state reached", ConnectionState.suspended, ably.connection.state);
            ably.connect();
            waiter.waitFor(ConnectionState.connected);
            assertEquals(0, ably.connection.connectionManager.msgSerial);
        }
    }

    /**
     * Verify that the connection in the disconnected state (after attempts to
     * connect to a non-existent ws host) allows an immediate explicit connect
     * attempt, instead of ignoring the explicit connect and waiting till the
     * next scheduled retry.
     */
    @Test
    public void connect_while_disconnected() {
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.realtimeHost = "non.existent.host";
            opts.environment = null;
            RealtimeClient ably = RealtimeClientFactory.create(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

            connectionWaiter.waitFor(ConnectionState.disconnected);
            assertEquals("Verify disconnected state is reached", ConnectionState.disconnected, ably.connection.state);

            long before = System.currentTimeMillis();
            ably.connection.connect();
            connectionWaiter.waitFor(ConnectionState.connecting);
            assertTrue("Verify explicit connect is actioned immediately", System.currentTimeMillis() - before < 1000L);

            ably.close();
            connectionWaiter.waitFor(ConnectionState.closed);
            assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify that the connection enters the disconnected state, after a token
     * used for successful connection expires
     */
    @Test
    public void connect_token_expire_disconnected() {
        try {
            final ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            final PubSubHttpClient ablyForToken = HttpClientFactory.create(optsForToken);
            Auth.AuthOptions httpAuthOptions = new Auth.AuthOptions() {{
                key = optsForToken.key;
                queryTime = true;
            }};
            TokenDetails tokenDetails = ablyForToken.auth.requestToken(new TokenParams() {{ ttl = 8000L; }}, httpAuthOptions);
            assertNotNull("Expected token value", tokenDetails.token);

            /* implement callback, using Ably instance with key */
            final class TokenGenerator implements TokenCallback {
                @Override
                public Object getTokenRequest(TokenParams params) throws AblyException {
                    ++cbCount;
                    return ablyForToken.auth.requestToken(params, null);
                }
                public int getCbCount() { return cbCount; }
                private int cbCount = 0;
            };

            TokenGenerator authCallback = new TokenGenerator();

            /* create Ably realtime instance without key */
            ClientOptions opts = createOptions();
            opts.tokenDetails = tokenDetails;
            opts.authCallback = authCallback;
            RealtimeClient ably = RealtimeClientFactory.create(opts);

            ably.connection.on(new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    System.out.println(state.current);
                }
            });

            /* wait for connected state */
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);

            /* wait for disconnected state (on token expiry), with timeout */
            connectionWaiter.waitFor(ConnectionState.disconnected, 1, 30000L);

            /* wait for connected state (on token renewal) */
            connectionWaiter.waitFor(ConnectionState.connected, 2, 30000L);

            /* verify that our token generator was called */
            assertEquals("Expected token generator to be called", 1, authCallback.getCbCount());

            /* end */
            ably.close();
            connectionWaiter.waitFor(ConnectionState.closed);
            assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify that the server issues reauth message 30 seconds before token expiration time, authCallback is
     * called to obtain new token and in-place re-authorization takes place with connection staying in connected
     * state. Also tests if UPDATE event is delivered on the connection
     *
     * Test for RTN4h, RTC8a1, RTN24 features
     */
    @Test
    public void connect_token_expire_inplace_reauth() {
        try {
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            final PubSubHttpClient ablyForToken = HttpClientFactory.create(optsForToken);
            /* Server will send reauth message 30 seconds before token expiration time i.e. in 4 seconds */
            TokenDetails tokenDetails = ablyForToken.auth.requestToken(new TokenParams() {{ ttl = 34000L; }}, null);
            assertNotNull("Expected token value", tokenDetails.token);

            final boolean[] flags = new boolean[] {
                    false, /* authCallback is called */
                    false, /* state other than connected is reached */
                    false  /* update event was delivered */
            };

            /* create Ably realtime instance without key */
            ClientOptions opts = createOptions();
            opts.tokenDetails = tokenDetails;
            opts.authCallback = new TokenCallback() {
                /* implement callback, using Ably instance with key */
                @Override
                public Object getTokenRequest(TokenParams params) throws AblyException {
                    synchronized (flags) {
                        flags[0] = true;
                    }
                    return ablyForToken.auth.requestToken(params, null);
                }
            };
            RealtimeClient ably = RealtimeClientFactory.create(opts);

            /* Test UPDATE event delivery */
            ably.connection.on(ConnectionEvent.update, new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    flags[2] = true;
                }
            });
            ably.connection.on(new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    if (state.previous == ConnectionState.connected && state.current != ConnectionState.connected) {
                        synchronized (flags) {
                            flags[1] = true;
                            flags.notify();
                        }
                    }
                }
            });

            synchronized (flags) {
                try {
                    flags.wait(8000);
                } catch (InterruptedException e) {}
            }

            assertTrue("Verify token generation was called", flags[0]);
            assertFalse("Verify connection didn't leave connected state", flags[1]);
            assertTrue("Verify UPDATE event was delivered", flags[2]);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify that the connection fails when attempting to recover with a
     * malformed connection id
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void connect_invalid_recover_fail() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.recover = "not_a_valid_connection_id:99";
            ably = RealtimeClientFactory.create(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            ErrorInfo fail = connectionWaiter.waitFor(ConnectionState.failed);
            assertEquals("Verify failed state is reached", ConnectionState.failed, ably.connection.state);
            assertEquals("Verify correct error code is given", 80018, fail.code);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            ably.close();
        }
    }

    /**
     * Verify that the connection creates a new connection but reports
     * a recovery error, when attempting to recover with an unknown
     * connection id
     */
    @Test
    public void connect_unknown_recover_fail() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            String recoveryKey =
                "{\"connectionKey\":\"0123456789abcdef-99\",\"msgSerial\":5,\"channelSerials\":{\"channel1\":\"98\",\"channel2\":\"32\",\"channel3\":\"09\"}}";
            opts.recover = recoveryKey;
            ably = RealtimeClientFactory.create(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            ErrorInfo connectedError = connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);
            assertNotNull("Verify error is returned", connectedError);
            assertEquals("Verify correct error code is given", 80018, connectedError.code);
            assertFalse("Verify new connection id is assigned", "0123456789abcdef-99".equals(ably.connection.key));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            ably.close();
        }
    }

    /**
     * Test that connection manager correctly fails messages set stored in message queue
     * Spec: RTN7c
     */

    @Test
    public void connect_test_queued_messages_on_failure() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);
            final int[] numberOfErrors = new int[]{0};

            // assume we are in connecting state now
            ably.connection.connectionManager.send(new ProtocolMessage(), true, new Callback<PublishResult>() {
                @Override
                public void onSuccess(PublishResult result) {
                    fail("Unexpected success sending message");
                }

                @Override
                public void onError(ErrorInfo reason) {
                    numberOfErrors[0]++;
                }
            });

            // transition to suspended state failing messages
            ably.connection.connectionManager.requestState(ConnectionState.suspended);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }
            // transition once more to ensure onError() won't be called twice
            ably.connection.connectionManager.requestState(ConnectionState.closed);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }

            // onError() should be called only once
            assertEquals("Verifying number of onError() calls", numberOfErrors[0], 1);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception");
        } finally {
            if (ably != null)
                ably.close();
        }
    }

    /**
     * Allow token to expire and try to authorize with already expired token after that. Test that the connection state
     * is changed in the correct way and without duplicates:
     *
     * connecting -> connected -> disconnected -> connecting -> disconnected -> connecting -> disconnected
     */
    @Test
    public void connect_reauth_failure_state_flow_test() {
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);

            PubSubHttpClient pubSubHttpClient = HttpClientFactory.create(opts);
            final TokenDetails tokenDetails = pubSubHttpClient.auth.requestToken(new TokenParams() {{ ttl = 2000L; }}, null);
            assertNotNull("Expected token value", tokenDetails.token);

            final ArrayList<ConnectionState> stateHistory = new ArrayList<>();

            ClientOptions optsForRealtime = createOptions();
            optsForRealtime.authCallback = new TokenCallback() {
                @Override
                public Object getTokenRequest(TokenParams params) throws AblyException {
                    // always return same token
                    return tokenDetails;
                }
            };
            optsForRealtime.tokenDetails = tokenDetails;
            final RealtimeClient realtimeClient = RealtimeClientFactory.create(optsForRealtime);

            (new ConnectionWaiter(realtimeClient.connection)).waitFor(ConnectionState.connected);
            final List<ConnectionState> correctHistory = Arrays.asList(
                    ConnectionState.disconnected,
                    ConnectionState.connecting,
                    ConnectionState.disconnected,
                    ConnectionState.connecting,
                    ConnectionState.disconnected
            );
            final int maxDisconnections = 3;
            realtimeClient.connection.on(new ConnectionStateListener() {
                int disconnections = 0;
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    synchronized (stateHistory) {
                        stateHistory.add(state.current);
                        if (state.current == ConnectionState.disconnected) {
                            disconnections++;
                            if (disconnections == maxDisconnections) {
                                assertEquals(correctHistory, stateHistory);
                                realtimeClient.close();
                            }
                        }
                    }
                }
            });

            ConnectionWaiter connectionWaiter = new ConnectionWaiter(realtimeClient.connection);
            connectionWaiter.waitFor(ConnectionState.closed);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        }
    }

    /**
     * Throw exception in authCallback repeatedly and check if connection goes into suspended state
     */
    @Test
    public void connect_auth_failure_and_suspend_test() {
        RealtimeClient realtimeClient = null;
        PubSubHttpClient pubSubHttpClient = null;

        try {
            /* Make test faster */
            final int[] numberOfAuthCalls = new int[] {0};
            final boolean[] reachedFinalState = new boolean[] {false};

            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.disconnectedRetryTimeout = 1000;

            pubSubHttpClient = HttpClientFactory.create(opts);
            final TokenDetails tokenDetails = pubSubHttpClient.auth.requestToken(new TokenParams() {{ ttl = 5000L; }}, null);
            assertNotNull("Expected token value", tokenDetails.token);

            ClientOptions optsForRealtime = createOptions();
            optsForRealtime.authCallback = new TokenCallback() {
                @Override
                public Object getTokenRequest(TokenParams params) throws AblyException {
                    if (numberOfAuthCalls[0]++ == 0)
                        return tokenDetails;
                    else
                        throw AblyException.fromErrorInfo(new ErrorInfo("Auth failure", 90000));
                }
            };
            realtimeClient = RealtimeClientFactory.create(optsForRealtime);

            realtimeClient.connection.on(new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    System.out.format(Locale.ROOT, "New state: %s\n", state.current);
                    synchronized (reachedFinalState) {
                        reachedFinalState[0] = state.current == ConnectionState.closed ||
                                state.current == ConnectionState.suspended ||
                                state.current == ConnectionState.failed;
                        reachedFinalState.notify();
                    }
                }
            });

            synchronized (reachedFinalState) {
                while (!reachedFinalState[0]) {
                    try { reachedFinalState.wait(); } catch (InterruptedException e) {}
                }
            }

            assertEquals("Verify suspended state", realtimeClient.connection.state, ConnectionState.suspended);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if (realtimeClient != null)
                realtimeClient.close();
        }
    }

    /**
     * Connect to unknown host and check if timer time is jittered
     * Spec: RTB1
     */
    @Test
    public void disconnect_retry_connection_timeout_jitter()  {

        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.realtimeHost = "non.existent.host";
            opts.environment = null;
            opts.disconnectedRetryTimeout = 5000; // Disconnected retry timeout set to 5 seconds.
            ably = RealtimeClientFactory.create(opts);

            final ArrayList<Long> disconnectedRetryTimeouts = new ArrayList<>();

            new Helpers.ConnectionWaiter(ably.connection).waitFor(ConnectionState.connecting);

            do {
                new Helpers.ConnectionWaiter(ably.connection).waitFor(ConnectionState.disconnected);
                Instant start = Instant.now();
                new Helpers.ConnectionWaiter(ably.connection).waitFor(ConnectionState.connecting);
                Instant end = Instant.now();
                disconnectedRetryTimeouts.add(Duration.between(start, end).toMillis());
            } while (disconnectedRetryTimeouts.stream().reduce(0L, Long::sum) + 10000 < Defaults.connectionStateTtl);

            System.out.println("Generated retry timeout values => ");
            System.out.println(String.join(",", disconnectedRetryTimeouts.stream().map(Object::toString).toArray(String[]::new)));

            // Upper bound = min((retryAttempt + 2) / 3, 2) * initialTimeout
            // Lower bound = 0.8 * Upper bound
            // Add deviation of 50ms since Instant.now() is being calculated after connecting state is reached
            assertTimeoutBetween(disconnectedRetryTimeouts.get(0).intValue(), 4000d, 5000d + 50);
            assertTimeoutBetween(disconnectedRetryTimeouts.get(1).intValue(), 5333.33, 6666.66 + 50);
            assertTimeoutBetween(disconnectedRetryTimeouts.get(2).intValue(), 6666.66, 8333.33 + 50);

            for (int i = 3; i < disconnectedRetryTimeouts.size(); i++)
            {
                assertTimeoutBetween(disconnectedRetryTimeouts.get(i).intValue(), 8000d, 10000d + 50);
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if (ably != null)
                ably.close();
        }
    }

    /**
     * Connect and check if timer time is jittered
     * Spec: RTB1
     */
    @Test
    public void disconnect_retry_channel_timeout_jitter_after_first_detach()  {
        RealtimeClient ably = null;
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            opts.channelRetryTimeout = 5000; //  channel retry timeout set to 5 seconds.
            opts.realtimeRequestTimeout = 100; // quickly timeout and transition to suspended
            opts.transportFactory = new MockWebsocketFactory();
            ((MockWebsocketFactory)opts.transportFactory).allowSend();
            fillInOptions(opts);

            ably = RealtimeClientFactory.create(opts);
            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);

            /* Block send() */
            ((MockWebsocketFactory)opts.transportFactory).blockSend();

            Channel channel = ably.channels.get("failed_attach");
            Helpers.ChannelWaiter channelWaiter = new Helpers.ChannelWaiter(channel);
            channel.attach();
            channelWaiter.waitFor(ChannelState.attaching);

            final ArrayList<Long> channelRetryTimeouts = new ArrayList<>();

            /* Inject detached message as if from the server */
            ProtocolMessage detachedMessage = new ProtocolMessage() {{
                action = Action.detached;
                channel = "failed_attach";
                error = new ErrorInfo("Test error", 12345);
            }};
            ably.connection.connectionManager.onMessage(null, detachedMessage);

            do
            {
                channelWaiter.waitFor(ChannelState.suspended);
                Instant start = Instant.now();

                channelWaiter.waitFor(ChannelState.attaching);
                Instant end = Instant.now();

                channelRetryTimeouts.add(Duration.between(start, end).toMillis());
            } while (channelRetryTimeouts.size() < 8); // channel keeps retrying attach indefinitely, limit the number of retries.

            System.out.println("Generated retry timeout values => ");
            System.out.println(String.join(",", channelRetryTimeouts.stream().map(Object::toString).toArray(String[]::new)));

            // Upper bound = min((retryAttempt + 2) / 3, 2) * initialTimeout
            // Lower bound = 0.8 * Upper bound
            // Add deviation of 50ms since Instant.now() is being calculated after connecting state is reached
            assertTimeoutBetween(channelRetryTimeouts.get(0).intValue(), 4000d, 5000d + 50);
            assertTimeoutBetween(channelRetryTimeouts.get(1).intValue(), 5333.33, 6666.66 + 50);
            assertTimeoutBetween(channelRetryTimeouts.get(2).intValue(), 6666.66, 8333.33 + 50);

            for (int i = 3; i < channelRetryTimeouts.size(); i++)
            {
                assertTimeoutBetween(channelRetryTimeouts.get(i).intValue(), 8000d, 10000d + 50);
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if (ably != null)
                ably.close();
        }
    }

    @Test
    public void disconnect_retry_channel_timeout_jitter_after_consistent_detach() {
        RealtimeClient ably = null;
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            opts.channelRetryTimeout = 5000; // channel retry timeout set to 5 seconds., no realtimeRequestTimeout is set.
            opts.transportFactory = new MockWebsocketFactory();
            ((MockWebsocketFactory)opts.transportFactory).allowSend();
            fillInOptions(opts);

            ably = RealtimeClientFactory.create(opts);
            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);

            /* Block send() */
            ((MockWebsocketFactory)opts.transportFactory).blockSend();

            Channel channel = ably.channels.get("failed_attach");
            Helpers.ChannelWaiter channelWaiter = new Helpers.ChannelWaiter(channel);
            channel.attach();
            channelWaiter.waitFor(ChannelState.attaching);

            final ArrayList<Long> channelRetryTimeouts = new ArrayList<>();

            /* Inject detached message as if from the server */
            ProtocolMessage detachedMessage = new ProtocolMessage() {{
                action = Action.detached;
                channel = "failed_attach";
                error = new ErrorInfo("Test error", 12345);
            }};

            do
            {
                ably.connection.connectionManager.onMessage(null, detachedMessage);

                channelWaiter.waitFor(ChannelState.suspended);
                Instant start = Instant.now();

                channelWaiter.waitFor(ChannelState.attaching);
                Instant end = Instant.now();

                channelRetryTimeouts.add(Duration.between(start, end).toMillis());
            } while (channelRetryTimeouts.size() < 8); // channel keeps retrying attach indefinitely, limit the number of retries.

            System.out.println("Generated retry timeout values => ");
            System.out.println(String.join(",", channelRetryTimeouts.stream().map(Object::toString).toArray(String[]::new)));

            // Upper bound = min((retryAttempt + 2) / 3, 2) * initialTimeout
            // Lower bound = 0.8 * Upper bound
            // Add deviation of 50ms since Instant.now() is being calculated after connecting state is reached
            assertTimeoutBetween(channelRetryTimeouts.get(0).intValue(), 4000d, 5000d + 50);
            assertTimeoutBetween(channelRetryTimeouts.get(1).intValue(), 5333.33, 6666.66 + 50);
            assertTimeoutBetween(channelRetryTimeouts.get(2).intValue(), 6666.66, 8333.33 + 50);

            for (int i = 3; i < channelRetryTimeouts.size(); i++)
            {
                assertTimeoutBetween(channelRetryTimeouts.get(i).intValue(), 8000d, 10000d + 50);
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if (ably != null)
                ably.close();
        }
    }

}
