package io.ably.lib.liveobjects

import io.ably.lib.liveobjects.adapter.AblyClientAdapter
import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.path.PathObjectSubscriptionRegister
import io.ably.lib.liveobjects.path.types.DefaultLiveMapPathObject
import io.ably.lib.liveobjects.path.types.LiveMapPathObject
import io.ably.lib.liveobjects.state.ObjectStateChange
import io.ably.lib.liveobjects.state.ObjectStateEvent
import io.ably.lib.liveobjects.value.ObjectType
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.AblyException
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.types.PublishResult
import io.ably.lib.util.Clock
import io.ably.lib.util.Log
import io.ably.lib.util.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.future.future
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

/**
 * Default implementation of [RealtimeObject], the entry point to the strongly-typed,
 * path-based LiveObjects API for a single channel.
 *
 * [get] returns the root of the path-addressed view once the channel is attached and the
 * initial sync has completed; path subscriptions are managed by [pathObjectSubscriptionRegister].
 *
 * Spec: RTO23
 */
internal class DefaultRealtimeObject(
  internal val channelName: String,
  internal val adapter: AblyClientAdapter,
) : RealtimeObject {

  private val tag = "DefaultRealtimeObjects"
  /**
   * @spec RTO3 - Objects pool storing all objects by object ID
   */
  internal val objectsPool = ObjectsPool(this)

  // Non-volatile by design: written and read only on [sequentialScope].
  internal var state = ObjectsState.Initialized

  /**
   * Set of serials for operations applied locally upon ACK, awaiting deduplication of the server echo.
   * Unsynchronized: accessed only on [sequentialScope].
   * @spec RTO7b, RTO7b1
   */
  internal val appliedOnAckSerials = mutableSetOf<String>()

  /**
   * @spec RTO4 - Used for handling object messages and object sync messages
   */
  internal val objectsManager = ObjectsManager(this)

  /**
   * Registry for PathObject subscriptions and path-event dispatch.
   * @spec RTO24a
   */
  internal val pathObjectSubscriptionRegister = PathObjectSubscriptionRegister(this)

  /**
   * Coroutine scope running all objects work one task at a time. `limitedParallelism(1)` is a view
   * over the shared Default pool - no dedicated thread is created or blocked, and the single slot is
   * released at every suspension point (e.g. while awaiting a publish ACK). It is the safety contract
   * for the unsynchronized state in this class and in ObjectsManager/ObjectsStateCoordinator
   * (e.g. [state], [appliedOnAckSerials], pendingSyncWaiters), and its FIFO order preserves
   * submission-order publishing for un-awaited mutations.
   */
  private val sequentialScope =
    CoroutineScope(Dispatchers.Default.limitedParallelism(1) + CoroutineName(channelName) + SupervisorJob())

  /**
   * Event bus for handling incoming object messages sequentially.
   * Processes messages inside [incomingObjectsHandler] job created using [sequentialScope].
   */
  private val objectsEventBus = MutableSharedFlow<ProtocolMessage>(extraBufferCapacity = UNLIMITED)
  private val incomingObjectsHandler: Job

  init {
    incomingObjectsHandler = initializeHandlerForIncomingObjectMessages()
  }

  /**
   * Runs [block] on the sequential scope and exposes it as a CompletableFuture. Failures
   * complete the future exceptionally with the original AblyException.
   *
   * Deliberately kept on [sequentialScope] rather than a parallel dispatcher, at no performance
   * cost: blocks suspend almost immediately on network I/O (releasing the slot), so only
   * microseconds of CPU work per operation are serialized, and all publishes funnel into the
   * single connection anyway. In return the FIFO order keeps un-awaited back-to-back mutations
   * publishing in submission order (deterministic LWW outcomes), [dispose] can cancel in-flight
   * operations via this scope's children, and [get]'s sync wait stays race-free (see [get]).
   *
   * Caveat: the returned future completes on this scope, so synchronous dependents (thenApply etc.)
   * run on the lane - a blocking callback there stalls objects processing for the channel.
   */
  internal fun <T> asyncFuture(block: suspend () -> T): CompletableFuture<T> =
    sequentialScope.future { block() }

  /**
   * Runs a mutating [block] on the sequential scope, exposed as a CompletableFuture<Void>.
   * Used by the path/instance write APIs.
   */
  internal fun asyncVoidFuture(block: suspend () -> Unit): CompletableFuture<Void> =
    asyncFuture(block).thenApply { null }

  override fun get(): CompletableFuture<LiveMapPathObject> {
    // RTO23a - get() checks only the object_subscribe MODE here;
    throwIfMissingObjectSubscribeMode()
    // MUST run on sequentialScope: ensureSynced()'s state check + SYNCED-listener registration is
    // atomic only there (SYNCED transitions run on the same scope). On a parallel dispatcher the
    // SYNCED event could fire between the two and this future would hang forever (lost wakeup).
    return asyncFuture { getRootAsync() }
  }

  override fun on(event: ObjectStateEvent, listener: ObjectStateChange.Listener): Subscription {
    throwIfInvalidAccessApiConfiguration()
    return objectsManager.on(event, listener)
  }

  override fun off(listener: ObjectStateChange.Listener) {
    throwIfInvalidAccessApiConfiguration()
    objectsManager.off(listener)
  }

  override fun offAll() {
    throwIfInvalidAccessApiConfiguration()
    objectsManager.offAll()
  }

  private suspend fun getRootAsync(): LiveMapPathObject {
    adapter.ensureAttached(channelName) // RTO23e
    objectsManager.ensureSynced(state) // RTO23c
    // RTO23d - a PathObject with an empty path, rooted at the channel's root InternalLiveMap;
    // the root reference (RTPO2b) is realised as a pool lookup at resolution time, which is
    // equivalent because the pool never replaces the root instance (RTO4b2, RTO5c2a).
    // RTTS6d - the static type is LiveMapPathObject.
    return DefaultLiveMapPathObject(this, "")
  }

  /**
   * Spec: RTO14
   */
  internal suspend fun getObjectIdStringWithNonce(objectType: ObjectType, initialValue: String): Pair<String, String> {
    val nonce = generateNonce()
    val msTimestamp = ServerTime.getCurrentTime(adapter) // RTO16 - Get server time for nonce generation
    return Pair(ObjectId.fromInitialValue(objectType, initialValue, nonce, msTimestamp).toString(), nonce)
  }

  /**
   * Spec: RTO15
   */
  internal suspend fun publish(wireObjectMessages: Array<WireObjectMessage>): PublishResult {
    // RTO15b, RTL6c - Ensure that the channel is in a valid state for publishing
    adapter.throwIfUnpublishableState(channelName)
    adapter.ensureMessageSizeWithinLimit(wireObjectMessages)
    // RTO15e - Must construct the ProtocolMessage as per RTO15e1, RTO15e2, RTO15e3
    val protocolMessage = ProtocolMessage(ProtocolMessage.Action.`object`, channelName)
    protocolMessage.state = wireObjectMessages
    // RTO15f, RTO15g - Send the ProtocolMessage using the adapter and capture success/failure
    return adapter.sendAsync(protocolMessage) // RTO15h
  }

  /**
   * Publishes the given object messages and, upon receiving the ACK, immediately applies them
   * locally as synthetic inbound messages using the assigned serial and connection's siteCode.
   *
   * Spec: RTO20
   */
  internal suspend fun publishAndApply(wireObjectMessages: Array<WireObjectMessage>) {
    // RTO20b - publish, propagate failure
    val publishResult = publish(wireObjectMessages)

    // RTO20c - validate required info
    val siteCode = adapter.connectionManager.siteCode
    if (siteCode == null) {
      Log.e(tag, "RTO20c1: siteCode not available; operations will be applied when echoed")
      return
    }
    val serials = publishResult.serials
    if (serials == null || serials.size != wireObjectMessages.size) {
      Log.e(tag, "RTO20c2: PublishResult.serials unavailable or wrong length; operations will be applied when echoed")
      return
    }

    // RTO20d - create synthetic inbound ObjectMessages
    val syntheticMessages = mutableListOf<WireObjectMessage>()
    wireObjectMessages.forEachIndexed { i, msg ->
      val serial = serials[i]
      if (serial == null) {
        Log.d(tag, "RTO20d1: serial null at index $i (conflated), skipping")
        return@forEachIndexed
      }
      syntheticMessages.add(msg.copy(serial = serial, siteCode = siteCode)) // RTO20d2a, RTO20d2b, RTO20d3
    }
    // RTO20d4 - empty synthetic list (e.g. all serials null per RTO20d1): nothing to apply, so complete successfully without the RTO20e wait
    if (syntheticMessages.isEmpty()) return

    // RTO20e, RTO20f - dispatch to sequential scope for ordering. This hop is also the thread-safety
    // boundary of the write path: everything above it touches only thread-safe/local state, while
    // applyAckResult touches unsynchronized state (sync waiters, objects state, pool mutation) whose
    // safety contract is the sequential scope.
    withContext(sequentialScope.coroutineContext) {
      objectsManager.applyAckResult(syntheticMessages) // suspends if SYNCING (RTO20e), applies on SYNCED (RTO20f)
    }
  }

  /**
   * Handles a ProtocolMessage containing proto action as `object` or `object_sync`.
   * @spec RTL1 - Processes incoming object messages and object sync messages
   */
  internal fun handle(protocolMessage: ProtocolMessage) {
    // RTL15b - Set channel serial for OBJECT messages
    adapter.setChannelSerial(channelName, protocolMessage)

    if (protocolMessage.state == null || protocolMessage.state.isEmpty()) {
      Log.w(tag, "Received ProtocolMessage with null or empty objects, ignoring")
      return
    }

    objectsEventBus.tryEmit(protocolMessage)
  }

  /**
   * Initializes the handler for incoming object messages and object sync messages.
   * Processes the messages sequentially to ensure thread safety and correct order of operations.
   *
   * @spec OM2 - Populates missing fields from parent protocol message
   */
  private fun initializeHandlerForIncomingObjectMessages(): Job {
    return sequentialScope.launch {
      objectsEventBus.collect { protocolMessage ->
        // OM2 - Populate missing fields from parent
        val objects = protocolMessage.state.filterIsInstance<WireObjectMessage>()
          .mapIndexed { index, objMsg ->
            objMsg.copy(
              connectionId = objMsg.connectionId ?: protocolMessage.connectionId, // OM2c
              timestamp = objMsg.timestamp ?: protocolMessage.timestamp, // OM2e
              id = objMsg.id ?: (protocolMessage.id + ':' + index) // OM2a
            )
          }

        try {
          when (protocolMessage.action) {
            ProtocolMessage.Action.`object` -> objectsManager.handleObjectMessages(objects)
            ProtocolMessage.Action.object_sync -> objectsManager.handleObjectSyncMessages(
              objects,
              protocolMessage.channelSerial
            )
            else -> Log.w(tag, "Ignoring protocol message with unhandled action: ${protocolMessage.action}")
          }
        } catch (exception: Exception) {
          // Skip current message if an error occurs, don't rethrow to avoid crashing the collector
          Log.e(tag, "Error handling objects message with protocolMsg id ${protocolMessage.id}", exception)
        }
      }
    }
  }

  /**
   * Dispatches channel state changes to the objects data lifecycle handlers: the
   * [ChannelState.attached] transition is handled per RTO4 (the sync lifecycle), and every
   * other state per RTO27.
   *
   * @spec RTO4 - handling of the ATTACHED transition
   * @spec RTO27 - manage the stored objects data across non-ATTACHED transitions
   */
  internal fun handleStateChange(state: ChannelState, hasObjects: Boolean) {
    sequentialScope.launch {
      when (state) {
        ChannelState.attached -> {
          Log.v(tag, "Objects.onAttached() channel=$channelName, hasObjects=$hasObjects")

          objectsManager.clearBufferedObjectOperations() // RTO4d - clear unconditionally on ATTACHED

          // RTO4c - always transition to SYNCING, regardless of the HAS_OBJECTS flag (CV-1:
          // matches the spec and current ably-js; the previous hasObjects/initialized
          // conditional was removed upstream in ably-js commit e280bff1, so a re-attach with
          // HAS_OBJECTS=0 from the Synced state now emits SYNCING -> SYNCED as required)
          objectsManager.startNewSync(null)

          // RTO4b
          if (!hasObjects) {
            // if no HAS_OBJECTS flag received on attach, we can end sync sequence immediately and treat it as no objects on a channel.
            // reset the objects pool to its initial state, and emit update events so subscribers to root object get notified about changes.
            objectsPool.resetToInitialPool(true) // RTO4b1, RTO4b2
            objectsManager.clearSyncObjectsPool() // RTO4b3
            // RTO4b5 removed — buffer already cleared by RTO4d above
            // defer the state change event until the next tick if we started a new sequence just now due to being in initialized state.
            // this allows any event listeners to process the start of the new sequence event that was emitted earlier during this event loop.
            objectsManager.endSync() // RTO4b4
          }
        }
        ChannelState.detached,
        ChannelState.suspended,
        ChannelState.failed -> {
          // RTO23c1, RTO20e1 - pass only state + errorReason; each parked waiter (get vs publishAndApply) builds its own 92008 error with its caller-specific prefix in failSyncWaiters
          val errorReason = try {
            adapter.getChannel(channelName).reason
          } catch (e: Exception) {
            null
          }
          objectsManager.failSyncWaiters(state, errorReason) // RTO23c1, RTO20e1
          // RTO27a - on DETACHED/FAILED the actual current state of the objects data can no longer
          // be known, so clear it without emitting update events; SUSPENDED is excluded here and
          // retains its data unchanged per RTO27b, since the connection may still recover
          if (state != ChannelState.suspended) {
            objectsPool.clearObjectsData(false) // RTO27a1
            objectsManager.clearSyncObjectsPool() // RTO27a2
          }
        }
        else -> {
          // RTO27b - all other states (INITIALIZED, ATTACHING, DETACHING) retain the objects data unchanged
        }
      }
    }
  }

  // Dispose of any resources associated with this RealtimeObjects instance
  fun dispose(cause: AblyException) {
    val disposeReason = CancellationException().apply { initCause(cause) }
    incomingObjectsHandler.cancel(disposeReason) // objectsEventBus automatically garbage collected when collector is cancelled
    objectsPool.dispose()
    objectsManager.dispose()
    pathObjectSubscriptionRegister.dispose()
    // Don't cancel sequentialScope (needed in getRoot method), just cancel ongoing coroutines
    sequentialScope.coroutineContext.cancelChildren(disposeReason)
  }

  /** Validates the channel is configured for access (read/subscribe) operations. Spec: RTO25 */
  internal fun throwIfInvalidAccessApiConfiguration() = adapter.throwIfInvalidAccessApiConfiguration(channelName)

  /** Validates only the object_subscribe channel mode. Spec: RTO23a */
  internal fun throwIfMissingObjectSubscribeMode() = adapter.throwIfMissingObjectSubscribeMode(channelName)

  /** Validates the channel is configured for write (mutation) operations. Spec: RTO26 */
  internal fun throwIfInvalidWriteApiConfiguration() = adapter.throwIfInvalidWriteApiConfiguration(channelName)

  /**
   * Provides the default Clock instance for the DefaultRealtimeObject.
   * This Clock is derived from the system clock, utilizing the client options
   * from the adapter configuration.
   */
  internal val clock: Clock get() = SystemClock.clockFrom(adapter.clientOptions)
}
