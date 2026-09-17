package io.ably.lib.realtime;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.push.PushChannel;
import io.ably.lib.liveobjects.LiveObjectsPlugin;


public class Channel extends ChannelBase {
    /**
     * A {@link PushChannel} object.
     * <p>
     * Spec: RSH4
     */
    public final PushChannel push;

    Channel(AblyRealtime ably, String name, ChannelOptions options, LiveObjectsPlugin liveObjectsPlugin) throws AblyException {
        super(ably, name, options, liveObjectsPlugin);
        this.push = ((io.ably.lib.rest.AblyRest) ably).channels.get(name, options).push;
    }

    /**
     * An interface whereby a client maybe notified of messages changes on a channel.
     */
    public interface MessageListener extends ChannelBase.MessageListener {}
}
