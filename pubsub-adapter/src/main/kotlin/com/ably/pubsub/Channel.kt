package com.ably.pubsub

import com.ably.query.OrderBy
import io.ably.pubsub.types.*

/**
 * An interface representing a Channel in the Ably API. This serves as the base interface
 * for [RealtimeChannel], providing common channel functionality
 * such as history retrieval and presence management.
 *
 * A channel is the medium through which messages are distributed. Channels can represent
 * different topics, rooms, or contexts in your application.
 *
 * @see <a href="https://ably.com/docs/realtime/channels">Ably Channels Documentation</a>
 */
public interface Channel {

  /**
   * The channel name.
   *
   * Channel names:
   * - Can contain any Unicode characters except colon (':')
   * - Are limited to 250 characters
   * - Are case-sensitive
   *
   * @see <a href="https://ably.com/docs/realtime/channels#channel-naming">Channel Naming Rules</a>
   */
  public val name: String

  /**
   * A [Presence] object.
   *
   * The Presence object enables clients to be notified when other clients enter or leave
   * the channel (presence events) and get the set of current members on the channel
   * (presence state).
   *
   * Common use cases include:
   * - Online status indicators
   * - Typing indicators
   * - User activity tracking
   *
   * Spec: RTL9
   */
  public val presence: Presence

  /**
   * Obtain recent history for this channel using the REST API.
   * The history provided relates to all clients of this application,
   * not just this instance.
   *
   * @param start The start of the query interval as a time in milliseconds since the epoch.
   * A message qualifies as a member of the result set if it was received at or after this time. (default: beginning of time)
   * @param end The end of the query interval as a time in milliseconds since the epoch.
   * A message qualifies as a member of the result set if it was received at or before this time. (default: now)
   * @param limit The maximum number of records to return. A limit greater than 1,000 is invalid.
   * @param orderBy The direction of this query.
   *
   * @return Paginated result of Messages for this Channel.
   */
  public fun history(
    start: Long? = null,
    end: Long? = null,
    limit: Int = 100,
    orderBy: OrderBy = OrderBy.NewestFirst,
  ): PaginatedResult<Message>

  /**
   * Asynchronously obtain recent history for this channel using the REST API.
   *
   * @param start The start of the query interval as a time in milliseconds since the epoch.
   * A message qualifies as a member of the result set if it was received at or after this time. (default: beginning of time)
   * @param end The end of the query interval as a time in milliseconds since the epoch.
   * A message qualifies as a member of the result set if it was received at or before this time. (default: now)
   * @param limit The maximum number of records to return. A limit greater than 1,000 is invalid.
   * @param orderBy The direction of this query.
   * @param callback  A Callback returning [AsyncPaginatedResult] object containing an array of [Message] objects.
   * Note: This callback is invoked on a background thread.
   */
  public fun historyAsync(
    callback: Callback<AsyncPaginatedResult<Message>>,
    start: Long? = null,
    end: Long? = null,
    limit: Int = 100,
    orderBy: OrderBy = OrderBy.NewestFirst,
  )

}
