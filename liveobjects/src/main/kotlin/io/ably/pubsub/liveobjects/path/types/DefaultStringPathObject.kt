package io.ably.pubsub.liveobjects.path.types

import io.ably.pubsub.liveobjects.DefaultRealtimeObject
import io.ably.pubsub.liveobjects.ValueType
import io.ably.pubsub.liveobjects.path.DefaultPathObject
import io.ably.pubsub.liveobjects.value.ResolvedValue
import io.ably.pubsub.liveobjects.value.valueType

/**
 * Default implementation of [StringPathObject], a terminal primitive view that only adds a
 * type-narrowed [value].
 *
 * Spec: RTTS6c
 */
internal class DefaultStringPathObject(
  channelObject: DefaultRealtimeObject,
  path: String,
) : DefaultPathObject(channelObject, path), StringPathObject {

  override fun value(): String? {
    channelObject.throwIfInvalidAccessApiConfiguration()
    val resolved = resolveValueAtCurrentPath() ?: return null
    if (resolved.valueType() != ValueType.STRING) return null // RTTS6c - exact type only
    return (resolved as ResolvedValue.Leaf).data.string
  }
}
