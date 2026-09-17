package io.ably.pubsub.http;

import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.util.JavaPlatformAgentProvider;

/**
 * A client that offers a simple stateless API to interact directly with Ably's REST API.
 *
 * This class implements {@link AutoCloseable} so you can use it in
 * try-with-resources constructs and have the JDK close it for you.
 */
public class PubSubHttpClient extends PubSubBase {
    /**
     * Constructs a client object using an Ably API key or token string.
     * <p>
     * Spec: RSC1
     * @param key The Ably API key or token string used to validate the client.
     * @throws AblyException
     * @deprecated use {@code io.ably.pubsub.server.PubSubServer#httpClientBuilder()} from the
     *             {@code io.ably.pubsub:server} artifact instead, which names the side of the
     *             connection your code runs on.
     */
    @Deprecated
    public PubSubHttpClient(String key) throws AblyException {
        super(key, new JavaPlatformAgentProvider());
    }

    /**
     * Construct a client object using an Ably {@link ClientOptions} object.
     * <p>
     * Spec: RSC1
     * @param options A {@link ClientOptions} object to configure the client connection to Ably.
     * @throws AblyException
     * @deprecated use {@code io.ably.pubsub.server.PubSubServer#httpClientBuilder()} from the
     *             {@code io.ably.pubsub:server} artifact instead, which names the side of the
     *             connection your code runs on.
     */
    @Deprecated
    public PubSubHttpClient(ClientOptions options) throws AblyException {
        super(options, new JavaPlatformAgentProvider());
    }
}
