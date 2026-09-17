package io.ably.pubsub.types;

/**
 * Describes the properties of the channel state.
 * <p>
 * Spec: CP2
 * </p>
 */
public class ChannelProperties {
    /**
     * Starts unset when a channel is instantiated, then updated with the channelSerial
     * from each {@link io.ably.pubsub.realtime.ChannelState#attached} event that matches the channel.
     * Used as the value for {@link io.ably.pubsub.realtime.Channel#history}.
     * <p>
     * Spec: CP2a
     */
    public String attachSerial;

    /**
     * ChannelSerial contains the channelSerial from latest ProtocolMessage of action type
     * Message/PresenceMessage received on the channel.
     * <p>
     * Spec: CP2b, RTL15b
     */
    public String channelSerial;

    public ChannelProperties() {}
}
