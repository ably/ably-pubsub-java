package io.ably.pubsub.network;

import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.net.URI;
import java.nio.ByteBuffer;

public class DefaultWebSocketClient extends org.java_websocket.client.WebSocketClient implements WebSocketClient {

    private final WebSocketListener listener;
    private final WebSocketEngineConfig config;

    private boolean shouldExplicitlyVerifyHostname = true;

    public DefaultWebSocketClient(URI serverUri, WebSocketListener listener, WebSocketEngineConfig config) {
        super(serverUri);
        this.listener = listener;
        this.config = config;
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        if (config.isTls() && shouldExplicitlyVerifyHostname && !isHostnameVerified(config.getHost())) {
            close();
        } else {
            listener.onOpen();
        }
    }

    @Override
    public void onMessage(String s) {
        listener.onMessage(s);
    }

    @Override
    public void onMessage(ByteBuffer blob) {
        listener.onMessage(blob);
    }

    /* This allows us to detect a websocket ping, so we don't need Ably pings. */
    @Override
    public void onWebsocketPing(WebSocket conn, Framedata f) {
        /* Call superclass to ensure the pong is sent. */
        super.onWebsocketPing(conn, f);
        listener.onWebsocketPing();
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        listener.onClose(code, reason);
    }

    @Override
    public void onError(Exception e) {
        listener.onError(e);
    }

    @Override
    public void cancel(int code, String reason) {
        closeConnection(code, reason);
    }

    @Override
    protected void onSetSSLParameters(SSLParameters sslParameters) {
        try {
            super.onSetSSLParameters(sslParameters);
            shouldExplicitlyVerifyHostname = false;
        } catch (NoSuchMethodError exception) {
            // This error will be thrown on Android below level 24.
            // When the minSdkVersion will be updated to 24 we should remove this overridden method.
            // https://github.com/TooTallNate/Java-WebSocket/wiki/No-such-method-error-setEndpointIdentificationAlgorithm#workaround
            shouldExplicitlyVerifyHostname = true;
            listener.onOldJavaVersionDetected(exception);
        }
    }

    @Override
    public void send(String text) {
        try {
            super.send(text);
        } catch (WebsocketNotConnectedException e) {
            throw new NotConnectedException(e);
        }
    }

    /**
     * Added because we had to override the onSetSSLParameters() that usually performs this verification.
     * When the minSdkVersion will be updated to 24 we should remove this method and its usages.
     * https://github.com/TooTallNate/Java-WebSocket/wiki/No-such-method-error-setEndpointIdentificationAlgorithm#workaround
     */
    private boolean isHostnameVerified(String hostname) {
        final SSLSession session = getSSLSession();
        if (HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)) {
            return true;
        } else {
            listener.onError(new IllegalArgumentException("Hostname verification failed, expected " + hostname + ", found " + session.getPeerHost()));
            return false;
        }
    }
}
