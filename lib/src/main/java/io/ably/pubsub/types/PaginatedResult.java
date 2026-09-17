package io.ably.pubsub.types;

/**
 * Contains a page of results for message or presence history, stats, or REST presence requests.
 * A PaginatedResult response from a REST API paginated query is also accompanied by metadata that indicates
 * the relative queries available to the PaginatedResult object.
 *
 * @param <T>
 */
public interface PaginatedResult<T> {

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
    PaginatedResult<T> first() throws AblyException;
    /**
     * Returns a new PaginatedResult for the current page of results.
     * <p>
     * Spec: TG5
     */
    PaginatedResult<T> current() throws AblyException;
    /**
     * Returns a new PaginatedResult loaded with the next page of results.
     * If there are no further pages, then null is returned.
     * <p>
     * Spec: TG4
     * @return A page of results for message and presence history, stats, and REST presence requests.
     */
    PaginatedResult<T> next() throws AblyException;

    boolean hasFirst();
    boolean hasCurrent();

    /**
     * Returns true if there are more pages available by calling next and returns false if this page is the last page available.
     * <p>
     * Spec: TG6
     * @return Whether or not there are more pages of results.
     */
    boolean hasNext();

    /**
     * Returns true if this page is the last page and returns false if there are more pages available by calling next available.
     * <p>
     * Spec: TG7
     * @return Whether or not this is the last page of results.
     */
    boolean isLast();
}
