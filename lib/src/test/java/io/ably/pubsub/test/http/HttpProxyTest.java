package io.ably.pubsub.test.http;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

import io.ably.pubsub.http.HttpAuth;
import io.ably.pubsub.http.HttpClientFactory;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.PaginatedResult;
import io.ably.pubsub.types.ProxyOptions;
import io.ably.pubsub.types.Stats;

public class HttpProxyTest extends ParameterizedTest {

    /**
     * Check access to stats API via proxy with invalid host, expecting failure
     */
    @Test
    public void proxy_simple_invalid_host() {
        try {
            /* setup client */
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.proxy = new ProxyOptions() {{
                host = "not-sandbox-proxy.ably.io";
                port = 6128;
            }};
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* attempt the call, expecting no exception */
            ably.stats(null);
            fail("proxy_simple_invalid_host: call succeeded unexpectedly");
        } catch (AblyException.HostFailedException e) {
            /* Verify we got a 50x */
            assertTrue(true);
        } catch (AblyException e) {
            fail("Wrong error code");
        }
    }

    /**
     * Check access to stats API via proxy with invalid port, expecting failure
     */
    @Test
    public void proxy_simple_invalid_port() {
        try {
            /* setup client */
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.proxy = new ProxyOptions() {{
                host = "sandbox-proxy.ably.io";
                port = 6127;
            }};
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* attempt the call, expecting no exception */
            ably.stats(null);
            fail("proxy_simple_invalid_port: call succeeded unexpectedly");
        } catch (AblyException.HostFailedException e) {
            /* Verify we got a 50x */
            assertTrue(true);
        } catch (AblyException e) {
            fail("Wrong error code");
        }
    }

    /**
     * Check access to stats API via proxy, non-TLS
     */
    @Test
    @Ignore("Proxy server not running")
    public void proxy_simple_plain() {
        try {
            /* setup client */
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.clientId = "testClientId"; /* force use of token auth */
            opts.tls = false;
            opts.proxy = new ProxyOptions() {{
                host = "sandbox-proxy.ably.io";
                port = 6128;
            }};
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* attempt the call, expecting no exception */
            PaginatedResult<Stats> stats = ably.stats(null);
            assertNotNull("Expected non-null stats", stats);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("proxy_simple_plain: Unexpected exception");
            return;
        }
    }

    /**
     * Check access to stats API via proxy
     */
    @Test
    @Ignore("Proxy server not running")
    public void proxy_simple_tls() {
        try {
            /* setup client */
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.proxy = new ProxyOptions() {{
                host = "sandbox-proxy.ably.io";
                port = 6128;
            }};
            PubSubHttpClient ably = HttpClientFactory.create(opts);

            /* attempt the call, expecting no exception */
            PaginatedResult<Stats> stats = ably.stats(null);
            assertNotNull("Expected non-null stats", stats);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("proxy_simple_tls: Unexpected exception");
            return;
        }
    }

    /**
     * Check access to stats API via proxy with authentication, non-tls
     */
    @Test
    @Ignore("Proxy server not running")
    public void proxy_basic_auth_plain() {
        try {
            /* setup client */
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.clientId = "testClientId";
            opts.tls = false;
            opts.proxy = new ProxyOptions() {{
                host = "sandbox-proxy.ably.io";
                port = 6129;
                username = "ably";
                password = "password";
            }};
            PubSubHttpClient ably = HttpClientFactory.create(opts);
    
            /* attempt the call, expecting no exception */
            PaginatedResult<Stats> stats = ably.stats(null);
            assertNotNull("Expected non-null stats", stats);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("proxy_basic_auth_plain: Unexpected exception");
            return;
        }
    }

    /**
     * Check access to stats API via proxy with authentication, non-tls
     */
    //@Test
    public void proxy_digest_auth_plain() {
        try {
            /* setup client */
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.clientId = "testClientId";
            opts.tls = false;
            opts.proxy = new ProxyOptions() {{
                host = "sandbox-proxy.ably.io";
                port = 6129;
                username = "ably-digest";
                password = "password";
                prefAuthType = HttpAuth.Type.DIGEST;
            }};
            PubSubHttpClient ably = HttpClientFactory.create(opts);
    
            /* attempt the call, expecting no exception */
            PaginatedResult<Stats> stats = ably.stats(null);
            assertNotNull("Expected non-null stats", stats);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("proxy_digest_auth_plain: Unexpected exception");
            return;
        }
    }

    /**
     * Check access to stats API via proxy with authentication, tls
     */
    //@Test
    public void proxy_basic_auth_tls() {
        try {
            /* setup client */
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.proxy = new ProxyOptions() {{
                host = "sandbox-proxy.ably.io";
                port = 6129;
                username = "ably";
                password = "password";
            }};
            PubSubHttpClient ably = HttpClientFactory.create(opts);
    
            /* attempt the call, expecting no exception */
            PaginatedResult<Stats> stats = ably.stats(null);
            assertNotNull("Expected non-null stats", stats);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("proxy_basic_auth_tls: Unexpected exception");
            return;
        }
    }
}
