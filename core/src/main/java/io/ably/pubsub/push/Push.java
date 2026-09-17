package io.ably.pubsub.push;

import io.ably.pubsub.http.PubSubBase;

/**
 * Enables a device to be registered and deregistered from receiving push notifications.
 */
public class Push extends PushBase {
    public Push(PubSubBase rest) {
        super(rest);
    }
}
