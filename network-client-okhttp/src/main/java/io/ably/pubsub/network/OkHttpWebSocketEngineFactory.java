package io.ably.pubsub.network;

public class OkHttpWebSocketEngineFactory implements WebSocketEngineFactory {
    @Override
    public WebSocketEngine create(WebSocketEngineConfig config) {
        return new OkHttpWebSocketEngine(config);
    }

    @Override
    public EngineType getEngineType() {
        return EngineType.OKHTTP;
    }
}
