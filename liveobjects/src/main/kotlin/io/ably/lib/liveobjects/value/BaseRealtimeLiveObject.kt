package io.ably.lib.liveobjects.value

import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.ObjectsOperationSource
import io.ably.lib.liveobjects.ObjectsPool
import io.ably.lib.liveobjects.ROOT_OBJECT_ID
import io.ably.lib.liveobjects.message.ObjectMessage
import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.message.WireObjectOperation
import io.ably.lib.liveobjects.message.WireObjectState
import io.ably.lib.liveobjects.message.toPublicMessage
import io.ably.lib.liveobjects.objectError
import io.ably.lib.liveobjects.value.livemap.InternalLiveMap
import io.ably.lib.util.Clock
import io.ably.lib.util.Log

internal enum class ObjectType(val value: String) {
  Map("map"),
  Counter("counter")
}

/**
 * Provides common functionality and base implementation for LiveMap and LiveCounter.
 *
 * @spec RTLO1/RTLO2 - Base class for LiveMap/LiveCounter object
 *
 * This should also be included in logging
 */
internal abstract class BaseRealtimeObject(
  internal val objectId: String, // RTLO3a
  internal val objectType: ObjectType,
  internal val realtimeObject: DefaultRealtimeObject,
) {

  protected open val tag = "BaseRealtimeObject"

  internal val clock: Clock get() = realtimeObject.clock

  private val objectsPool: ObjectsPool get() = realtimeObject.objectsPool

  internal val siteTimeserials = mutableMapOf<String, String>() // RTLO3b

  internal var createOperationIsMerged = false // RTLO3c

  @Volatile
  internal var isTombstoned = false // Accessed from public API for LiveMap/LiveCounter

  internal var tombstonedAt: Long? = null

  /**
   * Reverse references: parent InternalLiveMap objectId -> set of keys at which that map
   * references this object. Keyed by objectId per RTLO3f (RTLO3f1 permits direct references;
   * ids avoid map-to-map reference cycles and survive pool replacement). Only mutated and
   * traversed on the sequential scope, so a plain mutable map is safe.
   * Spec: RTLO3f, RTLO3f2
   */
  internal val parentReferences = mutableMapOf<String, MutableSet<String>>()

  /** Records that [parent] references this object at [key]. Spec: RTLO4g */
  internal fun addParentReference(parent: InternalLiveMap, key: String) {
    parentReferences.getOrPut(parent.objectId) { mutableSetOf() }.add(key) // RTLO4g1, RTLO4g2
  }

  /** Removes the recorded reference from [parent] at [key]. Spec: RTLO4h */
  internal fun removeParentReference(parent: InternalLiveMap, key: String) {
    val keys = parentReferences[parent.objectId] ?: return // RTLO4h1
    keys.remove(key) // RTLO4h2
    if (keys.isEmpty()) {
      parentReferences.remove(parent.objectId) // RTLO4h3
    }
  }

  /** Spec: RTO5c10a */
  internal fun clearParentReferences() = parentReferences.clear()

  /**
   * All key-paths from the root InternalLiveMap to this object: one per simple path in the
   * parent-reference graph, cycle-safe, order unspecified. Iterative DFS walking upward via
   * [parentReferences], resolving parent ids through the pool (stale ids are skipped).
   * Spec: RTLO4f (RTLO4f1..f4)
   */
  internal fun getFullPaths(): List<List<String>> {
    val paths = mutableListOf<List<String>>()
    // (object, path-so-far, visited objectIds on this branch)
    val stack = ArrayDeque<Triple<BaseRealtimeObject, List<String>, Set<String>>>()
    stack.addLast(Triple(this, emptyList(), emptySet()))
    while (stack.isNotEmpty()) {
      val (obj, currentPath, visited) = stack.removeLast()
      if (obj.objectId in visited) continue // RTLO4f2 - simple paths only, skip cycles
      val newVisited = visited + obj.objectId
      if (obj.objectId == ROOT_OBJECT_ID) {
        paths.add(currentPath) // RTLO4f2 - the empty path when this object is root itself
        continue
      }
      for ((parentId, keys) in obj.parentReferences) {
        val parent = objectsPool.get(parentId) ?: continue // stale reference - parent left the pool
        for (key in keys) {
          stack.addLast(Triple(parent, listOf(key) + currentPath, newVisited))
        }
      }
    }
    return paths // RTLO4f3 - each simple path exactly once
  }

  /**
   * This is invoked by ObjectMessage having updated data with parent `ProtocolMessageAction` as `object_sync`
   * @return an update describing the changes
   *
   * @spec RTLM6/RTLC6 - Overrides ObjectMessage with object data state from sync to LiveMap/LiveCounter
   */
  internal fun applyObjectSync(wireObjectMessage: WireObjectMessage): ObjectUpdate {
    val wireObjectState = wireObjectMessage.objectState as WireObjectState // we have non-null objectState here due to RTO5f
    validate(wireObjectState)
    // object's site serials are still updated even if it is tombstoned, so always use the site serials received from the operation.
    // should default to empty map if site serials do not exist on the object state, so that any future operation may be applied to this object.
    siteTimeserials.clear()
    siteTimeserials.putAll(wireObjectState.siteTimeserials) // RTLC6a, RTLM6a

    if (isTombstoned) {
      // this object is tombstoned. this is a terminal state which can't be overridden. skip the rest of object state message processing
      return ObjectUpdate.NoOp // RTLM6e1, RTLC6e1
    }
    return applyObjectState(wireObjectState, wireObjectMessage) // RTLM6, RTLC6
  }

  /**
   * This is invoked by ObjectMessage having updated data with parent `ProtocolMessageAction` as `object`
   * @return the [ObjectUpdate] produced by the operation (RTLC9g/RTLM7f); [ObjectUpdate.NoOp] if nothing was applied
   *
   * @spec RTLM15/RTLC7 - Applies ObjectMessage with object data operations to LiveMap/LiveCounter
   */
  internal fun applyObject(wireObjectMessage: WireObjectMessage, source: ObjectsOperationSource): ObjectUpdate {
    validateObjectId(wireObjectMessage.operation?.objectId)

    val msgTimeSerial = wireObjectMessage.serial
    val msgSiteCode = wireObjectMessage.siteCode
    val wireObjectOperation = wireObjectMessage.operation as WireObjectOperation

    if (!canApplyOperation(msgSiteCode, msgTimeSerial)) {
      // RTLC7b, RTLM15b
      if (!msgTimeSerial.isNullOrEmpty() && !msgSiteCode.isNullOrEmpty()) {
        Log.v(
          tag,
          "Skipping ${wireObjectOperation.action} op: op serial $msgTimeSerial <= site serial ${siteTimeserials[msgSiteCode]}; " +
            "objectId=$objectId"
        )
      }
      return ObjectUpdate.NoOp // RTLC7b / RTLM15b
    }
    // RTLC7c / RTLM15c - only update siteTimeserials for CHANNEL source
    if (source == ObjectsOperationSource.CHANNEL) {
      siteTimeserials[msgSiteCode!!] = msgTimeSerial!! // RTLC7c, RTLM15c
    }

    if (isTombstoned) {
      // this object is tombstoned so the operation cannot be applied
      return ObjectUpdate.NoOp // RTLC7e / RTLM15e
    }
    return applyObjectOperation(wireObjectOperation, wireObjectMessage) // RTLC7d
  }

  /**
   * Checks if an operation can be applied based on serial comparison.
   *
   * @spec RTLO4a - Serial comparison logic for LiveMap/LiveCounter operations
   */
  internal fun canApplyOperation(siteCode: String?, timeSerial: String?): Boolean {
    if (timeSerial.isNullOrEmpty() || siteCode.isNullOrEmpty()) {
      // RTLO4a3 - log a warning and refuse to apply; must not throw, as a throw would abort
      // every sibling operation in the same ProtocolMessage batch
      Log.w(tag, "Invalid serial values on object operation message; serial=$timeSerial, siteCode=$siteCode; objectId=$objectId")
      return false // RTLO4a3
    }
    val existingSiteSerial = siteTimeserials[siteCode] // RTLO4a4
    return existingSiteSerial == null || timeSerial > existingSiteSerial // RTLO4a5, RTLO4a6
  }

  internal fun validateObjectId(objectId: String?) {
    if (this.objectId != objectId) {
      throw objectError("Invalid object: incoming objectId=$objectId; $objectType objectId=${this.objectId}")
    }
  }

  /**
   * Marks the object as tombstoned. The returned update carries `tombstone = true` and the
   * source message (RTLO4e5..e8); the caller emits it via notifyUpdated.
   * The root object can never be tombstoned (RTLO4e10); such attempts return a noop update.
   */
  internal fun tombstone(serialTimestamp: Long?, message: WireObjectMessage?): ObjectUpdate {
    if (objectId == ROOT_OBJECT_ID) {
      // RTLO4e10 - the root object must always exist in the ObjectsPool (RTO3b); the realtime
      // system never publishes an OBJECT_DELETE operation or a tombstoned object state for it,
      // so an attempt to tombstone it indicates a faulty message. Log a warning and skip it
      Log.w(
        tag,
        "Attempt to tombstone the root object was rejected; " +
          "serial=${message?.serial}, siteCode=${message?.siteCode}, messageId=${message?.id}",
      )
      return ObjectUpdate.NoOp
    }
    if (serialTimestamp == null) {
      Log.w(tag, "Tombstoning object $objectId without serial timestamp, using local timestamp instead") // RTLO6b1
    }
    isTombstoned = true // RTLO4e2
    tombstonedAt = serialTimestamp ?: clock.currentTimeMillis() // RTLO4e3, RTLO6a, RTLO6b
    // RTLO4e5..e7 - stamp tombstone + source message on the diff update. Tombstoning an
    // already-empty object yields an empty diff, but the update must still be emitted (the
    // tombstone flag drives listener teardown per RTLO4b4c3c; ably-js diffs are never noop),
    // so synthesize an empty typed update in that case.
    return when (val update = clearData()) { // RTLO4e4
      is ObjectUpdate.MapUpdate -> update.copy(tombstone = true, objectMessage = message)
      is ObjectUpdate.CounterUpdate -> update.copy(tombstone = true, objectMessage = message)
      ObjectUpdate.NoOp -> when (objectType) {
        ObjectType.Map -> ObjectUpdate.MapUpdate(emptyMap(), message, tombstone = true)
        ObjectType.Counter -> ObjectUpdate.CounterUpdate(0.0, message, tombstone = true)
      }
    } // RTLO4e8
  }

  /**
   * Checks if the object is eligible for garbage collection.
   *
   * An object is eligible for garbage collection if it has been tombstoned and
   * the time since tombstoning exceeds the specified grace period.
   *
   * @param gcGracePeriod The grace period in milliseconds that tombstoned objects
   *                      should be kept before being eligible for collection.
   *                      This value is retrieved from the server's connection details
   *                      or defaults to 24 hours if not provided by the server.
   * @return true if the object is tombstoned and the grace period has elapsed,
   *         false otherwise
   */
  internal fun isEligibleForGc(gcGracePeriod: Long): Boolean {
    val currentTime = clock.currentTimeMillis()
    return isTombstoned && tombstonedAt?.let { currentTime - it >= gcGracePeriod } == true
  }

  /**
   * Validates that the provided object state is compatible with this object.
   * Checks object ID, type-specific validations, and any included create operations.
   */
  abstract fun validate(state: WireObjectState)

  /**
   * Applies an object state received during synchronization to this object.
   * This method should update the internal data structure with the complete state
   * received from the server.
   *
   * @param wireObjectState The complete state to apply to this object
   * @return A map describing the changes made to the object's data
   *
   */
  abstract fun applyObjectState(wireObjectState: WireObjectState, message: WireObjectMessage): ObjectUpdate

  /**
   * Applies an operation to this object.
   * This method handles the specific operation actions (e.g., update, remove)
   * by modifying the underlying data structure accordingly.
   *
   * @param operation The operation containing the action and data to apply
   * @param message The complete object message containing the operation
   * @return the [ObjectUpdate] produced by the operation (RTLC9g/RTLM7f); [ObjectUpdate.NoOp] if nothing was applied
   *
   */
  abstract fun applyObjectOperation(operation: WireObjectOperation, message: WireObjectMessage): ObjectUpdate

  /**
   * Clears the object's data and returns an update describing the changes.
   * This is called during tombstoning and explicit clear operations.
   *
   * This method:
   * 1. Calculates a diff between the current state and an empty state
   * 2. Clears all entries from the underlying data structure
   * 3. Returns a map containing metadata about what was cleared
   *
   * The returned map is used to notifying other components about what entries were removed.
   *
   * @return A map representing the diff of changes made
   */
  abstract fun clearData(): ObjectUpdate

  /**
   * Notifies subscribers about a change to this object: instance listeners first
   * (RTLO4b4c3a), then path-based subscriptions via the bubbling dispatch (RTLO4b4c3b ->
   * RTO24b). Tombstone updates additionally deregister all instance listeners afterwards
   * (RTLO4b4c3c); path subscriptions are unaffected (RTLO4b4c3c1).
   *
   * Spec: RTLO4b4c
   */
  internal fun notifyUpdated(update: ObjectUpdate) {
    if (update.noOp) {
      return // RTLO4b4c1
    }
    Log.v(tag, "Object $objectId updated: $update")
    val publicMessage = update.objectMessage
      ?.takeIf { it.operation != null } // sync messages never surface publicly (RTPO19e2/RTINS16e2)
      ?.toPublicMessage(realtimeObject.channelName) // PAOM3
    notifyInstanceSubscriptions(update, publicMessage) // RTLO4b4c3a
    notifyPathSubscriptions(update, publicMessage) // RTLO4b4c3b
    if (update.tombstone) {
      deregisterInstanceListeners() // RTLO4b4c3c
    }
  }

  /** Emits the update to this object's instance listeners as an InstanceSubscriptionEvent. */
  protected abstract fun notifyInstanceSubscriptions(update: ObjectUpdate, message: ObjectMessage?)

  /** Deregisters all instance listeners - tombstone teardown (RTLO4b4c3c). */
  protected abstract fun deregisterInstanceListeners()

  /**
   * Path-based subscription dispatch: one notifyPathEvent per path-to-this-object, with the
   * object's own path as the most-preferred candidate and, for map updates, one deeper
   * candidate per changed key.
   *
   * Spec: RTO24b (RTO24b1, RTO24b2, RTO24b2a1, RTO24b2a2)
   */
  private fun notifyPathSubscriptions(update: ObjectUpdate, message: ObjectMessage?) {
    val pathsToThis = getFullPaths() // RTO24b1
    if (pathsToThis.isEmpty()) {
      return // orphaned object (not reachable from root) - no path events
    }
    for (pathToThis in pathsToThis) { // RTO24b2
      val candidates = mutableListOf(pathToThis) // RTO24b2a1 - most preferred first
      if (update is ObjectUpdate.MapUpdate) {
        update.update.keys.forEach { candidates.add(pathToThis + it) } // RTO24b2a2
      }
      realtimeObject.pathObjectSubscriptionRegister.notifyPathEvent(candidates, message)
    }
  }

  /**
   * Called during garbage collection intervals to clean up expired entries.
   *
   * This method is invoked periodically (every 5 minutes) by the ObjectsPool
   * to perform cleanup of tombstoned data that has exceeded the grace period.
   *
   * This method should identify and remove entries that:
   * - Have been marked as tombstoned
   * - Have a tombstone timestamp older than the specified grace period
   *
   * @param gcGracePeriod The grace period in milliseconds that tombstoned entries
   *                      should be kept before being eligible for removal.
   *                      This value is retrieved from the server's connection details
   *                      or defaults to 24 hours if not provided by the server.
   *                      Must be greater than 2 minutes to ensure proper operation
   *                      ordering and avoid issues with delayed operations.
   *
   * Implementations typically use single-pass removal techniques to
   * efficiently clean up expired data without creating temporary collections.
   */
  abstract fun onGCInterval(gcGracePeriod: Long)
}
