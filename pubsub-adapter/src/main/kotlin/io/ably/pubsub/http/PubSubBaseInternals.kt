package io.ably.pubsub.http

/*
 * Package-private access bridge for PubSubBase, not part of the HTTP client API.
 *
 * PubSubBase declares time/stats/request as package-private, so WrapperRealtimeClient — which
 * lives in the realtime package — cannot call them directly. These extensions sit in PubSubBase's
 * own package and re-expose exactly those members, behind @InternalAPI so they are not a public
 * contract. This file is why the realtime wrapper compiles; it is unrelated to the HTTP client
 * surface that used to live alongside it.
 */

import com.ably.annotations.InternalAPI
import io.ably.pubsub.types.*

@InternalAPI
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public fun PubSubBase.time(http: Http): Long = time(http)

@InternalAPI
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public fun PubSubBase.timeAsync(http: Http, callback: Callback<Long>): Unit = timeAsync(http, callback)

@InternalAPI
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public fun PubSubBase.stats(http: Http, params: Array<Param>): PaginatedResult<Stats> = stats(http, params)

@InternalAPI
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public fun PubSubBase.statsAsync(http: Http, params: Array<Param>, callback: Callback<AsyncPaginatedResult<Stats>>): Unit =
  this.statsAsync(http, params, callback)

@InternalAPI
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public fun PubSubBase.request(
  http: Http,
  method: String,
  path: String,
  params: Array<Param>?,
  body: HttpCore.RequestBody?,
  headers: Array<Param>?
): HttpPaginatedResponse = this.request(http, method, path, params, body, headers)

@InternalAPI
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public fun PubSubBase.requestAsync(
  http: Http,
  method: String?,
  path: String?,
  params: Array<Param>?,
  body: HttpCore.RequestBody?,
  headers: Array<Param>?,
  callback: AsyncHttpPaginatedResponse.Callback?
): Unit = this.requestAsync(http, method, path, params, body, headers, callback)
