package io.ably.lib.realtime

import com.ably.Subscription
import com.ably.pubsub.RealtimePresence
import com.ably.query.OrderBy
import io.ably.lib.buildHistoryParams
import io.ably.lib.types.*
import java.util.*

internal class RealtimePresenceAdapter(private val javaPresence: Presence) : RealtimePresence {
  override fun get(clientId: String?, connectionId: String?, waitForSync: Boolean): List<PresenceMessage> {
    val params = buildList {
      clientId?.let { add(Param(Presence.GET_CLIENTID, it)) }
      connectionId?.let { add(Param(Presence.GET_CONNECTIONID, it)) }
      add(Param(Presence.GET_WAITFORSYNC, waitForSync))
    }
    return javaPresence.get(*params.toTypedArray()).toList()
  }

  override fun subscribe(listener: Presence.PresenceListener): Subscription {
    javaPresence.subscribe(listener)
    return Subscription {
      javaPresence.unsubscribe(listener)
    }
  }

  override fun subscribe(
    action: PresenceMessage.Action,
    listener: Presence.PresenceListener,
  ): Subscription {
    javaPresence.subscribe(action, listener)
    return Subscription {
      javaPresence.unsubscribe(action, listener)
    }
  }

  override fun subscribe(
    actions: EnumSet<PresenceMessage.Action>,
    listener: Presence.PresenceListener,
  ): Subscription {
    javaPresence.subscribe(actions, listener)
    return Subscription {
      javaPresence.unsubscribe(actions, listener)
    }
  }

  override fun enter(data: Any?, extras: MessageExtras?, listener: CompletionListener?) =
    javaPresence.enter(data, extras, listener)

  override fun update(data: Any?, extras: MessageExtras?, listener: CompletionListener?) =
    javaPresence.update(data, extras, listener)

  override fun leave(data: Any?, extras: MessageExtras?, listener: CompletionListener?) =
    javaPresence.leave(data, extras, listener)

  override fun enterClient(clientId: String, data: Any?, extras: MessageExtras?, listener: CompletionListener?) =
    javaPresence.enterClient(clientId, data, extras, listener)

  override fun updateClient(clientId: String, data: Any?, extras: MessageExtras?, listener: CompletionListener?) =
    javaPresence.updateClient(clientId, data, extras, listener)

  override fun leaveClient(clientId: String?, data: Any?, extras: MessageExtras?, listener: CompletionListener?) =
    javaPresence.leaveClient(clientId, data, extras, listener)

  override fun history(start: Long?, end: Long?, limit: Int, orderBy: OrderBy): PaginatedResult<PresenceMessage> =
    javaPresence.history(buildHistoryParams(start, end, limit, orderBy).toTypedArray())

  override fun historyAsync(
    callback: Callback<AsyncPaginatedResult<PresenceMessage>>,
    start: Long?,
    end: Long?,
    limit: Int,
    orderBy: OrderBy,
  ) =
    javaPresence.historyAsync(buildHistoryParams(start, end, limit, orderBy).toTypedArray(), callback)
}
