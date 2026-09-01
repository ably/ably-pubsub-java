package io.ably.lib.push;

import io.ably.lib.rest.AblyBase;

/**
 * Enables a device to be registered and deregistered from receiving push notifications.
 */
public class Push extends PushBase {
    public Push(AblyBase rest) {
        super(rest);
    }
}
