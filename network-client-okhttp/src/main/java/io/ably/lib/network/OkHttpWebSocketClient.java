package io.ably.lib.network;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okio.ByteString;

import java.nio.ByteBuffer;

public class OkHttpWebSocketClient implements WebSocketClient {
    private final OkHttpClient connection;
    private final Request request;
    private final WebSocketListener listener;
    private WebSocket webSocket;

    public OkHttpWebSocketClient(OkHttpClient connection, Request request, WebSocketListener listener) {
        this.connection = connection;
        this.request = request;
        this.listener = listener;
    }

    @Override
    public void connect() {
        webSocket = connection.newWebSocket(request, new WebSocketHandler(listener));
    }

    @Override
    public void close() {
        webSocket.close(1000, "Close");
    }

    @Override
    public void close(int code, String reason) {
        webSocket.close(code, reason);
    }

    @Override
    public void cancel(int code, String reason) {
        webSocket.cancel();
        listener.onClose(code, reason);
    }

    @Override
    public void send(byte[] bytes) {
        webSocket.send(ByteString.of(bytes));
    }

    @Override
    public void send(String message) {
        webSocket.send(message);
    }

    private static class WebSocketHandler extends okhttp3.WebSocketListener {
        private final WebSocketListener listener;

        private WebSocketHandler(WebSocketListener listener) {
            super();
            this.listener = listener;
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(1000, null);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            listener.onClose(code, reason);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            listener.onError(t);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            listener.onMessage(text);
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            listener.onMessage(ByteBuffer.wrap(bytes.toByteArray()));
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            listener.onOpen();
        }
    }
}
