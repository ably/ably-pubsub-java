package io.ably.lib.util;

import android.os.Build;

public class AndroidPlatformAgentProvider implements PlatformAgentProvider {
    @Override
    public String createPlatformAgent() {
        return "android" + AgentHeaderCreator.AGENT_DIVIDER + Build.VERSION.SDK_INT;
    }
}
