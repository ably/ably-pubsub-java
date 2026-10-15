package io.ably.pubsub.http;

import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ChannelOptions;

public class Channel extends ChannelBase {
    Channel(PubSubBase ably, String name, ChannelOptions options) throws AblyException {
        super(ably, name, options);
    }
}
