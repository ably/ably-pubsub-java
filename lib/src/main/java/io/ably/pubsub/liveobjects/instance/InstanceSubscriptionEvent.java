package io.ably.pubsub.liveobjects.instance;

import io.ably.pubsub.liveobjects.instance.types.LiveCounterInstance;
import io.ably.pubsub.liveobjects.instance.types.LiveMapInstance;
import io.ably.pubsub.liveobjects.message.ObjectMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Event delivered to {@link InstanceListener#onUpdated(InstanceSubscriptionEvent)} when
 * the LiveObject wrapped by a subscribed {@link LiveMapInstance} or
 * {@link LiveCounterInstance} is updated.
 *
 * <p>Spec: RTINS16e
 */
public interface InstanceSubscriptionEvent {

    /**
     * Returns an {@link Instance} wrapping the LiveObject that was updated.
     *
     * <p>Spec: RTINS16e1
     *
     * @return the updated instance
     */
    @NotNull Instance getObject();

    /**
     * Returns the {@link ObjectMessage} describing the operation that caused this
     * event, if any. The value is present whenever the underlying update carried an
     * object message with an operation; otherwise it is {@code null}.
     *
     * <p>Spec: RTINS16e2 / PAOM1
     *
     * @return the source {@code ObjectMessage}, or {@code null} if unavailable
     */
    @Nullable ObjectMessage getMessage();
}
