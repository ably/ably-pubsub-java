package io.ably.pubsub.liveobjects.value.livemap

import io.ably.pubsub.liveobjects.DefaultRealtimeObject
import io.ably.pubsub.liveobjects.invalidInputError
import io.ably.pubsub.liveobjects.message.WireMapCreate
import io.ably.pubsub.liveobjects.message.WireMapCreateWithObjectId
import io.ably.pubsub.liveobjects.message.WireObjectData
import io.ably.pubsub.liveobjects.message.WireObjectMessage
import io.ably.pubsub.liveobjects.message.WireObjectOperation
import io.ably.pubsub.liveobjects.message.WireObjectOperationAction
import io.ably.pubsub.liveobjects.message.WireObjectsMapEntry
import io.ably.pubsub.liveobjects.message.WireObjectsMapSemantics
import io.ably.pubsub.liveobjects.serialization.gson
import io.ably.pubsub.liveobjects.value.LiveMap
import io.ably.pubsub.liveobjects.value.LiveMapValue
import io.ably.pubsub.liveobjects.value.ObjectType
import io.ably.pubsub.liveobjects.value.livecounter.DefaultLiveCounter
import java.util.Base64

/**
 * Default implementation of the [LiveMap] value type - an immutable holder for the
 * initial entries of a LiveMap object to be created. Mirrors ably-js
 * `LiveMapValueType`.
 *
 * Instantiated reflectively by [LiveMap.create] through the constructor that takes
 * the initial entries map; the entries are retained internally with no public
 * accessor (Spec: RTLMV3d).
 *
 * Spec: RTLMV1, RTLMV2, RTLMV3
 */
internal class DefaultLiveMap(
    internal val entries: Map<String, LiveMapValue>,
) : LiveMap() {

  /**
   * Evaluates this value type into `[nested creates..., MAP_CREATE]` - depth-first, with this
   * map's own MAP_CREATE last (RTLMV4k). Mirrors ably-js
   * `LiveMapValueType.createMapCreateMessage` / `_getMapCreate`. The caller publishes the
   * messages (evaluation itself has no side effects on the channel).
   *
   * RTLMV4a (entries must be a Dict) and the RTLMV4c value-type check are compile-time
   * impossible with `Map<String, LiveMapValue>` - recorded as typed-SDK deviations.
   *
   * Spec: RTLMV4
   */
  internal suspend fun createMapCreateMessages(realtimeObject: DefaultRealtimeObject): List<WireObjectMessage> {
    if (entries.keys.any { it.isEmpty() }) { // RTLMV4b - String typing is compile-time; emptiness is ours
      throw invalidInputError("Map keys should not be empty")
    }

    val nestedCreateMessages = mutableListOf<WireObjectMessage>()
    val mapEntries = mutableMapOf<String, WireObjectsMapEntry>()
    for ((key, value) in entries) { // RTLMV4d
      val objectData: WireObjectData = when {
        value.isLiveCounter -> { // RTLMV4d1
          val msg = (value.asLiveCounter as DefaultLiveCounter).createCounterCreateMessage(realtimeObject)
          nestedCreateMessages.add(msg)
          WireObjectData(objectId = msg.operation!!.objectId)
        }
        value.isLiveMap -> { // RTLMV4d2 - recursive, depth-first
          val msgs = (value.asLiveMap as DefaultLiveMap).createMapCreateMessages(realtimeObject)
          nestedCreateMessages.addAll(msgs)
          // last message is the nested map's own MAP_CREATE (RTLMV4k)
          WireObjectData(objectId = msgs.last().operation!!.objectId)
        }
        else -> fromPrimitiveLiveMapValue(value) // RTLMV4d3..d7
      }
      mapEntries[key] = WireObjectsMapEntry(tombstone = false, data = objectData)
    }

    val mapCreate = WireMapCreate(semantics = WireObjectsMapSemantics.LWW, entries = mapEntries) // RTLMV4e
    // RTLMV4f - the @JsonAdapter WireObjectDataJsonSerializer performs the OD4 wire encoding
    val initialValueJSONString = gson.toJson(mapCreate)
    // RTLMV4g..i - nonce, server time and RTO14 objectId derivation
    val (objectId, nonce) = realtimeObject.getObjectIdStringWithNonce(ObjectType.Map, initialValueJSONString)

    val mapCreateMsg = WireObjectMessage(
      operation = WireObjectOperation(
        action = WireObjectOperationAction.MapCreate, // RTLMV4j1
        objectId = objectId, // RTLMV4j2
        mapCreateWithObjectId = WireMapCreateWithObjectId(
          nonce = nonce, // RTLMV4j3
          initialValue = initialValueJSONString, // RTLMV4j4
          derivedFrom = mapCreate, // RTLMV4j5 - local use only (@Transient on the wire type)
        ),
      )
    )
    return nestedCreateMessages + mapCreateMsg // RTLMV4k
  }
}

/**
 * Converts a primitive [LiveMapValue] to its wire ObjectData form. Shared by the MAP_SET path
 * (RTLM20e7b..f) and value-type evaluation (RTLMV4d3..d7). LiveMap/LiveCounter value types are
 * handled by the callers before reaching here.
 */
internal fun fromPrimitiveLiveMapValue(value: LiveMapValue): WireObjectData {
  return when {
    value.isBoolean -> WireObjectData(boolean = value.asBoolean) // RTLM20e7e
    value.isBinary -> WireObjectData(bytes = Base64.getEncoder().encodeToString(value.asBinary)) // RTLM20e7f
    value.isNumber -> WireObjectData(number = value.asNumber.toDouble()) // RTLM20e7d
    value.isString -> WireObjectData(string = value.asString) // RTLM20e7c
    value.isJsonObject -> WireObjectData(json = value.asJsonObject) // RTLM20e7b
    value.isJsonArray -> WireObjectData(json = value.asJsonArray) // RTLM20e7b
    else -> throw IllegalArgumentException("Unsupported value type") // unreachable - LiveMapValue union is closed
  }
}
