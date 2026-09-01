package io.ably.pubsub.device;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.pubsub.internal.Side;

/**
 * The door into Ably Pub/Sub for devices: Android apps and other end-user runtimes.
 * <p>
 * Clients built here declare themselves device-side to Ably: every connection and request
 * they make carries the {@code ably-pubsub-device} agent entry, which is how the platform
 * classifies the traffic (on MAU-priced accounts, device traffic is what is counted). The
 * side is the package's to declare — a caller-supplied agent entry cannot override it.
 * <p>
 * There is one door: a device holds one live client. Connectionless operations (history,
 * presence reads, token requests) are all available on it.
 * <p>
 * This builder is the only recommended entry point of this artifact; the classes it
 * constructs come from {@code io.ably.pubsub:core-android}, which is an internal
 * implementation artifact not intended for direct use.
 */
public final class PubSubDevice {
    private PubSubDevice() {}

    /**
     * Returns a builder for the device's client.
     *
     * @param options a {@link ClientOptions} object to configure the client.
     * @return the builder.
     */
    public static ClientBuilder clientBuilder(ClientOptions options) {
        return new ClientBuilder(options, null);
    }

    /**
     * Returns a builder for the device's client.
     *
     * @param keyOrToken an Ably API key or token string.
     * @return the builder.
     */
    public static ClientBuilder clientBuilder(String keyOrToken) {
        return new ClientBuilder(null, keyOrToken);
    }

    /**
     * Builds the device client. Accepts everything the core constructor accepts.
     */
    public static final class ClientBuilder {
        private final ClientOptions options;
        private final String keyOrToken;

        private ClientBuilder(ClientOptions options, String keyOrToken) {
            this.options = options;
            this.keyOrToken = keyOrToken;
        }

        /**
         * Constructs the client, declaring the device side on it.
         *
         * @return the client.
         * @throws AblyException if the options, key or token are rejected.
         */
        public AblyRealtime build() throws AblyException {
            final ClientOptions stamped;
            if (keyOrToken != null) {
                stamped = Side.optionsWithSideAgent(keyOrToken, Side.DEVICE_AGENT_IDENTIFIER, BuildConfig.VERSION);
            } else {
                stamped = Side.optionsWithSideAgent(options, Side.DEVICE_AGENT_IDENTIFIER, BuildConfig.VERSION);
            }
            return new AblyRealtime(stamped);
        }
    }
}
