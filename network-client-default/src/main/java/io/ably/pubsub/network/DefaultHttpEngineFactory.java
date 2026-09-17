package io.ably.pubsub.network;

public class DefaultHttpEngineFactory implements HttpEngineFactory {

    @Override
    public HttpEngine create(HttpEngineConfig config) {
        return new DefaultHttpEngine(config);
    }

    @Override
    public EngineType getEngineType() {
        return EngineType.DEFAULT;
    }
}
