package io.ably.lib.types;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ably.lib.util.Serialisation;
import org.junit.Test;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.ByteArrayOutputStream;

public class PresenceMessageTest {

    private final PresenceMessage.Serializer serializer = new PresenceMessage.Serializer();

    /**
     * Verify the 4-arg PresenceMessage(Action, String, Object, MessageExtras) constructor
     * correctly sets all fields, and the 3-arg constructor sets extras = null.
     */
    @Test
    public void presenceMessage_constructor_with_extras() {
        // Given
        JsonObject extrasJson = new JsonObject();
        JsonObject headers = new JsonObject();
        headers.addProperty("key", "value");
        extrasJson.add("headers", headers);
        MessageExtras extras = new MessageExtras(extrasJson);

        // When - 4-arg constructor
        PresenceMessage msg4 = new PresenceMessage(PresenceMessage.Action.enter, "client1", "data1", extras);

        // Then
        assertEquals(PresenceMessage.Action.enter, msg4.action);
        assertEquals("client1", msg4.clientId);
        assertEquals("data1", msg4.data);
        assertNotNull("Extras should be set", msg4.extras);
        assertEquals("value", msg4.extras.asJsonObject().getAsJsonObject("headers").get("key").getAsString());

        // When - 3-arg constructor
        PresenceMessage msg3 = new PresenceMessage(PresenceMessage.Action.update, "client2", "data2");

        // Then
        assertEquals(PresenceMessage.Action.update, msg3.action);
        assertEquals("client2", msg3.clientId);
        assertEquals("data2", msg3.data);
        assertNull("Extras should be null for 3-arg constructor", msg3.extras);

        // When - 2-arg constructor
        PresenceMessage msg2 = new PresenceMessage(PresenceMessage.Action.leave, "client3");

        // Then
        assertEquals(PresenceMessage.Action.leave, msg2.action);
        assertEquals("client3", msg2.clientId);
        assertNull("Data should be null for 2-arg constructor", msg2.data);
        assertNull("Extras should be null for 2-arg constructor", msg2.extras);
    }

    /**
     * Verify that clone() on a PresenceMessage with extras copies the extras field.
     */
    @Test
    public void presenceMessage_clone_copies_extras() {
        // Given
        JsonObject extrasJson = new JsonObject();
        JsonObject headers = new JsonObject();
        headers.addProperty("role", "admin");
        extrasJson.add("headers", headers);
        MessageExtras extras = new MessageExtras(extrasJson);

        PresenceMessage original = new PresenceMessage(PresenceMessage.Action.enter, "client1", "data1", extras);
        original.id = "test-id";
        original.connectionId = "test-connection";
        original.timestamp = 12345L;

        // When
        PresenceMessage cloned = (PresenceMessage) original.clone();

        // Then
        assertEquals("Action should be cloned", original.action, cloned.action);
        assertEquals("ClientId should be cloned", original.clientId, cloned.clientId);
        assertEquals("Data should be cloned", original.data, cloned.data);
        assertEquals("Id should be cloned", original.id, cloned.id);
        assertEquals("ConnectionId should be cloned", original.connectionId, cloned.connectionId);
        assertEquals("Timestamp should be cloned", original.timestamp, cloned.timestamp);
        assertNotNull("Extras should not be null on clone", cloned.extras);
        assertEquals("Extras should match original",
            original.extras.asJsonObject(), cloned.extras.asJsonObject());
    }

    /**
     * Serialize a PresenceMessage with extras to JSON and deserialize it back;
     * assert the extras are equal. Also verify that an invalid (non-object) extras
     * value in JSON produces the expected error.
     */
    @Test
    public void presenceMessage_extras_json_roundtrip() {
        // Given
        JsonObject extrasJson = new JsonObject();
        JsonObject headers = new JsonObject();
        headers.addProperty("foo", "bar");
        headers.addProperty("count", 42);
        extrasJson.add("headers", headers);
        MessageExtras extras = new MessageExtras(extrasJson);

        PresenceMessage original = new PresenceMessage(PresenceMessage.Action.enter, "client1", "test-data", extras);

        // When - serialize
        JsonElement serialized = serializer.serialize(original, null, null);
        JsonObject json = serialized.getAsJsonObject();

        // Then - verify JSON structure
        assertNotNull("Extras should be in JSON", json.get("extras"));
        JsonObject jsonExtras = json.getAsJsonObject("extras");
        assertEquals("bar", jsonExtras.getAsJsonObject("headers").get("foo").getAsString());
        assertEquals(42, jsonExtras.getAsJsonObject("headers").get("count").getAsInt());

        // When - deserialize
        PresenceMessage deserialized = serializer.deserialize(json, null, null);

        // Then - verify round-trip
        assertEquals("Action should survive round-trip", PresenceMessage.Action.enter, deserialized.action);
        assertEquals("ClientId should survive round-trip", "client1", deserialized.clientId);
        assertEquals("Data should survive round-trip", "test-data", deserialized.data);
        assertNotNull("Extras should survive round-trip", deserialized.extras);
        JsonObject deserializedHeaders = deserialized.extras.asJsonObject().getAsJsonObject("headers");
        assertEquals("foo header should survive round-trip", "bar", deserializedHeaders.get("foo").getAsString());
        assertEquals("count header should survive round-trip", 42, deserializedHeaders.get("count").getAsInt());

        // Verify null extras in JSON round-trips correctly
        PresenceMessage noExtras = new PresenceMessage(PresenceMessage.Action.leave, "client2", "data2");
        JsonElement serializedNoExtras = serializer.serialize(noExtras, null, null);
        JsonObject jsonNoExtras = serializedNoExtras.getAsJsonObject();
        assertNull("Extras should not be in JSON when null", jsonNoExtras.get("extras"));
        PresenceMessage deserializedNoExtras = serializer.deserialize(jsonNoExtras, null, null);
        assertNull("Extras should remain null after round-trip", deserializedNoExtras.extras);

        // Verify invalid (non-object) extras produces error
        JsonObject invalidJson = new JsonObject();
        invalidJson.addProperty("action", PresenceMessage.Action.enter.getValue());
        invalidJson.addProperty("clientId", "client1");
        invalidJson.addProperty("extras", "not-an-object");
        try {
            serializer.deserialize(invalidJson, null, null);
            fail("Expected exception for non-object extras");
        } catch (Exception e) {
            // Expected - invalid extras should cause an error
        }
    }

    /**
     * Serialize a PresenceMessage with extras via writeMsgpack / fromMsgpack
     * and assert the extras survive the round-trip.
     */
    @Test
    public void presenceMessage_extras_msgpack_roundtrip() throws Exception {
        // Given - message with extras
        JsonObject extrasJson = new JsonObject();
        JsonObject headers = new JsonObject();
        headers.addProperty("key", "value");
        headers.addProperty("num", 99);
        extrasJson.add("headers", headers);
        MessageExtras extras = new MessageExtras(extrasJson);

        PresenceMessage original = new PresenceMessage(PresenceMessage.Action.update, "client1", "test-data", extras);

        // When - encode to MessagePack
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessagePacker packer = Serialisation.msgpackPackerConfig.newPacker(out);
        original.writeMsgpack(packer);
        packer.close();

        // Decode from MessagePack
        MessageUnpacker unpacker = Serialisation.msgpackUnpackerConfig.newUnpacker(out.toByteArray());
        PresenceMessage unpacked = PresenceMessage.fromMsgpack(unpacker);
        unpacker.close();

        // Then
        assertEquals("Action should survive msgpack round-trip", PresenceMessage.Action.update, unpacked.action);
        assertEquals("ClientId should survive msgpack round-trip", "client1", unpacked.clientId);
        assertEquals("Data should survive msgpack round-trip", "test-data", unpacked.data);
        assertNotNull("Extras should survive msgpack round-trip", unpacked.extras);
        JsonObject unpackedHeaders = unpacked.extras.asJsonObject().getAsJsonObject("headers");
        assertEquals("key header should survive round-trip", "value", unpackedHeaders.get("key").getAsString());
        assertEquals("num header should survive round-trip", 99, unpackedHeaders.get("num").getAsInt());

        // Verify null extras case - field count should be different
        PresenceMessage noExtras = new PresenceMessage(PresenceMessage.Action.leave, "client2", "data2");

        ByteArrayOutputStream outNoExtras = new ByteArrayOutputStream();
        MessagePacker packerNoExtras = Serialisation.msgpackPackerConfig.newPacker(outNoExtras);
        noExtras.writeMsgpack(packerNoExtras);
        packerNoExtras.close();

        MessageUnpacker unpackerNoExtras = Serialisation.msgpackUnpackerConfig.newUnpacker(outNoExtras.toByteArray());
        PresenceMessage unpackedNoExtras = PresenceMessage.fromMsgpack(unpackerNoExtras);
        unpackerNoExtras.close();

        assertEquals("Action should survive round-trip", PresenceMessage.Action.leave, unpackedNoExtras.action);
        assertEquals("ClientId should survive round-trip", "client2", unpackedNoExtras.clientId);
        assertEquals("Data should survive round-trip", "data2", unpackedNoExtras.data);
        assertNull("Extras should be null when not set", unpackedNoExtras.extras);

        // Verify the packed sizes differ (extras adds fields)
        assertTrue("Message with extras should be larger than without",
            out.toByteArray().length > outNoExtras.toByteArray().length);
    }
}
