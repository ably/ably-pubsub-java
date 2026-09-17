package io.ably.pubsub.types;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.ably.pubsub.util.Log;
import org.jetbrains.annotations.NotNull;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains the details regarding the current version of the message - including when it was updated and by whom.
 */
public class MessageVersion {

    private static final String TAG = MessageVersion.class.getName();

    private static final String SERIAL = "serial";
    private static final String TIMESTAMP = "timestamp";
    private static final String CLIENT_ID = "clientId";
    private static final String DESCRIPTION = "description";
    private static final String METADATA = "metadata";

    /**
     * A unique identifier for the version of the message, lexicographically-comparable with other versions (that
     * share the same `Message.serial`). Will differ from the `Message.serial` only if the message has been
     * updated or deleted.
     */
    public String serial;

    /**
     * The timestamp of the message version.
     * <p>
     * If the `Message.action` is `message.create`, this will equal the `Message.timestamp`.
     */
    public long timestamp;

    /**
     * The client ID of the client that updated the message to this version.
     */
    public String clientId;

    /**
     * The description provided by the client that updated the message to this version.
     */
    public String description;

    /**
     * A map of string key-value pairs that may contain metadata associated with the operation to update
     * the message to this version.
     */
    public Map<String, String> metadata;

    public MessageVersion() {}

    public MessageVersion(String serial, Long timestamp) {
        this.serial = serial;
        this.timestamp = timestamp;
    }

    void writeMsgpack(MessagePacker packer) throws IOException {
        int fieldCount = 0;
        if (serial != null) ++fieldCount;
        if (timestamp != 0) ++fieldCount;
        if (clientId != null) fieldCount++;
        if (description != null) fieldCount++;
        if (metadata != null) fieldCount++;

        packer.packMapHeader(fieldCount);

        if (serial != null) {
            packer.packString(SERIAL);
            packer.packString(serial);
        }

        if (timestamp != 0) {
            packer.packString(TIMESTAMP);
            packer.packLong(timestamp);
        }

        if (clientId != null) {
            packer.packString(CLIENT_ID);
            packer.packString(clientId);
        }

        if (description != null) {
            packer.packString(DESCRIPTION);
            packer.packString(description);
        }

        if (metadata != null) {
            packer.packString(METADATA);
            packer.packMapHeader(metadata.size());
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                packer.packString(entry.getKey());
                packer.packString(entry.getValue());
            }
        }
    }

    static MessageVersion read(MessageUnpacker unpacker) throws IOException {
        MessageVersion version = new MessageVersion();
        int fieldCount = unpacker.unpackMapHeader();
        for (int i = 0; i < fieldCount; i++) {
            String fieldName = unpacker.unpackString().intern();
            MessageFormat fieldFormat = unpacker.getNextFormat();
            if (fieldFormat.equals(MessageFormat.NIL)) {
                unpacker.unpackNil();
                continue;
            }

            switch (fieldName) {
                case SERIAL:
                    version.serial = unpacker.unpackString();
                    break;
                case TIMESTAMP:
                    version.timestamp = unpacker.unpackLong();
                    break;
                case CLIENT_ID:
                    version.clientId = unpacker.unpackString();
                    break;
                case DESCRIPTION:
                    version.description = unpacker.unpackString();
                    break;
                case METADATA:
                    int mapSize = unpacker.unpackMapHeader();
                    version.metadata = new HashMap<>(mapSize);
                    for (int j = 0; j < mapSize; j++) {
                        String key = unpacker.unpackString();
                        String value = unpacker.unpackString();
                        version.metadata.put(key, value);
                    }
                    break;
                default:
                    Log.v(TAG, "Unexpected field: " + fieldName);
                    unpacker.skipValue();
                    break;
            }
        }
        return version;
    }


    static MessageVersion read(JsonElement json) throws MessageDecodeException {
        if (!json.isJsonObject()) {
            throw MessageDecodeException.fromDescription("Expected an object but got \"" + json.getClass() + "\".");
        }

        MessageVersion version = new MessageVersion();
        JsonObject map = json.getAsJsonObject();

        version.serial = readString(map, SERIAL);
        version.timestamp = readLong(map, TIMESTAMP);;
        version.clientId = readString(map, CLIENT_ID);;
        version.description = readString(map, DESCRIPTION);;
        if (map.has(METADATA)) {
            JsonObject metadataObject = map.getAsJsonObject(METADATA);
            version.metadata = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : metadataObject.entrySet()) {
                version.metadata.put(entry.getKey(), entry.getValue().getAsString());
            }
        }

        return version;
    }

    private static String readString(JsonObject map, String key) {
        JsonElement element = map.get(key);
        return (element != null && !element.isJsonNull()) ? element.getAsString() : null;
    }

    private static long readLong(JsonObject map, String key) {
        JsonElement element = map.get(key);
        return (element != null && !element.isJsonNull()) ? element.getAsLong() : 0;
    }

    JsonElement toJsonTree() {
        JsonObject json = new JsonObject();
        if (serial != null) {
            json.addProperty(SERIAL, serial);
        }
        if (timestamp != 0) {
            json.addProperty(TIMESTAMP, timestamp);
        }
        if (clientId != null) {
            json.addProperty(CLIENT_ID, clientId);
        }
        if (description != null) {
            json.addProperty(DESCRIPTION, description);
        }
        if (metadata != null) {
            JsonObject metadataObject = new JsonObject();
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                metadataObject.addProperty(entry.getKey(), entry.getValue());
            }
            json.add(METADATA, metadataObject);
        }
        return json;
    }

    public static class Serializer implements JsonSerializer<MessageVersion>, JsonDeserializer<MessageVersion> {
        @Override
        public JsonElement serialize(MessageVersion version, Type typeOfMessage, JsonSerializationContext ctx) {
            return version.toJsonTree();
        }

        @Override
        public MessageVersion deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                return read(json);
            } catch (MessageDecodeException e) {
                Log.e(TAG, e.getMessage(), e);
                throw new JsonParseException("Failed to deserialize MessageVersion from JSON.", e);
            }
        }
    }

    @Override
    public @NotNull String toString() {
        return "{MessageVersion serial=" + serial + ", timestamp=" + timestamp + "}";
    }
}
