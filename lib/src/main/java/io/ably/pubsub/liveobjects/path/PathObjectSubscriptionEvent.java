package io.ably.pubsub.liveobjects.path;

import io.ably.pubsub.liveobjects.message.ObjectMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Event delivered to {@link PathObjectListener#onUpdated(PathObjectSubscriptionEvent)}
 * when a change affects the subscribed path.
 *
 * <p>Spec: RTPO19e / RTTS3d
 */
public interface PathObjectSubscriptionEvent {

    /**
     * Returns a {@link PathObject} pointing to the path where the change occurred.
     *
     * <p>Spec: RTPO19e1
     *
     * @return the {@code PathObject} at the changed path
     */
    @NotNull PathObject getObject();

    /**
     * Returns the {@link ObjectMessage} describing the operation that caused this
     * event, if any. The value is present whenever the underlying update carried
     * an object message with an operation; otherwise it is {@code null}.
     *
     * <p>Spec: RTPO19e2 / PAOM1
     *
     * @return the source {@code ObjectMessage}, or {@code null} if unavailable
     */
    @Nullable ObjectMessage getMessage();
}
