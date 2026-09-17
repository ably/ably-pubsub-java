package io.ably.pubsub.realtime;

/**
 * Describes the possible states of a {@link Channel} object.
 */
public enum ChannelState {
    /**
     * The channel has been initialized but no attach has yet been attempted.
     */
    initialized(ChannelEvent.initialized),
    /**
     * An attach has been initiated by sending a request to Ably.
     * This is a transient state, followed either by a transition to ATTACHED, SUSPENDED, or FAILED.
     */
    attaching(ChannelEvent.attaching),
    /**
     * The attach has succeeded.
     * In the ATTACHED state a client may publish and subscribe to messages, or be present on the channel.
     */
    attached(ChannelEvent.attached),
    /**
     * A detach has been initiated on an ATTACHED channel by sending a request to Ably.
     * This is a transient state, followed either by a transition to DETACHED or FAILED.
     */
    detaching(ChannelEvent.detaching),
    /**
     * The channel, having previously been ATTACHED, has been detached by the user.
     */
    detached(ChannelEvent.detached),
    /**
     * An indefinite failure condition.
     * This state is entered if a channel error has been received from the Ably service,
     * such as an attempt to attach without the necessary access rights.
     */
    failed(ChannelEvent.failed),
    /**
     * The channel, having previously been ATTACHED, has lost continuity,
     * usually due to the client being disconnected from Ably for longer than two minutes.
     * It will automatically attempt to reattach as soon as connectivity is restored.
     */
    suspended(ChannelEvent.suspended);

    final private ChannelEvent event;
    ChannelState(ChannelEvent event) {
        this.event = event;
    }
    public ChannelEvent getChannelEvent() {
        return event;
    }

    // RTN15c6, RTN15c7, RTL3d, RTN15g3
    public boolean isReattachable() {
        return this == ChannelState.attaching || this == ChannelState.attached || this == ChannelState.suspended;
    }
}
