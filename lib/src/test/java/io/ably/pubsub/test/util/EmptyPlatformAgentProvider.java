package io.ably.pubsub.test.util;

import io.ably.pubsub.util.PlatformAgentProvider;

public class EmptyPlatformAgentProvider implements PlatformAgentProvider {
    @Override
    public String createPlatformAgent() {
        return null;
    }
}
