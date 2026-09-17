package io.ably.pubsub.liveobjects.path.types;

import io.ably.pubsub.liveobjects.path.PathObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * A {@link PathObject} whose underlying value is expected to be a {@code LiveCounter}.
 * Provides type-safe access to counter operations such as {@link #value()},
 * {@link #increment(Number)} and {@link #decrement(Number)}.
 *
 * <p>Counters are terminal nodes - navigation via {@code at(...)} is not available
 * here because it is only defined on {@code LiveMapPathObject}.
 *
 * <p>Operations are best-effort and resolve the path at call time. Read operations
 * return {@code null} when the path does not resolve to a {@code LiveCounter}; write
 * operations complete the returned {@link CompletableFuture} exceptionally with an
 * {@code AblyException} (status 400, code 92007) in that case.
 *
 * <p>Spec: RTTS6b
 */
public interface LiveCounterPathObject extends PathObject {

    /**
     * Returns the current value of the {@code LiveCounter} at this path, or {@code null}
     * when the path does not resolve to a {@code LiveCounter}.
     *
     * <p>Spec: RTPO7 / RTLC5
     *
     * @return the counter value, or {@code null}
     */
    @Nullable
    Double value();

    /**
     * Increments the {@code LiveCounter} at this path by {@code 1}. Equivalent to
     * calling {@link #increment(Number)} with {@code 1}.
     *
     * <p>Spec: RTPO17a1 (default {@code amount} of {@code 1})
     *
     * @return a future that completes when the operation has been acknowledged
     */
    @NotNull
    CompletableFuture<Void> increment();

    /**
     * Increments the {@code LiveCounter} at this path by {@code amount}.
     *
     * <p>Sends a {@code COUNTER_INC} operation to the realtime system; the local state
     * is updated when the operation is echoed back. The returned future completes
     * exceptionally with an {@code AblyException} (status 400, code 92005) if the path
     * cannot be resolved, or (status 400, code 92007) if the resolved value is not a
     * {@code LiveCounter}.
     *
     * <p>Spec: RTPO17
     *
     * @param amount the amount to add (may be negative)
     * @return a future that completes when the operation has been acknowledged
     */
    @NotNull
    CompletableFuture<Void> increment(@NotNull Number amount);

    /**
     * Decrements the {@code LiveCounter} at this path by {@code 1}. Equivalent to
     * calling {@link #decrement(Number)} with {@code 1}.
     *
     * <p>Spec: RTPO18a1 (default {@code amount} of {@code 1})
     *
     * @return a future that completes when the operation has been acknowledged
     */
    @NotNull
    CompletableFuture<Void> decrement();

    /**
     * Decrements the {@code LiveCounter} at this path by {@code amount}. Equivalent to
     * calling {@link #increment(Number)} with a negated value.
     *
     * <p>Spec: RTPO18
     *
     * @param amount the amount to subtract (may be negative)
     * @return a future that completes when the operation has been acknowledged
     */
    @NotNull
    CompletableFuture<Void> decrement(@NotNull Number amount);
}
