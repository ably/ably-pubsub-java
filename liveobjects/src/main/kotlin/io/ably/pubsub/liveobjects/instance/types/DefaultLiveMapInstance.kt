package io.ably.pubsub.liveobjects.instance.types

import com.google.gson.JsonObject
import io.ably.pubsub.liveobjects.DefaultRealtimeObject
import io.ably.pubsub.liveobjects.Subscription
import io.ably.pubsub.liveobjects.ValueType
import io.ably.pubsub.liveobjects.instance.DefaultInstance
import io.ably.pubsub.liveobjects.instance.Instance
import io.ably.pubsub.liveobjects.instance.InstanceListener
import io.ably.pubsub.liveobjects.instance.toInstance
import io.ably.pubsub.liveobjects.value.LiveMapValue
import io.ably.pubsub.liveobjects.value.livemap.InternalLiveMap
import java.util.AbstractMap
import java.util.concurrent.CompletableFuture

/**
 * Default implementation of [LiveMapInstance], bound to a specific [InternalLiveMap]
 * (RTINS2a). Operations dereference the wrapped map in O(1) - no path resolution.
 *
 * No type-guard branches are needed: the wrapped value is statically an InternalLiveMap
 * (RTTS9d - a matching cast returns a view that always matches), so the RTINS wrong-type
 * clauses are unreachable by construction.
 *
 * Spec: RTTS10a
 */
internal class DefaultLiveMapInstance(
  channelObject: DefaultRealtimeObject,
  internal val map: InternalLiveMap,
) : DefaultInstance(channelObject), LiveMapInstance {

  override fun getType(): ValueType = ValueType.LIVE_MAP

  override fun compactJson(): JsonObject {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTINS11a
    return map.compactJson() // RTINS11b -> RTPO13c/RTPO14b; RTTS7a1 narrowed to JsonObject
  }

  override fun asLiveMap(): LiveMapInstance = this

  override fun getId(): String = map.objectId // RTINS3a; RTTS10a non-null

  override fun get(key: String): Instance? {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTINS5b
    return map.get(key)?.toInstance(channelObject) // RTINS5c - null result stays null
  }

  override fun entries(): Iterable<Map.Entry<String, Instance>> {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTINS6a
    // RTINS6b. Deviation from RTLM11d3a (documented in the plan): entries whose value resolves
    // to null (dangling reference) or to an UNKNOWN leaf are skipped - a typed Iterable cannot
    // carry a null Instance. keys() intentionally does not skip them (RTLM12 delegation).
    return map.entries().mapNotNull { (key, resolved) ->
      resolved?.toInstance(channelObject)?.let { AbstractMap.SimpleImmutableEntry<String, Instance>(key, it) }
    }
  }

  override fun keys(): Iterable<String> {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTINS7a
    return map.keys().toList() // RTINS7b - via RTLM12
  }

  override fun values(): Iterable<Instance> {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTINS8a
    return entries().map { it.value } // RTINS8b
  }

  override fun size(): Long {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTINS9a
    return map.size() // RTINS9b - via RTLM10d; RTTS10a non-null
  }

  override fun set(key: String, value: LiveMapValue): CompletableFuture<Void> {
    channelObject.throwIfInvalidWriteApiConfiguration() // RTINS12b
    return channelObject.asyncVoidFuture { map.set(key, value) } // RTINS12c -> RTLM20
  }

  override fun remove(key: String): CompletableFuture<Void> {
    channelObject.throwIfInvalidWriteApiConfiguration() // RTINS13b
    return channelObject.asyncVoidFuture { map.remove(key) } // RTINS13c -> RTLM21
  }

  override fun subscribe(listener: InstanceListener): Subscription {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTINS16b
    // RTINS16c is satisfied by construction: primitive instances don't declare subscribe (RTTS10c)
    // RTINS16d - identity-based: follows the wrapped map wherever it sits in the graph
    // (RTINS16g); pure registration, no side effects (RTINS16h)
    return map.subscribe(listener)
  }
}
