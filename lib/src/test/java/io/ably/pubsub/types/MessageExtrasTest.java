package io.ably.pubsub.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ably.pubsub.util.Serialisation;
import org.junit.Test;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

public class MessageExtrasTest {
    /**
     * Construct an instance from a JSON source and validate that the
     * serialised JSON is the same.
     */
    @Test
    public void raw() {
        final JsonObject objectA = new JsonObject();
        objectA.addProperty("someKey", "someValue");

        final JsonObject objectB = new JsonObject();
        objectB.addProperty("someOtherKey", "someValue");

        final MessageExtras messageExtras = new MessageExtras(objectA);
        assertNull(messageExtras.getDelta());

        final MessageExtras.Serializer serializer = new MessageExtras.Serializer();
        final JsonElement serialised = serializer.serialize(messageExtras, null, null);

        assertEquals(objectA, serialised);
        assertNotEquals(objectB, serialised);
        assertNotEquals(objectB, objectA);
    }

    @Test
    public void rawViaMessagePack() throws IOException {
        final JsonObject object = new JsonObject();
        object.addProperty("foo", "bar");
        object.addProperty("cliché", "cache");
        final MessageExtras messageExtras = new MessageExtras(object);

        // Encode to MessagePack
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final MessagePacker packer = Serialisation.msgpackPackerConfig.newPacker(out);
        messageExtras.write(packer);
        packer.flush();

        // Decode from MessagePack
        MessageUnpacker unpacker = Serialisation.msgpackUnpackerConfig.newUnpacker(out.toByteArray());
        final MessageExtras unpacked = MessageExtras.read(unpacker);

        assertEquals(messageExtras, unpacked);
    }

    @Test(expected = NullPointerException.class)
    public void rawNullArgument() {
        new MessageExtras((JsonObject)null);
    }
}
