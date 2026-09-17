package io.ably.pubsub.util;

@FunctionalInterface
public interface TimerInstance {
    void cancel();
}
