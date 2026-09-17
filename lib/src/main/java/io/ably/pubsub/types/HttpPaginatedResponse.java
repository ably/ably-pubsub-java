package io.ably.pubsub.types;

import com.google.gson.JsonElement;

/**
 * A superset of {@link PaginatedResult} which represents a page of results plus metadata indicating the relative queries available to it.
 * HttpPaginatedResponse additionally carries information about the response to an HTTP request.
 */
public abstract class HttpPaginatedResponse {
    /**
     * Whether statusCode indicates success. This is equivalent to 200 <= statusCode < 300.
     * <p>
     * Spec: HP5
     */
    public boolean success;
    /**
     * The HTTP status code of the response.
     * <p>
     * Spec: HP4
     */
    public int statusCode;
    /**
     * The error code if the X-Ably-Errorcode HTTP header is sent in the response.
     * <p>
     * Spec: HP6
     */
    public int errorCode;
    /**
     * The error message if the X-Ably-Errormessage HTTP header is sent in the response.
     * <p>
     * Spec: HP7
     */
    public String errorMessage;
    /**
     * The headers of the response.
     * <p>
     * Spec: HP8
     */
    public Param[] headers;

    /**
     * Contains a page of results; for example,
     * an array of {@link Message} or {@link PresenceMessage} objects for a channel history request.
     * <p>
     * Spec: HP3
     */
    public abstract JsonElement[] items();

    /**
     * Perform the given relative query
     */
    public abstract HttpPaginatedResponse first() throws AblyException;
    public abstract HttpPaginatedResponse current() throws AblyException;
    public abstract HttpPaginatedResponse next() throws AblyException;

    public abstract boolean hasFirst();
    public abstract boolean hasCurrent();
    public abstract boolean hasNext();
    public abstract boolean isLast();
}
