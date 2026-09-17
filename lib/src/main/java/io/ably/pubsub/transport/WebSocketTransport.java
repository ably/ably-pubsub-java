package io.ably.pubsub.transport;

import io.ably.pubsub.debug.DebugOptions;
import io.ably.pubsub.http.HttpUtils;
import io.ably.pubsub.network.EngineType;
import io.ably.pubsub.network.NotConnectedException;
import io.ably.pubsub.network.WebSocketClient;
import io.ably.pubsub.network.WebSocketEngine;
import io.ably.pubsub.network.WebSocketEngineConfig;
import io.ably.pubsub.network.WebSocketEngineFactory;
import io.ably.pubsub.network.WebSocketListener;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.types.ProtocolMessage;
import io.ably.pubsub.types.ProtocolSerializer;
import io.ably.pubsub.util.Clock;
import io.ably.pubsub.util.ClientOptionsUtils;
import io.ably.pubsub.util.Log;
import io.ably.pubsub.util.AblyTimer;
import io.ably.pubsub.util.SystemClock;
import io.ably.pubsub.util.TimerInstance;

import javax.net.ssl.SSLContext;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.TimerTask;

public class WebSocketTransport implements ITransport {

    private static final String TAG = WebSocketTransport.class.getName();
    private static final int NEVER_CONNECTED = -1;
    private static final int BUGGYCLOSE = -2;
    private static final int CLOSE_NORMAL = 1000;
    private static final int GOING_AWAY = 1001;
    private static final int CLOSE_PROTOCOL_ERROR = 1002;
    private static final int REFUSE = 1003;
    /*  private static final int UNUSED               = 1004; */
    /*  private static final int NOCODE               = 1005; */
    private static final int ABNORMAL_CLOSE = 1006;
    private static final int NO_UTF8 = 1007;
    private static final int POLICY_VALIDATION = 1008;
    private static final int TOOBIG = 1009;
    private static final int EXTENSION = 1010;
    private static final int UNEXPECTED_CONDITION = 1011;
    private static final int TLS_ERROR = 1015;
    /******************
     * private members
     ******************/

    private final TransportParams params;
    private final ConnectionManager connectionManager;
    private final Clock clock;
    private final boolean channelBinaryMode;
    private String wsUri;
    private ConnectListener connectListener;
    private WebSocketClient webSocketClient;
    private final WebSocketEngine webSocketEngine;
    private WebSocketHandler webSocketHandler;
    private boolean activityCheckTurnedOff = false;

    private boolean connectHasBeenCalled = false;

    /******************
     * protected constructor
     ******************/
    protected WebSocketTransport(TransportParams params, ConnectionManager connectionManager) {
        this.params = params;
        this.connectionManager = connectionManager;
        this.clock = SystemClock.clockFrom(params.options);
        this.channelBinaryMode = params.options.useBinaryProtocol;
        this.webSocketEngine = createWebSocketEngine(params);
        params.heartbeats = !this.webSocketEngine.isPingListenerSupported();
    }

    private static WebSocketEngine createWebSocketEngine(TransportParams params) {
        WebSocketEngineFactory engineFactory;
        if (params.options instanceof DebugOptions && ((DebugOptions) params.options).webSocketEngineFactory != null) {
            engineFactory = ((DebugOptions) params.options).webSocketEngineFactory;
        } else {
            engineFactory = WebSocketEngineFactory.getFirstAvailable();
        }
        Log.v(TAG, String.format("Using %s WebSocket Engine", engineFactory.getEngineType().name()));
        WebSocketEngineConfig.WebSocketEngineConfigBuilder configBuilder = WebSocketEngineConfig.builder();
        configBuilder
            .tls(params.options.tls)
            .host(params.host)
            .proxy(ClientOptionsUtils.convertToProxyConfig(params.getClientOptions()));

        // OkHttp supports modern TLS algorithms by default
        if (params.options.tls && engineFactory.getEngineType() != EngineType.OKHTTP) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, null, null);
                SafeSSLSocketFactory factory = new SafeSSLSocketFactory(sslContext.getSocketFactory());
                configBuilder.sslSocketFactory(factory);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new IllegalStateException("Can't get safe tls algorithms", e);
            }
        }

        return engineFactory.create(configBuilder.build());
    }

    /******************
     * ITransport methods
     ******************/

    /**
     * Connect is called once when we create transport;
     * after transport is closed, we never call `connect` again
     */
    @Override
    public void connect(ConnectListener connectListener) {
        ensureConnectCalledOnce();
        this.connectListener = connectListener;
        try {
            boolean isTls = params.options.tls;
            String wsScheme = isTls ? "wss://" : "ws://";
            wsUri = wsScheme + params.host + ':' + params.port + "/";
            Param[] authParams = connectionManager.getAuthParams();
            Param[] connectParams = params.getConnectParams(authParams);
            if (connectParams.length > 0)
                wsUri = HttpUtils.encodeParams(wsUri, connectParams);

            Log.d(TAG, "connect(); wsUri = " + wsUri);
            webSocketHandler = new WebSocketHandler(this::receive);
            webSocketClient = this.webSocketEngine.create(wsUri, webSocketHandler);
            webSocketClient.connect();
        } catch (AblyException e) {
            Log.e(TAG, "Unexpected exception attempting connection; wsUri = " + wsUri, e);
            connectListener.onTransportUnavailable(this, e.errorInfo);
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected exception attempting connection; wsUri = " + wsUri, t);
            connectListener.onTransportUnavailable(this, AblyException.fromThrowable(t).errorInfo);
        }
    }

    /**
     * `connect()` can't be called more than once
     */
    private synchronized void ensureConnectCalledOnce() {
        if (connectHasBeenCalled) throw new IllegalStateException("WebSocketTransport is already initialized");
        connectHasBeenCalled = true;
    }

    @Override
    public void close() {
        Log.d(TAG, "close()");
        // Take local snapshots of the shared references. Callback threads (e.g., onClose)
        // may concurrently set these fields to null.
        //
        // Intentionally avoid synchronizing here:
        // - The WebSocket library may invoke our WebSocketHandler while holding its own
        //   internal locks.
        // - If close() also acquired a lock on WebSocketTransport, we could invert the
        //   lock order and create a circular wait (deadlock): close() waits for the WS
        //   library to release its lock, while the WS library waits for a lock on
        //   WebSocketTransport.
        final WebSocketClient client = webSocketClient;
        final WebSocketHandler handler = webSocketHandler;
        if (client != null && handler != null) {
            // Record activity so the activity timer remains armed. If a graceful close
            // stalls, the timer can detect inactivity and force-cancel the socket.
            handler.flagActivity();
            client.close();
        } else {
            Log.w(TAG, "close() called on uninitialized or already closed transport");
        }
    }

    @Override
    public void receive(ProtocolMessage msg) throws AblyException {
        connectionManager.onMessage(this, msg);
    }

    @Override
    public void send(ProtocolMessage msg) throws AblyException {
        Log.d(TAG, "send(); action = " + msg.action);
        try {
            if (channelBinaryMode) {
                byte[] encodedMsg = ProtocolSerializer.writeMsgpack(msg);

                // Check the logging level to avoid performance hit associated with building the message
                if (Log.level <= Log.VERBOSE) {
                    ProtocolMessage decodedMsg = ProtocolSerializer.readMsgpack(encodedMsg);
                    Log.v(TAG, "send(): " + decodedMsg.action + ": " + new String(ProtocolSerializer.writeJSON(decodedMsg)));
                }
                webSocketClient.send(encodedMsg);
            } else {
                // Check the logging level to avoid performance hit associated with building the message
                if (Log.level <= Log.VERBOSE)
                    Log.v(TAG, "send(): " + new String(ProtocolSerializer.writeJSON(msg)));
                webSocketClient.send(ProtocolSerializer.writeJSON(msg));
            }
        } catch (NotConnectedException e) {
            if (connectListener != null) {
                connectListener.onTransportUnavailable(this, AblyException.fromThrowable(e).errorInfo);
            } else
                throw AblyException.fromThrowable(e);
        } catch (Exception e) {
            throw AblyException.fromThrowable(e);
        }
    }

    @Override
    public String getHost() {
        return params.host;
    }

    protected void preProcessReceivedMessage(ProtocolMessage message) {
        //Gives the chance to child classes to do message pre-processing
    }

    /**
     * Visible For Testing
     * </p>
     * We need to turn off activity check for some tests (e.g. io.ably.pubsub.test.realtime.RealtimeConnectFailTest.disconnect_retry_channel_timeout_jitter_after_consistent_detach[binary_protocol])
     * Those tests expects that activity checks are passing, but protocol messages are not coming
     */
    protected void turnOffActivityCheckIfPingListenerIsNotSupported() {
        if (!webSocketEngine.isPingListenerSupported()) activityCheckTurnedOff = true;
    }

    public String toString() {
        return WebSocketTransport.class.getName() + " {" + getURL() + "}";
    }

    public String getURL() {
        return wsUri;
    }

    private boolean isActiveTransport() {
        return connectionManager.isActiveTransport(this);
    }

    //interface to transfer Protocol message from websocket
    interface WebSocketReceiver {
        void onMessage(ProtocolMessage protocolMessage) throws AblyException;
    }

    /******************
     * public factory API
     ******************/

    public static class Factory implements ITransport.Factory {
        @Override
        public WebSocketTransport getTransport(TransportParams params, ConnectionManager connectionManager) {
            return new WebSocketTransport(params, connectionManager);
        }
    }

    /**************************
     * WebSocketHandler methods
     **************************/

    class WebSocketHandler implements WebSocketListener {
        private final WebSocketReceiver receiver;
        /***************************
         * WsClient private members
         ***************************/

        private final AblyTimer timer = clock.newTimer("activity-timer");
        private volatile TimerInstance activityTimerHandle = null;
        private volatile long lastActivityTime;

        /**
         * Monitor for activity timer events
         */
        private final Object activityTimerMonitor = new Object();

        WebSocketHandler(WebSocketReceiver receiver) {
            this.receiver = receiver;
        }

        @Override
        public void onOpen() {
            Log.d(TAG, "onOpen()");
            connectListener.onTransportAvailable(WebSocketTransport.this);
            flagActivity();
        }

        @Override
        public void onMessage(ByteBuffer blob) {
            try {
                ProtocolMessage msg = ProtocolSerializer.readMsgpack(blob.array());
                Log.d(TAG, "onMessage(): msg (binary) = " + msg);
                WebSocketTransport.this.preProcessReceivedMessage(msg);
                receiver.onMessage(msg);
            } catch (AblyException e) {
                String msg = "Unexpected exception processing received binary message";
                Log.e(TAG, msg, e);
            }
            flagActivity();
        }

        @Override
        public void onMessage(String string) {
            try {
                ProtocolMessage msg = ProtocolSerializer.fromJSON(string);
                Log.d(TAG, "onMessage(): msg (text) = " + msg);
                WebSocketTransport.this.preProcessReceivedMessage(msg);
                receiver.onMessage(msg);
            } catch (AblyException e) {
                String msg = "Unexpected exception processing received text message";
                Log.e(TAG, msg, e);
            }
            flagActivity();
        }

        /* This allows us to detect a websocket ping, so we don't need Ably pings. */
        @Override
        public void onWebsocketPing() {
            Log.d(TAG, "onWebsocketPing()");
            flagActivity();
        }

        @Override
        public void onClose(final int wsCode, final String wsReason) {
            Log.d(TAG, "onClose(): wsCode = " + wsCode + "; wsReason = " + wsReason + "; remote = " + false);

            ErrorInfo reason;
            switch (wsCode) {
                case NEVER_CONNECTED:
                case CLOSE_NORMAL:
                case BUGGYCLOSE:
                case GOING_AWAY:
                case ABNORMAL_CLOSE:
                    /* we don't know the specific reason that the connection closed in these cases,
                     * but we have to assume it's a problem with connectivity rather than some other
                     * application problem */
                    reason = ConnectionManager.REASON_DISCONNECTED;
                    break;
                case REFUSE:
                case POLICY_VALIDATION:
                    reason = ConnectionManager.REASON_REFUSED;
                    break;
                case TOOBIG:
                    reason = ConnectionManager.REASON_TOO_BIG;
                    break;
                case NO_UTF8:
                case CLOSE_PROTOCOL_ERROR:
                case UNEXPECTED_CONDITION:
                case EXTENSION:
                case TLS_ERROR:
                default:
                    /* we don't know the specific reason that the connection closed in these cases,
                     * but we have to assume it's an application problem, and the problem will
                     * recur if we try again. The failed state means that we won't automatically
                     * try again. */
                    reason = ConnectionManager.REASON_FAILED;
                    break;
            }
            connectListener.onTransportUnavailable(WebSocketTransport.this, reason);
            dispose();
        }

        @Override
        public void onError(Throwable throwable) {
            Log.e(TAG, "Connection error ", throwable);
            connectListener.onTransportUnavailable(WebSocketTransport.this, new ErrorInfo(throwable.getMessage(), 503, 80000));
        }

        @Override
        public void onOldJavaVersionDetected(Throwable throwable) {
            Log.w(TAG, "Error when trying to set SSL parameters, most likely due to an old Java API version", throwable);
        }

        private void dispose() {
            timer.cancel();
        }

        private void flagActivity() {
            if (isActiveTransport()) {
                lastActivityTime = clock.currentTimeMillis();
                connectionManager.setLastActivity(lastActivityTime);
            }

            if (connectionManager.maxIdleInterval == 0) {
                Log.v(TAG, "checkActivity: turned off because maxIdleInterval is 0");
                return;
            }

            if (activityCheckTurnedOff) {
                Log.v(TAG, "checkActivity: turned off for test purpose");
                return;
            }

            checkActivity();
        }

        private void checkActivity() {
            long timeout = getActivityTimeout();

            if (timeout == 0) {
                Log.v(TAG, "checkActivity: infinite timeout");
                return;
            }

            // prevent going to the synchronized block if the timer is active
            if (activityTimerHandle != null) return;

            synchronized (activityTimerMonitor) {
                // Check if timer already running
                if (activityTimerHandle == null) {
                    // Start the activity timer task
                    startActivityTimer(timeout + 100);
                }
            }
        }

        private void startActivityTimer(long timeout) {
            TimerTask task = new TimerTask() {
                public void run() {
                    try {
                        onActivityTimerExpiry();
                    } catch (Exception exception) {
                        Log.e(TAG, "Unexpected exception in activity timer handler", exception);
                        webSocketClient.cancel(ABNORMAL_CLOSE, "Activity timer closed unexpectedly");
                    }
                }
            };
            activityTimerHandle = schedule(task, timeout);
        }

        private TimerInstance schedule(TimerTask task, long delay) {
            try {
                return timer.schedule(task, delay);
            } catch (IllegalStateException ise) {
                Log.w(TAG, "Timer has already has been canceled", ise);
                return () -> {};
            }
        }

        private void onActivityTimerExpiry() {
            long timeSinceLastActivity = clock.currentTimeMillis() - lastActivityTime;
            long timeRemaining = getActivityTimeout() - timeSinceLastActivity;

            // If we have no time remaining, then close the connection
            if (timeRemaining <= 0) {
                Log.e(TAG, "No activity for " + getActivityTimeout() + "ms, closing connection");
                webSocketClient.cancel(ABNORMAL_CLOSE, "timed out");
                return;
            }

            synchronized (activityTimerMonitor) {
                activityTimerHandle = null;
                // Otherwise, we've had some activity, restart the timer for the next timeout
                Log.v(TAG, "onActivityTimerExpiry: ok");
                startActivityTimer(timeRemaining + 100);
            }
        }

        private long getActivityTimeout() {
            return connectionManager.maxIdleInterval + params.options.realtimeRequestTimeout;
        }
    }

}
