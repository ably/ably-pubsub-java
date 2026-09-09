package io.ably.lib.liveobjects.message

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import io.ably.lib.liveobjects.byteSize
import io.ably.lib.liveobjects.serialization.WireObjectDataJsonSerializer
import io.ably.lib.liveobjects.serialization.gson
import java.util.Base64

/**
 * Wire-level object model for the path-based public API implementation.
 *
 * Copied from the legacy internal model (`io.ably.lib.objects.ObjectMessage`)
 * so that this package has no dependency on `io.ably.lib.objects`. The `Wire`
 * prefix distinguishes these internal carriers from the public interfaces in
 * `io.ably.lib.object.message`.
 *
 * Spec: OM*, OOP*, OD*, MCR*, MST*, MRM*, CCR*, CIN*, ODE*, MCL*, OME*, MCRO*, CCRO*, OMP*, OCN*, OST*
 */

/** Spec: OOP2 */
internal enum class WireObjectOperationAction(val code: Int) {
  MapCreate(0),
  MapSet(1),
  MapRemove(2),
  CounterCreate(3),
  CounterInc(4),
  ObjectDelete(5),
  MapClear(6),
  Unknown(-1); // code for unknown value during deserialization
}

/** Spec: OMP2 */
internal enum class WireObjectsMapSemantics(val code: Int) {
  LWW(0),
  Unknown(-1); // code for unknown value during deserialization
}

/** Spec: OD1, OD2 - binary carried as base64 string on the wire */
@JsonAdapter(WireObjectDataJsonSerializer::class)
internal data class WireObjectData(
  val objectId: String? = null, // OD2a
  val string: String? = null, // OD2f
  val number: Double? = null, // OD2e
  val boolean: Boolean? = null, // OD2c
  val bytes: String? = null, // OD2d - base64
  val json: JsonElement? = null, // decoded JSON leaf
)

/** Spec: MCR2 */
internal data class WireMapCreate(
  val semantics: WireObjectsMapSemantics, // MCR2a
  val entries: Map<String, WireObjectsMapEntry>, // MCR2b
)

/** Spec: MST2 */
internal data class WireMapSet(
  val key: String, // MST2a
  val value: WireObjectData, // MST2b
)

/** Spec: MRM2 */
internal data class WireMapRemove(
  val key: String, // MRM2a
)

/** Spec: CCR2 */
internal data class WireCounterCreate(
  val count: Double?, // CCR2a - may be absent on the wire (RTLC16d)
)

/** Spec: CIN2 */
internal data class WireCounterInc(
  val number: Double?, // CIN2a - may be absent on the wire (RTLC9h)
)

/** Spec: ODE2 - no attributes */
internal object WireObjectDelete

/** Spec: MCL2 - no attributes */
internal object WireMapClear

/** Spec: MCRO2 */
internal data class WireMapCreateWithObjectId(
  val initialValue: String, // MCRO2a
  val nonce: String, // MCRO2b
  @Transient val derivedFrom: WireMapCreate? = null, // RTLMV4j5 - local use only
)

/** Spec: CCRO2 */
internal data class WireCounterCreateWithObjectId(
  val initialValue: String, // CCRO2a
  val nonce: String, // CCRO2b
  @Transient val derivedFrom: WireCounterCreate? = null, // RTLCV4g5 - local use only
)

/** Spec: OME2 */
internal data class WireObjectsMapEntry(
  val tombstone: Boolean? = null, // OME2a
  val timeserial: String? = null, // OME2b
  val serialTimestamp: Long? = null, // OME2d
  val data: WireObjectData? = null, // OME2c
)

/** Spec: OMP1 */
internal data class WireObjectsMap(
  val semantics: WireObjectsMapSemantics? = null, // OMP3a
  val entries: Map<String, WireObjectsMapEntry>? = null, // OMP3b
  val clearTimeserial: String? = null, // OMP3c
)

/** Spec: OCN1 */
internal data class WireObjectsCounter(
  val count: Double? = null, // OCN2a
)

/** Spec: OOP3 */
internal data class WireObjectOperation(
  val action: WireObjectOperationAction, // OOP3a
  val objectId: String, // OOP3b
  val mapCreate: WireMapCreate? = null, // OOP3j
  val mapSet: WireMapSet? = null, // OOP3k
  val mapRemove: WireMapRemove? = null, // OOP3l
  val counterCreate: WireCounterCreate? = null, // OOP3m
  val counterInc: WireCounterInc? = null, // OOP3n
  val objectDelete: WireObjectDelete? = null, // OOP3o
  val mapCreateWithObjectId: WireMapCreateWithObjectId? = null, // OOP3p
  val counterCreateWithObjectId: WireCounterCreateWithObjectId? = null, // OOP3q
  val mapClear: WireMapClear? = null, // OOP3r
)

/** Spec: OST1 */
internal data class WireObjectState(
  val objectId: String, // OST2a
  val siteTimeserials: Map<String, String>, // OST2b
  val tombstone: Boolean, // OST2c
  val createOp: WireObjectOperation? = null, // OST2d
  val map: WireObjectsMap? = null, // OST2e
  val counter: WireObjectsCounter? = null, // OST2f
)

/** Spec: OM2 */
internal data class WireObjectMessage(
  val id: String? = null, // OM2a
  val timestamp: Long? = null, // OM2e
  val clientId: String? = null, // OM2b
  val connectionId: String? = null, // OM2c
  val extras: JsonObject? = null, // OM2d
  val operation: WireObjectOperation? = null, // OM2f
  @SerializedName("object")
  val objectState: WireObjectState? = null, // OM2g - wire key "object"
  val serial: String? = null, // OM2h
  val serialTimestamp: Long? = null, // OM2j
  val siteCode: String? = null, // OM2i
)

/**
 * Calculates the size of an ObjectMessage in bytes.
 * Spec: OM3
 */
internal fun WireObjectMessage.size(): Int {
  val clientIdSize = clientId?.byteSize ?: 0 // Spec: OM3f
  val operationSize = operation?.size() ?: 0 // Spec: OM3b, OOP4
  val objectStateSize = objectState?.size() ?: 0 // Spec: OM3c, OST3
  // Spec: OM3d - extras is measured as "the string length of its JSON representation" (docs
  // verbatim), i.e. the UTF-16 code-unit count of the JSON string, not its UTF-8 byte length.
  val extrasSize = extras?.let { gson.toJson(it).length } ?: 0

  return clientIdSize + operationSize + objectStateSize + extrasSize
}

/**
 * Calculates the size of an ObjectOperation in bytes.
 * Spec: OOP4
 */
private fun WireObjectOperation.size(): Int {
  val mapCreateSize = mapCreate?.size() ?: mapCreateWithObjectId?.derivedFrom?.size() ?: 0
  val mapSetSize = mapSet?.size() ?: 0
  val mapRemoveSize = mapRemove?.size() ?: 0
  val counterCreateSize = counterCreate?.size() ?: counterCreateWithObjectId?.derivedFrom?.size() ?: 0
  val counterIncSize = counterInc?.size() ?: 0

  return mapCreateSize + mapSetSize + mapRemoveSize +
    counterCreateSize + counterIncSize
}

/**
 * Calculates the size of an ObjectState in bytes.
 * Spec: OST3
 */
private fun WireObjectState.size(): Int {
  val mapSize = map?.size() ?: 0 // Spec: OST3b, OMP4
  val counterSize = counter?.size() ?: 0 // Spec: OST3c, OCN3
  val createOpSize = createOp?.size() ?: 0 // Spec: OST3d, OOP4

  return mapSize + counterSize + createOpSize
}

/**
 * Calculates the size of a MapCreate payload in bytes.
 */
private fun WireMapCreate.size(): Int {
  return entries.entries.sumOf { it.key.byteSize + it.value.size() }
}

/**
 * Calculates the size of a MapSet payload in bytes.
 */
private fun WireMapSet.size(): Int {
  return key.byteSize + value.size()
}

/**
 * Calculates the size of a MapRemove payload in bytes.
 */
private fun WireMapRemove.size(): Int {
  return key.byteSize
}

/**
 * Calculates the size of a CounterCreate payload in bytes.
 */
private fun WireCounterCreate.size(): Int {
  return 8 // Double is 8 bytes
}

/**
 * Calculates the size of a CounterInc payload in bytes.
 */
private fun WireCounterInc.size(): Int {
  return 8 // Double is 8 bytes
}

/**
 * Calculates the size of a MapCreateWithObjectId payload in bytes.
 */
private fun WireMapCreateWithObjectId.size(): Int {
  return initialValue.byteSize + nonce.byteSize
}

/**
 * Calculates the size of a CounterCreateWithObjectId payload in bytes.
 */
private fun WireCounterCreateWithObjectId.size(): Int {
  return initialValue.byteSize + nonce.byteSize
}

/**
 * Calculates the size of an ObjectMap in bytes.
 * Spec: OMP4
 */
private fun WireObjectsMap.size(): Int {
  // Calculate the size of all map entries in the map property
  val entriesSize = entries?.entries?.sumOf {
    it.key.byteSize + it.value.size() // Spec: OMP4a1, OMP4a2 (key measured as its UTF-8 byte length)
  } ?: 0

  return entriesSize
}

/**
 * Calculates the size of an ObjectCounter in bytes.
 * Spec: OCN3
 */
private fun WireObjectsCounter.size(): Int {
  // Size is 8 if count is a number, 0 if count is null or omitted
  return if (count != null) 8 else 0
}

/**
 * Calculates the size of a MapEntry in bytes.
 * Spec: OME3
 */
private fun WireObjectsMapEntry.size(): Int {
  // The size is equal to the size of the data property, calculated per "OD3"
  return data?.size() ?: 0
}

/**
 * Calculates the size of an ObjectData in bytes.
 * Spec: OD3
 */
private fun WireObjectData.size(): Int {
  string?.let { return it.byteSize } // Spec: OD3e
  number?.let { return 8 } // Spec: OD3d
  boolean?.let { return 1 } // Spec: OD3b
  bytes?.let { return Base64.getDecoder().decode(it).size } // Spec: OD3c
  json?.let { return it.toString().byteSize } // Spec: OD3g (UTF-8 byte length of JSON-encoded string)
  return 0
}

internal fun WireObjectData?.isInvalid(): Boolean {
  return this?.objectId.isNullOrEmpty() &&
    this?.string == null &&
    this?.number == null &&
    this?.boolean == null &&
    this?.bytes == null &&
    this?.json == null
}

/**
 * Compact-JSON form of a primitive wire leaf: primitives become JsonPrimitives, binary
 * stays base64-encoded (RTPO14b1), embedded JSON passes through.
 */
internal fun WireObjectData.toCompactJsonElement(): JsonElement = when {
  string != null -> JsonPrimitive(string)
  number != null -> JsonPrimitive(number)
  boolean != null -> JsonPrimitive(boolean)
  bytes != null -> JsonPrimitive(bytes) // base64 kept encoded - RTPO14b1
  json != null -> json!!
  else -> JsonNull.INSTANCE // unreachable for a valid Leaf (isInvalid-filtered)
}
