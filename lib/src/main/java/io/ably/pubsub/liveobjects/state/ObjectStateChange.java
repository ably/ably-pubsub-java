package io.ably.pubsub.liveobjects.state;

import io.ably.pubsub.liveobjects.Subscription;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;

public interface ObjectStateChange {
    /**
     * Subscribes to a specific Objects synchronization state event.
     *
     * <p>This method registers the provided listener to be notified when the specified
     * synchronization state event occurs. The returned subscription can be used to
     * unsubscribe later when the notifications are no longer needed.
     *
     * @param event the synchronization state event to subscribe to (SYNCING or SYNCED)
     * @param listener the listener that will be called when the event occurs
     * @return a subscription object that can be used to unsubscribe from the event
     */
    @NonBlocking
    Subscription on(@NotNull ObjectStateEvent event, @NotNull ObjectStateChange.Listener listener);

    /**
     * Unsubscribes the specified listener from all synchronization state events.
     *
     * <p>After calling this method, the provided listener will no longer receive
     * any synchronization state event notifications.
     *
     * @param listener the listener to unregister from all events
     */
    @NonBlocking
    void off(@NotNull ObjectStateChange.Listener listener);

    /**
     * Unsubscribes all listeners from all synchronization state events.
     *
     * <p>After calling this method, no listeners will receive any synchronization
     * state event notifications until new listeners are registered.
     */
    @NonBlocking
    void offAll();

    /**
     * Interface for receiving notifications about Objects synchronization state changes.
     * <p>
     * Implement this interface and register it with an {@code ObjectStateEmitter} to be notified
     * when synchronization state transitions occur.
     */
    interface Listener {
        /**
         * Called when the synchronization state changes.
         *
         * @param objectStateEvent The new state event (SYNCING or SYNCED)
         */
        void onStateChanged(ObjectStateEvent objectStateEvent);
    }
}
