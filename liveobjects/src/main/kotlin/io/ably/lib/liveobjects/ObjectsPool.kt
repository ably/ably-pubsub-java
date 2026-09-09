package io.ably.lib.liveobjects

import io.ably.lib.liveobjects.value.BaseRealtimeObject
import io.ably.lib.liveobjects.value.ObjectType
import io.ably.lib.liveobjects.value.livecounter.InternalLiveCounter
import io.ably.lib.liveobjects.value.livemap.InternalLiveMap
import io.ably.lib.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Constants for ObjectsPool configuration
 */
internal object ObjectsPoolDefaults {
  const val GC_INTERVAL_MS = 1000L * 60 * 5 // 5 minutes
  /**
   * The SDK will attempt to use the `objectsGCGracePeriod` value provided by the server in the `connectionDetails`
   * object of the `CONNECTED` event.
   * If the server does not provide this value, the SDK will fall back to this default value.
   * Must be > 2 minutes to ensure we keep tombstones long enough to avoid the possibility of receiving an operation
   * with an earlier serial that would not have been applied if the tombstone still existed.
   *
   * Applies both for map entries tombstones and object tombstones.
   */
  const val GC_GRACE_PERIOD_MS = 1000L * 60 * 60 * 24 // 24 hours
}

/**
 * Root object ID constant
 */
internal const val ROOT_OBJECT_ID = "root"

/**
 * ObjectsPool manages a pool of objects for a channel.
 *
 * @spec RTO3 - Maintains an objects pool for all objects on the channel
 */
internal class ObjectsPool(
  private val realtimeObjects: DefaultRealtimeObject
) {
  private val tag = "ObjectsPool"

  /**
   * ConcurrentHashMap for thread-safe access from public APIs in LiveMap and LiveCounter.
   * @spec RTO3a - Pool storing all ably objects by object ID
   */
  private val pool = ConcurrentHashMap<String, BaseRealtimeObject>()

  /**
   * Coroutine scope for garbage collection
   */
  private val gcScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
  private var gcJob: Job // Job for the garbage collection coroutine

  @Volatile private var gcGracePeriod = ObjectsPoolDefaults.GC_GRACE_PERIOD_MS
  private var gcPeriodSubscription: Subscription

  init {
    // RTO3b - Initialize pool with root object
    pool[ROOT_OBJECT_ID] = InternalLiveMap.zeroValue(ROOT_OBJECT_ID, realtimeObjects)
    // Start garbage collection coroutine with server-provided grace period if available
    gcPeriodSubscription = realtimeObjects.adapter.onGCGracePeriodUpdated { period ->
      period?.let {
        gcGracePeriod = it
        Log.i(tag, "Using objectsGCGracePeriod from server: $gcGracePeriod ms")
      } ?: Log.i(tag, "Server did not provide objectsGCGracePeriod, using default: $gcGracePeriod ms")
    }
    gcJob = startGCJob()
  }

  /**
   * Gets an object from the pool by object ID.
   */
  internal fun get(objectId: String): BaseRealtimeObject? {
    return pool[objectId]
  }

  /**
   * Sets a realtime object in the pool.
   */
  internal fun set(objectId: String, realtimeObject: BaseRealtimeObject) {
    pool[objectId] = realtimeObject
  }

  /**
   * All objects currently in the pool. Used by the RTO5c10 parent-reference rebuild.
   */
  internal fun all(): Collection<BaseRealtimeObject> = pool.values

  /**
   * Removes all objects but root from the pool and clears the data for root.
   * Does not create a new root object, so the reference to the root object remains the same.
   */
  internal fun resetToInitialPool(emitUpdateEvents: Boolean) {
    pool.entries.removeIf { (key, _) -> key != ROOT_OBJECT_ID } // only keep the root object
    clearObjectsData(emitUpdateEvents) // RTO4b2a - clear the root object and emit update events
  }


  /**
   * Deletes objects from the pool for which object ids are not found in the provided array of ids.
   * Spec: RTO5c2
   */
  internal fun deleteExtraObjectIds(objectIds: MutableSet<String>) {
    pool.entries.removeIf { (key, _) -> key !in objectIds && key != ROOT_OBJECT_ID } // RTO5c2a - Keep root object
  }

  /**
   * Clears the data stored for all objects in the pool.
   */
  internal fun clearObjectsData(emitUpdateEvents: Boolean) {
    for (obj in pool.values) {
      val update = obj.clearData()
      if (emitUpdateEvents) obj.notifyUpdated(update)
    }
  }

  /**
   * Creates a zero-value object if it doesn't exist in the pool.
   *
   * @spec RTO6 - Creates zero-value objects when needed
   */
  internal fun createZeroValueObjectIfNotExists(objectId: String): BaseRealtimeObject {
    val existingObject = get(objectId)
    if (existingObject != null) {
      return existingObject // RTO6a
    }

    val parsedObjectId = ObjectId.fromString(objectId) // RTO6b
    return when (parsedObjectId.type) {
      ObjectType.Map -> InternalLiveMap.zeroValue(objectId, realtimeObjects) // RTO6b2
      ObjectType.Counter -> InternalLiveCounter.zeroValue(objectId, realtimeObjects) // RTO6b3
    }.apply {
      set(objectId, this) // RTO6b2, RTO6b3 - Add the zero-value object to the pool
    }
  }

  /**
   * Garbage collection interval handler.
   */
  private fun onGCInterval() {
    pool.entries.removeIf { (key, obj) ->
      // RTO10c1b1 - the root object must never be removed from the pool (RTO3b). It can never
      // become tombstoned per RTLO4e10, so this exclusion is an additional safeguard
      if (key != ROOT_OBJECT_ID && obj.isEligibleForGc(gcGracePeriod)) { true } // Remove from pool
      else {
        obj.onGCInterval(gcGracePeriod)
        false  // Keep in pool
      }
    }
  }

  /**
   * Starts the garbage collection coroutine.
   */
  private fun startGCJob() : Job {
    return gcScope.launch {
      while (isActive) {
        try {
          onGCInterval()
        } catch (e: Exception) {
          Log.e(tag, "Error during garbage collection", e)
        }
        delay(ObjectsPoolDefaults.GC_INTERVAL_MS)
      }
    }
  }

  /**
   * Disposes of the ObjectsPool, cleaning up resources.
   * Should be called when the pool is no longer needed.
   */
  fun dispose() {
    gcPeriodSubscription.unsubscribe()
    gcJob.cancel()
    gcScope.cancel()
    pool.clear()
  }
}
