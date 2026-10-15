package io.ably.pubsub.util;

import io.ably.pubsub.transport.Defaults;

import java.util.Map;

public class AgentHeaderCreator {
    /**
     * Separates agent entries from each other.
     */
    private static final String AGENT_ENTRY_SEPARATOR = " ";

    /**
     * Separates agent name from agent version.
     */
    public static final String AGENT_DIVIDER = "/";

    public static String create(Map<String, String> additionalAgents, PlatformAgentProvider platformAgentProvider) {
        StringBuilder agentStringBuilder = new StringBuilder();
        agentStringBuilder.append(Defaults.ABLY_AGENT_VERSION);
        if (additionalAgents != null && !additionalAgents.isEmpty()) {
            agentStringBuilder.append(AGENT_ENTRY_SEPARATOR);
            agentStringBuilder.append(getAdditionalAgentEntries(additionalAgents));
        }
        String platformAgent = platformAgentProvider.createPlatformAgent();
        if (platformAgent != null) {
            agentStringBuilder.append(AGENT_ENTRY_SEPARATOR);
            agentStringBuilder.append(platformAgent);
        }

        return agentStringBuilder.toString();
    }

    private static String getAdditionalAgentEntries(Map<String, String> additionalAgents) {
        StringBuilder additionalAgentsBuilder = new StringBuilder();
        for (String additionalAgentName : additionalAgents.keySet()) {
            String additionalAgentVersion = additionalAgents.get(additionalAgentName);
            additionalAgentsBuilder.append(additionalAgentName);
            if (additionalAgentVersion != null) {
                additionalAgentsBuilder.append(AGENT_DIVIDER);
                additionalAgentsBuilder.append(additionalAgentVersion);
            }
            additionalAgentsBuilder.append(AGENT_ENTRY_SEPARATOR);
        }
        return additionalAgentsBuilder.toString().trim();
    }
}
