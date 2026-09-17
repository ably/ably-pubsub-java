package io.ably.pubsub.network;

/**
 * Create WebSocket client bind to the specific URL
 */
public interface WebSocketEngine {
    WebSocketClient create(String url, WebSocketListener listener);
    boolean isPingListenerSupported();
}
