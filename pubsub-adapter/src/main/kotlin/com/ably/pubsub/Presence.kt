package com.ably.pubsub

import com.ably.query.OrderBy
import io.ably.pubsub.types.*

/**
 * Enables get historic presence set for a channel.
 */
public interface Presence {

  /**
   * Retrieves a [PaginatedResult] object, containing an array of historical [PresenceMessage] objects for the channel.
   * If the channel is configured to persist messages,
   * then presence messages can be retrieved from history for up to 72 hours in the past.
   * If not, presence messages can only be retrieved from history for up to two minutes in the past.
   *
   * Spec: RSP4a
   *
   * @param start (RSP4b1) - The time from which messages are retrieved, specified as milliseconds since the Unix epoch.
   * @param end (RSP4b1) - The time until messages are retrieved, specified as milliseconds since the Unix epoch.
   * @param orderBy (RSP4b2) - The order for which messages are returned in.
   * @param limit (RSP4b3) - An upper limit on the number of messages returned. The default is 100, and the maximum is 1000.
   *
   * @return A [PaginatedResult] object containing an array of [PresenceMessage] objects.
   */
  public fun history(
    start: Long? = null,
    end: Long? = null,
    limit: Int = 100,
    orderBy: OrderBy = OrderBy.NewestFirst,
  ): PaginatedResult<PresenceMessage>

  /**
   * Asynchronously retrieves a [PaginatedResult] object, containing an array of historical [PresenceMessage] objects for the channel.
   * If the channel is configured to persist messages,
   * then presence messages can be retrieved from history for up to 72 hours in the past.
   * If not, presence messages can only be retrieved from history for up to two minutes in the past.
   *
   * Spec: RSP4a
   *
   * @param start (RSP4b1) - The time from which messages are retrieved, specified as milliseconds since the Unix epoch.
   * @param end (RSP4b1) - The time until messages are retrieved, specified as milliseconds since the Unix epoch.
   * @param orderBy (RSP4b2) - The order for which messages are returned in.
   * @param limit (RSP4b3) - An upper limit on the number of messages returned. The default is 100, and the maximum is 1000.
   * @param callback  A Callback returning [AsyncPaginatedResult] object containing an array of [PresenceMessage] objects.
   * Note: This callback is invoked on a background thread.
   */
  public fun historyAsync(
    callback: Callback<AsyncPaginatedResult<PresenceMessage>>,
    start: Long? = null,
    end: Long? = null,
    limit: Int = 100,
    orderBy: OrderBy = OrderBy.NewestFirst,
  )
}
