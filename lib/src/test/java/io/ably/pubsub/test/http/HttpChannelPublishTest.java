package io.ably.pubsub.test.http;

import io.ably.pubsub.debug.DebugOptions;
import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.network.HttpRequest;
import io.ably.pubsub.http.HttpClientFactory;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.http.Auth;
import io.ably.pubsub.http.Channel;
import io.ably.pubsub.test.common.Helpers.AsyncWaiter;
import io.ably.pubsub.test.common.Helpers.CompletionSet;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.AsyncPaginatedResult;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.Message;
import io.ably.pubsub.types.MessageSerializer;
import io.ably.pubsub.types.PaginatedResult;
import io.ably.pubsub.types.Param;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HttpChannelPublishTest extends ParameterizedTest {

    private PubSubHttpClient ably;

    @Before
    public void setUpBefore() throws Exception {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        ably = HttpClientFactory.create(opts);
    }

    /**
     * Publish events with data of various datatypes using text protocol
     */
    @Test
    public void channelpublish() {
        /* first, publish some messages */
        String channelName = "persisted:channelpublish_" + testParams.name;
        Channel pubChannel = ably.channels.get(channelName);
        try {
            pubChannel.publish("pub_text", "This is a string message payload");
            pubChannel.publish("pub_binary", "This is a byte[] message payload".getBytes());
        } catch(AblyException e) {
            e.printStackTrace();
            fail("channelpublish_text: Unexpected exception");
            return;
        }

        /* get the history for this channel */
        try {
            PaginatedResult<Message> messages = pubChannel.history(null);
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 2 messages", messages.items().length, 2);
            HashMap<String, Object> messageContents = new HashMap<String, Object>();
            /* verify message contents */
            for(Message message : messages.items())
                messageContents.put(message.name, message.data);
            assertEquals("Expect pub_text to be expected String", messageContents.get("pub_text"), "This is a string message payload");
            assertEquals("Expect pub_binary to be expected byte[]", new String((byte[])messageContents.get("pub_binary")), "This is a byte[] message payload");
        } catch (AblyException e) {
            e.printStackTrace();
            fail("channelpublish_text: Unexpected exception");
            return;
        }
    }

    /**
     * Publish events using the async publish API
     */
    @Test
    public void channelpublish_async() {
        /* first, publish some messages */
        String channelName = "persisted:channelpublish_async_" + testParams.name;
        String textPayload = "This is a string message payload";
        byte[] binaryPayload = "This is a byte[] message payload".getBytes();
        Channel pubChannel = ably.channels.get(channelName);
        CompletionSet pubComplete = new CompletionSet();

        pubChannel.publishAsync(new Message[] {new Message("pub_text", textPayload)}, pubComplete.add());
        pubChannel.publishAsync(new Message[] {new Message("pub_binary", binaryPayload)}, pubComplete.add());

        ErrorInfo[] pubErrors = pubComplete.waitFor();
        if(pubErrors != null && pubErrors.length > 0) {
            fail("channelpublish_async: Unexpected errors from publish");
            return;
        }

        /* get the history for this channel */
        AsyncWaiter<AsyncPaginatedResult<Message>> callback = new AsyncWaiter<AsyncPaginatedResult<Message>>();
        pubChannel.historyAsync(null, callback);
        callback.waitFor();

        if(callback.error != null) {
            fail("channelpublish_async: Unexpected errors from history: " + callback.error);
            return;
        }

        AsyncPaginatedResult<Message> messages = callback.result;
        assertNotNull("Expected non-null messages", messages);
        assertEquals("Expected 2 messages", messages.items().length, 2);
        HashMap<String, Object> messageContents = new HashMap<String, Object>();
        /* verify message contents */
        for(Message message : messages.items())
            messageContents.put(message.name, message.data);
        assertEquals("Expect pub_text to be expected String", messageContents.get("pub_text"), textPayload);
        assertThat("Expect pub_binary to be expected byte[]", (byte[])messageContents.get("pub_binary"), equalTo(binaryPayload));
    }

    /**
     * Verify processing of a client-supplied message id
     */
    @Test
    public void channel_idempotent_publish_client_generated_single() {

        String channelName = "persisted:channel_idempotent_publish_client_generated_single_" + testParams.name;
        Channel pubChannel;
        final Message messageWithId = new Message("name_withId", "data_withId");
        messageWithId.id = "client_generated_id";
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            opts.useBinaryProtocol = true;
            opts.httpListener = new DebugOptions.RawHttpListener() {
                @Override
                public HttpCore.Response onRawHttpRequest(String id, HttpRequest request, String authHeader, Map<String, List<String>> requestHeaders, HttpCore.RequestBody requestBody) {
                    /* verify request body contains the supplied ids */
                    try {
                        if(request.getMethod().equalsIgnoreCase("POST")) {
                            Message[] requestedMessages = MessageSerializer.readMsgpack(requestBody.getEncoded());
                            assertEquals(requestedMessages[0].id, messageWithId.id);
                        }
                    } catch (AblyException e) {
                        e.printStackTrace();
                    }
                    return null;
                }

                @Override
                public void onRawHttpResponse(String id, String method, HttpCore.Response response) {}

                @Override
                public void onRawHttpException(String id, String method, Throwable t) {}
            };
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* first, publish messages */
            pubChannel = ably.channels.get(channelName);
            pubChannel.publish(new Message[]{messageWithId});
        } catch(AblyException e) {
            e.printStackTrace();
            fail("channel_idempotent_publish_client_generated_single: Unexpected exception");
            return;
        }

        /* get the history for this channel */
        try {
            PaginatedResult<Message> messages = pubChannel.history(new Param[]{new Param("direction", "forwards")});
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 1 message", messages.items().length, 1);
            assertEquals(messages.items()[0].id, messageWithId.id);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("channel_idempotent_publish_client_generated_single: Unexpected exception");
            return;
        }
    }

    /**
     * Verify processing of a client-supplied message id
     */
    @Test
    public void channel_idempotent_publish_client_generated_multiple() {

        String channelName = "persisted:channel_idempotent_publish_client_generated_multiple_" + testParams.name;
        Channel pubChannel;
        final Message messageWithId0 = new Message("name_withId", "data_withId");
        messageWithId0.id = "client_generated_id:0";
        final Message messageWithId1 = new Message("name_withId", "data_withId");
        messageWithId1.id = "client_generated_id:1";
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            opts.useBinaryProtocol = true;
            opts.httpListener = new DebugOptions.RawHttpListener() {
                @Override
                public HttpCore.Response onRawHttpRequest(String id, HttpRequest request, String authHeader, Map<String, List<String>> requestHeaders, HttpCore.RequestBody requestBody) {
                    /* verify request body contains the supplied ids */
                    try {
                        if(request.getMethod().equalsIgnoreCase("POST")) {
                            Message[] requestedMessages = MessageSerializer.readMsgpack(requestBody.getEncoded());
                            assertEquals(requestedMessages[0].id, messageWithId0.id);
                            assertEquals(requestedMessages[1].id, messageWithId1.id);
                        }
                    } catch (AblyException e) {
                        e.printStackTrace();
                    }
                    return null;
                }

                @Override
                public void onRawHttpResponse(String id, String method, HttpCore.Response response) {}

                @Override
                public void onRawHttpException(String id, String method, Throwable t) {}
            };
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* first, publish messages */
            pubChannel = ably.channels.get(channelName);
            pubChannel.publish(new Message[]{messageWithId0, messageWithId1});
        } catch(AblyException e) {
            e.printStackTrace();
            fail("channel_idempotent_publish_client_generated_multiple: Unexpected exception");
            return;
        }

        /* get the history for this channel */
        try {
            PaginatedResult<Message> messages = pubChannel.history(new Param[]{new Param("direction", "forwards")});
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 2 messages", messages.items().length, 2);
            assertEquals(messages.items()[0].id, messageWithId0.id);
            assertEquals(messages.items()[1].id, messageWithId1.id);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("channel_idempotent_publish_client_generated_multiple: Unexpected exception");
            return;
        }
    }

    static class FailFirstRequest implements DebugOptions.RawHttpListener {
        int postRequestCount = 0;
        final String expectedId;

        FailFirstRequest() {
            this.expectedId = null;
        }

        FailFirstRequest(String expectedId) {
            this.expectedId = expectedId;
        }

        @Override
        public HttpCore.Response onRawHttpRequest(String id, HttpRequest request, String authHeader, Map<String, List<String>> requestHeaders, HttpCore.RequestBody requestBody) {
            /* verify request body contains the supplied ids */
            try {
                if(request.getMethod().equalsIgnoreCase("POST")) {
                    ++postRequestCount;
                    Message[] requestedMessages = MessageSerializer.readMsgpack(requestBody.getEncoded());
                    if(expectedId != null) {
                        assertEquals(requestedMessages[0].id, expectedId);
                    }
                }
            } catch (AblyException e) {}
            return null;
        }

        @Override
        public void onRawHttpResponse(String id, String method, HttpCore.Response response) {
            if(method.equalsIgnoreCase("POST") && postRequestCount == 1) {
                response.statusCode = 500;
            }
        }

        @Override
        public void onRawHttpException(String id, String method, Throwable t) {}
    }

    /**
     * Verify processing of a client-supplied message id
     * Spec: RSL1k5
     */
    @Test
    public void channel_idempotent_publish_client_generated_retried() {
        String channelName = "persisted:channel_idempotent_publish_client_generated_retried_" + testParams.name;

        final Message messageWithId = new Message("name_withId", "data_withId");
        messageWithId.id = "client_generated_id";
        FailFirstRequest requestListener = new FailFirstRequest(messageWithId.id);

        try {
            final ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            final PubSubHttpClient ablyForToken = HttpClientFactory.create(optsForToken);
            Auth.AuthOptions httpAuthOptions = new Auth.AuthOptions() {{
                key = optsForToken.key;
                queryTime = true;
            }};

            Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(new Auth.TokenParams() {{ ttl = 8000L; }}, httpAuthOptions);

            DebugOptions opts = new DebugOptions(tokenDetails.token);
            fillInOptions(opts);
            opts.useBinaryProtocol = true;
            opts.httpListener = requestListener;
            /* generate a fallback which resolves to the same address, which the library will treat as a different host */
            opts.fallbackHosts = new String[]{ablyForToken.httpCore.getPrimaryHost().toUpperCase(Locale.ROOT)};
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* publish message */
            Channel pubChannel = ably.channels.get(channelName);
            pubChannel.publish(new Message[]{messageWithId});

            /* get the history for this channel */
            PaginatedResult<Message> messages = pubChannel.history(new Param[]{new Param("direction", "forwards")});
            assertEquals("Expected 2 requests", requestListener.postRequestCount, 2);
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 1 message", messages.items().length, 1);
            assertEquals(messages.items()[0].id, messageWithId.id);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("channel_idempotent_publish_client_generated_retried: Unexpected exception");
            return;
        }
    }

    /**
     * Verify processing of a library-generated message id
     */
    @Test
    public void channel_idempotent_publish_library_generated_multiple() {

        String channelName = "persisted:channel_idempotent_publish_library_generated_multiple_" + testParams.name;
        Channel pubChannel;
        final Message messageWithId0 = new Message("name0", "data0");
        final Message messageWithId1 = new Message("name1", "data1");
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            opts.idempotentRestPublishing = true;
            opts.useBinaryProtocol = true;
            opts.httpListener = new DebugOptions.RawHttpListener() {
                @Override
                public HttpCore.Response onRawHttpRequest(String id, HttpRequest request, String authHeader, Map<String, List<String>> requestHeaders, HttpCore.RequestBody requestBody) {
                    /* verify request body contains the library-generated ids */
                    try {
                        if(request.getMethod().equalsIgnoreCase("POST")) {
                            Message[] requestedMessages = MessageSerializer.readMsgpack(requestBody.getEncoded());
                            assertTrue(requestedMessages[0].id.endsWith(":0"));
                            assertTrue(requestedMessages[1].id.endsWith(":1"));
                        }
                    } catch (AblyException e) {
                        e.printStackTrace();
                    }
                    return null;
                }

                @Override
                public void onRawHttpResponse(String id, String method, HttpCore.Response response) {}

                @Override
                public void onRawHttpException(String id, String method, Throwable t) {}
            };
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* first, publish messages */
            pubChannel = ably.channels.get(channelName);
            pubChannel.publish(new Message[]{messageWithId0, messageWithId1});
        } catch(AblyException e) {
            e.printStackTrace();
            fail("channel_idempotent_publish_client_generated_multiple: Unexpected exception");
            return;
        }

        /* get the history for this channel */
        try {
            PaginatedResult<Message> messages = pubChannel.history(new Param[]{new Param("direction", "forwards")});
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 2 messages", messages.items().length, 2);
            assertEquals(messages.items()[0].id, messageWithId0.id);
            assertEquals(messages.items()[1].id, messageWithId1.id);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("channel_idempotent_publish_client_generated_multiple: Unexpected exception");
            return;
        }
    }

    /**
     * Verify processing of a library-generated message id
     * Spec: RSL1k4
     */
    @Test
    public void channel_idempotent_publish_library_generated_retried() {
        String channelName = "persisted:channel_idempotent_publish_library_generated_retried_" + testParams.name;

        final Message messageWithId = new Message("name0", "data0");
        FailFirstRequest requestListener = new FailFirstRequest();

        try {
            final ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            final PubSubHttpClient ablyForToken = HttpClientFactory.create(optsForToken);
            Auth.AuthOptions httpAuthOptions = new Auth.AuthOptions() {{
                key = optsForToken.key;
                queryTime = true;
            }};

            Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(new Auth.TokenParams() {{ ttl = 8000L; }}, httpAuthOptions);

            DebugOptions opts = new DebugOptions(tokenDetails.token);
            fillInOptions(opts);
            opts.idempotentRestPublishing = true;
            opts.useBinaryProtocol = true;
            opts.httpListener = requestListener;
            /* generate a fallback which resolves to the same address, which the library will treat as a different host */
            opts.fallbackHosts = new String[]{ablyForToken.httpCore.getPrimaryHost().toUpperCase(Locale.ROOT)};
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* publish message */
            Channel pubChannel = ably.channels.get(channelName);
            pubChannel.publish(new Message[]{messageWithId});

            /* get the history for this channel */
            PaginatedResult<Message> messages = pubChannel.history(new Param[]{new Param("direction", "forwards")});
            assertEquals("Expected 2 requests", requestListener.postRequestCount, 2);
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 1 message", messages.items().length, 1);
            assertEquals(messages.items()[0].id, messageWithId.id);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("channel_idempotent_publish_library_generated_retried: Unexpected exception");
            return;
        }
    }

}
