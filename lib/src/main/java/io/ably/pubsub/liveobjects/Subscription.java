package io.ably.pubsub.liveobjects;

/**
 * Represents a registration for receiving events from a subscribe operation.
 * Provides a way to clean up and remove a subscription when it is no longer
 * needed.
 *
 * <p>Example usage:
 * <pre>
 * {@code
 * Subscription s = pathObject.subscribe(event -> { ... });
 * // Later, when done with the subscription
 * s.unsubscribe();
 * }
 * </pre>
 *
 * <p>Spec: SUB1
 */
public interface Subscription {

    /**
     * Deregisters the listener that was registered by the corresponding
     * {@code subscribe} call. Once called, the listener will not be invoked for
     * any subsequent events and references to it are cleaned up. Calling this
     * method more than once is a no-op.
     *
     * <p>Spec: SUB2a, SUB2b
     */
    void unsubscribe();
}
