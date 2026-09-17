package io.ably.lib.platform;

import io.ably.lib.transport.NetworkConnectivity;

public class Platform {
    public static final String name = "java";

    public Platform() {}

    public NetworkConnectivity getNetworkConnectivity() {
        return networkConnectivity;
    }

    private final NetworkConnectivity networkConnectivity = new NetworkConnectivity.DefaultNetworkConnectivity();
}
