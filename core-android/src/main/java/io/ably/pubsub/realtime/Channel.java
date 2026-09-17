package io.ably.pubsub.realtime;

import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ChannelOptions;
import io.ably.pubsub.push.PushChannel;
import io.ably.pubsub.liveobjects.LiveObjectsPlugin;


public class Channel extends ChannelBase {
    /**
     * A {@link PushChannel} object.
     * <p>
     * Spec: RSH4
     */
    public final PushChannel push;

    Channel(RealtimeClient ably, String name, ChannelOptions options, LiveObjectsPlugin liveObjectsPlugin) throws AblyException {
        super(ably, name, options, liveObjectsPlugin);
        this.push = ((io.ably.pubsub.http.PubSubHttpClient) ably).channels.get(name, options).push;
    }

    /**
     * An interface whereby a client maybe notified of messages changes on a channel.
     */
    public interface MessageListener extends ChannelBase.MessageListener {}
}
