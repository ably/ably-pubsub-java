package io.ably.pubsub.liveobjects.path;

import org.jetbrains.annotations.NotNull;

/**
 * Listener interface for path-based subscriptions created via
 * {@link PathObject#subscribe(PathObjectListener)} or
 * {@link PathObject#subscribe(PathObjectListener, PathObjectSubscriptionOptions)}.
 *
 * <p>Spec: RTPO19a1
 */
public interface PathObjectListener {

    /**
     * Invoked when a change is applied at, or beneath, the subscribed path according
     * to the configured {@link PathObjectSubscriptionOptions}.
     *
     * @param event the event describing the change
     */
    void onUpdated(@NotNull PathObjectSubscriptionEvent event);
}
