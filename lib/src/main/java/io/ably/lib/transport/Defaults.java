package io.ably.lib.transport;

import io.ably.lib.BuildConfig;
import io.ably.lib.types.ClientOptions;

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
     * agent entry (see io.ably.pubsub.internal.Side and the agents registry in ably-common).
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
    public static final String[] HOST_FALLBACKS     = { "A.ably-realtime.com", "B.ably-realtime.com", "C.ably-realtime.com", "D.ably-realtime.com", "E.ably-realtime.com" };
    public static final String HOST_REST            = "rest.ably.io";
    public static final String HOST_REALTIME        = "realtime.ably.io";
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

    /* Construct environment fallback hosts as per RSC15i */
    public static String[] getEnvironmentFallbackHosts(String environment) {
        return new String[] {
            environment + "-a-fallback.ably-realtime.com",
            environment + "-b-fallback.ably-realtime.com",
            environment + "-c-fallback.ably-realtime.com",
            environment + "-d-fallback.ably-realtime.com",
            environment + "-e-fallback.ably-realtime.com"
        };
    }
}
