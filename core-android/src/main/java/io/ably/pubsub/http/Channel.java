package io.ably.pubsub.http;

import io.ably.pubsub.push.PushChannel;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ChannelOptions;

public class Channel extends ChannelBase {
    /**
     * A {@link PushChannel} object.
     * <p>
     * Spec: RSH4
     */
    public final PushChannel push;

    Channel(PubSubBase ably, String name, ChannelOptions options) throws AblyException {
        super(ably, name, options);
        this.push = new PushChannel(this, (PubSubHttpClient)ably);
    }
}
