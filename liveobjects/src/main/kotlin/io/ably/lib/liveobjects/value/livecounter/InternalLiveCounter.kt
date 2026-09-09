package io.ably.lib.liveobjects.value.livecounter

import io.ably.lib.liveobjects.*
import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.instance.DefaultInstanceSubscriptionEvent
import io.ably.lib.liveobjects.instance.InstanceListener
import io.ably.lib.liveobjects.instance.types.DefaultLiveCounterInstance
import io.ably.lib.liveobjects.invalidInputError
import io.ably.lib.liveobjects.message.*
import io.ably.lib.liveobjects.value.BaseRealtimeObject
import io.ably.lib.liveobjects.value.ObjectType
import io.ably.lib.liveobjects.value.ObjectUpdate
import java.util.concurrent.atomic.AtomicReference

/**
 * @spec RTLC1/RTLC2 - LiveCounter implementation extends BaseRealtimeObject
 */
internal class InternalLiveCounter private constructor(
  objectId: String,
  realtimeObject: DefaultRealtimeObject,
) : BaseRealtimeObject(objectId, ObjectType.Counter, realtimeObject) {

  override val tag = "LiveCounter"

  /**
   * Thread-safe reference to hold the counter data value.
   * Accessed from public API for LiveCounter and updated by LiveCounterManager.
   */
  internal val data = AtomicReference<Double>(0.0) // RTLC3

  /**
   * liveCounterManager instance for managing LiveCounter operations
   */
  private val liveCounterManager = LiveCounterManager(this)

  internal suspend fun increment(amount: Number) = incrementAsync(amount.toDouble())

  // RTLC13c - negating the amount lets increment's validation and publish path cover decrement
  internal suspend fun decrement(amount: Number) = incrementAsync(-amount.toDouble())

  internal fun value(): Double {
    return data.get()
  }

  /** Identity-based subscription to this counter's updates. Spec: RTINS16d, RTLO4b */
  internal fun subscribe(listener: InstanceListener): Subscription {
    return liveCounterManager.subscribe(listener)
  }

  override fun validate(state: WireObjectState) = liveCounterManager.validate(state)

  private suspend fun incrementAsync(amount: Double) {
    // RTLC12e1 - Validate input parameter
    if (amount.isNaN() || amount.isInfinite()) {
      throw invalidInputError("Counter value increment should be a valid number")
    }

    // RTLC12e2, RTLC12e3, RTLC12e5 - Create ObjectMessage with the COUNTER_INC operation
    val msg = WireObjectMessage(
      operation = WireObjectOperation(
        action = WireObjectOperationAction.CounterInc,
        objectId = objectId,
        counterInc = WireCounterInc(number = amount)
      )
    )

    // RTLC12g - publish and apply locally on ACK
    realtimeObject.publishAndApply(arrayOf(msg))
  }

  override fun applyObjectState(wireObjectState: WireObjectState, message: WireObjectMessage): ObjectUpdate {
    return liveCounterManager.applyState(wireObjectState, message)
  }

  override fun applyObjectOperation(operation: WireObjectOperation, message: WireObjectMessage): ObjectUpdate {
    return liveCounterManager.applyOperation(operation, message)
  }

  override fun clearData(): ObjectUpdate {
    return liveCounterManager.calculateUpdateFromDataDiff(data.get(), 0.0).apply { this@InternalLiveCounter.data.set(0.0) }
  }

  override fun notifyInstanceSubscriptions(update: ObjectUpdate, message: ObjectMessage?) {
    // RTINS16e1, RTINS16e2 - the event wraps a fresh instance bound to this counter (the spec
    // requires "an Instance wrapping the underlying LiveObject", not a specific wrapper identity)
    liveCounterManager.notify(
      DefaultInstanceSubscriptionEvent(DefaultLiveCounterInstance(realtimeObject, this), message)
    )
  }

  override fun deregisterInstanceListeners() = liveCounterManager.offAll() // RTLO4b4c3c

  override fun onGCInterval(gcGracePeriod: Long) {
    // Nothing to GC for a counter object
    return
  }

  companion object {
    /**
     * Creates a zero-value counter object.
     * @spec RTLC4 - Returns LiveCounter with 0 value
     */
    internal fun zeroValue(objectId: String, realtimeObjects: DefaultRealtimeObject): InternalLiveCounter {
      return InternalLiveCounter(objectId, realtimeObjects)
    }
  }
}
