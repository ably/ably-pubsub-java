package io.ably.pubsub.realtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import io.ably.pubsub.http.BasePaginatedQuery;
import io.ably.pubsub.http.Http;
import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.http.HttpUtils;
import io.ably.pubsub.liveobjects.RealtimeObject;
import io.ably.pubsub.liveobjects.LiveObjectsPlugin;
import io.ably.pubsub.http.MessageEditsMixin;
import io.ably.pubsub.http.HttpAnnotations;
import io.ably.pubsub.transport.ConnectionManager;
import io.ably.pubsub.transport.ConnectionManager.QueuedMessage;
import io.ably.pubsub.transport.Defaults;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.AsyncPaginatedResult;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.ChannelMode;
import io.ably.pubsub.types.ChannelOptions;
import io.ably.pubsub.types.ChannelProperties;
import io.ably.pubsub.types.DecodingContext;
import io.ably.pubsub.types.DeltaExtras;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.Message;
import io.ably.pubsub.types.MessageAction;
import io.ably.pubsub.types.MessageAnnotations;
import io.ably.pubsub.types.MessageDecodeException;
import io.ably.pubsub.types.MessageOperation;
import io.ably.pubsub.types.MessageSerializer;
import io.ably.pubsub.types.MessageVersion;
import io.ably.pubsub.types.PaginatedResult;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.types.PresenceMessage;
import io.ably.pubsub.types.ProtocolMessage;
import io.ably.pubsub.types.ProtocolMessage.Action;
import io.ably.pubsub.types.ProtocolMessage.Flag;
import io.ably.pubsub.types.PublishResult;
import io.ably.pubsub.types.Summary;
import io.ably.pubsub.types.UpdateDeleteResult;
import io.ably.pubsub.util.Clock;
import io.ably.pubsub.util.CollectionUtils;
import io.ably.pubsub.util.EventEmitter;
import io.ably.pubsub.util.Listeners;
import io.ably.pubsub.util.Log;
import io.ably.pubsub.util.AblyTimer;
import io.ably.pubsub.util.ReconnectionStrategy;
import io.ably.pubsub.util.StringUtils;
import io.ably.pubsub.util.SystemClock;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.Nullable;

/**
 * Enables messages to be published and subscribed to.
 * Also enables historic messages to be retrieved and provides access to the {@link Presence} object of a channel.
 */
public abstract class ChannelBase extends EventEmitter<ChannelEvent, ChannelStateListener> {

    /************************************
     * ChannelState and state management
     ************************************/

    /**
     * The channel name.
     */
    public final String name;

    /**
     * A {@link Presence} object.
     * <p>
     * Spec: RTL9
     */
    public final Presence presence;

    /**
     * The current {@link ChannelState} of the channel.
     * <p>
     * Spec: RTL2b
     */
    public ChannelState state;

    /**
     * An {@link ErrorInfo} object describing the last error which occurred on the channel, if any.
     * <p>
     * Spec: RTL4e
     */
    public ErrorInfo reason;

    /**
     * A {@link ChannelProperties} object.
     * <p>
     * Spec: CP1, RTL15
     */
    public ChannelProperties properties = new ChannelProperties();

    private int retryAttempt = 0;

    /**
     * @see #markAsReleased()
     */
    private AtomicBoolean released = new AtomicBoolean(false);

    @Nullable private final LiveObjectsPlugin liveObjectsPlugin;

    private volatile MessageEditsMixin messageEditsMixin;

    public RealtimeObject object;

    public final RealtimeAnnotations annotations;

    /***
     * internal
     *
     */
    private static class AttachRequest{
        final boolean forceReattach;
        final CompletionListener completionListener;

        private AttachRequest(boolean forceReattach, CompletionListener completionListener) {
            this.forceReattach = forceReattach;
            this.completionListener = completionListener;
        }
    }
    private static class DetachRequest{
        final CompletionListener completionListener;
        private DetachRequest(CompletionListener completionListener) {
            this.completionListener = completionListener;
        }
    }
    private AttachRequest pendingAttachRequest;
    private DetachRequest pendingDetachRequest;

    private void setState(ChannelState newState, ErrorInfo reason) {
        setState(newState, reason, false, true);
    }
    private void setState(ChannelState newState, ErrorInfo reason, boolean resumed) {
        setState(newState, reason, resumed, true);
    }
    private void setState(ChannelState newState, ErrorInfo reason, boolean resumed, boolean notifyStateChange) {
        Log.v(TAG, "setState(): channel = " + name + "; setting " + newState);
        ChannelStateListener.ChannelStateChange stateChange;
        synchronized(this) {
            stateChange = new ChannelStateListener.ChannelStateChange(newState, this.state, reason, resumed);
            this.state = stateChange.current;
            this.reason = stateChange.reason;
        }

        // cover states other than attached, ChannelState.attached already covered in setAttached
        if (liveObjectsPlugin != null && newState!= ChannelState.attached) {
            try {
                liveObjectsPlugin.handleStateChange(name, newState, false);
            } catch (Throwable t) {
                Log.e(TAG, "Unexpected exception in liveObjectsPlugin.handle", t);
            }
        }

        if (newState != ChannelState.attaching && newState != ChannelState.suspended) {
            this.retryAttempt = 0;
        }

        // RTP5a1
        if (newState == ChannelState.detached || newState == ChannelState.suspended || newState == ChannelState.failed) {
            properties.channelSerial = null;
        }

        if(notifyStateChange) {
            /* broadcast state change */
            emit(newState, stateChange);
        }
        if (newState == ChannelState.detached && pendingAttachRequest != null){
            Log.v(TAG, "Pending attach request after detach- now reattaching channel:"+name);
            attach(pendingAttachRequest.forceReattach, pendingAttachRequest.completionListener);
            pendingAttachRequest = null;
        }else if (newState == ChannelState.attached && pendingDetachRequest != null){
            Log.v(TAG, "Pending detach request after attach. Now detaching channel:"+name);
            try {
                detach(pendingDetachRequest.completionListener);
                pendingDetachRequest = null;
            } catch (AblyException e) {
                Log.e(TAG,"Channel failed to detach after attach:"+name,e);
            }
        }
    }

    /************************************
     * attach / detach
     ************************************/

    /**
     * Attach to this channel ensuring the channel is created in the Ably system and all messages published
     * on the channel are received by any channel listeners registered using {@link Channel#subscribe}.
     * Any resulting channel state change will be emitted to any listeners registered using the
     * {@link EventEmitter#on} or {@link EventEmitter#once} methods.
     * As a convenience, attach() is called implicitly if {@link Channel#subscribe} for the channel is called,
     * or {@link Presence#enter} or {@link Presence#subscribe} are called on the {@link Presence} object for this channel.
     * <p>
     * Spec: RTL4d
     * @throws AblyException
     */
    public void attach() throws AblyException {
        attach(null);
    }

    /**
     * Attach to this channel ensuring the channel is created in the Ably system and all messages published
     * on the channel are received by any channel listeners registered using {@link Channel#subscribe}.
     * Any resulting channel state change will be emitted to any listeners registered using the
     * {@link EventEmitter#on} or {@link EventEmitter#once} methods.
     * As a convenience, attach() is called implicitly if {@link Channel#subscribe} for the channel is called,
     * or {@link Presence#enter} or {@link Presence#subscribe} are called on the {@link Presence} object for this channel.
     * <p>
     * Spec: RTL4d
     * @param listener A callback may optionally be passed in to this call to be notified of success or failure of the operation.
     * <p>
     * This listener is invoked on a background thread.
     * @throws AblyException
     */
    public void attach(CompletionListener listener) throws  AblyException {
        this.attach(false, listener);
    }

    void attach(boolean forceReattach, CompletionListener listener) {
        clearAttachTimers();
        attachWithTimeout(forceReattach, listener, null);
    }

    /**
     * This method carries queued messages accumulated on connection manager while the channel
     * isn't attached yet. It's added in the queue here
     * */
    synchronized void transferQueuedPresenceMessages(List<QueuedMessage> messagesToTransfer) {
        state = ChannelState.attaching;
        if (messagesToTransfer != null) {
            for (QueuedMessage queuedMessage : messagesToTransfer) {
                PresenceMessage[] presenceMessages = queuedMessage.msg.presence;
                if (presenceMessages != null && presenceMessages.length > 0) {
                    for (PresenceMessage presenceMessage : presenceMessages) {
                        this.presence.addPendingPresence(presenceMessage, queuedMessage.listener);
                    }
                }
            }
        }
    }

    private boolean attachResume;

    private void attachImpl(final boolean forceReattach, final CompletionListener listener, ErrorInfo reattachmentReason) throws AblyException {
        Log.v(TAG, "attach(); channel = " + name);
        if(!forceReattach) {
            /* check preconditions */
            switch(state) {
                case attaching: //RTL4h
                    if(listener != null) {
                        on(new ChannelStateCompletionListener(listener, ChannelState.attached, ChannelState.failed));
                    }
                    return;
                case detaching: //RTL4h
                    pendingAttachRequest = new AttachRequest(forceReattach,listener);
                    return;
                case attached: //RTL4a
                    callCompletionListenerSuccess(listener);
                    return;
                case failed: //RTL4g
                    this.reason = null;
                default:
            }
        }
        ConnectionManager connectionManager = ably.connection.connectionManager;
        if(!connectionManager.isActive()) {
            throw AblyException.fromErrorInfo(connectionManager.getStateErrorInfo());
        }

        // (RTL4i)
        ConnectionState connState = connectionManager.getConnectionState().state;
        if (connState == ConnectionState.connecting || connState == ConnectionState.disconnected) {
            if (listener != null) {
                on(new ChannelStateCompletionListener(listener, ChannelState.attached, ChannelState.failed));
            }
            setState(ChannelState.attaching, reattachmentReason);
            return;
        }

        /* send attach request and pending state */
        Log.v(TAG, "attach(); channel = " + name + "; sending ATTACH request");
        ProtocolMessage attachMessage = new ProtocolMessage(Action.attach, this.name);
        if(this.options != null) {
            if(this.options.hasParams()) {
                attachMessage.params = CollectionUtils.copy(this.options.params);
            }
            if(this.options.hasModes()) {
                attachMessage.setFlags(options.getModeFlags());
            }
        }
        attachMessage.channelSerial = properties.channelSerial; // RTL4c1
        if(this.decodeFailureRecoveryInProgress) { // RTL18c
            Log.v(TAG, "attach(); message decode recovery in progress, setting last message channelserial");
            attachMessage.channelSerial = this.lastPayloadProtocolMessageChannelSerial;
        }
        try {
            if (listener != null) {
                on(new ChannelStateCompletionListener(listener, ChannelState.attached, ChannelState.failed));
            }
            if (this.attachResume) {
                attachMessage.setFlag(Flag.attach_resume);
            }

            setState(ChannelState.attaching, reattachmentReason);
            connectionManager.send(attachMessage, true, null);
        } catch(AblyException e) {
            throw e;
        }
    }

    /**
     * Detach from this channel.
     * Any resulting channel state change is emitted to any listeners registered using the
     * {@link EventEmitter#on} or {@link EventEmitter#once} methods.
     * Once all clients globally have detached from the channel, the channel will be released in the Ably service within two minutes.
     * <p>
     * Spec: RTL5e
     * @throws AblyException
     */
    public void detach() throws AblyException {
        detach(null);
    }

    /**
     * Mark channel as released that means we can't perform any operation on this channel anymore
     */
    public void markAsReleased() {
        released.set(true);
    }

    /**
     * Detach from this channel.
     * Any resulting channel state change is emitted to any listeners registered using the
     * {@link EventEmitter#on} or {@link EventEmitter#once} methods.
     * Once all clients globally have detached from the channel, the channel will be released in the Ably service within two minutes.
     * <p>
     * Spec: RTL5e
     * @param listener A callback may optionally be passed in to this call to be notified of success or failure of the operation.
     * <p>
     * This listener is invoked on a background thread.
     * @throws AblyException
     */
    public void detach(CompletionListener listener) throws AblyException {
        clearAttachTimers();
        detachWithTimeout(listener);
    }

    private void detachImpl(CompletionListener listener) throws AblyException {
        Log.v(TAG, "detach(); channel = " + name);
        /* check preconditions */
        switch(state) {
            case initialized: // RTL5a
            case detached: {
                callCompletionListenerSuccess(listener);
                return;
            }
            case detaching: //RTL5i
                if (listener != null) {
                    on(new ChannelStateCompletionListener(listener, ChannelState.detached, ChannelState.failed));
                }
                return;
            case attaching: //RTL5i
                pendingDetachRequest = new DetachRequest(listener);
                return;
            case failed: //RTL5b
                ErrorInfo error = this.reason != null ?
                    this.reason : new ErrorInfo("Channel state is failed", 90000);
                callCompletionListenerError(listener, error);
                return;
            case suspended: //RTL5j
                setState(ChannelState.detached, null);
                callCompletionListenerSuccess(listener);
                return;
            default:
        }
        ConnectionManager connectionManager = ably.connection.connectionManager;
        if(!connectionManager.isActive()) { // RTL5g
            throw AblyException.fromErrorInfo(connectionManager.getStateErrorInfo());
        }

        sendDetachMessage(listener);
    }

    private void sendDetachMessage(CompletionListener listener) throws AblyException {
        ProtocolMessage detachMessage = new ProtocolMessage(Action.detach, this.name);
        try {
            if (listener != null) {
                on(new ChannelStateCompletionListener(listener, ChannelState.detached, ChannelState.failed));
            }

            this.attachResume = false;
            if (released.get()) {
                setDetached(null);
            } else {
                setState(ChannelState.detaching, null);
            }
            ably.connection.connectionManager.send(detachMessage, true, null);
        } catch(AblyException e) {
            throw e;
        }
    }

    /***
     * internal
     *
     */
    private static void callCompletionListenerSuccess(CompletionListener listener) {
        if(listener != null) {
            try {
                listener.onSuccess();
            } catch(Throwable t) {
                Log.e(TAG, "Unexpected exception calling CompletionListener", t);
            }
        }
    }

    private static void callCompletionListenerError(CompletionListener listener, ErrorInfo err) {
        if(listener != null) {
            try {
                listener.onError(err);
            } catch(Throwable t) {
                Log.e(TAG, "Unexpected exception calling CompletionListener", t);
            }
        }
    }

    private static void callCompletionListenerError(Callback<PublishResult> listener, ErrorInfo err) {
        if(listener != null) {
            try {
                listener.onError(err);
            } catch(Throwable t) {
                Log.e(TAG, "Unexpected exception calling CompletionListener", t);
            }
        }
    }

    private void setAttached(ProtocolMessage message) {
        clearAttachTimers();
        properties.attachSerial = message.channelSerial;
        params = message.params;
        modes = ChannelMode.toSet(message.flags);
        this.attachResume = true;

        if (state == ChannelState.detaching || state == ChannelState.detached) { //RTL5k
            Log.v(TAG, "setAttached(): channel is in detaching state, as per RTL5k sending detach message!");
            try {
                sendDetachMessage(null);
            } catch (AblyException e) {
                Log.e(TAG, e.getMessage(), e);
            }
            return;
        }
        if (liveObjectsPlugin != null) {
            try {
                liveObjectsPlugin.handleStateChange(name, ChannelState.attached, message.hasFlag(Flag.has_objects));
            } catch (Throwable t) {
                Log.e(TAG, "Unexpected exception in liveObjectsPlugin.handle", t);
            }
        }
        if(state == ChannelState.attached) {
            Log.v(TAG, String.format(Locale.ROOT, "Server initiated attach for channel %s", name));
            if (!message.hasFlag(Flag.resumed)) { // RTL12
                emitUpdate(message.error, false);
                presence.onAttached(message.hasFlag(Flag.has_presence));
            }
        }
        else {
            setState(ChannelState.attached, message.error, message.hasFlag(Flag.resumed));
            presence.onAttached(message.hasFlag(Flag.has_presence));
        }
    }

    private void setDetached(ErrorInfo reason) {
        clearAttachTimers();
        Log.v(TAG, "setDetached(); channel = " + name);
        presence.onChannelDetachedOrFailed(reason);
        setState(ChannelState.detached, reason);
    }

    private void setFailed(ErrorInfo reason) {
        clearAttachTimers();
        Log.v(TAG, "setFailed(); channel = " + name);
        presence.onChannelDetachedOrFailed(reason);
        this.attachResume = false;
        setState(ChannelState.failed, reason);
    }

    /* Timer for attach operation */
    private AblyTimer attachTimer;

    /* Timer for reattaching if attach failed */
    private AblyTimer reattachTimer;

    /**
     * Cancel attach/reattach timers
     */
    synchronized private void clearAttachTimers() {
        AblyTimer[] timers = new AblyTimer[]{attachTimer, reattachTimer};
        attachTimer = reattachTimer = null;
        for (AblyTimer t: timers) {
            if (t != null) {
                t.cancel();
            }
        }
    }

    private void attachWithTimeout(final CompletionListener listener) throws AblyException {
        this.attachWithTimeout(false, listener, null);
    }

    /**
     * Attach channel, if not attached within timeout set state to suspended and
     * set up timer to reattach it later
     */
    synchronized private void attachWithTimeout(final boolean forceReattach, final CompletionListener listener, ErrorInfo reattachmentReason) {
        checkChannelIsNotReleased();
        AblyTimer currentAttachTimer;
        try {
            currentAttachTimer = clock.newTimer("attach-timer");
        } catch(Throwable t) {
            /* an exception instancing the timer can arise because the runtime is exiting */
            callCompletionListenerError(listener, ErrorInfo.fromThrowable(t));
            return;
        }
        attachTimer = currentAttachTimer;

        try {
            attachImpl(forceReattach, new CompletionListener() {
                @Override
                public void onSuccess() {
                    clearAttachTimers();
                    callCompletionListenerSuccess(listener);
                }

                @Override
                public void onError(ErrorInfo reason) {
                    clearAttachTimers();
                    callCompletionListenerError(listener, reason);
                }
            }, reattachmentReason);
        } catch(AblyException e) {
            attachTimer = null;
            callCompletionListenerError(listener, e.errorInfo);
        }

        if(attachTimer == null) {
            /* operation has already succeeded or failed, no need to set the timer */
            return;
        }

        final AblyTimer inProgressTimer = currentAttachTimer;
        attachTimer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        String errorMessage = String.format(Locale.ROOT, "Attach timed out for channel %s", name);
                        Log.v(TAG, errorMessage);
                        synchronized (ChannelBase.this) {
                            if(attachTimer != inProgressTimer) {
                                return;
                            }
                            attachTimer = null;
                            if(state == ChannelState.attaching) {
                                setSuspended(new ErrorInfo(errorMessage, 90007), true);
                                reattachAfterTimeout();
                            }
                        }
                    }
                }, Defaults.realtimeRequestTimeout);
    }

    private void checkChannelIsNotReleased() {
        if (released.get()) throw new IllegalStateException("Unable to perform any operation on released channel");
    }

    /**
     * Must be called in suspended state. Wait for timeout specified in clientOptions, and then
     * try to attach the channel
     */
    synchronized private void reattachAfterTimeout() {
        AblyTimer currentReattachTimer;
        try {
            currentReattachTimer = clock.newTimer("reattach-timer");
        } catch(Throwable t) {
            /* an exception instancing the timer can arise because the runtime is exiting */
            return;
        }
        reattachTimer = currentReattachTimer;

        this.retryAttempt++;
        int retryDelay = ReconnectionStrategy.getRetryTime(ably.options.channelRetryTimeout, retryAttempt);

        final AblyTimer inProgressTimer = currentReattachTimer;
        reattachTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (ChannelBase.this) {
                    if (inProgressTimer != reattachTimer) {
                        return;
                    }
                    reattachTimer = null;
                    if (state == ChannelState.suspended) {
                        try {
                            attachWithTimeout(null);
                        } catch (AblyException e) {
                            Log.e(TAG, "Reattach channel failed; channel = " + name, e);
                        }
                    }
                }
            }
        }, retryDelay);
    }

    /**
     * Try to detach the channel. If the server doesn't confirm the detach operation within realtime
     * request timeout return channel to previous state
     */
    synchronized private void detachWithTimeout(final CompletionListener listener) {
        final ChannelState originalState = state;
        AblyTimer currentDetachTimer;
        try {
            currentDetachTimer = released.get() ? null : clock.newTimer("detach-timer");
        } catch(Throwable t) {
            /* an exception instancing the timer can arise because the runtime is exiting */
            callCompletionListenerError(listener, ErrorInfo.fromThrowable(t));
            return;
        }
        attachTimer = currentDetachTimer;

        try {
            // If channel has been released, completionListener won't be invoked anyway
            CompletionListener completionListener = released.get() ? null : new CompletionListener() {
                @Override
                public void onSuccess() {
                    clearAttachTimers();
                    callCompletionListenerSuccess(listener);
                }

                @Override
                public void onError(ErrorInfo reason) {
                    clearAttachTimers();
                    callCompletionListenerError(listener, reason);
                }
            };
            detachImpl(completionListener);
        } catch (AblyException e) {
            attachTimer = null;
            callCompletionListenerError(listener, e.errorInfo); // RTL5g
        }

        if(attachTimer == null) {
            /* operation has already succeeded or failed, no need to set the timer */
            return;
        }

        final AblyTimer inProgressTimer = currentDetachTimer;
        attachTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (ChannelBase.this) {
                    if (inProgressTimer != attachTimer) {
                        return;
                    }
                    attachTimer = null;
                    if (state == ChannelState.detaching) {
                        ErrorInfo reason = new ErrorInfo("Detach operation timed out", 90007);
                        callCompletionListenerError(listener, reason);
                        setState(originalState, reason);
                    }
                }
            }
        }, Defaults.realtimeRequestTimeout);
    }

    /* State changes provoked by ConnectionManager state changes. */
    public void setConnected() {
        // TODO - seems test is failing because of explicit attach after connect
        if (state.isReattachable()){
            attach(true,null); // RTN15c6, RTN15c7
        }
    }

    /** If the connection state enters the FAILED state, then an ATTACHING
     * or ATTACHED channel state will transition to FAILED and set the
     * Channel#errorReason
     */
    public void setConnectionFailed(ErrorInfo reason) {
        clearAttachTimers();
        if (state == ChannelState.attached || state == ChannelState.attaching)
            setFailed(reason);
    }

    /** (RTL3b) If the connection state enters the CLOSED state, then an
     * ATTACHING or ATTACHED channel state will transition to DETACHED. */
    public void setConnectionClosed(ErrorInfo reason) {
        clearAttachTimers();
        if (state == ChannelState.attached || state == ChannelState.attaching)
            setDetached(reason);
    }

    /** (RTL3c) If the connection state enters the SUSPENDED state, then an
     * ATTACHING or ATTACHED channel state will transition to SUSPENDED.
     * (RTN15c3) The client library should initiate an attach for channels
     *  that are in the SUSPENDED state. For all channels in the ATTACHING
     *  or ATTACHED state, the client library should fail any previously queued
     *  messages for that channel and initiate a new attach.
     * This also gets called when a connection enters CONNECTED but with a
     * non-fatal error for a failed reconnect (RTN16e). */
    public synchronized void setSuspended(ErrorInfo reason, boolean notifyStateChange) {
        clearAttachTimers();
        if (state == ChannelState.attached || state == ChannelState.attaching) {
            Log.v(TAG, "setSuspended(); channel = " + name);
            presence.onChannelSuspended(reason);
            setState(ChannelState.suspended, reason, false, notifyStateChange);
        }
    }

    /**
     * Internal
     * <p>
     * (RTN11d) Resets channels back to initialized and clears error reason
     */
    public synchronized void setReinitialized() {
        clearAttachTimers();
        setState(ChannelState.initialized, null);
    }

    @Override
    protected void apply(ChannelStateListener listener, ChannelEvent event, Object... args) {
        try {
            listener.onChannelStateChanged((ChannelStateListener.ChannelStateChange)args[0]);
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected exception calling ChannelStateListener", t);
        }
    }

    static ErrorInfo REASON_NOT_ATTACHED = new ErrorInfo("Channel not attached", 400, 90001);

    /************************************
     * subscriptions and MessageListener
     ************************************/

    /**
     * An interface whereby a client maybe notified of message on a channel.
     */
    public interface MessageListener {
        void onMessage(Message message);
    }

    /**
     * Deregisters all listeners to messages on this channel.
     * This removes all earlier subscriptions.
     * <p>
     * Spec: RTL8a, RTE5
     */
    public synchronized void unsubscribe() {
        Log.v(TAG, "unsubscribe(); channel = " + this.name);
        listeners.clear();
        eventListeners.clear();
    }

    /**
     * <p>
     * Checks if {@link io.ably.pubsub.types.ChannelOptions#attachOnSubscribe} is true.
     * </p>
     * Defaults to {@code true} when {@link io.ably.pubsub.realtime.ChannelBase#options} is null.
     * <p>Spec: TB4, RTL7g, RTL7h, RTP6d, RTP6e</p>
     */
    protected boolean attachOnSubscribeEnabled() {
        return options == null || options.attachOnSubscribe;
    }

    /**
     * Registers a listener for messages on this channel.
     * The caller supplies a listener function, which is called each time one or more messages arrives on the channel.
     * <p>
     * Spec: RTL7a
     * @param listener A listener may optionally be passed in to this call to be notified of success or failure
     *                 of the channel {@link Channel#attach} operation.
     * <p>
     * This listener is invoked on a background thread.
     * @throws AblyException
     */
    public synchronized void subscribe(MessageListener listener) throws AblyException {
        Log.v(TAG, "subscribe(); channel = " + this.name);
        listeners.add(listener);
        if (attachOnSubscribeEnabled()) {
            attach();
        }
    }

    /**
     * Deregisters the given listener (for any/all event names).
     * This removes an earlier subscription.
     * <p>
     * Spec: RTL8a
     * @param listener An event listener function.
     * <p>
     * This listener is invoked on a background thread.
     */
    public synchronized void unsubscribe(MessageListener listener) {
        Log.v(TAG, "unsubscribe(); channel = " + this.name);
        listeners.remove(listener);
        for (MessageMulticaster multicaster: eventListeners.values()) {
            multicaster.remove(listener);
        }
    }

    /**
     * Registers a listener for messages with a given event name on this channel.
     * The caller supplies a listener function, which is called each time one or more matching messages arrives on the channel.
     * <p>
     * Spec: RTL7b
     * @param name The event name.
     * @param listener A listener may optionally be passed in to this call to be notified of success or failure
     *                 of the channel {@link Channel#attach} operation.
     * <p>
     * This listener is invoked on a background thread.
     * @throws AblyException
     */
    public synchronized void subscribe(String name, MessageListener listener) throws AblyException {
        Log.v(TAG, "subscribe(); channel = " + this.name + "; event = " + name);
        subscribeImpl(name, listener);
        if (attachOnSubscribeEnabled()) {
            attach();
        }
    }

    /**
     * Deregisters the given listener for the specified event name.
     * This removes an earlier event-specific subscription
     * <p>
     * Spec: RTL8a
     * @param name The event name.
     * @param listener An event listener function.
     * <p>
     * This listener is invoked on a background thread.
     */
    public synchronized void unsubscribe(String name, MessageListener listener) {
        Log.v(TAG, "unsubscribe(); channel = " + this.name + "; event = " + name);
        unsubscribeImpl(name, listener);
    }

    /**
     * Registers a listener for messages on this channel for multiple event name values.
     * The caller supplies a listener function, which is called each time one or more matching messages arrives on the channel.
     * <p>
     * Spec: RTL7a
     * @param names An array of event names.
     * @param listener A listener may optionally be passed in to this call to be notified of success or failure
     *                 of the channel {@link Channel#attach} operation.
     * <p>
     * This listener is invoked on a background thread.
     * @throws AblyException
     */
    public synchronized void subscribe(String[] names, MessageListener listener) throws AblyException {
        Log.v(TAG, "subscribe(); channel = " + this.name + "; (multiple events)");
        for(String name : names)
            subscribeImpl(name, listener);
        if (attachOnSubscribeEnabled()) {
            attach();
        }
    }

    /**
     * Deregisters the given listener from all event names in the array.
     * <p>
     * Spec: RTL8a
     * @param names An array of event names.
     * @param listener An event listener function.
     * <p>
     * This listener is invoked on a background thread.
     */
    public synchronized void unsubscribe(String[] names, MessageListener listener) {
        Log.v(TAG, "unsubscribe(); channel = " + this.name + "; (multiple events)");
        for(String name : names)
            unsubscribeImpl(name, listener);
    }

    /***
     * internal
     *
     */
    private void onMessage(final ProtocolMessage protocolMessage) {
        Log.v(TAG, "onMessage(); channel = " + name);
        final Message[] messages = protocolMessage.messages;
        final Message firstMessage = messages[0];
        final Message lastMessage = messages[messages.length - 1];

        final DeltaExtras deltaExtras = (null == firstMessage.extras) ? null : firstMessage.extras.getDelta();
        if (null != deltaExtras && !deltaExtras.getFrom().equals(this.lastPayloadMessageId)) {
            Log.e(TAG, String.format(Locale.ROOT, "Delta message decode failure - previous message not available. Message id = %s, channel = %s", firstMessage.id, name));
            startDecodeFailureRecovery();
            return;
        }

        for(int i = 0; i < messages.length; i++) {
            final Message msg = messages[i];

            /* populate fields derived from protocol message */
            if(msg.connectionId == null) msg.connectionId = protocolMessage.connectionId;
            if(msg.timestamp == 0) msg.timestamp = protocolMessage.timestamp;
            if(msg.id == null) msg.id = protocolMessage.id + ':' + i;
            // (TM2s)
            if(msg.version == null) msg.version = new MessageVersion(msg.serial, msg.timestamp);
            // (TM2s1)
            if(msg.version.serial == null) msg.version.serial = msg.serial;
            // (TM2s2)
            if(msg.version.timestamp == 0) msg.version.timestamp = msg.timestamp;
            // (TM2u)
            if(msg.annotations == null) msg.annotations = new MessageAnnotations();
            // (TM8a)
            if(msg.annotations.summary == null) msg.annotations.summary = new Summary(new HashMap<>());

            try {
                if (msg.data != null) msg.decode(options, decodingContext);
            } catch (MessageDecodeException e) {
                if (e.errorInfo.code == 40018) {
                    Log.e(TAG, String.format(Locale.ROOT, "Delta message decode failure - %s. Message id = %s, channel = %s", e.errorInfo.message, msg.id, name));
                    startDecodeFailureRecovery();

                    // log messages skipped per RTL16
                    for (int j = i + 1; j < messages.length; j++) {
                        final String jId = messages[j].id; // might be null
                        final String jIdToLog = (null == jId) ? protocolMessage.id + ':' + j : jId;
                        Log.v(TAG, String.format(Locale.ROOT, "Delta recovery in progress - message skipped. Message id = %s, channel = %s", jIdToLog, name));
                    }

                    return;
                }
                else {
                    Log.e(TAG, String.format(Locale.ROOT, "Message decode failure - %s. Message id = %s, channel = %s", e.errorInfo.message, msg.id, name));
                }
            }

            /* broadcast */
            final MessageMulticaster listeners = eventListeners.get(msg.name);
            if(listeners != null)
                listeners.onMessage(msg);
        }

        lastPayloadMessageId = lastMessage.id;
        lastPayloadProtocolMessageChannelSerial = protocolMessage.channelSerial;

        for (final Message msg : messages) {
            this.listeners.onMessage(msg);
        }
    }

    private void startDecodeFailureRecovery() {
        if (this.decodeFailureRecoveryInProgress) {
            return;
        }
        Log.w(TAG, "Starting delta decode failure recovery process");
        this.decodeFailureRecoveryInProgress = true;
        this.attach(true, new CompletionListener() {
            @Override
            public void onSuccess() {
                decodeFailureRecoveryInProgress = false;
            }

            @Override
            public void onError(ErrorInfo reason) {
                decodeFailureRecoveryInProgress = false;
            }
        });
    }

    private MessageMulticaster listeners = new MessageMulticaster();
    private HashMap<String, MessageMulticaster> eventListeners = new HashMap<String, MessageMulticaster>();

    private static class MessageMulticaster extends io.ably.pubsub.util.Multicaster<MessageListener> implements MessageListener {
        @Override
        public void onMessage(Message message) {
            for (final MessageListener member : getMembers())
                try {
                    member.onMessage(message);
                } catch (Throwable t) {
                    Log.e(TAG, "Unexpected exception calling listener", t);
                }
        }
    }

    private void subscribeImpl(String name, MessageListener listener) throws AblyException {
        MessageMulticaster listeners = eventListeners.get(name);
        if(listeners == null) {
            listeners = new MessageMulticaster();
            eventListeners.put(name, listeners);
        }
        listeners.add(listener);
    }

    private void unsubscribeImpl(String name, MessageListener listener) {
        MessageMulticaster listeners = eventListeners.get(name);
        if(listeners != null) {
            listeners.remove(listener);
            if(listeners.isEmpty())
                eventListeners.remove(name);
        }
    }

    /************************************
     * publish and pending messages
     ************************************/

    /**
     * Publishes a single message to the channel with the given event name and payload.
     * When publish is called with this client library, it won't attempt to implicitly attach to the channel,
     * so long as <a href="https://ably.com/docs/realtime/channels#transient-publish">transient publishing</a> is available in the library.
     * Otherwise, the client will implicitly attach.
     * <p>
     * Spec: RTL6i
     * @param name the event name
     * @param data the message payload
     * @throws AblyException
     */
    @NonBlocking
    public void publish(String name, Object data) throws AblyException {
        publish(name, data, (Callback<PublishResult>) null);
    }

    /**
     * Publishes a message to the channel.
     * When publish is called with this client library, it won't attempt to implicitly attach to the channel.
     * <p>
     * Spec: RTL6i
     * @param message A {@link Message} object.
     * @throws AblyException
     */
    @NonBlocking
    public void publish(Message message) throws AblyException {
        publish(message, (Callback<PublishResult>) null);
    }

    /**
     * Publishes an array of messages to the channel.
     * When publish is called with this client library, it won't attempt to implicitly attach to the channel.
     * <p>
     * Spec: RTL6i
     * @param messages An array of {@link Message} objects.
     * @throws AblyException
     */
    @NonBlocking
    public void publish(Message[] messages) throws AblyException {
        publish(messages, (Callback<PublishResult>) null);
    }

    /**
     * Publishes a single message to the channel with the given event name and payload.
     * When publish is called with this client library, it won't attempt to implicitly attach to the channel,
     * so long as <a href="https://ably.com/docs/realtime/channels#transient-publish">transient publishing</a> is available in the library.
     * Otherwise, the client will implicitly attach.
     * <p>
     * Spec: RTL6i
     * @param name the event name
     * @param data the message payload
     * @param callback A callback may optionally be passed in to this call to be notified of success or failure of the operation,
     *                 receiving a {@link PublishResult} with message serial(s) on success.
     * <p>
     * This callback is invoked on a background thread.
     * @throws AblyException
     */
    @NonBlocking
    public void publish(String name, Object data, Callback<PublishResult> callback) throws AblyException {
        Log.v(TAG, "publish(String, Object); channel = " + this.name + "; event = " + name);
        publish(new Message[] {new Message(name, data)}, callback);
    }

    /**
     * Publishes a message to the channel.
     * When publish is called with this client library, it won't attempt to implicitly attach to the channel.
     * <p>
     * Spec: RTL6i
     * @param message A {@link Message} object.
     * @param callback A callback may optionally be passed in to this call to be notified of success or failure of the operation,
     *                 receiving a {@link PublishResult} with message serial(s) on success.
     * <p>
     * This callback is invoked on a background thread.
     * @throws AblyException
     */
    @NonBlocking
    public void publish(Message message, Callback<PublishResult> callback) throws AblyException {
        Log.v(TAG, "publish(Message); channel = " + this.name + "; event = " + message.name);
        publish(new Message[] {message}, callback);
    }

    @NonBlocking
    public synchronized void publish(Message[] messages, Callback<PublishResult> listener) throws AblyException {
        Log.v(TAG, "publish(Message[]); channel = " + this.name);
        ConnectionManager connectionManager = ably.connection.connectionManager;
        ConnectionManager.State connectionState = connectionManager.getConnectionState();
        boolean queueMessages = ably.options.queueMessages;
        if(!connectionManager.isActive() || (connectionState.queueEvents && !queueMessages)) {
            throw AblyException.fromErrorInfo(connectionState.defaultErrorInfo);
        }
        boolean connected = (connectionState.sendEvents);
        try {
            for(Message message : messages) {
                /* RTL6g3: check validity of any clientId;
                 * RTL6g4: be lenient with a null clientId if we're not connected */
                ably.auth.checkClientId(message, true, connected);
                message.encode(options);
            }
        } catch(AblyException e) {
            callCompletionListenerError(listener, e.errorInfo);
            return;
        }
        ProtocolMessage msg = new ProtocolMessage(Action.message, this.name);
        msg.messages = messages;
        switch(state) {
        case failed:
        case suspended:
            throw AblyException.fromErrorInfo(new ErrorInfo("Unable to publish in failed or suspended state", 400, 40000));
        default:
            connectionManager.send(msg, queueMessages, listener);
        }
    }

    /***
     * internal
     *
     */

    private static class FailedMessage {
        QueuedMessage msg;
        ErrorInfo reason;
        FailedMessage(QueuedMessage msg, ErrorInfo reason) {
            this.msg = msg;
            this.reason = reason;
        }
    }

    static Param[] replacePlaceholderParams(Channel channel, Param[] placeholderParams) throws AblyException {
        if (placeholderParams == null) {
            return null;
        }

        HashSet<Param> params = new HashSet<>();

        Param param;
        for(int i = 0; i < placeholderParams.length; i++) {
            param = placeholderParams[i];

            if(KEY_UNTIL_ATTACH.equals(param.key)) {
                if("true".equalsIgnoreCase(param.value)) {
                    if (channel.state != ChannelState.attached) {
                        throw AblyException.fromErrorInfo(new ErrorInfo("option untilAttach requires the channel to be attached", 40000, 400));
                    }

                    params.add(new Param(KEY_FROM_SERIAL, channel.properties.attachSerial));
                }
                else if(!"false".equalsIgnoreCase(param.value)) {
                    throw AblyException.fromErrorInfo(new ErrorInfo("option untilAttach is invalid. \"true\" or \"false\" expected", 40000, 400));
                }
            }
            else {
                /* Add non-placeholder param as is */
                params.add(param);
            }
        }

        return params.toArray(new Param[params.size()]);
    }


    private static final String KEY_UNTIL_ATTACH = "untilAttach";
    private static final String KEY_FROM_SERIAL = "fromSerial";

    //region Message Edits and Deletes

    /**
     * Retrieves the latest version of a specific message by its serial identifier.
     * <p>
     * This method allows you to fetch the current state of a message, including any updates
     * or deletions that have been applied since its creation.
     *
     * @param serial The unique serial identifier of the message to retrieve.
     * @return A {@link Message} object representing the latest version of the message.
     * @throws AblyException If the message cannot be retrieved or does not exist.
     */
    @Blocking
    public Message getMessage(String serial) throws AblyException {
        return messageEditsMixin.getMessage(ably.http, serial);
    }

    /**
     * Asynchronously retrieves the latest version of a specific message by its serial identifier.
     *
     * @param serial The unique serial identifier of the message to retrieve.
     * @param callback A callback to handle the result asynchronously.
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void getMessageAsync(String serial, Callback<Message> callback) {
        messageEditsMixin.getMessageAsync(ably.http, serial, callback);
    }

    /**
     * Asynchronously updates an existing message.
     *
     * @param message A {@link Message} object containing the fields to update and the serial identifier.
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void updateMessage(Message message) throws AblyException {
        updateMessage(message, null, null);
    }

    /**
     * Asynchronously updates an existing message.
     *
     * @param message A {@link Message} object containing the fields to update and the serial identifier.
     * @param operation operation metadata such as clientId, description, or metadata in the version field
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void updateMessage(Message message, MessageOperation operation) throws AblyException {
        updateMessage(message, operation, null);
    }

    /**
     * Asynchronously updates an existing message.
     *
     * @param message A {@link Message} object containing the fields to update and the serial identifier.
     * @param operation operation metadata such as clientId, description, or metadata in the version field
     * @param listener A callback to be notified of the outcome of this operation.
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void updateMessage(Message message, MessageOperation operation, Callback<UpdateDeleteResult> listener) throws AblyException {
        Log.v(TAG, "updateMessage(Message); channel = " + this.name + "; serial = " + message.serial);
        updateDeleteImpl(message, operation, MessageAction.MESSAGE_UPDATE, listener);
    }

    /**
     * Asynchronously updates an existing message.
     *
     * @param message A {@link Message} object containing the fields to update and the serial identifier.
     * @param listener A callback to be notified of the outcome of this operation.
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void updateMessage(Message message, Callback<UpdateDeleteResult> listener) throws AblyException {
        updateMessage(message, null, listener);
    }

    /**
     * Asynchronously marks a message as deleted.
     *
     * @param message A {@link Message} object containing the serial identifier and operation metadata.
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void deleteMessage(Message message) throws AblyException {
        deleteMessage(message, null, null);
    }

    /**
     * Asynchronously marks a message as deleted.
     *
     * @param message A {@link Message} object containing the serial identifier and operation metadata.
     * @param operation operation metadata such as clientId, description, or metadata in the version field
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void deleteMessage(Message message, MessageOperation operation) throws AblyException {
        deleteMessage(message, operation, null);
    }

    /**
     * Asynchronously marks a message as deleted.
     *
     * @param message A {@link Message} object containing the serial identifier and operation metadata.
     * @param operation operation metadata such as clientId, description, or metadata in the version field
     * @param listener A callback to be notified of the outcome of this operation.
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void deleteMessage(Message message, MessageOperation operation, Callback<UpdateDeleteResult> listener) throws AblyException {
        Log.v(TAG, "deleteMessage(Message); channel = " + this.name + "; serial = " + message.serial);
        updateDeleteImpl(message, operation, MessageAction.MESSAGE_DELETE, listener);
    }

    /**
     * Asynchronously marks a message as deleted.
     *
     * @param message A {@link Message} object containing the serial identifier and operation metadata.
     * @param callback A callback to be notified of the outcome of this operation.
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void deleteMessage(Message message, Callback<UpdateDeleteResult> callback) throws AblyException {
        deleteMessage(message, null, callback);
    }

    /**
     * Asynchronously appends message text to the end of the message data.
     *
     * @param message  A {@link Message} object containing the serial identifier and data to append.
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void appendMessage(Message message) throws AblyException {
        appendMessage(message, null, null);
    }

    /**
     * Asynchronously appends message text to the end of the message.
     *
     * @param message  A {@link Message} object containing the serial identifier and data to append.
     * @param operation operation details such as clientId, description, or metadata
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void appendMessage(Message message, MessageOperation operation) throws AblyException {
        appendMessage(message, operation, null);
    }

    /**
     * Asynchronously appends message text to the end of the message.
     *
     * @param message  A {@link Message} object containing the serial identifier and data to append.
     * @param operation operation details such as clientId, description, or metadata
     * @param listener A callback to be notified of the outcome of this operation.
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void appendMessage(Message message, MessageOperation operation, Callback<UpdateDeleteResult> listener) throws AblyException {
        Log.v(TAG, "appendMessage(Message); channel = " + this.name + "; serial = " + message.serial);
        updateDeleteImpl(message, operation, MessageAction.MESSAGE_APPEND, listener);
    }

    /**
     * Asynchronously appends message text to the end of the message.
     *
     * @param message  A {@link Message} object containing the serial identifier and data to append.
     * @param callback A callback to be notified of the outcome of this operation.
     * <p>
     * This callback is invoked on a background thread.
     */
    @NonBlocking
    public void appendMessage(Message message, Callback<UpdateDeleteResult> callback) throws AblyException {
        appendMessage(message, null, callback);
    }

    private void updateDeleteImpl(
        Message message,
        MessageOperation operation,
        MessageAction action,
        Callback<UpdateDeleteResult> listener
    ) throws AblyException {
        if (message.serial == null || message.serial.isEmpty()) {
            throw AblyException.fromErrorInfo(new ErrorInfo("Message serial cannot be empty", 400, 40003));
        }
        ConnectionManager connectionManager = ably.connection.connectionManager;
        ConnectionManager.State connectionState = connectionManager.getConnectionState();
        boolean queueMessages = ably.options.queueMessages;
        if (!connectionManager.isActive() || (connectionState.queueEvents && !queueMessages)) {
            throw AblyException.fromErrorInfo(connectionState.defaultErrorInfo);
        }
        boolean connected = (connectionState.sendEvents);

        Message updatedMessage = new Message(message.name, message.data, message.extras);
        updatedMessage.serial = message.serial;
        updatedMessage.action = action;
        updatedMessage.version = new MessageVersion();
        if (operation != null) {
            updatedMessage.version.clientId = operation.clientId;
            updatedMessage.version.description = operation.description;
            updatedMessage.version.metadata = operation.metadata;
        }

        try {
            ably.auth.checkClientId(message, true, connected);
            updatedMessage.encode(options);
        } catch (AblyException e) {
            if (listener != null) {
                listener.onError(e.errorInfo);
            }
            return;
        }

        ProtocolMessage msg = new ProtocolMessage(Action.message, this.name);
        msg.messages = new Message[] { updatedMessage };
        try {
            connectionManager.send(msg, queueMessages, Listeners.toPublishResultListener(listener));
        } catch (AblyException e) {
            if (listener != null) {
                listener.onError(e.errorInfo);
            }
        }
    }

    /**
     * Retrieves all historical versions of a specific message.
     * <p>
     * This method returns a paginated result containing all versions of the message,
     * ordered chronologically. Each version includes metadata about when and by whom
     * the message was modified.
     *
     * @param serial The unique serial identifier of the message.
     * @param params Query parameters for filtering or pagination (e.g., limit, start, end).
     * @return A {@link PaginatedResult} containing an array of {@link Message} objects
     *         representing all versions of the message.
     * @throws AblyException If the versions cannot be retrieved.
     */
    @Blocking
    public PaginatedResult<Message> getMessageVersions(String serial, Param[] params) throws AblyException {
        return messageEditsMixin.getMessageVersions(ably.http, serial, params);
    }

    /**
     * Asynchronously retrieves all historical versions of a specific message.
     *
     * @param serial The unique serial identifier of the message.
     * @param params Query parameters for filtering or pagination.
     * @param callback A callback to handle the result asynchronously.
     */
    @NonBlocking
    public void getMessageVersionsAsync(String serial, Param[] params, Callback<AsyncPaginatedResult<Message>> callback) throws AblyException {
        messageEditsMixin.getMessageVersionsAsync(ably.http, serial, params, callback);
    }

    //endregion

    /************************************
     * Channel history
     ************************************/

    /**
     * Retrieves a {@link PaginatedResult} object, containing an array of historical {@link Message} objects for the channel.
     * If the channel is configured to persist messages, then messages can be retrieved from history for up to 72 hours in the past.
     * If not, messages can only be retrieved from history for up to two minutes in the past.
     * <p>
     * Spec: RSL2a
     * @param params the request params:
     * <p>
     * start (RTL10a) - The time from which messages are retrieved, specified as milliseconds since the Unix epoch.
     * <p>
     * end (RTL10a) - The time until messages are retrieved, specified as milliseconds since the Unix epoch.
     * <p>
     * direction (RTL10a) - The order for which messages are returned in.
     * Valid values are backwards which orders messages from most recent to oldest,
     * or forwards which orders messages from oldest to most recent. The default is backwards.
     * <p>
     * limit (RTL10a) - An upper limit on the number of messages returned. The default is 100, and the maximum is 1000.
     * <p>
     * untilAttach (RTL10b) - When true, ensures message history is up until the point of the channel being attached.
     *               See <a href="https://ably.com/docs/realtime/history#continuous-history">continuous history</a> for more info.
     *               Requires the direction to be backwards.
     *               If the channel is not attached, or if direction is set to forwards, this option results in an error.
     * @return A {@link PaginatedResult} object containing an array of {@link Message} objects.
     * @throws AblyException
     */
    public PaginatedResult<Message> history(Param[] params) throws AblyException {
        return historyImpl(ably.http, params).sync();
    }

    PaginatedResult<Message> history(Http http, Param[] params) throws AblyException {
        return historyImpl(http, params).sync();
    }

    /**
     * Asynchronously retrieves a {@link PaginatedResult} object, containing an array of historical {@link Message} objects for the channel.
     * If the channel is configured to persist messages, then messages can be retrieved from history for up to 72 hours in the past.
     * If not, messages can only be retrieved from history for up to two minutes in the past.
     * <p>
     * Spec: RSL2a
     * @param params the request params:
     * <p>
     * start (RTL10a) - The time from which messages are retrieved, specified as milliseconds since the Unix epoch.
     * <p>
     * end (RTL10a) - The time until messages are retrieved, specified as milliseconds since the Unix epoch.
     * <p>
     * direction (RTL10a) - The order for which messages are returned in.
     * Valid values are backwards which orders messages from most recent to oldest,
     * or forwards which orders messages from oldest to most recent. The default is backwards.
     * <p>
     * limit (RTL10a) - An upper limit on the number of messages returned. The default is 100, and the maximum is 1000.
     * <p>
     * untilAttach (RTL10b) - When true, ensures message history is up until the point of the channel being attached.
     *               See <a href="https://ably.com/docs/realtime/history#continuous-history">continuous history</a> for more info.
     *               Requires the direction to be backwards.
     *               If the channel is not attached, or if direction is set to forwards, this option results in an error.
     * @param callback Callback with {@link AsyncPaginatedResult} object containing an array of {@link Message} objects.
     * @throws AblyException
     */
    public void historyAsync(Param[] params, Callback<AsyncPaginatedResult<Message>> callback) {
        historyAsync(ably.http, params, callback);
    }

    void historyAsync(Http http, Param[] params, Callback<AsyncPaginatedResult<Message>> callback) {
        historyImpl(http, params).async(callback);
    }

    private BasePaginatedQuery.ResultRequest<Message> historyImpl(Http http, Param[] params) {
        try {
            params = replacePlaceholderParams((Channel) this, params);
        } catch (AblyException e) {
            return new BasePaginatedQuery.ResultRequest.Failed<Message>(e);
        }

        HttpCore.BodyHandler<Message> bodyHandler = MessageSerializer.getMessageResponseHandler(options);
        return new BasePaginatedQuery<Message>(http, basePath + "/history", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, bodyHandler).get();
    }

    /************************************
     * Channel options
     ************************************/

    /**
     * Sets the {@link ChannelOptions} for the channel.
     * <p>
     * Spec: RTL16
     * @param options A {@link ChannelOptions} object.
     * @throws AblyException
     */
    public void setOptions(ChannelOptions options) throws AblyException {
        this.setOptions(options, null);
    }

    /**
     * Sets the {@link ChannelOptions} for the channel.
     * <p>
     * Spec: RTL16
     * @param options A {@link ChannelOptions} object.
     * @param listener An optional listener may be provided to notify of the success or failure of the operation.
     * @throws AblyException
     */
    public void setOptions(ChannelOptions options, CompletionListener listener) throws AblyException {
        this.options = options;
        this.messageEditsMixin = new MessageEditsMixin(basePath, ably.options, options, ably.auth);
        if(this.shouldReattachToSetOptions(options)) {
            this.attach(true, listener);
        } else {
            callCompletionListenerSuccess(listener);
        }
    }

    boolean shouldReattachToSetOptions(ChannelOptions options) {
        return
            (this.state == ChannelState.attached || this.state == ChannelState.attaching) &&
            (options.hasModes() || options.hasParams());
    }

    public Map<String, String> getParams() {
        return CollectionUtils.copy(params);
    }

    public ChannelMode[] getModes() {
        if (modes == null) {
            return new ChannelMode[0];
        }
        return modes.toArray(new ChannelMode[modes.size()]);
    }

    public ChannelOptions getOptions() {
        return options;
    }

    /************************************
     * internal general
     * @throws AblyException
     ************************************/

    private class ChannelStateCompletionListener implements ChannelStateListener {
        private CompletionListener completionListener;
        private final ChannelState successState;
        private final ChannelState failureState;

        ChannelStateCompletionListener(CompletionListener completionListener, ChannelState successState, ChannelState failureState) {
            this.completionListener = completionListener;
            this.successState = successState;
            this.failureState = failureState;
        }

        @Override
        public void onChannelStateChanged(ChannelStateListener.ChannelStateChange stateChange) {
            if(stateChange.current.equals(successState)) {
                ChannelBase.this.off(this);
                completionListener.onSuccess();
            }
            else if(stateChange.current.equals(failureState)) {
                ChannelBase.this.off(this);
                completionListener.onError(reason);
            }
        }
    }

    ChannelBase(RealtimeClient ably, String name, ChannelOptions options, @Nullable LiveObjectsPlugin liveObjectsPlugin) throws AblyException {
        Log.v(TAG, "RealtimeChannel(); channel = " + name);
        this.ably = ably;
        this.clock = SystemClock.clockFrom(ably.options);
        this.name = name;
        this.basePath = "/channels/" + HttpUtils.encodeURIComponent(name);
        this.setOptions(options);
        this.presence = new Presence((Channel) this);
        this.attachResume = false;
        state = ChannelState.initialized;
        this.decodingContext = new DecodingContext();
        this.liveObjectsPlugin = liveObjectsPlugin;
        if (liveObjectsPlugin != null) {
            this.object = liveObjectsPlugin.getInstance(name);
        } else {
            this.object = RealtimeObject.Unavailable.INSTANCE;
        }
        this.annotations = new RealtimeAnnotations(
            this,
            new HttpAnnotations(name, ably.http, ably.options, options)
        );
        this.messageEditsMixin = new MessageEditsMixin(basePath, ably.options, options, ably.auth);
    }

    void onChannelMessage(ProtocolMessage msg) {
        // RTL15b
        if (!StringUtils.isNullOrEmpty(msg.channelSerial) && (msg.action == Action.message ||
            msg.action == Action.presence || msg.action == Action.attached)) {
            Log.v(TAG, String.format(
                Locale.ROOT, "Setting channel serial for channelName - %s, previous - %s, current - %s",
                name, properties.channelSerial, msg.channelSerial));
            properties.channelSerial = msg.channelSerial;
        }

        switch(msg.action) {
        case attached:
            setAttached(msg);
            break;
        case detach:
        case detached:
            ChannelState oldState = state;
            switch(oldState) {
                // RTL13a
                case attached:
                case suspended:
                    /* Unexpected detach, reattach immediately as per RTL13a */
                    Log.v(TAG, String.format(Locale.ROOT, "Server initiated detach for channel %s; attempting reattach", name));
                    attachWithTimeout(true, null, msg.error);
                    break;
                case attaching:
                    /* RTL13b says we need to be suspended, but continue to retry */
                    Log.v(TAG, String.format(Locale.ROOT, "Server initiated detach for channel %s whilst attaching; moving to suspended", name));
                    setSuspended(msg.error, true);
                    reattachAfterTimeout();
                    break;
                case detaching:
                    setDetached((msg.error != null) ? msg.error : REASON_NOT_ATTACHED);
                    break;
                case detached:
                case failed:
                default:
                    /* do nothing */
                    break;
            }
            break;
        case message:
            if(state == ChannelState.attached) {
                onMessage(msg);
            } else {
                final String errorMsgPrefix = decodeFailureRecoveryInProgress ?
                    "Delta recovery in progress - message skipped." :
                    "Message skipped on a channel that is not ATTACHED.";

                // log messages skipped per RTL17
                for (final Message skippedMessage : msg.messages) {
                    Log.v(TAG, String.format(errorMsgPrefix + " Message id = %s, channel = %s", skippedMessage.id, name));
                }
            }
            break;
        case sync:
            presence.onSync(msg);
            break;
        case presence:
            presence.onPresence(msg);
            break;
        case error:
            setFailed(msg.error);
            break;
        case annotation:
            annotations.onAnnotation(msg);
            break;
        default:
            Log.e(TAG, "onChannelMessage(): Unexpected message action (" + msg.action + ")");
        }
    }

    /**
     * Emits UPDATE event
     * @param errorInfo
     */
    void emitUpdate(ErrorInfo errorInfo, boolean resumed) {
        if(state == ChannelState.attached)
            emit(ChannelEvent.update, ChannelStateListener.ChannelStateChange.createUpdateEvent(errorInfo, resumed));
    }

    public void emit(ChannelState state, ChannelStateListener.ChannelStateChange channelStateChange) {
        super.emit(state.getChannelEvent(), channelStateChange);
    }

    public void on(ChannelState state, ChannelStateListener listener) {
        super.on(state.getChannelEvent(), listener);
    }

    public void once(ChannelState state, ChannelStateListener listener) {
        super.once(state.getChannelEvent(), listener);
    }

    /**
     * (Internal) Sends a protocol message and provides a callback for completion.
     *
     * @param protocolMessage the protocol message to be sent
     * @param listener the listener to be notified upon completion of the message delivery
     */
    public void sendProtocolMessage(ProtocolMessage protocolMessage, CompletionListener listener) throws AblyException {
        ConnectionManager connectionManager = ably.connection.connectionManager;
        connectionManager.send(protocolMessage, ably.options.queueMessages, Listeners.fromCompletionListener(listener));
    }

    private static final String TAG = Channel.class.getName();
    final RealtimeClient ably;
    final Clock clock;
    final String basePath;
    ChannelOptions options;
    /**
     * Optional <a href="https://ably.com/docs/realtime/channels/channel-parameters/overview">channel parameters</a>
     * that configure the behavior of the channel.
     * <p>
     * Spec: RTL4k1
     */
    private Map<String, String> params;
    /**
     * An array of {@link ChannelMode} objects.
     * <p>
     * Spec: RTL4m
     */
    private Set<ChannelMode> modes;
    private String lastPayloadMessageId;
    private String lastPayloadProtocolMessageChannelSerial;
    private boolean decodeFailureRecoveryInProgress;
    private final DecodingContext decodingContext;
}
