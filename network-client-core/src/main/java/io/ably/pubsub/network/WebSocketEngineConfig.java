package io.ably.pubsub.network;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;

import javax.net.ssl.SSLSocketFactory;

@Data
@Setter(AccessLevel.NONE)
@Builder
@AllArgsConstructor
public class WebSocketEngineConfig {
    private final ProxyConfig proxy;
    private final boolean tls;
    private final String host;
    private final SSLSocketFactory sslSocketFactory;
}
