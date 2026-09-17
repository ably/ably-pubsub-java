package io.ably.pubsub.liveobjects.path

import io.ably.pubsub.liveobjects.message.ObjectMessage

/**
 * Default implementation of [PathObjectSubscriptionEvent], the event delivered to a
 * [PathObjectListener] when a change affects the subscribed path. A plain holder for the
 * changed [PathObject] and the source [ObjectMessage] (if any).
 *
 * Spec: RTPO19e / RTTS3d
 */
internal class DefaultPathObjectSubscriptionEvent(
  private val pathObject: PathObject,
  private val message: ObjectMessage?,
) : PathObjectSubscriptionEvent {

  override fun getObject(): PathObject = pathObject

  override fun getMessage(): ObjectMessage? = message
}
