package io.ably.pubsub.realtime

import com.ably.annotations.InternalAPI
import com.ably.http.HttpMethod
import com.ably.pubsub.*
import com.ably.pubsub.RealtimeClient
import io.ably.pubsub.realtime.RealtimeClient as CoreRealtimeClient
import com.ably.query.OrderBy
import com.ably.query.TimeUnit
import io.ably.pubsub.buildHistoryParams
import io.ably.pubsub.buildStatsParams
import io.ably.pubsub.http.Http
import io.ably.pubsub.http.HttpCore
import io.ably.pubsub.http.*
import io.ably.pubsub.types.*

@OptIn(InternalAPI::class)
internal class WrapperRealtimeClient(
  override val javaClient: CoreRealtimeClient,
  private val adapter: RealtimeClientAdapter,
  private val httpModule: Http,
  private val agents: Map<String, String>,
) : SdkWrapperCompatible<RealtimeClient>, RealtimeClient by adapter {

  override val channels: Channels<out RealtimeChannel>
    get() = WrapperRealtimeChannels(javaClient.channels, httpModule, agents)

  override fun createWrapperSdkProxy(options: WrapperSdkProxyOptions): RealtimeClient =
    adapter.createWrapperSdkProxy(options.copy(agents = options.agents + agents))

  override fun time(): Long = javaClient.time(httpModule)

  override fun timeAsync(callback: Callback<Long>) = javaClient.timeAsync(httpModule, callback)

  override fun stats(
    start: Long?,
    end: Long?,
    limit: Int,
    orderBy: OrderBy,
    unit: TimeUnit
  ): PaginatedResult<Stats> =
    javaClient.stats(httpModule, buildStatsParams(start, end, limit, orderBy, unit).toTypedArray())

  override fun statsAsync(
    callback: Callback<AsyncPaginatedResult<Stats>>,
    start: Long?,
    end: Long?,
    limit: Int,
    orderBy: OrderBy,
    unit: TimeUnit
  ) = javaClient.statsAsync(httpModule, buildStatsParams(start, end, limit, orderBy, unit).toTypedArray(), callback)

  override fun request(
    path: String,
    method: HttpMethod,
    params: List<Param>,
    body: HttpCore.RequestBody?,
    headers: List<Param>,
  ) = javaClient.request(httpModule, method.toString(), path, params.toTypedArray(), body, headers.toTypedArray())!!

  override fun requestAsync(
    path: String,
    callback: AsyncHttpPaginatedResponse.Callback,
    method: HttpMethod,
    params: List<Param>,
    body: HttpCore.RequestBody?,
    headers: List<Param>,
  ) = javaClient.requestAsync(
    httpModule,
    method.toString(),
    path,
    params.toTypedArray(),
    body,
    headers.toTypedArray(),
    callback
  )
}

internal class WrapperRealtimeChannels(
  private val javaChannels: CoreRealtimeClient.Channels,
  private val httpModule: Http,
  private val agents: Map<String, String>,
) :
  Channels<RealtimeChannel> by RealtimeChannelsAdapter(javaChannels) {

  override fun get(name: String): RealtimeChannel {
    if (javaChannels.containsKey(name)) return WrapperRealtimeChannel(javaChannels.get(name), httpModule)
    return try {
      WrapperRealtimeChannel(javaChannels.get(name, ChannelOptions().injectAgents(agents)), httpModule)
    } catch (e: AblyException) {
      WrapperRealtimeChannel(javaChannels.get(name), httpModule)
    }

  }

  override fun get(name: String, options: ChannelOptions): RealtimeChannel =
    WrapperRealtimeChannel(javaChannels.get(name, options.injectAgents(agents)), httpModule)
}

@OptIn(InternalAPI::class)
internal class WrapperRealtimeChannel(override val javaChannel: Channel, private val httpModule: Http) :
  RealtimeChannel by RealtimeChannelAdapter(javaChannel) {

  override val presence: RealtimePresence
    get() = WrapperRealtimePresence(javaChannel.presence, httpModule)

  override fun history(start: Long?, end: Long?, limit: Int, orderBy: OrderBy): PaginatedResult<Message> =
    javaChannel.history(httpModule, buildHistoryParams(start, end, limit, orderBy).toTypedArray())

  override fun historyAsync(
    callback: Callback<AsyncPaginatedResult<Message>>,
    start: Long?,
    end: Long?,
    limit: Int,
    orderBy: OrderBy,
  ) =
    javaChannel.historyAsync(httpModule, buildHistoryParams(start, end, limit, orderBy).toTypedArray(), callback)
}

internal class WrapperRealtimePresence(private val javaPresence: Presence, private val httpModule: Http) :
  RealtimePresence by RealtimePresenceAdapter(javaPresence) {

  override fun history(start: Long?, end: Long?, limit: Int, orderBy: OrderBy): PaginatedResult<PresenceMessage> =
    javaPresence.history(httpModule, buildHistoryParams(start, end, limit, orderBy).toTypedArray())

  override fun historyAsync(
    callback: Callback<AsyncPaginatedResult<PresenceMessage>>,
    start: Long?,
    end: Long?,
    limit: Int,
    orderBy: OrderBy,
  ) =
    javaPresence.historyAsync(httpModule, buildHistoryParams(start, end, limit, orderBy).toTypedArray(), callback)
}

private fun ChannelOptions.injectAgents(agents: Map<String, String>): ChannelOptions {
  val options = ChannelOptions()
  options.params = (this.params ?: mapOf()) + mapOf(
    "agent" to agents.map { "${it.key}/${it.value}" }.joinToString(" "),
  )
  options.modes = modes
  options.cipherParams = cipherParams
  options.attachOnSubscribe = attachOnSubscribe
  options.encrypted = encrypted
  return options
}
