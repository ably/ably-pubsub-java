package io.ably.pubsub.network;

/**
 * WebSocketClient instance bind to the specified URI.
 * The connection will be established once you call <var>connect</var>.
 */
public interface WebSocketClient {

    /**
     * Establish connection to the Websocket server
     */
    void connect();

    /**
     * Sends the closing handshake. May be sent in response to any other handshake.
     */
    void close();

    /**
     * Sends the closing handshake. May be sent in response to any other handshake.
     *
     * @param code   the closing code
     * @param reason the closing message
     */
    void close(int code, String reason);

    /**
     * This will close the connection immediately without a proper close handshake. The code and the
     * message therefore won't be transferred over the wire also they will be forwarded to `onClose`.
     *
     * @param code   the closing code
     * @param reason the closing message
     **/
    void cancel(int code, String reason);

    /**
     * Sends binary <var>message</var> to the connected webSocket server.
     *
     * @param message The byte-Array of data to send to the WebSocket server.
     */
    void send(byte[] message);

    /**
     * Sends <var>message</var> to the connected websocket server.
     *
     * @param message The string which will be transmitted.
     */
    void send(String message);

}
