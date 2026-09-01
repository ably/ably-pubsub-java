package io.ably.lib.rest;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;

public class Channel extends ChannelBase {
    Channel(AblyBase ably, String name, ChannelOptions options) throws AblyException {
        super(ably, name, options);
    }
}
