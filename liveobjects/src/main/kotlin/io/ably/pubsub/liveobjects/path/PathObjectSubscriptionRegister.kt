package io.ably.pubsub.liveobjects.path

import io.ably.pubsub.liveobjects.DefaultRealtimeObject
import io.ably.pubsub.liveobjects.Subscription
import io.ably.pubsub.liveobjects.message.ObjectMessage
import io.ably.pubsub.liveobjects.onceSubscription
import io.ably.pubsub.util.EventEmitter
import io.ably.pubsub.util.Log

/**
 * Registry for PathObject subscriptions and path-event dispatch. One per RealtimeObject.
 *
 * Built on [EventEmitter] — the SDK's thread-safe registration/dispatch primitive, already used
 * by the LiveMap/LiveCounter change coordinators. The two type parameters carry the split that makes
 * path dispatch fit a uniform emitter: [PathEvent] is the broadcast payload (same for all listeners),
 * [PathSubscription] is the per-subscription state, and [PathEventEmitter.apply] resolves each listener's
 * own covered path. Subscriptions may be added/removed from any thread; dispatch happens on the sequential
 * scope via LiveObject notifications.
 *
 * Spec: RTO24, RTO24a
 */
internal class PathObjectSubscriptionRegister(channelObject: DefaultRealtimeObject) {

  private val emitter = PathEventEmitter(channelObject)

  /** Registers a subscription for [segments]. Spec: RTPO19f */
  internal fun subscribe(segments: List<String>, listener: PathObjectListener, depth: Int?): Subscription {
    val subscription = PathSubscription(listener, depth, segments.toList())
    emitter.on(subscription)
    return onceSubscription { emitter.off(subscription) }
  }

  /**
   * Dispatches one path event: each subscription covering any candidate path is notified at
   * most once, at the first (most-preferred) covered candidate. Per-listener coverage,
   * dispatch and error handling happen in [PathEventEmitter.apply].
   *
   * Spec: RTO24b2b
   */
  internal fun notifyPathEvent(candidatePaths: List<List<String>>, message: ObjectMessage?) {
    emitter.emit(PathEvent(candidatePaths, message))
  }

  /** Drops all subscriptions; called when the owning RealtimeObject is disposed. */
  internal fun dispose() = emitter.off()
}

/** Broadcast payload for one path notification; candidatePaths ordered most-preferred-first (RTO24b2b). */
private class PathEvent(
  val candidatePaths: List<List<String>>,
  val message: ObjectMessage?,
)

/**
 * A single registration; the EventEmitter `Listener` plus its coverage state. Must stay a plain
 * class (identity equality) — a data class would let on() dedup and off() remove the wrong one.
 */
private class PathSubscription(
  val listener: PathObjectListener,
  val depth: Int?, // RTPO19c1 - null = infinite depth
  val segments: List<String>, // copied at subscribe time
)

/** Coverage-based, per-listener path dispatch. */
private class PathEventEmitter(
  private val channelObject: DefaultRealtimeObject,
) : EventEmitter<PathEvent, PathSubscription>() {

  private val tag = "PathObjectSubscriptionRegister"

  override fun apply(listener: PathSubscription?, event: PathEvent?, vararg args: Any?) {
    if (listener == null || event == null) {
      Log.w(tag, "Null listener or event passed to PathObject subscription dispatch")
      return
    }
    // first (most-preferred) covered candidate, or skip this subscription - RTO24b2b
    val chosen = event.candidatePaths.firstOrNull { covers(listener, it) } ?: return
    try {
      listener.listener.onUpdated(
        DefaultPathObjectSubscriptionEvent(
          DefaultPathObject(channelObject, PathSegments.join(chosen)), // RTO24b2b1 / RTPO19e1
          event.message, // RTO24b2b2 / RTPO19e2
        )
      )
    } catch (t: Throwable) {
      Log.e(tag, "Error in PathObject subscription listener; path=$chosen", t) // RTO24b2c
    }
  }

  /**
   * A subscription covers [eventPath] iff its path is a prefix of it (exact match included)
   * and the relative depth is within the subscription's depth window.
   *
   * Spec: RTO24c1 (worked examples RTO24c2)
   */
  private fun covers(entry: PathSubscription, eventPath: List<String>): Boolean {
    val subPath = entry.segments
    if (subPath.size > eventPath.size) return false
    for (i in subPath.indices) {
      if (eventPath[i] != subPath[i]) return false
    }
    val depth = entry.depth ?: return true // null = infinite depth
    return eventPath.size - subPath.size + 1 <= depth
  }
}
