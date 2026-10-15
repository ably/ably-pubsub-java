package io.ably.pubsub.types;

import io.ably.pubsub.http.HttpAuth;

public class ProxyOptions {
    public String host;
    public int port;
    public String username;
    public String password;
    public String[] nonProxyHosts;
    public HttpAuth.Type prefAuthType = HttpAuth.Type.BASIC;
}
