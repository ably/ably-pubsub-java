package io.ably.lib.liveobjects

import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.message.WireObjectOperation
import io.ably.lib.liveobjects.message.WireObjectOperationAction
import io.ably.lib.liveobjects.message.WireObjectState
import io.ably.lib.liveobjects.message.WireObjectsMap
import io.ably.lib.liveobjects.value.BaseRealtimeObject
import io.ably.lib.liveobjects.value.ObjectUpdate
import io.ably.lib.liveobjects.value.noOp
import io.ably.lib.liveobjects.value.livecounter.InternalLiveCounter
import io.ably.lib.liveobjects.value.livemap.InternalLiveMap
import io.ably.lib.liveobjects.value.livemap.isEntryOrRefTombstoned
import io.ably.lib.util.Log

/**
 * @spec RTO5 - Processes OBJECT and OBJECT_SYNC messages during sync sequences
 * @spec RTO6 - Creates zero-value objects when needed
 */
internal class ObjectsManager(private val realtimeObjects: DefaultRealtimeObject): ObjectsStateCoordinator() {
  private val tag = "ObjectsManager"
  /**
   * @spec RTO5 - Sync objects pool for collecting sync messages
   */
  private val syncObjectsPool = mutableMapOf<String, WireObjectMessage>()
  private var currentSyncId: String? = null
  /**
   * @spec RTO7 - Buffered object operations during sync
   */
  internal val bufferedObjectOperations = mutableListOf<WireObjectMessage>() // RTO7a

  /**
   * Handles object messages (non-sync messages).
   *
   * @spec RTO8 - Buffers messages if not synced, applies immediately if synced
   */
  internal fun handleObjectMessages(wireObjectMessages: List<WireObjectMessage>) {
    if (realtimeObjects.state != ObjectsState.Synced) {
      // RTO7 - The client receives object messages in realtime over the channel concurrently with the sync sequence.
      // Some of the incoming object messages may have already been applied to the objects described in
      // the sync sequence, but others may not; therefore we must buffer these messages so that we can apply
      // them to the objects once the sync is complete.
      Log.v(tag, "Buffering ${wireObjectMessages.size} object messages, state: ${realtimeObjects.state}")
      bufferedObjectOperations.addAll(wireObjectMessages) // RTO8a
      return
    }

    // Apply messages immediately if synced
    applyObjectMessages(wireObjectMessages, ObjectsOperationSource.CHANNEL) // RTO8b
  }

  /**
   * Handles object sync messages.
   *
   * @spec RTO5 - Parses sync channel serial and manages sync sequences
   */
  internal fun handleObjectSyncMessages(wireObjectMessages: List<WireObjectMessage>, syncChannelSerial: String?) {
    val syncTracker = ObjectsSyncTracker(syncChannelSerial)
    if (syncTracker.isMalformed) {
      // RTO5a6 - a malformed channelSerial (no ':' separator) is handled as absent (RTO5a5): warn and fall through so null syncId/syncCursor makes the flow apply the messages and end the sync like a single-message sync
      Log.w(tag, "OBJECT_SYNC channelSerial is malformed (missing ':' separator); treating as absent per RTO5a6: $syncChannelSerial")
    }
    val isNewSync = syncTracker.hasSyncStarted(currentSyncId)
    if (isNewSync) {
      // RTO5a2 - new sync sequence started
      startNewSync(syncTracker.syncId)
    }

    // RTO5a3 - continue current sync sequence
    applyObjectSyncMessages(wireObjectMessages) // RTO5f

    // RTO5a4 - if this is the last (or only) message in a sequence of sync updates, end the sync
    if (syncTracker.hasSyncEnded()) {
      // defer the state change event until the next tick if this was a new sync sequence
      // to allow any event listeners to process the start of the new sequence event that was emitted earlier during this event loop.
      endSync()
    }
  }

  /**
   * Starts a new sync sequence.
   *
   * @spec RTO5 - Sync sequence initialization
   */
  internal fun startNewSync(syncId: String?) {
    Log.v(tag, "Starting new sync sequence: syncId=$syncId")

    syncObjectsPool.clear() // RTO5a2a
    currentSyncId = syncId
    stateChange(ObjectsState.Syncing)
  }

  /**
   * Ends the current sync sequence.
   *
   * @spec RTO5c - Applies sync data and buffered operations
   */
  internal fun endSync() {
    Log.v(tag, "Ending sync sequence")
    applySync()                                                                    // RTO5c1/2/7
    applyObjectMessages(bufferedObjectOperations, ObjectsOperationSource.CHANNEL) // RTO5c6
    bufferedObjectOperations.clear()                                               // RTO5c5
    syncObjectsPool.clear()                                                        // RTO5c4
    currentSyncId = null                                                           // RTO5c3
    realtimeObjects.appliedOnAckSerials.clear()                                    // RTO5c9
    stateChange(ObjectsState.Synced)                              // RTO5c8 - emits SYNCED, resolving any
    // pending applyAckResult waiters (RTO20e) via awaitSyncCompletion's one-shot SYNCED listener
  }

  /**
   * Called from publishAndApply (via withContext sequentialScope).
   * If SYNCED: apply immediately with LOCAL source.
   * If not SYNCED: suspend until objects transition to SYNCED (RTO20e), then apply. If the channel leaves a
   * usable state while waiting, [awaitSyncCompletion] throws the 92008 error and the apply fails (RTO20e1).
   */
  internal suspend fun applyAckResult(messages: List<WireObjectMessage>) {
    // MUST run on the sequential scope: the state check + waiter registration in awaitSyncCompletion
    // is atomic only there (same lost-wakeup hazard as ensureSynced).
    if (realtimeObjects.state != ObjectsState.Synced) {
      // RTO20e1 - the publishAndApply-specific failure message prefix; get()'s wait uses its own (RTO23c1).
      awaitSyncCompletion("the operation could not be applied locally") // suspends until SYNCED (RTO20e); throws 92008 on channel state change (RTO20e1)
    }
    applyObjectMessages(messages, ObjectsOperationSource.LOCAL) // RTO20f
  }

  /**
   * Clears the sync objects pool.
   * Used by DefaultRealtimeObjects.handleStateChange.
   */
  internal fun clearSyncObjectsPool() {
    syncObjectsPool.clear()
  }

  /**
   * Clears the buffered object operations.
   * Used by DefaultRealtimeObjects.handleStateChange.
   */
  internal fun clearBufferedObjectOperations() {
    bufferedObjectOperations.clear()
  }

  /**
   * Applies sync data to objects pool.
   *
   * @spec RTO5c - Processes sync data and updates objects pool
   */
  private fun applySync() {
    if (syncObjectsPool.isEmpty()) {
      return
    }

    val receivedObjectIds = mutableSetOf<String>()
    // RTO5c1a2 - List to collect updates for existing objects
    val existingObjectUpdates = mutableListOf<Pair<BaseRealtimeObject, ObjectUpdate>>()

    // RTO5c1
    for ((objectId, objectMessage) in syncObjectsPool) {
      val wireObjectState = objectMessage.objectState as WireObjectState // we have non-null objectState here due to RTO5f
      receivedObjectIds.add(objectId)
      val existingObject = realtimeObjects.objectsPool.get(objectId)

      // RTO5c1a
      if (existingObject != null) {
        // Update existing object
        val update = existingObject.applyObjectSync(objectMessage) // RTO5c1a1
        existingObjectUpdates.add(Pair(existingObject, update))
      } else { // RTO5c1b
        // RTO5c1b1, RTO5c1b1a, RTO5c1b1b - Create new object and add it to the pool
        val newObject = createObjectFromState(wireObjectState) ?: continue // RTO5c1b1c - skip unsupported
        newObject.applyObjectSync(objectMessage)
        realtimeObjects.objectsPool.set(objectId, newObject)
      }
    }

    // RTO5c2 - need to remove realtimeObject instances from the ObjectsPool for which objectIds were not received during the sync sequence
    realtimeObjects.objectsPool.deleteExtraObjectIds(receivedObjectIds)

    // RTO5c10 - rebuild every parentReferences map after the pool has settled, so that
    // getFullPaths is correct by the time the RTO5c7 notifications below are dispatched
    rebuildAllParentReferences()

    // RTO5c7 - call subscription callbacks for all updated existing objects
    existingObjectUpdates.forEach { (obj, update) ->
      obj.notifyUpdated(update)
    }
  }

  /**
   * Rebuilds all parent references from the settled pool state. Necessary after a sync because
   * objects may reference other objects that were not yet in the pool when their references
   * were first applied. Mirrors ably-js realtimeobject.ts#_rebuildAllParentReferences.
   *
   * @spec RTO5c10
   */
  private fun rebuildAllParentReferences() {
    val objects = realtimeObjects.objectsPool.all()
    objects.forEach { it.clearParentReferences() } // RTO5c10a
    objects.filterIsInstance<InternalLiveMap>().forEach { map ->
      // RTO5c10b - RTLM11-equivalent iteration over the raw entries: skip entries that are
      // tombstoned or reference a tombstoned object (RTLM14), but avoid the full value
      // resolution of entries() since only entry.data.objectId is needed here
      for ((key, entry) in map.data) {
        val refId = entry.data?.objectId ?: continue
        if (entry.isEntryOrRefTombstoned(realtimeObjects.objectsPool)) continue
        realtimeObjects.objectsPool.get(refId)?.addParentReference(map, key)
      }
    }
  }

  /**
   * Applies object messages to objects.
   *
   * @spec RTO9 - Creates zero-value objects if they don't exist
   */
  internal fun applyObjectMessages(
    wireObjectMessages: List<WireObjectMessage>,
    source: ObjectsOperationSource = ObjectsOperationSource.CHANNEL,
  ) {
    // RTO9a
    for (objectMessage in wireObjectMessages) {
      if (objectMessage.operation == null) {
        // RTO9a1
        Log.w(tag, "Object message received without operation field, skipping message: ${objectMessage.id}")
        continue
      }

      val wireObjectOperation: WireObjectOperation = objectMessage.operation // RTO9a2
      if (wireObjectOperation.action == WireObjectOperationAction.Unknown) {
        // RTO9a2b - object operation action is unknown, skip the message
        Log.w(tag, "Object operation action is unknown, skipping message: ${objectMessage.id}")
        continue
      }

      // RTO9a3 - skip operations already applied on ACK (discard without taking any further action).
      // This check comes before zero-value object creation (RTO9a2a1) so that no zero-value object is
      // created for an objectId not yet in the pool when the echo is being discarded.
      // Note: siteTimeserials is NOT updated here intentionally — updating it to the echo's serial would
      // incorrectly reject older-but-unprocessed operations from the same site that arrive after the echo.
      if (objectMessage.serial != null &&
        realtimeObjects.appliedOnAckSerials.contains(objectMessage.serial)) {
        Log.d(tag, "RTO9a3: serial ${objectMessage.serial} already applied on ACK; discarding echo")
        realtimeObjects.appliedOnAckSerials.remove(objectMessage.serial)
        continue // discard without taking any further action
      }

      // RTO9a2a - we can receive an op for an object id we don't have yet in the pool. instead of buffering such operations,
      // we can create a zero-value object for the provided object id and apply the operation to that zero-value object.
      // this also means that all objects are capable of applying the corresponding *_CREATE ops on themselves,
      // since they need to be able to eventually initialize themselves from that *_CREATE op.
      // so to simplify operations handling, we always try to create a zero-value object in the pool first,
      // and then we can always apply the operation on the existing object in the pool.
      val obj = realtimeObjects.objectsPool.createZeroValueObjectIfNotExists(wireObjectOperation.objectId) // RTO9a2a1
      val update = obj.applyObject(objectMessage, source) // RTO9a2a2, RTO9a2a3
      if (source == ObjectsOperationSource.LOCAL && !update.noOp && objectMessage.serial != null) {
        realtimeObjects.appliedOnAckSerials.add(objectMessage.serial) // RTO9a2a4
      }
    }
  }

  /**
   * Applies sync messages to sync data pool, merging partial sync messages for the same objectId.
   *
   * @spec RTO5f - Collects and merges object states during sync sequence
   */
  private fun applyObjectSyncMessages(wireObjectMessages: List<WireObjectMessage>) {
    for (objectMessage in wireObjectMessages) {
      if (objectMessage.objectState == null) {
        Log.w(tag, "Object message received during OBJECT_SYNC without object field, skipping message: ${objectMessage.id}")
        continue
      }

      val wireObjectState: WireObjectState = objectMessage.objectState
      val objectId = wireObjectState.objectId
      val existingEntry = syncObjectsPool[objectId]

      if (existingEntry == null) {
        // RTO5f1 - objectId not in pool, store directly
        if (wireObjectState.counter != null || wireObjectState.map != null) {
          syncObjectsPool[objectId] = objectMessage
        } else {
          // RTO5c1b1c - object state must contain either counter or map data
          Log.w(tag, "Object state received without counter or map data, skipping message: ${objectMessage.id}")
        }
        continue
      }

      // RTO5f2 - objectId already in pool; this is a partial sync message, merge based on type
      when {
        wireObjectState.map != null -> {
          // RTO5f2a - map object: merge entries
          if (wireObjectState.tombstone) {
            // RTO5f2a1 - tombstone: replace pool entry entirely
            syncObjectsPool[objectId] = objectMessage
          } else {
            // RTO5f2a2 - merge map entries; server guarantees no duplicate keys across partials
            val existingState = existingEntry.objectState!! // non-null for existing entry
            val mergedEntries = existingState.map?.entries.orEmpty() + wireObjectState.map.entries.orEmpty()
            val mergedMap = (existingState.map ?: WireObjectsMap()).copy(entries = mergedEntries)
            val mergedState = existingState.copy(map = mergedMap)
            syncObjectsPool[objectId] = existingEntry.copy(objectState = mergedState)
          }
        }
        wireObjectState.counter != null -> {
          // RTO5f2b - counter objects must never be split across messages
          Log.e(tag, "Received partial sync message for a counter object, skipping: ${objectMessage.id}")
        }
        else -> {
          // RTO5f3 - unsupported type, log warning and skip
          Log.w(tag, "Received partial sync message for an unsupported object type, skipping: ${objectMessage.id}")
        }
      }
    }
  }

  /**
   * Creates an object from object state.
   *
   * @spec RTO5c1b - Creates objects from object state based on type
   */
  private fun createObjectFromState(wireObjectState: WireObjectState): BaseRealtimeObject? {
    return when {
      wireObjectState.counter != null -> InternalLiveCounter.zeroValue(wireObjectState.objectId, realtimeObjects) // RTO5c1b1a
      wireObjectState.map != null -> InternalLiveMap.zeroValue(wireObjectState.objectId, realtimeObjects) // RTO5c1b1b
      else -> {
        // RTO5c1b1c - unsupported object type, skip gracefully
        Log.w(tag, "Received unsupported object state during OBJECT_SYNC (no counter or map), skipping objectId: ${wireObjectState.objectId}")
        null
      }
    }
  }

  /**
   * Changes the state and emits events.
   *
   * @spec RTO2 - Emits state change events for syncing and synced states
   */
  private fun stateChange(newState: ObjectsState) {
    if (realtimeObjects.state == newState) {
      return
    }
    Log.v(tag, "Objects state changed to: $newState from ${realtimeObjects.state}")
    realtimeObjects.state = newState

    // deferEvent not needed since objectsStateChanged processes events in a sequential coroutine scope
    objectsStateChanged(newState)
  }

  internal fun dispose() {
    // pending applyAckResult waiters are cancelled via the sequential scope teardown in
    // DefaultRealtimeObject.dispose (their coroutines are cancelled, removing them from the waiter set)
    syncObjectsPool.clear()
    bufferedObjectOperations.clear()
    disposeObjectsStateListeners()
  }
}
