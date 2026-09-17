package io.ably.pubsub.util;

import io.ably.pubsub.network.ProxyAuthType;
import io.ably.pubsub.network.ProxyConfig;
import io.ably.pubsub.types.ClientOptions;

import java.util.Arrays;

public class ClientOptionsUtils {

    public static ProxyConfig convertToProxyConfig(ClientOptions clientOptions) {
        if (clientOptions.proxy == null) return null;

        ProxyConfig.ProxyConfigBuilder builder = ProxyConfig.builder();

        builder
            .host(clientOptions.proxy.host)
            .port(clientOptions.proxy.port)
            .username(clientOptions.proxy.username)
            .password(clientOptions.proxy.password);

        if (clientOptions.proxy.nonProxyHosts != null) {
            builder.nonProxyHosts(Arrays.asList(clientOptions.proxy.nonProxyHosts));
        }

        switch (clientOptions.proxy.prefAuthType) {
            case BASIC:
                builder.authType(ProxyAuthType.BASIC);
                break;
            case DIGEST:
                builder.authType(ProxyAuthType.DIGEST);
                break;
        }

        return builder.build();
    }
}
