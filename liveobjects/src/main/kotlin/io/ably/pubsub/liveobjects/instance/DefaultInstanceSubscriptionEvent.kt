package io.ably.pubsub.liveobjects.instance

import io.ably.pubsub.liveobjects.message.ObjectMessage
import io.ably.pubsub.liveobjects.value.livecounter.LiveCounterChangeEvent
import io.ably.pubsub.liveobjects.value.livemap.LiveMapChangeEvent

/**
 * Default implementation of [InstanceSubscriptionEvent], the event delivered to an
 * [InstanceListener] when the wrapped LiveObject is updated. A plain holder for the updated
 * [Instance] and the source [ObjectMessage] (if any).
 *
 * Implements both internal change-event markers so the map/counter change emitters can carry
 * it without casts (the markers exist only to type the two emitters).
 *
 * Spec: RTINS16e
 */
internal class DefaultInstanceSubscriptionEvent(
  private val instance: Instance,
  private val message: ObjectMessage?,
) : InstanceSubscriptionEvent, LiveMapChangeEvent, LiveCounterChangeEvent {

  override fun getObject(): Instance = instance

  override fun getMessage(): ObjectMessage? = message
}
