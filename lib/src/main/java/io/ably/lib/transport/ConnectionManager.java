package io.ably.lib.transport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.debug.DebugOptions.RawProtocolListener;
import io.ably.lib.http.HttpHelpers;
import io.ably.lib.liveobjects.LiveObjectsPlugin;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.realtime.Connection;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.realtime.ConnectionStateListener;
import io.ably.lib.realtime.ConnectionStateListener.ConnectionStateChange;
import io.ably.lib.rest.Auth;
import io.ably.lib.transport.ITransport.ConnectListener;
import io.ably.lib.transport.ITransport.TransportParams;
import io.ably.lib.transport.NetworkConnectivity.NetworkConnectivityListener;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ConnectionDetails;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Param;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.types.ProtocolSerializer;
import io.ably.lib.types.PublishResult;
import io.ably.lib.util.Clock;
import io.ably.lib.util.Log;
import io.ably.lib.util.PlatformAgentProvider;
import io.ably.lib.util.ReconnectionStrategy;
import io.ably.lib.util.SystemClock;
import org.jetbrains.annotations.Nullable;

public class ConnectionManager implements ConnectListener {
    final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();

    /**************************************************************
     * ConnectionManager
     *
     * This class is responsible for coordinating all actions that
     * relate to transports and connection state.
     *
     * It comprises two principal parts:
     * - An action queue, and a thread that performs those actions.
     *   Actions comprise connection state change requests, plus other
     *   actions that arise from transport state indications. An
     *   action Handler thread runs, except during idle times when
     *   there is no current or pending connection activity, that
     *   performs queued actions.
     *
     * - A state machine that represents the current connection state,
     *   and the possible transitions between states.
     **************************************************************/

    private static final String TAG = ConnectionManager.class.getName();
    private static final String INTERNET_CHECK_URL = "https://internet-up.ably-realtime.com/is-the-internet-up.txt";
    private static final String INTERNET_CHECK_OK = "yes";

    /***********************************
     * default errors
     ***********************************/

    static ErrorInfo REASON_CLOSED = new ErrorInfo("Can't attach when not in an active state", 200, 10000);
    static ErrorInfo REASON_DISCONNECTED = new ErrorInfo("Connection temporarily unavailable", 503, 80003);
    static ErrorInfo REASON_SUSPENDED = new ErrorInfo("Connection unavailable", 503, 80002);
    static ErrorInfo REASON_FAILED = new ErrorInfo("Connection failed", 400, 80000);
    static ErrorInfo REASON_REFUSED = new ErrorInfo("Access refused", 401, 40100);
    static ErrorInfo REASON_TOO_BIG = new ErrorInfo("Connection closed; message too large", 400, 40000);

    /**
     * When connection manager entering terminal state {@code currentState.terminal == true} it should clean up
     * {@link #handlerThread} and invoke {@link #stopConnectivityListener}.
     * <p>
     * If this flag is true that means that current state is terminal but cleaning up still in progress
     */
    private boolean cleaningUpAfterEnteringTerminalState = false;

    /**
     * Indicates whether a close request has been initiated for the connection.
     * <p>
     * This variable is set to true when a close request is made, typically to
     * signal that the connection should transition into a closing state.
     * It helps manage the connection lifecycle, ensuring that no further
     * operations for this connection are attempted once closure is requested.
     * <p>
     * Default value is false, indicating the connection remains active unless
     * explicitly requested to close.
     */
    private volatile boolean closeRequested = false;

    /**
     * A nullable reference to the LiveObjects plugin.
     * <p>
     * This field is initialized only if the LiveObjects plugin is present in the classpath.
     */
    private final LiveObjectsPlugin liveObjectsPlugin;
    public Long objectsGCGracePeriod = null;

    /**
     * Methods on the channels map owned by the {@link AblyRealtime} instance
     * which the {@link ConnectionManager} needs access to.
     */
    public interface Channels {
        void onMessage(ProtocolMessage msg);
        void suspendAll(ErrorInfo error, boolean notifyStateChange);
        Iterable<Channel> values();

        void transferToChannelQueue(List<QueuedMessage> queuedMessages);
    }

    /***********************************
     * a class encapsulating information
     * associated with a currentState change
     * request or notification
     ***********************************/

    public static class StateIndication {
        final ConnectionState state;
        final ErrorInfo reason;
        final String fallback;
        final String currentHost;

        StateIndication(ConnectionState state) {
            this(state, null);
        }

        public StateIndication(ConnectionState state, ErrorInfo reason) {
            this(state, reason, null, null);
        }

        StateIndication(ConnectionState state, ErrorInfo reason, String fallback, String currentHost) {
            this.state = state;
            this.reason = reason;
            this.fallback = fallback;
            this.currentHost = currentHost;
        }
    }

    /*************************************
     * a class encapsulating state machine
     * information for a given state
     *************************************/

    public abstract class State {
        public final ConnectionState state;
        public final ErrorInfo defaultErrorInfo;
        public final boolean queueEvents;
        public final boolean sendEvents;

        final boolean terminal;
        public long timeout;

        State(ConnectionState state, boolean queueEvents, boolean sendEvents, boolean terminal, long timeout, ErrorInfo defaultErrorInfo) {
            this.state = state;
            this.queueEvents = queueEvents;
            this.sendEvents = sendEvents;
            this.terminal = terminal;
            this.timeout = timeout;
            this.defaultErrorInfo = defaultErrorInfo;
        }

        /**
         * Called on the current state to determine the response to a
         * give state change request.
         * @param target: the state change request or event
         * @return StateIndication result: the determined response to
         * the request with the required state transition, if any. A
         * null result indicates that there is no resulting transition.
         */
        abstract StateIndication validateTransition(StateIndication target);

        /**
         * Called when the timeout occurs for the current state.
         * @return StateIndication result: the determined response to
         * the timeout with the required state transition, if any. A
         * null result indicates that there is no resulting transition.
         */
        StateIndication onTimeout() {
            return null;
        }

        /**
         * Perform a transition to this state.
         * @param stateIndication: the transition request that triggered this transition
         * @param change: the change event corresponding to this transition.
         */
        void enact(StateIndication stateIndication, ConnectionStateChange change) {
            if(change != null) {
                /* if now connected, send queued messages, etc */
                if(sendEvents) {
                    sendQueuedMessages();
                } else if(!queueEvents) {
                    failQueuedMessages(stateIndication.reason);
                }
                for(final Channel channel : channels.values()) {
                    enactForChannel(stateIndication, change, channel);
                }
            }
        }

        /**
         * Perform a transition to this state for a given channel.
         * @param stateIndication: the transition request that triggered this transition
         * @param change: the change event corresponding to this transition.
         * @param channel: the channel
         */
        void enactForChannel(StateIndication stateIndication, ConnectionStateChange change, Channel channel) {}
    }

    /**************************************************
     * Initialized: the initial state
     **************************************************/

    class Initialized extends State {
        Initialized() {
            super(ConnectionState.initialized, true, false, false, 0, null);
        }

        @Override
        StateIndication validateTransition(StateIndication target) {
            /* we can transition to any other state, other than ourselves */
            if(target.state == this.state) {
                return null;
            }
            return target;
        }
    }

    /**************************************************
     * Connecting: a connection attempt is in progress
     **************************************************/

    class Connecting extends State {
        Connecting() {
            super(ConnectionState.connecting, true, false, false, Defaults.TIMEOUT_CONNECT, null);
        }

        @Override
        StateIndication onTimeout() {
            return checkSuspended(null);
        }

        @Override
        StateIndication validateTransition(StateIndication target) {
            /* we can transition to any other state */
            return target;
        }

        @Override
        void enact(StateIndication stateIndication, ConnectionStateChange change) {
            super.enact(stateIndication, change);

            if (hasConnectBeenInvokeOnClosedOrFailedState(change)) {
                cleanMsgSerialAndErrorReason();
            }

            connectImpl(stateIndication);
        }
    }

    /**************************************************
     * Connected: a connection is established
     **************************************************/

    class Connected extends State {
        Connected() {
            super(ConnectionState.connected, false, true, false, 0, null);
        }

        @Override
        StateIndication validateTransition(StateIndication target) {
            if(target.state == this.state) {
                /* RTN24: no currentState change, so no transition, required, but there will be an update event;
                 * connected is special case because we want to deliver reauth notifications to listeners as an update */
                addAction(new UpdateAction(null));
                return null;
            }

            /* we can transition to any other state */
            return target;
        }

        @Override
        void enactForChannel(StateIndication stateIndication, ConnectionStateChange change, Channel channel) {
            channel.setConnected();
        }

        @Override
        void enact(StateIndication stateIndication, ConnectionStateChange change) {
            super.enact(stateIndication, change);
            pendingConnect = null;
        }
    }

    /**************************************************
     * Disconnected: no connection is established, but
     * a reconnection attempt will be made on timer
     * expiry, anticipating preservation of connection
     * state on reconnection
     **************************************************/

    class Disconnected extends State {
        Disconnected() {
            super(ConnectionState.disconnected, true, false, false, ably.options.disconnectedRetryTimeout, REASON_DISCONNECTED);
        }

        @Override
        StateIndication validateTransition(StateIndication target) {
            /* we can't transition to ourselves */
            if(target.state == this.state) {
                return null;
            }
            /* a closing event will transition directly to closed */
            if(target.state == ConnectionState.closing) {
                return new StateIndication(ConnectionState.closed);
            }
            /* otherwise, the transition is valid */
            return target;
        }

        @Override
        StateIndication onTimeout() {
            return new StateIndication(ConnectionState.connecting);
        }

        @Override
        void enactForChannel(StateIndication stateIndication, ConnectionStateChange change, Channel channel) {
            /* (RTL3e) If the connection currentState enters the
             * DISCONNECTED currentState, it will have no effect on the
             * channel states. */
        }

        @Override
        void enact(StateIndication stateIndication, ConnectionStateChange change) {
            super.enact(stateIndication, change);
            clearTransport();

            // If we were connected, immediately retry
            if(change.previous == ConnectionState.connected) {
                setSuspendTime();

                if (!suppressRetry) {
                    Log.v(TAG, "Was previously connected, retrying immediately");
                    requestState(ConnectionState.connecting);
                }
            }
        }
    }

    /**************************************************
     * Suspended: no connection is established. A
     * reconnection attempt will be made on timer expiry
     * but there will be no continuity of connection
     * state on reconnection
     **************************************************/

    class Suspended extends State {
        Suspended() {
            super(ConnectionState.suspended, false, false, false, ably.options.suspendedRetryTimeout, REASON_SUSPENDED);
        }

        @Override
        StateIndication validateTransition(StateIndication target) {
            /* we can't transition to ourselves */
            if(target.state == this.state) {
                return null;
            }
            /* a closing event will transition directly to closed */
            if(target.state == ConnectionState.closing) {
                return new StateIndication(ConnectionState.closed);
            }
            /* otherwise, the transition is valid */
            return target;
        }

        @Override
        StateIndication onTimeout() {
            return new StateIndication(ConnectionState.connecting);
        }

        @Override
        void enactForChannel(StateIndication stateIndication, ConnectionStateChange change, Channel channel) {
            /* (RTL3c) If the connection currentState enters the SUSPENDED
             * currentState, then an ATTACHING or ATTACHED channel currentState
             * will transition to SUSPENDED. */
            channel.setSuspended(defaultErrorInfo, true);
        }
    }

    /**************************************************
     * Closing: a close sequence is in progress
     **************************************************/

    class Closing extends State {
        Closing() {
            super(ConnectionState.closing, false, false, false, Defaults.TIMEOUT_CONNECT, REASON_CLOSED);
        }

        @Override
        StateIndication validateTransition(StateIndication target) {
            /* we can't transition to ourselves */
            if(target.state == this.state) {
                return null;
            }
            /* any disconnection event will transition directly to closed */
            if(target.state == ConnectionState.disconnected || target.state == ConnectionState.suspended) {
                return new StateIndication(ConnectionState.closed);
            }
            /* otherwise, the transition is valid */
            return target;
        }

        @Override
        StateIndication onTimeout() {
            return new StateIndication(ConnectionState.closed);
        }

        @Override
        void enact(StateIndication stateIndication, ConnectionStateChange change) {
            super.enact(stateIndication, change);
            boolean shouldAwaitConnection = change.previous == ConnectionState.connecting;
            boolean closed = closeImpl(shouldAwaitConnection);
            if (closed) {
                addAction(new AsynchronousStateChangeAction(ConnectionState.closed));
            }
        }
    }

    /**************************************************
     * Closed: the connection is closed, and no
     * reconnection attempt will be made unless
     * explicitly requested
     **************************************************/

    class Closed extends State {
        Closed() {
            super(ConnectionState.closed, false, false, true, 0, REASON_CLOSED);
        }

        @Override
        StateIndication validateTransition(StateIndication target) {
            /* we only leave the closed state via a connection attempt */
            if(target.state == ConnectionState.connecting) {
                return target;
            }
            /* otherwise, the transition is not valid */
            return null;
        }

        @Override
        void enactForChannel(StateIndication stateIndication, ConnectionStateChange change, Channel channel) {
            /* (RTL3b) If the connection currentState enters the CLOSED
             * currentState, then an ATTACHING or ATTACHED channel currentState
             * will transition to DETACHED. */
            channel.setConnectionClosed(REASON_CLOSED);
        }
    }

    /**************************************************
     * Failed: there is no connection, and there has
     * been an error, either in options validation or
     * as a response to a connection attempt, that
     * implies no new connection attempt will succeed.
     * No reconnection attempt will be made unless
     * explicitly requested
     **************************************************/

    class Failed extends State {
        Failed() {
            super(ConnectionState.failed, false, false, true, 0, REASON_FAILED);
        }

        @Override
        StateIndication validateTransition(StateIndication target) {
            /* we only leave the failed state via a connection attempt */
            if(target.state == ConnectionState.connecting) {
                return target;
            }
            /* otherwise, the transition is not valid */
            return null;
        }

        @Override
        void enactForChannel(StateIndication stateIndication, ConnectionStateChange change, Channel channel) {
            /* (RTL3a) If the connection currentState enters the FAILED
             * currentState, then an ATTACHING or ATTACHED channel currentState
             * will transition to FAILED, set the
             * Channel#errorReason and emit the error event. */
            channel.setConnectionFailed(stateIndication.reason);
        }

        @Override
        void enact(StateIndication stateIndication, ConnectionStateChange change) {
            super.enact(stateIndication, change);
            clearTransport();
        }
    }

    public ErrorInfo getStateErrorInfo() {
        return stateError != null ? stateError : currentState.defaultErrorInfo;
    }

    public boolean isActive() {
        return currentState.queueEvents || currentState.sendEvents;
    }

    /**
     * Listens for connection state changes.
     *
     * The close() method must be called when the ConnectionWaiter is no longer needed.
     */
    private class ConnectionWaiter implements ConnectionStateListener {
        private ConnectionStateChange change;
        private boolean closed = false;

        /**
         * Create a ConnectionWaiter as a connection listener.
         */
        private ConnectionWaiter() {
            connection.on(this);
        }

        /**
         * Wait for a currentState change notification
         */
        private synchronized ErrorInfo waitForChange() {
            if (closed) {
                throw new IllegalStateException("Already closed.");
            }

            Log.d(TAG, "ConnectionWaiter.waitFor()");
            if (change == null) {
                try { wait(); } catch(InterruptedException e) {}
            }
            Log.d(TAG, "ConnectionWaiter.waitFor done: currentState=" + currentState + ")");
            ErrorInfo reason = change.reason;
            change = null;
            return reason;
        }

        /**
         * ConnectionStateListener interface
         */
        @Override
        public synchronized void onConnectionStateChanged(ConnectionStateChange state) {
            change = state;
            notify();
        }

        /**
         * Remove this ConnectionWaiter as a connection listener.
         */
        private void close() {
            // This method is explicitly not synchronized. There may be a case for this in the
            // future, however its addition is designed to be lightweight with minimal impact.
            if (closed) {
                return;
            }
            closed = true;
            connection.off(this);
        }
    }

    /***********************
     * Actions
     ***********************/

    /**
     * A class that encapsulates actions to perform by the ConnectionManager
     */
    private interface Action extends Runnable {}

    /**
     * An class that performs a state transition
     */
    private abstract class StateChangeAction {

        protected final ITransport transport;
        protected final StateIndication stateIndication;
        protected ConnectionStateChange change;

        StateChangeAction(ITransport transport, StateIndication stateIndication) {
            this.transport = transport;
            this.stateIndication = stateIndication;
        }

        /**
         * Make the change to the ConnectionManager currentState represented by this Action
         */
        protected void setState() {
            change = ConnectionManager.this.setState(transport, stateIndication);
        }

        protected void enactState() {
            if(change != null) {
                // RTL3d1: implement the state change first, so channel states are updated
                // before listeners observe the new connection state
                states.get(stateIndication.state).enact(stateIndication, change);

                if(change.current != change.previous) {
                    /* broadcast currentState change */
                    connection.onConnectionStateChange(change);
                }

                if(currentState.terminal) {
                    clearTransport();
                }
            }
        }
    }

    /**
     * An Action that enacts a state transition, making the ConnectionManager state change
     * synchronously. This is for instances such as any transition away from the connected
     * state, where the state is updated synchronously with the transport state change.
     * This ensures that there is no possibility of an attempt to send on the transport
     * after it has indicated that it is not available.
     */
    private class SynchronousStateChangeAction extends StateChangeAction implements Action {
        SynchronousStateChangeAction(ITransport transport, StateIndication stateIndication) {
            super(transport, stateIndication);
            setState();
        }

        @Override
        public void run() {
            enactState();
        }
    }

    /**
     * An Action that enacts a state transition, making the ConnectionManager state change
     * asynchronously. This applies to all transitions that are not transitions away from
     * the connected state.
     */
    private class AsynchronousStateChangeAction extends StateChangeAction implements Action{
        AsynchronousStateChangeAction(ConnectionState state) {
            super(null, new StateIndication(state, null));
        }

        AsynchronousStateChangeAction(ITransport transport, StateIndication stateIndication) {
            super(transport, stateIndication);
        }

        @Override
        public void run() {
            setState();
            enactState();
        }
    }

    /**
     * An Action that performs an inband reauthorisation
     */
    private class ReauthAction implements Action {
        @Override
        public void run() {
            handleReauth();
        }
    }

    /**
     * An Action that handles dissemination of update events arising from a
     * connected -> connected transition
     */
    private class UpdateAction implements Action {
        private final ErrorInfo reason;

        UpdateAction(ErrorInfo reason) {
            this.reason = reason;
        }

        @Override
        public void run() {
            connection.emitUpdate(reason);
        }
    }

    /**
     * A queue of Actions awaiting processing
     */
    private static class ActionQueue extends ArrayDeque<Action> {
        public synchronized boolean add(Action action) {
            return super.add(action);
        }

        public synchronized Action poll() {
            return super.poll();
        }

        public synchronized Action peek() {
            return super.peek();
        }

        public synchronized int size() {
            return super.size();
        }
    }

    /**
     * Append an action to the pending action queue
     * @param action: the action
     */
    private synchronized void addAction(Action action) {
        actionQueue.add(action);
        notifyAll();
    }

    /**
     * A handler that runs in a dedicated Thread that processes queued actions
     */
    class ActionHandler implements Runnable {

        public void run() {
            while(true) {
                /*
                 * Until we're committed to exit we:
                 * - wait for an action or timeout
                 * - given an action, perform the action asynchronously;
                 * - if a timeout, perform the timeout state transition
                 */

                /* Hold the lock until we obtain an action */
                synchronized(ConnectionManager.this) {
                    while(actionQueue.size() == 0) {
                        /* if we're in a terminal state, then this thread is done */
                        if(currentState.terminal) {
                            /* indicate that this thread is committed to die */
                            handlerThread = null;
                            stopConnectivityListener();
                            cleaningUpAfterEnteringTerminalState = false;
                            ConnectionManager.this.notifyAll();
                            return;
                        }

                        /* wait for an action event or for expiry of the current currentState */
                        tryWait(currentState.timeout);

                        /* if during the wait some action was requested, handle it */
                        Action act = actionQueue.peek();
                        if (act != null) {
                            Log.d(TAG, "Wait ended by action: " + act.toString());
                            break;
                        }

                        /* if our currentState wants us to retry on timer expiry, do that */
                        if (!suppressRetry) {
                            StateIndication nextState = currentState.onTimeout();
                            if (nextState != null) {
                                requestState(nextState);
                            }
                        }
                    }
                }

                /* perform outstanding actions, without the ConnectionManager locked */
                Action deferredAction;
                while((deferredAction = actionQueue.poll()) != null) {
                    try {
                        deferredAction.run();
                    } catch(Exception e) {
                        Log.e(TAG, "Action invocation failed with exception: action = " + deferredAction.toString(), e);
                    }
                }
            }
        }
    }

    /***********************
     * ConnectionManager
     ***********************/

    public ConnectionManager(final AblyRealtime ably, final Connection connection, final Channels channels, final PlatformAgentProvider platformAgentProvider, LiveObjectsPlugin liveObjectsPlugin) throws AblyException {
        this.ably = ably;
        this.clock = SystemClock.clockFrom(ably.options);
        this.connection = connection;
        this.channels = channels;
        this.platformAgentProvider = platformAgentProvider;
        this.liveObjectsPlugin = liveObjectsPlugin;

        ClientOptions options = ably.options;
        this.hosts = new Hosts(options.realtimeHost, Defaults.HOST_REALTIME, options);

        /* debug options */
        ITransport.Factory transportFactory = null;
        RawProtocolListener protocolListener = null;
        if(options instanceof DebugOptions) {
            protocolListener = ((DebugOptions) options).protocolListener;
            transportFactory = ((DebugOptions) options).transportFactory;
        }
        this.protocolListener = protocolListener;
        this.transportFactory = (transportFactory != null) ? transportFactory : Defaults.TRANSPORT;

        /* construct all states */
        states.put(ConnectionState.initialized, new Initialized());
        states.put(ConnectionState.connecting, new Connecting());
        states.put(ConnectionState.connected, new Connected());
        states.put(ConnectionState.disconnected, new Disconnected());
        states.put(ConnectionState.suspended, new Suspended());
        states.put(ConnectionState.closing, new Closing());
        states.put(ConnectionState.closed, new Closed());
        states.put(ConnectionState.failed, new Failed());

        currentState = states.get(ConnectionState.initialized);

        setSuspendTime();
    }

    /*********************
     * host management
     *********************/

    /* This is only here for the benefit of ConnectionManagerTest. */
    public String getHost() {
        return lastUsedHost;
    }

    /*********************
     * states API
     *********************/

    public synchronized State getConnectionState() {
        return currentState;
    }

    public synchronized void connect() {
        /* connect() is the only action that will bring the ConnectionManager out of a terminal currentState */
        if(currentState.terminal || currentState.state == ConnectionState.initialized) {
            try {
                startup();
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e(TAG, "Failed to start up connection", e);
                return;
            }
        }
        if (closeRequested || currentState.terminal) {
            // (RTN11d)
            reinitializeChannelsAfterReconnect();
            closeRequested = false;
        }
        requestState(ConnectionState.connecting);
    }

    public void close() {
        closeRequested = true;
        requestState(ConnectionState.closing);
    }

    public void requestState(ConnectionState state) {
        requestState(new StateIndication(state, null));
    }

    public void requestState(StateIndication state) {
        requestState(null, state);
    }

    /**
     * Get query params representing the current authentication method and credentials.
     */
    Param[] getAuthParams() throws AblyException {
        return ably.auth.getAuthParams();
    }

    /**
     * Determines if the given WebSocketTransport instance is the currently active transport.
     *
     * @param transport the WebSocketTransport instance to check against the active transport
     * @return true if the provided transport is the currently active transport, false otherwise
     */
    boolean isActiveTransport(WebSocketTransport transport) {
        return transport == this.transport;
    }

    private synchronized void requestState(ITransport transport, StateIndication stateIndication) {
        Log.v(TAG, "requestState(): requesting " + stateIndication.state + "; id = " + connection.id);
        addAction(new AsynchronousStateChangeAction(transport, stateIndication));
    }

    private synchronized ConnectionStateChange setState(ITransport transport, StateIndication stateIndication) {
        /* check validity of transport */
        if (transport != null && transport != this.transport) {
            Log.v(TAG, "setState: action received for superseded transport; discarding");
            return null;
        }

        /* check validity of transition */
        StateIndication validatedStateIndication = currentState.validateTransition(stateIndication);
        if (validatedStateIndication == null) {
            Log.v(TAG, "setState(): not transitioning; not a valid transition " + stateIndication.state);
            return null;
        }

        if (stateIndication.state == ConnectionState.connected || stateIndication.state == ConnectionState.suspended) {
            this.disconnectedRetryAttempt = 0;
        }

        if (stateIndication.state == ConnectionState.disconnected) {
            states.get(ConnectionState.disconnected).timeout =
                ReconnectionStrategy.getRetryTime(ably.options.disconnectedRetryTimeout, ++disconnectedRetryAttempt);
        }

        // RTN8c, RTN9c
        if (stateIndication.state == ConnectionState.closing || stateIndication.state == ConnectionState.closed
        || stateIndication.state == ConnectionState.suspended || stateIndication.state == ConnectionState.failed) {
            connection.id = null;
            connection.key = null;
        }

        /* update currentState */
        ConnectionState newConnectionState = validatedStateIndication.state;
        State newState = states.get(newConnectionState);

        ErrorInfo reason = validatedStateIndication.reason;
        if (reason == null) {
            reason = newState.defaultErrorInfo;
        }
        Log.v(TAG, "setState(): setting " + newState.state + "; reason " + reason);
        ConnectionStateChange change = new ConnectionStateChange(currentState.state, newConnectionState, newState.timeout, reason);

        currentState = newState;
        cleaningUpAfterEnteringTerminalState = currentState.terminal;
        stateError = reason;

        return change;
    }

    /*********************
     * ping API
     *********************/

    public void ping(final CompletionListener listener) {
        HeartbeatWaiter waiter = new HeartbeatWaiter(listener);
        if(currentState.state != ConnectionState.connected) {
            waiter.onError(new ErrorInfo("Unable to ping service; not connected", 40000, 400));
            return;
        }
        synchronized(heartbeatWaiters) {
            heartbeatWaiters.add(waiter);
            waiter.start();
        }
        try {
            send(new ProtocolMessage(ProtocolMessage.Action.heartbeat), false, null);
        } catch (AblyException e) {
            waiter.onError(e.errorInfo);
        }
    }

    /**
     * A thread that waits for completion of a ping
     */
    private class HeartbeatWaiter extends Thread {
        private final CompletionListener listener;

        HeartbeatWaiter(CompletionListener listener) {
            this.listener = listener;
        }

        private void onSuccess() {
            clear();
            if(listener != null) {
                listener.onSuccess();
            }
        }

        private void onError(ErrorInfo reason) {
            clear();
            if(listener != null) {
                listener.onError(reason);
            }
        }

        private boolean clear() {
            boolean pending = heartbeatWaiters.remove(this);
            if(pending) {
                interrupt();
            }
            return pending;
        }

        @Override
        public void run() {
            boolean pending;
            synchronized(heartbeatWaiters) {
                try {
                    clock.waitOn(heartbeatWaiters, HEARTBEAT_TIMEOUT);
                } catch (InterruptedException ie) {
                }
                pending = clear();
            }
            if(pending) {
                onError(new ErrorInfo("Timed out waiting for heartbeat response", 50000, 500));
            } else {
                onSuccess();
            }
        }
    }

    /***************************************
     * auth event handling
     ***************************************/

    /**
     * (RTC8) For a realtime client, Auth.authorize instructs the library to
     * obtain a token using the provided tokenParams and authOptions and upgrade
     * the current connection to use that token; or if not currently connected,
     * to connect with the token.
     */
    public void onAuthUpdated(final String token, final boolean waitForResponse) throws AblyException {
        final ConnectionWaiter waiter = new ConnectionWaiter();
        try {
            switch(currentState.state) {
                case connected:
                    /* (RTC8a) If the connection is in the CONNECTED currentState and
                     * auth.authorize is called or Ably requests a re-authentication
                     * (see RTN22), the client must obtain a new token, then send an
                     * AUTH ProtocolMessage to Ably with an auth attribute
                     * containing an AuthDetails object with the token string. */
                    try {
                        ProtocolMessage msg = new ProtocolMessage(ProtocolMessage.Action.auth);
                        msg.auth = new ProtocolMessage.AuthDetails(token);
                        send(msg, false, null);
                    } catch (AblyException e) {
                        /* The send failed. Close the transport; if a subsequent
                         * reconnect succeeds, it will be with the new token. */
                        Log.v(TAG, "onAuthUpdated: closing transport after send failure");
                        transport.close();
                    }
                    break;

                case connecting:
                    /* Close the connecting transport. */
                    Log.v(TAG, "onAuthUpdated: closing connecting transport");
                    ErrorInfo disconnectError = new ErrorInfo("Aborting incomplete connection with superseded auth params", 503, 80003);
                    requestState(new StateIndication(ConnectionState.disconnected, disconnectError, null, null));
                    /* Start a new connection attempt. */
                    connect();
                    break;

                default:
                    /* Start a new connection attempt. */
                    connect();
                    break;
            }

            if(!waitForResponse) {
                return;
            }

            /* Wait for a currentState transition into anything other than connecting or
             * disconnected. Note that this includes the case that the connection
             * was already connected, and the AUTH message prompted the server to
             * send another connected message. */
            boolean waitingForConnected = true;
            while (waitingForConnected) {
                final ErrorInfo reason = waiter.waitForChange();
                final ConnectionState connectionState = currentState.state;
                switch (connectionState) {
                    case connected:
                        Log.v(TAG, "onAuthUpdated: got connected");
                        waitingForConnected = false;
                        break;

                    case connecting:
                    case disconnected:
                        Log.v(TAG, "onAuthUpdated: " + connectionState);
                        break;

                    default:
                        /* suspended/closed/error: throw the error. */
                        Log.v(TAG, "onAuthUpdated: throwing exception");
                        throw AblyException.fromErrorInfo(reason);
                }
            }
        } finally {
            waiter.close();
        }
    }


    /**
     * Async version of onAuthUpdated that returns a Future that includes an option Ably exception
     **/
    public void onAuthUpdatedAsync(final String token, final Auth.AuthUpdateResult authUpdateResult) {
        final ConnectionWaiter waiter = new ConnectionWaiter();
        switch (currentState.state) {
            case connected:
                /* (RTC8a) If the connection is in the CONNECTED currentState and
                 * auth.authorize is called or Ably requests a re-authentication
                 * (see RTN22), the client must obtain a new token, then send an
                 * AUTH ProtocolMessage to Ably with an auth attribute
                 * containing an AuthDetails object with the token string. */
                try {
                    ProtocolMessage msg = new ProtocolMessage(ProtocolMessage.Action.auth);
                    msg.auth = new ProtocolMessage.AuthDetails(token);
                    send(msg, false, null);
                } catch (AblyException e) {
                    /* The send failed. Close the transport; if a subsequent
                     * reconnect succeeds, it will be with the new token. */
                    Log.v(TAG, "onAuthUpdated: closing transport after send failure");
                    transport.close();
                }
                break;

            case connecting:
                /* Close the connecting transport. */
                Log.v(TAG, "onAuthUpdated: closing connecting transport");
                ErrorInfo disconnectError = new ErrorInfo("Aborting incomplete connection with superseded auth params", 503, 80003);
                requestState(new StateIndication(ConnectionState.disconnected, disconnectError, null, null));
                /* Start a new connection attempt. */
                connect();
                break;

            default:
                /* Start a new connection attempt. */
                connect();
                break;
        }

        /* Wait for a currentState transition into anything other than connecting or
         * disconnected in a background thread */
        singleThreadExecutor.execute(() -> {
            boolean waitingForConnected = true;
            while (waitingForConnected) {
                final ErrorInfo reason = waiter.waitForChange();
                final ConnectionState connectionState = currentState.state;
                switch (connectionState) {
                    case connected:
                        authUpdateResult.onUpdate(true, null);
                        Log.v(TAG, "onAuthUpdated: got connected");
                        waitingForConnected = false;
                        break;

                    case connecting:
                    case disconnected:
                        Log.v(TAG, "onAuthUpdated: " + connectionState);
                        break;

                    default:
                        /* suspended/closed/error: throw the error. */
                        Log.v(TAG, "onAuthUpdated: throwing exception");
                        authUpdateResult.onUpdate(false, reason);
                        waitingForConnected = false;
                }
            }
            waiter.close();
        });

    }

    /**
     * Called when where was an error during authentication attempt
     *
     * @param errorInfo Error associated with unsuccessful authentication
     */
    public void onAuthError(ErrorInfo errorInfo) {
        Log.i(TAG, String.format(Locale.ROOT, "onAuthError: (%d) %s", errorInfo.code, errorInfo.message));

        if(errorInfo.statusCode == 403) {
            ConnectionStateChange failedStateChange =
                new ConnectionStateChange(
                    connection.state,
                    ConnectionState.failed,
                    0,
                    errorInfo);

            this.connection.onConnectionStateChange(failedStateChange);
            return;
        }

        switch (currentState.state) {
            case connecting:
                ITransport transport = this.transport;
                if (transport != null)
                    /* request that the current transport is closed */
                    requestState(new StateIndication(ConnectionState.disconnected, errorInfo));
                break;

            case connected:
                /* stay connected but notify of authentication error */
                addAction(new UpdateAction(errorInfo));
                break;

            default:
                break;
        }
    }

    /***************************************
     * transport events/notifications
     ***************************************/

    /**
     * React on message from the transport
     * @param transport transport instance or null to bypass transport correctness check (for testing)
     * @param message
     * @throws AblyException
     */
    public void onMessage(ITransport transport, ProtocolMessage message) throws AblyException {
        if (transport != null && this.transport != transport) {
            return;
        }
        // Check the logging level to avoid performance hit associated with building the message
        if (Log.level <= Log.VERBOSE) {
            Log.v(TAG, "onMessage() (transport = " + transport + "): " + message.action + ": " + new String(ProtocolSerializer.writeJSON(message)));
        }
        try {
            if(protocolListener != null) {
                protocolListener.onRawMessageRecv(message);
            }
            switch(message.action) {
                case heartbeat:
                    onHeartbeat(message);
                    break;
                case error:
                    ErrorInfo reason = message.error;
                    if(reason == null) {
                        Log.e(TAG, "onMessage(): ERROR message received (no error detail)");
                    } else {
                        Log.e(TAG, "onMessage(): ERROR message received; message = " + reason.message + "; code = " + reason.code);
                    }

                    /* an error message may signify an error currentState in a channel, or in the connection */
                    if(message.channel != null) {
                        onChannelMessage(message);
                    } else {
                        onError(message);
                    }
                    break;
                case connected:
                    if (currentState.state == ConnectionState.closing) {
                        // Based on RTN12f, if a connected protocol message comes while in the closing state,
                        // send a close protocol message.
                        if (!trySendCloseProtocolMessage()) requestState(ConnectionState.closed);
                    } else {
                        onConnected(message);
                    }
                    break;
                case disconnect:
                case disconnected:
                    onDisconnected(message);
                    break;
                case closed:
                    onClosed(message);
                    break;
                case ack:
                    onAck(message);
                    break;
                case nack:
                    onNack(message);
                    break;
                case auth:
                    addAction(new ReauthAction());
                    break;
                case object:
                case object_sync:
                    if (liveObjectsPlugin != null) {
                        try {
                            liveObjectsPlugin.handle(message);
                        } catch (Throwable t) {
                            Log.e(TAG, "objectsPlugin threw while handling message", t);
                        }
                    }
                    break;
                default:
                    onChannelMessage(message);
            }
        }
        catch(Exception e) {
            // Prevent any non-AblyException to be thrown
            throw AblyException.fromThrowable(e);
        }
    }

    private void onChannelMessage(ProtocolMessage message) {
        channels.onMessage(message);
        connection.recoveryKey = connection.createRecoveryKey();
    }

    private synchronized void onConnected(ProtocolMessage message) {
        ably.options.recover = null; // RTN16k, explicitly setting null, so it won't be used for subsequent connection requests
        connection.reason = message.error;

        if (connection.id != null) { // there was a previous connection, so this is a resume and RTN15c applies
            Log.d(TAG, "There was a connection resume");
            if(message.connectionId.equals(connection.id)) { // RTN15c6 - resume success
                if(message.error == null) {
                    Log.d(TAG, "connection has reconnected and resumed successfully");
                } else {
                    Log.d(TAG, "connection resume success with non-fatal error: " + message.error.message);
                }
                addPendingMessagesToQueuedMessages(false);
            } else { // RTN15c7, RTN16d - resume     failure
                if (message.error != null) {
                    Log.d(TAG, "connection resume failed with error: " + message.error.message);
                } else { // This shouldn't happen but, putting it here for safety
                    Log.d(TAG, "connection resume failed without error" );
                }

                addPendingMessagesToQueuedMessages(true);
                channels.transferToChannelQueue(extractConnectionQueuePresenceMessages());
            }
        } else {
            msgSerial = 0;
        }

        connection.id = message.connectionId;

        ConnectionDetails connectionDetails = message.connectionDetails;
        /* Get any parameters from connectionDetails. */
        connection.key = connectionDetails.connectionKey; //RTN16d
        maxIdleInterval = connectionDetails.maxIdleInterval;
        connectionStateTtl = connectionDetails.connectionStateTtl;
        maxMessageSize = connectionDetails.maxMessageSize;
        siteCode = connectionDetails.siteCode; // CD2j
        objectsGCGracePeriod = connectionDetails.objectsGCGracePeriod;

        /* set the clientId resolved from token, if any */
        String clientId = connectionDetails.clientId;
        try {
            ably.auth.setClientId(clientId);
        } catch (AblyException e) {
            requestState(transport, new StateIndication(ConnectionState.failed, e.errorInfo));
            return;
        }

        connection.recoveryKey = connection.createRecoveryKey();

        /* indicated connected currentState */
        final StateIndication stateIndication = new StateIndication(ConnectionState.connected, message.error, null, null);
        requestState(stateIndication);
    }

    /*
    This method removes all messages in queuedMessages which has presence in them, moves them to a new
    list and returns them. We can't yet use Java 8's stream and predicates for this purpose as we support below
    Android v24.
    * */
    private synchronized List<QueuedMessage> extractConnectionQueuePresenceMessages() {
        final Iterator<QueuedMessage> queuedIterator = queuedMessages.iterator();
        final List<QueuedMessage> queuedPresenceMessages = new ArrayList<>();
        while (queuedIterator.hasNext()){
            final QueuedMessage queuedMessage = queuedIterator.next();
            if (queuedMessage.msg.presence != null){
                queuedPresenceMessages.add(queuedMessage);
                queuedIterator.remove();
            }
        }
        return queuedPresenceMessages;
    }

    /**
     * Add all pending queued messages to the front of QueuedMessages for them to be sent later
     * Spec: RTN19a, RTN19a1, RTN19a2
     * @param resetMessageSerial whether to reset message serial, this will determine whether to reset message serials
     * on pending queue, for example when a connection resume failed
     */
    private void addPendingMessagesToQueuedMessages(boolean resetMessageSerial) {
        synchronized (this) {
            List<QueuedMessage> allPendingMessages = pendingMessages.popAll();

            if (resetMessageSerial){  // failed resume, so all new published messages start with msgSerial = 0
                msgSerial = 0; //msgSerial will increase in sendImpl when messages are sent, RTN15c7
            } else if (!allPendingMessages.isEmpty()) { // pendingMessages needs to expect next msgSerial to be the earliest previously unacknowledged message
                msgSerial = allPendingMessages.get(0).msg.msgSerial;
            }

            // Add messages from pending messages to front of queuedMessages in order to retry them
            queuedMessages.addAll(0, allPendingMessages);
        }
    }

    public List<QueuedMessage> getPendingMessages() {
        return pendingMessages.queue;
    }

    private synchronized void onDisconnected(ProtocolMessage message) {
        ErrorInfo reason = message.error;
        if(reason != null && isTokenError(reason)) {
            ably.auth.onAuthError(reason);
        }
        requestState(new StateIndication(ConnectionState.disconnected, reason));
    }

    private synchronized void onClosed(ProtocolMessage message) {
        if(message.error != null) {
            this.onError(message);
        } else {
            connection.key = null;
            requestState(new StateIndication(ConnectionState.closed, null));
        }
    }

    private synchronized void onError(ProtocolMessage message) {
        connection.key = null;
        ErrorInfo reason = message.error;
        if(isTokenError(reason)) {
            ably.auth.onAuthError(reason);
        }
        ConnectionState destinationState = isFatalError(reason) ? ConnectionState.failed : ConnectionState.disconnected;
        requestState(transport, new StateIndication(destinationState, reason));
    }

    private void onAck(ProtocolMessage message) {
        pendingMessages.ack(message.msgSerial, message.count, message.res, message.error);
    }

    private void onNack(ProtocolMessage message) {
        pendingMessages.nack(message.msgSerial, message.count, message.error);
    }

    private void onHeartbeat(ProtocolMessage message) {
        synchronized(heartbeatWaiters) {
            heartbeatWaiters.clear();
            heartbeatWaiters.notifyAll();
        }
    }

    /******************************
     * ConnectionManager lifecycle
     ******************************/

    private synchronized void startup() throws InterruptedException {
        while (cleaningUpAfterEnteringTerminalState) {
            Log.v(TAG, "Waiting for termination action to clean up handler thread");
            wait();
        }

        if (handlerThread == null) {
            (handlerThread = new Thread(new ActionHandler())).start();
            startConnectivityListener();
        } else {
            Log.v(TAG, "`connect()` has been called twice on uninitialized or terminal state");
        }
    }

    private boolean checkConnectionStale() {
        if(lastActivity == 0) {
            return false;
        }
        long now = clock.currentTimeMillis();
        long intervalSinceLastActivity = now - lastActivity;
        if(intervalSinceLastActivity > (maxIdleInterval + connectionStateTtl)) {
            /* RTN15g1, RTN15g2 Force a new connection if the previous one is stale;
             * Clearing connection.key will ensure that we don't attempt to resume;
             * leaving the original connection.id will mean that we notice at
             * connection time that the connectionId has changed */
            if(connection.key != null) {
                Log.v(TAG, "Clearing stale connection key to suppress resume");
                connection.key = null;
                connection.recoveryKey = null;
            }
            return true;
        }
        return false;
    }

    private synchronized void setSuspendTime() {
        suspendTime = (clock.currentTimeMillis() + connectionStateTtl);
    }

    private boolean shouldTryFallback(TransportParams params, ErrorInfo reason) {
        return params != null && (reason == null || reason.statusCode >= 500);
    }

    /**
     * After a connection attempt failed, check to
     * see whether we should attempt to use a fallback.
     * @param reason
     * @return StateIndication if a fallback connection attempt is required, otherwise null
     */
    private StateIndication checkFallback(ErrorInfo reason) {
        // checkConnectivity is blocking http call, try to avoid it if we don't need to try fallback
        TransportParams pendingConnectionParams = pendingConnect;
        boolean internetIsUp = shouldTryFallback(pendingConnectionParams, reason) && checkConnectivity();
        if (internetIsUp) {
            /* we will try a fallback host */
            String hostFallback = hosts.getFallback(pendingConnectionParams.host);
            if (hostFallback != null && pendingConnectionParams == pendingConnect) {
                Log.v(TAG, "checkFallback: fallback to " + hostFallback);
                return new StateIndication(ConnectionState.connecting, null, hostFallback, pendingConnectionParams.host);
            }
        }
        pendingConnect = null;
        return null;
    }

    private synchronized StateIndication checkSuspended(ErrorInfo reason) {
        long currentTime = clock.currentTimeMillis();
        long timeToSuspend = suspendTime - currentTime;
        boolean suspendMode = timeToSuspend <= 0;
        Log.v(TAG, "checkSuspended: timeToSuspend = " + timeToSuspend + "ms; suspendMode = " + suspendMode);
        ConnectionState expiredState = suspendMode ? ConnectionState.suspended : ConnectionState.disconnected;
        return new StateIndication(expiredState, reason);
    }

    private void tryWait(long timeout) {
        try {
            if(timeout == 0) {
                wait();
            } else {
                clock.waitOn(this, timeout);
            }
        } catch (InterruptedException e) {}
    }

    private void handleReauth() {
        if (currentState.state == ConnectionState.connected) {
            Log.v(TAG, "Server initiated reauth");

            ErrorInfo errorInfo = null;

            /*
             * It is a server initiated reauth, it is issued while previous token is still valid for ~30 seconds,
             * we have to clear cached token and get a new one
             */
            try {
                ably.auth.renew();
            } catch (AblyException e) {
                errorInfo = e.errorInfo;
            }

            /* report connection currentState in UPDATE event */
            if (currentState.state == ConnectionState.connected) {
                connection.emitUpdate(errorInfo);
            }
        }
    }

    @Override
    public synchronized void onTransportAvailable(ITransport transport) {
        if (this.transport != transport) {
            /* This is from a transport that we have already abandoned. */
            Log.v(TAG, "onTransportAvailable: ignoring connection event from superseded transport");
            return;
        }
        if(protocolListener != null) {
            protocolListener.onRawConnect(transport.getURL());
        }
    }

    @Override
    public synchronized void onTransportUnavailable(ITransport transport, ErrorInfo reason) {
        Log.v(TAG, "onTransportUnavailable()");
        if (this.transport != transport) {
            /* This is from a transport that we have already abandoned. */
            Log.v(TAG, "onTransportUnavailable: ignoring disconnection event from superseded transport");
            return;
        }

        // If we're currently connected, start the suspend timer
        if (currentState.state == ConnectionState.connected) {
            setSuspendTime();
        }

        // Do not fallback for closing
        if (currentState.state == ConnectionState.closing) {
            requestState(ConnectionState.closed);
            return;
        }

        /* if this is a failure of a pending connection attempt, decide whether or not to attempt a fallback host */
        StateIndication fallbackAttempt = checkFallback(reason);
        if(fallbackAttempt != null) {
            requestState(fallbackAttempt);
            return;
        }

        StateIndication stateIndication = null;
        if(reason != null) {
            if(isFatalError(reason)) {
                Log.e(TAG, "onTransportUnavailable: unexpected transport error: " + reason.message);
                stateIndication = new StateIndication(ConnectionState.failed, reason);
            } else if(isTokenError(reason)) {
                ably.auth.onAuthError(reason);
            }
        }
        if(stateIndication == null) {
            stateIndication = checkSuspended(reason);
        }
        addAction(new SynchronousStateChangeAction(transport, stateIndication));
    }

    private class ConnectParams extends TransportParams {
        ConnectParams(ClientOptions options, PlatformAgentProvider platformAgentProvider) {
            super(options, platformAgentProvider);
            this.connectionKey = connection.key;
            this.port = Defaults.getPort(options);
        }
    }

    private void connectImpl(StateIndication request) {
        /* determine the parameters of this connection attempt, and
         * instance the transport.
         * First, choose the transport. (Right now there's only one.)
         * Second, choose the host. ConnectParams will use the default
         * (or requested) host, unless fallback!=null, in which case
         * checkSuspend has already chosen a fallback host at random */

        String host = request.fallback;
        if (host == null) {
            host = hosts.getPreferredHost();
        }
        checkConnectionStale();
        pendingConnect = new ConnectParams(ably.options, platformAgentProvider);
        pendingConnect.host = host;
        lastUsedHost = host;

        /* try the connection */
        ITransport transport;
        try {
            transport = transportFactory.getTransport(pendingConnect, this);
        } catch(Exception e) {
            String msg = "Unable to instance transport class";
            Log.e(getClass().getName(), msg, e);
            throw new RuntimeException(msg, e);
        }
        ITransport oldTransport;
        synchronized(this) {
            oldTransport = this.transport;
            this.transport = transport;
        }
        if (oldTransport != null) {
            oldTransport.close();
        }

        transport.connect(this);
        if(protocolListener != null) {
            protocolListener.onRawConnectRequested(transport.getURL());
        }
    }

    /**
     * (RTN11d)
     */
    private void cleanMsgSerialAndErrorReason() {
        this.msgSerial = 0;
        this.connection.reason = null;
    }

    /**
     * (RTN11d)
     */
    private void reinitializeChannelsAfterReconnect() {
        for (final Channel channel : channels.values()) {
            // (RTN11b)
            if (channel.state == ChannelState.attached || channel.state == ChannelState.attaching) {
                channel.setConnectionClosed(REASON_CLOSED);
            }

            // (RTN11d)
            channel.setReinitialized();
        }
    }

    private boolean hasConnectBeenInvokeOnClosedOrFailedState(ConnectionStateChange change) {
        return change.previous == ConnectionState.failed
            || change.previous == ConnectionState.closed
            || change.previous == ConnectionState.closing;
    }

    /**
     * Close any existing transport
     * @param shouldAwaitConnection true if `CONNECTING` state, moves immediately to `CLOSING`
     * @return closed if true, otherwise awaiting closed indication
     */
    private boolean closeImpl(boolean shouldAwaitConnection) {
        if (transport == null) {
            return true;
        }

        // Based on RTN12f we need to wait until connected protocol message come
        if (shouldAwaitConnection) {
            return false;
        }

        return !trySendCloseProtocolMessage();
    }

    /**
     * @return true if we successfully send `close` protocol message, false otherwise
     */
    private boolean trySendCloseProtocolMessage() {
        try {
            Log.v(TAG, "Requesting connection close");
            transport.send(new ProtocolMessage(ProtocolMessage.Action.close));
            return true;
        } catch (AblyException e) {
            /* we're closing, and the attempt to send the CLOSE message failed;
             * continue, because we're not going to reinstate the transport
             * just to send a CLOSE message */
            Log.v(TAG, "Closing incomplete transport");
            clearTransport();
            return false;
        }
    }

    private void clearTransport() {
        if(transport != null) {
            transport.close();
            transport = null;
        }
    }

    /**
     * Determine whether or not the client has connection to the network
     * without reference to a specific ably host. This is to determine whether
     * it is better to try a fallback host, or keep retrying with the default
     * host.
     * @return boolean, true if network is available
     */
    protected boolean checkConnectivity() {
        try {
            return HttpHelpers.getUrlString(ably.httpCore, INTERNET_CHECK_URL).contains(INTERNET_CHECK_OK);
        } catch(AblyException e) {
            Log.d(TAG, "Exception whilst checking connectivity", e);
            return false;
        }
    }

    protected void setLastActivity(long lastActivityTime) {
        this.lastActivity = lastActivityTime;
    }

    /******************
     * event queueing
     ******************/

    public static class QueuedMessage {
        public final ProtocolMessage msg;
        public final Callback<PublishResult> listener;
        public QueuedMessage(ProtocolMessage msg, Callback<PublishResult> listener) {
            this.msg = msg;
            this.listener = listener;
        }
    }

    public void send(ProtocolMessage msg, boolean queueEvents, Callback<PublishResult> listener) throws AblyException {
        State state;
        synchronized(this) {
            state = this.currentState;
            if(state.sendEvents) {
                sendImpl(msg, listener);
                return;
            }
            if(state.queueEvents && queueEvents) {
                queuedMessages.add(new QueuedMessage(msg, listener));
                return;
            }
        }
        throw AblyException.fromErrorInfo(state.defaultErrorInfo);
    }

    private void sendImpl(ProtocolMessage message, Callback<PublishResult> listener) throws AblyException {
        if(transport == null) {
            Log.v(TAG, "sendImpl(): Discarding message; transport unavailable");
            return;
        }
        if(ProtocolMessage.ackRequired(message)) {
            message.msgSerial = msgSerial++;
            pendingMessages.push(new QueuedMessage(message, listener));
        }
        if(protocolListener != null) {
            protocolListener.onRawMessageSend(message);
        }
        transport.send(message);
    }

    private void sendImpl(QueuedMessage msg) throws AblyException {
        if(transport == null) {
            Log.v(TAG, "sendImpl(): Discarding message; transport unavailable");
            return;
        }
        ProtocolMessage message = msg.msg;
        if(ProtocolMessage.ackRequired(message)) {
            message.msgSerial = msgSerial++;
            pendingMessages.push(msg);
        }
        if(protocolListener != null) {
            protocolListener.onRawMessageSend(message);
        }
        transport.send(message);
    }

    private void sendQueuedMessages() {
        synchronized(this) {
            while(!queuedMessages.isEmpty()) {
                try {
                    QueuedMessage message = queuedMessages.get(0);
                    // Do not send attach message from queued messages to prevent duplication
                    // (we always send attach on connect event)
                    if (message.msg.action != ProtocolMessage.Action.attach) {
                        sendImpl(message);
                    }
                } catch (AblyException e) {
                    Log.e(TAG, "sendQueuedMessages(): Unexpected error sending queued messages", e);
                } finally {
                    queuedMessages.remove(0);
                }
            }
        }
    }

    private void failQueuedMessages(ErrorInfo reason) {
        synchronized(this) {
            for (QueuedMessage queued: queuedMessages) {
                if (queued.listener != null) {
                    try {
                        queued.listener.onError(reason);
                    } catch (Throwable t) {
                        Log.e(TAG, "failQueuedMessages(): Unexpected error calling listener", t);
                    }
                }
            }
            queuedMessages.clear();

            //also pending messages
            pendingMessages.fail(reason);
        }
    }

    /**
     * A class containing a queue of messages awaiting acknowledgement
     */
    private static class PendingMessageQueue {
        private final List<QueuedMessage> queue = new ArrayList<>();

        public synchronized void push(QueuedMessage msg) {
            queue.add(msg);
        }

        public void ack(long msgSerial, int count, @Nullable PublishResult[] results, ErrorInfo reason) {
            QueuedMessage[] ackMessages = null, nackMessages = null;
            synchronized(this) {
                if (queue.isEmpty()) return;
                long startSerial = queue.get(0).msg.msgSerial;
                if(msgSerial < startSerial) {
                    /* this is an error condition and shouldn't happen but
                     * we can handle it gracefully by only processing the
                     * relevant portion of the response */
                    count -= (int)(startSerial - msgSerial);
                    if(count < 0)
                        count = 0;
                    msgSerial = startSerial;
                }
                if(msgSerial > startSerial) {
                    /* this counts as a nack of the messages earlier than serial,
                     * as well as an ack */
                    int nCount = (int)(msgSerial - startSerial);
                    List<QueuedMessage> nackList = queue.subList(0, nCount);
                    nackMessages = nackList.toArray(new QueuedMessage[nCount]);
                    nackList.clear();
                    startSerial = msgSerial;
                }
                if(msgSerial == startSerial) {
                    List<QueuedMessage> ackList = queue.subList(0, count);
                    ackMessages = ackList.toArray(new QueuedMessage[count]);
                    ackList.clear();
                }
            }
            if(nackMessages != null) {
                if(reason == null)
                    reason = new ErrorInfo("Unknown error", 500, 50000);
                for(QueuedMessage msg : nackMessages) {
                    try {
                        if(msg.listener != null)
                            msg.listener.onError(reason);
                    } catch(Throwable t) {
                        Log.e(TAG, "ack(): listener exception", t);
                    }
                }
            }
            if(ackMessages != null) {
                for (int i = 0; i < ackMessages.length; i++) {
                    QueuedMessage msg = ackMessages[i];
                    try {
                        if (msg.listener != null) {
                            PublishResult messageResult = results != null && results.length > i ? results[i] : null;
                            msg.listener.onSuccess(messageResult);
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "ack(): listener exception", t);
                    }
                }
            }
        }

        public synchronized void nack(long serial, int count, ErrorInfo reason) {
            QueuedMessage[] nackMessages = null;
            synchronized(this) {
                if (queue.isEmpty()) return;
                long startSerial = queue.get(0).msg.msgSerial;
                if(serial != startSerial) {
                    /* this is an error condition and shouldn't happen but
                     * we can handle it gracefully by only processing the
                     * relevant portion of the response */
                    count -= (int)(startSerial - serial);
                    serial = startSerial;
                }
                List<QueuedMessage> nackList = queue.subList(0, count);
                nackMessages = nackList.toArray(new QueuedMessage[count]);
                nackList.clear();
                startSerial += count;
            }
            if(nackMessages != null) {
                if(reason == null)
                    reason = new ErrorInfo("Unknown error", 500, 50000);
                for(QueuedMessage msg : nackMessages) {
                    try {
                        if(msg.listener != null)
                            msg.listener.onError(reason);
                    } catch(Throwable t) {
                        Log.e(TAG, "nack(): listener exception", t);
                    }
                }
            }
        }

        /**
         * @return all pending queued messages and clear the queue
         */
        synchronized List<QueuedMessage> popAll() {
            List<QueuedMessage> allPendingMessages = new ArrayList<>(queue);
            queue.clear();
            return allPendingMessages;
        }

        //fail all pending queued messages
        synchronized void fail(ErrorInfo reason) {
            for (QueuedMessage queuedMessage: queue){
                if (queuedMessage.listener != null) {
                    queuedMessage.listener.onError(reason);
                }
            }
            queue.clear();
        }
    }

    /***********************
     * Network connectivity
     **********************/

    private class CMConnectivityListener implements NetworkConnectivityListener {

        @Override
        public void onNetworkAvailable() {
            ConnectionManager cm = ConnectionManager.this;
            ConnectionState currentState = cm.getConnectionState().state;
            Log.i(TAG, "onNetworkAvailable(): currentState = " + currentState.name());
            if(currentState == ConnectionState.disconnected || currentState == ConnectionState.suspended) {
                Log.i(TAG, "onNetworkAvailable(): initiating reconnect");
                cm.connect();
            }
        }

        @Override
        public void onNetworkUnavailable(ErrorInfo reason) {
            ConnectionManager cm = ConnectionManager.this;
            ConnectionState currentState = cm.getConnectionState().state;
            Log.i(TAG, "onNetworkUnavailable(); currentState = " + currentState.name() + "; reason = " + reason.toString());
            if(currentState == ConnectionState.connected || currentState == ConnectionState.connecting) {
                Log.i(TAG, "onNetworkUnavailable(): closing connected transport");
                cm.requestState(new StateIndication(ConnectionState.disconnected, reason));
            }
        }
    }

    private void startConnectivityListener() {
        connectivityListener = new CMConnectivityListener();
        ably.platform.getNetworkConnectivity().addListener(connectivityListener);
    }

    private void stopConnectivityListener() {
        ably.platform.getNetworkConnectivity().removeListener(connectivityListener);
        connectivityListener = null;
    }

    /*******************
     * for tests only
     ******************/

    void disconnectAndSuppressRetries() {
        if(transport != null) {
            transport.close();
        }
        suppressRetry = true;
    }

    /*******************
     * misc error handling
     ******************/

    private boolean isTokenError(ErrorInfo err) {
        return ((err.code >= 40140) && (err.code < 40150)) || (err.code == 80019 && err.statusCode == 401);
    }

    private boolean isFatalError(ErrorInfo err) {
        if(err.code != 0) {
            /* token errors are assumed to be recoverable */
            if(isTokenError(err)) { return false; }
            /* 400 codes assumed to be fatal */
            if((err.code >= 40000) && (err.code < 50000)) { return true; }
        }
        /* otherwise, use statusCode */
        if(err.statusCode != 0 && err.statusCode < 500) { return true; }
        return false;
    }

    /*******************
     * private members
     ******************/

    final AblyRealtime ably;
    private final Clock clock;
    private final Channels channels;
    private final Connection connection;
    private final ITransport.Factory transportFactory;
    private final List<QueuedMessage> queuedMessages = new ArrayList<>();
    private final PendingMessageQueue pendingMessages = new PendingMessageQueue();
    private final HashSet<Object> heartbeatWaiters = new HashSet<Object>();
    private final ActionQueue actionQueue = new ActionQueue();
    private final Hosts hosts;
    private final PlatformAgentProvider platformAgentProvider;

    private Thread handlerThread;
    private final Map<ConnectionState, State> states = new HashMap<>();
    private State currentState;
    private ErrorInfo stateError;
    private volatile ConnectParams pendingConnect;
    private boolean suppressRetry; /* for tests only; modified via reflection */
    private volatile ITransport transport;
    private long suspendTime;
    public long msgSerial;
    private long lastActivity;
    private CMConnectivityListener connectivityListener;
    private long connectionStateTtl = Defaults.connectionStateTtl;
    public int maxMessageSize = Defaults.maxMessageSize;
    public String siteCode; // CD2j
    long maxIdleInterval = Defaults.maxIdleInterval;
    private int disconnectedRetryAttempt = 0;

    /* for debug/test only */
    private final RawProtocolListener protocolListener;
    private String lastUsedHost;

    private static final long HEARTBEAT_TIMEOUT = 5000L;
}
