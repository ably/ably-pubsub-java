package io.ably.pubsub.liveobjects;

import com.google.gson.JsonElement;
import io.ably.pubsub.liveobjects.http.HttpObjectGenerateIdBody;
import io.ably.pubsub.liveobjects.http.HttpObjectGenerateIdResult;
import io.ably.pubsub.liveobjects.http.HttpObjectGetFullResult;
import io.ably.pubsub.liveobjects.http.HttpObjectOperation;
import io.ably.pubsub.liveobjects.http.HttpObjectPublishResult;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.Param;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The entry point to LiveObjects for an HTTP (REST) channel, accessed via
 * {@code io.ably.pubsub.http.Channel#object}.
 *
 * <p>Unlike {@link RealtimeObject}, this API is stateless: every call is an independent request to
 * Ably's objects REST endpoint, and nothing is synchronized or cached locally. It is built into the
 * HTTP client and needs no plugin.
 *
 * <p>Like the rest of the HTTP channel API, each operation comes in two forms: a blocking method
 * that returns the result or throws an {@link AblyException}, and an {@code ...Async} method that
 * reports the result or the error to a {@link Callback}, invoked on a background thread. Errors
 * raised locally (for example invalid input) and errors returned by the server are reported the
 * same way.
 *
 * <p>Implementations are thread-safe.
 *
 * <p>Spec: RSO1, RSL16
 */
public interface HttpObject {

    /**
     * Fetches the channel object in the compact format. Equivalent to {@code getCompact(null)}.
     *
     * <p>Spec: RSO2
     *
     * @return the compact representation; see {@link #getCompact(Param[])}.
     * @throws AblyException if the request fails.
     */
    @Blocking
    @Nullable
    JsonElement getCompact() throws AblyException;

    /**
     * Fetches an object, or a value within it, from the channel in the compact format: the
     * server's compacted representation as JSON, like {@code PathObject#compactJson()} on a
     * realtime channel. A map is a {@link com.google.gson.JsonObject}, and a value is a JSON
     * primitive; a map that references itself appears as {@code {"objectId": ...}} (RSO8).
     *
     * <p>The value is returned as the server sent it, with no further decoding, so a string that
     * looks like Base64 or JSON stays that string (RSO2f). The result is the same whichever
     * protocol is used, except that binary values, which the binary protocol transmits as raw
     * bytes, are returned as Base64 strings, as the JSON protocol transmits them.
     *
     * <p>The {@code objectId} param, if any, selects the object and is sent as a path segment; every
     * other param, such as {@code path}, goes in the query string. The format is set by this method,
     * so a {@code compact} param is replaced.
     *
     * <p>{@code objectId} and {@code path} are sent to the server as given, without validation;
     * an invalid value surfaces as the server's error (RSO2h, RSO6).
     *
     * <p>Spec: RSO2
     *
     * @param params the request params, built with {@link io.ably.pubsub.liveobjects.http.HttpObjectGetParams},
     *               or {@code null} to fetch the channel object.
     * @return the compact representation, or {@code null} if the response has no body.
     * @throws AblyException if the request fails or the response can't be decoded.
     */
    @Blocking
    @Nullable
    JsonElement getCompact(@Nullable Param[] params) throws AblyException;

    /**
     * Asynchronously fetches the channel object in the compact format; see {@link #getCompact()}.
     *
     * <p>Spec: RSO2
     *
     * @param callback receives the result or the error, on a background thread.
     */
    @NonBlocking
    void getCompactAsync(@NotNull Callback<JsonElement> callback);

    /**
     * Asynchronously fetches an object, or a value within it, in the compact format; see
     * {@link #getCompact(Param[])}.
     *
     * <p>Spec: RSO2
     *
     * @param params the request params, built with {@link io.ably.pubsub.liveobjects.http.HttpObjectGetParams},
     *               or {@code null} to fetch the channel object.
     * @param callback receives the result or the error, on a background thread.
     */
    @NonBlocking
    void getCompactAsync(@Nullable Param[] params, @NotNull Callback<JsonElement> callback);

    /**
     * Fetches the channel object in the full format. Equivalent to {@code get(null)}.
     *
     * <p>Spec: RSO2
     *
     * @return the full representation; see {@link #get(Param[])}.
     * @throws AblyException if the request fails.
     */
    @Blocking
    @Nullable
    HttpObjectGetFullResult get() throws AblyException;

    /**
     * Fetches an object, or a value within it, from the channel in the full format (the spec's
     * {@code compact: false}), decoded into typed results: a
     * {@link io.ably.pubsub.liveobjects.http.HttpLiveMap},
     * {@link io.ably.pubsub.liveobjects.http.HttpLiveCounter}, a decoded
     * {@link io.ably.pubsub.liveobjects.http.HttpObjectData} leaf, or, for an object type this
     * library doesn't recognise, a plain {@link io.ably.pubsub.liveobjects.http.HttpLiveObject}
     * (RSO2g).
     *
     * <p>The {@code objectId} param, if any, selects the object and is sent as a path segment; every
     * other param, such as {@code path}, goes in the query string. The format is set by this method,
     * so a {@code compact} param is replaced.
     *
     * <p>{@code objectId} and {@code path} are sent to the server as given, without validation;
     * an invalid value surfaces as the server's error (RSO2h, RSO6).
     *
     * <p>Spec: RSO2
     *
     * @param params the request params, built with {@link io.ably.pubsub.liveobjects.http.HttpObjectGetParams},
     *               or {@code null} to fetch the channel object.
     * @return the decoded result, or {@code null} if the response has no body.
     * @throws AblyException if the request fails or the response can't be decoded.
     */
    @Blocking
    @Nullable
    HttpObjectGetFullResult get(@Nullable Param[] params) throws AblyException;

    /**
     * Asynchronously fetches the channel object in the full format; see {@link #get()}.
     *
     * <p>Spec: RSO2
     *
     * @param callback receives the result or the error, on a background thread.
     */
    @NonBlocking
    void getAsync(@NotNull Callback<HttpObjectGetFullResult> callback);

    /**
     * Asynchronously fetches an object, or a value within it, in the full format; see
     * {@link #get(Param[])}.
     *
     * <p>Spec: RSO2
     *
     * @param params the request params, built with {@link io.ably.pubsub.liveobjects.http.HttpObjectGetParams},
     *               or {@code null} to fetch the channel object.
     * @param callback receives the result or the error, on a background thread.
     */
    @NonBlocking
    void getAsync(@Nullable Param[] params, @NotNull Callback<HttpObjectGetFullResult> callback);

    /**
     * Publishes a single operation. Equivalent to publishing a one-element list.
     *
     * <p>Spec: RSO3
     *
     * @param operation the operation to publish.
     * @return the {@link HttpObjectPublishResult}.
     * @throws AblyException if the operation is invalid or the request fails.
     */
    @Blocking
    @NotNull
    HttpObjectPublishResult publish(@NotNull HttpObjectOperation operation) throws AblyException;

    /**
     * Publishes operations in a single request, in the given order.
     *
     * <p>The library does not generate operation ids, even when
     * {@code ClientOptions.idempotentRestPublishing} is enabled (RSO3f); set
     * {@link HttpObjectOperation#withId(String)} for idempotent publishing. The library doesn't
     * validate targeting or the number of operations; the server rejects invalid batches (RSO3g, RSO3h).
     * Invalid input (for example a non-finite number or a null value) fails before any request is sent.
     *
     * <p>A request that fails with a server or connection error is retried on fallback hosts, like
     * other REST requests. The server de-duplicates operations by id, so set ids if a batch must
     * not be applied twice: without them, a batch the first host applied before failing is applied
     * again by the retry.
     *
     * <p>Spec: RSO3
     *
     * @param operations the operations to publish.
     * @return the {@link HttpObjectPublishResult}.
     * @throws AblyException if an operation is invalid or the request fails.
     */
    @Blocking
    @NotNull
    HttpObjectPublishResult publish(@NotNull List<HttpObjectOperation> operations) throws AblyException;

    /**
     * Asynchronously publishes a single operation; see {@link #publish(HttpObjectOperation)}.
     *
     * <p>Spec: RSO3
     *
     * @param operation the operation to publish.
     * @param callback receives the result or the error, on a background thread.
     */
    @NonBlocking
    void publishAsync(@NotNull HttpObjectOperation operation, @NotNull Callback<HttpObjectPublishResult> callback);

    /**
     * Asynchronously publishes operations in a single request; see {@link #publish(List)}.
     *
     * <p>Spec: RSO3
     *
     * @param operations the operations to publish.
     * @param callback receives the result or the error, on a background thread.
     */
    @NonBlocking
    void publishAsync(@NotNull List<HttpObjectOperation> operations, @NotNull Callback<HttpObjectPublishResult> callback);

    /**
     * Generates an object ID for a map or counter create body, so that a batch can reference a new
     * object before the server has created it. Pass the result to
     * {@link HttpObjectOperation#mapCreateWithObjectId(HttpObjectGenerateIdResult)} or
     * {@link HttpObjectOperation#counterCreateWithObjectId(HttpObjectGenerateIdResult)}.
     *
     * <p>Nothing is published. The ID embeds the current server time, which is fetched from Ably the
     * first time this client needs it (RTO16); later calls don't go to the network.
     *
     * <p>Spec: RSO4
     *
     * @param createBody the map or counter create body the ID is derived from.
     * @return the generated {@link HttpObjectGenerateIdResult}.
     * @throws AblyException if the body is invalid or the server time can't be retrieved.
     */
    @Blocking
    @NotNull
    HttpObjectGenerateIdResult generateObjectId(@NotNull HttpObjectGenerateIdBody createBody) throws AblyException;

    /**
     * Asynchronously generates an object ID; see {@link #generateObjectId(HttpObjectGenerateIdBody)}.
     *
     * <p>Spec: RSO4
     *
     * @param createBody the map or counter create body the ID is derived from.
     * @param callback receives the result or the error, on a background thread.
     */
    @NonBlocking
    void generateObjectIdAsync(@NotNull HttpObjectGenerateIdBody createBody, @NotNull Callback<HttpObjectGenerateIdResult> callback);
}
