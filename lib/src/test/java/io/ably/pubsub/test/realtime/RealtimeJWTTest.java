package io.ably.pubsub.test.realtime;

import io.ably.pubsub.debug.DebugOptions;
import io.ably.pubsub.debug.DebugOptions.RawProtocolListener;
import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.http.HttpCore.ResponseHandler;
import io.ably.pubsub.http.HttpHelpers;
import io.ably.pubsub.realtime.RealtimeClient;
import io.ably.pubsub.realtime.RealtimeClientFactory;
import io.ably.pubsub.realtime.Channel;
import io.ably.pubsub.realtime.ChannelState;
import io.ably.pubsub.realtime.ConnectionEvent;
import io.ably.pubsub.realtime.ConnectionState;
import io.ably.pubsub.realtime.ConnectionStateListener;
import io.ably.pubsub.http.HttpClientFactory;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.http.Auth.TokenCallback;
import io.ably.pubsub.http.Auth.TokenParams;
import io.ably.pubsub.test.common.Helpers.ChannelWaiter;
import io.ably.pubsub.test.common.Helpers.ConnectionWaiter;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.test.common.Setup.Key;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.Message;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.types.PublishResult;
import io.ably.pubsub.types.ProtocolMessage;
import org.junit.Ignore;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RealtimeJWTTest extends ParameterizedTest {

    private PubSubHttpClient httpJWTRequester;
    private ClientOptions jwtRequesterOptions;
    private Key key = testVars.keys[0];
    private final String clientId = "testJWTClientID";
    private final String channelName = "testJWTChannel" + UUID.randomUUID().toString();
    private final String messageName = "testJWTMessage" + UUID.randomUUID().toString();
    Param[] keys = new Param[]{ new Param("keyName", key.keyName), new Param("keySecret", key.keySecret) };
    Param[] clientIdParam = new Param[] { new Param("clientId", clientId) };
    Param[] shortTokenTtl = new Param[] { new Param("expiresIn", 5) };
    Param[] mediumTokenTtl = new Param[] { new Param("expiresIn", 35) };
    private final String susbcribeOnlyCapability = "{\"" + channelName + "\": [\"subscribe\"]}";
    private final String publishCapability = "{\"" + channelName + "\": [\"publish\"]}";
    private static final String echoServer = "https://echo.ably.io/createJWT";

    /**
     * Request a JWT that specifies a clientId
     * Verifies that the clientId matches the one requested
     */
    @Test
    public void auth_clientid_match_the_one_requested_in_jwt() {
        try {
            /* create ably realtime with JWT token */
            ClientOptions realtimeOptions = buildClientOptions(mergeParams(keys, clientIdParam), null);
            assertNotNull("Expected token value", realtimeOptions.token);
            RealtimeClient realtimeClient = RealtimeClientFactory.create(realtimeOptions);

            /* wait for connected state */
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(realtimeClient.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Connected state was NOT reached", ConnectionState.connected, realtimeClient.connection.state);

            /* check expected clientId */
            assertEquals("clientId does NOT match the one requested", clientId, realtimeClient.auth.clientId);

            realtimeClient.close();
        } catch (AblyException e) {
            e.printStackTrace();
            fail();
        }
    }

    /**
     * Request a JWT with subscribe-only capabilities
     * Verifies that publishing on a channel fails
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void auth_jwt_with_subscribe_only_capability() {
        try {
            /* create ably realtime with JWT token that has subscribe-only capabilities */
            ClientOptions realtimeOptions = buildClientOptions(keys, susbcribeOnlyCapability);
            assertNotNull("Expected token value", realtimeOptions.token);
            final RealtimeClient realtimeClient = RealtimeClientFactory.create(realtimeOptions);

            /* wait for connected state */
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(realtimeClient.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Connected state was NOT reached", ConnectionState.connected, realtimeClient.connection.state);

            /* attach to channel and verify attached state */
            Channel channel = realtimeClient.channels.get(channelName);
            channel.attach();
            new ChannelWaiter(channel).waitFor(ChannelState.attached);

            /* publish and verify that it fails */
            channel.publish(messageName, null, new Callback<PublishResult>() {
                @Override
                public void onSuccess(PublishResult result) {
                    realtimeClient.close();
                    fail("It should not succeed");
                }

                @Override
                public void onError(ErrorInfo error) {
                    assertEquals("Unexpected status code", 401, error.statusCode);
                    assertEquals("Unexpected error code", 40160, error.code);
                    assertEquals("Unexpected error message", "Unable to perform channel operation (permission denied)", error.message);
                    realtimeClient.close();
                }
            });
            connectionWaiter.waitFor(ConnectionState.closed);
        } catch (AblyException e) {
            e.printStackTrace();
            fail();
        }
    }

    /**
     * Request a JWT with publish capabilities
     * Verifies that publishing on a channel succeeds
     */
    @Test
    public void auth_jwt_with_publish_capability() {
        try {
            /* create ably realtime with JWT token that has publish capabilities */
            ClientOptions realtimeOptions = buildClientOptions(keys, publishCapability);
            assertNotNull("Expected token value", realtimeOptions.token);
            final RealtimeClient realtimeClient = RealtimeClientFactory.create(realtimeOptions);

            /* wait for connected state */
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(realtimeClient.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Connected state was NOT reached", ConnectionState.connected, realtimeClient.connection.state);

            /* attach to channel and verify attached state */
            Channel channel = realtimeClient.channels.get(channelName);
            channel.attach();
            new ChannelWaiter(channel).waitFor(ChannelState.attached);

            /* publish, verify that it succeeds then close */
            final Message message = new Message(messageName, null);
            channel.publish(message, new Callback<PublishResult>() {
                @Override
                public void onSuccess(PublishResult result) {
                    System.out.println("Message " + messageName + " published successfully");
                    realtimeClient.close();
                }

                @Override
                public void onError(ErrorInfo reason) {
                    realtimeClient.close();
                    fail("Publish should not fail");
                }
            });
            connectionWaiter.waitFor(ConnectionState.closed);
        } catch (AblyException e) {
            e.printStackTrace();
            fail();
        }
    }

    /**
     * Request a JWT with a ttl of 5 seconds and
     * verify the correct error and message in the disconnected state change.
     * Spec: RTN15h1
     */
    @Test
    public void auth_jwt_with_token_that_expires() {
        try {
            /* create ably realtime with JWT token that expires in 5 seconds */
            ClientOptions realtimeOptions = buildClientOptions(mergeParams(keys, shortTokenTtl), null);
            assertNotNull("Expected token value", realtimeOptions.token);
            final RealtimeClient realtimeClient = RealtimeClientFactory.create(realtimeOptions);

            /* wait for connected state */
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(realtimeClient.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Connected state was NOT reached", ConnectionState.connected, realtimeClient.connection.state);

            /* Verify the expected error reason when disconnected */
            realtimeClient.connection.once(ConnectionEvent.disconnected, new ConnectionStateListener() {

                @Override
                public void onConnectionStateChanged(ConnectionStateChange stateChange) {
                    assertEquals("Unexpected connection stage change", 40142, stateChange.reason.code);
                    assertTrue("Unexpected error message", stateChange.reason.message.contains("Key/token status changed (expire)"));
                }
            });
            connectionWaiter.waitFor(ConnectionState.failed);
        } catch (AblyException e) {
            e.printStackTrace();
            fail();
        }
    }

    /**
     * Request a JWT with a ttl of 35 seconds and
     * verify that the client reauths without going through a disconnected state. (RTC8a4)
     */
    @Test
    public void auth_jwt_with_client_than_reauths_without_disconnecting() {
        try {
            final String[] tokens = new String[1];
            final boolean[] authMessages = new boolean[] { false };
            final boolean[] updateEvents = new boolean[] { false };

            /* create ably realtime with authUrl and params that include a ttl of 35 seconds */
            DebugOptions options = new DebugOptions(testVars.keys[0].keyStr);
            options.environment = createOptions().environment;
            options.authUrl = echoServer;
            options.authParams = mergeParams(keys, mediumTokenTtl);
            options.protocolListener = new RawProtocolListener() {
                @Override
                public void onRawConnectRequested(String url) {}
                @Override
                public void onRawConnect(String url) { }
                @Override
                public void onRawMessageSend(ProtocolMessage message) { }
                @Override
                public void onRawMessageRecv(ProtocolMessage message) {
                    if (message.action == ProtocolMessage.Action.auth) {
                        authMessages[0] = true;
                    }
                }
            };
            final RealtimeClient realtimeClient = RealtimeClientFactory.create(options);

            /* Once connected for the first time capture the assigned token */
            realtimeClient.connection.once(ConnectionEvent.connected, new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange stateChange) {
                    assertEquals("State is not connected", ConnectionState.connected, stateChange.current);
                    synchronized (tokens) {
                        tokens[0] = realtimeClient.auth.getTokenDetails().token;
                    }
                }
            });

            /* Fail if the disconnected state is ever reached */
            realtimeClient.connection.once(ConnectionEvent.disconnected, new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange stateChange) {
                    fail("Should NOT enter the disconnected state");
                }
            });

            /* Once receiving the update event check that the token is a new one
             * and verify the auth protocol message has been received. */
            realtimeClient.connection.on(ConnectionEvent.update, new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    assertNotEquals("Token should not be the same", tokens[0], realtimeClient.auth.getTokenDetails().token);
                    assertTrue("Auth protocol message has not been received", authMessages[0]);
                    updateEvents[0] = true;
                    realtimeClient.close();
                }
            });

            /* wait for connected state */
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(realtimeClient.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Connected state was NOT reached", ConnectionState.connected, realtimeClient.connection.state);

            /* wait for closed state */
            connectionWaiter.waitFor(ConnectionState.closed);
            assertTrue("Update event not received", updateEvents[0]);
        } catch (AblyException e) {
            e.printStackTrace();
            fail();
        }
    }

    /**
     * Request a JWT with a ttl of 35 seconds and
     * verify that the client reauths without going through a disconnected state using an authCallback. (RTC8a4)
     */
    @Test
    public void auth_jwt_with_client_than_reauths_without_disconnecting_via_authCallback() {
        try {
            final String[] tokens = new String[1];
            final boolean[] authMessages = new boolean[] { false };
            final boolean[] updateEvents = new boolean[] { false };
            final List<Boolean> callbackCalled = new ArrayList<Boolean>();

            /* authCallback that with params that include a ttl of 35 seconds */
            TokenCallback authCallback = new TokenCallback() {
                @Override
                public Object getTokenRequest(TokenParams params) throws AblyException {
                    final String[] resultToken = new String[1];
                    PubSubHttpClient rest = HttpClientFactory.create(createOptions(testVars.keys[0].keyStr));
                    HttpHelpers.getUri(rest.httpCore, echoServer, new Param[]{}, mergeParams(keys, mediumTokenTtl), new ResponseHandler() {
                        @Override
                        public Object handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
                            try {
                                synchronized (tokens) {
                                    callbackCalled.add(true);
                                }
                                resultToken[0] = new String(response.body, "UTF-8");
                            } catch (UnsupportedEncodingException e) {
                                e.printStackTrace();
                                fail("Error in fetching a JWT token using authCallback");
                            }
                            return null;
                        }
                    });
                    return resultToken[0];
                }
            };

            /* create ably realtime with authCallback defined above */
            DebugOptions options = new DebugOptions(testVars.keys[0].keyStr);
            options.environment = createOptions().environment;
            options.authCallback = authCallback;
            options.protocolListener = new RawProtocolListener() {
                @Override
                public void onRawConnectRequested(String url) {}
                @Override
                public void onRawConnect(String url) { }
                @Override
                public void onRawMessageSend(ProtocolMessage message) { }
                @Override
                public void onRawMessageRecv(ProtocolMessage message) {
                    synchronized (tokens) {
                        if (message.action == ProtocolMessage.Action.auth) {
                            authMessages[0] = true;
                        }
                    }
                }
            };

            final RealtimeClient realtimeClient = RealtimeClientFactory.create(options);

            /* Once connected for the first time capture the assigned token and
            * verify the callback has been called once */
            realtimeClient.connection.once(ConnectionEvent.connected, new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange stateChange) {
                    synchronized (tokens) {
                        assertTrue("Callback not called the first time", callbackCalled.get(0));
                        assertEquals("State is not connected", ConnectionState.connected, stateChange.current);
                        tokens[0] = realtimeClient.auth.getTokenDetails().token;
                    }
                }
            });

            /* Fail if the disconnected state is ever reached */
            realtimeClient.connection.once(ConnectionEvent.disconnected, new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange stateChange) {
                    realtimeClient.close();
                    fail("Should NOT enter the disconnected state");
                }
            });

            /* Once receiving the update event check that the token is a new one,
             * verify the auth protocol message has been received and verify the callback has been called twice. */
            realtimeClient.connection.on(ConnectionEvent.update, new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    synchronized (tokens) {
                        assertTrue("Callback not called the second time", callbackCalled.get(1));
                        assertNotEquals("Token should not be the same", realtimeClient.auth.getTokenDetails().token, tokens[0]);
                        assertTrue("Auth protocol message has not been received", authMessages[0]);
                        updateEvents[0] = true;
                        realtimeClient.close();
                    }
                }
            });

            /* wait for connected state */
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(realtimeClient.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Connected state was NOT reached", ConnectionState.connected, realtimeClient.connection.state);

            /* wait for closed state */
            connectionWaiter.waitFor(ConnectionState.closed);
            assertTrue("Update event not received", updateEvents[0]);
        } catch (AblyException e) {
            e.printStackTrace();
            fail();
        }
    }

    /**
     * Helper to create ClientOptions with a JWT token fetched via authUrl according to the parameters
     */
    private ClientOptions buildClientOptions(Param[] params, String capability) {
        try {
            final String[] resultToken = new String[1];
            PubSubHttpClient rest = HttpClientFactory.create(createOptions(testVars.keys[0].keyStr));
            HttpHelpers.getUri(rest.httpCore, echoServer, null, params, new ResponseHandler() {
                @Override
                public Object handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
                    try {
                        resultToken[0] = new String(response.body, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                        fail("Error in fetching a JWT token " + e);
                    }
                    return null;
                }
            });
            ClientOptions realtimeOptions = createOptions();
            realtimeOptions.token = resultToken[0];
            return realtimeOptions;
        } catch (AblyException e) {
            fail("Failure in fetching a JWT token to create ClientOptions " + e);
            return null;
        }
    }

}
