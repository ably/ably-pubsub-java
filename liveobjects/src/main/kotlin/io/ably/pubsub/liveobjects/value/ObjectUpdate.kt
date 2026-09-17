package io.ably.pubsub.liveobjects.value

import io.ably.pubsub.liveobjects.message.WireObjectMessage
import io.ably.pubsub.liveobjects.value.livemap.MapChange

/**
 * An update emitted for a LiveObject data change. Spec: RTLO4b4 (LiveObjectUpdate)
 * - [objectMessage] - the source wire message that caused the update, if any (RTLO4b4d)
 * - [tombstone] - true when the update results from tombstoning this object (RTLO4b4e)
 */
internal sealed class ObjectUpdate {
  abstract val objectMessage: WireObjectMessage?
  abstract val tombstone: Boolean

  /** RTLO4b4b - no-op update; nothing changed, nothing is emitted. */
  object NoOp : ObjectUpdate() {
    override val objectMessage: WireObjectMessage? get() = null
    override val tombstone: Boolean get() = false
  }

  /** RTLM18 - LiveMapUpdate: per-key diff. Spec: RTLM18a, RTLM18b */
  internal data class MapUpdate(
    val update: kotlin.collections.Map<String, MapChange>, // RTLM18b
    override val objectMessage: WireObjectMessage? = null,
    override val tombstone: Boolean = false,
  ) : ObjectUpdate()

  /** RTLC11 - LiveCounterUpdate: amount delta. Spec: RTLC11a, RTLC11b */
  internal data class CounterUpdate(
    val amount: Double, // RTLC11b1
    override val objectMessage: WireObjectMessage? = null,
    override val tombstone: Boolean = false,
  ) : ObjectUpdate()
}

internal val ObjectUpdate.noOp get() = this is ObjectUpdate.NoOp
