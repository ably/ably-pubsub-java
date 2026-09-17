package io.ably.pubsub.realtime;

import io.ably.pubsub.types.ErrorInfo;

/**
 * An interface whereby a client may be notified of state changes for a connection.
 */
public interface ConnectionStateListener {

    /**
     * Called when connection state changes.
     * @param state information about the new state. Check {@link ConnectionState ConnectionState} - for all states available.
     */
    void onConnectionStateChanged(ConnectionStateListener.ConnectionStateChange state);

    /**
     * Contains {@link ConnectionState} change information emitted by the {@link Connection} object.
     */
    class ConnectionStateChange {
        /**
         * The event that triggered this {@link ConnectionState} change.
         * <p>
         * Spec: TA5
         */
        public final ConnectionEvent event;
        /**
         * The previous {@link ConnectionState}.
         * For the {@link ConnectionEvent#update} event, this is equal to the current {@link ConnectionState}.
         * <p>
         * Spec: TA2
         */
        public final ConnectionState previous;
        /**
         * The new {@link ConnectionState}.
         * <p>
         * Spec: TA2
         */
        public final ConnectionState current;
        /**
         * Duration in milliseconds, after which the client retries a connection where applicable.
         * <p>
         * Spec: RTN14d, TA2
         */
        public final long retryIn;
        /**
         * An {@link ErrorInfo} object containing any information relating to the transition.
         * <p>
         * Spec: RTN4f, TA3
         */
        public final ErrorInfo reason;

        public ConnectionStateChange(ConnectionState previous, ConnectionState current, long retryIn, ErrorInfo reason) {
            this.event = current.getConnectionEvent();
            this.previous = previous;
            this.current = current;
            this.retryIn = retryIn;
            this.reason = reason;
        }

        /* private constructor for UPDATE event */
        private ConnectionStateChange(ErrorInfo reason) {
            this.event = ConnectionEvent.update;
            this.current = this.previous = ConnectionState.connected;
            this.retryIn = 0;
            this.reason = reason;
        }

        /* construct UPDATE event */
        public static ConnectionStateChange createUpdateEvent(ErrorInfo reason) {
            return new ConnectionStateChange(reason);
        }
    }

    class Multicaster extends io.ably.pubsub.util.Multicaster<ConnectionStateListener> implements ConnectionStateListener {
        @Override
        public void onConnectionStateChanged(ConnectionStateChange state) {
            for (final ConnectionStateListener member : getMembers())
                try {
                    member.onConnectionStateChanged(state);
                } catch(Throwable t) {}
        }
    }

    class Filter implements ConnectionStateListener {
        @Override
        public void onConnectionStateChanged(ConnectionStateChange change) {
            if(change.current == state)
                listener.onConnectionStateChanged(change);
        }
        Filter(ConnectionState state, ConnectionStateListener listener) { this.state = state; this.listener = listener; }
        ConnectionState state;
        ConnectionStateListener listener;
    }
}
