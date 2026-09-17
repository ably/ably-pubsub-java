package io.ably.pubsub.liveobjects.instance.types

import com.google.gson.JsonPrimitive
import io.ably.pubsub.liveobjects.DefaultRealtimeObject
import io.ably.pubsub.liveobjects.ValueType
import io.ably.pubsub.liveobjects.instance.DefaultInstance

/**
 * Default implementation of [StringInstance], a read-only primitive view bound to its
 * extracted value (RTINS2a).
 *
 * Spec: RTTS10c
 */
internal class DefaultStringInstance(
  channelObject: DefaultRealtimeObject,
  internal val value: String,
) : DefaultInstance(channelObject), StringInstance {

  override fun getType(): ValueType = ValueType.STRING

  override fun compactJson(): JsonPrimitive {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTINS11a
    return JsonPrimitive(value) // RTTS7a3
  }

  override fun asString(): StringInstance = this

  override fun value(): String {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTINS4a
    return value // RTINS4c; RTTS10c non-null
  }
}
