package io.ably.pubsub.realtime

import com.ably.Subscription
import com.ably.annotations.InternalAPI
import com.ably.pubsub.RealtimeChannel
import com.ably.pubsub.RealtimePresence
import com.ably.query.OrderBy
import io.ably.pubsub.buildHistoryParams
import io.ably.pubsub.types.*
import io.ably.pubsub.util.Listeners


@OptIn(InternalAPI::class)
internal class RealtimeChannelAdapter(override val javaChannel: Channel) : RealtimeChannel {
  override val name: String
    get() = javaChannel.name
  override val presence: RealtimePresence
    get() = RealtimePresenceAdapter(javaChannel.presence)
  override val state: ChannelState
    get() = javaChannel.state
  override val reason: ErrorInfo
    get() = javaChannel.reason
  override val properties: ChannelProperties
    get() = javaChannel.properties

  override fun attach(listener: CompletionListener?) = javaChannel.attach(listener)

  override fun detach(listener: CompletionListener?) = javaChannel.detach(listener)

  override fun subscribe(listener: ChannelBase.MessageListener): Subscription {
    javaChannel.subscribe(listener)
    return Subscription {
      javaChannel.unsubscribe(listener)
    }
  }

  override fun subscribe(eventName: String, listener: ChannelBase.MessageListener): Subscription {
    javaChannel.subscribe(eventName, listener)
    return Subscription {
      javaChannel.unsubscribe(eventName, listener)
    }
  }

  override fun subscribe(eventNames: List<String>, listener: ChannelBase.MessageListener): Subscription {
    javaChannel.subscribe(eventNames.toTypedArray(), listener)
    return Subscription {
      javaChannel.unsubscribe(eventNames.toTypedArray(), listener)
    }
  }

  /*
   * The core publish overloads report a PublishResult, so a CompletionListener handed to this
   * adapter is wrapped into one; the wrapper drops the result and calls the listener.
   */
  override fun publish(name: String?, data: Any?, listener: CompletionListener?) =
    javaChannel.publish(name, data, Listeners.fromCompletionListener<PublishResult>(listener))

  override fun publish(message: Message, listener: CompletionListener?) =
    javaChannel.publish(message, Listeners.fromCompletionListener<PublishResult>(listener))

  override fun publish(messages: List<Message>, listener: CompletionListener?) =
    javaChannel.publish(messages.toTypedArray(), Listeners.fromCompletionListener<PublishResult>(listener))

  override fun setOptions(options: ChannelOptions) = javaChannel.setOptions(options)

  override fun history(start: Long?, end: Long?, limit: Int, orderBy: OrderBy): PaginatedResult<Message> =
    javaChannel.history(buildHistoryParams(start, end, limit, orderBy).toTypedArray())

  override fun historyAsync(
    callback: Callback<AsyncPaginatedResult<Message>>,
    start: Long?,
    end: Long?,
    limit: Int,
    orderBy: OrderBy,
  ) =
    javaChannel.historyAsync(buildHistoryParams(start, end, limit, orderBy).toTypedArray(), callback)
}
