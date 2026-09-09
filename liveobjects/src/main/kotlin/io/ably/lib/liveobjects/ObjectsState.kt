package io.ably.lib.liveobjects

import io.ably.lib.liveobjects.state.ObjectStateChange
import io.ably.lib.liveobjects.state.ObjectStateEvent
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.AblyException
import io.ably.lib.types.ErrorInfo
import io.ably.lib.util.EventEmitter
import io.ably.lib.util.Log
import kotlinx.coroutines.*

/**
 * @spec RTO2 - enum representing objects state
 */
internal enum class ObjectsState {
  Initialized,
  Syncing,
  Synced
}

/**
 * Maps internal ObjectsState values to their corresponding public ObjectsStateEvent values.
 * Used to determine which events should be emitted when state changes occur.
 * INITIALIZED maps to null (no event), while SYNCING and SYNCED map to their respective events.
 */
private val objectsStateToEventMap = mapOf(
  ObjectsState.Initialized to null,
  ObjectsState.Syncing to ObjectStateEvent.SYNCING,
  ObjectsState.Synced to ObjectStateEvent.SYNCED
)

/**
 * An interface for managing and communicating changes in the synchronization state of objects.
 *
 * Implementations should ensure thread-safe event emission and proper synchronization
 * between state change notifications.
 */
internal interface HandlesObjectsStateChange {
  /**
   * Handles changes in the state of objects by notifying all registered listeners.
   * Implementations should ensure thread-safe event emission to both internal and public listeners.
   * Makes sure every event is processed in the order they were received.
   * @param newState The new state of the objects, SYNCING or SYNCED.
   */
  fun objectsStateChanged(newState: ObjectsState)

  /**
   * Suspends the current coroutine until objects are synchronized.
   * Returns immediately if state is already SYNCED, otherwise waits for the SYNCED event.
   *
   * @param currentState The current state of objects to determine if waiting is necessary
   */
  suspend fun ensureSynced(currentState: ObjectsState)

  /**
   * Disposes all registered state change listeners and cancels any pending operations.
   * Should be called when the associated RealtimeObjects instance is no longer needed.
   */
  fun disposeObjectsStateListeners()
}


internal abstract class ObjectsStateCoordinator : ObjectStateChange, HandlesObjectsStateChange {
  private val tag = "ObjectsStateCoordinator"
  private val internalObjectStateEmitter = ObjectsStateEmitter()
  // related to RTC10, should have a separate EventEmitter for users of the library
  private val externalObjectStateEmitter = ObjectsStateEmitter()

  /**
   * Pending sync waiters: both get() (RTO23c/RTO23c1) and publishAndApply (RTO20e/RTO20e1) park here until
   * SYNCED. Each suspends until SYNCED, and is failed — rather than orphaned — if the channel enters
   * DETACHED/SUSPENDED/FAILED while waiting. Each waiter carries its own [SyncWaiter.failureDescription]
   * message prefix so [failSyncWaiters] builds the caller-specific 92008 error (RTO23c1's "object could not
   * be retrieved" vs RTO20e1's "operation could not be applied locally"). All access happens on the single
   * sequential scope (see [DefaultRealtimeObject]), so no additional synchronization is required.
   */
  private val pendingSyncWaiters = mutableSetOf<SyncWaiter>()

  /**
   * A parked sync waiter. Its [deferred] resolves on SYNCED, or is failed by [failSyncWaiters] with an error
   * whose message begins with [failureDescription] — the caller-specific RTO23c1 (get) / RTO20e1
   * (publishAndApply) prefix. Mirrors ably-js's shared `_waitForSyncedOrChannelFailure(failureDescription)`
   * helper, where the only per-caller difference is that prefix.
   */
  private class SyncWaiter(
    val failureDescription: String,
    val deferred: CompletableDeferred<Unit> = CompletableDeferred(),
  )

  override fun on(event: ObjectStateEvent, listener: ObjectStateChange.Listener): Subscription {
    externalObjectStateEmitter.on(event, listener)
    return onceSubscription {
      externalObjectStateEmitter.off(event, listener)
    }
  }

  override fun off(listener: ObjectStateChange.Listener) = externalObjectStateEmitter.off(listener)

  override fun offAll() = externalObjectStateEmitter.off()

  override fun objectsStateChanged(newState: ObjectsState) {
    objectsStateToEventMap[newState]?.let { objectsStateEvent ->
      internalObjectStateEmitter.emit(objectsStateEvent)
      externalObjectStateEmitter.emit(objectsStateEvent)
    }
  }

  override suspend fun ensureSynced(currentState: ObjectsState) {
    // MUST be called on the sequential scope: the state check and the waiter registration in
    // awaitSyncCompletion below are atomic only because SYNCED transitions run on that same scope.
    // Off it, a SYNCED fired between the check and the registration would be lost and this would
    // suspend forever.
    if (currentState != ObjectsState.Synced) {
      // RTO23c1 - route get()'s wait through the same [pendingSyncWaiters] machinery publishAndApply uses (RTO20e/RTO20e1) so [failSyncWaiters] fails it with the 92008 error instead of orphaning it on DETACHED/SUSPENDED/FAILED
      awaitSyncCompletion("the object could not be retrieved")
    }
  }

  /**
   * Suspends until objects transition to SYNCED (via a one-shot SYNCED listener), or throws if the channel
   * leaves a usable state while waiting ([failSyncWaiters], RTO20e1/RTO23c1). Shared by get() (RTO23c) and
   * publishAndApply (RTO20e); the two callers differ only in [failureDescription], the message prefix used to
   * build the failure error (see [SyncWaiter]). Unlike [ensureSynced], the waiter is tracked in
   * [pendingSyncWaiters] so it can be failed rather than orphaned across re-syncs (the SYNCED event resolves
   * whichever sync ultimately completes, regardless of how many sync cycles occur while waiting).
   *
   * @param failureDescription caller-specific prefix for the 92008 error message if the wait is failed —
   *   "the object could not be retrieved" for get() (RTO23c1) or "the operation could not be applied locally"
   *   for publishAndApply (RTO20e1).
   *
   * Spec: RTO20e, RTO20e1, RTO23c, RTO23c1
   */
  protected suspend fun awaitSyncCompletion(failureDescription: String) {
    val waiter = SyncWaiter(failureDescription)
    pendingSyncWaiters.add(waiter)
    // off() the one-shot in finally (not just once()'s auto-drop on SYNCED) so it never lingers when the wait ends via failSyncWaiters (RTO20e1/RTO23c1)
    val syncedListener = ObjectStateChange.Listener {
      Log.v(tag, "Objects state changed to SYNCED, resuming parked sync waiter")
      waiter.deferred.complete(Unit)
    }
    internalObjectStateEmitter.once(ObjectStateEvent.SYNCED, syncedListener)
    try {
      waiter.deferred.await()
    } finally {
      pendingSyncWaiters.remove(waiter)
      internalObjectStateEmitter.off(ObjectStateEvent.SYNCED, syncedListener)
    }
  }

  /**
   * Fails every pending [awaitSyncCompletion] waiter — called from [DefaultRealtimeObject.handleStateChange]
   * when the channel enters DETACHED/SUSPENDED/FAILED while a get() or publishAndApply is waiting for SYNCED.
   * The failure site supplies only the [state] context and the channel's [reason]; each waiter then builds
   * its own AblyException, prefixing its caller-specific [SyncWaiter.failureDescription] (RTO23c1 vs RTO20e1)
   * onto the shared 92008 / statusCode 400 / cause=reason error that both spec points mandate identically.
   * Mirrors ably-js, which only catches channel-state transitions that fire after a waiter has started
   * waiting (once() semantics).
   *
   * Spec: RTO20e1, RTO23c1
   */
  fun failSyncWaiters(state: ChannelState, reason: ErrorInfo?) {
    if (pendingSyncWaiters.isEmpty()) return
    val waiters = pendingSyncWaiters.toList()
    pendingSyncWaiters.clear()
    val cause = reason?.let { AblyException.fromErrorInfo(it) }
    waiters.forEach { waiter ->
      val error = ablyException(
        "${waiter.failureDescription}: channel entered $state whilst waiting for objects sync",
        ObjectErrorCode.PublishAndApplyFailedDueToChannelState,
        ObjectHttpStatusCode.BadRequest,
        cause = cause,
      )
      waiter.deferred.completeExceptionally(error)
    }
  }

  override fun disposeObjectsStateListeners() = offAll()
}

private class ObjectsStateEmitter : EventEmitter<ObjectStateEvent, ObjectStateChange.Listener>() {
  private val tag = "ObjectsStateEmitter"
  override fun apply(listener: ObjectStateChange.Listener?, event: ObjectStateEvent?, vararg args: Any?) {
    try {
      event?.let { listener?.onStateChanged(it) }
        ?: Log.w(tag, "Null event passed to ObjectsStateChange Listener callback")
    } catch (t: Throwable) {
      Log.e(tag, "Error occurred while executing listener callback for event: $event", t)
    }
  }
}
