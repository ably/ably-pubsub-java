package io.ably.pubsub.liveobjects.instance

import io.ably.pubsub.liveobjects.DefaultRealtimeObject
import io.ably.pubsub.liveobjects.ValueType
import io.ably.pubsub.liveobjects.instance.types.BinaryInstance
import io.ably.pubsub.liveobjects.instance.types.BooleanInstance
import io.ably.pubsub.liveobjects.instance.types.DefaultBinaryInstance
import io.ably.pubsub.liveobjects.instance.types.DefaultBooleanInstance
import io.ably.pubsub.liveobjects.instance.types.DefaultJsonArrayInstance
import io.ably.pubsub.liveobjects.instance.types.DefaultJsonObjectInstance
import io.ably.pubsub.liveobjects.instance.types.DefaultLiveCounterInstance
import io.ably.pubsub.liveobjects.instance.types.DefaultLiveMapInstance
import io.ably.pubsub.liveobjects.instance.types.DefaultNumberInstance
import io.ably.pubsub.liveobjects.instance.types.DefaultStringInstance
import io.ably.pubsub.liveobjects.instance.types.JsonArrayInstance
import io.ably.pubsub.liveobjects.instance.types.JsonObjectInstance
import io.ably.pubsub.liveobjects.instance.types.LiveCounterInstance
import io.ably.pubsub.liveobjects.instance.types.LiveMapInstance
import io.ably.pubsub.liveobjects.instance.types.NumberInstance
import io.ably.pubsub.liveobjects.instance.types.StringInstance
import io.ably.pubsub.liveobjects.value.ResolvedValue
import io.ably.pubsub.liveobjects.value.valueType
import java.util.Base64

/**
 * Default implementation of [Instance], the identity-addressed node in the LiveObjects graph.
 *
 * An instance is always bound to a specific resolved value of a known type, so this base is
 * abstract: each concrete sub-type supplies [getType] and [compactJson] (left abstract here)
 * and overrides only the single `as*` cast matching its own type to return `this`. The
 * remaining `as*` casts fall through to the implementations here, which fail fast because the
 * wrapped value is not of the requested type.
 *
 * Only the channel's [channelObject] context is carried; unlike a path object there is no
 * parent/child path, since an instance is identity-addressed.
 *
 * Spec: RTINS1, RTTS7
 */
internal abstract class DefaultInstance(
  internal val channelObject: DefaultRealtimeObject,
) : Instance {

  override fun asLiveMap(): LiveMapInstance = throw IllegalStateException("Not a LiveMap instance")

  override fun asLiveCounter(): LiveCounterInstance = throw IllegalStateException("Not a LiveCounter instance")

  override fun asNumber(): NumberInstance = throw IllegalStateException("Not a Number instance")

  override fun asString(): StringInstance = throw IllegalStateException("Not a String instance")

  override fun asBoolean(): BooleanInstance = throw IllegalStateException("Not a Boolean instance")

  override fun asBinary(): BinaryInstance = throw IllegalStateException("Not a Binary instance")

  override fun asJsonObject(): JsonObjectInstance = throw IllegalStateException("Not a JsonObject instance")

  override fun asJsonArray(): JsonArrayInstance = throw IllegalStateException("Not a JsonArray instance")
}

/**
 * Wraps a resolved value in its typed Instance. Returns null only for a Leaf that matches no
 * known category (ValueType.UNKNOWN - no typed wrapper exists for it). Primitive instances bind
 * the extracted (decoded) value, not the wire leaf: an Instance is identity/value-addressed and
 * O(1), so it must not re-read mutable map state.
 *
 * Spec: RTPO8c, RTPO8f, RTINS5c, RTTS7e (an Instance is always a concrete typed sub-class)
 */
internal fun ResolvedValue.toInstance(channelObject: DefaultRealtimeObject): Instance? = when (this) {
  is ResolvedValue.MapRef -> DefaultLiveMapInstance(channelObject, map)
  is ResolvedValue.CounterRef -> DefaultLiveCounterInstance(channelObject, counter)
  is ResolvedValue.Leaf -> when (valueType()) {
    ValueType.STRING -> DefaultStringInstance(channelObject, data.string!!)
    ValueType.NUMBER -> DefaultNumberInstance(channelObject, data.number!!)
    ValueType.BOOLEAN -> DefaultBooleanInstance(channelObject, data.boolean!!)
    ValueType.BINARY -> DefaultBinaryInstance(channelObject, Base64.getDecoder().decode(data.bytes))
    ValueType.JSON_OBJECT -> DefaultJsonObjectInstance(channelObject, data.json!!.asJsonObject)
    ValueType.JSON_ARRAY -> DefaultJsonArrayInstance(channelObject, data.json!!.asJsonArray)
    else -> null // UNKNOWN leaf - no typed wrapper exists
  }
}
