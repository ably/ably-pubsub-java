package io.ably.lib.liveobjects.message;

import org.jetbrains.annotations.Nullable;

/**
 * Payload of a {@link ObjectOperationAction#COUNTER_CREATE} operation, describing the
 * initial state of the created {@code LiveCounter} object.
 *
 * <p>Spec: CCR*
 */
public interface CounterCreate {

    /**
     * Returns the initial value of the created counter object.
     *
     * <p>Spec: CCR2a
     *
     * @return the initial counter value, or {@code null} if absent from the operation
     *         (such an operation marks the create as merged without changing the value, per RTLC16d)
     */
    @Nullable Double getCount();
}
