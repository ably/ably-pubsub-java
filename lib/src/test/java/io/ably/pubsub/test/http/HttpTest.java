package io.ably.pubsub.test.http;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;
import io.ably.pubsub.http.AsyncHttpScheduler;
import io.ably.pubsub.http.Http;
import io.ably.pubsub.http.HttpConstants;
import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.http.HttpHelpers;
import io.ably.pubsub.http.SyncHttpScheduler;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.test.util.EmptyPlatformAgentProvider;
import io.ably.pubsub.test.util.StatusHandler;
import io.ably.pubsub.test.util.TimeHandler;
import io.ably.pubsub.transport.Defaults;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.util.PlatformAgentProvider;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import io.ably.pubsub.debug.DebugOptions;
import io.ably.pubsub.network.HttpRequest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

/**
 * Created by gokhanbarisaker on 2/2/16.
 */
public class HttpTest {

    private static final String PATTERN_HOST_FALLBACK = "(?i)[a-e]\\.ably-realtime.com";
    private static final String CUSTOM_PATTERN_HOST_FALLBACK = "(?i)[f-k]\\.ably-realtime.com";
    private static final String[] CUSTOM_HOSTS = { "f.ably-realtime.com", "g.ably-realtime.com", "h.ably-realtime.com", "i.ably-realtime.com", "j.ably-realtime.com", "k.ably-realtime.com" };
    private static final String TEST_SERVER_HOST = "localhost";
    private static final int TEST_SERVER_PORT = 27331;
    private static final PlatformAgentProvider platformAgentProvider = new EmptyPlatformAgentProvider();

    @Rule
    public Timeout testTimeout = Timeout.seconds(60);

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private static RouterNanoHTTPD server;

    @BeforeClass
    public static void setUp() throws IOException {
        server = new RouterNanoHTTPD(TEST_SERVER_PORT);
        server.addRoute("/status/:code", StatusHandler.class);
        server.addRoute("/time", TimeHandler.class);
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


    /*******************************************
     * Spec: RSC15
     *******************************************/

    /**
     * <p>
     * Validates {@code HttpCore} performs fallback behavior httpMaxRetryCount number of times at most,
     * when host & fallback hosts are unreachable. Then, finally throws an error.
     * </p>
     * <p>
     * Spec: RSC15a
     * </p>
     *
     * @throws Exception
     */
    @Ignore("FIXME: flaky test")
    @Test
    public void http_ably_execute_fallback() throws AblyException {
        ClientOptions options = new ClientOptions();
        options.tls = false;
        /* Select a port that will be refused immediately by the production host */
        options.port = 7777;

        /* Create a list to capture the host of URL arguments that get called with httpExecute method.
         * This will later be used to validate hosts used for requests
         */
        ArrayList<String> urlHostArgumentStack = new ArrayList<>(4);

        /*
         * Extend the httpCore, so that we can capture provided url arguments without mocking and changing its organic behavior.
         */
        HttpCore httpCore = new HttpCore(options, null, platformAgentProvider) {
            /* Store only string representations to avoid try/catch blocks */
            List<String> urlArgumentStack;

            @Override
            public <T> T httpExecute(URL url, String method, Param[] headers, RequestBody requestBody, boolean withCredentials, ResponseHandler<T> responseHandler) throws AblyException {
                // Store a copy of given argument
                urlArgumentStack.add(url.getHost());

                // Execute the original method without changing behavior
                return super.httpExecute(url, method, headers, requestBody, withCredentials, responseHandler);
            }

            public HttpCore setUrlArgumentStack(List<String> urlArgumentStack) {
                this.urlArgumentStack = urlArgumentStack;
                return this;
            }
        }.setUrlArgumentStack(urlHostArgumentStack);

        Http http = new Http(new AsyncHttpScheduler(httpCore, options), new SyncHttpScheduler(httpCore));
        try {
            HttpHelpers.ablyHttpExecute(
                    http, "/path/to/fallback", /* Ignore path */
                    HttpConstants.Methods.GET, /* Ignore method */
                    new Param[0], /* Ignore headers */
                    new Param[0], /* Ignore params */
                    null, /* Ignore requestBody */
                    null, /* Ignore requestHandler */
                    false /* Ignore requireAblyAuth */
            );
        } catch (AblyException e) {
            /* Verify that,
             *      - an {@code AblyException} with {@code ErrorInfo} having a `50x` status code is thrown.
             */
            assertThat(e.errorInfo.statusCode / 10, is(equalTo(50)));
        }

        /* Verify that,
         *      - {code HttpCore#httpExecute} have been called with (httpMaxRetryCount + 1) URLs
         *      - first call executed against production rest host
         *      - other calls executed against a random fallback host
         */
        int expectedCallCount = options.httpMaxRetryCount + 1;
        assertThat(urlHostArgumentStack.size(), is(equalTo(expectedCallCount)));
        assertThat(urlHostArgumentStack.get(0), is(equalTo(Defaults.HOST_REST)));

        for (int i = 1; i < expectedCallCount; i++) {
            urlHostArgumentStack.get(i).matches(PATTERN_HOST_FALLBACK);
        }
    }

    /**
     * Validates that fallbacks are disabled when ClientOptions#fallbackHosts are set to empty and the only host used is Defaults#HOST_REST
     * @throws AblyException
     */

    @Test
    public void http_ably_execute_null_fallbacks() throws AblyException {
        ClientOptions options = new ClientOptions();
        options.tls = false;
        options.fallbackHosts = new String[0];
        options.port = 7777;

        ArrayList<String> urlHostArgumentStack = new ArrayList<>();

        HttpCore httpCore = new HttpCore(options, null, platformAgentProvider) {
            List<String> urlArgumentStack;

            @Override
            public <T> T httpExecuteWithRetry(URL url, String method, Param[] headers, RequestBody requestBody, ResponseHandler<T> responseHandler, boolean allowAblyAuth) throws AblyException {
                urlArgumentStack.add(url.getHost());
                return super.httpExecuteWithRetry(url, method, headers, requestBody, responseHandler, allowAblyAuth);
            }

            public HttpCore setUrlArgumentStack(List<String> urlArgumentStack) {
                this.urlArgumentStack = urlArgumentStack;
                return this;
            }
        }.setUrlArgumentStack(urlHostArgumentStack);

        Http http = new Http(new AsyncHttpScheduler(httpCore, options), new SyncHttpScheduler(httpCore));
        try {
            HttpHelpers.ablyHttpExecute(
                    http, "/path/to/fallback", /* Ignore path */
                    HttpConstants.Methods.GET, /* Ignore method */
                    new Param[0], /* Ignore headers */
                    new Param[0], /* Ignore params */
                    null, /* Ignore requestBody */
                    null, /* Ignore requestHandler */
                    false /* Ignore requireAblyAuth */
            );
        } catch (AblyException.HostFailedException e) {
            /* Verify that,
             *      - a {@code AblyException.HostFailedException} is thrown.
             */
            assertTrue(true);
        } catch (AblyException e) {
            assertTrue(false);
        }

        /* Validate that only one host is used and it is Defaults#HOST_REST */
        Assert.assertTrue(urlHostArgumentStack.size() == 1);
        assertThat(urlHostArgumentStack.get(0), is(equalTo(Defaults.HOST_REST)));
    }

    /**
     * <strong>This method mocks the API behavior</strong>
     * <p>
     * Validates {@link HttpCore} is using first attempt to default primary host
     * for every new HTTP request, after expiry of any fallbackRetryTimeout.
     * </p>
     * <p>
     * Spec: RSC15e, RSC15f
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void http_ably_execute_first_attempt_to_default() throws AblyException {
        String hostExpectedPattern = PATTERN_HOST_FALLBACK;
        ClientOptions options = new ClientOptions("not:a.key");
        options.httpMaxRetryCount = 1;
        options.fallbackRetryTimeout = 100;
        PubSubHttpClient ably = new PubSubHttpClient(options);

        HttpCore httpCore = Mockito.spy(new HttpCore(ably.options, ably.auth, platformAgentProvider));

        String responseExpected = "Lorem Ipsum";
        ArgumentCaptor<URL> url = ArgumentCaptor.forClass(URL.class);

        /* Partially mock httpCore */
        Answer answer = new GrumpyAnswer(
                1, /* Throw exception */
                AblyException.fromErrorInfo(ErrorInfo.fromResponseStatus("Internal Server Error", 500)), /* That is HostFailedException */
                responseExpected /* Then return a valid response with second call */
        );

        doAnswer(answer) /* Behave as defined above */
                .when(httpCore) /* when following method is executed on {@code HttpCore} instance */
                .httpExecute(
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid fallback url */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );

        Http http = new Http(new AsyncHttpScheduler(httpCore, options), new SyncHttpScheduler(httpCore));
        String responseActual = (String) HttpHelpers.ablyHttpExecute(
                http, "", /* Ignore */
                "", /* Ignore */
                new Param[0], /* Ignore */
                new Param[0], /* Ignore */
                mock(HttpCore.RequestBody.class), /* Ignore */
                mock(HttpCore.ResponseHandler.class), /* Ignore */
                false /* Ignore */
        );

        assertThat("Unexpected default primary host", url.getAllValues().get(0).getHost(), is(equalTo(Defaults.HOST_REST)));
        assertThat("Unexpected host fallback", url.getAllValues().get(1).getHost().matches(hostExpectedPattern), is(true));
        assertThat("Unexpected response", responseActual, is(equalTo(responseExpected)));

        /* wait for expiry of fallbackRetryTimeout */
        try {
            Thread.sleep(200L);
        } catch(InterruptedException ie) {}

        String responseActual2 = (String) HttpHelpers.ablyHttpExecute(
                http, "", /* Ignore */
                "", /* Ignore */
                new Param[0], /* Ignore */
                new Param[0], /* Ignore */
                mock(HttpCore.RequestBody.class), /* Ignore */
                mock(HttpCore.ResponseHandler.class), /* Ignore */
                false /* Ignore */
        );

        /* Verify call causes captor to capture same arguments thrice.
         * Do the validation, after we completed the {@code ArgumentCaptor} related assertions */
        verify(httpCore, times(3))
                .httpExecute( /* Just validating call counter. Ignore following parameters */
                        any(URL.class), /* Ignore */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );
        assertThat("Unexpected default primary host", url.getAllValues().get(2).getHost(), is(equalTo(Defaults.HOST_REST)));
        assertThat("Unexpected response", responseActual2, is(equalTo(responseExpected)));
    }

    /**
     * <strong>This method mocks the API behavior</strong>
     * <p>
     * Validates {@link HttpCore} is using overriden host for every new HTTP request,
     * even if a previous request to that endpoint has failed.
     * </p>
     * <p>
     * Spec: RSC15e
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void http_ably_execute_overriden_host() throws AblyException {
        final String fakeHost = "fake.ably.io";
        ClientOptions options = new ClientOptions("not:a.key");
        options.restHost = fakeHost;
        PubSubHttpClient ably = new PubSubHttpClient(options);

        HttpCore httpCore = Mockito.spy(new HttpCore(ably.options, ably.auth, platformAgentProvider));

        String responseExpected = "Lorem Ipsum";
        ArgumentCaptor<URL> url = ArgumentCaptor.forClass(URL.class);

        /* Partially mock httpCore */
        Answer answer = new GrumpyAnswer(
                1, /* Throw exception */
                AblyException.fromErrorInfo(ErrorInfo.fromResponseStatus("Internal Server Error", 500)), /* That is HostFailedException */
                responseExpected /* Then return a valid response with second call */
        );

        doAnswer(answer) /* Behave as defined above */
                .when(httpCore) /* when following method is executed on {@code HttpCore} instance */
                .httpExecute(
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid fallback url */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );

        Http http = new Http(new AsyncHttpScheduler(httpCore, options), new SyncHttpScheduler(httpCore));
        try {
            HttpHelpers.ablyHttpExecute(
                    http, "", /* Ignore */
                    "", /* Ignore */
                    new Param[0], /* Ignore */
                    new Param[0], /* Ignore */
                    mock(HttpCore.RequestBody.class), /* Ignore */
                    mock(HttpCore.ResponseHandler.class), /* Ignore */
                    false /* Ignore */
            );
        } catch (AblyException e) {
            /* Verify that,
             *      - an {@code AblyException} with {@code ErrorInfo} having the 500 error from above
             */
            ErrorInfo expectedErrorInfo = new ErrorInfo("Internal Server Error", 500, 50000);
            assertThat(e, new ErrorInfoMatcher(expectedErrorInfo));
        }

        assertThat("Unexpected host", url.getAllValues().get(0).getHost(), is(equalTo(fakeHost)));

        try {
            HttpHelpers.ablyHttpExecute(
                    http, "", /* Ignore */
                    "", /* Ignore */
                    new Param[0], /* Ignore */
                    new Param[0], /* Ignore */
                    mock(HttpCore.RequestBody.class), /* Ignore */
                    mock(HttpCore.ResponseHandler.class), /* Ignore */
                    false /* Ignore */
            );
        } catch (AblyException e) {
            /* Verify that,
             *      - an {@code AblyException} with {@code ErrorInfo} having the 500 error from above
             */
            ErrorInfo expectedErrorInfo = new ErrorInfo("Internal Server Error", 500, 50000);
            assertThat(e, new ErrorInfoMatcher(expectedErrorInfo));
        }

        /* Verify call causes captor to capture same arguments twice.
         * Do the validation, after we completed the {@code ArgumentCaptor} related assertions */
        verify(httpCore, times(2))
                .httpExecute( /* Just validating call counter. Ignore following parameters */
                        any(URL.class), /* Ignore */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );
        assertThat("Unexpected host", url.getAllValues().get(1).getHost(), is(equalTo(fakeHost)));
    }

    /**
     * <strong>This method mocks the API behavior</strong>
     * <p>
     * Validates {@link HttpCore} isn't using any fallback hosts if {@link ClientOptions#fallbackHosts}
     * array is empty.
     * </p>
     * <p>
     * Spec: RSC15a
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void http_ably_execute_empty_fallback_array() throws AblyException {
        ClientOptions options = new ClientOptions("not:a.key");
        options.fallbackHosts = new String[0];
        PubSubHttpClient ably = new PubSubHttpClient(options);

        HttpCore httpCore = Mockito.spy(new HttpCore(ably.options, ably.auth, platformAgentProvider));

        String responseExpected = "Lorem Ipsum";
        ArgumentCaptor<URL> url = ArgumentCaptor.forClass(URL.class);

        /* Partially mock httpCore */
        Answer answer = new GrumpyAnswer(
                2, /* Throw exception twice (2) */
                AblyException.fromErrorInfo(ErrorInfo.fromResponseStatus("Internal Server Error", 500)), /* That is HostFailedException */
                responseExpected /* Then return a valid response with third call */
        );

        doAnswer(answer) /* Behave as defined above */
                .when(httpCore) /* when following method is executed on {@code HttpCore} instance */
                .httpExecute(
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid fallback url */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );

        Http http = new Http(new AsyncHttpScheduler(httpCore, options), new SyncHttpScheduler(httpCore));
        try {
            HttpHelpers.ablyHttpExecute(
                    http, "", /* Ignore */
                    "", /* Ignore */
                    new Param[0], /* Ignore */
                    new Param[0], /* Ignore */
                    mock(HttpCore.RequestBody.class), /* Ignore */
                    mock(HttpCore.ResponseHandler.class), /* Ignore */
                    false /* Ignore */
            );
        } catch (AblyException e) {
            /* Verify that, an {@code AblyException} with {@code ErrorInfo} with the 500 error from above. */
            ErrorInfo expectedErrorInfo = new ErrorInfo("Internal Server Error", 500, 50000);
            assertThat(e, new ErrorInfoMatcher(expectedErrorInfo));
        }

        /* Verify call causes captor to capture same arguments once.
         * Do the validation, after we completed the {@code ArgumentCaptor} related assertions */
        verify(httpCore, times(1))
                .httpExecute( /* Just validating call counter. Ignore following parameters */
                        any(URL.class), /* Ignore */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );
        assertThat("Unexpected host", url.getAllValues().get(0).getHost(), is(equalTo(Defaults.HOST_REST)));
    }

    /**
     * <p>
     * Validates {@code HttpCore} performs fallback behavior with custom {@link ClientOptions#fallbackHosts}
     * array httpMaxRetryCount number of times at most,
     * when host & fallback hosts are unreachable. Then, finally throws an error.
     * </p>
     * <p>
     * Spec: RSC15a
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void http_ably_execute_custom_fallback_array() throws AblyException {
        final String[] expectedFallbackHosts = new String[]{"f.ably-realtime.com", "g.ably-realtime.com", "h.ably-realtime.com", "i.ably-realtime.com", "j.ably-realtime.com"};
        final List<String> fallbackHostsList = Arrays.asList(expectedFallbackHosts);

        ClientOptions options = new ClientOptions("not.a:key");
        options.fallbackHosts = expectedFallbackHosts;
        int expectedCallCount = options.httpMaxRetryCount + 1;
        PubSubHttpClient ably = new PubSubHttpClient(options);

        HttpCore httpCore = Mockito.spy(new HttpCore(ably.options, ably.auth, platformAgentProvider));

        String responseExpected = "Lorem Ipsum";
        ArgumentCaptor<URL> url = ArgumentCaptor.forClass(URL.class);

        /* Partially mock httpCore */
        Answer answer = new GrumpyAnswer(
                options.httpMaxRetryCount, /* Throw exception options.httpMaxRetryCount */
                AblyException.fromErrorInfo(ErrorInfo.fromResponseStatus("Internal Server Error", 500)), /* That is HostFailedException */
                responseExpected /* Then return a valid response with third call */
        );

        doAnswer(answer) /* Behave as defined above */
                .when(httpCore) /* when following method is executed on {@code HttpCore} instance */
                .httpExecute(
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid fallback url */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );

        Http http = new Http(new AsyncHttpScheduler(httpCore, options), new SyncHttpScheduler(httpCore));
        String responseActual = (String) HttpHelpers.ablyHttpExecute(
                http, "", /* Ignore */
                "", /* Ignore */
                new Param[0], /* Ignore */
                new Param[0], /* Ignore */
                mock(HttpCore.RequestBody.class), /* Ignore */
                mock(HttpCore.ResponseHandler.class), /* Ignore */
                false /* Ignore */
        );

        /* Verify {code HttpCore#httpExecute} have been called (httpMaxRetryCount + 1) times */
        verify(httpCore, times(expectedCallCount))
                .httpExecute( /* Just validating call counter. Ignore following parameters */
                        any(URL.class), /* Ignore */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );

        /* Verify that,
         * - delivered expected response
         * - first call executed against production rest host
         * - other calls executed against a random custom fallback host */
        List<URL> allValues = url.getAllValues();
        assertThat("Unexpected response", responseActual, is(equalTo(responseExpected)));
        assertThat("Unexpected default primary host", allValues.get(0).getHost(), is(equalTo(Defaults.HOST_REST)));
        for (int i = 1; i < allValues.size(); i++) {
            assertThat("Unexpected host fallback", fallbackHostsList.contains(allValues.get(i).getHost()), is(true));
        }
    }

    /**
     * Validates that HttpCore uses ClientOptions#fallbackHosts when there is AblyException.HostFailedException thrown
     * @throws AblyException
     */

    @Test
    public void http_ably_execute_custom_fallback() throws AblyException {
        ClientOptions options = new ClientOptions();
        options.tls = false;
        options.fallbackHosts = CUSTOM_HOSTS;
        options.port = 7777;

        ArrayList<String> urlHostArgumentStack = new ArrayList<>();

        HttpCore httpCore = new HttpCore(options, null, platformAgentProvider) {
            /* Store only string representations to avoid try/catch blocks */
            List<String> urlArgumentStack;

            @Override
            public <T> T httpExecuteWithRetry(URL url, String method, Param[] headers, RequestBody requestBody, ResponseHandler<T> responseHandler, boolean allowAblyAuth) throws AblyException {
                urlArgumentStack.add(url.getHost());
                /* verify if fallback hosts are from specified list */
                if(!url.getHost().equals(Defaults.HOST_REST))
                    assertTrue(Arrays.asList(CUSTOM_HOSTS).contains(url.getHost()));

                return super.httpExecuteWithRetry(url, method, headers, requestBody, responseHandler, allowAblyAuth);
            }

            public HttpCore setUrlArgumentStack(List<String> urlArgumentStack) {
                this.urlArgumentStack = urlArgumentStack;
                return this;
            }
        }.setUrlArgumentStack(urlHostArgumentStack);

        Http http = new Http(new AsyncHttpScheduler(httpCore, options), new SyncHttpScheduler(httpCore));
        try {
            HttpHelpers.ablyHttpExecute(
                    http, "/path/to/fallback", /* Ignore path */
                    HttpConstants.Methods.GET, /* Ignore method */
                    new Param[0], /* Ignore headers */
                    new Param[0], /* Ignore params */
                    null, /* Ignore requestBody */
                    null, /* Ignore requestHandler */
                    false /* Ignore requireAblyAuth */
            );
        } catch (AblyException.HostFailedException e) {
            /* Verify that, a {@code AblyException.HostFailedException} is thrown. */
            assertTrue(true);
        } catch (AblyException e) {
            assertTrue(false);
        }

        int expectedCallCount = options.httpMaxRetryCount + 1;
        Assert.assertTrue(urlHostArgumentStack.size() == expectedCallCount);
        assertThat(urlHostArgumentStack.get(0), is(equalTo(Defaults.HOST_REST)));

        for (int i = 1; i < expectedCallCount; i++) {
            Assert.assertTrue(urlHostArgumentStack.get(i).matches(CUSTOM_PATTERN_HOST_FALLBACK));
        }
    }

    /**
     * <strong>This method mocks the API behavior</strong>
     * <p>
     * Validates httpCore is not using any fallback host when we receive valid response from httpCore's host
     * </p>
     * <p>
     * Spec: RSC15a
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void http_execute_nofallback() throws Exception {
        HttpCore httpCore = Mockito.spy(new HttpCore(new ClientOptions(), null, platformAgentProvider));

        String responseExpected = "Lorem Ipsum";
        String hostExpected = Defaults.HOST_REST;
        ArgumentCaptor<URL> url = ArgumentCaptor.forClass(URL.class);

        /* Partially mock httpCore */
        doReturn(responseExpected) /* Provide response */
                .when(httpCore) /* when following method is executed on {@code HttpCore} instance */
                .httpExecute(
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid rest host */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );

        Http http = new Http(new AsyncHttpScheduler(httpCore, new ClientOptions()), new SyncHttpScheduler(httpCore));
        String responseActual = (String) HttpHelpers.ablyHttpExecute(
                http, "", /* Ignore */
                "", /* Ignore */
                new Param[0], /* Ignore */
                new Param[0], /* Ignore */
                mock(HttpCore.RequestBody.class), /* Ignore */
                mock(HttpCore.ResponseHandler.class), /* Ignore */
                false /* Ignore */
        );


        /* Verify
         *   - httpCore call executed once,
         *   - with given host,
         *   - and delivered expected response */
        verify(httpCore, times(1))
                .httpExecute( /* Just validating call counter. Ignore following parameters */
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid rest host */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );

        assertThat(url.getValue().getHost(), is(equalTo(hostExpected)));
        assertThat(responseActual, is(equalTo(responseExpected)));
    }

    /**
     * <strong>This method mocks the API behavior</strong>
     * <p>
     * Validates httpCore is using a fallback host when HostFailedException thrown
     * </p>
     * <p>
     * Spec: RSC15a
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void http_execute_singlefallback() throws Exception {
        HttpCore httpCore = Mockito.spy(new HttpCore(new ClientOptions(), null, platformAgentProvider));

        String hostExpectedPattern = PATTERN_HOST_FALLBACK;
        String responseExpected = "Lorem Ipsum";
        ArgumentCaptor<URL> url = ArgumentCaptor.forClass(URL.class);

        /* Partially mock httpCore */
        Answer answer = new GrumpyAnswer(
                1, /* Throw exception once (1) */
                AblyException.fromErrorInfo(ErrorInfo.fromResponseStatus("Internal Server Error", 500)), /* That is HostFailedException */
                responseExpected /* Then return a valid response with the second call */
        );

        doAnswer(answer) /* Behave as defined above */
                .when(httpCore) /* when following method is executed on {@code HttpCore} instance */
                .httpExecute(
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid rest host */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );

        /* Call method with real parameters */
        Http http = new Http(new AsyncHttpScheduler(httpCore, new ClientOptions()), new SyncHttpScheduler(httpCore));
        String responseActual = (String) HttpHelpers.ablyHttpExecute(
                http, "", /* Ignore */
                "", /* Ignore */
                new Param[0], /* Ignore */
                new Param[0], /* Ignore */
                mock(HttpCore.RequestBody.class), /* Ignore */
                mock(HttpCore.ResponseHandler.class), /* Ignore */
                false /* Ignore */
        );


        /* Verify
         *   - httpCore call executed twice (one for prod host and 1 for fallback),
         *   - last call performed against a fallback host,
         *   - and fallback host delivered expected response */

        verify(httpCore, times(2))
                .httpExecute( /* Just validating call counter. Ignore following parameters */
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid rest host */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );

        assertThat(url.getValue().getHost().matches(hostExpectedPattern), is(true));
        assertThat(responseActual, is(equalTo(responseExpected)));
    }

    /**
     * <strong>This method mocks the API behavior</strong>
     * <p>
     * Validates httpCore is using different hosts when HostFailedException happened multiple times.
     * </p>
     * <p>
     * Spec: RSC15a
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void http_execute_multiplefallback() throws Exception {
        HttpCore httpCore = Mockito.spy(new HttpCore(new ClientOptions(), null, platformAgentProvider));

        String hostExpectedPattern = PATTERN_HOST_FALLBACK;
        String responseExpected = "Lorem Ipsum";
        ArgumentCaptor<URL> url = ArgumentCaptor.forClass(URL.class);

        /* Partially mock httpCore */
        Answer answer = new GrumpyAnswer(
                2, /* Throw exception twice (2) */
                AblyException.fromErrorInfo(ErrorInfo.fromResponseStatus("Internal Server Error", 500)), /* That is HostFailedException */
                responseExpected /* Then return a valid response with third call */
        );

        doAnswer(answer) /* Behave as defined above */
                .when(httpCore) /* when following method is executed on {@code HttpCore} instance */
                .httpExecute(
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid rest host */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );

        Http http = new Http(new AsyncHttpScheduler(httpCore, new ClientOptions()), new SyncHttpScheduler(httpCore));
        String responseActual = (String) HttpHelpers.ablyHttpExecute(
                http, "", /* Ignore */
                "", /* Ignore */
                new Param[0], /* Ignore */
                new Param[0], /* Ignore */
                mock(HttpCore.RequestBody.class), /* Ignore */
                mock(HttpCore.ResponseHandler.class), /* Ignore */
                false /* Ignore */
        );


        /* Verify
         *   - httpCore call executed thrice,
         *   - with 2 fallback hosts,
         *   - each host having a unique value,
         *   - and delivered expected response */

        assertThat(url.getAllValues().get(1).getHost().matches(hostExpectedPattern), is(true));
        assertThat(url.getAllValues().get(2).getHost().matches(hostExpectedPattern), is(true));
        assertThat(url.getAllValues().get(1).toString(), is(not(equalTo(url.getAllValues().get(2).toString()))));

        assertThat(responseActual, is(equalTo(responseExpected)));

        /* Verify call causes captor to capture same arguments twice.
         * Do the validation, after we completed the {@code ArgumentCaptor} related assertions */
        verify(httpCore, times(3))
                .httpExecute( /* Just validating call counter. Ignore following parameters */
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid rest host */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );
    }

    /**
     * <strong>This method mocks the API behavior</strong>
     * <p>
     * Validates {@code HttpCore} is using its a successful fallback host first
     * when a consecutive call happens within the fallbackRetryTimeout, so long
     * as calls to that fallback continue to succeed
     * </p>
     * <p>
     * Spec: RSC15f
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void http_execute_fallback_success_timeout_unexpired() throws Exception {
        ClientOptions opts = new ClientOptions();
        opts.fallbackRetryTimeout = 2000L;
        HttpCore httpCore = Mockito.spy(new HttpCore(opts, null, platformAgentProvider));

        String hostExpected = Defaults.HOST_REST;
        ArgumentCaptor<URL> url = ArgumentCaptor.forClass(URL.class);

        /* Partially mock httpCore */
        Answer answer = new GrumpyAnswer(
                1, /* Throw exception once (1) */
                AblyException.fromErrorInfo(ErrorInfo.fromResponseStatus("Internal Server Error", 500)), /* That is HostFailedException */
                "Lorem Ipsum" /* Ignore */
        );

        doAnswer(answer) /* Behave as defined above */
                .when(httpCore) /* when following method is executed on {@code HttpCore} instance */
                .httpExecute(
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid rest host */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );

        Http http = new Http(new AsyncHttpScheduler(httpCore, new ClientOptions()), new SyncHttpScheduler(httpCore));
        HttpHelpers.ablyHttpExecute(
                http, "", /* Ignore */
                "", /* Ignore */
                new Param[0], /* Ignore */
                new Param[0], /* Ignore */
                mock(HttpCore.RequestBody.class), /* Ignore */
                mock(HttpCore.ResponseHandler.class), /* Ignore */
                false /* Ignore */
        );

        /* Verify there was a fallback with first call */
        String successFallback = url.getValue().getHost();
        assertThat(successFallback.matches(PATTERN_HOST_FALLBACK), is(true));

        /* wait for a short time, but not enough for the fallbackRetryTimeout to expire */
        try { Thread.sleep(200L); } catch(InterruptedException ie) {}

        /* Update behavior to succeed on next attempt */
        url = ArgumentCaptor.forClass(URL.class);
        doReturn("Lorem Ipsum") /* Return some response string that we will ignore */
                .when(httpCore) /* when following method is executed on {@code HttpCore} instance */
                .httpExecute(
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid rest host */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );

        HttpHelpers.ablyHttpExecute(
                http, "", /* Ignore */
                "", /* Ignore */
                new Param[0], /* Ignore */
                new Param[0], /* Ignore */
                mock(HttpCore.RequestBody.class), /* Ignore */
                mock(HttpCore.ResponseHandler.class), /* Ignore */
                false /* Ignore */
        );

        /* Verify second call was called with the cached fallback host */
        assertThat(url.getValue().getHost().equals(successFallback), is(true));
    }

    /**
     * <strong>This method mocks the API behavior</strong>
     * <p>
     * Validates {@code HttpCore} reverts to using the primary endpoint
     * if a request to a preferred fallback fails (after initially succeeding
     * and being cached)
     * </p>
     * <p>
     * Spec: RSC15f
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void http_execute_fallback_failure_timeout_unexpired() throws Exception {
        ClientOptions opts = new ClientOptions();
        opts.fallbackRetryTimeout = 2000L;
        HttpCore httpCore = Mockito.spy(new HttpCore(opts, null, platformAgentProvider));

        String primaryHost = Defaults.HOST_REST;
        ArgumentCaptor<URL> url = ArgumentCaptor.forClass(URL.class);

        /* Partially mock httpCore */
        Answer answer = new GrumpyAnswer(
                1, /* Throw exception once */
                AblyException.fromErrorInfo(ErrorInfo.fromResponseStatus("Internal Server Error", 500)), /* That is HostFailedException */
                "Lorem Ipsum" /* Ignore */
        );

        doAnswer(answer) /* Behave as defined above */
                .when(httpCore) /* when following method is executed on {@code HttpCore} instance */
                .httpExecute(
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid rest host */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );

        Http http = new Http(new AsyncHttpScheduler(httpCore, new ClientOptions()), new SyncHttpScheduler(httpCore));
        HttpHelpers.ablyHttpExecute(
                http, "", /* Ignore */
                "", /* Ignore */
                new Param[0], /* Ignore */
                new Param[0], /* Ignore */
                mock(HttpCore.RequestBody.class), /* Ignore */
                mock(HttpCore.ResponseHandler.class), /* Ignore */
                false /* Ignore */
        );

        /* Verify there was a fallback with first call */
        String failFallback = url.getValue().getHost();
        assertThat(failFallback.matches(PATTERN_HOST_FALLBACK), is(true));

        /* wait for a short time, but not enough for the fallbackRetryTimeout to expire */
        try { Thread.sleep(200L); } catch(InterruptedException ie) {}

        /* reset the mocked response so that the next request fails */
        answer = new GrumpyAnswer(
                1, /* Throw exception once */
                AblyException.fromErrorInfo(ErrorInfo.fromResponseStatus("Internal Server Error", 500)), /* That is HostFailedException */
                "Lorem Ipsum" /* Ignore */
        );

        doAnswer(answer) /* Behave as defined above */
                .when(httpCore) /* when following method is executed on {@code HttpCore} instance */
                .httpExecute(
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid rest host */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );

        HttpHelpers.ablyHttpExecute(
                http, "", /* Ignore */
                "", /* Ignore */
                new Param[0], /* Ignore */
                new Param[0], /* Ignore */
                mock(HttpCore.RequestBody.class), /* Ignore */
                mock(HttpCore.ResponseHandler.class), /* Ignore */
                false /* Ignore */
        );

        /* Verify second call was called with httpCore host */
        assertThat(url.getValue().getHost().equals(primaryHost), is(true));
    }

    /**
     * <strong>This method mocks the API behavior</strong>
     * <p>
     * Validates {@code HttpCore} is using the primary host first
     * when a consecutive call happens after the fallbackRetryTimeout
     * </p>
     * <p>
     * Spec: RSC15f
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void http_execute_fallback_timeout_expired() throws Exception {
        ClientOptions opts = new ClientOptions();
        opts.fallbackRetryTimeout = 2000L;
        HttpCore httpCore = Mockito.spy(new HttpCore(opts, null, platformAgentProvider));

        String hostExpected = Defaults.HOST_REST;
        ArgumentCaptor<URL> url = ArgumentCaptor.forClass(URL.class);

        /* Partially mock httpCore */
        Answer answer = new GrumpyAnswer(
                1, /* Throw exception once (1) */
                AblyException.fromErrorInfo(ErrorInfo.fromResponseStatus("Internal Server Error", 500)), /* That is HostFailedException */
                "Lorem Ipsum" /* Ignore */
        );

        doAnswer(answer) /* Behave as defined above */
                .when(httpCore) /* when following method is executed on {@code HttpCore} instance */
                .httpExecute(
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid rest host */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );

        Http http = new Http(new AsyncHttpScheduler(httpCore, new ClientOptions()), new SyncHttpScheduler(httpCore));
        HttpHelpers.ablyHttpExecute(
                http, "", /* Ignore */
                "", /* Ignore */
                new Param[0], /* Ignore */
                new Param[0], /* Ignore */
                mock(HttpCore.RequestBody.class), /* Ignore */
                mock(HttpCore.ResponseHandler.class), /* Ignore */
                false /* Ignore */
        );

        /* Verify there was a fallback with first call */
        assertThat(url.getValue().getHost().matches(PATTERN_HOST_FALLBACK), is(true));

        /* wait for the fallbackRetryTimeout to expire */
        try { Thread.sleep(2000L); } catch(InterruptedException ie) {}

        /* Update behavior to perform a call without a fallback */
        url = ArgumentCaptor.forClass(URL.class);
        doReturn("Lorem Ipsum") /* Return some response string that we will ignore */
                .when(httpCore) /* when following method is executed on {@code HttpCore} instance */
                .httpExecute(
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid rest host */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );

        HttpHelpers.ablyHttpExecute(
                http, "", /* Ignore */
                "", /* Ignore */
                new Param[0], /* Ignore */
                new Param[0], /* Ignore */
                mock(HttpCore.RequestBody.class), /* Ignore */
                mock(HttpCore.ResponseHandler.class), /* Ignore */
                false /* Ignore */
        );

        /* Verify second call was called with httpCore host */
        assertThat(url.getValue().getHost().equals(hostExpected), is(true));
    }

    /**
     * <strong>This method mocks the API behavior</strong>
     * <p>
     * Validates {@code HttpCore} is throwing an exception,
     * when connection to host failed more than allowed count ({@code Defaults.HTTP_MAX_RETRY_COUNT})
     * </p>
     * <p>
     * Spec: -
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void http_execute_excessivefallback() throws AblyException {
        ClientOptions options = new ClientOptions();
        HttpCore httpCore = Mockito.spy(new HttpCore(options, null, platformAgentProvider));

        ArgumentCaptor<URL> url = ArgumentCaptor.forClass(URL.class);
        int excessiveFallbackCount = options.httpMaxRetryCount + 1;

        /* Partially mock httpCore */
        Answer answer = new GrumpyAnswer(
                excessiveFallbackCount, /* Throw exception more than httpMaxRetryCount number of times */
                AblyException.fromErrorInfo(ErrorInfo.fromResponseStatus("Internal Server Error", 500)), /* That is HostFailedException */
                "Lorem Ipsum" /* Ignore */
        );

        doAnswer(answer) /* Behave as defined above */
                .when(httpCore) /* when following method is executed on {@code HttpCore} instance */
                .httpExecute(
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid rest host */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(HttpCore.RequestBody.class), /* Ignore */
                        anyBoolean(), /* Ignore */
                        any(HttpCore.ResponseHandler.class) /* Ignore */
                );


        /* Verify
         *   - ably exception with 50x status code is thrown
         */
        thrown.expect(AblyException.HostFailedException.class);

        Http http = new Http(new AsyncHttpScheduler(httpCore, new ClientOptions()), new SyncHttpScheduler(httpCore));
        HttpHelpers.ablyHttpExecute(
                http, "", /* Ignore */
                "", /* Ignore */
                new Param[0], /* Ignore */
                new Param[0], /* Ignore */
                mock(HttpCore.RequestBody.class), /* Ignore */
                mock(HttpCore.ResponseHandler.class), /* Ignore */
                false /* Ignore */
        );
    }

    /**
     * <p>
     * Validates {@code HttpCore#httpExecute} is throwing an {@code HostFailedException},
     * when api returns a response code between 500 and 504
     * </p>
     * <p>
     * Spec: RSC15d
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void http_execute_response_50x() throws AblyException, MalformedURLException {
        URL url;
        HttpCore httpCore = new HttpCore(new ClientOptions(), null, platformAgentProvider);

        AblyException.HostFailedException hfe;

        for (int statusCode = 500; statusCode <= 504; statusCode++) {
            url = new URL("http://localhost:" + server.getListeningPort() + "/status/" + statusCode);
            hfe = null;

            try {
                String result = HttpHelpers.httpExecute(httpCore, url, HttpConstants.Methods.GET, new Param[0], null, null);
            } catch (AblyException.HostFailedException e) {
                hfe = e;
            } catch (AblyException e) {
                e.printStackTrace();
            }

            Assert.assertNotNull("Status code " + statusCode + " should throw an exception", hfe);
        }
    }

    /**
     * <p>
     * Validates {@code HttpCore#httpExecute} isn't throwing an {@code HostFailedException},
     * when api returns a non-server-error response code (Informational 1xx,
     * Multiple Choices 3xx, Client Error 4xx)
     * </p>
     * <p>
     * Spec: RSC15d
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void http_execute_response_non5xx() throws AblyException, MalformedURLException {
        URL url;
        HttpCore httpCore = new HttpCore(new ClientOptions(), null, platformAgentProvider);

        /* Informational 1xx */

        for (int statusCode = 100; statusCode <= 101; statusCode++) {
            url = new URL("http://localhost:" + server.getListeningPort() + "/status/" + statusCode);

            try {
                HttpHelpers.httpExecute(httpCore, url, HttpConstants.Methods.GET, new Param[0], null, null);
            } catch (AblyException.HostFailedException e) {
                fail("Informal status code " + statusCode + " shouldn't throw an exception");
            } catch (Exception e) {
                /* non HostFailedExceptions are ignored */
            }
        }


        /* Informational 3xx */

        for (int statusCode = 300; statusCode <= 307; statusCode++) {
            url = new URL("http://localhost:" + server.getListeningPort() + "/status/" + statusCode);

            try {
                HttpHelpers.httpExecute(httpCore, url, HttpConstants.Methods.GET, new Param[0], null, null);
            } catch (AblyException.HostFailedException e) {
                fail("Multiple choices status code " + statusCode + " shouldn't throw an exception");
            } catch (Exception e) {
                /* non HostFailedExceptions are ignored */
            }
        }


        /* Informational 4xx */

        for (int statusCode = 400; statusCode <= 417; statusCode++) {
            url = new URL("http://localhost:" + server.getListeningPort() + "/status/" + statusCode);

            try {
                HttpHelpers.httpExecute(httpCore, url, HttpConstants.Methods.GET, new Param[0], null, null);
            } catch (AblyException.HostFailedException e) {
                fail("Client error status code " + statusCode + " shouldn't throw an exception");
            } catch (Exception e) {
                /* non HostFailedExceptions are ignored */
            }
        }
    }

    @Test
    public void test_asynchttp_concurrent_default_notqueued() {
        test_asynchttp_concurrent(0, 64, 5000, 20000);
    }

    @Test
    public void test_asynchttp_concurrent_default_queued() {
        test_asynchttp_concurrent(0, 128, 10000, 25000);
    }

    @Test
    public void test_asynchttp_concurrent_10_notqueued() {
        test_asynchttp_concurrent(10, 10, 5000, 20000);
    }

    @Test
    public void test_asynchttp_concurrent_11_queued() {
        test_asynchttp_concurrent(10, 11, 10000, 25000);
    }

    private void test_asynchttp_concurrent(int poolSize, int requestCount, int expectedMinDuration, int expectedMaxDuration) {
        try {
            ClientOptions options = new ClientOptions("not.a:key");
            options.tls = false;
            options.restHost = TEST_SERVER_HOST;
            options.port = TEST_SERVER_PORT;
            if(poolSize > 0) {
                options.asyncHttpThreadpoolSize = poolSize;
            }
            final PubSubHttpClient pubSubHttpClient = new PubSubHttpClient(options);

            final Object waiter = new Object();
            final long startTime = System.currentTimeMillis();
            final int[] counter = new int[1];
            final boolean[] error = new boolean[1];

            /* start parallel requests */
            for(int i = 0; i < requestCount; i++) {
                pubSubHttpClient.timeAsync(new Callback<Long>() {
                    @Override
                    public void onSuccess(Long result) {
                        synchronized(waiter) {
                            counter[0]++;
                            waiter.notify();
                        }
                    }

                    @Override
                    public void onError(ErrorInfo reason) {
                        synchronized(waiter) {
                            counter[0]++;
                            error[0] = true;
                            waiter.notify();
                        }
                        fail("Unexpected error: " + reason.message);
                    }
                });
            }

            /* wait for all requests to complete */
            while(counter[0] < requestCount) {
                synchronized(waiter) {
                    try {
                        waiter.wait();
                    } catch(InterruptedException ie) {}
                }
            }

            /* assert */
            assertEquals("Verify all requests completed", counter[0], requestCount);
            assertFalse("Verify there were no errors", error[0]);

            long endTime = System.currentTimeMillis(), duration = endTime - startTime;
            assertTrue("Verify duration at least minimum", duration >= expectedMinDuration);
            assertTrue("Verify duration at most maximum", duration <= expectedMaxDuration);

        } catch(AblyException ae) {
            ae.printStackTrace();
        }
    }

    /*********************************************
     * Minions
     *********************************************/

    static class GrumpyAnswer implements Answer<String> {
        private int grumpinessLevel;
        private Throwable nope;
        private String value;

        /**
         * Throws grumpinessLevel number of nope to you and then gives its response properly.
         *
         * @param grumpinessLevel Quantity of nope that will be thrown into your face, each time.
         * @param nope            Expected nope
         * @param value           Expected value that will be returned after grumpiness level goes below or equal to 0.
         */
        GrumpyAnswer(int grumpinessLevel, Throwable nope, String value) {
            this.grumpinessLevel = grumpinessLevel;
            this.nope = nope;
            this.value = value;
        }

        @Override
        public String answer(InvocationOnMock invocation) throws Throwable {
            if (grumpinessLevel-- > 0) {
                throw nope;
            }

            return value;
        }
    }

    static class ErrorInfoMatcher extends TypeSafeMatcher<AblyException> {
        ErrorInfo errorInfo;

        ErrorInfoMatcher(ErrorInfo errorInfo) {
            super();
            this.errorInfo = errorInfo;
        }

        @Override
        protected boolean matchesSafely(AblyException item) {
            return errorInfo.code == item.errorInfo.code &&
                    errorInfo.statusCode == item.errorInfo.statusCode;
        }

        @Override
        protected void describeMismatchSafely(AblyException item, Description mismatchDescription) {
            mismatchDescription.appendText(item.errorInfo.toString());
        }

        @Override
        public void describeTo(Description description) {
            description.appendText(errorInfo.toString());
        }
    }

    /**
     * Verifies RSC15f: a late-arriving success from a fallback host must not
     * re-pin the fallback when the fallbackRetryTimeout has already expired.
     *
     * Sequence:
     *   req1  primary  → 500 (triggers fallback)
     *   req2  fallback → 200 immediate (pins fallback, timeout = 100 ms)
     *   req3  fallback → 200 delayed 200 ms (in-flight when timeout expires)
     *   req4  primary  → 200 immediate (timeout expired → primary tried again)
     *   req5  primary  → 200 immediate (late req3 success must NOT have re-pinned)
     */
    @Test
    public void http_fallback_late_success_does_not_repin_expired_timeout() throws Exception {
        final String primaryHost = "main.realtime.ably.net";
        final String fallbackHost = "main.a.fallback.ably-realtime.com";

        final List<String> capturedHosts = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch delayedRequestStarted = new CountDownLatch(1);
        final AtomicInteger requestCount = new AtomicInteger(0);

        DebugOptions opts = new DebugOptions("appId.keyId:keySecret");
        opts.restHost = primaryHost;
        opts.fallbackHosts = new String[]{fallbackHost};
        opts.fallbackRetryTimeout = 100L;
        opts.httpMaxRetryCount = 1;
        opts.httpListener = new DebugOptions.RawHttpListener() {
            @Override
            public HttpCore.Response onRawHttpRequest(String id, HttpRequest request, String authHeader,
                    Map<String, List<String>> requestHeaders, HttpCore.RequestBody requestBody) {
                int n = requestCount.incrementAndGet();
                capturedHosts.add(request.getUrl().getHost());

                HttpCore.Response r = new HttpCore.Response();
                switch (n) {
                    case 1:
                        r.statusCode = 500;
                        r.statusLine = "Internal Server Error";
                        break;
                    case 2:
                        r.statusCode = 200;
                        r.contentType = "application/json";
                        r.body = "[1000]".getBytes();
                        r.contentLength = r.body.length;
                        break;
                    case 3:
                        delayedRequestStarted.countDown();
                        try { Thread.sleep(200L); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        r.statusCode = 200;
                        r.contentType = "application/json";
                        r.body = "[2000]".getBytes();
                        r.contentLength = r.body.length;
                        break;
                    case 4:
                        r.statusCode = 200;
                        r.contentType = "application/json";
                        r.body = "[3000]".getBytes();
                        r.contentLength = r.body.length;
                        break;
                    case 5:
                        r.statusCode = 200;
                        r.contentType = "application/json";
                        r.body = "[4000]".getBytes();
                        r.contentLength = r.body.length;
                        break;
                    default:
                        r.statusCode = 500;
                        r.statusLine = "Unexpected extra request";
                }
                return r;
            }
            @Override public void onRawHttpResponse(String id, String method, HttpCore.Response r) {}
            @Override public void onRawHttpException(String id, String method, Throwable t) {}
        };

        PubSubHttpClient client = new PubSubHttpClient(opts);

        // req1 (primary→500) + req2 (fallback→200); fallback is now pinned
        client.time();

        // req3: fire in background — hits pinned fallback, sleeps 200 ms inside listener
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Long> requestFuture = executor.submit(() -> {
                try { return client.time(); }
                catch (AblyException e) { throw new RuntimeException(e); }
            });

            // Wait until req3 has actually entered the listener before starting the clock
            assertTrue("Delayed request must start within 5 s", delayedRequestStarted.await(5, TimeUnit.SECONDS));

            // Wait 150 ms so that fallbackRetryTimeout (100 ms) expires
            Thread.sleep(150L);

            // req4: timeout expired → primary tried again
            client.time();

            // Wait for req3's delayed response to arrive (late fallback success)
            requestFuture.get(5, TimeUnit.SECONDS);

            // req5: late success from req3 must NOT have re-pinned the fallback
            client.time();

            assertEquals(5, capturedHosts.size());
            assertEquals(primaryHost,  capturedHosts.get(0)); // req1 – primary fails
            assertEquals(fallbackHost, capturedHosts.get(1)); // req2 – fallback pins
            assertEquals(fallbackHost, capturedHosts.get(2)); // req3 – in-flight fallback
            assertEquals(primaryHost,  capturedHosts.get(3)); // req4 – timeout expired
            assertEquals(primaryHost,  capturedHosts.get(4)); // req5 – late success did not re-pin
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    executor.awaitTermination(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
