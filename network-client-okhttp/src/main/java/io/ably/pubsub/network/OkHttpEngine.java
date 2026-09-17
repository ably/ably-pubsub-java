package io.ably.pubsub.network;

import okhttp3.Call;
import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

public class OkHttpEngine implements HttpEngine {

    private final OkHttpClient client;
    private final HttpEngineConfig config;

    public OkHttpEngine(OkHttpClient client, HttpEngineConfig config) {
        this.client = client;
        this.config = config;
    }

    @Override
    public HttpCall call(HttpRequest request) {
        Call call = client.newBuilder()
            .connectTimeout(request.getHttpOpenTimeout(), TimeUnit.MILLISECONDS)
            .readTimeout(request.getHttpReadTimeout(), TimeUnit.MILLISECONDS)
            .build()
            .newCall(OkHttpUtils.toOkhttpRequest(request));
        return new OkHttpCall(call);
    }

    @Override
    public boolean isUsingProxy() {
        return config.getProxy() != null;
    }
}
