package io.ably.pubsub.network;

import java.net.URI;

public class DefaultWebSocketEngine implements WebSocketEngine {
    private final WebSocketEngineConfig config;

    public DefaultWebSocketEngine(WebSocketEngineConfig config) {
        this.config = config;
    }

    @Override
    public WebSocketClient create(String url, WebSocketListener listener) {
        DefaultWebSocketClient client =  new DefaultWebSocketClient(URI.create(url), listener, config);
        if (config.isTls()) {
            client.setSocketFactory(config.getSslSocketFactory());
        }
        return client;
    }

    @Override
    public boolean isPingListenerSupported() {
        return true;
    }
}
