package io.ably.pubsub.network;

import okhttp3.Credentials;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.Map;

public class OkHttpUtils {
    public static void injectProxySetting(ProxyConfig proxyConfig, OkHttpClient.Builder connectionBuilder) {
        if (proxyConfig == null) return;
        connectionBuilder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyConfig.getHost(), proxyConfig.getPort())));
        if (proxyConfig.getUsername() == null || proxyConfig.getAuthType() != ProxyAuthType.BASIC) return;
        String username = proxyConfig.getUsername();
        String password = proxyConfig.getPassword();
        connectionBuilder.proxyAuthenticator((route, response) -> {
            String credential = Credentials.basic(username, password);
            return response.request().newBuilder()
                .header("Proxy-Authorization", credential)
                .build();
        });
    }

    public static Request toOkhttpRequest(HttpRequest request) {
        Request.Builder builder = new Request.Builder()
            .url(request.getUrl());

        RequestBody body = null;

        if (request.getBody() != null) {
            body = RequestBody.create(request.getBody().getContent(), MediaType.parse(request.getBody().getContentType()));
        }

        builder.method(request.getMethod(), body);
        for (Map.Entry<String, List<String>> entry : request.getHeaders().entrySet()) {
            String headerName = entry.getKey();
            List<String> values = entry.getValue();
            for (String headerValue : values) {
                builder.addHeader(headerName, headerValue);
            }
        }

        return builder.build();
    }
}
