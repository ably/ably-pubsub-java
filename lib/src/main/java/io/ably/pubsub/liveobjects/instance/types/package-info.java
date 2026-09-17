/**
 * Type-specific {@code Instance} sub-types: the typed-SDK partition of instance
 * operations. {@link io.ably.pubsub.liveobjects.instance.types.LiveMapInstance}
 * (RTTS10a) carries map reads, writes and subscribe,
 * {@link io.ably.pubsub.liveobjects.instance.types.LiveCounterInstance} (RTTS10b)
 * carries counter operations and subscribe, and the six primitive sub-types
 * (RTTS10c) expose only a type-narrowed, non-null {@code value()}.
 *
 * <p>Spec: RTTS10
 */
package io.ably.pubsub.liveobjects.instance.types;
