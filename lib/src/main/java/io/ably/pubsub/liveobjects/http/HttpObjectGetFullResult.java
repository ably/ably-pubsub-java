package io.ably.pubsub.liveobjects.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.ably.pubsub.liveobjects.ValueType;
import org.jetbrains.annotations.NotNull;

/**
 * A value returned by {@link io.ably.pubsub.liveobjects.HttpObject#get(io.ably.pubsub.types.Param[])}
 * (the full format), and the type of each
 * {@link HttpLiveMapEntry#getData() map entry's data}. It is one of:
 * <ul>
 *     <li>{@link HttpLiveMap} - a map object (RSO9a)</li>
 *     <li>{@link HttpLiveCounter} - a counter object (RSO9b)</li>
 *     <li>{@link HttpObjectData} - a decoded leaf value (RSO9c)</li>
 *     <li>{@link HttpLiveObject} itself - an object of a type this library doesn't recognise (RSO9d)</li>
 * </ul>
 *
 * <p>As with a realtime {@code Instance}, discriminate with {@link #getType()} and narrow with the
 * {@code as*} helpers, which fail fast with {@link IllegalStateException} on a type mismatch. The
 * result is an immutable snapshot, so the helpers for leaf values return the value itself, e.g.
 * {@code result.asLiveMap().getEntries().get("name").getData().asString()}.
 *
 * <p>Spec: RSO9
 */
public interface HttpObjectGetFullResult {

    /**
     * Returns the type of this value: {@link ValueType#LIVE_MAP}, {@link ValueType#LIVE_COUNTER},
     * a primitive type for a leaf, or {@link ValueType#UNKNOWN} for an object type this library
     * doesn't recognise or a JSON leaf that is neither an object nor an array.
     *
     * @return the value type.
     */
    @NotNull ValueType getType();

    /**
     * @return this value as an {@link HttpLiveMap}.
     * @throws IllegalStateException if this is not a map.
     */
    @NotNull HttpLiveMap asLiveMap();

    /**
     * @return this value as an {@link HttpLiveCounter}.
     * @throws IllegalStateException if this is not a counter.
     */
    @NotNull HttpLiveCounter asLiveCounter();

    /**
     * Returns this value as an {@link HttpLiveObject}: a map, a counter, or an object of a type this
     * library doesn't recognise.
     *
     * @return this value as an {@link HttpLiveObject}.
     * @throws IllegalStateException if this is a leaf value, not an object.
     */
    @NotNull HttpLiveObject asLiveObject();

    /**
     * @return this leaf as an {@link HttpObjectData}, with the getter for its one value.
     * @throws IllegalStateException if this is an object, not a leaf value.
     */
    @NotNull HttpObjectData asObjectData();

    /**
     * @return the string value.
     * @throws IllegalStateException if this is not a string leaf.
     */
    @NotNull String asString();

    /**
     * @return the number value.
     * @throws IllegalStateException if this is not a number leaf.
     */
    double asNumber();

    /**
     * @return the boolean value.
     * @throws IllegalStateException if this is not a boolean leaf.
     */
    boolean asBoolean();

    /**
     * @return the binary value.
     * @throws IllegalStateException if this is not a binary leaf.
     */
    byte @NotNull [] asBinary();

    /**
     * @return the JSON object value.
     * @throws IllegalStateException if this is not a JSON object leaf.
     */
    @NotNull JsonObject asJsonObject();

    /**
     * @return the JSON array value.
     * @throws IllegalStateException if this is not a JSON array leaf.
     */
    @NotNull JsonArray asJsonArray();
}
