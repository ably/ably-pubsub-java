package io.ably.pubsub.liveobjects.value;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

/**
 * The union of values assignable to a {@code LiveMap} key:
 * {@code Boolean | Binary | Number | String | JsonArray | JsonObject |
 * LiveCounter | LiveMap}. Provides compile-time type safety for write
 * operations; the design follows Gson's {@code JsonElement} pattern.
 *
 * <p>The {@link LiveMap} and {@link LiveCounter} variants hold <em>new-object
 * value types</em> describing the initial state of a nested object to create -
 * not references to existing live objects.
 *
 * <p>Spec: RTPO15a2 / RTINS12a2 / RTLM20 (accepted value types)
 */
public abstract class LiveMapValue {

    /**
     * Gets the underlying value.
     *
     * @return the value as an Object
     */
    @NotNull
    public abstract Object getValue();

    /**
     * Returns true if this LiveMapValue represents a Boolean value.
     *
     * @return true if this is a Boolean value
     */
    public boolean isBoolean() { return false; }

    /**
     * Returns true if this LiveMapValue represents a Binary value.
     *
     * @return true if this is a Binary value
     */
    public boolean isBinary() { return false; }

    /**
     * Returns true if this LiveMapValue represents a Number value.
     *
     * @return true if this is a Number value
     */
    public boolean isNumber() { return false; }

    /**
     * Returns true if this LiveMapValue represents a String value.
     *
     * @return true if this is a String value
     */
    public boolean isString() { return false; }

    /**
     * Returns true if this LiveMapValue represents a JsonArray value.
     *
     * @return true if this is a JsonArray value
     */
    public boolean isJsonArray() { return false; }

    /**
     * Returns true if this LiveMapValue represents a JsonObject value.
     *
     * @return true if this is a JsonObject value
     */
    public boolean isJsonObject() { return false; }

    /**
     * Returns true if this LiveMapValue represents a new {@link LiveCounter}
     * value type.
     *
     * @return true if this is a LiveCounter value
     */
    public boolean isLiveCounter() { return false; }

    /**
     * Returns true if this LiveMapValue represents a new {@link LiveMap}
     * value type.
     *
     * @return true if this is a LiveMap value
     */
    public boolean isLiveMap() { return false; }

    /**
     * Gets the Boolean value if this LiveMapValue represents a Boolean.
     *
     * @return the Boolean value
     * @throws IllegalStateException if this is not a Boolean value
     */
    @NotNull
    public Boolean getAsBoolean() {
        throw new IllegalStateException("Not a Boolean value");
    }

    /**
     * Gets the Binary value if this LiveMapValue represents a Binary.
     *
     * @return the Binary value
     * @throws IllegalStateException if this is not a Binary value
     */
    public byte @NotNull [] getAsBinary() {
        throw new IllegalStateException("Not a Binary value");
    }

    /**
     * Gets the Number value if this LiveMapValue represents a Number.
     *
     * @return the Number value
     * @throws IllegalStateException if this is not a Number value
     */
    @NotNull
    public Number getAsNumber() {
        throw new IllegalStateException("Not a Number value");
    }

    /**
     * Gets the String value if this LiveMapValue represents a String.
     *
     * @return the String value
     * @throws IllegalStateException if this is not a String value
     */
    @NotNull
    public String getAsString() {
        throw new IllegalStateException("Not a String value");
    }

    /**
     * Gets the JsonArray value if this LiveMapValue represents a JsonArray.
     *
     * @return the JsonArray value
     * @throws IllegalStateException if this is not a JsonArray value
     */
    @NotNull
    public JsonArray getAsJsonArray() {
        throw new IllegalStateException("Not a JsonArray value");
    }

    /**
     * Gets the JsonObject value if this LiveMapValue represents a JsonObject.
     *
     * @return the JsonObject value
     * @throws IllegalStateException if this is not a JsonObject value
     */
    @NotNull
    public JsonObject getAsJsonObject() {
        throw new IllegalStateException("Not a JsonObject value");
    }

    /**
     * Gets the {@link LiveCounter} value type if this LiveMapValue represents one.
     *
     * @return the LiveCounter value type
     * @throws IllegalStateException if this is not a LiveCounter value
     */
    @NotNull
    public LiveCounter getAsLiveCounter() {
        throw new IllegalStateException("Not a LiveCounter value");
    }

    /**
     * Gets the {@link LiveMap} value type if this LiveMapValue represents one.
     *
     * @return the LiveMap value type
     * @throws IllegalStateException if this is not a LiveMap value
     */
    @NotNull
    public LiveMap getAsLiveMap() {
        throw new IllegalStateException("Not a LiveMap value");
    }

    /**
     * Creates a LiveMapValue from a Boolean.
     *
     * @param value the boolean value
     * @return a LiveMapValue containing the boolean
     */
    @NotNull
    public static LiveMapValue of(@NotNull Boolean value) {
        return new BooleanValue(value);
    }

    /**
     * Creates a LiveMapValue from a Binary. The array is copied, so later
     * modifications to {@code value} do not affect the created LiveMapValue.
     *
     * @param value the binary value
     * @return a LiveMapValue containing the binary
     */
    @NotNull
    public static LiveMapValue of(byte @NotNull [] value) {
        return new BinaryValue(value);
    }

    /**
     * Creates a LiveMapValue from a Number.
     *
     * @param value the number value
     * @return a LiveMapValue containing the number
     */
    @NotNull
    public static LiveMapValue of(@NotNull Number value) {
        return new NumberValue(value);
    }

    /**
     * Creates a LiveMapValue from a String.
     *
     * @param value the string value
     * @return a LiveMapValue containing the string
     */
    @NotNull
    public static LiveMapValue of(@NotNull String value) {
        return new StringValue(value);
    }

    /**
     * Creates a LiveMapValue from a JsonArray.
     *
     * @param value the JsonArray value
     * @return a LiveMapValue containing the JsonArray
     */
    @NotNull
    public static LiveMapValue of(@NotNull JsonArray value) {
        return new JsonArrayValue(value);
    }

    /**
     * Creates a LiveMapValue from a JsonObject.
     *
     * @param value the JsonObject value
     * @return a LiveMapValue containing the JsonObject
     */
    @NotNull
    public static LiveMapValue of(@NotNull JsonObject value) {
        return new JsonObjectValue(value);
    }

    /**
     * Creates a LiveMapValue from a new {@link LiveCounter} value type.
     *
     * @param value the LiveCounter value type
     * @return a LiveMapValue containing the LiveCounter
     */
    @NotNull
    public static LiveMapValue of(@NotNull LiveCounter value) {
        return new LiveCounterValue(value);
    }

    /**
     * Creates a LiveMapValue from a new {@link LiveMap} value type.
     *
     * @param value the LiveMap value type
     * @return a LiveMapValue containing the LiveMap
     */
    @NotNull
    public static LiveMapValue of(@NotNull LiveMap value) {
        return new LiveMapValueWrapper(value);
    }

    // Concrete implementations for each allowed type

    /**
     * Boolean value implementation.
     */
    private static final class BooleanValue extends LiveMapValue {
        private final Boolean value;

        BooleanValue(@NotNull Boolean value) {
            this.value = value;
        }

        @Override
        public @NotNull Object getValue() {
            return value;
        }

        @Override
        public boolean isBoolean() { return true; }

        @Override
        public @NotNull Boolean getAsBoolean() { return value; }
    }

    /**
     * Binary value implementation.
     */
    private static final class BinaryValue extends LiveMapValue {
        private final byte[] value;

        BinaryValue(byte @NotNull [] value) {
            this.value = value.clone();
        }

        @Override
        public @NotNull Object getValue() {
            return value.clone();
        }

        @Override
        public boolean isBinary() { return true; }

        @Override
        public byte @NotNull [] getAsBinary() { return value.clone(); }
    }

    /**
     * Number value implementation.
     */
    private static final class NumberValue extends LiveMapValue {
        private final Number value;

        NumberValue(@NotNull Number value) {
            this.value = value;
        }

        @Override
        public @NotNull Object getValue() {
            return value;
        }

        @Override
        public boolean isNumber() { return true; }

        @Override
        public @NotNull Number getAsNumber() { return value; }
    }

    /**
     * String value implementation.
     */
    private static final class StringValue extends LiveMapValue {
        private final String value;

        StringValue(@NotNull String value) {
            this.value = value;
        }

        @Override
        public @NotNull Object getValue() {
            return value;
        }

        @Override
        public boolean isString() { return true; }

        @Override
        public @NotNull String getAsString() { return value; }
    }

    /**
     * JsonArray value implementation.
     */
    private static final class JsonArrayValue extends LiveMapValue {
        private final JsonArray value;

        JsonArrayValue(@NotNull JsonArray value) {
            this.value = value;
        }

        @Override
        public @NotNull Object getValue() {
            return value;
        }

        @Override
        public boolean isJsonArray() { return true; }

        @Override
        public @NotNull JsonArray getAsJsonArray() { return value; }
    }

    /**
     * JsonObject value implementation.
     */
    private static final class JsonObjectValue extends LiveMapValue {
        private final JsonObject value;

        JsonObjectValue(@NotNull JsonObject value) {
            this.value = value;
        }

        @Override
        public @NotNull Object getValue() {
            return value;
        }

        @Override
        public boolean isJsonObject() { return true; }

        @Override
        public @NotNull JsonObject getAsJsonObject() { return value; }
    }

    /**
     * LiveCounter value implementation.
     */
    private static final class LiveCounterValue extends LiveMapValue {
        private final LiveCounter value;

        LiveCounterValue(@NotNull LiveCounter value) {
            this.value = value;
        }

        @Override
        public @NotNull Object getValue() {
            return value;
        }

        @Override
        public boolean isLiveCounter() { return true; }

        @Override
        public @NotNull LiveCounter getAsLiveCounter() { return value; }
    }

    /**
     * LiveMap value implementation.
     */
    private static final class LiveMapValueWrapper extends LiveMapValue {
        private final LiveMap value;

        LiveMapValueWrapper(@NotNull LiveMap value) {
            this.value = value;
        }

        @Override
        public @NotNull Object getValue() {
            return value;
        }

        @Override
        public boolean isLiveMap() { return true; }

        @Override
        public @NotNull LiveMap getAsLiveMap() { return value; }
    }
}
