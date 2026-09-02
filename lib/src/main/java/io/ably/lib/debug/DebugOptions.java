package io.ably.lib.debug;

import java.util.List;
import java.util.Map;

import io.ably.lib.http.HttpCore;
import io.ably.lib.network.HttpEngine;
import io.ably.lib.network.HttpRequest;
import io.ably.lib.network.WebSocketEngineFactory;
import io.ably.lib.transport.ITransport;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.util.Clock;

public class DebugOptions extends ClientOptions {
    public interface RawProtocolListener {
        void onRawConnectRequested(String url);
        void onRawConnect(String url);
        void onRawMessageSend(ProtocolMessage message);
        void onRawMessageRecv(ProtocolMessage message);
    }

    public interface RawHttpListener {
        HttpCore.Response onRawHttpRequest(String id, HttpRequest request, String authHeader, Map<String, List<String>> requestHeaders, HttpCore.RequestBody requestBody);
        void onRawHttpResponse(String id, String method, HttpCore.Response response);
        void onRawHttpException(String id, String method, Throwable t);
    }

    public DebugOptions() { super(); pushFullWait = true; }

    public DebugOptions(String key) throws AblyException { super(key); pushFullWait = true; }

    public RawProtocolListener protocolListener;
    public RawHttpListener httpListener;
    public ITransport.Factory transportFactory;
    public HttpEngine httpEngine;
    public WebSocketEngineFactory webSocketEngineFactory;
    public Clock clock;

    public DebugOptions copy() {
        DebugOptions copied = new DebugOptions();
        copied.protocolListener = protocolListener;
        copied.httpListener = httpListener;
        copied.transportFactory = transportFactory;
        copied.httpEngine = httpEngine;
        copied.webSocketEngineFactory = webSocketEngineFactory;
        copied.clock = clock;
        copied.clientId = clientId;
        copied.logLevel = logLevel;
        copied.logHandler = logHandler;
        copied.tls = tls;
        copied.restHost = restHost;
        copied.realtimeHost = realtimeHost;
        copied.port = port;
        copied.tlsPort = tlsPort;
        copied.autoConnect = autoConnect;
        copied.useBinaryProtocol = useBinaryProtocol;
        copied.queueMessages = queueMessages;
        copied.echoMessages = echoMessages;
        copied.recover = recover;
        copied.proxy = proxy;
        copied.environment = environment;
        copied.idempotentRestPublishing = idempotentRestPublishing;
        copied.httpOpenTimeout = httpOpenTimeout;
        copied.httpRequestTimeout = httpRequestTimeout;
        copied.httpMaxRetryDuration = httpMaxRetryDuration;
        copied.httpMaxRetryCount = httpMaxRetryCount;
        copied.realtimeRequestTimeout = realtimeRequestTimeout;
        copied.disconnectedRetryTimeout = disconnectedRetryTimeout;
        copied.suspendedRetryTimeout = suspendedRetryTimeout;
        copied.fallbackHostsUseDefault = fallbackHostsUseDefault;
        copied.fallbackRetryTimeout = fallbackRetryTimeout;
        copied.defaultTokenParams = defaultTokenParams;
        copied.channelRetryTimeout = channelRetryTimeout;
        copied.asyncHttpThreadpoolSize = asyncHttpThreadpoolSize;
        copied.pushFullWait = pushFullWait;
        copied.localStorage = localStorage;
        copied.addRequestIds = addRequestIds;
        copied.authCallback = authCallback;
        copied.authUrl = authUrl;
        copied.authMethod = authMethod;
        copied.key = key;
        copied.token = token;
        copied.tokenDetails = tokenDetails;
        copied.authHeaders = authHeaders;
        copied.authParams = authParams;
        copied.queryTime = queryTime;
        copied.useTokenAuth = useTokenAuth;
        copied.headers = headers;
        copied.fallbackHosts = fallbackHosts;
        copied.transportParams = transportParams;
        copied.agents = agents;
        return copied;
    }
}
