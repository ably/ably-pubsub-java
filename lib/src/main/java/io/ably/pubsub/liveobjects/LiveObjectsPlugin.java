package io.ably.pubsub.liveobjects;

import io.ably.pubsub.liveobjects.adapter.AblyClientAdapter;
import io.ably.pubsub.liveobjects.adapter.Adapter;
import io.ably.pubsub.realtime.RealtimeClient;
import io.ably.pubsub.realtime.ChannelState;
import io.ably.pubsub.types.ProtocolMessage;
import io.ably.pubsub.util.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;

/**
 * The LiveObjectsPlugin interface provides a mechanism for managing and interacting with
 * live data objects in a real-time environment. It allows for the retrieval, disposal, and
 * management of Objects instances associated with specific channel names.
 */
public interface LiveObjectsPlugin {

    /**
     * Retrieves an instance of RealtimeObjects associated with the specified channel name.
     * This method ensures that a RealtimeObjects instance is available for the given channel,
     * creating one if it does not already exist.
     *
     * @param channelName the name of the channel for which the RealtimeObjects instance is to be retrieved.
     * @return the RealtimeObjects instance associated with the specified channel name.
     */
    @NotNull
    RealtimeObject getInstance(@NotNull String channelName);

    /**
     * Handles a protocol message.
     * This method is invoked whenever a protocol message is received, allowing the implementation
     * to process the message and take appropriate actions.
     *
     * @param message the protocol message to handle.
     */
    void handle(@NotNull ProtocolMessage message);

    /**
     * Handles state changes for a specific channel.
     * This method is invoked whenever a channel's state changes, allowing the implementation
     * to update the RealtimeObjects instances accordingly based on the new state and presence of objects.
     *
     * @param channelName the name of the channel whose state has changed.
     * @param state the new state of the channel.
     * @param hasObjects flag indicates whether the channel has any associated objects.
     */
    void handleStateChange(@NotNull String channelName, @NotNull ChannelState state, boolean hasObjects);

    /**
     * Disposes of the RealtimeObjects instance associated with the specified channel name.
     * This method removes the RealtimeObjects instance for the given channel, releasing any
     * resources associated with it.
     * This is invoked when ablyRealtimeClient.channels.release(channelName) is called
     *
     * @param channelName the name of the channel whose RealtimeObjects instance is to be removed.
     */
    void dispose(@NotNull String channelName);

    /**
     * Disposes of the plugin instance and all underlying resources.
     * This is invoked when ablyRealtimeClient.close() is called
     */
    void dispose();

    /**
     * Attempts to initialize the LiveObjects plugin by reflectively loading its implementation
     * from the classpath. Returns a new plugin instance on every successful invocation, or
     * {@code null} if the LiveObjects plugin is not present in the classpath.
     *
     * @param realtimeClient the RealtimeClient client used to build the adapter the plugin runs against.
     * @return a new {@link LiveObjectsPlugin} instance, or {@code null} if the plugin is unavailable.
     */
    @Nullable
    static LiveObjectsPlugin tryInitialize(@NotNull RealtimeClient realtimeClient) {
        return Factory.create(realtimeClient);
    }

    /**
     * Reflectively constructs the LiveObjects plugin implementation. Lives in a nested class so the
     * implementation-class name stays {@code private} (interface fields are forced {@code public}),
     * mirroring {@link io.ably.pubsub.liveobjects.serialization.ObjectSerializer.Holder}. Unlike {@code Holder}
     * this is stateless: {@link #create} returns a new instance on every call.
     */
    final class Factory {
        private static final String TAG = LiveObjectsPlugin.Factory.class.getName();
        private static final String IMPLEMENTATION_CLASS = "io.ably.pubsub.liveobjects.DefaultLiveObjectsPlugin";

        private Factory() {}

        @Nullable
        static LiveObjectsPlugin create(@NotNull RealtimeClient realtimeClient) {
            try {
                Class<?> objectsImplementation = Class.forName(IMPLEMENTATION_CLASS);
                AblyClientAdapter adapter = new Adapter(realtimeClient);
                return (LiveObjectsPlugin) objectsImplementation
                    .getDeclaredConstructor(AblyClientAdapter.class)
                    .newInstance(adapter);
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException |
                     InvocationTargetException e) {
                Log.i(TAG, "LiveObjects plugin not found in classpath. LiveObjects functionality will not be available.", e);
                return null;
            }
        }
    }
}
