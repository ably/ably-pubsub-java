package io.ably.pubsub.types;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.ably.pubsub.util.Log;
import io.ably.pubsub.util.Serialisation;
import org.jetbrains.annotations.NotNull;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.lang.reflect.Type;

public class Annotation extends BaseMessage {

    private static final String TAG = Annotation.class.getName();

    private static final String ACTION = "action";
    private static final String SERIAL = "serial";
    private static final String MESSAGE_SERIAL = "messageSerial";
    private static final String TYPE = "type";
    private static final String NAME = "name";
    private static final String COUNT = "count";
    private static final String EXTRAS = "extras";

    /**
     * (TAN2b) The action, whether this is an annotation being added or removed,
     * one of the AnnotationAction enum values.
     */
    public AnnotationAction action;

    /**
     * (TAN2i) This annotation's unique serial (lexicographically totally ordered).
     */
    public String serial;

    /**
     * (TAN2j) The serial of the message (of type `MESSAGE_CREATE`) that this annotation is annotating.
     */
    public String messageSerial;

    /**
     * (TAN2k) The type of annotation it is, typically some identifier together with an aggregation method;
     * for example: "emoji:distinct.v1". Handled opaquely by the SDK and validated serverside. |
     */
    public String type;

    /**
     * (TAN2d) The name of this annotation. This is the field that most annotation aggregations will operate on.
     * For example, using "distinct.v1" aggregation (specified in the type), the message summary will show a list
     * of clients who have published an annotation with each distinct annotation.name.
     */
    public String name;

    /**
     * (TAN2e) An optional count, only relevant to certain aggregation methods,
     * see aggregation methods documentation for more info.
     */
    public Integer count;

    /**
     * (TAN2l) A JSON object for metadata and/or ancillary payloads.
     */
    public MessageExtras extras;

    public static Annotation fromMsgpack(MessageUnpacker unpacker) throws IOException {
        return (new Annotation()).readMsgpack(unpacker);
    }

    void writeMsgpack(MessagePacker packer) throws IOException {
        int fieldCount = super.countFields();
        if (action != null) ++fieldCount;
        if (serial != null) ++fieldCount;
        if (messageSerial != null) ++fieldCount;
        if (type != null) ++fieldCount;
        if (name != null) ++fieldCount;
        if (count != null) ++fieldCount;
        if (extras != null) ++fieldCount;

        packer.packMapHeader(fieldCount);
        super.writeFields(packer);

        if (action != null) {
            packer.packString(ACTION);
            packer.packInt(action.ordinal());
        }

        if (serial != null) {
            packer.packString(SERIAL);
            packer.packString(serial);
        }

        if (messageSerial != null) {
            packer.packString(MESSAGE_SERIAL);
            packer.packString(messageSerial);
        }

        if (type != null) {
            packer.packString(TYPE);
            packer.packString(type);
        }

        if (name != null) {
            packer.packString(NAME);
            packer.packString(name);
        }

        if (count != null) {
            packer.packString(COUNT);
            packer.packInt(count);
        }

        if (extras != null) {
            packer.packString(EXTRAS);
            extras.write(packer);
        }
    }

    Annotation readMsgpack(MessageUnpacker unpacker) throws IOException {
        int fieldCount = unpacker.unpackMapHeader();
        for (int i = 0; i < fieldCount; i++) {
            String fieldName = unpacker.unpackString().intern();
            MessageFormat fieldFormat = unpacker.getNextFormat();
            if (fieldFormat.equals(MessageFormat.NIL)) {
                unpacker.unpackNil();
                continue;
            }

            if (super.readField(unpacker, fieldName, fieldFormat)) {
                continue;
            }
            if (fieldName.equals(ACTION)) {
                action = AnnotationAction.tryFindByOrdinal(unpacker.unpackInt());
            } else if (fieldName.equals(SERIAL)) {
                serial = unpacker.unpackString();
            } else if (fieldName.equals(MESSAGE_SERIAL)) {
                messageSerial = unpacker.unpackString();
            } else if (fieldName.equals(TYPE)) {
                type = unpacker.unpackString();
            } else if (fieldName.equals(NAME)) {
                name = unpacker.unpackString();
            } else if (fieldName.equals(COUNT)) {
                count = unpacker.unpackInt();
            } else if (fieldName.equals(EXTRAS)) {
                extras = MessageExtras.read(unpacker);
            } else {
                Log.v(TAG, "Unexpected field: " + fieldName);
                unpacker.skipValue();
            }
        }
        return this;
    }

    @Override
    protected void read(final JsonObject map) throws MessageDecodeException {
        super.read(map);

        Integer actionOrdinal = readInt(map, ACTION);
        action = actionOrdinal == null ? null : AnnotationAction.tryFindByOrdinal(actionOrdinal);
        serial = readString(map, SERIAL);
        messageSerial = readString(map, MESSAGE_SERIAL);

        type = readString(map, TYPE);
        name = readString(map, NAME);
        count = readInt(map, COUNT);

        final JsonElement extrasElement = map.get(EXTRAS);
        if (extrasElement != null) {
            if (!extrasElement.isJsonObject()) {
                throw MessageDecodeException.fromDescription("Message extras is of type \"" + extrasElement.getClass() + "\" when expected a JSON object.");
            }
            extras = MessageExtras.read((JsonObject) extrasElement);
        }
    }

    public static class Serializer implements JsonSerializer<Annotation>, JsonDeserializer<Annotation> {
        @Override
        public JsonElement serialize(Annotation annotation, Type typeOfMessage, JsonSerializationContext ctx) {
            final JsonObject json = BaseMessage.toJsonObject(annotation);
            if (annotation.action != null) {
                json.addProperty(ACTION, annotation.action.ordinal());
            }

            if (annotation.serial != null) {
                json.addProperty(SERIAL, annotation.serial);
            }

            if (annotation.messageSerial != null) {
                json.addProperty(MESSAGE_SERIAL, annotation.messageSerial);
            }

            if (annotation.type != null) {
                json.addProperty(TYPE, annotation.type);
            }

            if (annotation.name != null) {
                json.addProperty(NAME, annotation.name);
            }

            if (annotation.count != null) {
                json.addProperty(COUNT, annotation.count);
            }

            if (annotation.extras != null) {
                json.add(EXTRAS, Serialisation.gson.toJsonTree(annotation.extras));
            }

            return json;
        }

        @Override
        public Annotation deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (!json.isJsonObject()) {
                throw new JsonParseException("Expected an object but got \"" + json.getClass() + "\".");
            }

            final Annotation annotation = new Annotation();

            try {
                annotation.read((JsonObject) json);
            } catch (MessageDecodeException e) {
                Log.e(TAG, e.getMessage(), e);
                throw new JsonParseException("Failed to deserialize Message from JSON.", e);
            }

            return annotation;
        }
    }

    /**
     * Generate a String summary of this Message
     * @return string
     */
    @Override
    public @NotNull String toString() {
        StringBuilder result = new StringBuilder("{");
        super.getDetails(result);
        result.append(" name=").append(name);
        result.append(" serial=").append(serial);
        result.append(" messageSerial=").append(messageSerial);
        result.append(" type=").append(type);
        result.append('}');
        return result.toString();
    }

    public static class ActionSerializer implements JsonSerializer<AnnotationAction>, JsonDeserializer<AnnotationAction> {
        @Override
        public AnnotationAction deserialize(JsonElement json, Type t, JsonDeserializationContext ctx)
            throws JsonParseException {
            return AnnotationAction.tryFindByOrdinal(json.getAsInt());
        }

        @Override
        public JsonElement serialize(AnnotationAction action, Type t, JsonSerializationContext ctx) {
            return new JsonPrimitive(action.ordinal());
        }
    }
}
