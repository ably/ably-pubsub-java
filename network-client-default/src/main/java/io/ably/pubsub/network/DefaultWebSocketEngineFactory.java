package io.ably.pubsub.network;

public class DefaultWebSocketEngineFactory implements WebSocketEngineFactory {

    @Override
    public WebSocketEngine create(WebSocketEngineConfig config) {
        return new DefaultWebSocketEngine(config);
    }

    @Override
    public EngineType getEngineType() {
        return EngineType.DEFAULT;
    }
}
