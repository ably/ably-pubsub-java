package io.ably.pubsub.liveobjects.instance.types;

import com.google.gson.JsonPrimitive;
import io.ably.pubsub.liveobjects.instance.Instance;
import io.ably.pubsub.liveobjects.instance.InstanceListener;
import io.ably.pubsub.liveobjects.Subscription;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * A {@link Instance} bound to a {@code LiveCounter}. Provides type-safe
 * access to counter operations such as {@link #value()}, {@link #increment(Number)}
 * and {@link #decrement(Number)}.
 *
 * <p>Spec: RTTS10b
 */
public interface LiveCounterInstance extends Instance {

    /**
     * Returns the object id of the wrapped {@code LiveCounter}.
     *
     * <p>Spec: RTINS3a
     *
     * @return the wrapped {@code LiveCounter}'s object id
     */
    @NotNull
    String getId();

    /**
     * Returns the current value of the wrapped {@code LiveCounter}.
     *
     * <p>Spec: RTINS4 / RTLC5
     *
     * @return the counter value
     */
    @NotNull
    Double value();

    /**
     * Returns the compacted JSON snapshot of the wrapped value, narrowed to a
     * {@link JsonPrimitive}: a {@code LiveCounterInstance} always compacts to a numeric
     * JSON primitive.
     *
     * <p>Spec: RTTS7a
     *
     * @return the compacted JSON primitive
     */
    @Override
    @NotNull JsonPrimitive compactJson();

    /**
     * Increments the wrapped {@code LiveCounter} by {@code 1}. Equivalent to
     * calling {@link #increment(Number)} with {@code 1}.
     *
     * <p>Spec: RTINS14a1 (default {@code amount} of {@code 1})
     *
     * @return a future that completes when the operation has been acknowledged
     */
    @NotNull
    CompletableFuture<Void> increment();

    /**
     * Increments the wrapped {@code LiveCounter} by {@code amount}.
     *
     * <p>Sends a {@code COUNTER_INC} operation to the realtime system; the local state
     * is updated when the operation is echoed back.
     *
     * <p>Spec: RTINS14
     *
     * @param amount the amount to add (may be negative)
     * @return a future that completes when the operation has been acknowledged
     */
    @NotNull
    CompletableFuture<Void> increment(@NotNull Number amount);

    /**
     * Decrements the wrapped {@code LiveCounter} by {@code 1}. Equivalent to
     * calling {@link #decrement(Number)} with {@code 1}.
     *
     * <p>Spec: RTINS15a1 (default {@code amount} of {@code 1})
     *
     * @return a future that completes when the operation has been acknowledged
     */
    @NotNull
    CompletableFuture<Void> decrement();

    /**
     * Decrements the wrapped {@code LiveCounter} by {@code amount}. Equivalent to
     * calling {@link #increment(Number)} with a negated value.
     *
     * <p>Spec: RTINS15
     *
     * @param amount the amount to subtract (may be negative)
     * @return a future that completes when the operation has been acknowledged
     */
    @NotNull
    CompletableFuture<Void> decrement(@NotNull Number amount);

    /**
     * Subscribes a listener for updates on the wrapped {@code LiveCounter}. The
     * listener is invoked whenever the wrapped counter is changed by a local or remote
     * operation. Call {@link Subscription#unsubscribe()} on the returned handle
     * to stop receiving events for this listener.
     *
     * <p>The subscription is identity-based: it follows the specific underlying
     * {@code LiveCounter}, regardless of where it sits in the LiveObjects graph.
     *
     * <p>Spec: RTTS10b / RTINS16
     *
     * @param listener the listener to invoke on updates
     * @return a subscription handle that can be used to unsubscribe this listener
     */
    @NonBlocking
    @NotNull Subscription subscribe(@NotNull InstanceListener listener);
}
