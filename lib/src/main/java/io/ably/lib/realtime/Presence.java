package io.ably.lib.realtime;

import io.ably.lib.http.BasePaginatedQuery;
import io.ably.lib.http.Http;
import io.ably.lib.http.HttpCore;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.AsyncPaginatedResult;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.MessageDecodeException;
import io.ably.lib.types.MessageExtras;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import io.ably.lib.types.PresenceMessage;
import io.ably.lib.types.PresenceSerializer;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.types.PublishResult;
import io.ably.lib.util.Listeners;
import io.ably.lib.util.Log;
import io.ably.lib.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Enables the presence set to be entered and subscribed to, and the historic presence set to be retrieved for a channel.
 */
public class Presence {

    /************************************
     * subscriptions and PresenceListener
     ************************************/

    /**
     * String parameter names for get() call with Param... as an argument
     */
    public final static String GET_WAITFORSYNC = "waitForSync";
    public final static String GET_CLIENTID = "clientId";
    public final static String GET_CONNECTIONID = "connectionId";

    /**
     * Retrieves the current members present on the channel and the metadata for each member,
     * such as their {@link io.ably.lib.types.PresenceMessage.Action} and ID.
     * Returns an array of {@link PresenceMessage} objects.
     * <p>
     * Spec: RTP11
     * @param params the request params:
     * <p>
     * waitForSync (RTP11c1) - Sets whether to wait for a full presence set synchronization between Ably and the clients on
     *               the channel to complete before returning the results.
     *               Synchronization begins as soon as the channel is {@link ChannelState#attached}.
     *               When set to true the results will be returned as soon as the sync is complete.
     *               When set to false the current list of members will be returned without the sync completing.
     *               The default is true.
     * <p>
     * clientId (RTP11c2) - Filters the array of returned presence members by a specific client using its ID.
     * <p>
     * connectionId (RTP11c3) - Filters the array of returned presence members by a specific connection using its ID.
     * @return An array of {@link PresenceMessage} objects.
     * @throws AblyException
     */
    public synchronized PresenceMessage[] get(Param... params) throws AblyException {
        if (channel.state == ChannelState.failed) {
            throw AblyException.fromErrorInfo(new ErrorInfo("channel operation failed (invalid channel state)", 90001));
        }

        channel.attach();
        try {
            Collection<PresenceMessage> values = presence.get(params);
            return values.toArray(new PresenceMessage[values.size()]);
        } catch (InterruptedException e) {
            Log.v(TAG, String.format(Locale.ROOT, "Channel %s: get() operation interrupted", channel.name));
            throw AblyException.fromThrowable(e);
        }
    }

    /**
     * Retrieves the current members present on the channel and the metadata for each member,
     * such as their {@link io.ably.lib.types.PresenceMessage.Action} and ID.
     * Returns an array of {@link PresenceMessage} objects.
     * <p>
     * Spec: RTP11
     * @param wait (RTP11c1) - Sets whether to wait for a full presence set synchronization between Ably and the clients on
     *               the channel to complete before returning the results.
     *               Synchronization begins as soon as the channel is {@link ChannelState#attached}.
     *               When set to true the results will be returned as soon as the sync is complete.
     *               When set to false the current list of members will be returned without the sync completing.
     *               The default is true.
     * @return An array of {@link PresenceMessage} objects.
     * @throws AblyException
     */
    public synchronized PresenceMessage[] get(boolean wait) throws AblyException {
        return get(new Param(GET_WAITFORSYNC, String.valueOf(wait)));
    }

    /**
     * Retrieves the current members present on the channel and the metadata for each member,
     * such as their {@link io.ably.lib.types.PresenceMessage.Action} and ID.
     * Returns an array of {@link PresenceMessage} objects.
     * <p>
     * Spec: RTP11
     * @param clientId (RTP11c2) - Filters the array of returned presence members by a specific client using its ID.
     * @param wait (RTP11c1) - Sets whether to wait for a full presence set synchronization between Ably and the clients on
     *               the channel to complete before returning the results.
     *               Synchronization begins as soon as the channel is {@link ChannelState#attached}.
     *               When set to true the results will be returned as soon as the sync is complete.
     *               When set to false the current list of members will be returned without the sync completing.
     *               The default is true.
     * @return An array of {@link PresenceMessage} objects.
     * @throws AblyException
     */
    public synchronized PresenceMessage[] get(String clientId, boolean wait) throws AblyException {
        return get(new Param(GET_WAITFORSYNC, String.valueOf(wait)), new Param(GET_CLIENTID, clientId));
    }

    void addPendingPresence(PresenceMessage presenceMessage, Callback<PublishResult> listener) {
        synchronized(channel) {
            final QueuedPresence queuedPresence = new QueuedPresence(presenceMessage, Listeners.unwrap(listener));
            pendingPresence.add(queuedPresence);
        }
    }

    /**
     * An interface allowing a listener to be notified of arrival of a presence message.
     */
    public interface PresenceListener {
        void onPresenceMessage(PresenceMessage message);
    }

    /**
     * Registers a listener that is called each time a {@link PresenceMessage} matching a given {@link PresenceMessage.Action},
     * or an action within an array of {@link PresenceMessage.Action}, is received on the channel,
     * such as a new member entering the presence set.
     *
     * <p>
     * Spec: RTP6a
     *
     * @param listener An event listener function.
     * @param completionListener A callback to be notified of success or failure of the channel {@link Channel#attach()} operation.
     * <p></p>
     * These listeners are invoked on a background thread.
     * @throws AblyException
     */
    public void subscribe(PresenceListener listener, CompletionListener completionListener) throws AblyException {
        implicitAttachOnSubscribe(completionListener);
        listeners.add(listener);
    }

    /**
     * Registers a listener that is called each time a {@link PresenceMessage} matching a given {@link PresenceMessage.Action},
     * or an action within an array of {@link PresenceMessage.Action}, is received on the channel,
     * such as a new member entering the presence set.
     *
     * <p>
     * Spec: RTP6a
     *
     * @param listener An event listener function.
     * <p>
     * This listener is invoked on a background thread.
     * @throws AblyException
     */
    public void subscribe(PresenceListener listener) throws AblyException {
        subscribe(listener, null);
    }

    /**
     * Deregisters a specific listener that is registered to receive {@link PresenceMessage} on the channel.
     * <p>
     * Spec: RTP7a
     * @param listener An event listener function.
     */
    public void unsubscribe(PresenceListener listener) {
        listeners.remove(listener);
        for (Multicaster multicaster: eventListeners.values()) {
            multicaster.remove(listener);
        }
    }

    /**
     * Registers a listener that is called each time a {@link PresenceMessage} matching a given {@link PresenceMessage.Action},
     * or an action within an array of {@link PresenceMessage.Action}, is received on the channel,
     * such as a new member entering the presence set.
     *
     * <p>
     * Spec: RTP6b
     *
     * @param action A {@link PresenceMessage.Action} to register the listener for.
     * @param listener An event listener function.
     * @param completionListener A callback to be notified of success or failure of the channel {@link Channel#attach()} operation.
     * <p></p>
     * These listeners are invoked on a background thread.
     * @throws AblyException
     */
    public void subscribe(PresenceMessage.Action action, PresenceListener listener, CompletionListener completionListener) throws AblyException {
        implicitAttachOnSubscribe(completionListener);
        subscribeImpl(action, listener);
    }

    /**
     * Registers a listener that is called each time a {@link PresenceMessage} matching a given {@link PresenceMessage.Action},
     * or an action within an array of {@link PresenceMessage.Action}, is received on the channel,
     * such as a new member entering the presence set.
     *
     * <p>
     * Spec: RTP6b
     *
     * @param action A {@link PresenceMessage.Action} to register the listener for.
     * @param listener An event listener function.
     * <p>
     * This listener is invoked on a background thread.
     * @throws AblyException
     */
    public void subscribe(PresenceMessage.Action action, PresenceListener listener) throws AblyException {
        subscribe(action, listener, null);
    }

    /**
     * Deregisters a specific listener that is registered to receive
     * {@link PresenceMessage} on the channel for a given {@link PresenceMessage.Action}.
     * <p>
     * Spec: RTP7b
     * @param action A specific {@link PresenceMessage.Action} to deregister the listener for.
     * @param listener An event listener function.
     */
    public void unsubscribe(PresenceMessage.Action action, PresenceListener listener) {
        unsubscribeImpl(action, listener);
    }

    /**
     * Registers a listener that is called each time a {@link PresenceMessage} matching a given {@link PresenceMessage.Action},
     * or an action within an array of {@link PresenceMessage.Action}, is received on the channel,
     * such as a new member entering the presence set.
     *
     * <p>
     * Spec: RTP6b
     *
     * @param actions An array of {@link PresenceMessage.Action} to register the listener for.
     * @param listener An event listener function.
     * @param completionListener A callback to be notified of success or failure of the channel {@link Channel#attach()} operation.
     * <p></p>
     * These listeners are invoked on a background thread.
     * @throws AblyException
     */
    public void subscribe(EnumSet<PresenceMessage.Action> actions, PresenceListener listener, CompletionListener completionListener) throws AblyException {
        implicitAttachOnSubscribe(completionListener);
        for (PresenceMessage.Action action : actions) {
            subscribeImpl(action, listener);
        }
    }

    /**
     * Registers a listener that is called each time a {@link PresenceMessage} matching a given {@link PresenceMessage.Action},
     * or an action within an array of {@link PresenceMessage.Action}, is received on the channel,
     * such as a new member entering the presence set.
     *
     * <p>
     * Spec: RTP6b
     *
     * @param actions An array of {@link PresenceMessage.Action} to register the listener for.
     * @param listener An event listener function.
     * <p></p>
     * These listeners are invoked on a background thread.
     * @throws AblyException
     */
    public void subscribe(EnumSet<PresenceMessage.Action> actions, PresenceListener listener) throws AblyException {
        subscribe(actions, listener, null);
    }

    /**
     * Deregisters a specific listener that is registered to receive
     * {@link PresenceMessage} on the channel for a given {@link PresenceMessage.Action}.
     * <p>
     * Spec: RTP7b
     * @param actions An array of specific {@link PresenceMessage.Action} to deregister the listener for.
     * @param listener An event listener function.
     */
    public void unsubscribe(EnumSet<PresenceMessage.Action> actions, PresenceListener listener) {
        for (PresenceMessage.Action action : actions) {
            unsubscribeImpl(action, listener);
        }
    }

    /**
     * Deregisters all listeners currently receiving {@link PresenceMessage} for the channel.
     * <p>
     * Spec: RTP7a, RTE5
     */
    public void unsubscribe() {
        listeners.clear();
        eventListeners.clear();
    }

    /**
     * Implicitly attach channel on subscribe. Throw exception if channel is in failed state.
     * @param completionListener Registers listener, gets called when ATTACH operation is a success.
     * @throws AblyException Throws exception when channel is in failed state.
     */
    private void implicitAttachOnSubscribe(CompletionListener completionListener) throws AblyException {
        // RTP6e
        if (!channel.attachOnSubscribeEnabled()) {
            if (completionListener != null) {
                String errorString = String.format(
                    "Channel %s: attachOnSubscribe=false doesn't expect attach completion callback", channel.name);
                Log.e(TAG, errorString);
                ErrorInfo errorInfo = new ErrorInfo(errorString, 400,40000);
                throw AblyException.fromErrorInfo(errorInfo);
            }
            return;
        }
        if (channel.state == ChannelState.failed) {
            String errorString = String.format(Locale.ROOT, "Channel %s: subscribe in FAILED channel state", channel.name);
            Log.e(TAG, errorString);
            ErrorInfo errorInfo = new ErrorInfo(errorString, 90001);
            throw AblyException.fromErrorInfo(errorInfo);
        }
        channel.attach(completionListener);
    }

    /* End sync and emit leave messages for residual members */
    private void endSync() {
        List<PresenceMessage> residualMembers = presence.endSync();
        for (PresenceMessage member: residualMembers) {         // RTP19
            member.action = PresenceMessage.Action.leave;
            member.id = null;
            member.timestamp = channel.clock.currentTimeMillis();
        }
        broadcastPresence(residualMembers);
    }

    private void updateInnerPresenceMessageFields(ProtocolMessage message) {
        for(int i = 0; i < message.presence.length; i++) {
            PresenceMessage msg = message.presence[i];
            try {
                msg.decode(channel.options);
            } catch (MessageDecodeException e) {
                Log.e(TAG, String.format(Locale.ROOT, "%s on channel %s", e.errorInfo.message, channel.name));
            }
            /* populate fields derived from protocol message */
            if(msg.connectionId == null) msg.connectionId = message.connectionId;
            if(msg.timestamp == 0) msg.timestamp = message.timestamp;
            if(msg.id == null) msg.id = message.id + ':' + i;
        }
    }

    void onSync(ProtocolMessage protocolMessage) {
        String syncCursor = null;
        String syncChannelSerial = protocolMessage.channelSerial;
        // RTP18a
        if(!StringUtils.isNullOrEmpty(syncChannelSerial)) {
            String[] serials = syncChannelSerial.split(":");
            String syncSequenceId = serials[0];
            syncCursor = serials.length > 1 ? serials[1] : "";

            /* If a new sequence identifier is sent from Ably, then the client library
             * must consider that to be the start of a new sync sequence
             * and any previous in-flight sync should be discarded. (part of RTP18)*/
            if (presence.syncInProgress && !StringUtils.isNullOrEmpty(currentSyncChannelSerial)
                && !currentSyncChannelSerial.equals(syncSequenceId)) {
                endSync();
            }

            presence.startSync();

            if (!StringUtils.isNullOrEmpty(syncCursor))
            {
                currentSyncChannelSerial = syncSequenceId;
            }
        }

        onPresence(protocolMessage);

        // RTP18b, RTP18c
        if (StringUtils.isNullOrEmpty(syncChannelSerial) || StringUtils.isNullOrEmpty(syncCursor))
        {
            endSync();
            currentSyncChannelSerial = null;
        }
    }

    void onPresence(ProtocolMessage protocolMessage) {
        updateInnerPresenceMessageFields(protocolMessage);
        List<PresenceMessage> updatedPresenceMessages = new ArrayList<>();
        for(PresenceMessage presenceMessage : protocolMessage.presence) {
            boolean updateInternalPresence = presenceMessage.connectionId.equals(channel.ably.connection.id);
            boolean memberUpdated = false;

            switch(presenceMessage.action) {
                case enter:
                case update:
                case present:
                    PresenceMessage shallowPresenceCopy = (PresenceMessage)presenceMessage.clone();
                    shallowPresenceCopy.action = PresenceMessage.Action.present;
                    memberUpdated = presence.put(shallowPresenceCopy);
                    if(updateInternalPresence)
                        internalPresence.put(presenceMessage);
                    break;
                case leave:
                    memberUpdated = presence.remove(presenceMessage);
                    if(updateInternalPresence)
                        internalPresence.remove(presenceMessage);
                    break;
                case absent:
            }
            if (memberUpdated) {
                updatedPresenceMessages.add(presenceMessage);
            }
        }
        /*
         * RTP2g: Any incoming presence message that passes the newness check should be emitted on the
         * Presence object, with an event name set to its original action.
         */
        broadcastPresence(updatedPresenceMessages);
    }

    private void broadcastPresence(List<PresenceMessage> messages) {
        for(PresenceMessage message : messages) {
            listeners.onPresenceMessage(message);

            Multicaster eventListener = eventListeners.get(message.action);
            if(eventListener != null)
                eventListener.onPresenceMessage(message);
        }
    }

    private final Multicaster listeners = new Multicaster();
    private final EnumMap<PresenceMessage.Action, Multicaster> eventListeners = new EnumMap<>(PresenceMessage.Action.class);

    private static class Multicaster extends io.ably.lib.util.Multicaster<PresenceListener> implements PresenceListener {
        @Override
        public void onPresenceMessage(PresenceMessage message) {
            for (final PresenceListener member : getMembers())
                try {
                    member.onPresenceMessage(message);
                } catch(Throwable t) {}
        }
    }

    private void subscribeImpl(PresenceMessage.Action action, PresenceListener listener) {
        Multicaster listeners = eventListeners.get(action);
        if(listeners == null) {
            listeners = new Multicaster();
            eventListeners.put(action, listeners);
        }
        listeners.add(listener);
    }

    private void unsubscribeImpl(PresenceMessage.Action action, PresenceListener listener) {
        Multicaster listeners = eventListeners.get(action);
        if(listeners != null) {
            listeners.remove(listener);
            if(listeners.isEmpty()) {
                eventListeners.remove(action);
            }
        }
    }


    /************************************
     * enter/leave and pending messages
     ************************************/

    /**
     * Enters the presence set for the channel, optionally passing a data payload.
     * A clientId is required to be present on a channel.
     * An optional callback may be provided to notify of the success or failure of the operation.
     *
     * <p>
     * Spec: RTP8
     *
     * @param data The payload associated with the presence member.
     * @param listener An callback to notify of the success or failure of the operation.
     * <p>
     * This listener is invoked on a background thread.
     * @throws AblyException
     */
    public void enter(Object data, CompletionListener listener) throws AblyException {
        enter(data, null, listener);
    }

    /**
     * Enters the presence set for the channel, optionally passing a data payload and extras.
     * A clientId is required to be present on a channel.
     * An optional callback may be provided to notify of the success or failure of the operation.
     *
     * <p>
     * Spec: RTP8
     *
     * @param data The payload associated with the presence member.
     * @param extras The extras associated with the presence member.
     * @param listener A callback to notify of the success or failure of the operation.
     * <p>
     * This listener is invoked on a background thread.
     * @throws AblyException
     */
    public void enter(Object data, MessageExtras extras, CompletionListener listener) throws AblyException {
        Log.v(TAG, "enter(); channel = " + channel.name);
        updatePresence(new PresenceMessage(PresenceMessage.Action.enter, null, data, extras), listener);
    }

    /**
     * Updates the data payload for a presence member.
     * If called before entering the presence set, this is treated as an {@link PresenceMessage.Action#enter} event.
     * An optional callback may be provided to notify of the success or failure of the operation.
     *
     * <p>
     * Spec: RTP9
     *
     * @param data The payload associated with the presence member.
     * @param listener An callback to notify of the success or failure of the operation.
     * <p>
     * This listener is invoked on a background thread.
     * @throws AblyException
     */
    public void update(Object data, CompletionListener listener) throws AblyException {
        update(data, null, listener);
    }

    /**
     * Updates the data payload for a presence member, optionally passing extras.
     * If called before entering the presence set, this is treated as an {@link PresenceMessage.Action#enter} event.
     * An optional callback may be provided to notify of the success or failure of the operation.
     *
     * <p>
     * Spec: RTP9
     *
     * @param data The payload associated with the presence member.
     * @param extras The extras associated with the presence member.
     * @param listener A callback to notify of the success or failure of the operation.
     * <p>
     * This listener is invoked on a background thread.
     * @throws AblyException
     */
    public void update(Object data, MessageExtras extras, CompletionListener listener) throws AblyException {
        Log.v(TAG, "update(); channel = " + channel.name);
        updatePresence(new PresenceMessage(PresenceMessage.Action.update, null, data, extras), listener);
    }

    /**
     * Leaves the presence set for the channel.
     * A client must have previously entered the presence set before they can leave it.
     *
     * <p>
     * Spec: RTP10
     *
     * @param data The payload associated with the presence member.
     * @param listener a listener to notify of the success or failure of the operation.
     * <p>
     * This listener is invoked on a background thread.
     * @throws AblyException
     */
    public void leave(Object data, CompletionListener listener) throws AblyException {
        leave(data, null, listener);
    }

    /**
     * Leaves the presence set for the channel, optionally passing extras.
     * A client must have previously entered the presence set before they can leave it.
     *
     * <p>
     * Spec: RTP10
     *
     * @param data The payload associated with the presence member.
     * @param extras The extras associated with the presence member.
     * @param listener a listener to notify of the success or failure of the operation.
     * <p>
     * This listener is invoked on a background thread.
     * @throws AblyException
     */
    public void leave(Object data, MessageExtras extras, CompletionListener listener) throws AblyException {
        Log.v(TAG, "leave(); channel = " + channel.name);
        updatePresence(new PresenceMessage(PresenceMessage.Action.leave, null, data, extras), listener);
    }

    /**
     * Leaves the presence set for the channel.
     * A client must have previously entered the presence set before they can leave it.
     *
     * <p>
     * Spec: RTP10
     *
     * @param listener a listener to notify of the success or failure of the operation.
     * <p>
     * This listener is invoked on a background thread.
     * @throws AblyException
     */
    public void leave(CompletionListener listener) throws AblyException {
        leave(null, listener);
    }

    /**
     * Enters the presence set of the channel for a given clientId.
     * Enables a single client to update presence on behalf of any number of clients using a single connection.
     * The library must have been instantiated with an API key or a token bound to a wildcard clientId.
     *
     * <p>
     * Spec: RTP4, RTP14, RTP15
     *
     * @param clientId The ID of the client to enter into the presence set.
     */
    public void enterClient(String clientId) throws AblyException {
        enterClient(clientId, null);
    }

    /**
     * Enters the presence set of the channel for a given clientId.
     * Enables a single client to update presence on behalf of any number of clients using a single connection.
     * The library must have been instantiated with an API key or a token bound to a wildcard clientId.
     *
     * <p>
     * Spec: RTP4, RTP14, RTP15
     *
     * @param clientId The ID of the client to enter into the presence set.
     * @param data The payload associated with the presence member.
     */
    public void enterClient(String clientId, Object data) throws AblyException {
        enterClient(clientId, data, null);
    }

    /**
     * Enters the presence set of the channel for a given clientId.
     * Enables a single client to update presence on behalf of any number of clients using a single connection.
     * The library must have been instantiated with an API key or a token bound to a wildcard clientId.
     *
     * <p>
     * Spec: RTP4, RTP14, RTP15
     *
     * @param clientId The ID of the client to enter into the presence set.
     * @param data The payload associated with the presence member.
     * @param listener An callback to notify of the success or failure of the operation.
     * <p>
     * This listener is invoked on a background thread.
     */
    public void enterClient(String clientId, Object data, CompletionListener listener) throws AblyException {
        enterClient(clientId, data, null, listener);
    }

    /**
     * Enters the presence set of the channel for a given clientId, optionally passing extras.
     * Enables a single client to update presence on behalf of any number of clients using a single connection.
     * The library must have been instantiated with an API key or a token bound to a wildcard clientId.
     *
     * <p>
     * Spec: RTP4, RTP14, RTP15
     *
     * @param clientId The ID of the client to enter into the presence set.
     * @param data The payload associated with the presence member.
     * @param extras The extras associated with the presence member.
     * @param listener A callback to notify of the success or failure of the operation.
     * <p>
     * This listener is invoked on a background thread.
     */
    public void enterClient(String clientId, Object data, MessageExtras extras, CompletionListener listener) throws AblyException {
        if(clientId == null) {
            String errorMessage = String.format(Locale.ROOT, "Channel %s: unable to enter presence channel (null clientId specified)", channel.name);
            Log.v(TAG, errorMessage);
            if(listener != null) {
                listener.onError(new ErrorInfo(errorMessage, 40000));
                return;
            }
        }
        Log.v(TAG, "enterClient(); channel = " + channel.name + "; clientId = " + clientId);
        updatePresence(new PresenceMessage(PresenceMessage.Action.enter, clientId, data, extras), listener);
    }

    private void enterClientWithId(String id, String clientId, Object data, CompletionListener listener) throws AblyException {
        if(clientId == null) {
            String errorMessage = String.format(Locale.ROOT, "Channel %s: unable to enter presence channel (null clientId specified)", channel.name);
            Log.v(TAG, errorMessage);
            if(listener != null) {
                listener.onError(new ErrorInfo(errorMessage, 40000));
                return;
            }
        }
        PresenceMessage presenceMsg = new PresenceMessage(PresenceMessage.Action.enter, clientId, data);
        presenceMsg.id = id;
        Log.v(TAG, "enterClient(); channel = " + channel.name + "; clientId = " + clientId);
        updatePresence(presenceMsg, listener);
    }

    /**
     * Updates the data payload for a presence member using a given clientId.
     * Enables a single client to update presence on behalf of any number of clients using a single connection.
     * The library must have been instantiated with an API key or a token bound to a wildcard clientId.
     * An optional callback may be provided to notify of the success or failure of the operation.
     *
     * <p>
     * Spec: RTP15
     *
     * @param clientId The ID of the client to update in the presence set.
     */
    public void updateClient(String clientId) throws AblyException {
        updateClient(clientId, null);
    }

    /**
     * Updates the data payload for a presence member using a given clientId.
     * Enables a single client to update presence on behalf of any number of clients using a single connection.
     * The library must have been instantiated with an API key or a token bound to a wildcard clientId.
     * An optional callback may be provided to notify of the success or failure of the operation.
     *
     * <p>
     * Spec: RTP15
     *
     * @param clientId The ID of the client to update in the presence set.
     * @param data The payload to update for the presence member.
     */
    public void updateClient(String clientId, Object data) throws AblyException {
        updateClient(clientId, data, null);
    }

    /**
     * Updates the data payload for a presence member using a given clientId.
     * Enables a single client to update presence on behalf of any number of clients using a single connection.
     * The library must have been instantiated with an API key or a token bound to a wildcard clientId.
     * An optional callback may be provided to notify of the success or failure of the operation.
     *
     * <p>
     * Spec: RTP15
     *
     * @param clientId The ID of the client to update in the presence set.
     * @param data The payload to update for the presence member.
     * @param listener An callback to notify of the success or failure of the operation.
     * <p>
     * This listener is invoked on a background thread.
     */
    public void updateClient(String clientId, Object data, CompletionListener listener) throws AblyException {
        updateClient(clientId, data, null, listener);
    }

    /**
     * Updates the data payload for a presence member using a given clientId, optionally passing extras.
     * Enables a single client to update presence on behalf of any number of clients using a single connection.
     * The library must have been instantiated with an API key or a token bound to a wildcard clientId.
     * An optional callback may be provided to notify of the success or failure of the operation.
     *
     * <p>
     * Spec: RTP15
     *
     * @param clientId The ID of the client to update in the presence set.
     * @param data The payload to update for the presence member.
     * @param extras The extras associated with the presence member.
     * @param listener A callback to notify of the success or failure of the operation.
     * <p>
     * This listener is invoked on a background thread.
     */
    public void updateClient(String clientId, Object data, MessageExtras extras, CompletionListener listener) throws AblyException {
        if(clientId == null) {
            String errorMessage = String.format(Locale.ROOT, "Channel %s: unable to update presence channel (null clientId specified)", channel.name);
            Log.v(TAG, errorMessage);
            if(listener != null) {
                listener.onError(new ErrorInfo(errorMessage, 40000));
                return;
            }
        }
        Log.v(TAG, "updateClient(); channel = " + channel.name + "; clientId = " + clientId);
        updatePresence(new PresenceMessage(PresenceMessage.Action.update, clientId, data, extras), listener);
    }

    /**
     * Leaves the presence set of the channel for a given clientId.
     * Enables a single client to update presence on behalf of any number of clients using a single connection.
     * The library must have been instantiated with an API key or a token bound to a wildcard clientId.
     *
     * <p>
     * Spec: RTP15
     *
     * @param clientId The ID of the client to leave the presence set for.
     */
    public void leaveClient(String clientId) throws AblyException {
        leaveClient(clientId, null);
    }

    /**
     * Leaves the presence set of the channel for a given clientId.
     * Enables a single client to update presence on behalf of any number of clients using a single connection.
     * The library must have been instantiated with an API key or a token bound to a wildcard clientId.
     *
     * <p>
     * Spec: RTP15
     *
     * @param clientId The ID of the client to leave the presence set for.
     * @param data The payload associated with the presence member.
     */
    public void leaveClient(String clientId, Object data) throws AblyException {
        leaveClient(clientId, data, null);
    }

    /**
     * Leaves the presence set of the channel for a given clientId.
     * Enables a single client to update presence on behalf of any number of clients using a single connection.
     * The library must have been instantiated with an API key or a token bound to a wildcard clientId.
     *
     * <p>
     * Spec: RTP15
     *
     * @param clientId The ID of the client to leave the presence set for.
     * @param data The payload associated with the presence member.
     * @param listener An callback to notify of the success or failure of the operation.
     * <p>
     * This listener is invoked on a background thread.
     */
    public void leaveClient(String clientId, Object data, CompletionListener listener) throws AblyException {
        leaveClient(clientId, data, null, listener);
    }

    /**
     * Leaves the presence set of the channel for a given clientId, optionally passing extras.
     * Enables a single client to update presence on behalf of any number of clients using a single connection.
     * The library must have been instantiated with an API key or a token bound to a wildcard clientId.
     *
     * <p>
     * Spec: RTP15
     *
     * @param clientId The ID of the client to leave the presence set for.
     * @param data The payload associated with the presence member.
     * @param extras The extras associated with the presence member.
     * @param listener A callback to notify of the success or failure of the operation.
     * <p>
     * This listener is invoked on a background thread.
     */
    public void leaveClient(String clientId, Object data, MessageExtras extras, CompletionListener listener) throws AblyException {
        if(clientId == null) {
            String errorMessage = String.format(Locale.ROOT, "Channel %s: unable to leave presence channel (null clientId specified)", channel.name);
            Log.v(TAG, errorMessage);
            if(listener != null) {
                listener.onError(new ErrorInfo(errorMessage, 40000));
                return;
            }
        }
        Log.v(TAG, "leaveClient(); channel = " + channel.name + "; clientId = " + clientId);
        updatePresence(new PresenceMessage(PresenceMessage.Action.leave, clientId, data, extras), listener);
    }

    /**
     * Update the presence for this channel with a given PresenceMessage update.
     * The connection must be authenticated in a way that enables it to represent
     * the clientId in the message.
     *
     * @param msg the presence message
     * @param listener a listener to be notified on completion of the operation.
     * <p>
     * This listener is invoked on a background thread.
     * @throws AblyException
     */
    public void updatePresence(PresenceMessage msg, CompletionListener listener) throws AblyException {
        Log.v(TAG, "updatePresence(); channel = " + channel.name);

        AblyRealtime ably = channel.ably;
        boolean connected = (ably.connection.state == ConnectionState.connected);
        try {
            ably.auth.checkClientId(msg, false, connected);
        } catch(AblyException e) {
            if(listener != null) {
                listener.onError(e.errorInfo);
            }
            return;
        }

        msg.encode(null);
        synchronized(channel) {
            switch(channel.state) {
            case initialized:
                channel.attach();
            case attaching:
                Log.v(TAG, "updatePresence(); put message in pending presence queue");
                pendingPresence.add(new QueuedPresence(msg, listener));
                break;
            case attached:
                Log.v(TAG, "updatePresence(); send message to connection manager");
                ProtocolMessage message = new ProtocolMessage(ProtocolMessage.Action.presence, channel.name);
                message.presence = new PresenceMessage[] { msg };
                ConnectionManager connectionManager = ably.connection.connectionManager;
                connectionManager.send(message, ably.options.queueMessages, Listeners.fromCompletionListener(listener));
                break;
            default:
                throw AblyException.fromErrorInfo(new ErrorInfo("Unable to enter presence channel in detached or failed state", 400, 91001));
            }
        }
    }

    /************************************
     * history
     ************************************/

    /**
     * Retrieves a {@link PaginatedResult} object, containing an array of historical {@link PresenceMessage} objects for the channel.
     * If the channel is configured to persist messages,
     * then presence messages can be retrieved from history for up to 72 hours in the past.
     * If not, presence messages can only be retrieved from history for up to two minutes in the past.
     * <p>
     * Spec: RTP12c
     * @param params the request params:
     * <p>
     * start (RTP12a) - The time from which messages are retrieved, specified as milliseconds since the Unix epoch.
     * <p>
     * end (RTP12a) - The time until messages are retrieved, specified as milliseconds since the Unix epoch.
     * <p>
     * direction (RTP12a) - The order for which messages are returned in.
     *               Valid values are backwards which orders messages from most recent to oldest,
     *               or forwards which orders messages from oldest to most recent.
     *               The default is backwards.
     * limit (RTP12a) - An upper limit on the number of messages returned. The default is 100, and the maximum is 1000.
     * @return A {@link PaginatedResult} object containing an array of {@link PresenceMessage} objects.
     * @throws AblyException
     */
    public PaginatedResult<PresenceMessage> history(Param[] params) throws AblyException {
        return history(channel.ably.http, params);
    }

    PaginatedResult<PresenceMessage> history(Http http, Param[] params) throws AblyException {
        return historyImpl(http, params).sync();
    }

    /**
     * Asynchronously retrieves a {@link PaginatedResult} object, containing an array of historical {@link PresenceMessage} objects for the channel.
     * If the channel is configured to persist messages,
     * then presence messages can be retrieved from history for up to 72 hours in the past.
     * If not, presence messages can only be retrieved from history for up to two minutes in the past.
     * <p>
     * Spec: RTP12c
     * @param params the request params:
     * <p>
     * start (RTP12a) - The time from which messages are retrieved, specified as milliseconds since the Unix epoch.
     * <p>
     * end (RTP12a) - The time until messages are retrieved, specified as milliseconds since the Unix epoch.
     * <p>
     * direction (RTP12a) - The order for which messages are returned in.
     *               Valid values are backwards which orders messages from most recent to oldest,
     *               or forwards which orders messages from oldest to most recent.
     *               The default is backwards.
     * limit (RTP12a) - An upper limit on the number of messages returned. The default is 100, and the maximum is 1000.
     * @param callback  A Callback returning {@link AsyncPaginatedResult} object containing an array of {@link PresenceMessage} objects.
     * <p>
     * This callback is invoked on a background thread.
     * @throws AblyException
     */
    public void historyAsync(Param[] params, Callback<AsyncPaginatedResult<PresenceMessage>> callback) {
        historyImpl(channel.ably.http, params).async(callback);
    }

    void historyAsync(Http http, Param[] params, Callback<AsyncPaginatedResult<PresenceMessage>> callback) {
        historyImpl(http, params).async(callback);
    }

    private BasePaginatedQuery.ResultRequest<PresenceMessage> historyImpl(Http http, Param[] params) {
        try {
            params = Channel.replacePlaceholderParams(channel, params);
        } catch (AblyException e) {
            return new BasePaginatedQuery.ResultRequest.Failed<PresenceMessage>(e);
        }

        AblyRealtime ably = channel.ably;
        HttpCore.BodyHandler<PresenceMessage> bodyHandler = PresenceSerializer.getPresenceResponseHandler(channel.options);
        return new BasePaginatedQuery<PresenceMessage>(http, channel.basePath + "/presence/history", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, bodyHandler).get();
    }

    /**
     * internal
     *
     */
    private static class QueuedPresence {
        public PresenceMessage msg;
        public CompletionListener listener;
        QueuedPresence(PresenceMessage msg, CompletionListener listener) { this.msg = msg; this.listener = listener; }
    }

    private final List<QueuedPresence> pendingPresence = new ArrayList<QueuedPresence>();

    private void sendQueuedMessages() {
        Log.v(TAG, "sendQueuedMessages()");
        AblyRealtime ably = channel.ably;
        boolean queueMessages = ably.options.queueMessages;
        ConnectionManager connectionManager = ably.connection.connectionManager;
        int count = pendingPresence.size();
        if(count == 0)
            return;

        ProtocolMessage message = new ProtocolMessage(ProtocolMessage.Action.presence, channel.name);
        Iterator<QueuedPresence> allQueued = pendingPresence.iterator();
        PresenceMessage[] presenceMessages = message.presence = new PresenceMessage[count];
        CompletionListener listener;

        if(count == 1) {
            QueuedPresence queued = allQueued.next();
            presenceMessages[0] = queued.msg;
            listener = queued.listener;
        } else {
            int idx = 0;
            CompletionListener.Multicaster mListener = new CompletionListener.Multicaster();
            while(allQueued.hasNext()) {
                QueuedPresence queued = allQueued.next();
                presenceMessages[idx++] = queued.msg;
                if(queued.listener != null)
                    mListener.add(queued.listener);
            }
            listener = mListener.isEmpty() ? null : mListener;
        }

        pendingPresence.clear();

        try {
            connectionManager.send(message, queueMessages, Listeners.fromCompletionListener(listener));
        } catch(AblyException e) {
            Log.e(TAG, "sendQueuedMessages(): Unexpected exception sending message", e);
            if(listener != null)
                listener.onError(e.errorInfo);
        }
    }

    private void failQueuedMessages(ErrorInfo reason) {
        Log.v(TAG, "failQueuedMessages()");
        for(QueuedPresence msg : pendingPresence)
            if(msg.listener != null)
                try {
                    msg.listener.onError(reason);
                } catch(Throwable t) {
                    Log.e(TAG, "failQueuedMessages(): Unexpected exception calling listener", t);
                }
        pendingPresence.clear();
    }


    /************************************
     * attach / detach
     ************************************/

    void onAttached(boolean hasPresence) {
        presence.startSync();
        if (!hasPresence) { // RTP19a
            endSync();
        }
        sendQueuedMessages(); // RTP5b
        enterInternalMembers(); // RTP17f
    }

    /**
     * Spec: RTP17g
     */
    void enterInternalMembers() {
        for (final PresenceMessage item: internalPresence.members.values()) {
            try {
                enterClientWithId(item.id, item.clientId, item.data, new CompletionListener() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onError(ErrorInfo reason) {
                        String errorString = String.format(Locale.ROOT, "Cannot automatically re-enter %s on channel %s (%s)",
                            item.clientId, channel.name, reason == null ? "" : reason.message);
                        Log.e(TAG, errorString);
                        channel.emitUpdate(new ErrorInfo(errorString, 91004), true);
                    }
                });
            } catch(AblyException e) {
                String errorString = String.format(Locale.ROOT, "Cannot automatically re-enter %s on channel %s (%s)",
                    item.clientId, channel.name, e.errorInfo.message);
                Log.e(TAG, errorString);
                channel.emitUpdate(new ErrorInfo(errorString, 91004), true);
            }
        }
    }

    // RTP5a
    void onChannelDetachedOrFailed(ErrorInfo reason) {
        /* Interrupt get() call if needed */
        synchronized (presence) {
            presence.notifyAll();
        }

        presence.clear();
        internalPresence.clear();
        failQueuedMessages(reason);
    }

    // RTP5f, RTP16b
    void onChannelSuspended(ErrorInfo reason) {
        /* Interrupt get() call if needed */
        synchronized (presence) {
            presence.notifyAll();
        }

        failQueuedMessages(reason);
    }

    /**
     * A class encapsulating a map of the members of this presence channel,
     * indexed by a String key that is a combination of connectionId and clientId.
     * This map synchronises the membership of the presence set by handling
     * sync messages from the service. Since sync messages can be out-of-order -
     * eg an enter sync event being received after that member has in fact left -
     * this map keeps "witness" entries, with absent Action, to remember the
     * fact that a leave event has been seen for a member. These entries are
     * cleared once the last set of updates of a sync sequence have been received.
     *
     */
    private class PresenceMap {

        /**
         * Wait for sync to be complete. If we are in attaching state wait for initial sync to
         * complete as well. Return false if wait was interrupted because channel transitioned to
         * state other than attached or attaching
         */
        synchronized void waitForSync() throws AblyException, InterruptedException {
            boolean syncIsComplete = false;    /* temporary variable to avoid potential race conditions */
            while (channel.state == ChannelState.attaching) {
                wait();
            }
            if (channel.state == ChannelState.attached) {
                do {
                    syncIsComplete = !syncInProgress && syncComplete;
                    if (!syncIsComplete) {
                        wait();
                    }
                } while (!syncIsComplete);
            }

            /* invalid channel state */
            int errorCode;
            String errorMessage;

            if (channel.state == ChannelState.suspended) {
                /* (RTP11d) If the Channel is in the SUSPENDED state then the get function will by default,
                 * or if waitForSync is set to true, result in an error with code 91005 and a message stating
                 * that the presence state is out of sync due to the channel being in a SUSPENDED state */
                errorCode = 91005;
                errorMessage = String.format(Locale.ROOT, "Channel %s: presence state is out of sync due to the channel being in a SUSPENDED state", channel.name);
            } else if(syncIsComplete) {
                return;
            } else {
                errorCode = 90001;
                errorMessage = String.format(Locale.ROOT, "Channel %s: cannot get presence state because channel is in invalid state", channel.name);
            }
            Log.v(TAG, errorMessage);
            throw AblyException.fromErrorInfo(new ErrorInfo(errorMessage, errorCode));
        }

        synchronized Collection<PresenceMessage> get(Param[] params) throws AblyException, InterruptedException {
            boolean waitForSync = true;
            String clientId = null;
            String connectionId = null;

            for (Param param: params) {
                switch (param.key) {
                    case GET_WAITFORSYNC:
                        waitForSync = Boolean.parseBoolean(param.value);
                        break;
                    case GET_CLIENTID:
                        clientId = param.value;
                        break;
                    case GET_CONNECTIONID:
                        connectionId = param.value;
                        break;
                }
            }

            HashSet<PresenceMessage> result = new HashSet<>();
            if (waitForSync)
                waitForSync();

            for (PresenceMessage member: members.values()) {
                if ((clientId == null || member.clientId.equals(clientId)) &&
                        (connectionId == null || member.connectionId.equals(connectionId)))
                    result.add(member);
            }

            return result;
        }

        /**
         * Add or update the presence state for a member
         * @param item
         * @return true if the given message represents a change;
         * false if the message is already superseded
         */
        synchronized boolean put(PresenceMessage item) {
            String key = memberKey(item);
            /* we've seen this member, so do not remove it at the end of sync */
            if(residualMembers != null)
                residualMembers.remove(key);

            /* check if there is a newer existing member (or absent witness) */
            if (hasNewerItem(key, item))
                return false;

            members.put(key, item);
            return true;
        }

        /**
         * Determine if there is a newer item already in the map
         * @param key key used to search the item in the map
         * @param item new presence message to be added
         * @return true if there is a newer item
         */
        synchronized boolean hasNewerItem(String key, PresenceMessage item) {
            PresenceMessage existingItem = members.get(key);
            if(existingItem == null)
                return false;

            /*
             * (RTP2b1) If either presence message has a connectionId which is not an initial substring
             * of its id, compare them by timestamp numerically. (This will be the case when one of them
             * is a 'synthesized leave' event sent by realtime to indicate a connection disconnected
             * unexpectedly 15s ago. Such messages will have an id that does not correspond to its
             * connectionId, as it wasn't actually published by that connection
             */
            if(item.connectionId != null && existingItem.connectionId != null &&
                    (!item.id.startsWith(item.connectionId) || !existingItem.id.startsWith(existingItem.connectionId)))
                return existingItem.timestamp >= item.timestamp;

            /*
             * (RTP2b2) Else split the id of both presence messages (which will be of the form
             * connid:msgSerial:index, e.g. aaaaaa:0:0) on the separator :, and parse the latter two as
             * integers. Compare them first by msgSerial numerically, then (if @msgSerial@s are equal) by
             * index numerically, larger being newer in both cases
             */
            String[] itemComponents = item.id.split(":", 3);
            String[] existingItemComponents = existingItem.id.split(":", 3);

            if(itemComponents.length < 3 || existingItemComponents.length < 3)
                return false;

            try {
                long messageSerial = Long.parseLong(itemComponents[1]);
                long messageIndex = Long.parseLong(itemComponents[2]);
                long existingMessageSerial = Long.parseLong(existingItemComponents[1]);
                long existingMessageIndex = Long.parseLong(existingItemComponents[2]);

                return existingMessageSerial > messageSerial ||
                        (existingMessageSerial == messageSerial && existingMessageIndex >= messageIndex);
            }
            catch(NumberFormatException e) {
                return false;
            }
        }

        /**
         * Remove a member.
         * @param item
         * @return
         */
        synchronized boolean remove(PresenceMessage item) {
            String key = memberKey(item);
            if (hasNewerItem(key, item))
                return false;
            PresenceMessage existingItem = members.remove(key);
            if(existingItem != null && existingItem.action == PresenceMessage.Action.absent)
                return false;
            return true;
        }

        /**
         * Start a sync sequence.
         * Note that this is called each time a sync message is received that is not
         * the last.
         */
        synchronized void startSync() {
            Log.v(TAG, "startSync(); channel = " + channel.name + "; syncInProgress = " + syncInProgress);
            /* we might be called multiple times while a sync is in progress */
            if(!syncInProgress) {
                residualMembers = new HashSet<String>(members.keySet());
                syncInProgress = true;
            }
        }

        /**
         * Finish a sync sequence. Returns "residual" items that were removed as a part of a sync
         */
        synchronized List<PresenceMessage> endSync() {
            Log.v(TAG, "endSync(); channel = " + channel.name + "; syncInProgress = " + syncInProgress);
            ArrayList<PresenceMessage> removedEntries = new ArrayList<>();
            if(syncInProgress) {
                /* we can now strip out the absent members, as we have
                 * received all of the out-of-order sync messages */
                for(Iterator<Map.Entry<String, PresenceMessage>> it = members.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<String, PresenceMessage> entry = it.next();
                    if(entry.getValue().action == PresenceMessage.Action.absent) {
                        it.remove();
                    }
                }
                /* any members that were present at the start of the sync,
                 * and have not been seen in sync, can be removed */
                for(String itemKey: residualMembers) {
                    PresenceMessage removedMember = members.remove(itemKey);
                    /* This null check is added as a potential fix for an issue that
                     * could not be reproduced, reported here https://github.com/ably/ably-java/issues/853 */
                    if(removedMember != null) {
                        /* clone presence message as it still can be in the internal presence map */
                        removedEntries.add((PresenceMessage) removedMember.clone());
                    }
                }
                residualMembers = null;

                /* finish, notifying any waiters */
                syncInProgress = false;
            }
            syncComplete = true;
            notifyAll();
            return removedEntries;
        }

        /**
         * Clear all entries
         */
        synchronized void clear() {
            members.clear();
            if(residualMembers != null)
                residualMembers.clear();
        }

        /**
         * Combines clientId and connectionId to ensure that multiple connected clients with an identical clientId are uniquely identifiable.
         * A string function that returns the combined clientId and connectionId.
         * <p>
         * Spec: TP3h
         * @return A combination of clientId and connectionId.
         */
        public String memberKey(PresenceMessage item) {
            return item.memberKey();
        }

        private boolean syncInProgress;
        private Collection<String> residualMembers;
        private final HashMap<String, PresenceMessage> members = new HashMap<String, PresenceMessage>();
    }

    private class InternalPresenceMap extends PresenceMap {
        /**
         * Get the member key for the internal PresenceMessage.
         * Spec: RTP17h
         * @return key of the presence message
         */
        @Override
        public String memberKey(PresenceMessage item) {
            return item.clientId;
        }
    }

    private final PresenceMap presence = new PresenceMap();
    private final PresenceMap internalPresence = new InternalPresenceMap(); // RTP17

    /************************************
     * general
     ************************************/

    Presence(Channel channel) {
        this.channel = channel;
    }

    private static final String TAG = Channel.class.getName();

    private final Channel channel;

    /* channel serial if sync is in progress */
    private String currentSyncChannelSerial;
    /**
     * Indicates whether the presence set synchronization between Ably and the clients on the channel has been completed.
     * Set to true when the sync is complete.
     * <p>
     * Spec: RTP13
     */
    public boolean syncComplete;
}
