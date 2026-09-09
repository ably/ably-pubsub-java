package io.ably.lib.liveobjects.serialization

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ably.lib.liveobjects.message.WireCounterCreate
import io.ably.lib.liveobjects.message.WireCounterCreateWithObjectId
import io.ably.lib.liveobjects.message.WireCounterInc
import io.ably.lib.liveobjects.message.WireMapClear
import io.ably.lib.liveobjects.message.WireMapCreate
import io.ably.lib.liveobjects.message.WireMapCreateWithObjectId
import io.ably.lib.liveobjects.message.WireMapRemove
import io.ably.lib.liveobjects.message.WireMapSet
import io.ably.lib.liveobjects.message.WireObjectData
import io.ably.lib.liveobjects.message.WireObjectDelete
import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.message.WireObjectOperation
import io.ably.lib.liveobjects.message.WireObjectOperationAction
import io.ably.lib.liveobjects.message.WireObjectState
import io.ably.lib.liveobjects.message.WireObjectsCounter
import io.ably.lib.liveobjects.message.WireObjectsMap
import io.ably.lib.liveobjects.message.WireObjectsMapEntry
import io.ably.lib.liveobjects.message.WireObjectsMapSemantics
import io.ably.lib.liveobjects.objectStateError
import io.ably.lib.util.Serialisation
import java.util.Base64
import org.msgpack.core.MessageFormat
import org.msgpack.core.MessagePacker
import org.msgpack.core.MessageUnpacker

/**
 * Write WireObjectMessage to MessagePacker
 */
internal fun WireObjectMessage.writeMsgpack(packer: MessagePacker) {
  var fieldCount = 0

  if (id != null) fieldCount++
  if (timestamp != null) fieldCount++
  if (clientId != null) fieldCount++
  if (connectionId != null) fieldCount++
  if (extras != null) fieldCount++
  if (operation != null) fieldCount++
  if (objectState != null) fieldCount++
  if (serial != null) fieldCount++
  if (serialTimestamp != null) fieldCount++
  if (siteCode != null) fieldCount++

  packer.packMapHeader(fieldCount)

  if (id != null) {
    packer.packString("id")
    packer.packString(id)
  }

  if (timestamp != null) {
    packer.packString("timestamp")
    packer.packLong(timestamp)
  }

  if (clientId != null) {
    packer.packString("clientId")
    packer.packString(clientId)
  }

  if (connectionId != null) {
    packer.packString("connectionId")
    packer.packString(connectionId)
  }

  if (extras != null) {
    packer.packString("extras")
    packer.writePayload(Serialisation.gsonToMsgpack(extras))
  }

  if (operation != null) {
    packer.packString("operation")
    operation.writeMsgpack(packer)
  }

  if (objectState != null) {
    packer.packString("object")
    objectState.writeMsgpack(packer)
  }

  if (serial != null) {
    packer.packString("serial")
    packer.packString(serial)
  }

  if (serialTimestamp != null) {
    packer.packString("serialTimestamp")
    packer.packLong(serialTimestamp)
  }

  if (siteCode != null) {
    packer.packString("siteCode")
    packer.packString(siteCode)
  }
}

/**
 * Read a WireObjectMessage from MessageUnpacker
 */
internal fun readObjectMessage(unpacker: MessageUnpacker): WireObjectMessage {
  if (unpacker.nextFormat == MessageFormat.NIL) {
    unpacker.unpackNil()
    return WireObjectMessage() // default/empty message
  }

  val fieldCount = unpacker.unpackMapHeader()

  var id: String? = null
  var timestamp: Long? = null
  var clientId: String? = null
  var connectionId: String? = null
  var extras: JsonObject? = null
  var operation: WireObjectOperation? = null
  var objectState: WireObjectState? = null
  var serial: String? = null
  var serialTimestamp: Long? = null
  var siteCode: String? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat

    if (fieldFormat == MessageFormat.NIL) {
      unpacker.unpackNil()
      continue
    }

    when (fieldName) {
      "id" -> id = unpacker.unpackString()
      "timestamp" -> timestamp = unpacker.unpackLong()
      "clientId" -> clientId = unpacker.unpackString()
      "connectionId" -> connectionId = unpacker.unpackString()
      "extras" -> extras = Serialisation.msgpackToGson(unpacker.unpackValue()) as? JsonObject
      "operation" -> operation = readObjectOperation(unpacker)
      "object" -> objectState = readObjectState(unpacker)
      "serial" -> serial = unpacker.unpackString()
      "serialTimestamp" -> serialTimestamp = unpacker.unpackLong()
      "siteCode" -> siteCode = unpacker.unpackString()
      else -> unpacker.skipValue()
    }
  }

  return WireObjectMessage(
    id = id,
    timestamp = timestamp,
    clientId = clientId,
    connectionId = connectionId,
    extras = extras,
    operation = operation,
    objectState = objectState,
    serial = serial,
    serialTimestamp = serialTimestamp,
    siteCode = siteCode
  )
}

/**
 * Write WireObjectOperation to MessagePacker
 */
private fun WireObjectOperation.writeMsgpack(packer: MessagePacker) {
  var fieldCount = 1 // action is always required
  require(objectId.isNotEmpty()) { "objectId must be non-empty per Objects protocol" }
  fieldCount++

  if (mapCreate != null) fieldCount++
  if (mapSet != null) fieldCount++
  if (mapRemove != null) fieldCount++
  if (counterCreate != null) fieldCount++
  if (counterInc != null) fieldCount++
  if (objectDelete != null) fieldCount++
  if (mapCreateWithObjectId != null) fieldCount++
  if (counterCreateWithObjectId != null) fieldCount++
  if (mapClear != null) fieldCount++

  packer.packMapHeader(fieldCount)

  packer.packString("action")
  packer.packInt(action.code)

  // Always include objectId as per Objects protocol
  packer.packString("objectId")
  packer.packString(objectId)

  if (mapCreate != null) {
    packer.packString("mapCreate")
    mapCreate.writeMsgpack(packer)
  }

  if (mapSet != null) {
    packer.packString("mapSet")
    mapSet.writeMsgpack(packer)
  }

  if (mapRemove != null) {
    packer.packString("mapRemove")
    mapRemove.writeMsgpack(packer)
  }

  if (counterCreate != null) {
    packer.packString("counterCreate")
    counterCreate.writeMsgpack(packer)
  }

  if (counterInc != null) {
    packer.packString("counterInc")
    counterInc.writeMsgpack(packer)
  }

  if (objectDelete != null) {
    packer.packString("objectDelete")
    packer.packMapHeader(0) // empty map
  }

  if (mapCreateWithObjectId != null) {
    packer.packString("mapCreateWithObjectId")
    mapCreateWithObjectId.writeMsgpack(packer)
  }

  if (counterCreateWithObjectId != null) {
    packer.packString("counterCreateWithObjectId")
    counterCreateWithObjectId.writeMsgpack(packer)
  }

  if (mapClear != null) {
    packer.packString("mapClear")
    packer.packMapHeader(0) // empty map, no fields
  }

}

/**
 * Read WireObjectOperation from MessageUnpacker
 */
private fun readObjectOperation(unpacker: MessageUnpacker): WireObjectOperation {
  val fieldCount = unpacker.unpackMapHeader()

  var action: WireObjectOperationAction? = null
  var objectId: String = ""
  var mapCreate: WireMapCreate? = null
  var mapSet: WireMapSet? = null
  var mapRemove: WireMapRemove? = null
  var counterCreate: WireCounterCreate? = null
  var counterInc: WireCounterInc? = null
  var objectDelete: WireObjectDelete? = null
  var mapCreateWithObjectId: WireMapCreateWithObjectId? = null
  var counterCreateWithObjectId: WireCounterCreateWithObjectId? = null
  var mapClear: WireMapClear? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat

    if (fieldFormat == MessageFormat.NIL) {
      unpacker.unpackNil()
      continue
    }

    when (fieldName) {
      "action" -> {
        val actionCode = unpacker.unpackInt()
        action = WireObjectOperationAction.entries.firstOrNull { it.code == actionCode }
          ?: WireObjectOperationAction.entries.firstOrNull { it.code == -1 }
          ?: throw objectStateError("Unknown WireObjectOperationAction code: $actionCode and no Unknown fallback found")
      }
      "objectId" -> objectId = unpacker.unpackString()
      "mapCreate" -> mapCreate = readMapCreate(unpacker)
      "mapSet" -> mapSet = readMapSet(unpacker)
      "mapRemove" -> mapRemove = readMapRemove(unpacker)
      "counterCreate" -> counterCreate = readCounterCreate(unpacker)
      "counterInc" -> counterInc = readCounterInc(unpacker)
      "objectDelete" -> {
        unpacker.skipValue() // empty map, just consume it
        objectDelete = WireObjectDelete
      }
      "mapCreateWithObjectId" -> mapCreateWithObjectId = readMapCreateWithObjectId(unpacker)
      "counterCreateWithObjectId" -> counterCreateWithObjectId = readCounterCreateWithObjectId(unpacker)
      "mapClear" -> {
        unpacker.skipValue() // empty map, consume it
        mapClear = WireMapClear
      }
      else -> unpacker.skipValue()
    }
  }

  if (action == null) {
    throw objectStateError("Missing required 'action' field in WireObjectOperation")
  }

  if (objectId.isEmpty()) {
    throw objectStateError("Missing required 'objectId' field in WireObjectOperation")
  }

  return WireObjectOperation(
    action = action,
    objectId = objectId,
    mapCreate = mapCreate,
    mapSet = mapSet,
    mapRemove = mapRemove,
    counterCreate = counterCreate,
    counterInc = counterInc,
    objectDelete = objectDelete,
    mapCreateWithObjectId = mapCreateWithObjectId,
    counterCreateWithObjectId = counterCreateWithObjectId,
    mapClear = mapClear,
  )
}

/**
 * Write WireObjectState to MessagePacker
 */
private fun WireObjectState.writeMsgpack(packer: MessagePacker) {
  var fieldCount = 3 // objectId, siteTimeserials, and tombstone are required

  if (createOp != null) fieldCount++
  if (map != null) fieldCount++
  if (counter != null) fieldCount++

  packer.packMapHeader(fieldCount)

  packer.packString("objectId")
  packer.packString(objectId)

  packer.packString("siteTimeserials")
  packer.packMapHeader(siteTimeserials.size)
  for ((key, value) in siteTimeserials) {
    packer.packString(key)
    packer.packString(value)
  }

  packer.packString("tombstone")
  packer.packBoolean(tombstone)

  if (createOp != null) {
    packer.packString("createOp")
    createOp.writeMsgpack(packer)
  }

  if (map != null) {
    packer.packString("map")
    map.writeMsgpack(packer)
  }

  if (counter != null) {
    packer.packString("counter")
    counter.writeMsgpack(packer)
  }
}

/**
 * Read WireObjectState from MessageUnpacker
 */
private fun readObjectState(unpacker: MessageUnpacker): WireObjectState {
  val fieldCount = unpacker.unpackMapHeader()

  var objectId = ""
  var siteTimeserials = mapOf<String, String>()
  var tombstone = false
  var createOp: WireObjectOperation? = null
  var map: WireObjectsMap? = null
  var counter: WireObjectsCounter? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat

    if (fieldFormat == MessageFormat.NIL) {
      unpacker.unpackNil()
      continue
    }

    when (fieldName) {
      "objectId" -> objectId = unpacker.unpackString()
      "siteTimeserials" -> {
        val mapSize = unpacker.unpackMapHeader()
        val tempMap = mutableMapOf<String, String>()
        for (j in 0 until mapSize) {
          val key = unpacker.unpackString()
          val value = unpacker.unpackString()
          tempMap[key] = value
        }
        siteTimeserials = tempMap
      }
      "tombstone" -> tombstone = unpacker.unpackBoolean()
      "createOp" -> createOp = readObjectOperation(unpacker)
      "map" -> map = readObjectMap(unpacker)
      "counter" -> counter = readObjectCounter(unpacker)
      else -> unpacker.skipValue()
    }
  }

  if (objectId.isEmpty()) {
    throw objectStateError("Missing required 'objectId' field in WireObjectState")
  }

  return WireObjectState(
    objectId = objectId,
    siteTimeserials = siteTimeserials,
    tombstone = tombstone,
    createOp = createOp,
    map = map,
    counter = counter
  )
}

/**
 * Write WireMapCreate to MessagePacker
 */
private fun WireMapCreate.writeMsgpack(packer: MessagePacker) {
  packer.packMapHeader(2)
  packer.packString("semantics")
  packer.packInt(semantics.code)
  packer.packString("entries")
  packer.packMapHeader(entries.size)
  for ((key, value) in entries) {
    packer.packString(key)
    value.writeMsgpack(packer)
  }
}

/**
 * Read WireMapCreate from MessageUnpacker
 */
private fun readMapCreate(unpacker: MessageUnpacker): WireMapCreate {
  val fieldCount = unpacker.unpackMapHeader()
  var semantics: WireObjectsMapSemantics = WireObjectsMapSemantics.LWW
  var entries: Map<String, WireObjectsMapEntry> = emptyMap()

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat
    if (fieldFormat == MessageFormat.NIL) { unpacker.unpackNil(); continue }
    when (fieldName) {
      "semantics" -> {
        val code = unpacker.unpackInt()
        semantics = WireObjectsMapSemantics.entries.firstOrNull { it.code == code }
          ?: WireObjectsMapSemantics.entries.firstOrNull { it.code == -1 }
          ?: throw objectStateError("Unknown MapSemantics code: $code and no UNKNOWN fallback found")
      }
      "entries" -> {
        val mapSize = unpacker.unpackMapHeader()
        val tempMap = mutableMapOf<String, WireObjectsMapEntry>()
        for (j in 0 until mapSize) {
          tempMap[unpacker.unpackString()] = readObjectMapEntry(unpacker)
        }
        entries = tempMap
      }
      else -> unpacker.skipValue()
    }
  }
  return WireMapCreate(semantics = semantics, entries = entries)
}

/**
 * Write WireMapSet to MessagePacker
 */
private fun WireMapSet.writeMsgpack(packer: MessagePacker) {
  packer.packMapHeader(2)
  packer.packString("key")
  packer.packString(key)
  packer.packString("value")
  value.writeMsgpack(packer)
}

/**
 * Read WireMapSet from MessageUnpacker
 */
private fun readMapSet(unpacker: MessageUnpacker): WireMapSet {
  val fieldCount = unpacker.unpackMapHeader()
  var key: String? = null
  var value: WireObjectData? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat
    if (fieldFormat == MessageFormat.NIL) { unpacker.unpackNil(); continue }
    when (fieldName) {
      "key" -> key = unpacker.unpackString()
      "value" -> value = readObjectData(unpacker)
      else -> unpacker.skipValue()
    }
  }
  return WireMapSet(
    key = key ?: throw objectStateError("Missing 'key' in WireMapSet payload"),
    value = value ?: throw objectStateError("Missing 'value' in WireMapSet payload")
  )
}

/**
 * Write WireMapRemove to MessagePacker
 */
private fun WireMapRemove.writeMsgpack(packer: MessagePacker) {
  packer.packMapHeader(1)
  packer.packString("key")
  packer.packString(key)
}

/**
 * Read WireMapRemove from MessageUnpacker
 */
private fun readMapRemove(unpacker: MessageUnpacker): WireMapRemove {
  val fieldCount = unpacker.unpackMapHeader()
  var key: String? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat
    if (fieldFormat == MessageFormat.NIL) { unpacker.unpackNil(); continue }
    when (fieldName) {
      "key" -> key = unpacker.unpackString()
      else -> unpacker.skipValue()
    }
  }
  return WireMapRemove(key = key ?: throw objectStateError("Missing 'key' in WireMapRemove payload"))
}

/**
 * Write WireCounterCreate to MessagePacker
 */
private fun WireCounterCreate.writeMsgpack(packer: MessagePacker) {
  packer.packMapHeader(if (count != null) 1 else 0)
  if (count != null) {
    packer.packString("count")
    packer.packDouble(count)
  }
}

/**
 * Read WireCounterCreate from MessageUnpacker
 */
private fun readCounterCreate(unpacker: MessageUnpacker): WireCounterCreate {
  val fieldCount = unpacker.unpackMapHeader()
  var count: Double? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat
    if (fieldFormat == MessageFormat.NIL) { unpacker.unpackNil(); continue }
    when (fieldName) {
      "count" -> count = unpacker.unpackDouble()
      else -> unpacker.skipValue()
    }
  }
  // count may legitimately be absent (RTLC16d) - the apply path treats it as a noop
  return WireCounterCreate(count = count)
}

/**
 * Write WireCounterInc to MessagePacker
 */
private fun WireCounterInc.writeMsgpack(packer: MessagePacker) {
  packer.packMapHeader(if (number != null) 1 else 0)
  if (number != null) {
    packer.packString("number")
    packer.packDouble(number)
  }
}

/**
 * Read WireCounterInc from MessageUnpacker
 */
private fun readCounterInc(unpacker: MessageUnpacker): WireCounterInc {
  val fieldCount = unpacker.unpackMapHeader()
  var number: Double? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat
    if (fieldFormat == MessageFormat.NIL) { unpacker.unpackNil(); continue }
    when (fieldName) {
      "number" -> number = unpacker.unpackDouble()
      else -> unpacker.skipValue()
    }
  }
  // number may legitimately be absent (RTLC9h) - the apply path treats it as a noop
  return WireCounterInc(number = number)
}

/**
 * Write WireMapCreateWithObjectId to MessagePacker
 */
private fun WireMapCreateWithObjectId.writeMsgpack(packer: MessagePacker) {
  packer.packMapHeader(2)
  packer.packString("initialValue")
  packer.packString(initialValue)
  packer.packString("nonce")
  packer.packString(nonce)
}

/**
 * Read WireMapCreateWithObjectId from MessageUnpacker
 */
private fun readMapCreateWithObjectId(unpacker: MessageUnpacker): WireMapCreateWithObjectId {
  val fieldCount = unpacker.unpackMapHeader()
  var initialValue: String? = null
  var nonce: String? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat
    if (fieldFormat == MessageFormat.NIL) { unpacker.unpackNil(); continue }
    when (fieldName) {
      "initialValue" -> initialValue = unpacker.unpackString()
      "nonce" -> nonce = unpacker.unpackString()
      else -> unpacker.skipValue()
    }
  }
  return WireMapCreateWithObjectId(
    initialValue = initialValue ?: throw objectStateError("Missing 'initialValue' in WireMapCreateWithObjectId payload"),
    nonce = nonce ?: throw objectStateError("Missing 'nonce' in WireMapCreateWithObjectId payload")
  )
}

/**
 * Write WireCounterCreateWithObjectId to MessagePacker
 */
private fun WireCounterCreateWithObjectId.writeMsgpack(packer: MessagePacker) {
  packer.packMapHeader(2)
  packer.packString("initialValue")
  packer.packString(initialValue)
  packer.packString("nonce")
  packer.packString(nonce)
}

/**
 * Read WireCounterCreateWithObjectId from MessageUnpacker
 */
private fun readCounterCreateWithObjectId(unpacker: MessageUnpacker): WireCounterCreateWithObjectId {
  val fieldCount = unpacker.unpackMapHeader()
  var initialValue: String? = null
  var nonce: String? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat
    if (fieldFormat == MessageFormat.NIL) { unpacker.unpackNil(); continue }
    when (fieldName) {
      "initialValue" -> initialValue = unpacker.unpackString()
      "nonce" -> nonce = unpacker.unpackString()
      else -> unpacker.skipValue()
    }
  }
  return WireCounterCreateWithObjectId(
    initialValue = initialValue ?: throw objectStateError("Missing 'initialValue' in WireCounterCreateWithObjectId payload"),
    nonce = nonce ?: throw objectStateError("Missing 'nonce' in WireCounterCreateWithObjectId payload")
  )
}

/**
 * Write ObjectMap to MessagePacker
 */
private fun WireObjectsMap.writeMsgpack(packer: MessagePacker) {
  var fieldCount = 0

  if (semantics != null) fieldCount++
  if (entries != null) fieldCount++
  if (clearTimeserial != null) fieldCount++

  packer.packMapHeader(fieldCount)

  if (semantics != null) {
    packer.packString("semantics")
    packer.packInt(semantics.code)
  }

  if (entries != null) {
    packer.packString("entries")
    packer.packMapHeader(entries.size)
    for ((key, value) in entries) {
      packer.packString(key)
      value.writeMsgpack(packer)
    }
  }

  if (clearTimeserial != null) {
    packer.packString("clearTimeserial")
    packer.packString(clearTimeserial)
  }
}

/**
 * Read ObjectMap from MessageUnpacker
 */
private fun readObjectMap(unpacker: MessageUnpacker): WireObjectsMap {
  val fieldCount = unpacker.unpackMapHeader()

  var semantics: WireObjectsMapSemantics? = null
  var entries: Map<String, WireObjectsMapEntry>? = null
  var clearTimeserial: String? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat

    if (fieldFormat == MessageFormat.NIL) {
      unpacker.unpackNil()
      continue
    }

    when (fieldName) {
      "semantics" -> {
        val semanticsCode = unpacker.unpackInt()
        semantics = WireObjectsMapSemantics.entries.firstOrNull { it.code == semanticsCode }
          ?: WireObjectsMapSemantics.entries.firstOrNull { it.code == -1 }
          ?: throw objectStateError("Unknown MapSemantics code: $semanticsCode and no UNKNOWN fallback found")
      }
      "entries" -> {
        val mapSize = unpacker.unpackMapHeader()
        val tempMap = mutableMapOf<String, WireObjectsMapEntry>()
        for (j in 0 until mapSize) {
          val key = unpacker.unpackString()
          val value = readObjectMapEntry(unpacker)
          tempMap[key] = value
        }
        entries = tempMap
      }
      "clearTimeserial" -> clearTimeserial = unpacker.unpackString()
      else -> unpacker.skipValue()
    }
  }

  return WireObjectsMap(semantics = semantics, entries = entries, clearTimeserial = clearTimeserial)
}

/**
 * Write ObjectCounter to MessagePacker
 */
private fun WireObjectsCounter.writeMsgpack(packer: MessagePacker) {
  var fieldCount = 0

  if (count != null) fieldCount++

  packer.packMapHeader(fieldCount)

  if (count != null) {
    packer.packString("count")
    packer.packDouble(count)
  }
}

/**
 * Read ObjectCounter from MessageUnpacker
 */
private fun readObjectCounter(unpacker: MessageUnpacker): WireObjectsCounter {
  val fieldCount = unpacker.unpackMapHeader()

  var count: Double? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat

    if (fieldFormat == MessageFormat.NIL) {
      unpacker.unpackNil()
      continue
    }

    when (fieldName) {
      "count" -> count = unpacker.unpackDouble()
      else -> unpacker.skipValue()
    }
  }

  return WireObjectsCounter(count = count)
}

/**
 * Write ObjectMapEntry to MessagePacker
 */
private fun WireObjectsMapEntry.writeMsgpack(packer: MessagePacker) {
  var fieldCount = 0

  if (tombstone != null) fieldCount++
  if (timeserial != null) fieldCount++
  if (serialTimestamp != null) fieldCount++
  if (data != null) fieldCount++

  packer.packMapHeader(fieldCount)

  if (tombstone != null) {
    packer.packString("tombstone")
    packer.packBoolean(tombstone)
  }

  if (timeserial != null) {
    packer.packString("timeserial")
    packer.packString(timeserial)
  }

  if (serialTimestamp != null) {
    packer.packString("serialTimestamp")
    packer.packLong(serialTimestamp)
  }

  if (data != null) {
    packer.packString("data")
    data.writeMsgpack(packer)
  }
}

/**
 * Read ObjectMapEntry from MessageUnpacker
 */
private fun readObjectMapEntry(unpacker: MessageUnpacker): WireObjectsMapEntry {
  val fieldCount = unpacker.unpackMapHeader()

  var tombstone: Boolean? = null
  var timeserial: String? = null
  var serialTimestamp: Long? = null
  var data: WireObjectData? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat

    if (fieldFormat == MessageFormat.NIL) {
      unpacker.unpackNil()
      continue
    }

    when (fieldName) {
      "tombstone" -> tombstone = unpacker.unpackBoolean()
      "timeserial" -> timeserial = unpacker.unpackString()
      "serialTimestamp" -> serialTimestamp = unpacker.unpackLong()
      "data" -> data = readObjectData(unpacker)
      else -> unpacker.skipValue()
    }
  }

  return WireObjectsMapEntry(tombstone = tombstone, timeserial = timeserial, serialTimestamp = serialTimestamp, data = data)
}

/**
 * Write WireObjectData to MessagePacker
 */
private fun WireObjectData.writeMsgpack(packer: MessagePacker) {
  var fieldCount = 0

  if (objectId != null) fieldCount++
  if (string != null) fieldCount++
  if (number != null) fieldCount++
  if (boolean != null) fieldCount++
  if (bytes != null) fieldCount++
  if (json != null) fieldCount++

  packer.packMapHeader(fieldCount)

  if (objectId != null) {
    packer.packString("objectId")
    packer.packString(objectId)
  }

  if (string != null) {
    packer.packString("string")
    packer.packString(string)
  }

  if (number != null) {
    packer.packString("number")
    packer.packDouble(number)
  }

  if (boolean != null) {
    packer.packString("boolean")
    packer.packBoolean(boolean)
  }

  if (bytes != null) {
    val rawBytes = Base64.getDecoder().decode(bytes)
    packer.packString("bytes")
    packer.packBinaryHeader(rawBytes.size)
    packer.writePayload(rawBytes)
  }

  if (json != null) {
    packer.packString("json")
    packer.packString(json.toString())
  }
}

/**
 * Read WireObjectData from MessageUnpacker
 */
private fun readObjectData(unpacker: MessageUnpacker): WireObjectData {
  val fieldCount = unpacker.unpackMapHeader()
  var objectId: String? = null
  var string: String? = null
  var number: Double? = null
  var boolean: Boolean? = null
  var bytes: String? = null
  var json: JsonElement? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat

    if (fieldFormat == MessageFormat.NIL) {
      unpacker.unpackNil()
      continue
    }

    when (fieldName) {
      "objectId" -> objectId = unpacker.unpackString()
      "string" -> string = unpacker.unpackString()
      "number" -> number = unpacker.unpackDouble()
      "boolean" -> boolean = unpacker.unpackBoolean()
      "bytes" -> {
        val size = unpacker.unpackBinaryHeader()
        val rawBytes = ByteArray(size)
        unpacker.readPayload(rawBytes)
        bytes = Base64.getEncoder().encodeToString(rawBytes)
      }
      "json" -> json = JsonParser.parseString(unpacker.unpackString())
      else -> unpacker.skipValue()
    }
  }

  return WireObjectData(objectId = objectId, string = string, number = number, boolean = boolean, bytes = bytes, json = json)
}
