package io.ably.pubsub.device

import io.ably.lib.push.Storage
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.rest.Auth
import io.ably.lib.types.ClientOptions
import io.ably.lib.types.Param
import io.ably.lib.types.ProxyOptions
import io.ably.lib.util.Log

/**
 * Entry point for the Ably Pub/Sub SDK for devices: applications running on end-user devices, whose
 * connections are counted on accounts with monthly-active-user billing.
 *
 * Clients built here are the same [AblyRealtime] objects the core SDK has always returned, and
 * behave identically. What the artifact adds is the choice itself: the dependency you declare and
 * the factory you call state which side of the connection your code runs on, rather than leaving it
 * to be inferred.
 *
 * If your code runs on infrastructure you control, use the `io.ably.pubsub:server` artifact instead.
 *
 * There is a single door because there is a single choice to make. Connectionless operations —
 * history, presence queries, token requests, `request()` and `batchPublish()` — are all available on
 * the client this returns.
 *
 * ```
 * val client = PubSubDevice.clientBuilder()
 *     .key("xVLyHw.MHOCLg:...")
 *     .clientId("bob")
 *     .build()
 * ```
 */
public object PubSubDevice {

    /**
     * Creates a builder for a device client. It exposes one method per [ClientOptions] property.
     *
     * @return a new builder.
     */
    @JvmStatic
    public fun clientBuilder(): ClientBuilder = ClientBuilder()

    /**
     * Builds a device client. Obtain one from [PubSubDevice.clientBuilder].
     */
    public class ClientBuilder internal constructor() {

        /** Accumulates the calls made on this builder; handed to the client as-is by [build]. */
        private val options = ClientOptions()

        /**
         * Sets [Auth.AuthOptions.authCallback].
         *
         * @param authCallback the value to set.
         * @return this builder.
         */
        public fun authCallback(authCallback: Auth.TokenCallback): ClientBuilder = apply { options.authCallback = authCallback }

        /**
         * Sets [Auth.AuthOptions.authUrl].
         *
         * @param authUrl the value to set.
         * @return this builder.
         */
        public fun authUrl(authUrl: String): ClientBuilder = apply { options.authUrl = authUrl }

        /**
         * Sets [Auth.AuthOptions.authMethod].
         *
         * @param authMethod the value to set.
         * @return this builder.
         */
        public fun authMethod(authMethod: String): ClientBuilder = apply { options.authMethod = authMethod }

        /**
         * Sets [Auth.AuthOptions.key].
         *
         * @param key the value to set.
         * @return this builder.
         */
        public fun key(key: String): ClientBuilder = apply { options.key = key }

        /**
         * Sets [Auth.AuthOptions.token].
         *
         * @param token the value to set.
         * @return this builder.
         */
        public fun token(token: String): ClientBuilder = apply { options.token = token }

        /**
         * Sets [Auth.AuthOptions.tokenDetails].
         *
         * @param tokenDetails the value to set.
         * @return this builder.
         */
        public fun tokenDetails(tokenDetails: Auth.TokenDetails): ClientBuilder = apply { options.tokenDetails = tokenDetails }

        /**
         * Sets [Auth.AuthOptions.authHeaders].
         *
         * @param authHeaders the value to set.
         * @return this builder.
         */
        public fun authHeaders(authHeaders: Array<Param>): ClientBuilder = apply { options.authHeaders = authHeaders }

        /**
         * Sets [Auth.AuthOptions.authParams].
         *
         * @param authParams the value to set.
         * @return this builder.
         */
        public fun authParams(authParams: Array<Param>): ClientBuilder = apply { options.authParams = authParams }

        /**
         * Sets [Auth.AuthOptions.queryTime].
         *
         * @param queryTime the value to set.
         * @return this builder.
         */
        public fun queryTime(queryTime: Boolean): ClientBuilder = apply { options.queryTime = queryTime }

        /**
         * Sets [Auth.AuthOptions.useTokenAuth].
         *
         * @param useTokenAuth the value to set.
         * @return this builder.
         */
        public fun useTokenAuth(useTokenAuth: Boolean): ClientBuilder = apply { options.useTokenAuth = useTokenAuth }

        /**
         * Sets [ClientOptions.clientId].
         *
         * @param clientId the value to set.
         * @return this builder.
         */
        public fun clientId(clientId: String): ClientBuilder = apply { options.clientId = clientId }

        /**
         * Sets [ClientOptions.logLevel].
         *
         * @param logLevel the value to set.
         * @return this builder.
         */
        public fun logLevel(logLevel: Int): ClientBuilder = apply { options.logLevel = logLevel }

        /**
         * Sets [ClientOptions.logHandler].
         *
         * @param logHandler the value to set.
         * @return this builder.
         */
        public fun logHandler(logHandler: Log.LogHandler): ClientBuilder = apply { options.logHandler = logHandler }

        /**
         * Sets [ClientOptions.tls].
         *
         * @param tls the value to set.
         * @return this builder.
         */
        public fun tls(tls: Boolean): ClientBuilder = apply { options.tls = tls }

        /**
         * Sets [ClientOptions.headers].
         *
         * @param headers the value to set.
         * @return this builder.
         */
        public fun headers(headers: Map<String, String>): ClientBuilder = apply { options.headers = headers }

        /**
         * Sets [ClientOptions.restHost].
         *
         * @param restHost the value to set.
         * @return this builder.
         */
        public fun restHost(restHost: String): ClientBuilder = apply { options.restHost = restHost }

        /**
         * Sets [ClientOptions.port].
         *
         * @param port the value to set.
         * @return this builder.
         */
        public fun port(port: Int): ClientBuilder = apply { options.port = port }

        /**
         * Sets [ClientOptions.tlsPort].
         *
         * @param tlsPort the value to set.
         * @return this builder.
         */
        public fun tlsPort(tlsPort: Int): ClientBuilder = apply { options.tlsPort = tlsPort }

        /**
         * Sets [ClientOptions.useBinaryProtocol].
         *
         * @param useBinaryProtocol the value to set.
         * @return this builder.
         */
        public fun useBinaryProtocol(useBinaryProtocol: Boolean): ClientBuilder = apply { options.useBinaryProtocol = useBinaryProtocol }

        /**
         * Sets [ClientOptions.proxy].
         *
         * @param proxy the value to set.
         * @return this builder.
         */
        public fun proxy(proxy: ProxyOptions): ClientBuilder = apply { options.proxy = proxy }

        /**
         * Sets [ClientOptions.environment].
         *
         * @param environment the value to set.
         * @return this builder.
         */
        public fun environment(environment: String): ClientBuilder = apply { options.environment = environment }

        /**
         * Sets [ClientOptions.idempotentRestPublishing].
         *
         * @param idempotentRestPublishing the value to set.
         * @return this builder.
         */
        public fun idempotentRestPublishing(idempotentRestPublishing: Boolean): ClientBuilder = apply { options.idempotentRestPublishing = idempotentRestPublishing }

        /**
         * Sets [ClientOptions.httpOpenTimeout].
         *
         * @param httpOpenTimeout the value to set.
         * @return this builder.
         */
        public fun httpOpenTimeout(httpOpenTimeout: Int): ClientBuilder = apply { options.httpOpenTimeout = httpOpenTimeout }

        /**
         * Sets [ClientOptions.httpRequestTimeout].
         *
         * @param httpRequestTimeout the value to set.
         * @return this builder.
         */
        public fun httpRequestTimeout(httpRequestTimeout: Int): ClientBuilder = apply { options.httpRequestTimeout = httpRequestTimeout }

        /**
         * Sets [ClientOptions.httpMaxRetryDuration].
         *
         * @param httpMaxRetryDuration the value to set.
         * @return this builder.
         */
        public fun httpMaxRetryDuration(httpMaxRetryDuration: Int): ClientBuilder = apply { options.httpMaxRetryDuration = httpMaxRetryDuration }

        /**
         * Sets [ClientOptions.httpMaxRetryCount].
         *
         * @param httpMaxRetryCount the value to set.
         * @return this builder.
         */
        public fun httpMaxRetryCount(httpMaxRetryCount: Int): ClientBuilder = apply { options.httpMaxRetryCount = httpMaxRetryCount }

        /**
         * Sets [ClientOptions.fallbackHosts].
         *
         * @param fallbackHosts the value to set.
         * @return this builder.
         */
        public fun fallbackHosts(fallbackHosts: Array<String>): ClientBuilder = apply { options.fallbackHosts = fallbackHosts }

        /**
         * Sets [ClientOptions.fallbackHostsUseDefault].
         *
         * @param fallbackHostsUseDefault the value to set.
         * @return this builder.
         */
        @Deprecated("Deprecated on ClientOptions itself; use fallbackHosts to supply custom hosts.")
        @Suppress("DEPRECATION")
        public fun fallbackHostsUseDefault(fallbackHostsUseDefault: Boolean): ClientBuilder = apply { options.fallbackHostsUseDefault = fallbackHostsUseDefault }

        /**
         * Sets [ClientOptions.fallbackRetryTimeout].
         *
         * @param fallbackRetryTimeout the value to set.
         * @return this builder.
         */
        public fun fallbackRetryTimeout(fallbackRetryTimeout: Long): ClientBuilder = apply { options.fallbackRetryTimeout = fallbackRetryTimeout }

        /**
         * Sets [ClientOptions.defaultTokenParams].
         *
         * @param defaultTokenParams the value to set.
         * @return this builder.
         */
        public fun defaultTokenParams(defaultTokenParams: Auth.TokenParams): ClientBuilder = apply { options.defaultTokenParams = defaultTokenParams }

        /**
         * Sets [ClientOptions.asyncHttpThreadpoolSize].
         *
         * @param asyncHttpThreadpoolSize the value to set.
         * @return this builder.
         */
        public fun asyncHttpThreadpoolSize(asyncHttpThreadpoolSize: Int): ClientBuilder = apply { options.asyncHttpThreadpoolSize = asyncHttpThreadpoolSize }

        /**
         * Sets [ClientOptions.pushFullWait].
         *
         * @param pushFullWait the value to set.
         * @return this builder.
         */
        public fun pushFullWait(pushFullWait: Boolean): ClientBuilder = apply { options.pushFullWait = pushFullWait }

        /**
         * Sets [ClientOptions.localStorage].
         *
         * @param localStorage the value to set.
         * @return this builder.
         */
        public fun localStorage(localStorage: Storage): ClientBuilder = apply { options.localStorage = localStorage }

        /**
         * Sets [ClientOptions.addRequestIds].
         *
         * @param addRequestIds the value to set.
         * @return this builder.
         */
        public fun addRequestIds(addRequestIds: Boolean): ClientBuilder = apply { options.addRequestIds = addRequestIds }

        /**
         * Sets [ClientOptions.agents].
         *
         * @param agents the value to set.
         * @return this builder.
         */
        public fun agents(agents: Map<String, String>): ClientBuilder = apply { options.agents = agents }

        /**
         * Sets [ClientOptions.realtimeHost].
         *
         * @param realtimeHost the value to set.
         * @return this builder.
         */
        public fun realtimeHost(realtimeHost: String): ClientBuilder = apply { options.realtimeHost = realtimeHost }

        /**
         * Sets [ClientOptions.autoConnect].
         *
         * @param autoConnect the value to set.
         * @return this builder.
         */
        public fun autoConnect(autoConnect: Boolean): ClientBuilder = apply { options.autoConnect = autoConnect }

        /**
         * Sets [ClientOptions.queueMessages].
         *
         * @param queueMessages the value to set.
         * @return this builder.
         */
        public fun queueMessages(queueMessages: Boolean): ClientBuilder = apply { options.queueMessages = queueMessages }

        /**
         * Sets [ClientOptions.echoMessages].
         *
         * @param echoMessages the value to set.
         * @return this builder.
         */
        public fun echoMessages(echoMessages: Boolean): ClientBuilder = apply { options.echoMessages = echoMessages }

        /**
         * Sets [ClientOptions.recover].
         *
         * @param recover the value to set.
         * @return this builder.
         */
        public fun recover(recover: String): ClientBuilder = apply { options.recover = recover }

        /**
         * Sets [ClientOptions.realtimeRequestTimeout].
         *
         * @param realtimeRequestTimeout the value to set.
         * @return this builder.
         */
        public fun realtimeRequestTimeout(realtimeRequestTimeout: Long): ClientBuilder = apply { options.realtimeRequestTimeout = realtimeRequestTimeout }

        /**
         * Sets [ClientOptions.disconnectedRetryTimeout].
         *
         * @param disconnectedRetryTimeout the value to set.
         * @return this builder.
         */
        public fun disconnectedRetryTimeout(disconnectedRetryTimeout: Long): ClientBuilder = apply { options.disconnectedRetryTimeout = disconnectedRetryTimeout }

        /**
         * Sets [ClientOptions.suspendedRetryTimeout].
         *
         * @param suspendedRetryTimeout the value to set.
         * @return this builder.
         */
        public fun suspendedRetryTimeout(suspendedRetryTimeout: Long): ClientBuilder = apply { options.suspendedRetryTimeout = suspendedRetryTimeout }

        /**
         * Sets [ClientOptions.channelRetryTimeout].
         *
         * @param channelRetryTimeout the value to set.
         * @return this builder.
         */
        public fun channelRetryTimeout(channelRetryTimeout: Int): ClientBuilder = apply { options.channelRetryTimeout = channelRetryTimeout }

        /**
         * Sets [ClientOptions.transportParams].
         *
         * @param transportParams the value to set.
         * @return this builder.
         */
        public fun transportParams(transportParams: Array<Param>): ClientBuilder = apply { options.transportParams = transportParams }

        /**
         * Builds the client, which connects immediately unless [autoConnect] was set to false.
         *
         * @return an [AblyRealtime].
         * @throws io.ably.lib.types.AblyException if the options are invalid, for example if no
         *         authentication parameters were supplied.
         */
        @Suppress("DEPRECATION") // this factory is the replacement for that constructor
        public fun build(): AblyRealtime = AblyRealtime(options)
    }
}
