package io.ably.pubsub.liveobjects.path.types

import io.ably.pubsub.liveobjects.DefaultRealtimeObject
import io.ably.pubsub.liveobjects.path.DefaultPathObject
import io.ably.pubsub.liveobjects.pathNotResolvedError
import io.ably.pubsub.liveobjects.typeMismatchError
import io.ably.pubsub.liveobjects.value.ResolvedValue
import java.util.concurrent.CompletableFuture

/**
 * Default implementation of [LiveCounterPathObject].
 *
 * Counters are terminal nodes (no navigation), so this only adds the counter read/write
 * operations on top of [DefaultPathObject].
 *
 * Spec: RTTS6b
 */
internal class DefaultLiveCounterPathObject(
  channelObject: DefaultRealtimeObject,
  path: String,
) : DefaultPathObject(channelObject, path), LiveCounterPathObject {

  override fun value(): Double? { // RTTS6b
    channelObject.throwIfInvalidAccessApiConfiguration()
    val counter = (resolveValueAtCurrentPath() as? ResolvedValue.CounterRef)?.counter
      ?: return null // not a LiveCounter (or unresolved) -> null
    return counter.value() // RTPO7c via RTLC5c
  }

  // RTPO17a1 - default amount of 1; delegates to increment(Number)
  override fun increment(): CompletableFuture<Void> = increment(1)

  override fun increment(amount: Number): CompletableFuture<Void> {
    channelObject.throwIfInvalidWriteApiConfiguration() // RTPO17b / RTO26
    val resolvedValue = resolveValueAtCurrentPath() ?: throw pathNotResolvedError(path) // RTPO17c / RTPO3c2
    if (resolvedValue !is ResolvedValue.CounterRef) {
      throw typeMismatchError("Cannot increment a non-LiveCounter object at path: \"$path\"") // RTPO17e
    }
    return channelObject.asyncVoidFuture { resolvedValue.counter.increment(amount) } // RTPO17d -> RTLC12
  }

  // RTPO18a1 - default amount of 1; delegates to decrement(Number)
  override fun decrement(): CompletableFuture<Void> = decrement(1)

  override fun decrement(amount: Number): CompletableFuture<Void> {
    channelObject.throwIfInvalidWriteApiConfiguration() // RTPO18b / RTO26
    val resolvedValue = resolveValueAtCurrentPath() ?: throw pathNotResolvedError(path) // RTPO18c / RTPO3c2
    if (resolvedValue !is ResolvedValue.CounterRef) {
      throw typeMismatchError("Cannot decrement a non-LiveCounter object at path: \"$path\"") // RTPO18e
    }
    return channelObject.asyncVoidFuture { resolvedValue.counter.decrement(amount) } // RTPO18d -> RTLC13
  }
}
