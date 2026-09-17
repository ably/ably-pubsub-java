package io.ably.pubsub.liveobjects.path.types

import io.ably.pubsub.liveobjects.DefaultRealtimeObject
import io.ably.pubsub.liveobjects.path.DefaultPathObject
import io.ably.pubsub.liveobjects.path.PathObject
import io.ably.pubsub.liveobjects.path.PathSegments
import io.ably.pubsub.liveobjects.pathNotResolvedError
import io.ably.pubsub.liveobjects.typeMismatchError
import io.ably.pubsub.liveobjects.value.LiveMapValue
import io.ably.pubsub.liveobjects.value.ResolvedValue
import java.util.AbstractMap
import java.util.concurrent.CompletableFuture

/**
 * Default implementation of [LiveMapPathObject], adding map navigation and read/write
 * operations on top of [DefaultPathObject].
 *
 * Spec: RTTS6a
 */
internal class DefaultLiveMapPathObject(
  channelObject: DefaultRealtimeObject,
  path: String,
) : DefaultPathObject(channelObject, path), LiveMapPathObject {

  // RTPO5c, RTPO5d - purely navigational, no resolution; returns the untyped base node (RTTS3h)
  override fun get(key: String): PathObject =
    DefaultPathObject(channelObject, PathSegments.appendKey(path, key))

  // RTPO6b, RTPO6c, RTPO6d - purely navigational, dot-delimited with backslash-escaped dots
  override fun at(path: String): PathObject =
    DefaultPathObject(channelObject, PathSegments.appendPath(this.path, path))

  override fun entries(): Iterable<Map.Entry<String, PathObject>> { // RTPO9
    channelObject.throwIfInvalidAccessApiConfiguration() // RTPO9a
    val map = (resolveValueAtCurrentPath() as? ResolvedValue.MapRef)?.map
      ?: return emptyList() // RTPO9d - not a LiveMap (or unresolved) -> empty
    // RTPO9c - derive from the map's keys at call time; child paths as if by get()
    return map.keys().map { key ->
      AbstractMap.SimpleImmutableEntry<String, PathObject>(key, get(key))
    }
  }

  override fun keys(): Iterable<String> { // RTPO10
    channelObject.throwIfInvalidAccessApiConfiguration() // RTPO10a
    val map = (resolveValueAtCurrentPath() as? ResolvedValue.MapRef)?.map
      ?: return emptyList() // RTPO10d - not a LiveMap (or unresolved) -> empty
    return map.keys().toList() // RTPO10c - via RTLM12
  }

  override fun values(): Iterable<PathObject> { // RTPO11
    channelObject.throwIfInvalidAccessApiConfiguration() // RTPO11a
    val map = (resolveValueAtCurrentPath() as? ResolvedValue.MapRef)?.map
      ?: return emptyList() // RTPO11d - not a LiveMap (or unresolved) -> empty
    return map.keys().map { key -> get(key) } // RTPO11c - child paths as if by get()
  }

  override fun size(): Long? { // RTPO12
    channelObject.throwIfInvalidAccessApiConfiguration() // RTPO12a
    val map = (resolveValueAtCurrentPath() as? ResolvedValue.MapRef)?.map
      ?: return null // RTPO12d - not a LiveMap (or unresolved) -> null
    return map.size() // RTPO12c - via RTLM10d
  }

  override fun set(key: String, value: LiveMapValue): CompletableFuture<Void> {
    channelObject.throwIfInvalidWriteApiConfiguration() // RTPO15b / RTO26
    val resolvedValue = resolveValueAtCurrentPath() ?: throw pathNotResolvedError(path) // RTPO15c / RTPO3c2
    if (resolvedValue !is ResolvedValue.MapRef) {
      throw typeMismatchError("Cannot set a key on a non-LiveMap object at path: \"$path\"") // RTPO15e
    }
    return channelObject.asyncVoidFuture { resolvedValue.map.set(key, value) } // RTPO15d -> RTLM20
  }

  override fun remove(key: String): CompletableFuture<Void> {
    channelObject.throwIfInvalidWriteApiConfiguration() // RTPO16b / RTO26
    val resolvedValue = resolveValueAtCurrentPath() ?: throw pathNotResolvedError(path) // RTPO16c / RTPO3c2
    if (resolvedValue !is ResolvedValue.MapRef) {
      throw typeMismatchError("Cannot remove a key from a non-LiveMap object at path: \"$path\"") // RTPO16e
    }
    return channelObject.asyncVoidFuture { resolvedValue.map.remove(key) } // RTPO16d -> RTLM21
  }
}
