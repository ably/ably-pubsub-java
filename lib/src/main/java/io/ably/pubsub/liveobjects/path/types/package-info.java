/**
 * Type-specific {@code PathObject} sub-types: the typed-SDK partition of path
 * operations. {@link io.ably.pubsub.liveobjects.path.types.LiveMapPathObject} (RTTS6a)
 * carries map navigation and writes,
 * {@link io.ably.pubsub.liveobjects.path.types.LiveCounterPathObject} (RTTS6b) carries
 * counter operations, and the six primitive sub-types (RTTS6c) expose only a
 * type-narrowed {@code value()}.
 *
 * <p>Spec: RTTS6
 */
package io.ably.pubsub.liveobjects.path.types;
