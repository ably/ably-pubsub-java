package io.ably.pubsub.server;

import io.ably.lib.push.Storage;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.Param;
import io.ably.lib.types.ProxyOptions;
import io.ably.lib.util.Log.LogHandler;

import java.util.Map;

/**
 * Entry point for the Ably Pub/Sub SDK for servers: applications running on infrastructure you
 * control, whose traffic is exempt from monthly-active-user billing.
 * <p>
 * Clients built here are the same {@link AblyRest} and {@link AblyRealtime} objects the core SDK
 * has always returned, and behave identically. What the artifact adds is the choice itself: the
 * dependency you declare and the factory you call state which side of the connection your code runs
 * on, rather than leaving it to be inferred.
 * <p>
 * If your code runs on an end-user device, use the {@code io.ably.pubsub:device} artifact instead.
 *
 * <pre>{@code
 * AblyRest http = PubSubServer.httpClientBuilder()
 *     .key("xVLyHw.MHOCLg:...")
 *     .build();
 *
 * AblyRealtime realtime = PubSubServer.realtimeClientBuilder()
 *     .key("xVLyHw.MHOCLg:...")
 *     .echoMessages(false)
 *     .build();
 * }</pre>
 */
public final class PubSubServer {

    private PubSubServer() {
    }

    /**
     * Creates a builder for a stateless HTTP client, which talks to Ably over plain HTTP requests
     * without holding a connection open.
     * <p>
     * This is the right choice for most server-side work: publishing, reading history, querying
     * presence, issuing tokens and push administration.
     *
     * @return a new builder.
     */
    public static HttpClientBuilder httpClientBuilder() {
        return new HttpClientBuilder();
    }

    /**
     * Creates a builder for a realtime client, which holds a persistent connection to Ably and can
     * subscribe to messages and presence as they happen.
     * <p>
     * Choose this over {@link #httpClientBuilder()} only when the server needs to receive messages
     * live, rather than only send them.
     *
     * @return a new builder.
     */
    public static RealtimeClientBuilder realtimeClientBuilder() {
        return new RealtimeClientBuilder();
    }

    /**
     * The options common to both server-side clients. One method per {@link ClientOptions}
     * property that can affect a client of either kind; {@link RealtimeClientBuilder} adds the
     * properties that only mean something for a persistent connection.
     *
     * @param <T> the concrete builder type, so that chaining preserves it.
     */
    public abstract static class ClientBuilder<T extends ClientBuilder<T>> {

        /** Accumulates the calls made on this builder; handed to the client as-is by build(). */
        final ClientOptions options = new ClientOptions();

        ClientBuilder() {
        }

        @SuppressWarnings("unchecked")
        private T self() {
            return (T) this;
        }

        /**
         * Sets {@link Auth.AuthOptions#authCallback}.
         *
         * @param authCallback the value to set.
         * @return this builder.
         */
        public T authCallback(Auth.TokenCallback authCallback) {
            options.authCallback = authCallback;
            return self();
        }

        /**
         * Sets {@link Auth.AuthOptions#authUrl}.
         *
         * @param authUrl the value to set.
         * @return this builder.
         */
        public T authUrl(String authUrl) {
            options.authUrl = authUrl;
            return self();
        }

        /**
         * Sets {@link Auth.AuthOptions#authMethod}.
         *
         * @param authMethod the value to set.
         * @return this builder.
         */
        public T authMethod(String authMethod) {
            options.authMethod = authMethod;
            return self();
        }

        /**
         * Sets {@link Auth.AuthOptions#key}.
         *
         * @param key the value to set.
         * @return this builder.
         */
        public T key(String key) {
            options.key = key;
            return self();
        }

        /**
         * Sets {@link Auth.AuthOptions#token}.
         *
         * @param token the value to set.
         * @return this builder.
         */
        public T token(String token) {
            options.token = token;
            return self();
        }

        /**
         * Sets {@link Auth.AuthOptions#tokenDetails}.
         *
         * @param tokenDetails the value to set.
         * @return this builder.
         */
        public T tokenDetails(Auth.TokenDetails tokenDetails) {
            options.tokenDetails = tokenDetails;
            return self();
        }

        /**
         * Sets {@link Auth.AuthOptions#authHeaders}.
         *
         * @param authHeaders the value to set.
         * @return this builder.
         */
        public T authHeaders(Param[] authHeaders) {
            options.authHeaders = authHeaders;
            return self();
        }

        /**
         * Sets {@link Auth.AuthOptions#authParams}.
         *
         * @param authParams the value to set.
         * @return this builder.
         */
        public T authParams(Param[] authParams) {
            options.authParams = authParams;
            return self();
        }

        /**
         * Sets {@link Auth.AuthOptions#queryTime}.
         *
         * @param queryTime the value to set.
         * @return this builder.
         */
        public T queryTime(boolean queryTime) {
            options.queryTime = queryTime;
            return self();
        }

        /**
         * Sets {@link Auth.AuthOptions#useTokenAuth}.
         *
         * @param useTokenAuth the value to set.
         * @return this builder.
         */
        public T useTokenAuth(boolean useTokenAuth) {
            options.useTokenAuth = useTokenAuth;
            return self();
        }

        /**
         * Sets {@link ClientOptions#clientId}.
         *
         * @param clientId the value to set.
         * @return this builder.
         */
        public T clientId(String clientId) {
            options.clientId = clientId;
            return self();
        }

        /**
         * Sets {@link ClientOptions#logLevel}.
         *
         * @param logLevel the value to set.
         * @return this builder.
         */
        public T logLevel(int logLevel) {
            options.logLevel = logLevel;
            return self();
        }

        /**
         * Sets {@link ClientOptions#logHandler}.
         *
         * @param logHandler the value to set.
         * @return this builder.
         */
        public T logHandler(LogHandler logHandler) {
            options.logHandler = logHandler;
            return self();
        }

        /**
         * Sets {@link ClientOptions#tls}.
         *
         * @param tls the value to set.
         * @return this builder.
         */
        public T tls(boolean tls) {
            options.tls = tls;
            return self();
        }

        /**
         * Sets {@link ClientOptions#headers}.
         *
         * @param headers the value to set.
         * @return this builder.
         */
        public T headers(Map<String, String> headers) {
            options.headers = headers;
            return self();
        }

        /**
         * Sets {@link ClientOptions#restHost}.
         *
         * @param restHost the value to set.
         * @return this builder.
         */
        public T restHost(String restHost) {
            options.restHost = restHost;
            return self();
        }

        /**
         * Sets {@link ClientOptions#port}.
         *
         * @param port the value to set.
         * @return this builder.
         */
        public T port(int port) {
            options.port = port;
            return self();
        }

        /**
         * Sets {@link ClientOptions#tlsPort}.
         *
         * @param tlsPort the value to set.
         * @return this builder.
         */
        public T tlsPort(int tlsPort) {
            options.tlsPort = tlsPort;
            return self();
        }

        /**
         * Sets {@link ClientOptions#useBinaryProtocol}.
         *
         * @param useBinaryProtocol the value to set.
         * @return this builder.
         */
        public T useBinaryProtocol(boolean useBinaryProtocol) {
            options.useBinaryProtocol = useBinaryProtocol;
            return self();
        }

        /**
         * Sets {@link ClientOptions#proxy}.
         *
         * @param proxy the value to set.
         * @return this builder.
         */
        public T proxy(ProxyOptions proxy) {
            options.proxy = proxy;
            return self();
        }

        /**
         * Sets {@link ClientOptions#environment}.
         *
         * @param environment the value to set.
         * @return this builder.
         */
        public T environment(String environment) {
            options.environment = environment;
            return self();
        }

        /**
         * Sets {@link ClientOptions#idempotentRestPublishing}.
         *
         * @param idempotentRestPublishing the value to set.
         * @return this builder.
         */
        public T idempotentRestPublishing(boolean idempotentRestPublishing) {
            options.idempotentRestPublishing = idempotentRestPublishing;
            return self();
        }

        /**
         * Sets {@link ClientOptions#httpOpenTimeout}.
         *
         * @param httpOpenTimeout the value to set.
         * @return this builder.
         */
        public T httpOpenTimeout(int httpOpenTimeout) {
            options.httpOpenTimeout = httpOpenTimeout;
            return self();
        }

        /**
         * Sets {@link ClientOptions#httpRequestTimeout}.
         *
         * @param httpRequestTimeout the value to set.
         * @return this builder.
         */
        public T httpRequestTimeout(int httpRequestTimeout) {
            options.httpRequestTimeout = httpRequestTimeout;
            return self();
        }

        /**
         * Sets {@link ClientOptions#httpMaxRetryDuration}.
         *
         * @param httpMaxRetryDuration the value to set.
         * @return this builder.
         */
        public T httpMaxRetryDuration(int httpMaxRetryDuration) {
            options.httpMaxRetryDuration = httpMaxRetryDuration;
            return self();
        }

        /**
         * Sets {@link ClientOptions#httpMaxRetryCount}.
         *
         * @param httpMaxRetryCount the value to set.
         * @return this builder.
         */
        public T httpMaxRetryCount(int httpMaxRetryCount) {
            options.httpMaxRetryCount = httpMaxRetryCount;
            return self();
        }

        /**
         * Sets {@link ClientOptions#fallbackHosts}.
         *
         * @param fallbackHosts the value to set.
         * @return this builder.
         */
        public T fallbackHosts(String[] fallbackHosts) {
            options.fallbackHosts = fallbackHosts;
            return self();
        }

        /**
         * Sets {@link ClientOptions#fallbackHostsUseDefault}.
         *
         * @param fallbackHostsUseDefault the value to set.
         * @return this builder.
         * @deprecated deprecated on {@link ClientOptions} itself; use
         *             {@link #fallbackHosts(String[])} to supply custom hosts.
         */
        @Deprecated
        public T fallbackHostsUseDefault(boolean fallbackHostsUseDefault) {
            options.fallbackHostsUseDefault = fallbackHostsUseDefault;
            return self();
        }

        /**
         * Sets {@link ClientOptions#fallbackRetryTimeout}.
         *
         * @param fallbackRetryTimeout the value to set.
         * @return this builder.
         */
        public T fallbackRetryTimeout(long fallbackRetryTimeout) {
            options.fallbackRetryTimeout = fallbackRetryTimeout;
            return self();
        }

        /**
         * Sets {@link ClientOptions#defaultTokenParams}.
         *
         * @param defaultTokenParams the value to set.
         * @return this builder.
         */
        public T defaultTokenParams(Auth.TokenParams defaultTokenParams) {
            options.defaultTokenParams = defaultTokenParams;
            return self();
        }

        /**
         * Sets {@link ClientOptions#asyncHttpThreadpoolSize}.
         *
         * @param asyncHttpThreadpoolSize the value to set.
         * @return this builder.
         */
        public T asyncHttpThreadpoolSize(int asyncHttpThreadpoolSize) {
            options.asyncHttpThreadpoolSize = asyncHttpThreadpoolSize;
            return self();
        }

        /**
         * Sets {@link ClientOptions#pushFullWait}.
         *
         * @param pushFullWait the value to set.
         * @return this builder.
         */
        public T pushFullWait(boolean pushFullWait) {
            options.pushFullWait = pushFullWait;
            return self();
        }

        /**
         * Sets {@link ClientOptions#localStorage}.
         *
         * @param localStorage the value to set.
         * @return this builder.
         */
        public T localStorage(Storage localStorage) {
            options.localStorage = localStorage;
            return self();
        }

        /**
         * Sets {@link ClientOptions#addRequestIds}.
         *
         * @param addRequestIds the value to set.
         * @return this builder.
         */
        public T addRequestIds(boolean addRequestIds) {
            options.addRequestIds = addRequestIds;
            return self();
        }

        /**
         * Sets {@link ClientOptions#agents}.
         *
         * @param agents the value to set.
         * @return this builder.
         */
        public T agents(Map<String, String> agents) {
            options.agents = agents;
            return self();
        }
    }

    /**
     * Builds a stateless HTTP client. Obtain one from {@link PubSubServer#httpClientBuilder()}.
     * <p>
     * Deliberately does not expose the realtime-only options, since an {@link AblyRest} never
     * opens a connection for them to apply to.
     */
    public static final class HttpClientBuilder extends ClientBuilder<HttpClientBuilder> {

        HttpClientBuilder() {
        }

        /**
         * Builds the client.
         *
         * @return an {@link AblyRest}.
         * @throws AblyException if the options are invalid, for example if no authentication
         *                       parameters were supplied.
         */
        @SuppressWarnings("deprecation") // this factory is the replacement for that constructor
        public AblyRest build() throws AblyException {
            return new AblyRest(options);
        }
    }

    /**
     * Builds a realtime client. Obtain one from {@link PubSubServer#realtimeClientBuilder()}.
     */
    public static final class RealtimeClientBuilder extends ClientBuilder<RealtimeClientBuilder> {

        RealtimeClientBuilder() {
        }

        /**
         * Sets {@link ClientOptions#realtimeHost}.
         *
         * @param realtimeHost the value to set.
         * @return this builder.
         */
        public RealtimeClientBuilder realtimeHost(String realtimeHost) {
            options.realtimeHost = realtimeHost;
            return this;
        }

        /**
         * Sets {@link ClientOptions#autoConnect}.
         *
         * @param autoConnect the value to set.
         * @return this builder.
         */
        public RealtimeClientBuilder autoConnect(boolean autoConnect) {
            options.autoConnect = autoConnect;
            return this;
        }

        /**
         * Sets {@link ClientOptions#queueMessages}.
         *
         * @param queueMessages the value to set.
         * @return this builder.
         */
        public RealtimeClientBuilder queueMessages(boolean queueMessages) {
            options.queueMessages = queueMessages;
            return this;
        }

        /**
         * Sets {@link ClientOptions#echoMessages}.
         *
         * @param echoMessages the value to set.
         * @return this builder.
         */
        public RealtimeClientBuilder echoMessages(boolean echoMessages) {
            options.echoMessages = echoMessages;
            return this;
        }

        /**
         * Sets {@link ClientOptions#recover}.
         *
         * @param recover the value to set.
         * @return this builder.
         */
        public RealtimeClientBuilder recover(String recover) {
            options.recover = recover;
            return this;
        }

        /**
         * Sets {@link ClientOptions#realtimeRequestTimeout}.
         *
         * @param realtimeRequestTimeout the value to set.
         * @return this builder.
         */
        public RealtimeClientBuilder realtimeRequestTimeout(long realtimeRequestTimeout) {
            options.realtimeRequestTimeout = realtimeRequestTimeout;
            return this;
        }

        /**
         * Sets {@link ClientOptions#disconnectedRetryTimeout}.
         *
         * @param disconnectedRetryTimeout the value to set.
         * @return this builder.
         */
        public RealtimeClientBuilder disconnectedRetryTimeout(long disconnectedRetryTimeout) {
            options.disconnectedRetryTimeout = disconnectedRetryTimeout;
            return this;
        }

        /**
         * Sets {@link ClientOptions#suspendedRetryTimeout}.
         *
         * @param suspendedRetryTimeout the value to set.
         * @return this builder.
         */
        public RealtimeClientBuilder suspendedRetryTimeout(long suspendedRetryTimeout) {
            options.suspendedRetryTimeout = suspendedRetryTimeout;
            return this;
        }

        /**
         * Sets {@link ClientOptions#channelRetryTimeout}.
         *
         * @param channelRetryTimeout the value to set.
         * @return this builder.
         */
        public RealtimeClientBuilder channelRetryTimeout(int channelRetryTimeout) {
            options.channelRetryTimeout = channelRetryTimeout;
            return this;
        }

        /**
         * Sets {@link ClientOptions#transportParams}.
         *
         * @param transportParams the value to set.
         * @return this builder.
         */
        public RealtimeClientBuilder transportParams(Param[] transportParams) {
            options.transportParams = transportParams;
            return this;
        }

        /**
         * Builds the client, which connects immediately unless {@link #autoConnect(boolean)} was
         * set to false.
         *
         * @return an {@link AblyRealtime}.
         * @throws AblyException if the options are invalid, for example if no authentication
         *                       parameters were supplied.
         */
        @SuppressWarnings("deprecation") // this factory is the replacement for that constructor
        public AblyRealtime build() throws AblyException {
            return new AblyRealtime(options);
        }
    }
}
