/**
 * The identity-addressed view of the LiveObjects graph.
 * {@link io.ably.pubsub.liveobjects.instance.Instance} wraps a specific resolved
 * LiveObject or primitive value and dereferences it in O(1), following the
 * object wherever it sits in the graph. Type-specific operations live on the
 * sub-types in {@link io.ably.pubsub.liveobjects.instance.types}; instance
 * subscriptions use {@link io.ably.pubsub.liveobjects.instance.InstanceListener} and
 * {@link io.ably.pubsub.liveobjects.instance.InstanceSubscriptionEvent}.
 *
 * <p>Spec: RTINS1-RTINS16, RTTS7-RTTS9
 */
package io.ably.pubsub.liveobjects.instance;
