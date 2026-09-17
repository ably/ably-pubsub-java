package io.ably.pubsub.liveobjects.instance.types

import com.google.gson.JsonPrimitive
import io.ably.pubsub.liveobjects.DefaultRealtimeObject
import io.ably.pubsub.liveobjects.Subscription
import io.ably.pubsub.liveobjects.ValueType
import io.ably.pubsub.liveobjects.instance.DefaultInstance
import io.ably.pubsub.liveobjects.instance.InstanceListener
import io.ably.pubsub.liveobjects.value.livecounter.InternalLiveCounter
import java.util.concurrent.CompletableFuture

/**
 * Default implementation of [LiveCounterInstance], bound to a specific [InternalLiveCounter]
 * (RTINS2a). Operations dereference the wrapped counter in O(1) - no path resolution.
 *
 * Spec: RTTS10b
 */
internal class DefaultLiveCounterInstance(
  channelObject: DefaultRealtimeObject,
  internal val counter: InternalLiveCounter,
) : DefaultInstance(channelObject), LiveCounterInstance {

  override fun getType(): ValueType = ValueType.LIVE_COUNTER

  override fun compactJson(): JsonPrimitive {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTINS11a
    return JsonPrimitive(counter.value()) // RTPO13d; RTTS7a3 narrowed to JsonPrimitive
  }

  override fun asLiveCounter(): LiveCounterInstance = this

  override fun getId(): String = counter.objectId // RTINS3a; RTTS10b non-null

  override fun value(): Double {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTINS4a
    return counter.value() // RTINS4b via RTLC5c; RTTS10b non-null
  }

  // RTINS14a1 - default amount of 1; delegates to increment(Number)
  override fun increment(): CompletableFuture<Void> = increment(1)

  override fun increment(amount: Number): CompletableFuture<Void> {
    channelObject.throwIfInvalidWriteApiConfiguration() // RTINS14b
    return channelObject.asyncVoidFuture { counter.increment(amount) } // RTINS14c -> RTLC12
  }

  // RTINS15a1 - default amount of 1; delegates to decrement(Number)
  override fun decrement(): CompletableFuture<Void> = decrement(1)

  override fun decrement(amount: Number): CompletableFuture<Void> {
    channelObject.throwIfInvalidWriteApiConfiguration() // RTINS15b
    return channelObject.asyncVoidFuture { counter.decrement(amount) } // RTINS15c -> RTLC13
  }

  override fun subscribe(listener: InstanceListener): Subscription {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTINS16b
    // RTINS16c is satisfied by construction: primitive instances don't declare subscribe (RTTS10c)
    // RTINS16d - identity-based: follows the wrapped counter wherever it sits in the graph
    // (RTINS16g); pure registration, no side effects (RTINS16h)
    return counter.subscribe(listener)
  }
}
