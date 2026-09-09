package io.ably.lib.liveobjects.value.livemap

import io.ably.lib.liveobjects.*
import io.ably.lib.liveobjects.ObjectsPool
import io.ably.lib.liveobjects.instance.DefaultInstanceSubscriptionEvent
import io.ably.lib.liveobjects.instance.InstanceListener
import io.ably.lib.liveobjects.instance.types.DefaultLiveMapInstance
import io.ably.lib.liveobjects.message.*
import io.ably.lib.liveobjects.value.*
import io.ably.lib.liveobjects.value.BaseRealtimeObject
import io.ably.lib.liveobjects.value.ObjectType
import io.ably.lib.liveobjects.value.ObjectUpdate
import io.ably.lib.liveobjects.value.livecounter.DefaultLiveCounter
import com.google.gson.JsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.AbstractMap

/**
 * @spec RTLM1/RTLM2 - LiveMap implementation extends BaseRealtimeObject
 */
internal class InternalLiveMap private constructor(
  objectId: String,
  realtimeObject: DefaultRealtimeObject,
  internal val semantics: WireObjectsMapSemantics = WireObjectsMapSemantics.LWW
) : BaseRealtimeObject(objectId, ObjectType.Map, realtimeObject) {

  override val tag = "LiveMap"

  /**
   * ConcurrentHashMap for thread-safe access from public APIs in LiveMap and LiveMapManager.
   */
  internal val data = ConcurrentHashMap<String, LiveMapEntry>()

  /** @spec RTLM25 */
  internal var clearTimeserial: String? = null

  /**
   * LiveMapManager instance for managing LiveMap operations
   */
  private val liveMapManager = LiveMapManager(this)

  internal val objectsPool: ObjectsPool get() = realtimeObject.objectsPool

  /** Spec: RTLM5 */
  internal fun get(keyName: String): ResolvedValue? {
    if (isTombstoned) {
      return null // RTLM5e
    }
    return data[keyName]?.getResolvedValue(objectsPool) // RTLM5d1, RTLM5d2
  }

  /**
   * Non-tombstoned entries (RTLM11d1). A non-tombstoned entry whose objectId reference does not
   * resolve is still yielded with a null value (RTLM11d3a).
   */
  internal fun entries(): Iterable<Map.Entry<String, ResolvedValue?>> {
    return sequence<Map.Entry<String, ResolvedValue?>> {
      for ((key, entry) in data.entries) {
        if (entry.isEntryOrRefTombstoned(objectsPool)) continue // RTLM11d1
        yield(AbstractMap.SimpleImmutableEntry(key, entry.getResolvedValue(objectsPool))) // RTLM11d3, RTLM11d3a
      }
    }.asIterable()
  }

  internal fun keys(): Iterable<String> {
    val iterableEntries = entries()
    return sequence {
      for (entry in iterableEntries) {
        yield(entry.key) // RTLM12b
      }
    }.asIterable()
  }

  internal fun values(): Iterable<ResolvedValue?> {
    val iterableEntries = entries()
    return sequence {
      for (entry in iterableEntries) {
        yield(entry.value) // RTLM13b
      }
    }.asIterable()
  }

  internal fun size(): Long {
    return data.values.count { !it.isEntryOrRefTombstoned(objectsPool) }.toLong() // RTLM10d
  }

  /**
   * Recursively compacted JSON snapshot. Cycles are emitted as {"objectId": <id>} markers
   * (RTPO14b2); binary leaves stay base64 strings - already the wire form (RTPO14b1).
   * Visited ids are added before iterating and never removed (ably-js parity: a map referenced
   * twice on sibling branches yields the marker for the second sibling too).
   *
   * Spec: RTPO13c (structure), RTPO14b (JSON differences)
   */
  internal fun compactJson(visited: MutableSet<String> = mutableSetOf()): JsonObject {
    val result = JsonObject()
    visited.add(objectId)
    for ((key, resolved) in entries()) { // RTPO13c1 - tombstoned entries excluded via entries()
      when (resolved) {
        is ResolvedValue.MapRef ->
          if (resolved.map.objectId in visited) {
            result.add(key, JsonObject().apply { addProperty("objectId", resolved.map.objectId) }) // RTPO14b2
          } else {
            result.add(key, resolved.map.compactJson(visited)) // RTPO13c2
          }
        is ResolvedValue.CounterRef -> result.addProperty(key, resolved.counter.value()) // RTPO13c3
        is ResolvedValue.Leaf -> result.add(key, resolved.data.toCompactJsonElement()) // RTPO13c4, RTPO14b1
        null -> {
          // dangling reference (RTLM11d3a) - omitted from the snapshot, matching the ably-js
          // observable result (undefined-valued keys are dropped by JSON serialisation)
        }
      }
    }
    return result
  }

  internal suspend fun set(keyName: String, value: LiveMapValue) =  setAsync(keyName, value)

  internal suspend fun remove(keyName: String) = removeAsync(keyName)

  override fun validate(state: WireObjectState) = liveMapManager.validate(state)

  /** Identity-based subscription to this map's updates. Spec: RTINS16d, RTLO4b */
  internal fun subscribe(listener: InstanceListener): Subscription {
    return liveMapManager.subscribe(listener)
  }

  private suspend fun setAsync(keyName: String, value: LiveMapValue) {
    // RTLM20e1 / RTLMV4b - validate input parameters
    if (keyName.isEmpty()) {
      throw invalidInputError("Map key should not be empty")
    }

    // RTLM20e7 - the wire value; LiveMap/LiveCounter value types are evaluated into their
    // *_CREATE messages first (RTLM20e7g1) and referenced by objectId (RTLM20e7g2)
    val createMessages: List<WireObjectMessage>
    val objectData: WireObjectData
    when {
      value.isLiveCounter -> { // RTLM20e7g
        val msg = (value.asLiveCounter as DefaultLiveCounter).createCounterCreateMessage(realtimeObject) // RTLCV4
        createMessages = listOf(msg)
        objectData = WireObjectData(objectId = msg.operation!!.objectId) // RTLM20e7g2
      }
      value.isLiveMap -> { // RTLM20e7g
        val msgs = (value.asLiveMap as DefaultLiveMap).createMapCreateMessages(realtimeObject) // RTLMV4
        createMessages = msgs
        objectData = WireObjectData(objectId = msgs.last().operation!!.objectId) // RTLM20e7g2
      }
      else -> {
        createMessages = emptyList()
        objectData = fromPrimitiveLiveMapValue(value) // RTLM20e7b..f
      }
    }

    // RTLM20e - Create ObjectMessage with the MAP_SET operation
    val mapSetMsg = WireObjectMessage(
      operation = WireObjectOperation(
        action = WireObjectOperationAction.MapSet, // RTLM20e2
        objectId = objectId, // RTLM20e3
        mapSet = WireMapSet(
          key = keyName, // RTLM20e6
          value = objectData // RTLM20e7
        )
      )
    )

    // RTLM20h - publish (nested creates first, RTLM20h1; single message otherwise, RTLM20h2)
    // and apply locally on ACK
    realtimeObject.publishAndApply((createMessages + mapSetMsg).toTypedArray())
  }

  private suspend fun removeAsync(keyName: String) {
    // Validate input parameter
    if (keyName.isEmpty()) {
      throw invalidInputError("Map key should not be empty")
    }

    // RTLM21e - Create ObjectMessage with the MAP_REMOVE operation
    val msg = WireObjectMessage(
      operation = WireObjectOperation(
        action = WireObjectOperationAction.MapRemove,
        objectId = objectId,
        mapRemove = WireMapRemove(key = keyName)
      )
    )

    // RTLM21g - publish and apply locally on ACK
    realtimeObject.publishAndApply(arrayOf(msg))
  }

  override fun applyObjectState(wireObjectState: WireObjectState, message: WireObjectMessage): ObjectUpdate {
    return liveMapManager.applyState(wireObjectState, message)
  }

  override fun applyObjectOperation(operation: WireObjectOperation, message: WireObjectMessage): ObjectUpdate {
    return liveMapManager.applyOperation(operation, message)
  }

  override fun clearData(): ObjectUpdate {
    // RTLO4e9 - before the data is reset, drop the parent references this map holds on objects
    // referenced by its entries. Covers RTO4b resets, detached/failed clears and object
    // tombstoning in one place (ably-js does the same in LiveMap.clearData).
    for ((key, entry) in data) {
      val refId = entry.data?.objectId ?: continue
      objectsPool.get(refId)?.removeParentReference(this, key) // RTLO4e9a, RTLO4e9b
    }
    clearTimeserial = null  // RTLM4d
    return liveMapManager.calculateUpdateFromDataDiff(data.toMap(), emptyMap())
      .apply { this@InternalLiveMap.data.clear() }
  }

  override fun notifyInstanceSubscriptions(update: ObjectUpdate, message: ObjectMessage?) {
    // RTINS16e1, RTINS16e2 - the event wraps a fresh instance bound to this map (the spec
    // requires "an Instance wrapping the underlying LiveObject", not a specific wrapper identity)
    liveMapManager.notify(
      DefaultInstanceSubscriptionEvent(DefaultLiveMapInstance(realtimeObject, this), message)
    )
  }

  override fun deregisterInstanceListeners() = liveMapManager.offAll() // RTLO4b4c3c

  override fun onGCInterval(gcGracePeriod: Long) {
    data.entries.removeIf { (_, entry) -> entry.isEligibleForGc(gcGracePeriod, clock) }
  }

  companion object {
    /**
     * Creates a zero-value map object.
     * @spec RTLM4 - Returns LiveMap with empty map data
     */
    internal fun zeroValue(objectId: String, objects: DefaultRealtimeObject): InternalLiveMap {
      return InternalLiveMap(objectId, objects)
    }
  }
}
