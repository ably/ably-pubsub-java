package io.ably.pubsub.liveobjects.instance.types

import com.google.gson.JsonPrimitive
import io.ably.pubsub.liveobjects.DefaultRealtimeObject
import io.ably.pubsub.liveobjects.ValueType
import io.ably.pubsub.liveobjects.instance.DefaultInstance
import java.util.Base64

/**
 * Default implementation of [BinaryInstance], a read-only primitive view bound to its
 * extracted (base64-decoded) value (RTINS2a).
 *
 * Spec: RTTS10c
 */
internal class DefaultBinaryInstance(
  channelObject: DefaultRealtimeObject,
  internal val value: ByteArray,
) : DefaultInstance(channelObject), BinaryInstance {

  override fun getType(): ValueType = ValueType.BINARY

  override fun compactJson(): JsonPrimitive {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTINS11a
    return JsonPrimitive(Base64.getEncoder().encodeToString(value)) // RTTS7a3; base64 per RTPO14b1
  }

  override fun asBinary(): BinaryInstance = this

  override fun value(): ByteArray {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTINS4a
    return value.clone() // RTINS4c; defensive copy out
  }
}
