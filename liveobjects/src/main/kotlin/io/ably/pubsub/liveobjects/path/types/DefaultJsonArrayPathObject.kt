package io.ably.pubsub.liveobjects.path.types

import com.google.gson.JsonArray
import io.ably.pubsub.liveobjects.DefaultRealtimeObject
import io.ably.pubsub.liveobjects.ValueType
import io.ably.pubsub.liveobjects.path.DefaultPathObject
import io.ably.pubsub.liveobjects.value.ResolvedValue
import io.ably.pubsub.liveobjects.value.valueType

/**
 * Default implementation of [JsonArrayPathObject], a terminal primitive view that only adds
 * a type-narrowed [value].
 *
 * Spec: RTTS6c
 */
internal class DefaultJsonArrayPathObject(
  channelObject: DefaultRealtimeObject,
  path: String,
) : DefaultPathObject(channelObject, path), JsonArrayPathObject {

  override fun value(): JsonArray? {
    channelObject.throwIfInvalidAccessApiConfiguration()
    val resolved = resolveValueAtCurrentPath() ?: return null
    if (resolved.valueType() != ValueType.JSON_ARRAY) return null // RTTS6c - exact type only
    return (resolved as ResolvedValue.Leaf).data.json!!.asJsonArray
  }
}
