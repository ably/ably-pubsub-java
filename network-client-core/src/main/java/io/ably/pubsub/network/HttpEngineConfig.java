package io.ably.pubsub.network;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;

@Data
@Setter(AccessLevel.NONE)
@Builder
@AllArgsConstructor
public class HttpEngineConfig {
    private final ProxyConfig proxy;
}
