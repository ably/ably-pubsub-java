package io.ably.pubsub.network;

import java.net.InetSocketAddress;
import java.net.Proxy;

public class DefaultHttpEngine implements HttpEngine {

    private final HttpEngineConfig config;

    public DefaultHttpEngine(HttpEngineConfig config) {
        this.config = config;
    }

    @Override
    public HttpCall call(HttpRequest request) {
        Proxy proxy = isUsingProxy()
                ? new Proxy(Proxy.Type.HTTP, new InetSocketAddress(config.getProxy().getHost(), config.getProxy().getPort()))
                : Proxy.NO_PROXY;
        return new DefaultHttpCall(request, proxy);
    }

    @Override
    public boolean isUsingProxy() {
        return config.getProxy() != null;
    }
}
