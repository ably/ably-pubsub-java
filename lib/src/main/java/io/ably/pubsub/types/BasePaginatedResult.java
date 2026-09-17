package io.ably.pubsub.types;

import io.ably.pubsub.http.Http;

/**
 * A type that represents a page of results from a paginated query.
 * The response is accompanied by metadata that indicates the relative
 * queries available.
 *
 * It works for both sync and async requests. Typically, one of the two options are chosen by
 * wrapping this class to offer a PaginatedResult or AsyncPaginatedResult.
 *
 * @param <T>
 */
public interface BasePaginatedResult<T> {
    /**
     * Get the contents as an array of component type
     */
    T[] items();

    /**
     * Perform the given relative query
     */
    Http.Request<BasePaginatedResult<T>> first();
    Http.Request<BasePaginatedResult<T>> current();
    Http.Request<BasePaginatedResult<T>> next();

    boolean hasFirst();
    boolean hasCurrent();
    boolean hasNext();

    boolean isLast();
}
