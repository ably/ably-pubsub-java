package io.ably.pubsub.server;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.pubsub.internal.Side;

/**
 * The door into Ably Pub/Sub for servers and other trusted backend environments.
 * <p>
 * Clients built here declare themselves server-side to Ably: every connection and request
 * they make carries the {@code ably-pubsub-server} agent entry, which is how the platform
 * classifies the traffic (and, on MAU-priced accounts using API-key auth, how it earns the
 * server exemption). The side is the package's to declare — a caller-supplied agent entry
 * cannot override it.
 * <p>
 * These builders are the only recommended entry points of this artifact; the classes they
 * construct come from {@code io.ably.pubsub:core}, which is an internal implementation
 * artifact not intended for direct use.
 */
public final class PubSubServer {
    private PubSubServer() {}

    /**
     * Returns a builder for a stateless client that interacts with Ably over HTTP.
     *
     * @param options a {@link ClientOptions} object to configure the client.
     * @return the builder.
     */
    public static HttpClientBuilder httpClientBuilder(ClientOptions options) {
        return new HttpClientBuilder(options, null);
    }

    /**
     * Returns a builder for a stateless client that interacts with Ably over HTTP.
     *
     * @param keyOrToken an Ably API key or token string.
     * @return the builder.
     */
    public static HttpClientBuilder httpClientBuilder(String keyOrToken) {
        return new HttpClientBuilder(null, keyOrToken);
    }

    /**
     * Returns a builder for a stateful client that maintains a live connection to Ably.
     *
     * @param options a {@link ClientOptions} object to configure the client.
     * @return the builder.
     */
    public static RealtimeClientBuilder realtimeClientBuilder(ClientOptions options) {
        return new RealtimeClientBuilder(options, null);
    }

    /**
     * Returns a builder for a stateful client that maintains a live connection to Ably.
     *
     * @param keyOrToken an Ably API key or token string.
     * @return the builder.
     */
    public static RealtimeClientBuilder realtimeClientBuilder(String keyOrToken) {
        return new RealtimeClientBuilder(null, keyOrToken);
    }

    /**
     * Resolves the caller's input exactly as the core constructors would, then stamps the
     * server-side agent entry. Resolution happens at {@code build()} time so the caller's
     * input is read once, when the client is constructed.
     */
    private static ClientOptions stampedOptions(ClientOptions options, String keyOrToken) throws AblyException {
        if (keyOrToken != null) {
            return Side.optionsWithSideAgent(keyOrToken, Side.SERVER_AGENT_IDENTIFIER, BuildConfig.VERSION);
        }
        return Side.optionsWithSideAgent(options, Side.SERVER_AGENT_IDENTIFIER, BuildConfig.VERSION);
    }

    /**
     * Builds the HTTP (REST) client. Accepts everything the core constructor accepts.
     */
    public static final class HttpClientBuilder {
        private final ClientOptions options;
        private final String keyOrToken;

        private HttpClientBuilder(ClientOptions options, String keyOrToken) {
            this.options = options;
            this.keyOrToken = keyOrToken;
        }

        /**
         * Constructs the client, declaring the server side on it.
         *
         * @return the client.
         * @throws AblyException if the options, key or token are rejected.
         */
        public AblyRest build() throws AblyException {
            return new AblyRest(stampedOptions(options, keyOrToken));
        }
    }

    /**
     * Builds the realtime client. Accepts everything the core constructor accepts.
     */
    public static final class RealtimeClientBuilder {
        private final ClientOptions options;
        private final String keyOrToken;

        private RealtimeClientBuilder(ClientOptions options, String keyOrToken) {
            this.options = options;
            this.keyOrToken = keyOrToken;
        }

        /**
         * Constructs the client, declaring the server side on it.
         *
         * @return the client.
         * @throws AblyException if the options, key or token are rejected.
         */
        public AblyRealtime build() throws AblyException {
            return new AblyRealtime(stampedOptions(options, keyOrToken));
        }
    }
}
