package io.ably.pubsub.realtime;

import io.ably.pubsub.types.ErrorInfo;
import org.jetbrains.annotations.NotNull;

/**
 * An interface whereby a client may be notified of state changes for a channel.
 */
public interface ChannelStateListener {

    /**
     * Called when channel state changes.
     * @param stateChange information about the new state. Check {@link ChannelState ChannelState} - for all states available.
     */
    void onChannelStateChanged(ChannelStateChange stateChange);

    /**
     * Contains state change information emitted by {@link Channel} objects.
     */
    class ChannelStateChange {
        /**
         * The event that triggered this{@link ChannelState} change.
         * <p>
         * Spec: TH5
         */
        final public ChannelEvent event;
        /**
         * The new current {@link ChannelState}.
         * <p>
         * Spec: RTL2a, RTL2b
         */
        final public ChannelState current;
        /**
         * The previous state.
         * For the {@link ChannelEvent#update} event, this is equal to the current {@link ChannelState}.
         * <p>
         * Spec: RTL2a, RTL2b
         */
        final public ChannelState previous;
        /**
         * An {@link ErrorInfo} object containing any information relating to the transition.
         * <p>
         * Spec: RTL2e, TH3
         */
        final public ErrorInfo reason;
        /**
         * Indicates whether message continuity on this channel is preserved,
         * see <a href="https://ably.com/docs/realtime/channels#nonfatal-errors">Nonfatal channel errors</a> for more info.
         * <p>
         * Spec: RTL2f, TH4
         */
        final public boolean resumed;

        ChannelStateChange(ChannelState current, ChannelState previous, ErrorInfo reason, boolean resumed) {
            this.event = current.getChannelEvent();
            this.current = current;
            this.previous = previous;
            this.reason = reason;
            this.resumed = resumed;
        }

        private ChannelStateChange(ErrorInfo reason, boolean resumed) {
            this.event = ChannelEvent.update;
            this.current = this.previous = ChannelState.attached;
            this.reason = reason;
            this.resumed = resumed;
        }

        /* construct UPDATE event */
        static ChannelStateChange createUpdateEvent(ErrorInfo reason, boolean resumed) {
            return new ChannelStateChange(reason, resumed);
        }

        @Override
        public @NotNull String toString() {
            return '{' +
                "event=" + event +
                ", current=" + current +
                ", previous=" + previous +
                ", reason=" + reason +
                ", resumed=" + resumed +
                '}';
        }
    }

    class Multicaster extends io.ably.pubsub.util.Multicaster<ChannelStateListener> implements ChannelStateListener {
        @Override
        public void onChannelStateChanged(ChannelStateChange stateChange) {
            for (final ChannelStateListener member : getMembers())
                try {
                    member.onChannelStateChanged(stateChange);
                } catch(Throwable ignored) {}
        }
    }

    class Filter implements ChannelStateListener {
        @Override
        public void onChannelStateChanged(ChannelStateChange stateChange) {
            if(stateChange.current == this.state)
                listener.onChannelStateChanged(stateChange);
        }
        Filter(ChannelState state, ChannelStateListener listener) { this.state = state; this.listener = listener; }
        ChannelState state;
        ChannelStateListener listener;
    }
}
