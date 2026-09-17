package io.ably.pubsub.http;

import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;

/**
 * The seam through which a {@link PubSubHttpClient} is constructed, now that the client's own
 * constructors are not public.
 * <p>
 * It exists for the {@code io.ably.pubsub:device} and {@code io.ably.pubsub:server} door
 * artifacts, whose builders are the supported way to obtain a client, and for this SDK's own
 * tests. It sits in the client's package so that it can reach the non-public constructor, and it
 * is compiled into each platform artifact from the shared source directory, so that each one
 * constructs its own platform's client.
 * <p>
 * Application code should declare the side it runs on and use that artifact's builder instead:
 * {@code io.ably.pubsub.server.PubSubServer#httpClientBuilder()} on infrastructure you control,
 * or {@code io.ably.pubsub.device.PubSubDevice#clientBuilder()} on an end-user device.
 */
public final class HttpClientFactory {
    private HttpClientFactory() {}

    /**
     * Constructs a client from the given options.
     *
     * @param options the options to configure the client with.
     * @return a new client.
     * @throws AblyException if the options are invalid, for example if they carry no
     *         authentication parameters.
     */
    public static PubSubHttpClient create(ClientOptions options) throws AblyException {
        return new PubSubHttpClient(options);
    }
}
