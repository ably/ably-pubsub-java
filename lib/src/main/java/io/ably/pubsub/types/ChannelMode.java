package io.ably.pubsub.types;

import java.util.HashSet;
import java.util.Set;

import io.ably.pubsub.types.ProtocolMessage.Flag;

/**
 * Describes the possible flags used to configure client capabilities, using {@link ChannelOptions}.
 */
public enum ChannelMode {
    /**
     * The client can enter the presence set.
     */
    presence(Flag.presence),
    /**
     * The client can publish messages.
     */
    publish(Flag.publish),
    /**
     * The client can subscribe to messages.
     */
    subscribe(Flag.subscribe),
    /**
     * The client can receive presence messages.
     */
    presence_subscribe(Flag.presence_subscribe),

    /**
     * The client can publish object messages.
     */
    object_publish(Flag.object_publish),

    /**
     * The client can subscribe to object messages.
     */
    object_subscribe(Flag.object_subscribe),

    /**
     * The client can publish annotation messages.
     */
    annotation_publish(Flag.annotation_publish),

    /**
     * The client can subscribe to annotation messages.
     */
    annotation_subscribe(Flag.annotation_subscribe);

    private final int mask;

    ChannelMode(final Flag flag) {
        mask = flag.getMask();
    }

    public int getMask() {
        return mask;
    }

    public static Set<ChannelMode> toSet(final int flags) {
        final Set<ChannelMode> set = new HashSet<>();
        for (final ChannelMode mode : ChannelMode.values()) {
            final int mask = mode.getMask();
            if ((flags & mask) == mask) {
                set.add(mode);
            }
        }
        return set;
    }
}
