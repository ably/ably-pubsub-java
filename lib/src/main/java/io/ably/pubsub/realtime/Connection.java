package io.ably.pubsub.realtime;

import io.ably.pubsub.liveobjects.LiveObjectsPlugin;
import io.ably.pubsub.realtime.ConnectionStateListener.ConnectionStateChange;
import io.ably.pubsub.transport.ConnectionManager;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.RecoveryKeyContext;
import io.ably.pubsub.util.EventEmitter;
import io.ably.pubsub.util.Log;
import io.ably.pubsub.util.PlatformAgentProvider;

/**
 * Enables the management of a connection to Ably.
 * Extends an {@link EventEmitter} object.
 * <p>
 * Spec: RTN4a, RTN4e, RTN4g
 */
public class Connection extends EventEmitter<ConnectionEvent, ConnectionStateListener> {

    /**
     * The current {@link ConnectionState} of the connection.
     * <p>
     * Spec: RTN4d
     */
    public ConnectionState state;

    /**
     * An {@link ErrorInfo} object describing the last error received if a connection failure occurs.
     * <p>
     * Spec: RTN14a
     */
    public ErrorInfo reason;

    /**
     * A unique private connection key used to recover or resume a connection, assigned by Ably.
     * When recovering a connection explicitly, the recoveryKey is used in the recover client options
     * as it contains both the key and the last message serial.
     * This private connection key can also be used by other REST clients to publish on behalf of this client.
     * See the
     * <a href="https://ably.com/docs/rest/channels#publish-on-behalf">publishing over REST on behalf of a realtime client docs</a>
     * for more info.
     * <p>
     * Spec: RTN9
     */
    public String key;

    /**
     * The recovery key string can be used by another client to recover this connection's state in the recover client options property.
     * See <a href="https://ably.com/docs/realtime/connection#connection-state-recover-options">connection state recover options</a>
     * for more information.
     * <p>
     * Spec: RTN16m
     * @deprecated use createRecoveryKey method instead.
     */
    @Deprecated
    public String recoveryKey;

    /**
     * createRecoveryKey is a method that returns a json string which incorporates the @connectionKey@, the
     * current @msgSerial@, and a collection of pairs of channel @name@ and current @channelSerial@ for every
     * currently attached channel.
     * <p>
     * Spec: RTN16g, RTN16c
     * </p>
     */
    public String createRecoveryKey() {
        if (key == null || key.isEmpty() || this.state == ConnectionState.closing ||
            this.state == ConnectionState.closed ||
            this.state == ConnectionState.failed ||
            this.state == ConnectionState.suspended
        ) {
            return null; // RTN16g2
        }

        return new RecoveryKeyContext(key, connectionManager.msgSerial, ably.getChannelSerials()).encode();
    }

    /**
     * A unique public identifier for this connection, used to identify this member.
     * <p>
     * Spec: RTN8
     */
    public String id;

    /**
     * Explicitly calling connect() is unnecessary unless the autoConnect attribute of the {@link io.ably.pubsub.types.ClientOptions}
     * object is false.
     * Unless already connected or connecting, this method causes the connection to open,
     * entering the {@link ConnectionState#connecting} state.
     * <p>
     * Spec: RTC1b, RTN3, RTN11
     */
    public void connect() {
        connectionManager.connect();
    }

    /**
     * When connected, sends a heartbeat ping to the Ably server and executes the callback with any error and the response
     * time in milliseconds when a heartbeat ping request is echoed from the server.
     * This can be useful for measuring true round-trip latency to the connected Ably server.
     * @param listener A listener to be notified of success or failure.
     * <p>
     * Spec: RTN13
     */
    public void ping(CompletionListener listener) {
        connectionManager.ping(listener);
    }

    /**
     * Causes the connection to close, entering the {@link ConnectionState#closing} state.
     * Once closed, the library does not attempt to re-establish the connection without an explicit call to {@link Connection#connect}.
     * <p>
     * Spec: RTN12
     */
    public void close() {
        key = null;
        recoveryKey = null;
        connectionManager.close();
    }

    /*****************
     * internal
     *****************/

    Connection(RealtimeClient ably, ConnectionManager.Channels channels, PlatformAgentProvider platformAgentProvider, LiveObjectsPlugin liveObjectsPlugin) throws AblyException {
        this.ably = ably;
        this.state = ConnectionState.initialized;
        this.connectionManager = new ConnectionManager(ably, this, channels, platformAgentProvider, liveObjectsPlugin);
    }

    public void onConnectionStateChange(ConnectionStateChange stateChange) {
        state = stateChange.current;
        reason = stateChange.reason;
        emit(state, stateChange);
    }

    @Override
    protected void apply(ConnectionStateListener listener, ConnectionEvent event, Object... args) {
        try {
            listener.onConnectionStateChanged((ConnectionStateChange)args[0]);
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected exception calling ConnectionStateListener", t);
        }
    }

    public void emitUpdate(ErrorInfo errorInfo) {
        if (state == ConnectionState.connected)
            emit(ConnectionEvent.update, ConnectionStateListener.ConnectionStateChange.createUpdateEvent(errorInfo));
    }

    @Deprecated
    public void emit(ConnectionState state, ConnectionStateChange stateChange) {
        super.emit(state.getConnectionEvent(), stateChange);
    }

    @Deprecated
    public void on(ConnectionState state, ConnectionStateListener listener) {
        super.on(state.getConnectionEvent(), listener);
    }

    @Deprecated
    public void once(ConnectionState state, ConnectionStateListener listener) {
        super.once(state.getConnectionEvent(), listener);
    }

    private static final String TAG = Connection.class.getName();
    final RealtimeClient ably;
    public final ConnectionManager connectionManager;
}
