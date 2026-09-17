/**
 * Adapter layer bridging the path-based LiveObjects implementation to the core Ably client.
 * {@link io.ably.pubsub.liveobjects.adapter.AblyClientAdapter} is the abstraction the implementation
 * depends on; {@link io.ably.pubsub.liveobjects.adapter.Adapter} is the default implementation backed
 * by an {@link io.ably.pubsub.realtime.RealtimeClient} client.
 *
 * <p>This package is intentionally independent of the legacy {@code io.ably.pubsub.objects}
 * package so the path-based API can evolve on its own.
 */
package io.ably.pubsub.liveobjects.adapter;
