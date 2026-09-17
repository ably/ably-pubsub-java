package io.ably.pubsub.liveobjects.path;

import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ErrorInfo;
import org.jetbrains.annotations.Nullable;

/**
 * Optional subscription options accepted by
 * {@link PathObject#subscribe(PathObjectListener, PathObjectSubscriptionOptions)}.
 *
 * <p>Spec: RTPO19c
 */
public final class PathObjectSubscriptionOptions {

    private final Integer depth;

    /**
     * Creates options with no {@code depth} set: there is no depth limit, and
     * changes at any depth within nested children trigger the listener.
     * Equivalent to passing a {@code null} depth.
     *
     * <p>Spec: RTPO19c1
     */
    public PathObjectSubscriptionOptions() {
        this.depth = null;
    }

    /**
     * Creates options with the given {@code depth}. For infinite depth, use the
     * no-arg constructor {@link #PathObjectSubscriptionOptions()} instead.
     *
     * <p>Spec: RTPO19c1, RTPO19c1a
     *
     * @param depth how many levels of path nesting below the subscribed path should
     *              trigger the listener; must be a positive integer
     * @throws AblyException with {@code statusCode} 400 and {@code code} 40003 if
     *                       {@code depth} is not a positive integer
     */
    public PathObjectSubscriptionOptions(int depth) throws AblyException {
        if (depth <= 0) {
            throw AblyException.fromErrorInfo(
                new ErrorInfo("Subscription depth must be greater than 0 or omitted for infinite depth", 400, 40003));
        }
        this.depth = depth;
    }

    /**
     * Returns the configured nesting depth, or {@code null} if not set.
     *
     * <p>Spec: RTPO19c1
     *
     * @return the depth value, or {@code null}
     */
    @Nullable
    public Integer getDepth() {
        return depth;
    }
}
