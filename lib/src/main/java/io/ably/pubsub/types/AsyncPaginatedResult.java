package io.ably.pubsub.types;

/**
 * A type that represents a page of results from a paginated query.
 * The response is accompanied by metadata that indicates the relative
 * queries available.
 *
 * @param <T>
 */
public interface AsyncPaginatedResult<T> {

    /**
     * Contains the current page of results; for example, an array of {@link Message} or {@link PresenceMessage}
     * objects for a channel history request.
     * <p>
     * Spec: TG3
     */
    T[] items();

    /**
     * Returns a new PaginatedResult for the first page of results.
     * <p>
     * Spec: TG5
     */
    void first(Callback<AsyncPaginatedResult<T>> callback);
    /**
     * Returns a new PaginatedResult for the current page of results.
     * <p>
     * Spec: TG5
     */
    void current(Callback<AsyncPaginatedResult<T>> callback);
    /**
     * Returns a new PaginatedResult loaded with the next page of results.
     * If there are no further pages, then null is returned.
     * <p>
     * Spec: TG4
     * @return A page of results for message and presence history, stats, and REST presence requests.
     */
    void next(Callback<AsyncPaginatedResult<T>> callback);

    boolean hasFirst();
    boolean hasCurrent();

    /**
     * Returns true if there are more pages available by calling next and returns false if this page is the last page available.
     * <p>
     * Spec: TG6
     * @return Whether or not there are more pages of results.
     */
    boolean hasNext();
}
