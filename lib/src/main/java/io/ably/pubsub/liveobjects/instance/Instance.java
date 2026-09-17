package io.ably.pubsub.liveobjects.instance;

import com.google.gson.JsonElement;
import io.ably.pubsub.liveobjects.ValueType;
import io.ably.pubsub.liveobjects.instance.types.BinaryInstance;
import io.ably.pubsub.liveobjects.instance.types.BooleanInstance;
import io.ably.pubsub.liveobjects.instance.types.JsonArrayInstance;
import io.ably.pubsub.liveobjects.instance.types.JsonObjectInstance;
import io.ably.pubsub.liveobjects.instance.types.LiveCounterInstance;
import io.ably.pubsub.liveobjects.instance.types.LiveMapInstance;
import io.ably.pubsub.liveobjects.instance.types.NumberInstance;
import io.ably.pubsub.liveobjects.instance.types.StringInstance;
import org.jetbrains.annotations.NotNull;

/**
 * A direct-reference view of a single resolved LiveObject ({@code LiveMap} or
 * {@code LiveCounter}) or primitive value.
 *
 * <p>Unlike {@code PathObject}, which re-resolves its path on every call, an
 * {@code Instance} is identity-addressed: it wraps an already-resolved value (typically
 * obtained from a {@code PathObject}), so its type is fixed and known for the lifetime
 * of the instance, and it is dereferenced in O(1) regardless of where that value sits
 * in the graph. Read operations validate the access API preconditions and fail with an
 * {@code AblyException} if those are not satisfied.
 *
 * <p>This base type exposes only the methods whose behaviour is independent of the
 * wrapped type; everything else - including {@code subscribe} (RTTS7b) - is
 * partitioned onto the sub-types. Use the {@code as*} helpers to obtain a sub-type
 * view, or discriminate via {@link #getType()}. Because the wrapped type is fixed and
 * known, a mismatched {@code as*} cast fails fast with an {@link IllegalStateException}
 * rather than returning a best-effort view (contrast {@code PathObject}, whose casts
 * never throw).
 *
 * <p>Spec: RTINS1, RTTS7
 *
 * @see LiveMapInstance
 * @see LiveCounterInstance
 * @see InstanceListener
 */
public interface Instance {

    /**
     * Returns the {@link ValueType} of the value wrapped by this instance. Use this
     * instead of dedicated {@code isLiveMap}/{@code isLiveCounter}/etc. checks.
     *
     * <p>An {@code Instance} is always constructed from a resolved value, so this never
     * returns {@link ValueType#UNKNOWN} in normal operation.
     *
     * <p>Spec: RTTS8a
     *
     * @return the wrapped value type
     */
    @NotNull ValueType getType();

    /**
     * Returns a JSON-serializable, recursively compacted snapshot of the wrapped value.
     * Behaves identically to {@code PathObject#compactJson} except that it operates on
     * the wrapped value directly instead of resolving a path. An {@code Instance} is
     * always bound to a resolved value, so this always returns a non-null result;
     * failures of the access API preconditions are signalled via {@code AblyException}.
     *
     * <p>Spec: RTINS11 / RTINS11c (universal non-null invariant - Instance is bound
     * to an already-resolved value, so the path-resolution failure mode of
     * PathObject#compactJson does not apply) / RTTS7a (typed-SDK signature reflects
     * the universal invariant)
     *
     * @return the compacted JSON snapshot
     */
    @NotNull JsonElement compactJson();

    /**
     * Returns this instance viewed as a {@link LiveMapInstance}.
     *
     * <p>Because an {@code Instance} wraps an already-resolved value of a known, fixed
     * type, this fails fast: it throws {@link IllegalStateException} if the wrapped value
     * is not a {@code LiveMap}, rather than returning a best-effort view. Use
     * {@link #getType()} to discriminate the type before casting.
     *
     * <p>Spec: RTTS9a / RTTS9d
     *
     * @return a {@link LiveMapInstance} view of this instance
     * @throws IllegalStateException if the wrapped value is not a {@code LiveMap}
     */
    @NotNull LiveMapInstance asLiveMap();

    /**
     * Returns this instance viewed as a {@link LiveCounterInstance}.
     * Fails fast: throws {@link IllegalStateException} if the wrapped value is not a
     * {@code LiveCounter}.
     *
     * <p>Spec: RTTS9b / RTTS9d
     *
     * @return a {@link LiveCounterInstance} view of this instance
     * @throws IllegalStateException if the wrapped value is not a {@code LiveCounter}
     */
    @NotNull LiveCounterInstance asLiveCounter();

    /**
     * Returns this instance viewed as a {@link NumberInstance}.
     * Fails fast: throws {@link IllegalStateException} if the wrapped value is not a
     * {@code Number}.
     *
     * <p>Spec: RTTS9c / RTTS9d
     *
     * @return a {@link NumberInstance} view of this instance
     * @throws IllegalStateException if the wrapped value is not a {@code Number}
     */
    @NotNull NumberInstance asNumber();

    /**
     * Returns this instance viewed as a {@link StringInstance}.
     * Fails fast: throws {@link IllegalStateException} if the wrapped value is not a
     * {@code String}.
     *
     * <p>Spec: RTTS9c / RTTS9d
     *
     * @return a {@link StringInstance} view of this instance
     * @throws IllegalStateException if the wrapped value is not a {@code String}
     */
    @NotNull StringInstance asString();

    /**
     * Returns this instance viewed as a {@link BooleanInstance}.
     * Fails fast: throws {@link IllegalStateException} if the wrapped value is not a
     * {@code Boolean}.
     *
     * <p>Spec: RTTS9c / RTTS9d
     *
     * @return a {@link BooleanInstance} view of this instance
     * @throws IllegalStateException if the wrapped value is not a {@code Boolean}
     */
    @NotNull BooleanInstance asBoolean();

    /**
     * Returns this instance viewed as a {@link BinaryInstance}.
     * Fails fast: throws {@link IllegalStateException} if the wrapped value is not a
     * binary value.
     *
     * <p>Spec: RTTS9c / RTTS9d
     *
     * @return a {@link BinaryInstance} view of this instance
     * @throws IllegalStateException if the wrapped value is not a binary value
     */
    @NotNull BinaryInstance asBinary();

    /**
     * Returns this instance viewed as a {@link JsonObjectInstance}.
     * Fails fast: throws {@link IllegalStateException} if the wrapped value is not a
     * JSON object.
     *
     * <p>Spec: RTTS9c / RTTS9d
     *
     * @return a {@link JsonObjectInstance} view of this instance
     * @throws IllegalStateException if the wrapped value is not a JSON object
     */
    @NotNull JsonObjectInstance asJsonObject();

    /**
     * Returns this instance viewed as a {@link JsonArrayInstance}.
     * Fails fast: throws {@link IllegalStateException} if the wrapped value is not a
     * JSON array.
     *
     * <p>Spec: RTTS9c / RTTS9d
     *
     * @return a {@link JsonArrayInstance} view of this instance
     * @throws IllegalStateException if the wrapped value is not a JSON array
     */
    @NotNull JsonArrayInstance asJsonArray();
}
