package io.ably.pubsub.test.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import io.ably.pubsub.transport.Defaults;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import com.google.gson.JsonElement;

import io.ably.pubsub.debug.DebugOptions;
import io.ably.pubsub.http.HttpConstants;
import io.ably.pubsub.http.HttpUtils;
import io.ably.pubsub.http.HttpClientFactory;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.http.Channel;
import io.ably.pubsub.test.common.Helpers.RawHttpRequest;
import io.ably.pubsub.test.common.Helpers.RawHttpTracker;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.AsyncHttpPaginatedResponse;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.HttpPaginatedResponse;
import io.ably.pubsub.types.Message;
import io.ably.pubsub.types.PaginatedResult;
import io.ably.pubsub.types.Param;
import net.jodah.concurrentunit.Waiter;

/* Spec: RSC19 */
public class HttpRequestTest extends ParameterizedTest {

    private PubSubHttpClient setupAbly;
    private String channelName;
    private String channelAltName;
    private String channelNamePrefix;
    private String channelPath;
    private String channelsPath;
    private String channelMessagesPath;

    @Rule
    public Timeout testTimeout = Timeout.seconds(30);

    @Before
    public void setUpBefore() throws Exception {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        setupAbly = HttpClientFactory.create(opts);
        channelNamePrefix = "persisted:rest_request_" + testParams.name;
        channelName = channelNamePrefix + "_channel";
        channelAltName = channelNamePrefix + "_alt_channel";
        channelsPath = "/channels";
        channelPath = channelsPath + "/" + channelName;
        channelMessagesPath = channelPath + "/messages";

        /* publish events */
        Channel channel = setupAbly.channels.get(channelName);
        for(int i = 0; i < 4; i++) {
            channel.publish("Test event", "Test data " + i);
        }
        Channel altChannel = setupAbly.channels.get(channelAltName);
        for(int i = 0; i < 4; i++) {
            altChannel.publish("Test event", "Test alt data " + i);
        }

        /* wait to persist */
        try { Thread.sleep(1000L); } catch(InterruptedException ie) {}
    }

    /**
     * Get channel details using the request() API
     * Spec: RSC19a, RSC19d, HP1, HP3, HP4, HP5, HP8
     */
    @Test
    public void request_simple() {
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            RawHttpTracker httpListener = new RawHttpTracker();
            opts.httpListener = httpListener;
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            Param[] testParams = new Param[] { new Param("testParam", "testValue") };
            Param[] testHeaders = new Param[] { new Param("x-test-header", "testValue") };
            HttpPaginatedResponse channelResponse = ably.request(HttpConstants.Methods.GET, channelPath, testParams, null, testHeaders);

            /* check HttpPagninatedResponse details are present */
            assertEquals("Verify statusCode is present", channelResponse.statusCode, 200);
            assertTrue("Verify success is indicated", channelResponse.success);
            assertNull("Verify no error is indicated", channelResponse.errorMessage);
            Map<String, Param> headers = HttpUtils.indexParams(channelResponse.headers);
            assertEquals("Verify Content-Type header is present", headers.get("content-type").value, "application/json");

            /* check it looks like a ChannelDetails */
            assertNotNull("Verify a result is returned", channelResponse);
            JsonElement[] items = channelResponse.items();
            assertEquals("Verify a single items is returned", items.length, 1);
            JsonElement channelDetails = items[0];
            assertTrue("Verify an object is returned", channelDetails.isJsonObject());
            assertTrue("Verify channelId member is present", channelDetails.getAsJsonObject().has("channelId"));
            assertEquals("Verify channelId member is channelName", channelName, channelDetails.getAsJsonObject().get("channelId").getAsString());

            /* check request has expected attributes; use last request in case of challenges preceding sending auth header */
            RawHttpRequest req = httpListener.getLastRequest();
            /* Spec: RSC19b */
            assertNotNull("Verify Authorization header present", httpListener.getRequestHeader(req.id, "Authorization"));
            /* Spec: RSC19c */
            assertTrue("Verify Accept header present", httpListener.getRequestHeader(req.id, "Accept").contains("application/json"));
            assertTrue("Verify Content-Type header present", httpListener.getResponseHeader(req.id, "Content-Type").contains("application/json"));
        } catch(AblyException e) {
            e.printStackTrace();
            fail("request_simple: Unexpected exception");
            return;
        }
    }

    /**
     * Get channel details using the requestAsync() API
     * Spec: RSC19a, RSC19d, HP1, HP3, HP4, HP5, HP8
     */
    @Test
    public void request_simple_async() {
        final Waiter waiter = new Waiter();
        DebugOptions opts;
        try {
            opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            final RawHttpTracker httpListener = new RawHttpTracker();
            opts.httpListener = httpListener;
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            ably.requestAsync(HttpConstants.Methods.GET, channelPath, null, null, null, new AsyncHttpPaginatedResponse.Callback() {
                @Override
                public void onResponse(AsyncHttpPaginatedResponse channelResponse) {

                    /* check HttpPaginatedResponse details are present */
                    waiter.assertEquals(channelResponse.statusCode, 200);
                    waiter.assertTrue(channelResponse.success);
                    waiter.assertNull(channelResponse.errorMessage);
                    Map<String, Param> headers = HttpUtils.indexParams(channelResponse.headers);
                    waiter.assertEquals(headers.get("content-type").value, "application/json");

                    /* check it looks like a ChannelDetails */
                    /* Verify a result is returned */
                    waiter.assertNotNull(channelResponse);
                    JsonElement[] items = channelResponse.items();
                    waiter.assertEquals(items.length, 1);
                    JsonElement channelDetails = items[0];
                    waiter.assertTrue(channelDetails.isJsonObject());
                    waiter.assertTrue(channelDetails.getAsJsonObject().has("channelId"));
                    waiter.assertEquals(channelName, channelDetails.getAsJsonObject().get("channelId").getAsString());

                    /* check request has expected attributes */
                    RawHttpRequest req = httpListener.values().iterator().next();
                    /* Spec: RSC19b */
                    waiter.assertNotNull(httpListener.getRequestHeader(req.id, "Authorization"));
                    /* Spec: RSC19c */
                    waiter.assertTrue(httpListener.getRequestHeader(req.id, "Accept").contains("application/json"));
                    waiter.assertTrue(httpListener.getResponseHeader(req.id, "Content-Type").contains("application/json"));
                    waiter.resume();
                }
                @Override
                public void onError(ErrorInfo reason) {
                    waiter.fail("request_simple_async: Unexpected exception");
                    waiter.resume();
                }
            });

            try {
                waiter.await(15000);
            } catch (TimeoutException e) {
                fail("request_simple_async: Operation timed out");
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("request_simple_async: Unexpected exception");
        }
    }

    /**
     * Get channel details using the paginatedRequest() API
     * Spec: HP2
     */
    @Test
    public void request_paginated() {
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            Param[] params = new Param[] { new Param("prefix", channelNamePrefix) };
            Param[] requestHeaders = new Param[] { new Param(Defaults.ABLY_PROTOCOL_VERSION_HEADER, 2) };
            HttpPaginatedResponse channelsResponse =
                ably.request(HttpConstants.Methods.GET, channelsPath, params, null, requestHeaders);

            /* check HttpPagninatedResponse details are present */
            assertEquals("Verify statusCode is present", channelsResponse.statusCode, 200);
            assertTrue("Verify success is indicated", channelsResponse.success);
            assertNull("Verify no error is indicated", channelsResponse.errorMessage);
            Map<String, Param> headers = HttpUtils.indexParams(channelsResponse.headers);
            assertEquals("Verify Content-Type header is present", headers.get("content-type").value, "application/json");

            /* check it looks like an array of ChannelDetails */
            assertNotNull("Verify a result is returned", channelsResponse);
            JsonElement[] items = channelsResponse.items();
            assertTrue("Verify at least two channels are returned", items.length >= 2);
            for(int i = 0; i < items.length; i++) {
                assertTrue("Verify channelId member is a matching channelName", items[i].getAsJsonObject().get("channelId").getAsString().startsWith(channelNamePrefix));
            }

            /* check that there is either no next link, or no results from it */
            if(channelsResponse.hasNext()) {
                channelsResponse = channelsResponse.next();
                items = channelsResponse.items();
                assertEquals("Verify no further channels are returned", items.length, 0);
            }
        } catch(AblyException e) {
            e.printStackTrace();
            fail("request_simple: Unexpected exception");
            return;
        }
    }

    /**
     * Get channel details using the paginatedRequestAsync() API
     * Spec: HP2
     */
    @Test
    public void request_paginated_async() {
        final Waiter waiter = new Waiter();
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            Param[] params = new Param[] { new Param("prefix", channelNamePrefix) };
            Param[] requestHeaders = new Param[] { new Param(Defaults.ABLY_PROTOCOL_VERSION_HEADER, 2) };
            ably.requestAsync(HttpConstants.Methods.GET, channelsPath, params, null, requestHeaders, new AsyncHttpPaginatedResponse.Callback() {
                @Override
                public void onResponse(AsyncHttpPaginatedResponse channelResponse) {

                    /* check HttpPaginatedResponse details are present */
                    waiter.assertEquals(channelResponse.statusCode, 200);
                    waiter.assertTrue(channelResponse.success);
                    waiter.assertNull(channelResponse.errorMessage);
                    Map<String, Param> headers = HttpUtils.indexParams(channelResponse.headers);
                    waiter.assertEquals(headers.get("content-type").value, "application/json");

                    /* check it looks like an array of ChannelDetails */
                    waiter.assertNotNull(channelResponse);
                    JsonElement[] items = channelResponse.items();
                    waiter.assertTrue(items.length >= 2);
                    for(int i = 0; i < items.length; i++) {
                        waiter.assertTrue(items[i].getAsJsonObject().get("channelId").getAsString().startsWith(channelNamePrefix));
                    }
                    /* check that there is either no next link, or no results from it */
                    if(!channelResponse.hasNext()) {
                        waiter.resume();
                        return;
                    }
                    channelResponse.next(new AsyncHttpPaginatedResponse.Callback() {
                        @Override
                        public void onResponse(AsyncHttpPaginatedResponse channelResponse) {
                            JsonElement[] items = channelResponse.items();
                            assertEquals("Verify no further channels are returned", items.length, 0);
                            waiter.resume();
                        }
                        @Override
                        public void onError(ErrorInfo reason) {
                            waiter.fail("request_paginated_async: Unexpected exception");
                            waiter.resume();
                        }
                    });
                }
                @Override
                public void onError(ErrorInfo reason) {
                    waiter.fail("request_paginated_async: Unexpected exception");
                    waiter.resume();
                }
            });

            try {
                waiter.await(15000);
            } catch (TimeoutException e) {
                fail("request_paginated_async: Operation timed out");
            }
        } catch(AblyException e) {
            e.printStackTrace();
            fail("request_paginated_async: Unexpected exception");
            return;
        }
    }

    /**
     * Get channel details using the paginatedRequest() API with a specified limit,
     * checking pagination links
     * Spec: HP2
     */
    @Test
    public void request_paginated_limit() {
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            Param[] params = new Param[] { new Param("prefix", channelNamePrefix), new Param("limit", "1") };
            Param[] requestHeaders = new Param[] { new Param(Defaults.ABLY_PROTOCOL_VERSION_HEADER, 2) };
            HttpPaginatedResponse channelsResponse =
                ably.request(HttpConstants.Methods.GET, channelsPath, params, null, requestHeaders);

            /* check HttpPagninatedResponse details are present */
            assertEquals("Verify statusCode is present", channelsResponse.statusCode, 200);
            assertTrue("Verify success is indicated", channelsResponse.success);
            assertNull("Verify no error is indicated", channelsResponse.errorMessage);
            Map<String, Param> headers = HttpUtils.indexParams(channelsResponse.headers);
            assertEquals("Verify Content-Type header is present", headers.get("content-type").value, "application/json");

            /* check it looks like an array of ChannelDetails */
            assertNotNull("Verify a result is returned", channelsResponse);
            JsonElement[] items = channelsResponse.items();
            assertTrue("Verify one channel is returned", items.length == 1);
            for(int i = 0; i < items.length; i++) {
                assertTrue("Verify channelId member is a matching channelName", items[i].getAsJsonObject().get("channelId").getAsString().startsWith(channelNamePrefix));
            }

            /* get next page */
            channelsResponse = channelsResponse.next();
            assertNotNull("Verify a result is returned", channelsResponse);
            items = channelsResponse.items();
            assertTrue("Verify one channel is returned", items.length == 1);
            for(int i = 0; i < items.length; i++) {
                assertTrue("Verify channelId member is a matching channelName", items[i].getAsJsonObject().get("channelId").getAsString().startsWith(channelNamePrefix));
            }

            /* get first page */
            HttpPaginatedResponse firstResponse = channelsResponse.first();
            assertNotNull("Verify a result is returned", firstResponse);
            items = channelsResponse.items();
            assertTrue("Verify one channel is returned", items.length == 1);
            for(int i = 0; i < items.length; i++) {
                assertTrue("Verify channelId member is a matching channelName", items[i].getAsJsonObject().get("channelId").getAsString().startsWith(channelNamePrefix));
            }

            /* check that there is either no next link, or no results from it */
            if(channelsResponse.hasNext()) {
                channelsResponse = channelsResponse.next();
                items = channelsResponse.items();
                assertEquals("Verify no further channels are returned", items.length, 0);
            }
        } catch(AblyException e) {
            e.printStackTrace();
            fail("request_paginated_limit: Unexpected exception");
            return;
        }
    }

    /**
     * Get channel details using the paginatedRequestAsync() API with a specified limit,
     * checking pagination links
     * Spec: HP2
     */
    @Test
    public void request_paginated_async_limit() {
        final Waiter waiter = new Waiter();
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            Param[] params = new Param[] { new Param("prefix", channelNamePrefix), new Param("limit", "1") };
            Param[] headers = new Param[] { new Param(Defaults.ABLY_PROTOCOL_VERSION_HEADER, 2) };
            ably.requestAsync(HttpConstants.Methods.GET, channelsPath, params, null, headers, new AsyncHttpPaginatedResponse.Callback() {
                @Override
                public void onResponse(AsyncHttpPaginatedResponse channelsResponse) {

                    /* check HttpPagninatedResponse details are present */
                    assertEquals("Verify statusCode is present", channelsResponse.statusCode, 200);
                    assertTrue("Verify success is indicated", channelsResponse.success);
                    assertNull("Verify no error is indicated", channelsResponse.errorMessage);
                    Map<String, Param> headers = HttpUtils.indexParams(channelsResponse.headers);
                    assertEquals("Verify Content-Type header is present", headers.get("content-type").value, "application/json");

                    /* check it looks like an array of ChannelDetails */
                    waiter.assertNotNull(channelsResponse);
                    JsonElement[] items = channelsResponse.items();
                    waiter.assertTrue(items.length == 1);
                    for(int i = 0; i < items.length; i++) {
                        waiter.assertTrue(items[i].getAsJsonObject().get("channelId").getAsString().startsWith(channelNamePrefix));
                    }
                    channelsResponse.next(new AsyncHttpPaginatedResponse.Callback() {
                        @Override
                        public void onResponse(final AsyncHttpPaginatedResponse channelsResponse) {
                            /* check it looks like an array of ChannelDetails */
                            waiter.assertNotNull(channelsResponse);
                            JsonElement[] items = channelsResponse.items();
                            waiter.assertTrue(items.length == 1);
                            for(int i = 0; i < items.length; i++) {
                                waiter.assertTrue(items[i].getAsJsonObject().get("channelId").getAsString().startsWith(channelNamePrefix));
                            }

                            /* check that there is a first link */
                            channelsResponse.first(new AsyncHttpPaginatedResponse.Callback() {
                                @Override
                                public void onResponse(AsyncHttpPaginatedResponse firstResponse) {
                                    waiter.assertNotNull(firstResponse);
                                    JsonElement[] items = firstResponse.items();
                                    waiter.assertTrue(items.length == 1);
                                    for(int i = 0; i < items.length; i++) {
                                        waiter.assertTrue(items[i].getAsJsonObject().get("channelId").getAsString().startsWith(channelNamePrefix));
                                    }

                                    /* check that there is either no next link, or no results from it */
                                    if(!channelsResponse.hasNext()) {
                                        waiter.resume();
                                        return;
                                    }
                                    channelsResponse.next(new AsyncHttpPaginatedResponse.Callback() {
                                        @Override
                                        public void onResponse(AsyncHttpPaginatedResponse result) {
                                            JsonElement[] items = result.items();
                                            waiter.assertEquals(items.length, 0);
                                            waiter.resume();
                                        }
                                        @Override
                                        public void onError(ErrorInfo reason) {
                                            waiter.fail("request_paginated_async_limit: Unexpected exception");
                                            waiter.resume();
                                        }
                                    });
                                }
                                @Override
                                public void onError(ErrorInfo reason) {
                                    waiter.fail("request_paginated_async_limit: Unexpected exception");
                                    waiter.resume();
                                }});
                        }
                        @Override
                        public void onError(ErrorInfo reason) {
                            waiter.fail("request_paginated_async_limit: Unexpected exception");
                            waiter.resume();
                        }
                    });
                }
                @Override
                public void onError(ErrorInfo reason) {
                    waiter.fail("request_paginated_async_limit: Unexpected exception");
                    waiter.resume();
                }
            });

            try {
                waiter.await(15000);
            } catch (TimeoutException e) {
                fail("request_paginated_async: Operation timed out");
            }
        } catch(AblyException e) {
            e.printStackTrace();
            fail("request_paginated_async_limit: Unexpected exception");
            return;
        }
    }

    /**
     * Publish a message using the request() API
     * Spec: RSC19a, RSC19b
     *
     */
    @Test
    public void request_post() {
        final String messageData = "Test data (request_post)";
        DebugOptions opts;
        try {
            opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            final RawHttpTracker httpListener = new RawHttpTracker();
            opts.httpListener = httpListener;
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* publish a message */
            Message message = new Message("Test event", messageData);
            HttpUtils.JsonRequestBody requestBody = new HttpUtils.JsonRequestBody(message);
            HttpPaginatedResponse publishResponse = ably.request(HttpConstants.Methods.POST, channelMessagesPath, null, requestBody, null);
            RawHttpRequest req = httpListener.getLastRequest();

            /* check HttpPagninatedResponse details are present */
            assertEquals("Verify statusCode is present", publishResponse.statusCode, 201);
            assertTrue("Verify success is indicated", publishResponse.success);
            assertNull("Verify no error is indicated", publishResponse.errorMessage);

            /* wait to persist */
            try { Thread.sleep(1000L); } catch(InterruptedException ie) {}

            /* get the history */
            Param[] params = new Param[] { new Param("limit", "1") };
            PaginatedResult<Message> resultPage = ably.channels.get(channelName).history(params);

            /* check it looks like a result page */
            assertNotNull("Verify a result is returned", resultPage);
            assertTrue("Verify an single message is returned", resultPage.items().length == 1);
            assertEquals("Verify returned message was the one posted", messageData, resultPage.items()[0].data);

            /* check request has expected attributes */
            /* Spec: RSC19b */
            assertNotNull("Verify Authorization header present", httpListener.getRequestHeader(req.id, "authorization"));
            /* Spec: RSC19c */
            assertTrue("Verify Accept header present", httpListener.getRequestHeader(req.id, "Accept").contains("application/json"));
            assertTrue("Verify Content-Type header present", httpListener.getRequestHeader(req.id, "Content-Type").contains("application/json"));
            assertTrue("Verify Content-Type header present", httpListener.getResponseHeader(req.id, "Content-Type").contains("application/json"));
        } catch(AblyException e) {
            e.printStackTrace();
            fail("request_post: Unexpected exception");
            return;
        }
    }

    /**
     * Publish a message using the requestAsync() API
     * Spec: RSC19a, RSC19b
     */
    @Test
    public void request_post_async() {
        final Waiter waiter = new Waiter();
        final String messageData = "Test data (request_post_async)";
        DebugOptions opts;
        try {
            opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            final RawHttpTracker httpListener = new RawHttpTracker();
            opts.httpListener = httpListener;
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* publish a message */
            Message message = new Message("Test event", messageData);
            HttpUtils.JsonRequestBody requestBody = new HttpUtils.JsonRequestBody(message);
            Param[] requestHeaders = new Param[] { new Param(Defaults.ABLY_PROTOCOL_VERSION_HEADER, 2) };
            ably.requestAsync(HttpConstants.Methods.POST, channelMessagesPath, null, requestBody, requestHeaders, new AsyncHttpPaginatedResponse.Callback() {
                @Override
                public void onResponse(AsyncHttpPaginatedResponse publishResponse) {

                    /* check HttpPaginatedResponse details are present */
                    assertEquals("Verify statusCode is present", publishResponse.statusCode, 201);
                    assertTrue("Verify success is indicated", publishResponse.success);
                    assertNull("Verify no error is indicated", publishResponse.errorMessage);

                    /* wait to persist */
                    try { Thread.sleep(1000L); } catch(InterruptedException ie) {}

                    /* get the history */
                    Param[] params = new Param[] { new Param("limit", "1") };
                    PaginatedResult<Message> resultPage;
                    try {
                        resultPage = setupAbly.channels.get(channelName).history(params);

                        /* check it looks like a result page */
                        waiter.assertNotNull(resultPage);
                        waiter.assertTrue(resultPage.items().length == 1);
                        waiter.assertEquals(messageData, resultPage.items()[0].data);

                        /* check request has expected attributes */
                        RawHttpRequest req = httpListener.values().iterator().next();
                        /* Spec: RSC19b */
                        waiter.assertNotNull(httpListener.getRequestHeader(req.id, "Authorization"));
                        /* Spec: RSC19c */
                        waiter.assertTrue(httpListener.getRequestHeader(req.id, "Accept").contains("application/json"));
                        waiter.assertTrue(httpListener.getRequestHeader(req.id, "Content-Type").contains("application/json"));
                        waiter.assertTrue(httpListener.getResponseHeader(req.id, "Content-Type").contains("application/json"));
                        waiter.resume();
                    } catch (AblyException e) {
                        e.printStackTrace();
                        waiter.fail("request_post_async: Unexpected exception");
                        waiter.resume();
                    }
                }
                @Override
                public void onError(ErrorInfo reason) {
                    waiter.fail("request_post_async: Unexpected exception");
                    waiter.resume();
                }
            });

            try {
                waiter.await(15000);
            } catch (TimeoutException e) {
                fail("request_paginated_async: Operation timed out");
            }
        } catch(AblyException e) {
            e.printStackTrace();
            fail("request_post_async: Unexpected exception");
            return;
        }
    }

    /**
     * Verify 400 error responses are indicated with an HttpPaginatedResponse
     * Spec: RSC19e, HP4, HP5, HP6, HP7
     */
    @Test
    public void request_404() {
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            HttpPaginatedResponse errorResponse = ably.request(HttpConstants.Methods.GET, "/non-existent-path", null, null, null);

            /* check HttpPaginatedResponse details are present */
            assertEquals("Verify statusCode is present", errorResponse.statusCode, 404);
            assertFalse("Verify non-success is indicated", errorResponse.success);
            assertNotNull("Verify error is indicated", errorResponse.errorMessage);
            Map<String, Param> headers = HttpUtils.indexParams(errorResponse.headers);
            assertEquals("Verify Content-Type header is present", headers.get("content-type").value, "application/json");
        } catch(AblyException e) {
            e.printStackTrace();
            fail("request_404: Unexpected exception");
        }
    }

    /**
     * Verify 400 error responses are indicated with an response callback
     * Spec: RSC19e, HP4, HP5, HP6, HP7
     */
    @Test
    public void request_404_async() {
        try {
            final Waiter waiter = new Waiter();
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            ably.requestAsync(HttpConstants.Methods.GET, "/non-existent-path", null, null, null, new AsyncHttpPaginatedResponse.Callback() {
                @Override
                public void onResponse(AsyncHttpPaginatedResponse response) {

                    /* check HttpPaginatedResponse details are present */
                    waiter.assertEquals(response.statusCode, 404);
                    waiter.assertFalse(response.success);
                    waiter.assertNotNull(response.errorMessage);
                    waiter.assertTrue(response.errorCode != 0);
                    Map<String, Param> headers = HttpUtils.indexParams(response.headers);
                    waiter.assertEquals(headers.get("content-type").value, "application/json");
                    waiter.resume();
                }
                @Override
                public void onError(ErrorInfo reason) {
                    waiter.fail("request_404_async: Expected a response callback");
                    waiter.resume();
                }
            });

            try {
                waiter.await(15000);
            } catch (TimeoutException e) {
                fail("request_404_async: Operation timed out");
            }
        } catch(AblyException e) {
            e.printStackTrace();
            fail("request_404_async: Unexpected exception");
            return;
        }
    }


    /**
     * Verify 500 error responses are indicated with an exception
     * Spec: RSC19e
     */
    @Test
    public void request_500() {
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            opts.environment = "non.existent.env";
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            ably.request(HttpConstants.Methods.GET, "/", null, null, null);
            fail("request_500: Expected an exception");
        } catch(AblyException e) {
            assertEquals("Verify expected status code in error response", e.errorInfo.statusCode, 500);
            return;
        }
    }

    /**
     * Verify 500 error responses are indicated with an error callback
     * Spec: RSC19e
     */
    @Test
    public void request_500_async() {
        try {
            final Waiter waiter = new Waiter();
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            opts.environment = "non.existent.env";
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            ably.requestAsync(HttpConstants.Methods.GET, "/", null, null, null, new AsyncHttpPaginatedResponse.Callback() {
                @Override
                public void onResponse(AsyncHttpPaginatedResponse response) {
                    waiter.fail("request_500_async: Expected an error");
                    waiter.resume();
                }
                @Override
                public void onError(ErrorInfo reason) {
                    waiter.assertEquals(reason.statusCode, 500);
                    waiter.resume();
                }
            });

            try {
                waiter.await(15000);
            } catch (TimeoutException e) {
                fail("request_500_async: Operation timed out");
            }
        } catch(AblyException e) {
            e.printStackTrace();
            fail("request_500_async: Unexpected exception");
            return;
        }
    }

    /**
     * Trying to publish a message on behalf of another client with invalid connection key.
     * Specs: TM2h
     */
    @Test
    public void request_post_with_invalid_connection_key() throws AblyException {
        // Given
        DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
        fillInOptions(opts);
        opts.httpListener = new RawHttpTracker();
        PubSubHttpClient ably = HttpClientFactory.create(opts);
        Message message = new Message("Test event", "Test data (invalid key)");
        message.connectionKey = "invalid";
        HttpUtils.JsonRequestBody requestBody = new HttpUtils.JsonRequestBody(message);

        // When
        HttpPaginatedResponse publishResponse = ably.request(HttpConstants.Methods.POST, channelMessagesPath, null, requestBody, null);

        // Then
        assertFalse("Verify failure is indicated", publishResponse.success);
        assertNotNull("Verify error is indicated", publishResponse.errorMessage);
        assertEquals("Verify statusCode is present", publishResponse.statusCode, 400);
        assertEquals("Verify errorCode is present", publishResponse.errorCode, 40006);
    }
}
