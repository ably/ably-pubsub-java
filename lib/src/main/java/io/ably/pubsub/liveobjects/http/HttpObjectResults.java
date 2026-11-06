package io.ably.pubsub.liveobjects.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ably.pubsub.liveobjects.ValueType;
import io.ably.pubsub.liveobjects.message.ObjectsMapSemantics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Implementations of the {@link io.ably.pubsub.liveobjects.HttpObject} result types.
 */
final class HttpObjectResults {

    private HttpObjectResults() {}

    /** The {@code as*} casts of {@link HttpObjectGetFullResult}, driven by {@link #getType()}. */
    abstract static class Result implements HttpObjectGetFullResult {

        @Override
        public @NotNull HttpLiveMap asLiveMap() {
            return (HttpLiveMap) expect(this instanceof HttpLiveMap, "LIVE_MAP");
        }

        @Override
        public @NotNull HttpLiveCounter asLiveCounter() {
            return (HttpLiveCounter) expect(this instanceof HttpLiveCounter, "LIVE_COUNTER");
        }

        @Override
        public @NotNull HttpLiveObject asLiveObject() {
            return (HttpLiveObject) expect(this instanceof HttpLiveObject, "an object");
        }

        @Override
        public @NotNull HttpObjectData asObjectData() {
            return (HttpObjectData) expect(this instanceof HttpObjectData, "a leaf value");
        }

        @Override
        public @NotNull String asString() {
            return asObjectData(ValueType.STRING).getString();
        }

        @Override
        public double asNumber() {
            return asObjectData(ValueType.NUMBER).getNumber();
        }

        @Override
        public boolean asBoolean() {
            return asObjectData(ValueType.BOOLEAN).getBoolean();
        }

        @Override
        public byte @NotNull [] asBinary() {
            return asObjectData(ValueType.BINARY).getBytes();
        }

        @Override
        public @NotNull JsonObject asJsonObject() {
            return asObjectData(ValueType.JSON_OBJECT).getJson().getAsJsonObject();
        }

        @Override
        public @NotNull JsonArray asJsonArray() {
            return asObjectData(ValueType.JSON_ARRAY).getJson().getAsJsonArray();
        }

        private HttpObjectData asObjectData(ValueType type) {
            return (HttpObjectData) expect(getType() == type, type.name());
        }

        private Object expect(boolean matches, String expected) {
            if (!matches) {
                throw new IllegalStateException("Expected " + expected + " but the value is " + getType());
            }
            return this;
        }
    }

    /** Spec: RSO9d */
    static class LiveObject extends Result implements HttpLiveObject {
        private final String objectId;
        private final JsonObject unrecognizedFields;

        LiveObject(String objectId, JsonObject unrecognizedFields) {
            this.objectId = objectId;
            this.unrecognizedFields = unrecognizedFields;
        }

        @Override
        public @NotNull String getObjectId() {
            return objectId;
        }

        @Override
        public @NotNull JsonObject getUnrecognizedFields() {
            return unrecognizedFields.deepCopy(); // JsonObject is mutable; keep the result immutable
        }

        @Override
        public @NotNull ValueType getType() {
            return ValueType.UNKNOWN;
        }

        @Override
        public String toString() {
            return "HttpLiveObject{objectId=" + objectId + ", unrecognizedFields=" + unrecognizedFields + "}";
        }
    }

    /** Spec: RSO14 */
    static final class LiveMap extends LiveObject implements HttpLiveMap {
        private final ObjectsMapSemantics semantics;
        private final Map<String, HttpLiveMapEntry> entries;

        LiveMap(String objectId, ObjectsMapSemantics semantics, Map<String, HttpLiveMapEntry> entries,
                JsonObject unrecognizedFields) {
            super(objectId, unrecognizedFields);
            this.semantics = semantics;
            this.entries = Collections.unmodifiableMap(entries);
        }

        @Override
        public @NotNull ObjectsMapSemantics getSemantics() {
            return semantics;
        }

        @Override
        public @NotNull Map<String, HttpLiveMapEntry> getEntries() {
            return entries;
        }

        @Override
        public @NotNull ValueType getType() {
            return ValueType.LIVE_MAP;
        }

        @Override
        public String toString() {
            return "HttpLiveMap{objectId=" + getObjectId() + ", semantics=" + semantics + ", entries=" + entries + "}";
        }
    }

    /** Spec: RSO16 */
    static final class LiveMapEntry implements HttpLiveMapEntry {
        private final HttpObjectGetFullResult data;

        LiveMapEntry(HttpObjectGetFullResult data) {
            this.data = data;
        }

        @Override
        public @NotNull HttpObjectGetFullResult getData() {
            return data;
        }

        @Override
        public String toString() {
            return "HttpLiveMapEntry{data=" + data + "}";
        }
    }

    /** Spec: RSO15 */
    static final class LiveCounter extends LiveObject implements HttpLiveCounter {
        private final Double count;

        LiveCounter(String objectId, Double count, JsonObject unrecognizedFields) {
            super(objectId, unrecognizedFields);
            this.count = count;
        }

        @Override
        public @Nullable Double getCount() {
            return count;
        }

        @Override
        public @NotNull ValueType getType() {
            return ValueType.LIVE_COUNTER;
        }

        @Override
        public String toString() {
            return "HttpLiveCounter{objectId=" + getObjectId() + ", count=" + count + "}";
        }
    }

    /** Spec: RSO9c - {@code objectId} is always unset (RSO9c1). */
    static final class ObjectDataLeaf extends Result implements HttpObjectData {
        private final String string;
        private final Double number;
        private final Boolean bool;
        private final byte[] bytes;
        private final JsonElement json;

        ObjectDataLeaf(String string, Double number, Boolean bool, byte[] bytes, JsonElement json) {
            this.string = string;
            this.number = number;
            this.bool = bool;
            this.bytes = bytes;
            this.json = json;
        }

        @Override
        public @Nullable String getObjectId() {
            return null;
        }

        @Override
        public @Nullable String getString() {
            return string;
        }

        @Override
        public @Nullable Double getNumber() {
            return number;
        }

        @Override
        public @Nullable Boolean getBoolean() {
            return bool;
        }

        @Override
        public byte @Nullable [] getBytes() {
            return bytes != null ? bytes.clone() : null; // byte[] is mutable; keep the result immutable
        }

        @Override
        public @Nullable JsonElement getJson() {
            return json != null ? json.deepCopy() : null; // JsonElement is mutable; keep the result immutable
        }

        @Override
        public @NotNull ValueType getType() {
            if (string != null) return ValueType.STRING;
            if (number != null) return ValueType.NUMBER;
            if (bool != null) return ValueType.BOOLEAN;
            if (bytes != null) return ValueType.BINARY;
            if (json != null && json.isJsonObject()) return ValueType.JSON_OBJECT;
            if (json != null && json.isJsonArray()) return ValueType.JSON_ARRAY;
            return ValueType.UNKNOWN;
        }

        @Override
        public String toString() {
            return "HttpObjectData{string=" + string + ", number=" + number + ", boolean=" + bool
                + ", bytes=" + (bytes == null ? null : "<" + bytes.length + " bytes>") + ", json=" + json + "}";
        }
    }

    /** Spec: RSO10 */
    static final class PublishResult implements HttpObjectPublishResult {
        private final String messageId;
        private final String channel;
        private final List<String> objectIds;

        PublishResult(String messageId, String channel, List<String> objectIds) {
            this.messageId = messageId;
            this.channel = channel;
            this.objectIds = Collections.unmodifiableList(objectIds);
        }

        @Override
        public @NotNull String getMessageId() {
            return messageId;
        }

        @Override
        public @NotNull String getChannel() {
            return channel;
        }

        @Override
        public @NotNull List<String> getObjectIds() {
            return objectIds;
        }
    }

    /** Spec: RSO11 */
    static final class GenerateIdResult implements HttpObjectGenerateIdResult {
        private final String objectId;
        private final String nonce;
        private final String initialValue;

        GenerateIdResult(String objectId, String nonce, String initialValue) {
            this.objectId = objectId;
            this.nonce = nonce;
            this.initialValue = initialValue;
        }

        @Override
        public @NotNull String getObjectId() {
            return objectId;
        }

        @Override
        public @NotNull String getNonce() {
            return nonce;
        }

        @Override
        public @NotNull String getInitialValue() {
            return initialValue;
        }
    }
}
