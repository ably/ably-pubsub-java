package io.ably.pubsub.liveobjects.instance.types

import com.google.gson.JsonArray
import io.ably.pubsub.liveobjects.DefaultRealtimeObject
import io.ably.pubsub.liveobjects.ValueType
import io.ably.pubsub.liveobjects.instance.DefaultInstance

/**
 * Default implementation of [JsonArrayInstance], a read-only primitive view bound to its
 * extracted value (RTINS2a).
 *
 * Spec: RTTS10c
 */
internal class DefaultJsonArrayInstance(
  channelObject: DefaultRealtimeObject,
  internal val value: JsonArray,
) : DefaultInstance(channelObject), JsonArrayInstance {

  override fun getType(): ValueType = ValueType.JSON_ARRAY

  override fun compactJson(): JsonArray {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTINS11a
    return value // RTTS7a2
  }

  override fun asJsonArray(): JsonArrayInstance = this

  override fun value(): JsonArray {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTINS4a
    return value // RTINS4c; RTTS10c non-null
  }
}
