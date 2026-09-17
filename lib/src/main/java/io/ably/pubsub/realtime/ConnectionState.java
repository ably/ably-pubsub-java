package io.ably.pubsub.realtime;

/**
 * Describes the realtime {@link Connection} object states.
 */
public enum ConnectionState {
    /**
     * A connection with this state has been initialized but no connection has yet been attempted.
     */
    initialized(ConnectionEvent.initialized),
    /**
     * A connection attempt has been initiated.
     * The connecting state is entered as soon as the library has completed initialization,
     * and is reentered each time connection is re-attempted following disconnection.
     */
    connecting(ConnectionEvent.connecting),
    /**
     * A connection exists and is active.
     */
    connected(ConnectionEvent.connected),
    /**
     * A temporary failure condition.
     * No current connection exists because there is no network connectivity or no host is available.
     * The disconnected state is entered if an established connection is dropped, or if a connection attempt was unsuccessful.
     * In the disconnected state the library will periodically attempt to open a new connection (approximately every 15 seconds),
     * anticipating that the connection will be re-established soon and thus connection and channel continuity will be possible.
     * In this state, developers can continue to publish messages as they are automatically placed in a local queue,
     * to be sent as soon as a connection is reestablished.
     * Messages published by other clients while this client is disconnected will be delivered to it upon reconnection,
     * so long as the connection was resumed within 2 minutes. After 2 minutes have elapsed,
     * recovery is no longer possible and the connection will move to the SUSPENDED state.
     */
    disconnected(ConnectionEvent.disconnected),
    /**
     * A long term failure condition.
     * No current connection exists because there is no network connectivity or no host is available.
     * The suspended state is entered after a failed connection attempt if there has then been no connection for a period of two minutes.
     * In the suspended state, the library will periodically attempt to open a new connection every 30 seconds.
     * Developers are unable to publish messages in this state.
     * A new connection attempt can also be triggered by an explicit call to {@link Connection#connect}.
     * Once the connection has been re-established, channels will be automatically re-attached.
     * The client has been disconnected for too long for them to resume from where they left off,
     * so if it wants to catch up on messages published by other clients while it was disconnected,
     * it needs to use the <a href="https://ably.com/docs/realtime/history">History API</a>.
     */
    suspended(ConnectionEvent.suspended),
    /**
     * An explicit request by the developer to close the connection has been sent to the Ably service.
     * If a reply is not received from Ably within a short period of time,
     * the connection is forcibly terminated and the connection state becomes CLOSED.
     */
    closing(ConnectionEvent.closing),
    /**
     * The connection has been explicitly closed by the client.
     * In the closed state, no reconnection attempts are made automatically by the library, and clients may not publish messages.
     * No connection state is preserved by the service or by the library.
     * A new connection attempt can be triggered by an explicit call to {@link Connection#connect}, which results in a new connection.
     */
    closed(ConnectionEvent.closed),
    /**
     * This state is entered if the client library encounters a failure condition that it cannot recover from.
     * This may be a fatal connection error received from the Ably service,
     * for example an attempt to connect with an incorrect API key, or a local terminal error,
     * for example the token in use has expired and the library does not have any way to renew it.
     * In the failed state, no reconnection attempts are made automatically by the library, and clients may not publish messages.
     * A new connection attempt can be triggered by an explicit call to {@link Connection#connect}.
     */
    failed(ConnectionEvent.failed);

    final private ConnectionEvent event;
    ConnectionState(ConnectionEvent event) {
        this.event = event;
    }
    public ConnectionEvent getConnectionEvent() {
        return event;
    }
}
