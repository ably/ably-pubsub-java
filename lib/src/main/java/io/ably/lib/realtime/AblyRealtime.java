package io.ably.lib.realtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.ably.lib.liveobjects.LiveObjectsPlugin;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.types.ReadOnlyMap;
import io.ably.lib.types.RecoveryKeyContext;
import io.ably.lib.util.InternalMap;
import io.ably.lib.util.Log;
import io.ably.lib.util.StringUtils;
import org.jetbrains.annotations.Nullable;

/**
 * A client that extends the functionality of the {@link AblyRest} and provides additional realtime-specific features.
 *
 * This class implements {@link AutoCloseable} so you can use it in
 * try-with-resources constructs and have the JDK close it for you.
 */
public class AblyRealtime extends AblyRest {
    /**
     * The {@link Connection} object for this instance.
     * <p>
     * Spec: RTC2
     */
    public final Connection connection;

    /**
     * A {@link Channels} object.
     * <p>
     * Spec: RTC3, RTS1
     */
    public final Channels channels;

    /**
     * A nullable reference to the LiveObjects plugin.
     * <p>
     * This field is initialized only if the LiveObjects plugin is present in the classpath.
     */
    @Nullable
    private final LiveObjectsPlugin liveObjectsPlugin;

    /**
     * Constructs a Realtime client object using an Ably API key or token string.
     * <p>
     * Spec: RSC1
     * @param key The Ably API key or token string used to validate the client.
     * @throws AblyException
     * @deprecated use {@code io.ably.pubsub.device.PubSubDevice#clientBuilder()} from the
     *             {@code io.ably.pubsub:device} artifact if this code runs on an end-user device, or
     *             {@code io.ably.pubsub.server.PubSubServer#realtimeClientBuilder()} from the
     *             {@code io.ably.pubsub:server} artifact if it runs on infrastructure you control.
     */
    @Deprecated
    public AblyRealtime(String key) throws AblyException {
        this(new ClientOptions(key));
    }

    /**
     * Constructs a RealtimeClient object using an Ably {@link ClientOptions} object.
     * <p>
     * Spec: RSC1
     * @param options A {@link ClientOptions} object.
     * @throws AblyException
     * @deprecated use {@code io.ably.pubsub.device.PubSubDevice#clientBuilder()} from the
     *             {@code io.ably.pubsub:device} artifact if this code runs on an end-user device, or
     *             {@code io.ably.pubsub.server.PubSubServer#realtimeClientBuilder()} from the
     *             {@code io.ably.pubsub:server} artifact if it runs on infrastructure you control.
     */
    @Deprecated
    public AblyRealtime(ClientOptions options) throws AblyException {
        super(options);
        final InternalChannels channels = new InternalChannels();
        this.channels = channels;

        liveObjectsPlugin = LiveObjectsPlugin.tryInitialize(this);

        connection = new Connection(this, channels, platformAgentProvider, liveObjectsPlugin);

        if (!StringUtils.isNullOrEmpty(options.recover)) {
            RecoveryKeyContext recoveryKeyContext = RecoveryKeyContext.decode(options.recover);
            if (recoveryKeyContext != null) {
                setChannelSerialsFromRecoverOption(recoveryKeyContext.getChannelSerials()); // RTN16j
                connection.connectionManager.msgSerial = recoveryKeyContext.getMsgSerial(); //RTN16f
            }
        }

        if(options.autoConnect) connection.connect();
    }

    /**
     * Calls {@link Connection#connect} and causes the connection to open,
     * entering the connecting state. Explicitly calling connect() is unnecessary
     * unless the {@link ClientOptions#autoConnect} property is disabled.
     * <p>
     * Spec: RTN11
     */
    @Override
    public void connect() {
        super.connect(); // resets thread pool for async requests
        connection.connect();
    }

    /**
     * Calls {@link Connection#close} and causes the connection to close, entering the closing state.
     * Once closed, the library will not attempt to re-establish the connection
     * without an explicit call to {@link Connection#connect}.
     * <p>
     * Spec: RTN12
     */
    @Override
    public void close() {
        try {
            super.close(); // throws checked exception
        } catch (final Exception exception) {
            // Soften to Log, rather than throw.
            // This is because our close() method has never declared that it throws a checked exception.
            // Which is confusing, given AutoCloseable declares that it does.
            // TODO captured in https://github.com/ably/ably-java/issues/806
            // It's also because this particular piece of resource cleanup, focussed on thread pool resources used by
            // our REST code in the base class, is being introduced in an SDK patch release for version 1.2.
            Log.e(TAG, "There was an exception releasing client instance base resources.", exception);
        }

        connection.close();
        if (liveObjectsPlugin != null) {
            liveObjectsPlugin.dispose();
        }
    }

    /**
     * Authentication token has changed.
     */
    @Override
    protected void onAuthUpdated(String token, boolean waitForResponse) throws AblyException {
        connection.connectionManager.onAuthUpdated(token, waitForResponse);
    }

    /**
     * Authentication token has changed. Async version
     */
    @Override
    protected void onAuthUpdatedAsync(String token, Auth.AuthUpdateResult authUpdateResult)  {
        connection.connectionManager.onAuthUpdatedAsync(token,authUpdateResult);
    }

    /**
     * Authentication error occurred
     */
    protected void onAuthError(ErrorInfo errorInfo) {
        connection.connectionManager.onAuthError(errorInfo);
    }

    /**
     * A collection of Channels associated with this Ably Realtime instance.
     */
    public interface Channels extends ReadOnlyMap<String, Channel> {
        /**
         * Creates a new {@link Channel} object, or returns the existing channel object.
         * <p>
         * Spec: RSN3a, RTS3a
         * @param channelName The channel name.
         * @return A {@link Channel} object.
         */
        Channel get(String channelName);

        /**
         * Creates a new {@link Channel} object, with the specified {@link ChannelOptions}, or returns the existing channel object.
         * <p>
         * Spec: RSN3c, RTS3c
         * @param channelName The channel name.
         * @param channelOptions A {@link ChannelOptions} object.
         * @return A {@link Channel} object.
         * @throws AblyException
         */
        Channel get(String channelName, ChannelOptions channelOptions) throws AblyException;

        /**
         * Releases a {@link Channel} object, deleting it, and enabling it to be garbage collected.
         * It also removes any listeners associated with the channel.
         * To release a channel, the {@link ChannelState} must be INITIALIZED, DETACHED, or FAILED.
         * <p>
         * Spec: RSN4, RTS4
         * @param channelName The channel name.
         */
        void release(String channelName);
    }

    private class InternalChannels extends InternalMap<String, Channel> implements Channels, ConnectionManager.Channels {
        /**
         * Get the named channel; if it does not already exist,
         * create it with default options.
         * @param channelName the name of the channel
         * @return the channel
         */
        @Override
        public Channel get(String channelName) {
            try {
                return get(channelName, null);
            } catch (AblyException e) { return null; }
        }

        @Override
        public Channel get(final String channelName, final ChannelOptions channelOptions) throws AblyException {
            // We're not using computeIfAbsent because that requires Java 1.8.
            // Hence there's the slight inefficiency of creating newChannel when it may not be
            // needed because there is an existingChannel.
            final Channel newChannel = new Channel(AblyRealtime.this, channelName, channelOptions, liveObjectsPlugin);
            final Channel existingChannel = map.putIfAbsent(channelName, newChannel);

            if (existingChannel != null) {
                if (channelOptions != null) {
                    if (existingChannel.shouldReattachToSetOptions(channelOptions)) {
                        throw AblyException.fromErrorInfo(new ErrorInfo("Channels.get() cannot be used to set channel options that would cause the channel to reattach. Please, use Channel.setOptions() instead.", 40000, 400));
                    }
                    existingChannel.setOptions(channelOptions);
                }
                return existingChannel;
            }

            return newChannel;
        }

        @Override
        public void release(String channelName) {
            Channel channel = map.remove(channelName);
            if(channel != null) {
                channel.markAsReleased();
                try {
                    channel.detach();
                } catch (AblyException e) {
                    Log.e(TAG, "Unexpected exception detaching channel; channelName = " + channelName, e);
                }
            }
            if (liveObjectsPlugin != null) {
                liveObjectsPlugin.dispose(channelName);
            }
        }

        @Override
        public void onMessage(ProtocolMessage msg) {
            String channelName = msg.channel;
            Channel channel = null;
            synchronized(this) {
                if (channels.containsKey(channelName)) {
                    channel = channels.get(channelName);
                }
            }
            if(channel == null) {
                Log.e(TAG, "Received channel message for non-existent channel");
                return;
            }
            channel.onChannelMessage(msg);
        }

        @Override
        public void suspendAll(ErrorInfo error, boolean notifyStateChange) {
            for (Channel channel : map.values()) {
                channel.setSuspended(error, notifyStateChange);
            }
        }

        /**
         * By spec RTN15c3
         * Move queued messages from connection manager to their respective channel for them to be sent after reattach
         * @param queuedMessages Queued messages transferred from ConnectionManager
         */
        @Override
        public void transferToChannelQueue(List<ConnectionManager.QueuedMessage> queuedMessages) {
            final Map<String, List<ConnectionManager.QueuedMessage>> channelQueueMap  = new HashMap<>();
            for (ConnectionManager.QueuedMessage queuedMessage : queuedMessages) {
                final String channelName = queuedMessage.msg.channel;
                if (!channelQueueMap.containsKey(channelName)){
                    channelQueueMap.put(channelName, new ArrayList<>());
                }
                channelQueueMap.get(channelName).add(queuedMessage);
            }

            for (Channel channel : map.values()) {
                if (channel.state.isReattachable()) {
                    Log.d(TAG, "reAttach(); channel = " + channel.name);
                    channel.transferQueuedPresenceMessages(channelQueueMap.get(channel.name));
                }
            }
        }

        private void clear() {
            map.clear();
        }
    }

    protected void setChannelSerialsFromRecoverOption(Map<String, String> serials) {
        for (Map.Entry<String, String> entry : serials.entrySet()) {
            String channelName = entry.getKey();
            String channelSerial = entry.getValue();
            Channel channel = this.channels.get(channelName);
            if (channel != null) {
                channel.properties.channelSerial = channelSerial;
            }
        }
    }

    protected Map<String, String> getChannelSerials() {
        Map<String, String> channelSerials = new HashMap<>();
        for (Channel channel : this.channels.values()) {
            if (channel.state == ChannelState.attached) {
                channelSerials.put(channel.name, channel.properties.channelSerial);
            }
        }
        return channelSerials;
    }

    /********************
     * internal
     ********************/

    private static final String TAG = AblyRealtime.class.getName();
}
