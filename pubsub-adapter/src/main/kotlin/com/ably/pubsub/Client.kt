package com.ably.pubsub

import com.ably.query.OrderBy
import com.ably.query.TimeUnit
import com.ably.http.HttpMethod
import io.ably.pubsub.http.HttpCore
import io.ably.pubsub.push.Push
import io.ably.pubsub.http.Auth
import io.ably.pubsub.types.*

/**
 * A client that offers a base interface to interact with Ably's API.
 *
 * This class implements {@link AutoCloseable} so you can use it in
 * try-with-resources constructs and have the JDK close it for you.
 */
public interface Client : AutoCloseable {

  /**
   * An [Auth] object.
   *
   * Spec: RSC5
   */
  public val auth: Auth

  /**
   * A [Channels] object.
   *
   * Spec: RTC3, RTS1
   */
  public val channels: Channels<out Channel>

  /**
   * Client options
   */
  public val options: ClientOptions

  /**
   * An [Push] object.
   *
   * Spec: RSH7
   */
  public val push: Push

  /**
   * Retrieves the time from the Ably service as milliseconds
   * since the Unix epoch. Clients that do not have access
   * to a sufficiently well maintained time source and wish
   * to issue Ably [Auth.TokenRequest] with
   * a more accurate timestamp should use the
   * [ClientOptions.queryTime] property instead of this method.
   * <p>
   * Spec: RSC16
   * @return The time as milliseconds since the Unix epoch.
   */
  public fun time(): Long

  /**
   * Asynchronously retrieves the time from the Ably service as milliseconds
   * since the Unix epoch. Clients that do not have access
   * to a sufficiently well maintained time source and wish
   * to issue Ably [Auth.TokenRequest] with
   * a more accurate timestamp should use the
   * [ClientOptions.queryTime] property instead of this method.
   *
   * Spec: RSC16
   *
   * @param callback Listener with the time as milliseconds since the Unix epoch.
   * This callback is invoked on a background thread
   */
  public fun timeAsync(callback: Callback<Long>)

  /**
   * Queries the REST /stats API and retrieves your application's usage statistics.
   * @param start (RSC6b1) - The time from which stats are retrieved, specified as milliseconds since the Unix epoch.
   * @param end (RSC6b1) - The time until stats are retrieved, specified as milliseconds since the Unix epoch.
   * @param orderBy (RSC6b2) - The order for which stats are returned in.
   * @param limit (RSC6b3) - An upper limit on the number of stats returned. The default is 100, and the maximum is 1000.
   * @param unit (RSC6b4) - minute, hour, day or month. Based on the unit selected, the given start or end times are rounded down to the start of the relevant interval depending on the unit granularity of the query.
   *
   * Spec: RSC6a
   *
   * @return A [PaginatedResult] object containing an array of [Stats] objects.
   * @throws AblyException
   */
  public fun stats(
    start: Long? = null,
    end: Long? = null,
    limit: Int = 100,
    orderBy: OrderBy = OrderBy.NewestFirst,
    unit: TimeUnit = TimeUnit.Minute,
  ): PaginatedResult<Stats>

  /**
   * Asynchronously queries the REST /stats API and retrieves your application's usage statistics.
   *
   *  @param start (RSC6b1) - The time from which stats are retrieved, specified as milliseconds since the Unix epoch.
   * @param end (RSC6b1) - The time until stats are retrieved, specified as milliseconds since the Unix epoch.
   * @param orderBy (RSC6b2) - The order for which stats are returned in.
   * @param limit (RSC6b3) - An upper limit on the number of stats returned. The default is 100, and the maximum is 1000.
   * @param unit (RSC6b4) - minute, hour, day or month. Based on the unit selected, the given start or end times are rounded down to the start of the relevant interval depending on the unit granularity of the query.
   *
   * Spec: RSC6a
   *
   * @param callback Listener which returns a [AsyncPaginatedResult] object containing an array of [Stats] objects.
   * This callback is invoked on a background thread
   */
  public fun statsAsync(
    callback: Callback<AsyncPaginatedResult<Stats>>,
    start: Long? = null,
    end: Long? = null,
    limit: Int = 100,
    orderBy: OrderBy = OrderBy.NewestFirst,
    unit: TimeUnit = TimeUnit.Minute,
  )

  /**
   * Makes a REST request to a provided path. This is provided as a convenience
   * for developers who wish to use REST API functionality that is either not
   * documented or is not yet included in the public API, without having to
   * directly handle features such as authentication, paging, fallback hosts,
   * MsgPack and JSON support.
   *
   * Spec: RSC19
   *
   * @param method The request method to use, such as GET, POST.
   * @param path The request path.
   * @param params The parameters to include in the URL query of the request.
   * The parameters depend on the endpoint being queried.
   * See the [REST API reference](https://ably.com/docs/api/rest-api)
   * for the available parameters of each endpoint.
   * @param body The RequestBody of the request.
   * @param headers Additional HTTP headers to include in the request.
   * @return An [HttpPaginatedResponse] object returned by the HTTP request, containing an empty or JSON-encodable object.
   */
  public fun request(
    path: String,
    method: HttpMethod = HttpMethod.Get,
    params: List<Param> = emptyList(),
    body: HttpCore.RequestBody? = null,
    headers: List<Param> = emptyList(),
  ): HttpPaginatedResponse

  /**
   * Makes a async REST request to a provided path. This is provided as a convenience
   * for developers who wish to use REST API functionality that is either not
   * documented or is not yet included in the public API, without having to
   * directly handle features such as authentication, paging, fallback hosts,
   * MsgPack and JSON support.
   *
   * Spec: RSC19
   *
   * @param method The request method to use, such as GET, POST.
   * @param path The request path.
   * @param params The parameters to include in the URL query of the request.
   * The parameters depend on the endpoint being queried.
   * See the [REST API reference](https://ably.com/docs/api/rest-api)
   * for the available parameters of each endpoint.
   * @param body The RequestBody of the request.
   * @param headers Additional HTTP headers to include in the request.
   * @param callback called with the asynchronous result,
   * returns an [AsyncHttpPaginatedResponse] object returned by the HTTP request,
   * containing an empty or JSON-encodable object.
   * This callback is invoked on a background thread
   */
  public fun requestAsync(
    path: String,
    callback: AsyncHttpPaginatedResponse.Callback,
    method: HttpMethod = HttpMethod.Get,
    params: List<Param> = emptyList(),
    body: HttpCore.RequestBody? = null,
    headers: List<Param> = emptyList(),
  )
}
