package io.ably.pubsub.liveobjects.http;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

/**
 * An object in a {@link io.ably.pubsub.liveobjects.HttpObject#get(io.ably.pubsub.types.Param[])}
 * response. Known types are the subtypes {@link HttpLiveMap} and {@link HttpLiveCounter}; an object
 * whose type this library doesn't recognise is returned as a plain {@code HttpLiveObject}.
 *
 * <p>Spec: RSO9d
 */
public interface HttpLiveObject extends HttpObjectGetFullResult {

    /**
     * @return the object's ID.
     */
    @NotNull String getObjectId();

    /**
     * Fields of the object the library doesn't model, passed through unchanged (RSF1, RSO2g4).
     * For an unrecognised object type this holds its type-specific data. Values are JSON, as in a
     * {@code getCompact} result: binary values are Base64 strings for both protocols.
     *
     * @return a copy of the fields, by name; changes to it are not reflected. Empty if there are none.
     */
    @NotNull JsonObject getUnrecognizedFields();
}
