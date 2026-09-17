package io.ably.pubsub.types;

import com.google.gson.JsonObject;
import io.ably.pubsub.util.Serialisation;
import org.msgpack.core.MessagePacker;

import java.io.IOException;
import java.util.Map;

/**
 * Represents metadata about a message operation (update/delete).
 * Contains optional information about who performed the operation and why.
 */
public class MessageOperation {

    private static final String CLIENT_ID = "clientId";
    private static final String DESCRIPTION = "description";
    private static final String METADATA = "metadata";

    /**
     * Optional identifier of the client performing the operation.
     */
    public String clientId;

    /**
     * Optional human-readable description of the operation.
     */
    public String description;

    /**
     * Optional dictionary of key-value pairs containing additional metadata about the operation.
     */
    public Map<String, String> metadata;

    /**
     * Default constructor
     */
    public MessageOperation() {
    }

    /**
     * Constructor with all fields
     */
    public MessageOperation(String clientId, String description, Map<String, String> metadata) {
        this.clientId = clientId;
        this.description = description;
        this.metadata = metadata;
    }

    /**
     * Writes this MessageOperation to MessagePack format.
     *
     * @param packer The MessagePacker to write to
     * @throws IOException If writing fails
     */
    void writeMsgpack(MessagePacker packer) throws IOException {
        int fieldCount = 0;
        if (clientId != null) ++fieldCount;
        if (description != null) ++fieldCount;
        if (metadata != null) ++fieldCount;

        packer.packMapHeader(fieldCount);

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
            MessageSerializer.write(metadata, packer);
        }
    }


    /**
     * Converts this MessageOperation to a JsonObject.
     *
     * @return JsonObject representation
     */
    JsonObject asJsonObject() {
        JsonObject json = new JsonObject();

        if (clientId != null) {
            json.addProperty(CLIENT_ID, clientId);
        }
        if (description != null) {
            json.addProperty(DESCRIPTION, description);
        }
        if (metadata != null) {
            json.add(METADATA, Serialisation.gson.toJsonTree(metadata));
        }

        return json;
    }

}
