package io.ably.lib.types;

import io.ably.lib.push.Storage;
import io.ably.lib.rest.Auth.AuthOptions;
import io.ably.lib.rest.Auth.TokenParams;
import io.ably.lib.transport.Defaults;
import io.ably.lib.util.Log;
import io.ably.lib.util.Log.LogHandler;

import java.util.Map;

/**
 * Passes additional client-specific properties to the {@link io.ably.lib.rest.AblyRest} or the {@link io.ably.lib.realtime.AblyRealtime}.
 * <p>
 * Extends an {@link AuthOptions} object.
 * <p>
 * Spec: TO3j
 */
public class ClientOptions extends AuthOptions {

    /**
     * Creates a ClientOptions instance used to configure Rest and Realtime clients
     */
    public ClientOptions() {}

    /**
     * Creates a ClientOptions instance used to configure Rest and Realtime clients
     *
     * @param key the key obtained from the application dashboard.
     * @throws AblyException if the key is not in a valid format
     */
    public ClientOptions(String key) throws AblyException {
        super(key);
        logLevel = Log.defaultLevel;
    }

    /**
     * A client ID, used for identifying this client when publishing messages or for presence purposes.
     * The clientId can be any non-empty string, except it cannot contain a *.
     * This option is primarily intended to be used in situations where the library is instantiated with a key.
     * Note that a clientId may also be implicit in a token used to instantiate the library.
     * An error will be raised if a clientId specified here conflicts with the clientId implicit in the token.
     * <p>
     * Spec: RSC17, RSA4, RSA15, TO3a
     */
    public String clientId;

    /**
     * Controls the verbosity of the logs output from the library. Levels include verbose, debug, info, warn and error.
     * <p>
     * Spec: TO3b
     */
    public int logLevel;

    /**
     * Controls the log output of the library. This is a function to handle each line of log output.
     * <p>
     * Spec: TO3c
     */
    public LogHandler logHandler;

    /**
     * When false, the client will use an insecure connection.
     * The default is true, meaning a TLS connection will be used to connect to Ably.
     * <p>
     * Spec: RSC18, TO3d
     */
    public boolean tls = true;

    /**
     * FIXME: unused
     */
    public Map<String, String> headers;

    /**
     * Enables a non-default Ably host to be specified. For development environments only.
     * The default value is rest.ably.io.
     * <p>
     * Spec: RSC12, TO3k2
     */
    public String restHost;

    /**
     * Enables a non-default Ably host to be specified for realtime connections.
     * For development environments only. The default value is realtime.ably.io.
     * <p>
     * Spec: RTC1d, TO3k3
     */
    public String realtimeHost;

    /**
     * Enables a non-default Ably port to be specified. For development environments only. The default value is 80.
     * <p>
     * Spec: TO3k4
     */
    public int port;

    /**
     * Enables a non-default Ably TLS port to be specified. For development environments only.
     * The default value is 443.
     * <p>
     * Spec: TO3k5
     */
    public int tlsPort;

    /**
     * When true, the client connects to Ably as soon as it is instantiated.
     * You can set this to false and explicitly connect to Ably using the
     * {@link io.ably.lib.realtime.Connection#connect} method. The default is true.
     * <p>
     * Spec: RTC1b, TO3e
     */
    public boolean autoConnect = true;

    /**
     * When true, the more efficient MsgPack binary encoding is used. When false, JSON text encoding is used.
     * The default is true.
     * <p>
     * Spec: TO3f
     */
    public boolean useBinaryProtocol = true;

    /**
     * If false, this disables the default behavior whereby the library queues messages
     * on a connection in the disconnected or connecting states.
     * The default behavior enables applications to submit messages immediately upon
     * instantiating the library without having to wait for the connection to be established.
     * Applications may use this option to disable queueing if they wish to have
     * application-level control over the queueing. The default is true.
     * <p>
     * Spec: RTP16b, TO3g
     */
    public boolean queueMessages = true;

    /**
     * If false, prevents messages originating from this connection being echoed back on the same connection. The default is true.
     * <p>
     * Spec: RTC1a, TO3h
     */
    public boolean echoMessages = true;

    /**
     * Enables a connection to inherit the state of a previous connection that may have existed under a
     * different instance of the Realtime library. This might typically be used by clients of the browser
     * library to ensure connection state can be preserved when the user refreshes the page.
     * A recovery key string can be explicitly provided, or alternatively if a callback function is provided,
     * the client library will automatically persist the recovery key between page reloads and call the callback
     * when the connection is recoverable. The callback is then responsible for confirming whether the connection
     * should be recovered or not. See connection state recovery for further information.
     * <p>
     * Spec: RTC1c, TO3i, RTN16i
     */
    public String recover;

    /**
     * Proxy settings
     */
    public ProxyOptions proxy;

    /**
     * Enables a <a href="https://ably.com/docs/platform-customization">custom environment</a> to be used with the Ably service.
     * <p>
     * Spec: RSC15b, TO3k1
     */
    public String environment;

    /**
     * When true, enables idempotent publishing by assigning a unique message ID client-side,
     * allowing the Ably servers to discard automatic publish retries following a failure such as a network fault.
     * The default is true.
     * <p>
     * Spec: RSL1k1, RTL6a1, TO3n
     */
    public boolean idempotentRestPublishing = true;

    /**
     * Timeout for opening a connection to Ably to initiate an HTTP request.
     * The default is 4 seconds.
     * <p>
     * Spec: TO3l3
     */
    public int httpOpenTimeout = Defaults.TIMEOUT_HTTP_OPEN;

    /**
     * Timeout for a client performing a complete HTTP request to Ably, including the connection phase.
     * The default is 10 seconds.
     * <p>
     * Spec: TO3l4
     */
    public int httpRequestTimeout = Defaults.TIMEOUT_HTTP_REQUEST;

    /**
     * Denotes elapsed time in which fallback host retries for HTTP requests will be attempted.
     * Default is 15 seconds.
     * Spec: TO3l6
     */
    public int httpMaxRetryDuration = Defaults.httpMaxRetryDuration;

    /**
     * The maximum number of fallback hosts to use as a fallback when an HTTP request to the primary host
     * is unreachable or indicates that it is unserviceable.
     * The default value is 3.
     * <p>
     * Spec: TO3l5
     */
    public int httpMaxRetryCount = Defaults.HTTP_MAX_RETRY_COUNT;

    /**
     * Timeout for the wait of acknowledgement for operations performed via a realtime connection,
     * before the client library considers a request failed and triggers a failure condition.
     * Operations include establishing a connection with Ably, or sending a HEARTBEAT, CONNECT, ATTACH, DETACH or CLOSE request.
     * It is the equivalent of httpRequestTimeout but for realtime operations, rather than REST.
     * The default is 10 seconds.
     * <p>
     * Spec: TO3l11
     */
    public long realtimeRequestTimeout = Defaults.realtimeRequestTimeout;

    /**
     * When the connection enters the disconnected state, after this timeout,
     * if the state is still disconnected, the client library will attempt to reconnect automatically.
     * The default is 15 seconds (TO3l1).
     * <p>
     * Spec: TO3l1
     */
    public long disconnectedRetryTimeout = Defaults.TIMEOUT_DISCONNECT;

    /**
     * An array of fallback hosts to be used in the case of an error necessitating the use of an alternative host.
     * If you have been provided a set of custom fallback hosts by Ably, please specify them here.
     * <p>
     * Spec: RSC15b, RSC15a, TO3k6
     */
    public String[] fallbackHosts;

    /**
     * This is a timeout when the connection enters the suspendedState.
     * Client will try to connect indefinitely till state changes to connected.
     * The default is 30 seconds.
     * <p>
     * Spec: RTN14d, TO3l2
     */
    public long suspendedRetryTimeout = Defaults.suspendedRetryTimeout;

    /**
     * An array of fallback hosts to be used in the case of an error necessitating the use of an alternative host.
     * If you have been provided a set of custom fallback hosts by Ably, please specify them here.
     * <p>
     * Spec: RSC15b, RSC15a, TO3k6
     */
    @Deprecated
    public boolean fallbackHostsUseDefault;

    /**
     * The maximum time before HTTP requests are retried against the default endpoint.
     * The default is 600 seconds.
     * <p>
     * Spec: TO3l10
     */
    public long fallbackRetryTimeout = Defaults.fallbackRetryTimeout;

    /**
     * When a {@link TokenParams} object is provided, it overrides the client library
     * defaults when issuing new Ably Tokens or Ably {@link io.ably.lib.rest.Auth.TokenRequest}.
     * <p>
     * Spec: TO3j11
     */
    public TokenParams defaultTokenParams = new TokenParams();

    /**
     * When a channel becomes {@link io.ably.lib.realtime.ChannelState#suspended}
     * following a server initiated {@link io.ably.lib.realtime.ChannelState#detached},
     * after this delay, if the channel is still {@link io.ably.lib.realtime.ChannelState#suspended}
     * and the connection is {@link io.ably.lib.realtime.ConnectionState#connected},
     * the client library will attempt to re-attach the channel automatically.
     * The default is 15 seconds.
     * <p>
     * Spec: RTL13b, TO3l7
     */
    public int channelRetryTimeout = Defaults.TIMEOUT_CHANNEL_RETRY;

    /**
     * A set of key-value pairs that can be used to pass in arbitrary connection parameters,
     * such as <a href="https://ably.com/docs/realtime/connection#heartbeats">heartbeatInterval</a>
     * or <a href="https://ably.com/docs/realtime/presence#unstable-connections">remainPresentFor</a>.
     * <p>
     * Spec: RTC1f
     */
    public Param[] transportParams;

    /**
     * Allows the caller to specify a non-default size for the asyncHttp threadpool
     */
    public int asyncHttpThreadpoolSize = Defaults.HTTP_ASYNC_THREADPOOL_SIZE;

    /**
     * Whether to tell Ably to wait for push REST requests to fully wait for all their effects
     * before responding.
     */
    public boolean pushFullWait = false;

    /**
     * Custom Local Device storage. In the case nothing is provided then a default implementation
     * using SharedPreferences is used.
     */
    public Storage localStorage = null;

    /**
     * When true, every REST request to Ably should include a random string in the request_id query string parameter.
     * The random string should be a url-safe base64-encoding sequence of at least 9 bytes, obtained from a source of randomness.
     * This request ID must remain the same if a request is retried to a fallback host.
     * Any log messages associated with the request should include the request ID.
     * If the request fails, the request ID must be included in the {@link ErrorInfo} returned to the user.
     * The default is false.
     * <p>
     * Spec: TO3p
     */
    public boolean addRequestIds = false;

    /**
     * A set of additional entries for the Ably agent header. Each entry can be a key string or set of key-value pairs.
     * <p>
     * Spec: RSC7d6
     */
    public Map<String, String> agents;

    /**
     * Internal method
     *
     * @return copy of client options
     */
    public ClientOptions copy() {
        ClientOptions copied = new ClientOptions();
        copied.clientId = clientId;
        copied.logLevel = logLevel;
        copied.logHandler = logHandler;
        copied.tls = tls;
        copied.restHost = restHost;
        copied.realtimeHost = realtimeHost;
        copied.port = port;
        copied.tlsPort = tlsPort;
        copied.autoConnect = autoConnect;
        copied.useBinaryProtocol = useBinaryProtocol;
        copied.queueMessages = queueMessages;
        copied.echoMessages = echoMessages;
        copied.recover = recover;
        copied.proxy = proxy;
        copied.environment = environment;
        copied.idempotentRestPublishing = idempotentRestPublishing;
        copied.httpOpenTimeout = httpOpenTimeout;
        copied.httpRequestTimeout = httpRequestTimeout;
        copied.httpMaxRetryDuration = httpMaxRetryDuration;
        copied.httpMaxRetryCount = httpMaxRetryCount;
        copied.realtimeRequestTimeout = realtimeRequestTimeout;
        copied.disconnectedRetryTimeout = disconnectedRetryTimeout;
        copied.suspendedRetryTimeout = suspendedRetryTimeout;
        copied.fallbackHostsUseDefault = fallbackHostsUseDefault;
        copied.fallbackRetryTimeout = fallbackRetryTimeout;
        copied.defaultTokenParams = defaultTokenParams;
        copied.channelRetryTimeout = channelRetryTimeout;
        copied.asyncHttpThreadpoolSize = asyncHttpThreadpoolSize;
        copied.pushFullWait = pushFullWait;
        copied.localStorage = localStorage;
        copied.addRequestIds = addRequestIds;
        copied.authCallback = authCallback;
        copied.authUrl = authUrl;
        copied.authMethod = authMethod;
        copied.key = key;
        copied.token = token;
        copied.tokenDetails = tokenDetails;
        copied.authHeaders = authHeaders;
        copied.authParams = authParams;
        copied.queryTime = queryTime;
        copied.useTokenAuth = useTokenAuth;
        copied.headers = headers;
        copied.fallbackHosts = fallbackHosts;
        copied.transportParams = transportParams;
        copied.agents = agents;
        return copied;
    }

    /**
     * Internal method
     * <p>
     * clears all auth options
     */
    public void clearAuthOptions() {
        key = null;
        token = null;
        tokenDetails = null;
        authHeaders = null;
        authParams = null;
        queryTime = false;
        useTokenAuth = false;
    }
}
