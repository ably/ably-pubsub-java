package io.ably.pubsub.network;

/**
 * An HTTP engine instance that can make cancelable HTTP requests.
 * It contains some engine-wide configurations, such as proxy settings,
 * if it operates under a corporate proxy.
 */
public interface HttpEngine {
    /**
     * @return cancelable Http request call
     */
    HttpCall call(HttpRequest request);

    /**
     * @return <code>true</code> if it uses proxy, <code>false</code>  otherwise
     */
    boolean isUsingProxy();
}
