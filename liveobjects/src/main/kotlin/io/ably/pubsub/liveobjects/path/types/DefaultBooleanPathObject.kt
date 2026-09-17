package io.ably.pubsub.liveobjects.path.types

import io.ably.pubsub.liveobjects.DefaultRealtimeObject
import io.ably.pubsub.liveobjects.ValueType
import io.ably.pubsub.liveobjects.path.DefaultPathObject
import io.ably.pubsub.liveobjects.value.ResolvedValue
import io.ably.pubsub.liveobjects.value.valueType

/**
 * Default implementation of [BooleanPathObject], a terminal primitive view that only adds a
 * type-narrowed [value].
 *
 * Spec: RTTS6c
 */
internal class DefaultBooleanPathObject(
  channelObject: DefaultRealtimeObject,
  path: String,
) : DefaultPathObject(channelObject, path), BooleanPathObject {

  override fun value(): Boolean? {
    channelObject.throwIfInvalidAccessApiConfiguration()
    val resolved = resolveValueAtCurrentPath() ?: return null
    if (resolved.valueType() != ValueType.BOOLEAN) return null // RTTS6c - exact type only
    return (resolved as ResolvedValue.Leaf).data.boolean
  }
}
