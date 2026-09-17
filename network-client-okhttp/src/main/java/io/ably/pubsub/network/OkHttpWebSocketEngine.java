package io.ably.pubsub.network;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class OkHttpWebSocketEngine implements WebSocketEngine {
    private final WebSocketEngineConfig config;

    public OkHttpWebSocketEngine(WebSocketEngineConfig config) {
        this.config = config;
    }

    @Override
    public WebSocketClient create(String url, WebSocketListener listener) {
        OkHttpClient.Builder connectionBuilder = new OkHttpClient.Builder();

        Request.Builder requestBuilder = new Request.Builder().url(url);

        OkHttpUtils.injectProxySetting(config.getProxy(), connectionBuilder);

        if (config.getSslSocketFactory() != null) {
            connectionBuilder.sslSocketFactory(config.getSslSocketFactory());
        }

        return new OkHttpWebSocketClient(connectionBuilder.build(), requestBuilder.build(), listener);
    }

    @Override
    public boolean isPingListenerSupported() {
        return false;
    }
}
