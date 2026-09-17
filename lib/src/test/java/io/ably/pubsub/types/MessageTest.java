package io.ably.pubsub.types;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ably.pubsub.types.Message.Serializer;
import io.ably.pubsub.util.Serialisation;
import org.junit.Test;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;

public class MessageTest {

    private final Serializer serializer = new Serializer();

    @Test
    public void serialize_message() {
        // Given
        Message message = new Message("test-name", "test-data");
        message.clientId = "test-client-id";
        message.connectionKey = "test-key";

        // When
        JsonElement serializedElement = serializer.serialize(message, null, null);

        // Then
        JsonObject serializedObject = serializedElement.getAsJsonObject();
        assertEquals("test-client-id", serializedObject.get("clientId").getAsString());
        assertEquals("test-key", serializedObject.get("connectionKey").getAsString());
        assertEquals("test-data", serializedObject.get("data").getAsString());
        assertEquals("test-name", serializedObject.get("name").getAsString());
    }

    @Test
    public void serialize_message_with_name_and_data() {
        // Given
        Message message = new Message("test-name", "test-data");

        // When
        JsonElement serializedElement = serializer.serialize(message, null, null);

        // Then
        JsonObject serializedObject = serializedElement.getAsJsonObject();
        assertNull(serializedObject.get("clientId"));
        assertNull(serializedObject.get("connectionKey"));
        assertNull(serializedObject.get("extras"));
        assertEquals("test-data", serializedObject.get("data").getAsString());
        assertEquals("test-name", serializedObject.get("name").getAsString());
    }

    @Test
    public void serialize_message_with_serial() {
        // Given
        Message message = new Message("test-name", "test-data");
        message.clientId = "test-client-id";
        message.connectionKey = "test-key";
        message.action = MessageAction.MESSAGE_CREATE;
        message.serial = "01826232498871-001@abcdefghij:001";

        // When
        JsonElement serializedElement = serializer.serialize(message, null, null);

        // Then
        JsonObject serializedObject = serializedElement.getAsJsonObject();
        assertEquals("test-client-id", serializedObject.get("clientId").getAsString());
        assertEquals("test-key", serializedObject.get("connectionKey").getAsString());
        assertEquals("test-data", serializedObject.get("data").getAsString());
        assertEquals("test-name", serializedObject.get("name").getAsString());
        assertEquals(0, serializedObject.get("action").getAsInt());
        assertEquals("01826232498871-001@abcdefghij:001", serializedObject.get("serial").getAsString());
    }

    @Test
    public void deserialize_message_with_serial() throws Exception {
        // Given
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("clientId", "test-client-id");
        jsonObject.addProperty("data", "test-data");
        jsonObject.addProperty("name", "test-name");
        jsonObject.addProperty("action", 0);
        jsonObject.addProperty("serial", "01826232498871-001@abcdefghij:001");

        // When
        Message message = Message.fromEncoded(jsonObject, new ChannelOptions());

        // Then
        assertEquals("test-client-id", message.clientId);
        assertEquals("test-data", message.data);
        assertEquals("test-name", message.name);
        assertEquals(MessageAction.MESSAGE_CREATE, message.action);
        assertEquals("01826232498871-001@abcdefghij:001", message.serial);
    }

    @Test
    public void serialize_message_with_operation() {
        // Given
        Message message = new Message("test-name", "test-data");
        message.clientId = "test-client-id";
        message.connectionKey = "test-key";
        MessageVersion version = new MessageVersion();
        version.clientId = "operation-client-id";
        version.description = "operation-description";
        version.metadata = new HashMap<>();
        version.metadata.put("key1", "value1");
        version.metadata.put("key2", "value2");
        message.version = version;

        // When
        JsonElement serializedElement = serializer.serialize(message, null, null);

        // Then
        JsonObject serializedObject = serializedElement.getAsJsonObject();
        assertEquals("test-client-id", serializedObject.get("clientId").getAsString());
        assertEquals("test-key", serializedObject.get("connectionKey").getAsString());
        assertEquals("test-data", serializedObject.get("data").getAsString());
        assertEquals("test-name", serializedObject.get("name").getAsString());
        JsonObject versionObject = serializedObject.getAsJsonObject("version");
        assertEquals("operation-client-id", versionObject.get("clientId").getAsString());
        assertEquals("operation-description", versionObject.get("description").getAsString());
        JsonObject metadataObject = versionObject.getAsJsonObject("metadata");
        assertEquals("value1", metadataObject.get("key1").getAsString());
        assertEquals("value2", metadataObject.get("key2").getAsString());
    }

    @Test
    public void deserialize_message_with_operation() throws Exception {
        // Given
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("clientId", "test-client-id");
        jsonObject.addProperty("data", "test-data");
        jsonObject.addProperty("name", "test-name");
        jsonObject.addProperty("connectionKey", "test-key");
        JsonObject versionObject = new JsonObject();
        versionObject.addProperty("clientId", "operation-client-id");
        versionObject.addProperty("description", "operation-description");
        JsonObject metadataObject = new JsonObject();
        metadataObject.addProperty("key1", "value1");
        metadataObject.addProperty("key2", "value2");
        versionObject.add("metadata", metadataObject);
        jsonObject.add("version", versionObject);

        // When
        Message message = Message.fromEncoded(jsonObject, new ChannelOptions());

        // Then
        assertEquals("test-client-id", message.clientId);
        assertEquals("test-data", message.data);
        assertEquals("test-name", message.name);
        assertEquals("test-key", message.connectionKey);
        assertEquals("operation-client-id", message.version.clientId);
        assertEquals("operation-description", message.version.description);
        assertEquals("value1", message.version.metadata.get("key1"));
        assertEquals("value2", message.version.metadata.get("key2"));
    }

    @Test
    public void deserialize_message_with_unknown_action() throws Exception {
        // Given
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("clientId", "test-client-id");
        jsonObject.addProperty("data", "test-data");
        jsonObject.addProperty("name", "test-name");
        jsonObject.addProperty("action", 10);
        jsonObject.addProperty("serial", "01826232498871-001@abcdefghij:001");

        // When
        Message message = Message.fromEncoded(jsonObject, new ChannelOptions());

        // Then
        assertEquals("test-client-id", message.clientId);
        assertEquals("test-data", message.data);
        assertEquals("test-name", message.name);
        assertNull(message.action);
        assertEquals("01826232498871-001@abcdefghij:001", message.serial);
    }

    @Test
    public void serialize_and_deserialize_with_msgpack() throws Exception {
        // Given
        Message message = new Message("test-name", "test-data");
        message.clientId = "test-client-id";
        message.connectionKey = "test-key";
        message.action = MessageAction.MESSAGE_CREATE;
        message.serial = "01826232498871-001@abcdefghij:001";
        MessageVersion version = new MessageVersion();
        version.clientId = "operation-client-id";
        version.description = "operation-description";
        version.metadata = new HashMap<>();
        version.metadata.put("key1", "value1");
        version.metadata.put("key2", "value2");
        message.version = version;

        // When Encode to MessagePack
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessagePacker packer = Serialisation.msgpackPackerConfig.newPacker(out);
        message.writeMsgpack(packer);
        packer.close();

        // Decode from MessagePack
        MessageUnpacker unpacker = Serialisation.msgpackUnpackerConfig.newUnpacker(out.toByteArray());
        Message unpacked = Message.fromMsgpack(unpacker);
        unpacker.close();

        // Then
        assertEquals("test-client-id", unpacked.clientId);
        assertEquals("test-key", unpacked.connectionKey);
        assertEquals("test-data", unpacked.data);
        assertEquals("test-name", unpacked.name);
        assertEquals(MessageAction.MESSAGE_CREATE, unpacked.action);
        assertEquals("01826232498871-001@abcdefghij:001", unpacked.serial);
        assertEquals("operation-client-id", unpacked.version.clientId);
        assertEquals("operation-description", unpacked.version.description);
        assertEquals("value1", unpacked.version.metadata.get("key1"));
        assertEquals("value2", unpacked.version.metadata.get("key2"));
    }
}
