package io.ably.pubsub.realtime;

/**
 * Describes the events emitted by a {@link Channel} object.
 * An event is either an UPDATE or a {@link ChannelState}.
 */
public enum ChannelEvent {
    initialized,
    attaching,
    attached,
    detaching,
    detached,
    failed,
    suspended,
    /**
     * An event for changes to channel conditions that do not result in a change in {@link ChannelState}.
     * <p>
     * Spec: RTL2g
     */
    update
}
