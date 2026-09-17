package io.ably.pubsub.network;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;

import java.util.List;

@Data
@Setter(AccessLevel.NONE)
@Builder
@AllArgsConstructor
public class ProxyConfig {
    private String host;
    private int port;
    private String username;
    private String password;
    private List<String> nonProxyHosts;
    private ProxyAuthType authType;
}
