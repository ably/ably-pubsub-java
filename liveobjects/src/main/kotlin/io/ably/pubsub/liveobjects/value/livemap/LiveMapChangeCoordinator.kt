package io.ably.pubsub.liveobjects.value.livemap

import io.ably.pubsub.liveobjects.Subscription
import io.ably.pubsub.liveobjects.instance.InstanceListener
import io.ably.pubsub.liveobjects.instance.InstanceSubscriptionEvent
import io.ably.pubsub.liveobjects.onceSubscription
import io.ably.pubsub.liveobjects.value.ObjectUpdate
import io.ably.pubsub.util.EventEmitter
import io.ably.pubsub.util.Log

internal val noOpMapUpdate: ObjectUpdate = ObjectUpdate.NoOp

/** Change type for a LiveMap key diff. Spec: RTLM18b */
internal enum class MapChange { Updated, Removed }

/**
 * Interface for handling live map changes by notifying subscribers of updates.
 * Implementations typically propagate updates through event emission to registered listeners.
 */
internal interface HandlesLiveMapChange {
  /**
   * Notifies all registered listeners about a map update by propagating the change through the event system.
   * This method is called when map data changes and triggers the emission of update events to subscribers.
   */
  fun notify(update: LiveMapChangeEvent)
}

internal interface LiveMapChangeEvent : InstanceSubscriptionEvent

internal abstract class LiveMapChangeCoordinator: HandlesLiveMapChange {
  private val mapChangeEmitter = LiveMapChangeEmitter()

  fun subscribe(listener: InstanceListener): Subscription {
    mapChangeEmitter.on(listener)
    return onceSubscription {
      mapChangeEmitter.off(listener)
    }
  }

  /** Deregisters all instance listeners - tombstone teardown. Spec: RTLO4b4c3c */
  internal fun offAll() = mapChangeEmitter.off()

  override fun notify(update: LiveMapChangeEvent) = mapChangeEmitter.emit(update)
}

private class LiveMapChangeEmitter : EventEmitter<LiveMapChangeEvent, InstanceListener>() {
  private val tag = "LiveMapChangeEmitter"

  override fun apply(listener: InstanceListener?, event: LiveMapChangeEvent?, vararg args: Any?) {
    try {
      event?.let { listener?.onUpdated(it) }
        ?: Log.w(tag, "Null event passed to LiveMapChange listener callback")
    } catch (t: Throwable) {
      Log.e(tag, "Error occurred while executing listener callback for event: $event", t)
    }
  }
}
