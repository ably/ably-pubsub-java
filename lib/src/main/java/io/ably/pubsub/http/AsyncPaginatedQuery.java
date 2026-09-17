package io.ably.pubsub.http;

import io.ably.pubsub.types.AsyncPaginatedResult;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.Param;

/**
 * An object that encapsulates parameters of a REST query with a paginated response
 *
 * @param <T> the body response type.
 */
public class AsyncPaginatedQuery<T> {
    private final BasePaginatedQuery<T> base;

    /**
     * Construct a PaginatedQuery
     *
     * @param http the httpCore instance
     * @param path the path of the resource being queried
     * @param headers headers to pass into the first and all relative queries
     * @param params params to pass into the initial query
     * @param bodyHandler handler to parse response bodies for first and all relative queries
     */
    public AsyncPaginatedQuery(Http http, String path, Param[] headers, Param[] params, HttpCore.BodyHandler<T> bodyHandler) {
        this(http, path, headers, params, null, bodyHandler);
    }

    /**
     * Construct a PaginatedQuery
     *
     * @param http the http instance
     * @param path the path of the resource being queried
     * @param headers headers to pass into the first and all relative queries
     * @param params params to pass into the initial query
     * @param bodyHandler handler to parse response bodies for first and all relative queries
     */
    public AsyncPaginatedQuery(Http http, String path, Param[] headers, Param[] params, HttpCore.RequestBody requestBody, HttpCore.BodyHandler<T> bodyHandler) {
        base = new BasePaginatedQuery<T>(http, path, headers, params, requestBody, bodyHandler);
    }

    /**
     * Get the result of the first query
     * @param callback On success returns A PaginatedResult<T> giving the
     * first page of results together with any available links to related results pages.
     */
    public void get(Callback<AsyncPaginatedResult<T>> callback) {
        base.get().async(callback);
    }

}
