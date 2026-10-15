package io.ably.pubsub.network;

import okhttp3.OkHttpClient;

public class OkHttpEngineFactory implements HttpEngineFactory {
    @Override
    public HttpEngine create(HttpEngineConfig config) {
        OkHttpClient.Builder connectionBuilder = new OkHttpClient.Builder();
        OkHttpUtils.injectProxySetting(config.getProxy(), connectionBuilder);
        return new OkHttpEngine(connectionBuilder.build(), config);
    }

    @Override
    public EngineType getEngineType() {
        return EngineType.OKHTTP;
    }
}
