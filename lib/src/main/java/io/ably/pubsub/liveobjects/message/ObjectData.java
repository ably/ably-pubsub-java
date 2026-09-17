package io.ably.pubsub.liveobjects.message;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a value in an object on a channel. A value is either a reference to another
 * object ({@link #getObjectId()}) or exactly one of the primitive payloads
 * ({@link #getString()}, {@link #getNumber()}, {@link #getBoolean()},
 * {@link #getBytes()}, {@link #getJson()}).
 *
 * <p>Spec: OD1
 */
public interface ObjectData {

    /**
     * Returns a reference to another object, used to support composable object
     * structures.
     *
     * <p>Spec: OD2a
     *
     * @return the referenced object id, or {@code null} if this value is a primitive
     */
    @Nullable String getObjectId();

    /**
     * Returns the string value.
     *
     * <p>Spec: OD2f
     *
     * @return the string value, or {@code null} if not applicable
     */
    @Nullable String getString();

    /**
     * Returns the numeric value.
     *
     * <p>Spec: OD2e
     *
     * @return the numeric value, or {@code null} if not applicable
     */
    @Nullable Double getNumber();

    /**
     * Returns the boolean value.
     *
     * <p>Spec: OD2c
     *
     * @return the boolean value, or {@code null} if not applicable
     */
    @Nullable Boolean getBoolean();

    /**
     * Returns the binary value. The returned array is the underlying message
     * payload and is not defensively copied; callers must treat it as read-only.
     *
     * <p>Spec: OD2d
     *
     * @return the binary value, or {@code null} if not applicable
     */
    byte @Nullable [] getBytes();

    /**
     * Returns the JSON object or array value.
     *
     * <p>Spec: OD2g
     *
     * @return the JSON value, or {@code null} if not applicable
     */
    @Nullable JsonElement getJson();
}
