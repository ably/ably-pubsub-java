package io.ably.pubsub.realtime;

import io.ably.pubsub.liveobjects.LiveObjectsPlugin;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ChannelOptions;
import org.jetbrains.annotations.Nullable;

public class Channel extends ChannelBase {
    Channel(RealtimeClient ably, String name, ChannelOptions options, @Nullable LiveObjectsPlugin liveObjectsPlugin) throws AblyException {
        super(ably, name, options, liveObjectsPlugin);
    }

    public interface MessageListener extends ChannelBase.MessageListener {}
}
