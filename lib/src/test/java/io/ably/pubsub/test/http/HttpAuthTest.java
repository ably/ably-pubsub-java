package io.ably.pubsub.test.http;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;
import io.ably.pubsub.debug.DebugOptions;
import io.ably.pubsub.http.HttpConstants;
import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.network.HttpRequest;
import io.ably.pubsub.http.HttpClientFactory;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.http.Auth;
import io.ably.pubsub.http.Auth.AuthMethod;
import io.ably.pubsub.http.Auth.TokenCallback;
import io.ably.pubsub.http.Auth.TokenDetails;
import io.ably.pubsub.http.Auth.TokenParams;
import io.ably.pubsub.http.Auth.TokenRequest;
import io.ably.pubsub.http.Channel;
import io.ably.pubsub.test.common.Helpers;
import io.ably.pubsub.test.common.Helpers.RawHttpTracker;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.test.util.TokenServer;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.Message;
import io.ably.pubsub.types.MessageSerializer;
import io.ably.pubsub.types.PaginatedResult;
import io.ably.pubsub.types.Param;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HttpAuthTest extends ParameterizedTest {

    @Rule
    public Timeout testTimeout = Timeout.seconds(40);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    /**
     * Init token server
     */
    @BeforeClass
    public static void auth_start_tokenserver() {
        try {
            ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            tokenServer = new TokenServer(ably, 8982);
            tokenServer.start();

            nanoHTTPD = new SessionHandlerNanoHTTPD(27335);
            nanoHTTPD.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);

            while (!nanoHTTPD.wasStarted()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            fail("auth_start_tokenserver: Unexpected exception starting server");
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_start_tokenserver: Unexpected exception starting server");
        }
    }

    /**
     * Kill token server
     */
    @AfterClass
    public static void auth_stop_tokenserver() {
        if(tokenServer != null)
            tokenServer.stop();
        if (nanoHTTPD != null)
            nanoHTTPD.stop();
    }

    /**
     * Init library with a key only
     */
    @Test
    public void authinit0() {
        try {
            PubSubHttpClient ably = HttpClientFactory.create(new ClientOptions(testVars.keys[0].keyStr));
            assertEquals("Unexpected Auth method mismatch", ably.auth.getAuthMethod(), AuthMethod.basic);
            assertEquals("Unexpected clientId mismatch", ably.auth.clientId, "*");
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authinit0: Unexpected exception instantiating library");
        }
    }

    /**
     * Init library with a key and tls=false results in an error
     * Spec: RSC18
     */
    @Test
    public void auth_basic_nontls() {
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.tls = false;
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            ably.stats(null);
            fail("Unexpected success calling with Basic auth over httpCore");
        } catch (AblyException e) {
            e.printStackTrace();
            assertEquals("Verify expected error code", e.errorInfo.statusCode, 401);
        }
    }

    /**
     * Init library with useTokenAuth set
     */
    @Test
    public void authinit0_useTokenAuth() {
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.useTokenAuth = true;
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            assertEquals("Unexpected Auth method mismatch", ably.auth.getAuthMethod(), AuthMethod.token);
            /* Spec: RSA12a */
            assertEquals("Unexpected clientId mismatch", ably.auth.clientId, null);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authinit0point5: Unexpected exception instantiating library");
        }
    }

    /**
     * Init library with a token only
     */
    @Test
    public void authinit1() {
        try {
            ClientOptions opts = new ClientOptions();
            opts.token = "this_is_not_really_a_token";
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            assertEquals("Unexpected Auth method mismatch", ably.auth.getAuthMethod(), AuthMethod.token);
            /* Spec: RSA12a */
            assertEquals("Unexpected clientId mismatch", ably.auth.clientId, null);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authinit1: Unexpected exception instantiating library");
        }
    }

    /**
     * Init library with a token callback
     */
    private boolean authinit2_cbCalled;
    @Test
    public void authinit2() {
        try {
            ClientOptions opts = createOptions();
            opts.restHost = testVars.restHost;
            opts.environment = testVars.environment;
            opts.port = testVars.port;
            opts.tlsPort = testVars.tlsPort;
            opts.tls = testVars.tls;
            opts.authCallback = new TokenCallback() {
                @Override
                public String getTokenRequest(TokenParams params) throws AblyException {
                    authinit2_cbCalled = true;
                    return "this_is_not_really_a_token_request";
                }};
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            /* make a call to trigger token request */
            try {
                ably.stats(null);
            } catch(Throwable t) {}
            assertEquals("Unexpected Auth method mismatch", ably.auth.getAuthMethod(), AuthMethod.token);
            /* Spec: RSA12a */
            assertEquals("Unexpected clientId mismatch", ably.auth.clientId, null);
            assertTrue("Token callback not called", authinit2_cbCalled);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authinit2: Unexpected exception instantiating library");
        }
    }

    /**
     * Init library with a key and clientId; expect basic auth to be chosen
     * Spec: RSC17, RSA7b1
     */
    @Test
    public void authinit_clientId_auth_basic() {
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.clientId = "testClientId";
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            assertEquals("Unexpected Auth method mismatch", ably.auth.getAuthMethod(), AuthMethod.basic);
            assertEquals("Unexpected clientId mismatch", ably.auth.clientId, "testClientId");
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authinit_clientId_auth_basic: Unexpected exception instantiating library");
        }
    }

    /**
     * Init library with a key and token; verify Auth.clientId is null before
     * authorization and set following auth
     * Spec: RSA12b, RSA7b2
     */
    @Test
    public void auth_clientid_null_before_auth() {
        try {
            final String defaultClientId = "default clientId";
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.useTokenAuth = true;
            opts.defaultTokenParams = new TokenParams() {{ this.clientId = defaultClientId; }};
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            assertEquals("Unexpected Auth method mismatch", ably.auth.getAuthMethod(), AuthMethod.token);
            assertEquals("Unexpected clientId mismatch", ably.auth.clientId, null);
            ably.auth.authorize(null, null);
            assertEquals("Unexpected clientId mismatch", ably.auth.clientId, defaultClientId);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authinit_token_implies_token: Unexpected exception instantiating library");
        }
    }

    /**
     * Init library with a key and token; expect token auth to be chosen
     * Spec: RSA4
     */
    @Test
    public void authinit_token_implies_token() {
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.token = testVars.keys[0].keyStr;
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            assertEquals("Unexpected Auth method mismatch", ably.auth.getAuthMethod(), AuthMethod.token);
            assertEquals("Unexpected clientId mismatch", ably.auth.clientId, null);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authinit_token_implies_token: Unexpected exception instantiating library");
        }
    }

    /**
     * Init library with a key and tokenDetails; expect token auth to be chosen
     * Spec: RSA4
     */
    @Test
    public void authinit_tokendetails_implies_token() {
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.tokenDetails = new TokenDetails() {{ token = testVars.keys[0].keyStr; }};
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            assertEquals("Unexpected Auth method mismatch", ably.auth.getAuthMethod(), AuthMethod.token);
            assertEquals("Unexpected clientId mismatch", ably.auth.clientId, null);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authinit_token_implies_token: Unexpected exception instantiating library");
        }
    }

    /**
     * Init library with a key and authCallback; expect token auth to be chosen
     * Spec: RSA4
     */
    @Test
    public void authinit_authcallback_implies_token() {
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.authCallback = new TokenCallback() {
                @Override
                public Object getTokenRequest(TokenParams params) throws AblyException {
                    return null;
                }};
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            assertEquals("Unexpected Auth method mismatch", ably.auth.getAuthMethod(), AuthMethod.token);
            assertEquals("Unexpected clientId mismatch", ably.auth.clientId, null);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authinit_token_implies_token: Unexpected exception instantiating library");
        }
    }

    /**
     * Init library with a key and authUrl; expect token auth to be chosen
     * Spec: RSA4
     */
    @Test
    public void authinit_authurl_implies_token() {
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.authUrl = "http://auth.url";
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            assertEquals("Unexpected Auth method mismatch", ably.auth.getAuthMethod(), AuthMethod.token);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authinit_token_implies_token: Unexpected exception instantiating library");
        }
    }

    /**
     * Init library with a token
     */
    @Test
    public void authinit4() {
        try {
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            PubSubHttpClient ablyForToken = HttpClientFactory.create(optsForToken);
            TokenDetails tokenDetails = ablyForToken.auth.requestToken(null, null);
            assertNotNull("Expected token value", tokenDetails.token);
            ClientOptions opts = new ClientOptions();
            opts.token = tokenDetails.token;
            opts.environment = testVars.environment;
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            assertEquals("Unexpected Auth method mismatch", ably.auth.getAuthMethod(), AuthMethod.token);
            assertEquals("Unexpected clientId mismatch", ably.auth.clientId, null);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("authinit3: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify authURL called and handled when returning token request
     * Spec: RSA8c
     */
    @Test
    public void auth_authURL_tokenrequest() {
        try {
            ClientOptions opts = createOptions();
            opts.environment = testVars.environment;
            opts.authUrl = "http://localhost:8982/get-token-request";
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            /* make a call to trigger token request */
            try {
                TokenDetails tokenDetails = ably.auth.requestToken(null, null);
                assertNotNull("Expected token value", tokenDetails.token);
            } catch (AblyException e) {
                e.printStackTrace();
                fail("auth_authURL_tokenrequest: Unexpected exception requesting token");
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_tokenrequest: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify authURL called and handled when returning token request, use POST method
     * Spec: RSA8c1b
     */
    @Test
    public void auth_authURL_tokenrequest_post() {
        try {
            ClientOptions opts = createOptions();
            opts.environment = testVars.environment;
            opts.authUrl = "http://localhost:8982/post-token-request";
            opts.authMethod = HttpConstants.Methods.POST;
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            /* make a call to trigger token request */
            try {
                TokenDetails tokenDetails = ably.auth.requestToken(null, null);
                assertNotNull("Expected token value", tokenDetails.token);
            } catch (AblyException e) {
                e.printStackTrace();
                fail("auth_authURL_tokenrequest: Unexpected exception requesting token");
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_tokenrequest: Unexpected exception instantiating library");
        }
    }


    /**
     * Verify authURL called and handled when returning token
     * Spec: RSA8c
     */
    @Test
    public void auth_authURL_token() {
        try {
            ClientOptions opts = createOptions();
            opts.environment = testVars.environment;
            opts.authUrl = "http://localhost:8982/get-token";
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            /* make a call to trigger token request */
            try {
                TokenDetails tokenDetails = ably.auth.requestToken(null, null);
                assertNotNull("Expected token value", tokenDetails.token);
            } catch (AblyException e) {
                e.printStackTrace();
                fail("auth_authURL_token: Unexpected exception requesting token");
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_token: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify authURL called and handled when returning error
     * Spec: RSA8c
     */
    @Test
    public void auth_authURL_err() {
        try {
            ClientOptions opts = createOptions();
            opts.environment = testVars.environment;
            opts.authUrl = "http://localhost:8982/404";
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            /* make a call to trigger token request */
            try {
                ably.auth.requestToken(null, null);
                fail("auth_authURL_err: Unexpected success requesting token");
            } catch (AblyException e) {
                assertEquals("Expected error code", e.errorInfo.code, 80019);
                assertEquals("Expected forwarded error code", ((AblyException)e.getCause()).errorInfo.statusCode, 404);
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_token: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify authURL called and handled when timing out
     * Spec: RSA8c, RSA4c
     */
    @Test
    public void auth_authURL_timeout() {
        try {
            ClientOptions opts = createOptions();
            opts.environment = testVars.environment;
            opts.authUrl = "http://localhost:8982/wait?delay=6000";
            opts.httpRequestTimeout = 5000;
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            /* make a call to trigger token request */
            try {
                ably.auth.requestToken(null, null);
                fail("auth_authURL_err: Unexpected success requesting token");
            } catch (AblyException e) {
                assertEquals("Expected error code", e.errorInfo.code, 80019);
                assertEquals("Expected forwarded error code", ((AblyException)e.getCause()).errorInfo.statusCode, 500);
                assertTrue("Expected forwarded forwarded exception", (e.getCause().getCause()) instanceof SocketTimeoutException);
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_timeout: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify authURL is passed specified params in a GET
     * Spec: RSA8c1a
     */
    @Test
    public void auth_authURL_authParams_get() {
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            RawHttpTracker httpListener = new RawHttpTracker();
            opts.httpListener = httpListener;

            opts.authUrl = "http://localhost:8982/get-token-request";
            opts.authParams = new Param[]{new Param("test-param", "test-value")};
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* make a call to trigger token request */
            ably.auth.requestToken(null, null);

            /* check request contained expected params */
            assertTrue("Verify expected params passed to authURL", httpListener.getFirstRequest().url.getQuery().contains("test-param=test-value"));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_authParams_get: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify authURL is passed specified params in a POST body
     * Spec: RSA8c1b
     */
    @Test
    public void auth_authURL_authParams_post() {
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            RawHttpTracker httpListener = new RawHttpTracker();
            opts.httpListener = httpListener;

            opts.authUrl = "http://localhost:8982/post-token-request";
            opts.authMethod = HttpConstants.Methods.POST;
            opts.authParams = new Param[]{new Param("test-param", "test-value")};
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* make a call to trigger token request */
            ably.auth.requestToken(null, null);

            /* check request contained expected params */
            byte[] requestBody = httpListener.getFirstRequest().requestBody.getEncoded();
            assertTrue("Verify expected params passed to authURL", (new String(requestBody, "UTF-8")).contains("test-param=test-value"));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_authParams_post: Unexpected exception instantiating library");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            fail("auth_authURL_authParams_post: Unexpected exception decoding request body");
        }
    }

    /**
     * Verify authURL is passed specified params in a GET
     * Spec: RSA8c1c
     */
    @Test
    public void auth_authURL_urlParams_get() {
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            RawHttpTracker httpListener = new RawHttpTracker();
            opts.httpListener = httpListener;

            opts.authUrl = "http://localhost:8982/get-token-request?test-param=test-value";
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* make a call to trigger token request */
            ably.auth.requestToken(null, null);

            /* check request contained expected params */
            assertTrue("Verify expected params passed to authURL", httpListener.getFirstRequest().url.getQuery().contains("test-param=test-value"));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_urlParams_get: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify authURL is passed specified params in a POST
     * Spec: RSA8c1c
     */
    @Test
    public void auth_authURL_urlParams_post() {
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            RawHttpTracker httpListener = new RawHttpTracker();
            opts.httpListener = httpListener;

            opts.authUrl = "http://localhost:8982/post-token-request?test-param=test-value";
            opts.authMethod = HttpConstants.Methods.POST;
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* make a call to trigger token request */
            ably.auth.requestToken(null, null);

            /* check request contained expected params */
            assertTrue("Verify expected params passed to authURL", httpListener.getFirstRequest().url.getQuery().contains("test-param=test-value"));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_urlParams_post: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify authURL is passed specified params in a GET, with the specified precedence
     * Spec: RSA8c1c
     */
    @Test
    public void auth_authURL_urlParams_get_conflict() {
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            RawHttpTracker httpListener = new RawHttpTracker();
            opts.httpListener = httpListener;

            opts.authUrl = "http://localhost:8982/get-token-request?test-param=test-value-urlParam";
            opts.authParams = new Param[]{new Param("test-param", "test-value-authParam")};
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* make a call to trigger token request */
            ably.auth.requestToken(null, null);

            /* check request contained expected params */
            assertTrue("Verify expected params passed to authURL", httpListener.getFirstRequest().url.getQuery().contains("test-param=test-value-authParam"));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_urlParams_get: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify tokenParams take precedence over authParams in authURL request
     * Spec: RSA8c2
     */
    @Test
    public void auth_authURL_authParams_get_conflict() {
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            RawHttpTracker httpListener = new RawHttpTracker();
            opts.httpListener = httpListener;

            opts.authUrl = "http://localhost:8982/get-token-request";
            opts.authParams = new Param[]{new Param("ttl", "500")};
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* make a call to trigger token request */
            ably.auth.requestToken(new TokenParams() {{
                ttl = 300;
            }}, null);

            /* check request contained expected params */
            assertTrue("Verify expected params passed to authURL", httpListener.getFirstRequest().url.getQuery().contains("ttl=300"));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_urlParams_get: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify authURL is passed specified headers in a GET
     * Spec: RSA8c3
     */
    @Test
    public void auth_authURL_authHeaders_get() {
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            RawHttpTracker httpListener = new RawHttpTracker();
            opts.httpListener = httpListener;

            opts.authUrl = "http://localhost:8982/get-token-request";
            opts.authHeaders = new Param[]{new Param("test-header", "test-value")};
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* make a call to trigger token request */
            ably.auth.requestToken(null, null);

            /* check request contained expected params */
            List<String> headers = httpListener.getFirstRequest().requestHeaders.get("test-header");
            assertTrue("Verify expected headers passed to authURL", headers.contains("test-value"));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_authHeaders_get: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify authURL is passed specified headers in a POST
     * Spec: RSA8c3
     */
    @Test
    public void auth_authURL_authHeaders_post() {
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            RawHttpTracker httpListener = new RawHttpTracker();
            opts.httpListener = httpListener;

            opts.authUrl = "http://localhost:8982/get-token-request";
            opts.authHeaders = new Param[]{new Param("test-header", "test-value")};
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* make a call to trigger token request */
            ably.auth.requestToken(null, null);

            /* check request contained expected params */
            List<String> headers = httpListener.getFirstRequest().requestHeaders.get("test-header");
            assertTrue("Verify expected headers passed to authURL", headers.contains("test-value"));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_authHeaders_post: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify authCallback called and handled when returning {@code TokenRequest}
     * Spec: RSA8d
     */
    @Test
    public void auth_authcallback_tokenrequest() {
        try {
            /* implement callback, using Ably instance with key */
            TokenCallback authCallback = new TokenCallback() {
                private PubSubHttpClient ably = HttpClientFactory.create(createOptions(testVars.keys[0].keyStr));
                @Override
                public Object getTokenRequest(TokenParams params) throws AblyException {
                    return ably.auth.createTokenRequest(params, null);
                }
            };

            /* create Ably instance without key */
            ClientOptions opts = createOptions();
            opts.authCallback = authCallback;
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* make a call to trigger token request */
            try {
                TokenDetails tokenDetails = ably.auth.requestToken(null, null);
                assertNotNull("Expected token value", tokenDetails.token);
            } catch (AblyException e) {
                e.printStackTrace();
                fail("auth_authURL_tokenrequest: Unexpected exception requesting token");
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_tokenrequest: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify authCallback called and handled when returning {@code TokenDetails}
     * Spec: RSA8d
     */
    @Test
    public void auth_authcallback_tokendetails() {
        try {
            /* implement callback, using Ably instance with key */
            TokenCallback authCallback = new TokenCallback() {
                private PubSubHttpClient ably = HttpClientFactory.create(createOptions(testVars.keys[0].keyStr));
                @Override
                public Object getTokenRequest(TokenParams params) throws AblyException {
                    return ably.auth.requestToken(params, null);
                }
            };

            /* create Ably instance without key */
            ClientOptions opts = createOptions();
            opts.authCallback = authCallback;
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* make a call to trigger token request */
            try {
                TokenDetails tokenDetails = ably.auth.requestToken(null, null);
                assertNotNull("Expected token value", tokenDetails.token);
            } catch (AblyException e) {
                e.printStackTrace();
                fail("auth_authURL_tokenrequest: Unexpected exception requesting token");
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_tokenrequest: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify authCallback called and handled when returning token string
     * Spec: RSA8d
     */
    @Test
    public void auth_authcallback_tokenstring() throws AblyException {
            /* implement callback, using Ably instance with key */
        TokenCallback authCallback = new TokenCallback() {
            private PubSubHttpClient ably = HttpClientFactory.create(createOptions(testVars.keys[0].keyStr));
            @Override
            public Object getTokenRequest(TokenParams params) throws AblyException {
                return ably.auth.requestToken(params, null).token;
            }
        };

        /* create Ably instance without key */
        ClientOptions opts = createOptions();
        opts.authCallback = authCallback;
        PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* make a call to trigger token request */
        try {
            TokenDetails tokenDetails = ably.auth.requestToken(null, null);
            assertNotNull("Expected token value", tokenDetails.token);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_tokenrequest: Unexpected exception requesting token");
        }
    }

    /**
     * Verify authCallback called when token expires; Ably initialised with token
     */
    @Test
    public void auth_authcallback_token_expire() {
        try {
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            final PubSubHttpClient ablyForToken = HttpClientFactory.create(optsForToken);
            TokenDetails tokenDetails = ablyForToken.auth.requestToken(new TokenParams() {{ ttl = 5000L; }}, null);
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

            /* create Ably instance without key */
            ClientOptions opts = createOptions();
            opts.token = tokenDetails.token;
            opts.authCallback = authCallback;
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* wait until token expires */
            try {
                Thread.sleep(6000L);
            } catch(InterruptedException ie) {}

            /* make a request that relies on the token */
            try {
                ably.stats(new Param[] { new Param("by", "hour"), new Param("limit", "1") });
            } catch (AblyException e) {
                e.printStackTrace();
                fail("auth_authURL_tokenrequest: Unexpected exception requesting token");
            }

            /* verify that the auth callback was called */
            assertEquals("Expected token generator to be called", 1, authCallback.getCbCount());
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_tokenrequest: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify authCallback called when token expires; Ably initialised with key
     */
    @Test
    public void auth_authcallback_key_expire() {
        try {
            /* create Ably instance with key */
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.clientId = "testClientId";
            opts.useTokenAuth = true;
            opts.defaultTokenParams.ttl = 5000L;
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* make a request that relies on the token */
            System.out.println("auth_authcallback_key_expire: making first request");
            try {
                ably.stats(new Param[] { new Param("by", "hour"), new Param("limit", "1") });
            } catch (AblyException e) {
                e.printStackTrace();
                fail("auth_authURL_tokenrequest: Unexpected exception requesting token");
            }
            String firstToken = ably.auth.getTokenDetails().token;

            /* wait until token expires */
            System.out.println("auth_authcallback_key_expire: sleeping");
            try {
                Thread.sleep(6000L);
            } catch(InterruptedException ie) {}

            /* make a request that relies on the token */
            System.out.println("auth_authcallback_key_expire: making second request");
            try {
                ably.stats(new Param[] { new Param("by", "hour"), new Param("limit", "1") });
            } catch (AblyException e) {
                e.printStackTrace();
                fail("auth_authURL_tokenrequest: Unexpected exception requesting token");
            }
            String secondToken = ably.auth.getTokenDetails().token;

            /* verify that the token was renewed */
            assertNotEquals("Verify token was renewed", firstToken, secondToken);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_tokenrequest: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify authCallback called and handled when returning error
     */
    @Test
    public void auth_authcallback_err() {
        try {
            /* implement callback, using Ably instance with key */
            TokenCallback authCallback = new TokenCallback() {
                @Override
                public Object getTokenRequest(TokenParams params) throws AblyException {
                    throw AblyException.fromErrorInfo(new ErrorInfo("test exception", 404, 12345));
                }
            };

            /* create Ably instance without key */
            ClientOptions opts = createOptions();
            opts.authCallback = authCallback;
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* make a call to trigger token request */
            try {
                ably.auth.requestToken(null, null);
                fail("auth_authURL_err: Unexpected success requesting token");
            } catch (AblyException e) {
                assertEquals("Expected error code", e.errorInfo.code, 80019);
                assertEquals("Expected forwarded error code", ((AblyException)e.getCause()).errorInfo.code, 12345);
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_token: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify library throws an error on initialistion if no auth details are provided
     * Spec: RSA14
     */
    @Test
    public void authinit_no_auth() {
        try {
            ClientOptions opts = new ClientOptions();
            HttpClientFactory.create(opts);
            fail("authinit_no_auth: Unexpected success instantiating library");
        } catch (AblyException e) {
            assertEquals("Verify exception thrown initialising library", e.errorInfo.code, 40000);
        }
    }

    /**
     * Verify preemptive auth occurs when an API call is made using basic auth
     */
    @Test
    public void auth_preemptive_auth_basic() {
        try {
            /* create Ably instance with key */
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            RawHttpTracker httpListener = new RawHttpTracker();
            opts.httpListener = httpListener;
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* make a request that relies on authentication */
            try {
                ably.stats(new Param[] { new Param("by", "hour"), new Param("limit", "1") });
            } catch (AblyException e) {
                e.printStackTrace();
                fail("auth_preemptive_auth_basic: Unexpected exception making API call");
            }

            /* verify that the request was sent once only with a basic auth header */
            assertEquals("Verify one request made", httpListener.size(), 1);
            assertTrue("Verify request had auth header", httpListener.getFirstRequest().authHeader.startsWith("Basic"));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_preemptive_auth_basic: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify preemptive auth occurs when an API call is on an Ably instanced initialised with a token
     */
    @Test
    public void auth_preemptive_auth_given_token() {
        try {
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            optsForToken.clientId = "testClientId";
            PubSubHttpClient ablyForToken = HttpClientFactory.create(optsForToken);
            TokenDetails tokenDetails = ablyForToken.auth.requestToken(null, null);

            /* create Ably instance with token */
            DebugOptions opts = new DebugOptions(tokenDetails.token);
            fillInOptions(opts);
            RawHttpTracker httpListener = new RawHttpTracker();
            opts.httpListener = httpListener;
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* make a request that relies on authentication */
            try {
                ably.stats(new Param[] { new Param("by", "hour"), new Param("limit", "1") });
            } catch (AblyException e) {
                e.printStackTrace();
                fail("auth_preemptive_auth_given_token: Unexpected exception making API call");
            }

            /* verify that the request was sent once only with a basic auth header */
            assertEquals("Verify one request made", httpListener.size(), 1);
            assertTrue("Verify request had auth header", httpListener.getFirstRequest().authHeader.startsWith("Bearer"));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_preemptive_auth_given_token: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify preemptive auth occurs when an API call is on an Ably instanced with a key but using token auth
     */
    @Test
    public void auth_preemptive_auth_created_token() {
        try {
            /* create Ably instance with key */
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            opts.clientId = "testClientId";
            opts.useTokenAuth = true;
            RawHttpTracker httpListener = new RawHttpTracker();
            opts.httpListener = httpListener;
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* make a request that relies on authentication */
            try {
                ably.stats(new Param[] { new Param("by", "hour"), new Param("limit", "1") });
            } catch (AblyException e) {
                e.printStackTrace();
                fail("auth_preemptive_auth_basic: Unexpected exception making API call");
            }

            /* verify that there were two requests: one to get a token, and one to make the API call */
            assertEquals("Verify two requests made", httpListener.size(), 2);
            assertTrue("Verify token request made", httpListener.getFirstRequest().url.getPath().endsWith("requestToken"));
            assertTrue("Verify API request had auth header", httpListener.getLastRequest().authHeader.startsWith("Bearer"));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_preemptive_auth_basic: Unexpected exception instantiating library");
        }
    }

    /**
     * RSA7c: A clientId value of "*" provided in ClientOptions throws an exception
     */
    @Test
    public void auth_client_wildcard() {
        ClientOptions opts;
        try {
            opts = createOptions();
            opts.clientId = "*";
            HttpClientFactory.create(opts);
        } catch (AblyException e) {
            assertEquals("Verify exception raised from disallowed wildcard clientId", e.errorInfo.code, 40000);
        }
    }

    /**
     * RSA15a: Any clientId provided in ClientOptions must match any
     * non wildcard ('*') clientId value in TokenDetails
     * RSA15c: Following an auth request which uses a TokenDetails or TokenRequest
     * object that contains an incompatible clientId, the library should ... result
     * in an appropriate error response
     */
    @Test
    public void auth_client_match_token() {
        TokenDetails tokenDetails = new TokenDetails() {{
            clientId = "token clientId";
            token = "not.really.a.token";
        }};
        ClientOptions opts;
        try {
            opts = createOptions();
            opts.tokenDetails = tokenDetails;
            opts.clientId = "options clientId";
            HttpClientFactory.create(opts);
        } catch (AblyException e) {
            assertEquals("Verify exception raised from incompatible clientIds", e.errorInfo.code, 40101);
        }
    }

    /**
     * RSA15a: Any clientId provided in ClientOptions must match any
     * non wildcard ('*') clientId value in TokenDetails
     * RSA15b: If the clientId from TokenDetails or connectionDetails contains
     * only a wildcard string '*', then the client is permitted to be either
     * unidentified or identified by providing
     * a clientId when communicating with Ably
     */
    @Test
    public void auth_client_match_token_wildcard() {
        TokenDetails tokenDetails = new TokenDetails() {{
            clientId = "*";
            token = "not.really.a.token";
        }};
        ClientOptions opts;
        try {
            opts = createOptions();
            opts.tokenDetails = tokenDetails;
            opts.clientId = "options clientId";
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            assertEquals("Verify given clientId is compatible with wildcard token clientId", ably.auth.clientId, "options clientId");
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_token: Unexpected exception instantiating library");
        }
    }

    /**
     * RSA15b: If the clientId from TokenDetails or connectionDetails contains
     * only a wildcard string '*', then the client is permitted to be either
     * unidentified or identified by providing
     * a clientId when communicating with Ably
     */
    @Test
    public void auth_client_null_match_token_wildcard() {
        TokenDetails tokenDetails = new TokenDetails() {{
            clientId = "*";
            token = "not.really.a.token";
        }};
        ClientOptions opts;
        try {
            opts = createOptions();
            opts.tokenDetails = tokenDetails;
            opts.clientId = null;
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            assertEquals("Verify given clientId is compatible with wildcard token clientId", ably.auth.clientId, "*");
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_authURL_token: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify token details has null client id after authenticating with null client id,
     * the message gets published, and published message also does not contain a client id.<br>
     * <br>
     * Spec: RSA8f1
     */
    @Test
    public void auth_clientid_null_success() {
        try {
            /* implement callback, using Ably instance with key */
            TokenCallback authCallback = new TokenCallback() {
                private PubSubHttpClient ably = HttpClientFactory.create(createOptions(testVars.keys[0].keyStr));
                @Override
                public Object getTokenRequest(TokenParams params) throws AblyException {
                    return ably.auth.requestToken(params, null);
                }
            };

            /* create Ably instance without clientId */
            ClientOptions options = createOptions();
            options.clientId = null;
            options.authCallback = authCallback;
            PubSubHttpClient ably = HttpClientFactory.create(options);

            /* Fetch token */
            TokenDetails tokenDetails = ably.auth.requestToken(null, null);
            assertEquals("Auth#clientId is expected to be null", null, ably.auth.clientId);
            assertEquals("TokenDetails#clientId is expected to be null", null, tokenDetails.clientId);

            /* Publish message */
            String messageName = "clientless";
            String messageData = String.valueOf(System.currentTimeMillis());

            Channel channel = ably.channels.get("test");
            channel.publish(messageName, messageData);

            /* Fetch published message */
            PaginatedResult<Message> result = channel.history(null);
            Message[] messages = result.items();
            Message publishedMessage = null;
            Message message;

            for(int i = 0; i < messages.length; i++) {
                message = messages[i];

                if(messageName.equals(message.name) &&
                    messageData.equals(message.data)) {
                    publishedMessage = message;
                    break;
                }
            }

            assertNotNull("Recently published message expected to be accessible", publishedMessage);
            assertEquals("Message#clientId is expected to be null", null, publishedMessage.clientId);
        } catch (Exception e) {
            e.printStackTrace();
            fail("auth_clientid_null_success: Unexpected exception");
        }
    }

    /**
     * Verify message gets rejected when there is a client id mismatch
     * between token details and message<br>
     * <br>
     * Spec: RSA8f2
     */
    @Test
    public void auth_clientid_null_mismatch() throws AblyException {
        PubSubHttpClient ably = null;

        try {
            /* implement callback, using Ably instance with key */
            TokenCallback authCallback = new TokenCallback() {
                private PubSubHttpClient ably = HttpClientFactory.create(createOptions(testVars.keys[0].keyStr));
                @Override
                public Object getTokenRequest(TokenParams params) throws AblyException {
                    return ably.auth.requestToken(params, null);
                }
            };

            /* create Ably instance */
            ClientOptions options = createOptions();
            options.authCallback = authCallback;
            ably = HttpClientFactory.create(options);

            /* Create token with null clientId */
            TokenParams tokenParams = new TokenParams() {{ clientId = null; }};
            TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, null);
            assertEquals("TokenDetails#clientId is expected to be null", null, tokenDetails.clientId);
            assertEquals("Auth#clientId is expected to be null", null, ably.auth.clientId);
        } catch (Exception e) {
            e.printStackTrace();
            fail("auth_clientid_null_mismatch: Unexpected exception");
        }

        try {
            /* Publish a message with mismatching client id */
            Message message = new Message(
                    "I", /* name */
                    "will", /* data */
                    "fail" /* mismatching client id */
            );
            Channel channel = ably.channels.get("test");
            channel.publish(new Message[]{ message });
        } catch(AblyException e) {
            assertEquals("Verify exception is raised with expected error code", e.errorInfo.code, 40012);
        }
    }

    /**
     * Verify message with wildcard `*` client id gets published,
     * and contains null client id.<br>
     * <br>
     * Spec: RSA8f3, RSA7b4
     */
    @Test
    public void auth_clientid_null_wildcard () {
        try {
            /* implement callback, using Ably instance with key */
            TokenCallback authCallback = new TokenCallback() {
                private PubSubHttpClient ably = HttpClientFactory.create(createOptions(testVars.keys[0].keyStr));
                @Override
                public Object getTokenRequest(TokenParams params) throws AblyException {
                    params.clientId = "*";
                    return ably.auth.requestToken(params, null);
                }
            };

            /* create Ably instance with wildcard clientId */
            ClientOptions options = createOptions();
            options.authCallback = authCallback;
            PubSubHttpClient ably = HttpClientFactory.create(options);

            /* Fetch token */
            TokenDetails tokenDetails = ably.auth.authorize(null, null);
            assertEquals("TokenDetails#clientId is expected to be wildcard '*'", "*", tokenDetails.clientId);
            assertEquals("Auth#clientId is expected to be wildcard '*'", "*", ably.auth.clientId);

            /* Publish message */
            String messageName = "wildcard";
            String messageData = String.valueOf(System.currentTimeMillis());

            Channel channel = ably.channels.get("test");
            channel.publish(messageName, messageData);

            /* Fetch published message */
            PaginatedResult<Message> result = channel.history(null);
            Message[] messages = result.items();
            Message publishedMessage = null;
            Message message;

            for(int i = 0; i < messages.length; i++) {
                message = messages[i];

                if(messageName.equals(message.name) &&
                        messageData.equals(message.data)) {
                    publishedMessage = message;
                    break;
                }
            }

            assertNotNull("Recently published message expected to be accessible", publishedMessage);
            assertEquals("Message#clientId is expected to be null", null, publishedMessage.clientId);
        } catch (Exception e) {
            e.printStackTrace();
            fail("auth_clientid_null_wildcard: Unexpected exception");
        }
    }

    /**
     * Verify message with explicit client id successfully gets published,
     * when authenticated with wildcard '*' client id<br>
     * <br>
     * Spec: RSA8f4
     */
    @Test
    public void auth_clientid_explicit_wildcard () {
        try {
            /* implement callback, using Ably instance with key */
            TokenCallback authCallback = new TokenCallback() {
                private PubSubHttpClient ably = HttpClientFactory.create(createOptions(testVars.keys[0].keyStr));
                @Override
                public Object getTokenRequest(TokenParams params) throws AblyException {
                    params.clientId = "*";
                    return ably.auth.requestToken(params, null);
                }
            };

            /* create Ably instance with wildcard clientId */
            ClientOptions options = createOptions();
            options.authCallback = authCallback;
            PubSubHttpClient ably = HttpClientFactory.create(options);

            /* Fetch token */
            TokenDetails tokenDetails = ably.auth.authorize(null, null);
            assertEquals("TokenDetails#clientId is expected to be wildcard '*'", "*", tokenDetails.clientId);
            assertEquals("Auth#clientId is expected to be wildcard '*'", "*", ably.auth.clientId);

            /* Publish a message */
            Message messagePublishee = new Message(
                    "wildcard",  /* name */
                    String.valueOf(System.currentTimeMillis()), /* data */
                    "brian that is called brian" /* clientId */
            );

            Channel channel = ably.channels.get("test");
            channel.publish(new Message[] { messagePublishee });

            /* Fetch published message */
            PaginatedResult<Message> result = channel.history(null);
            Message[] messages = result.items();
            Message messagePublished = null;
            Message message;

            for(int i = 0; i < messages.length; i++) {
                message = messages[i];

                if(messagePublishee.name.equals(message.name) &&
                        messagePublishee.data.equals(message.data)) {
                    messagePublished = message;
                    break;
                }
            }

            assertNotNull("Recently published message expected to be accessible", messagePublished);
            assertEquals("Message#clientId is expected to be same with explicitly defined clientId", messagePublishee.clientId, messagePublished.clientId);
        } catch (Exception e) {
            e.printStackTrace();
            fail("auth_clientid_explicit_wildcard: Unexpected exception");
        }
    }

    /**
     * Verify message does not have explicit client id populated
     * when library is identified
     * Spec: RSA7a1,RSL1g1a
     */
    @Test
    public void auth_clientid_publish_implicit() {
        try {
            String clientId = "test clientId";
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            optsForToken.clientId = clientId;
            PubSubHttpClient ablyForToken = HttpClientFactory.create(optsForToken);
            TokenDetails tokenDetails = ablyForToken.auth.requestToken(null, null);

            final Message[] messages = new Message[1];

            /* create Ably instance with clientId */
            DebugOptions options = new DebugOptions(testVars.keys[0].keyStr) {{
                this.httpListener = new RawHttpListener() {
                    @Override
                    public HttpCore.Response onRawHttpRequest(String id, HttpRequest request, String authHeader,
                                                              Map<String, List<String>> requestHeaders, HttpCore.RequestBody requestBody) {
                        try {
                            if(testParams.useBinaryProtocol) {
                                messages[0] = MessageSerializer.readMsgpack(requestBody.getEncoded())[0];
                            } else {
                                messages[0] = MessageSerializer.readMessagesFromJson(requestBody.getEncoded())[0];
                            }
                        } catch (AblyException e) {
                            e.printStackTrace();
                            fail("auth_clientid_publish_implicit: Unexpected exception");
                        }
                        return null;
                    }

                    @Override
                    public void onRawHttpResponse(String id, String method, HttpCore.Response response) {}

                    @Override
                    public void onRawHttpException(String id, String method, Throwable t) {}
                };
            }};
            fillInOptions(options);
            options.tokenDetails = tokenDetails;
            PubSubHttpClient ably = HttpClientFactory.create(options);

            /* Publish a message */
            Message messagePublishee = new Message(
                    "I have clientId",  /* name */
                    String.valueOf(System.currentTimeMillis()) /* data */
            );

            Channel channel = ably.channels.get("test_" + testParams.name);
            channel.publish(new Message[] { messagePublishee });

            /* Get sent message */
            Message messagePublished = messages[0];
            assertNull("Published message does not contain clientId", messagePublished.clientId);
        } catch (Exception e) {
            e.printStackTrace();
            fail("auth_clientid_publish_implicit: Unexpected exception");
        }
    }

    /**
     * Verify message does have explicit client id populated
     * when library is initialised as wildcard
     * Spec: RSA8f4
     */
    @Test
    public void auth_clientid_publish_explicit_in_message() {
        try {
            final String messageClientId = "test clientId";
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            PubSubHttpClient ablyForToken = HttpClientFactory.create(optsForToken);
            TokenDetails tokenDetails = ablyForToken.auth.requestToken(new TokenParams() {{
                this.clientId = "*";
            }}, null);

            final Message[] messages = new Message[1];

            /* create Ably instance with clientId */
            DebugOptions options = new DebugOptions(testVars.keys[0].keyStr) {{
                this.httpListener = new RawHttpListener() {
                    @Override
                    public HttpCore.Response onRawHttpRequest(String id, HttpRequest request, String authHeader,
                                                              Map<String, List<String>> requestHeaders, HttpCore.RequestBody requestBody) {
                        try {
                            if(testParams.useBinaryProtocol) {
                                messages[0] = MessageSerializer.readMsgpack(requestBody.getEncoded())[0];
                            } else {
                                messages[0] = MessageSerializer.readMessagesFromJson(requestBody.getEncoded())[0];
                            }
                        } catch (AblyException e) {
                            e.printStackTrace();
                            fail("auth_clientid_publish_implicit: Unexpected exception");
                        }
                        return null;
                    }

                    @Override
                    public void onRawHttpResponse(String id, String method, HttpCore.Response response) {}

                    @Override
                    public void onRawHttpException(String id, String method, Throwable t) {}
                };
            }};
            fillInOptions(options);
            options.tokenDetails = tokenDetails;
            PubSubHttpClient ably = HttpClientFactory.create(options);

            /* Publish a message */
            Message messagePublishee = new Message(
                    "I have clientId",  /* name */
                    String.valueOf(System.currentTimeMillis()), /* data */
                    messageClientId /* clientId */
            );

            Channel channel = ably.channels.get("test_" + testParams.name);
            channel.publish(new Message[] { messagePublishee });

            /* Get sent message */
            Message messagePublished = messages[0];
            assertEquals("Published message contains clientId", messagePublished.clientId, messageClientId);
        } catch (Exception e) {
            e.printStackTrace();
            fail("auth_clientid_publish_explicit_in_message: Unexpected exception");
        }
    }

    /**
     * Verify message with wildcard `*` client id gets published,
     * and contains null client id.<br>
     * <br>
     * Spec: RTL6e1
     */
    @Test
    public void auth_clientid_basic_null_wildcard() {
        try {
            /* create Ably instance with basic auth and no clientId */
            ClientOptions options = createOptions(testVars.keys[0].keyStr);
            PubSubHttpClient ably = HttpClientFactory.create(options);

            /* Publish message */
            String messageName = "wildcard";
            String messageData = String.valueOf(System.currentTimeMillis());
            String clientId = "message clientId";

            Channel channel = ably.channels.get("auth_clientid_basic_null_wildcard_" + testParams.name);
            Message message = new Message(messageName, messageData);
            message.clientId = clientId;
            channel.publish(new Message[] { message });

            /* Fetch published message */
            PaginatedResult<Message> result = channel.history(null);
            Message[] messages = result.items();
            Message publishedMessage = null;

            for(int i = 0; i < messages.length; i++) {
                Message msg = messages[i];

                if(messageName.equals(msg.name) &&
                        messageData.equals(msg.data)) {
                    publishedMessage = message;
                    break;
                }
            }

            assertNotNull("Recently published message expected to be accessible", publishedMessage);
            assertEquals("Message#clientId is expected to be set", clientId, publishedMessage.clientId);
        } catch (Exception e) {
            e.printStackTrace();
            fail("auth_clientid_basic_null_wildcard: Unexpected exception");
        }
    }

    /**
     * Verify client id in token is populated from defaultTokenParams
     * when library is initialised without explicit clientId
     * Spec: RSA7a4, RSA7d
     */
    @Test
    public void auth_clientid_in_defaultparams() {
        try {
            final String defaultClientId = "default clientId";

            /* create Ably instance with defaultTokenParams */
            ClientOptions options = createOptions(testVars.keys[0].keyStr);
            options.useTokenAuth = true;
            options.defaultTokenParams = new TokenParams() {{ this.clientId = defaultClientId; }};
            PubSubHttpClient ably = HttpClientFactory.create(options);

            /* get a token with these default params */
            ably.auth.authorize(null, null);

            /* verify that clientId is set */
            assertEquals("Verify expected clientId is set", ably.auth.clientId, defaultClientId);

        } catch (Exception e) {
            e.printStackTrace();
            fail("auth_clientid_in_defaultparams: Unexpected exception");
        }
    }

    /**
     * Verify client id in token populated from ClientOptions
     * overriding any clientId in defaultTokenParams
     * Spec: RSA7a4, RSA7d
     */
    @Test
    public void auth_clientid_in_opts_overrides_defaultparams() {
        try {
            final String defaultClientId = "default clientId";
            final String clientId = "options clientId";

            /* create Ably instance with defaultTokenParams */
            ClientOptions options = createOptions(testVars.keys[0].keyStr);
            options.useTokenAuth = true;
            options.clientId = clientId;
            options.defaultTokenParams = new TokenParams() {{ this.clientId = defaultClientId; }};
            PubSubHttpClient ably = HttpClientFactory.create(options);

            /* get a token with these default params */
            ably.auth.authorize(null, null);

            /* verify that clientId is set */
            assertEquals("Verify expected clientId is set", ably.auth.clientId, clientId);

        } catch (Exception e) {
            e.printStackTrace();
            fail("auth_clientid_in_defaultparams: Unexpected exception");
        }
    }

    /**
     * Verify token auth used when useTokenAuth=true
     */
    @Test
    public void auth_useTokenAuth() {
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.useTokenAuth = true;
            PubSubHttpClient ably = HttpClientFactory.create(opts);
            /* verify that we don't have a token yet. */
            assertTrue("Not expecting a token yet", ably.auth.getTokenDetails() == null);
            /* make a request that relies on the token */
            try {
                ably.stats(new Param[] { new Param("by", "hour"), new Param("limit", "1") });
            } catch (AblyException e) {
                e.printStackTrace();
                fail("Unexpected exception requesting token");
            }
            /* verify that we have a token. */
            assertTrue("Expected to use token auth", ably.auth.getTokenDetails() != null);
            System.out.println("Token is " + ably.auth.getTokenDetails().token);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception instantiating library");
        }
    }

    /**
     * Test behaviour of queryTime parameter in ClientOpts. Time is requested from the Ably server only once,
     * cached value should be used afterwards
     * Spec: RSA9a
     */
    @Test
    public void auth_testQueryTime() {
        try {
            nanoHTTPD.clearRequestHistory();
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            opts.tls = false;
            opts.restHost = "localhost";
            opts.port = nanoHTTPD.getListeningPort();
            opts.queryTime = true;

            PubSubHttpClient ably1 = HttpClientFactory.create(opts);
            @SuppressWarnings("unused")
            Auth.TokenRequest tr1 = ably1.auth.createTokenRequest(null, null);

            PubSubHttpClient ably2 = HttpClientFactory.create(opts);
            @SuppressWarnings("unused")
            Auth.TokenRequest tr2 = ably2.auth.createTokenRequest(null, null);

            List<String> requestHistory = nanoHTTPD.getRequestHistory();
            /* search for all /time request in the list */
            int timeRequestCount = 0;
            for (String request: requestHistory)
                if (request.contains("/time"))
                    timeRequestCount++;

            assertEquals("Verify number of time requests", timeRequestCount, 2);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception");
        }
    }

    /**
     * Verify JSON serialisation and deserialisation of basic types
     * Spec: TE6, TD7
     */
    @Test
    public void auth_json_interop() {
        /* create a token request */
        PubSubHttpClient ably;
        TokenRequest tokenRequest;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.clientId = "Test client id";
            ably = HttpClientFactory.create(opts);
            tokenRequest = ably.auth.createTokenRequest(new TokenParams() {{
                ttl = 10000;
                capability = "{\"*\": [\"*\"]}";
            }}, null);
            String serialisedTokenRequest = tokenRequest.asJson();
            TokenRequest deserialisedTokenRequest = TokenRequest.fromJson(serialisedTokenRequest);
            assertEquals("Verify token request is serialised and deserialised successfully", tokenRequest, deserialisedTokenRequest);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception");
            return;
        }
        /* create a token details */
        try {
            TokenDetails tokenDetails = ably.auth.requestToken(tokenRequest, null);
            String serialisedTokenDetails = tokenDetails.asJson();
            TokenDetails deserialisedTokenDetails = TokenDetails.fromJson(serialisedTokenDetails);
            assertEquals("Verify token details is serialised and deserialised successfully", tokenDetails, deserialisedTokenDetails);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception");
        }
    }

    /**
     * Verify TokenRequest as JSON TTL and capability are omitted if not explicitly set to nonzero.
     * Spec: RSA5, RSA6
     */
    @Test
    public void auth_token_request_json_omitted_defaults() {
        PubSubHttpClient ably;
        TokenRequest tokenRequest;
        try {
            for (final String cap : new String[] {null, ""}) {
                ClientOptions opts = createOptions(testVars.keys[0].keyStr);
                opts.clientId = "Test client id";
                ably = HttpClientFactory.create(opts);
                tokenRequest = ably.auth.createTokenRequest(new TokenParams() {{
                    capability = cap;
                }}, null);
                String serialisedTokenRequest = tokenRequest.asJson();
                assertTrue("Verify token request has no ttl", !serialisedTokenRequest.contains("ttl"));
                assertTrue("Verify token request has no capability", !serialisedTokenRequest.contains("capability"));
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception");
        }
    }

    /**
     * Verify that renewing the token when useTokenAuth is true doesn't use the old (expired) token.
     */
    @Test
    public void auth_renew_token_bearer_auth() {
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.useTokenAuth = true;
            opts.defaultTokenParams = new TokenParams() {{
                ttl = 500;
            }};
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            // Any request will issue a new token with the defaultTokenParams and use it.

            ably.channels.get("test").history(null);
            TokenDetails oldToken = ably.auth.getTokenDetails();

            // Sleep until old token expires, then ensure it did.

            Thread.sleep(1000);
            ClientOptions optsWithOldToken = createOptions();
            optsWithOldToken.tokenDetails = oldToken;
            PubSubHttpClient ablyWithOldToken = HttpClientFactory.create(optsWithOldToken);
            try {
                ablyWithOldToken.channels.get("test").history(null);
                fail("expected old token to be expired already");
            } catch(AblyException e) {}

            // The library should now renew the token using the key.

            ably.channels.get("test").history(null);
            TokenDetails newToken = ably.auth.getTokenDetails();

            assertNotEquals(oldToken.token, newToken.token);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Unexpected exception");
        }
    }

    /**
     * Verify that a local token validity check is made if queryTime == true
     * and local time is in sync with server
     * Spec: RSA4b1
     */
    @Test
    public void auth_local_token_expiry_check_sync() {
        try {
            /* get a TokenDetails and allow to expire */
            final String testKey = testVars.keys[0].keyStr;
            ClientOptions optsForToken = createOptions(testKey);
            optsForToken.queryTime = true;
            PubSubHttpClient ablyForToken = HttpClientFactory.create(optsForToken);

            TokenDetails tokenDetails = ablyForToken.auth.requestToken(new TokenParams(){{ ttl = 100L; }}, null);

            /* create Ably instance with token details */
            DebugOptions opts = new DebugOptions();
            fillInOptions(opts);
            opts.queryTime = true;
            opts.tokenDetails = tokenDetails;
            RawHttpTracker httpListener = new RawHttpTracker();
            opts.httpListener = httpListener;
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* sync this library instance to server by creating a token request */
            ably.auth.createTokenRequest(null, new Auth.AuthOptions() {{ key = testKey; queryTime = true; }});

            /* wait for the token to expire */
            try { Thread.sleep(200L); } catch(InterruptedException ie) {}

            /* make a request that relies on authentication */
            try {
                ably.stats(new Param[] { new Param("by", "hour"), new Param("limit", "1") });
                fail("auth_local_token_expiry_check_sync: API call unexpectedly succeeded");
                return;
            } catch (AblyException e) {
                assertEquals("Verify that API request failed with credentials error", e.errorInfo.code, 40106);
                for(Helpers.RawHttpRequest req : httpListener.values()) {
                    assertFalse("Verify no API request attempted", req.url.getPath().contains("stats"));
                }
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_local_token_expiry_check_sync: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify that a local token validity check is not made if queryTime == false
     * and local time is not in sync with server
     * Spec: RSA4b1
     */
    @Test
    public void auth_local_token_expiry_check_nosync() {
        try {
            /* get a TokenDetails and allow to expire */
            final String testKey = testVars.keys[0].keyStr;
            ClientOptions optsForToken = createOptions(testKey);
            optsForToken.queryTime = true;
            PubSubHttpClient ablyForToken = HttpClientFactory.create(optsForToken);

            TokenDetails tokenDetails = ablyForToken.auth.requestToken(new TokenParams(){{ ttl = 100L; }}, null);

            /* create Ably instance with token details */
            DebugOptions opts = new DebugOptions();
            fillInOptions(opts);
            opts.queryTime = false;
            opts.tokenDetails = tokenDetails;
            RawHttpTracker httpListener = new RawHttpTracker();
            opts.httpListener = httpListener;
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* wait for the token to expire */
            try { Thread.sleep(200L); } catch(InterruptedException ie) {}

            /* make a request that relies on authentication */
            try {
                ably.stats(new Param[] { new Param("by", "hour"), new Param("limit", "1") });
                fail("auth_local_token_expiry_check_nosync: API call unexpectedly succeeded");
                return;
            } catch (AblyException e) {
                assertEquals("Verify API request attempted", httpListener.size(), 1);
                assertEquals("Verify API request failed with token expiry error", httpListener.getFirstRequest().response.headers.get("x-ably-errorcode").get(0), "40142");
                assertEquals("Verify that API request failed with credentials error", e.errorInfo.code, 40106);
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_local_token_expiry_check_nosync: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify that if a local token validity check suppressed because queryTime == false
     * this does not prevent token renewal by an auth callback when a request fails
     * Spec: RSA4b1
     */
    @Test
    public void auth_local_token_expiry_check_nosync_retried() {
        try {
            /* get a TokenDetails and allow to expire */
            final String testKey = testVars.keys[0].keyStr;
            ClientOptions optsForToken = createOptions(testKey);
            optsForToken.queryTime = false;
            final PubSubHttpClient ablyForToken = HttpClientFactory.create(optsForToken);

            TokenDetails tokenDetails = ablyForToken.auth.requestToken(new TokenParams(){{ ttl = 100L; }}, null);

            /* create Ably instance with token details */
            DebugOptions opts = new DebugOptions();
            fillInOptions(opts);
            opts.queryTime = false;
            opts.tokenDetails = tokenDetails;
            opts.authCallback = new TokenCallback() {
                @Override
                public Object getTokenRequest(TokenParams params) throws AblyException {
                    return ablyForToken.auth.createTokenRequest(params, null);
                }
            };

            RawHttpTracker httpListener = new RawHttpTracker();
            opts.httpListener = httpListener;
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* wait for the token to expire */
            try { Thread.sleep(200L); } catch(InterruptedException ie) {}

            /* make a request that relies on authentication */
            try {
                ably.stats(new Param[] { new Param("by", "hour"), new Param("limit", "1") });
            } catch (AblyException e) {
                fail("auth_local_token_expiry_check_nosync: API call unexpectedly failed");
                return;
            }
            assertEquals("Verify API request attempted", httpListener.size(), 3);
            for(Helpers.RawHttpRequest x : httpListener.values()) {
                System.out.println(x.url.toString());
            }
            assertEquals("Verify API request failed with token expiry error", httpListener.getFirstRequest().response.headers.get("x-ably-errorcode").get(0), "40142");
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_local_token_expiry_check_nosync: Unexpected exception instantiating library");
        }
    }

    private static TokenServer tokenServer;
    private static SessionHandlerNanoHTTPD nanoHTTPD;

    private static class SessionHandlerNanoHTTPD extends RouterNanoHTTPD {
        private final ArrayList<String> requestHistory = new ArrayList<>();

        SessionHandlerNanoHTTPD(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            synchronized (requestHistory) {
                requestHistory.add(session.getUri());
            }
            /* the only request supported here is /time */
            return newFixedLengthResponse(String.format(Locale.US, "[%d]", System.currentTimeMillis()));
        }

        public void clearRequestHistory() { requestHistory.clear(); }

        public List<String> getRequestHistory() { return requestHistory; }
    }

}
