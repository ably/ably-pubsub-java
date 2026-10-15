package io.ably.pubsub.transport;

import io.ably.pubsub.BuildConfig;
import io.ably.pubsub.types.ClientOptions;

public class Defaults {
    /**
     * The level of compatibility with the Ably service that this SDK supports.
     * Also referred to as the 'wire protocol version'.
     * This value is presented as a string, as specified in G4a.
     * <p>
     * spec: G4
     * </p>
     */
    public static final String ABLY_PROTOCOL_VERSION = "6";

    /**
     * The SDK family identifier. It renamed from {@code ably-java} with the per-side package
     * split, so the identifier alone partitions the fleet: {@code ably-java/*} is legacy-package
     * traffic, {@code ably-pubsub-java/*} is new-package traffic. It names the family rather than
     * any one published artifact; the side a client declares travels as a separate versionless
     * agent entry (see {@link io.ably.pubsub.util.Side} and the agents registry in ably-common).
     */
    public static final String ABLY_AGENT_VERSION   = String.format("%s/%s", "ably-pubsub-java", BuildConfig.VERSION);

    /* realtime params */
    public static final String ABLY_PROTOCOL_VERSION_PARAM = "v";
    public static final String ABLY_AGENT_PARAM = "agent";

    /* http headers */
    public static final String ABLY_PROTOCOL_VERSION_HEADER = "X-Ably-Version";
    public static final String ABLY_CLIENT_ID_HEADER = "X-Ably-ClientId";
    public static final String ABLY_AGENT_HEADER = "Ably-Agent";

    /* Hosts */
    /* REC1a: the endpoint used when ClientOptions#endpoint is unset */
    public static final String ENDPOINT             = "main";
    /* REC2c1: the fallback domains for the default endpoint */
    public static final String[] HOST_FALLBACKS     = getEndpointFallbackHosts(ENDPOINT);
    public static final int PORT                    = 80;
    public static final int TLS_PORT                = 443;

    /* Timeouts */
    public static int TIMEOUT_CONNECT               = 15000;
    public static int TIMEOUT_DISCONNECT            = 15000;
    public static int TIMEOUT_CHANNEL_RETRY         = 15000;

    /* TO3l3 */
    public static int TIMEOUT_HTTP_OPEN = 4000;
    /* TO3l4 */
    public static int TIMEOUT_HTTP_REQUEST = 10000;
    /* TO3l6 */
    public static int httpMaxRetryDuration = 15000;

    /* DF1b */
    public static long realtimeRequestTimeout = 10000L;
    /* TO3l2 */
    public static long suspendedRetryTimeout = 30000L;
    /* TO3l10 */
    public static long fallbackRetryTimeout = 10*60*1000L;
    /* CD2h (but no default in the spec) */
    public static long maxIdleInterval = 20000L;
    // 64kB, as per CD2c
    public static int maxMessageSize = 65536;
    /* DF1a */
    public static long connectionStateTtl = 120000L;

    public static final ITransport.Factory TRANSPORT = new WebSocketTransport.Factory();
    public static final int HTTP_MAX_RETRY_COUNT    = 3;
    public static final int HTTP_ASYNC_THREADPOOL_SIZE = 64;

    public static int getPort(ClientOptions options) {
        return options.tls
            ? ((options.tlsPort != 0) ? options.tlsPort : Defaults.TLS_PORT)
            : ((options.port != 0) ? options.port : Defaults.PORT);
    }

    /**
     * Resolves the primary domain that both REST requests and realtime connections use.
     * <p>
     * Spec: REC1
     *
     * @param endpoint the configured endpoint, or null for the default
     * @return the primary domain
     */
    public static String getPrimaryDomain(String endpoint) {
        if (endpoint == null) {
            endpoint = ENDPOINT;
        }
        /* REC1b2 */
        if (isHostname(endpoint)) {
            return endpoint;
        }
        /* REC1b3 */
        if (endpoint.startsWith(NONPROD_PREFIX)) {
            return endpoint.substring(NONPROD_PREFIX.length()) + ".realtime.ably-nonprod.net";
        }
        /* REC1b4 */
        return endpoint + ".realtime.ably.net";
    }

    /**
     * Resolves the default fallback domains for an endpoint, used when ClientOptions#fallbackHosts is unset.
     * <p>
     * Spec: REC2c
     *
     * @param endpoint the configured endpoint, or null for the default
     * @return the fallback domains; empty when the endpoint is a hostname
     */
    public static String[] getEndpointFallbackHosts(String endpoint) {
        if (endpoint == null) {
            endpoint = ENDPOINT;
        }
        /* REC2c2 */
        if (isHostname(endpoint)) {
            return new String[0];
        }
        /* REC2c3 */
        if (endpoint.startsWith(NONPROD_PREFIX)) {
            return fallbackHosts(endpoint.substring(NONPROD_PREFIX.length()), "ably-realtime-nonprod.com");
        }
        /* REC2c1, REC2c4 */
        return fallbackHosts(endpoint, "ably-realtime.com");
    }

    private static final String NONPROD_PREFIX = "nonprod:";

    /* REC1b2: an endpoint is a hostname if it contains '.' or '::', or is "localhost" */
    private static boolean isHostname(String endpoint) {
        return endpoint.contains(".") || endpoint.contains("::") || endpoint.equals("localhost");
    }

    private static String[] fallbackHosts(String routingPolicyId, String domain) {
        return new String[] {
            routingPolicyId + ".a.fallback." + domain,
            routingPolicyId + ".b.fallback." + domain,
            routingPolicyId + ".c.fallback." + domain,
            routingPolicyId + ".d.fallback." + domain,
            routingPolicyId + ".e.fallback." + domain
        };
    }
}
