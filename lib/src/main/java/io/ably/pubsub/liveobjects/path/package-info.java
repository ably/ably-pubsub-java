/**
 * The path-addressed view of the LiveObjects graph.
 * {@link io.ably.pubsub.liveobjects.path.PathObject} stores a path from the channel's
 * root {@code LiveMap} and re-resolves it lazily on every call, so a reference
 * survives object replacement at its path. Type-specific operations live on the
 * sub-types in {@link io.ably.pubsub.liveobjects.path.types}; path-based subscriptions
 * use {@link io.ably.pubsub.liveobjects.path.PathObjectListener},
 * {@link io.ably.pubsub.liveobjects.path.PathObjectSubscriptionEvent} and
 * {@link io.ably.pubsub.liveobjects.path.PathObjectSubscriptionOptions}.
 *
 * <p>Spec: RTPO1-RTPO19, RTTS3-RTTS5
 */
package io.ably.pubsub.liveobjects.path;
