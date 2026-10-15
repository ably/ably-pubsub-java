package io.ably.pubsub.types;

import com.google.gson.JsonElement;

public abstract class AsyncHttpPaginatedResponse {
    public boolean success;
    public int statusCode;
    public int errorCode;
    public String errorMessage;
    public Param[] headers;

    /**
     * Get the contents as an array of component type
     */
    public abstract JsonElement[] items();

    /**
     * Obtain params required to perform the given relative query
     */
    public abstract void first(Callback callback);
    public abstract void current(Callback callback);
    public abstract void next(Callback callback);

    public abstract boolean hasFirst();
    public abstract boolean hasCurrent();
    public abstract boolean hasNext();

    /**
     * An interface allowing a client to be notified of the outcome
     * of an asynchronous operation.
     */
    public interface Callback {
        /**
         * Called when the associated request completes with an Http response,
         */
        void onResponse(AsyncHttpPaginatedResponse response);

        /**
         * Called when the associated operation completes with an error.
         * @param reason information about the error.
         */
        void onError(ErrorInfo reason);
    }
}
