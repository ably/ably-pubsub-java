package io.ably.pubsub.liveobjects.adapter;

import io.ably.pubsub.realtime.RealtimeClient;
import io.ably.pubsub.realtime.Channel;
import io.ably.pubsub.realtime.ChannelBase;
import io.ably.pubsub.realtime.Connection;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.ReadOnlyMap;
import io.ably.pubsub.util.Log;
import org.jetbrains.annotations.NotNull;

/**
 * Default {@link AblyClientAdapter} implementation backed by an {@link RealtimeClient} client.
 * Holding the {@code RealtimeClient} reference gives the path-based LiveObjects implementation
 * access to the full client configuration and runtime state it may need.
 */
public class Adapter implements AblyClientAdapter {
    private final RealtimeClient ably;
    private static final String TAG = AblyClientAdapter.class.getName();

    public Adapter(@NotNull RealtimeClient ably) {
        this.ably = ably;
    }

    @Override
    public @NotNull ClientOptions getClientOptions() {
        return ably.options;
    }

    @Override
    public @NotNull Connection getConnection() {
        return ably.connection;
    }

    @Override
    public long getTime() throws AblyException {
        return ably.time();
    }

    @Override
    public @NotNull ChannelBase getChannel(@NotNull String channelName) throws AblyException {
        // Look up via the read-only map view. Channels#get(String) would create the channel if
        // absent; ReadOnlyMap only exposes get(Object), which returns null atomically for an
        // unknown channel instead of silently recreating it.
        final ReadOnlyMap<String, Channel> channels = ably.channels;
        final ChannelBase channel = channels.get(channelName);
        if (channel == null) {
            Log.e(TAG, "getChannel(): channel not found: " + channelName);
            ErrorInfo errorInfo = new ErrorInfo("Channel not found: " + channelName, 404);
            throw AblyException.fromErrorInfo(errorInfo);
        }
        return channel;
    }
}
