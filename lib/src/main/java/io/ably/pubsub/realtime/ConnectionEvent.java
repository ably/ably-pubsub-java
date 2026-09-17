package io.ably.pubsub.realtime;

/**
 * Describes the events emitted by a {@link Connection} object. An event is either an UPDATE or a {@link ConnectionState}.
 */
public enum ConnectionEvent {
    initialized,
    connecting,
    connected,
    disconnected,
    suspended,
    closing,
    closed,
    failed,
    /**
     * An event for changes to connection conditions for which the {@link ConnectionState} does not change.
     * <p>
     * Spec: RTN4h
     */
    update
}
