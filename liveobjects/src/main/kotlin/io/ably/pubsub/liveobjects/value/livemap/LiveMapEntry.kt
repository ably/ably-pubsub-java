package io.ably.pubsub.liveobjects.value.livemap

import io.ably.pubsub.liveobjects.ObjectsPool
import io.ably.pubsub.liveobjects.message.WireObjectData
import io.ably.pubsub.liveobjects.message.isInvalid
import io.ably.pubsub.liveobjects.value.ObjectType
import io.ably.pubsub.liveobjects.value.ResolvedValue
import io.ably.pubsub.liveobjects.value.livecounter.InternalLiveCounter
import io.ably.pubsub.util.Clock

/**
 * @spec RTLM3 - Map data structure storing entries
 */
internal data class LiveMapEntry(
  val isTombstoned: Boolean = false,
  val tombstonedAt: Long? = null,
  val timeserial: String? = null,
  val data: WireObjectData? = null
)

/**
 * Checks if entry is directly tombstoned or references a tombstoned object. Spec: RTLM14
 * @param objectsPool The object pool containing referenced DefaultRealtimeObjects
 */
internal fun LiveMapEntry.isEntryOrRefTombstoned(objectsPool: ObjectsPool): Boolean {
  if (isTombstoned) {
    return true // RTLM14a
  }
  data?.objectId?.let { refId -> // RTLM5d2f -has an objectId reference
    objectsPool.get(refId)?.let { refObject ->
      if (refObject.isTombstoned) {
        return true
      }
    }
  }
  return false // RTLM14b
}

/**
 * Resolves this entry to the internal graph view: a primitive leaf as wire ObjectData,
 * or a reference to another internal object from the pool.
 * Spec: RTLM5d2
 */
internal fun LiveMapEntry.getResolvedValue(objectsPool: ObjectsPool): ResolvedValue? {
  if (isTombstoned) { return null } // RTLM5d2h
  val d = data ?: return null // RTLM5d2g
  d.objectId?.let { refId -> // RTLM5d2f - has an objectId reference
    val refObject = objectsPool.get(refId) ?: return null // RTLM5d2f1
    if (refObject.isTombstoned) {
      return null // tombstoned objects must not be surfaced to the end users (RTLM14c behaviour)
    }
    // RTLM5d2f2 - safe casts by construction: the pool only ever contains these two subclasses
    return when (refObject.objectType) {
      ObjectType.Map -> ResolvedValue.MapRef(refObject as InternalLiveMap)
      ObjectType.Counter -> ResolvedValue.CounterRef(refObject as InternalLiveCounter)
    }
  }
  // RTLM5d2b..e - primitive leaf; keep the wire form, typed narrowing (incl. base64 decode
  // for bytes) happens at the PathObject/Instance layer
  if (d.isInvalid()) return null // RTLM5d2g
  return ResolvedValue.Leaf(d)
}

/**
 * Extension function to check if a LiveMapEntry is expired and ready for garbage collection
 */
internal fun LiveMapEntry.isEligibleForGc(gcGracePeriod: Long, clock: Clock): Boolean {
  val currentTime = clock.currentTimeMillis()
  return isTombstoned && tombstonedAt?.let { currentTime - it >= gcGracePeriod } == true
}
