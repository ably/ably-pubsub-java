package io.ably.lib.realtime;

import io.ably.lib.liveobjects.LiveObjectsPlugin;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import org.jetbrains.annotations.Nullable;

public class Channel extends ChannelBase {
    Channel(AblyRealtime ably, String name, ChannelOptions options, @Nullable LiveObjectsPlugin liveObjectsPlugin) throws AblyException {
        super(ably, name, options, liveObjectsPlugin);
    }

    public interface MessageListener extends ChannelBase.MessageListener {}
}
