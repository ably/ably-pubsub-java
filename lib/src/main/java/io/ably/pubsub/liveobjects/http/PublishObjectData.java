package io.ably.pubsub.liveobjects.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A leaf value in an {@link HttpObjectOperation}: the value of a {@code mapSet}, or the data of a
 * {@code mapCreate} entry. Exactly one field is set; create instances with the static factories.
 *
 * <p>Spec: RSO7
 */
public final class PublishObjectData {

    private final @Nullable String string;
    private final @Nullable Number number;
    private final @Nullable Boolean bool;
    private final byte @Nullable [] bytes;
    private final @Nullable JsonElement json;
    private final @Nullable String objectId;

    private PublishObjectData(@Nullable String string, @Nullable Number number, @Nullable Boolean bool,
                              byte @Nullable [] bytes, @Nullable JsonElement json, @Nullable String objectId) {
        this.string = string;
        this.number = number;
        this.bool = bool;
        this.bytes = bytes;
        this.json = json;
        this.objectId = objectId;
    }

    /** Spec: RSO7a */
    public static @NotNull PublishObjectData string(@NotNull String value) {
        return new PublishObjectData(value, null, null, null, null, null);
    }

    /** Spec: RSO7b */
    public static @NotNull PublishObjectData number(@NotNull Number value) {
        return new PublishObjectData(null, value, null, null, null, null);
    }

    /** Spec: RSO7c */
    public static @NotNull PublishObjectData bool(boolean value) {
        return new PublishObjectData(null, null, value, null, null, null);
    }

    /** Spec: RSO7d */
    public static @NotNull PublishObjectData bytes(byte @NotNull [] value) {
        return new PublishObjectData(null, null, null, value, null, null);
    }

    /** Spec: RSO7e */
    public static @NotNull PublishObjectData json(@NotNull JsonObject value) {
        return new PublishObjectData(null, null, null, null, value, null);
    }

    /** Spec: RSO7e */
    public static @NotNull PublishObjectData json(@NotNull JsonArray value) {
        return new PublishObjectData(null, null, null, null, value, null);
    }

    /**
     * A reference to an existing object by its ID.
     * <p>Spec: RSO7f
     */
    public static @NotNull PublishObjectData objectId(@NotNull String objectId) {
        return new PublishObjectData(null, null, null, null, null, objectId);
    }

    public @Nullable String getString() {
        return string;
    }

    public @Nullable Number getNumber() {
        return number;
    }

    public @Nullable Boolean getBoolean() {
        return bool;
    }

    public byte @Nullable [] getBytes() {
        return bytes;
    }

    /**
     * @return the JSON value, a {@link JsonObject} or {@link JsonArray}, or {@code null}.
     */
    public @Nullable JsonElement getJson() {
        return json;
    }

    public @Nullable String getObjectId() {
        return objectId;
    }
}
