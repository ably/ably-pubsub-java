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
     * Construct a client object using an Ably {@link ClientOptions} object.
     * <p>
     * Not public: a client is obtained from the builders of the {@code io.ably.pubsub:device} or
     * {@code io.ably.pubsub:server} artifact, which name the side of the connection your code
     * runs on. {@link HttpClientFactory} is the seam those builders construct through.
     * <p>
     * Spec: RSC1
     * @param options A {@link ClientOptions} object to configure the client connection to Ably.
     * @throws AblyException
     */
    protected PubSubHttpClient(ClientOptions options) throws AblyException {
        super(options, new JavaPlatformAgentProvider());
    }
}
