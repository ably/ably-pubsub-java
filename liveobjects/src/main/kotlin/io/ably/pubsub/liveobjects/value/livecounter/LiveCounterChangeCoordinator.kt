package io.ably.pubsub.liveobjects.value.livecounter

import io.ably.pubsub.liveobjects.Subscription
import io.ably.pubsub.liveobjects.instance.InstanceListener
import io.ably.pubsub.liveobjects.instance.InstanceSubscriptionEvent
import io.ably.pubsub.liveobjects.onceSubscription
import io.ably.pubsub.liveobjects.value.ObjectUpdate
import io.ably.pubsub.util.EventEmitter
import io.ably.pubsub.util.Log

internal val noOpCounterUpdate: ObjectUpdate = ObjectUpdate.NoOp

/**
 * Interface for handling live counter changes by notifying subscribers of updates.
 * Implementations typically propagate updates through event emission to registered listeners.
 */
internal interface HandlesLiveCounterChange {
  /**
   * Notifies all registered listeners about a counter update by propagating the change through the event system.
   * This method is called when counter data changes and triggers the emission of update events to subscribers.
   */
  fun notify(update: LiveCounterChangeEvent)
}

internal interface LiveCounterChangeEvent : InstanceSubscriptionEvent

internal abstract class LiveCounterChangeCoordinator: HandlesLiveCounterChange {
  private val counterChangeEmitter = LiveCounterChangeEmitter()

  fun subscribe(listener: InstanceListener): Subscription {
    counterChangeEmitter.on(listener)
    return onceSubscription {
      counterChangeEmitter.off(listener)
    }
  }

  /** Deregisters all instance listeners - tombstone teardown. Spec: RTLO4b4c3c */
  internal fun offAll() = counterChangeEmitter.off()

  override fun notify(update: LiveCounterChangeEvent) = counterChangeEmitter.emit(update)
}

private class LiveCounterChangeEmitter : EventEmitter<LiveCounterChangeEvent, InstanceListener>() {
  private val tag = "LiveCounterChangeEmitter"

  override fun apply(listener: InstanceListener?, event: LiveCounterChangeEvent?, vararg args: Any?) {
    try {
      event?.let { listener?.onUpdated(it) }
        ?: Log.w(tag, "Null event passed to LiveCounterChange listener callback")
    } catch (t: Throwable) {
      Log.e(tag, "Error occurred while executing listener callback for event: $event", t)
    }
  }
}
