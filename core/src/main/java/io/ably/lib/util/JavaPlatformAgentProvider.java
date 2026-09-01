package io.ably.lib.util;

public class JavaPlatformAgentProvider implements PlatformAgentProvider {
    @Override
    public String createPlatformAgent() {
        String jreVersion = System.getProperty("java.version");
        if (jreVersion == null || jreVersion.trim().isEmpty()) {
            return null;
        } else {
            return "jre" + AgentHeaderCreator.AGENT_DIVIDER + jreVersion.trim();
        }
    }
}
