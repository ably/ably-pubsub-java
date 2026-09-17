package io.ably.pubsub.liveobjects.unit.fixtures

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ably.pubsub.liveobjects.message.WireMapCreate
import io.ably.pubsub.liveobjects.message.WireMapCreateWithObjectId
import io.ably.pubsub.liveobjects.message.WireMapSet
import io.ably.pubsub.liveobjects.message.WireObjectData
import io.ably.pubsub.liveobjects.message.WireObjectMessage
import io.ably.pubsub.liveobjects.message.WireObjectOperation
import io.ably.pubsub.liveobjects.message.WireObjectOperationAction
import io.ably.pubsub.liveobjects.message.WireObjectState
import io.ably.pubsub.liveobjects.message.WireObjectsCounter
import io.ably.pubsub.liveobjects.message.WireObjectsMap
import io.ably.pubsub.liveobjects.message.WireObjectsMapEntry
import io.ably.pubsub.liveobjects.message.WireObjectsMapSemantics
import java.util.Base64

internal val dummyObjectDataStringValue = WireObjectData(objectId = "object-id", string = "dummy string")

internal val dummyBinaryObjectValue = WireObjectData(objectId = "object-id", bytes = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)))

internal val dummyNumberObjectValue = WireObjectData(objectId = "object-id", number = 42.0)

internal val dummyBooleanObjectValue = WireObjectData(objectId = "object-id", boolean = true)

val dummyJsonObject = JsonObject().apply { addProperty("foo", "bar") }
internal val dummyJsonObjectValue = WireObjectData(objectId = "object-id", json = dummyJsonObject)

val dummyJsonArray = JsonArray().apply { add(1); add(2); add(3) }
internal val dummyJsonArrayValue = WireObjectData(objectId = "object-id", json = dummyJsonArray)

internal val dummyObjectsMapEntry = WireObjectsMapEntry(
  tombstone = false,
  timeserial = "dummy-timeserial",
  data = dummyObjectDataStringValue
)

internal val dummyObjectsMap = WireObjectsMap(
  semantics = WireObjectsMapSemantics.LWW,
  entries = mapOf("dummy-key" to dummyObjectsMapEntry)
)

internal val dummyObjectsCounter = WireObjectsCounter(
  count = 123.0
)

internal val dummyMapCreate = WireMapCreate(
  semantics = WireObjectsMapSemantics.LWW,
  entries = mapOf("dummy-key" to dummyObjectsMapEntry)
)

internal val dummyObjectOperation = WireObjectOperation(
  action = WireObjectOperationAction.MapCreate,
  objectId = "dummy-object-id",
  mapCreate = dummyMapCreate,
  mapCreateWithObjectId = WireMapCreateWithObjectId(nonce = "dummy-nonce", initialValue = "{\"foo\":\"bar\"}"),
)

internal val dummyObjectState = WireObjectState(
  objectId = "dummy-object-id",
  siteTimeserials = mapOf("site1" to "serial1"),
  tombstone = false,
  createOp = dummyObjectOperation,
  map = dummyObjectsMap,
  counter = dummyObjectsCounter
)

internal val dummyObjectMessage = WireObjectMessage(
  id = "dummy-id",
  timestamp = 1234567890L,
  clientId = "dummy-client-id",
  connectionId = "dummy-connection-id",
  extras = JsonObject().apply { addProperty("meta", "data") },
  operation = dummyObjectOperation,
  objectState = dummyObjectState,
  serial = "dummy-serial",
  siteCode = "dummy-site-code"
)

internal fun dummyObjectMessageWithStringData(): WireObjectMessage {
  return dummyObjectMessage
}

internal fun dummyObjectMessageWithBinaryData(): WireObjectMessage {
  val binaryObjectMapEntry = dummyObjectsMapEntry.copy(data = dummyBinaryObjectValue)
  val binaryObjectMap = dummyObjectsMap.copy(entries = mapOf("dummy-key" to binaryObjectMapEntry))
  val binaryMapCreate = dummyMapCreate.copy(entries = mapOf("dummy-key" to binaryObjectMapEntry))
  val binaryObjectOperation = dummyObjectOperation.copy(mapCreate = binaryMapCreate)
  val binaryObjectState = dummyObjectState.copy(
    map = binaryObjectMap,
    createOp = binaryObjectOperation
  )
  return dummyObjectMessage.copy(
    operation = binaryObjectOperation,
    objectState = binaryObjectState
  )
}

internal fun dummyObjectMessageWithNumberData(): WireObjectMessage {
  val numberObjectMapEntry = dummyObjectsMapEntry.copy(data = dummyNumberObjectValue)
  val numberObjectMap = dummyObjectsMap.copy(entries = mapOf("dummy-key" to numberObjectMapEntry))
  val numberMapCreate = dummyMapCreate.copy(entries = mapOf("dummy-key" to numberObjectMapEntry))
  val numberObjectOperation = dummyObjectOperation.copy(mapCreate = numberMapCreate)
  val numberObjectState = dummyObjectState.copy(
    map = numberObjectMap,
    createOp = numberObjectOperation
  )
  return dummyObjectMessage.copy(
    operation = numberObjectOperation,
    objectState = numberObjectState
  )
}

internal fun dummyObjectMessageWithBooleanData(): WireObjectMessage {
  val booleanObjectMapEntry = dummyObjectsMapEntry.copy(data = dummyBooleanObjectValue)
  val booleanObjectMap = dummyObjectsMap.copy(entries = mapOf("dummy-key" to booleanObjectMapEntry))
  val booleanMapCreate = dummyMapCreate.copy(entries = mapOf("dummy-key" to booleanObjectMapEntry))
  val booleanObjectOperation = dummyObjectOperation.copy(mapCreate = booleanMapCreate)
  val booleanObjectState = dummyObjectState.copy(
    map = booleanObjectMap,
    createOp = booleanObjectOperation
  )
  return dummyObjectMessage.copy(
    operation = booleanObjectOperation,
    objectState = booleanObjectState
  )
}

internal fun dummyObjectMessageWithJsonObjectData(): WireObjectMessage {
  val jsonObjectMapEntry = dummyObjectsMapEntry.copy(data = dummyJsonObjectValue)
  val jsonObjectMap = dummyObjectsMap.copy(entries = mapOf("dummy-key" to jsonObjectMapEntry))
  val jsonMapCreate = dummyMapCreate.copy(entries = mapOf("dummy-key" to jsonObjectMapEntry))
  val jsonObjectOperation = dummyObjectOperation.copy(
    action = WireObjectOperationAction.MapSet,
    mapCreate = null,
    mapSet = WireMapSet(key = "dummy-key", value = dummyJsonObjectValue)
  )
  val jsonObjectState = dummyObjectState.copy(
    map = jsonObjectMap,
    createOp = jsonObjectOperation
  )
  return dummyObjectMessage.copy(
    operation = jsonObjectOperation,
    objectState = jsonObjectState
  )
}

internal fun dummyObjectMessageWithJsonArrayData(): WireObjectMessage {
  val jsonArrayMapEntry = dummyObjectsMapEntry.copy(data = dummyJsonArrayValue)
  val jsonArrayMap = dummyObjectsMap.copy(entries = mapOf("dummy-key" to jsonArrayMapEntry))
  val jsonArrayOperation = dummyObjectOperation.copy(
    action = WireObjectOperationAction.MapSet,
    mapCreate = null,
    mapSet = WireMapSet(key = "dummy-key", value = dummyJsonArrayValue)
  )
  val jsonArrayState = dummyObjectState.copy(
    map = jsonArrayMap,
    createOp = jsonArrayOperation
  )
  return dummyObjectMessage.copy(
    operation = jsonArrayOperation,
    objectState = jsonArrayState
  )
}
