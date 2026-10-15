package io.ably.pubsub.network;

import java.nio.ByteBuffer;

/**
 * WebSocket Listener
 */
public interface WebSocketListener {
    /**
     * Called after an opening handshake has been performed and the given websocket is ready to be
     * written on.
     */
    void onOpen();

    /**
     * Callback for binary messages received from the remote host
     *
     * @param blob The binary message that was received.
     * @see #onMessage(String)
     **/
    void onMessage(ByteBuffer blob);

    /**
     * Callback for string messages received from the remote host
     *
     * @param string The UTF-8 decoded message that was received.
     * @see #onMessage(ByteBuffer)
     **/
    void onMessage(String string);

    /**
     * Callback for receiving ping frame if it supported by websocket engine
     */
    void onWebsocketPing();

    /**
     * Called after the websocket connection has been closed.
     *
     * @param reason Additional information string
     **/
    void onClose(int code, String reason);

    /**
     * Called when errors occurs. If an error causes the websocket connection to fail {@link
     * WebSocketListener#onClose(int, String)} will be called additionally.<br> This method will be called
     * primarily because of IO or protocol errors.<br> If the given exception is an RuntimeException
     * that probably means that you encountered a bug.<br>
     *
     * @param throwable The exception causing this error
     **/
    void onError(Throwable throwable);

    /**
     * We invoke this callback when runtime is not able to use secure https algorithms (TLS 1.2 +)
     */
    void onOldJavaVersionDetected(Throwable throwable);
}
