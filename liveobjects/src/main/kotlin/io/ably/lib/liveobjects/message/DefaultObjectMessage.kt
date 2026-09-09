package io.ably.lib.liveobjects.message

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.ably.lib.liveobjects.objectStateError
import java.util.*

/**
 * Builds the user-facing PublicAPI::ObjectMessage from an inbound wire
 * ObjectMessage that carried an operation. Mirrors ably-js
 * `objectmessage.ts#toUserFacingMessage`.
 *
 * Precondition (PAOM3a1): the source message has its `operation` populated.
 *
 * Spec: PAOM3
 */
internal fun WireObjectMessage.toPublicMessage(channelName: String): ObjectMessage =
  DefaultObjectMessage(this, channelName)

/**
 * PublicAPI::ObjectMessage implementation - a read-only view over the source
 * wire message. Spec: PAOM1, PAOM2
 */
internal class DefaultObjectMessage(
  private val message: WireObjectMessage,
  private val channelName: String,
) : ObjectMessage {

  private val operation: ObjectOperation = DefaultObjectOperation(
    message.operation ?: throw objectStateError("Cannot build public ObjectMessage without an operation") // PAOM3a1
  )

  override fun getId(): String? = message.id // PAOM2a
  override fun getClientId(): String? = message.clientId // PAOM2b
  override fun getConnectionId(): String? = message.connectionId // PAOM2c
  override fun getTimestamp(): Long? = message.timestamp // PAOM2d
  override fun getChannel(): String = channelName // PAOM2e, PAOM3b
  override fun getOperation(): ObjectOperation = operation // PAOM2f
  override fun getSerial(): String? = message.serial // PAOM2g
  override fun getSerialTimestamp(): Long? = message.serialTimestamp // PAOM2h
  override fun getSiteCode(): String? = message.siteCode // PAOM2i
  override fun getExtras(): JsonObject? = message.extras // PAOM2j
}

/**
 * PublicAPI::ObjectOperation implementation. Resolves the outbound-only
 * `*CreateWithObjectId` variants back to their derived MapCreate/CounterCreate
 * forms. Spec: PAOOP1, PAOOP2, PAOOP3
 */
internal class DefaultObjectOperation(private val operation: WireObjectOperation) : ObjectOperation {

  override fun getAction(): ObjectOperationAction = operation.action.toPublic() // PAOOP2a

  override fun getObjectId(): String = operation.objectId // PAOOP2b

  // PAOOP3b - prefer mapCreate, else the MapCreate the WithObjectId variant was derived from
  override fun getMapCreate(): MapCreate? =
    (operation.mapCreate ?: operation.mapCreateWithObjectId?.derivedFrom)?.let { DefaultMapCreate(it) }

  override fun getMapSet(): MapSet? = operation.mapSet?.let { DefaultMapSet(it) } // PAOOP2d

  override fun getMapRemove(): MapRemove? = operation.mapRemove?.let { DefaultMapRemove(it) } // PAOOP2e

  // PAOOP3c - prefer counterCreate, else the derived CounterCreate
  override fun getCounterCreate(): CounterCreate? =
    (operation.counterCreate ?: operation.counterCreateWithObjectId?.derivedFrom)?.let { DefaultCounterCreate(it) }

  override fun getCounterInc(): CounterInc? = operation.counterInc?.let { DefaultCounterInc(it) } // PAOOP2g

  override fun getObjectDelete(): ObjectDelete? = operation.objectDelete?.let { DefaultObjectDelete } // PAOOP2h

  override fun getMapClear(): MapClear? = operation.mapClear?.let { DefaultMapClear } // PAOOP2i
}

/** Spec: MCR2 */
internal class DefaultMapCreate(private val mapCreate: WireMapCreate) : MapCreate {
  override fun getSemantics(): ObjectsMapSemantics = mapCreate.semantics.toPublic()
  override fun getEntries(): Map<String, ObjectsMapEntry> =
    Collections.unmodifiableMap(mapCreate.entries.mapValues { (_, entry) -> DefaultObjectsMapEntry(entry) })
}

/** Spec: MST2 */
internal class DefaultMapSet(private val mapSet: WireMapSet) : MapSet {
  override fun getKey(): String = mapSet.key
  override fun getValue(): ObjectData = DefaultObjectData(mapSet.value)
}

/** Spec: MRM2 */
internal class DefaultMapRemove(private val mapRemove: WireMapRemove) : MapRemove {
  override fun getKey(): String = mapRemove.key
}

/** Spec: CCR2 */
internal class DefaultCounterCreate(private val counterCreate: WireCounterCreate) : CounterCreate {
  override fun getCount(): Double? = counterCreate.count
}

/** Spec: CIN2 */
internal class DefaultCounterInc(private val counterInc: WireCounterInc) : CounterInc {
  override fun getNumber(): Double? = counterInc.number
}

/** Spec: ODE2 - no attributes */
internal object DefaultObjectDelete : ObjectDelete

/** Spec: MCL2 - no attributes */
internal object DefaultMapClear : MapClear

/** Spec: OME2 */
internal class DefaultObjectsMapEntry(private val entry: WireObjectsMapEntry) : ObjectsMapEntry {
  override fun getTombstone(): Boolean? = entry.tombstone
  override fun getTimeserial(): String? = entry.timeserial
  override fun getSerialTimestamp(): Long? = entry.serialTimestamp
  override fun getData(): ObjectData? = entry.data?.let { DefaultObjectData(it) }
}

/**
 * Decoded public ObjectData: binary is delivered decoded (the wire form is
 * base64); there is no `encoding` field in the public shape. Spec: OD2
 */
internal class DefaultObjectData(private val data: WireObjectData) : ObjectData {
  override fun getObjectId(): String? = data.objectId
  override fun getString(): String? = data.string
  override fun getNumber(): Double? = data.number
  override fun getBoolean(): Boolean? = data.boolean
  override fun getBytes(): ByteArray? = data.bytes?.let { Base64.getDecoder().decode(it) }
  override fun getJson(): JsonElement? = data.json
}

/** Internal action -> public enum; unrecognized wire values map to UNKNOWN. Spec: PAOOP2a, OOP2 */
internal fun WireObjectOperationAction.toPublic(): ObjectOperationAction = when (this) {
  WireObjectOperationAction.MapCreate -> ObjectOperationAction.MAP_CREATE
  WireObjectOperationAction.MapSet -> ObjectOperationAction.MAP_SET
  WireObjectOperationAction.MapRemove -> ObjectOperationAction.MAP_REMOVE
  WireObjectOperationAction.CounterCreate -> ObjectOperationAction.COUNTER_CREATE
  WireObjectOperationAction.CounterInc -> ObjectOperationAction.COUNTER_INC
  WireObjectOperationAction.ObjectDelete -> ObjectOperationAction.OBJECT_DELETE
  WireObjectOperationAction.MapClear -> ObjectOperationAction.MAP_CLEAR
  WireObjectOperationAction.Unknown -> ObjectOperationAction.UNKNOWN
}

/** Internal semantics -> public enum. Spec: OMP2 */
internal fun WireObjectsMapSemantics.toPublic(): ObjectsMapSemantics = when (this) {
  WireObjectsMapSemantics.LWW -> ObjectsMapSemantics.LWW
  WireObjectsMapSemantics.Unknown -> ObjectsMapSemantics.UNKNOWN
}
