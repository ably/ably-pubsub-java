package io.ably.pubsub.liveobjects.adapter;

import io.ably.pubsub.realtime.ChannelBase;
import io.ably.pubsub.realtime.Connection;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NotNull;

/**
 * Bridges the path-based LiveObjects implementation to the core Ably client, exposing the
 * client configuration, connection and channel state it needs without coupling it to the
 * concrete {@link io.ably.pubsub.realtime.RealtimeClient} type.
 *
 * <p>This is the adapter for the path-based {@code io.ably.pubsub.object} API and is intentionally
 * kept independent of the legacy {@code io.ably.pubsub.objects} package.
 */
public interface AblyClientAdapter {
    /**
     * Retrieves the client options configured for the Ably client.
     * Used to access client configuration parameters such as echoMessages setting
     * that affect the behavior of Objects operations.
     *
     * @return the client options containing configuration parameters
     */
    @NotNull ClientOptions getClientOptions();

    /**
     * Retrieves the connection instance for handling connection state and operations.
     * Used to check connection status, obtain error information, and manage
     * message transmission across the Ably connection.
     *
     * @return the connection instance
     */
    @NotNull Connection getConnection();

    /**
     * Retrieves the current time in milliseconds from the Ably server.
     * Spec: RTO16
     */
    @Blocking
    long getTime() throws AblyException;

    /**
     * Retrieves the channel instance for the specified channel name.
     * If the channel does not exist, an AblyException is thrown.
     *
     * @param channelName the name of the channel to retrieve
     * @return the ChannelBase instance for the specified channel
     * @throws AblyException if the channel is not found or cannot be retrieved
     */
    @NotNull ChannelBase getChannel(@NotNull String channelName) throws AblyException;
}
