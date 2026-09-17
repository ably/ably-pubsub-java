package io.ably.pubsub.liveobjects.path;

import com.google.gson.JsonElement;
import io.ably.pubsub.liveobjects.ValueType;
import io.ably.pubsub.liveobjects.instance.Instance;
import io.ably.pubsub.liveobjects.path.types.BinaryPathObject;
import io.ably.pubsub.liveobjects.path.types.BooleanPathObject;
import io.ably.pubsub.liveobjects.path.types.JsonArrayPathObject;
import io.ably.pubsub.liveobjects.path.types.JsonObjectPathObject;
import io.ably.pubsub.liveobjects.path.types.LiveCounterPathObject;
import io.ably.pubsub.liveobjects.path.types.LiveMapPathObject;
import io.ably.pubsub.liveobjects.path.types.NumberPathObject;
import io.ably.pubsub.liveobjects.path.types.StringPathObject;
import io.ably.pubsub.liveobjects.Subscription;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A lazy, path-based reference into the LiveObjects graph rooted at the channel's root
 * {@code LiveMap}.
 *
 * <p>A {@code PathObject} stores a path as an ordered list of string segments and
 * resolves it against the local object graph each time a terminal method is called;
 * the freshly resolved value is the sole basis for that call's result. Resolution is
 * best-effort: the value at a path may change between two calls (e.g. between
 * {@link #exists()} and a subsequent write) as updates from other clients are applied.
 *
 * <p>When the path does not resolve, or resolves to a type the called method does not
 * apply to, read operations degrade gracefully - returning {@code null} or an empty
 * result - whereas write operations fail with an {@code AblyException} (code 92005 if
 * the path does not resolve, 92007 on a type mismatch). All terminal operations
 * additionally validate the access/write API preconditions and fail with an
 * {@code AblyException} if those are not satisfied.
 *
 * <p>This base type exposes only the methods whose behaviour is independent of the
 * resolved type; map and counter reads/writes are partitioned onto the sub-types
 * (RTTS3e). Use the {@code as*} helpers to obtain a sub-type view without type
 * validation, e.g. {@code pathObject.asLiveMap().at("a.b.c")} (RTTS3g). The spec's
 * {@code compact} is not exposed; {@link #compactJson()} is the supported equivalent
 * (RTTS3f).
 *
 * <p>Spec: RTPO1, RTPO2, RTTS3
 *
 * @see LiveMapPathObject
 * @see LiveCounterPathObject
 * @see PathObjectListener
 */
public interface PathObject {

    /**
     * Returns the {@link ValueType} of the value currently resolved at this path, or
     * {@code null} when the path does not resolve to any value. Use this instead of
     * dedicated {@code isLiveMap}/{@code isLiveCounter}/etc. checks.
     *
     * <p>A {@code null} result means there is no value at this path - nothing is stored
     * there (e.g. an absent or removed map entry). This is deliberately distinct from
     * {@link ValueType#UNKNOWN}, which is returned only when a value <em>is</em> present
     * but its type matches none of the known categories. In other words: {@code null}
     * means "no value", {@code UNKNOWN} means "a value of an unrecognized type".
     *
     * <p>Spec: RTTS4b
     *
     * @return the resolved value type at this path, or {@code null} if the path does
     *         not resolve to a value
     */
    @Nullable ValueType getType();

    /**
     * Returns a dot-delimited string representation of the stored path segments.
     * Dot characters inside individual segments are escaped with a backslash, so a
     * path with segments {@code ["a", "b.c", "d"]} is represented as {@code "a.b\.c.d"}.
     * An empty path (i.e. the root {@code PathObject}) returns the empty string.
     *
     * <p>Spec: RTPO4 / RTTS3a
     *
     * @return the dot-delimited path from the root to this position
     */
    @NotNull String path();

    /**
     * Resolves this path and returns a {@link Instance} wrapping the resolved value,
     * whether it is a {@code LiveMap}, {@code LiveCounter} or a primitive (RTPO8c/RTPO8f).
     *
     * <p>Returns {@code null} when the path does not resolve (RTPO8e).
     *
     * <p>Spec: RTPO8 / RTTS3b
     *
     * @return a {@link Instance} wrapping the resolved value, or {@code null}
     */
    @Nullable Instance instance();

    /**
     * Returns a JSON-serializable, recursively compacted snapshot of the value at this
     * path. Behaves like the spec's {@code compact} except that {@code Binary} values
     * are base64-encoded and cyclic references are represented as
     * {@code { "objectId": ... }} markers, so the result is safe to serialise as JSON.
     *
     * <p>Returns {@code null} when the path does not resolve.
     *
     * <p>Spec: RTPO14 / RTTS3c
     *
     * @return the compacted JSON snapshot, or {@code null} if the path does not resolve
     */
    @Nullable JsonElement compactJson();

    /**
     * Returns {@code true} if a value currently resolves at this path in the local
     * object graph. This is a best-effort check evaluated at call time; the answer may
     * change immediately afterwards as remote operations are applied. Useful as a
     * guard before performing operations whose semantics depend on existence.
     *
     * <p>Complexity is O(n) in the path length because the path must be resolved.
     *
     * <p>Spec: RTTS4a
     *
     * @return {@code true} if the path resolves to a value, {@code false} otherwise
     */
    boolean exists();

    /**
     * Returns this {@code PathObject} wrapped as a {@link LiveMapPathObject}.
     *
     * <p>This is a best-effort cast - it does not validate that the underlying value
     * at this path is a {@code LiveMap}. Read operations are always permitted on the
     * returned wrapper; write or terminal operations that require resolution will fail
     * at call time if the resolved value is not a {@code LiveMap}.
     *
     * <p>Spec: RTTS5a
     *
     * @return a {@link LiveMapPathObject} view of this path
     */
    @NotNull LiveMapPathObject asLiveMap();

    /**
     * Returns this {@code PathObject} wrapped as a {@link LiveCounterPathObject}.
     * Best-effort cast; does not validate the underlying type at this path.
     *
     * <p>Spec: RTTS5b
     *
     * @return a {@link LiveCounterPathObject} view of this path
     */
    @NotNull LiveCounterPathObject asLiveCounter();

    /**
     * Returns this {@code PathObject} wrapped as a {@link NumberPathObject}.
     * Best-effort cast; does not validate the underlying type at this path.
     *
     * <p>Spec: RTTS5c
     *
     * @return a {@link NumberPathObject} view of this path
     */
    @NotNull NumberPathObject asNumber();

    /**
     * Returns this {@code PathObject} wrapped as a {@link StringPathObject}.
     * Best-effort cast; does not validate the underlying type at this path.
     *
     * <p>Spec: RTTS5c
     *
     * @return a {@link StringPathObject} view of this path
     */
    @NotNull StringPathObject asString();

    /**
     * Returns this {@code PathObject} wrapped as a {@link BooleanPathObject}.
     * Best-effort cast; does not validate the underlying type at this path.
     *
     * <p>Spec: RTTS5c
     *
     * @return a {@link BooleanPathObject} view of this path
     */
    @NotNull BooleanPathObject asBoolean();

    /**
     * Returns this {@code PathObject} wrapped as a {@link BinaryPathObject}.
     * Best-effort cast; does not validate the underlying type at this path.
     *
     * <p>Spec: RTTS5c
     *
     * @return a {@link BinaryPathObject} view of this path
     */
    @NotNull BinaryPathObject asBinary();

    /**
     * Returns this {@code PathObject} wrapped as a {@link JsonObjectPathObject}.
     * Best-effort cast; does not validate the underlying type at this path.
     *
     * <p>Spec: RTTS5c
     *
     * @return a {@link JsonObjectPathObject} view of this path
     */
    @NotNull JsonObjectPathObject asJsonObject();

    /**
     * Returns this {@code PathObject} wrapped as a {@link JsonArrayPathObject}.
     * Best-effort cast; does not validate the underlying type at this path.
     *
     * <p>Spec: RTTS5c
     *
     * @return a {@link JsonArrayPathObject} view of this path
     */
    @NotNull JsonArrayPathObject asJsonArray();

    /**
     * Subscribes a listener for path-based update events. The listener is invoked when
     * an operation modifies the value at this path. The same path may be subscribed by
     * multiple listeners independently. Call {@link Subscription#unsubscribe()}
     * on the returned handle to stop receiving events for this listener.
     *
     * <p>Spec: RTPO19 / RTTS3d
     *
     * @param listener the listener to invoke on updates
     * @return a subscription handle that can be used to unsubscribe this listener
     */
    @NonBlocking
    @NotNull Subscription subscribe(@NotNull PathObjectListener listener);

    /**
     * Subscribes a listener for path-based update events using the provided
     * {@link PathObjectSubscriptionOptions}. Options control coverage rules such as the
     * {@code depth} of nested updates that trigger the listener. Call
     * {@link Subscription#unsubscribe()} on the returned handle to stop
     * receiving events for this listener.
     *
     * <p>Spec: RTPO19 / RTTS3d
     *
     * @param listener the listener to invoke on updates
     * @param options  optional subscription options, may be {@code null}
     * @return a subscription handle that can be used to unsubscribe this listener
     */
    @NonBlocking
    @NotNull Subscription subscribe(@NotNull PathObjectListener listener, @Nullable PathObjectSubscriptionOptions options);
}
