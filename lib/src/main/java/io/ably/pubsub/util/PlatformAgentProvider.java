package io.ably.pubsub.util;

public interface PlatformAgentProvider {
    /**
     * Creates the platform agent for agent header {@link AgentHeaderCreator}.
     *
     * @return Platform agent string or null if not available.
     */
    String createPlatformAgent();
}
