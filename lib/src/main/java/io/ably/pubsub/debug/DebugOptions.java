package io.ably.pubsub.debug;

import java.util.List;
import java.util.Map;

import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.network.HttpEngine;
import io.ably.pubsub.network.HttpRequest;
import io.ably.pubsub.network.WebSocketEngineFactory;
import io.ably.pubsub.transport.ITransport;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.ProtocolMessage;
import io.ably.pubsub.util.Clock;

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

    @Override
    public DebugOptions copy() {
        DebugOptions copied = new DebugOptions();
        copyInto(copied);
        copied.protocolListener = protocolListener;
        copied.httpListener = httpListener;
        copied.transportFactory = transportFactory;
        copied.httpEngine = httpEngine;
        copied.webSocketEngineFactory = webSocketEngineFactory;
        copied.clock = clock;
        return copied;
    }
}
