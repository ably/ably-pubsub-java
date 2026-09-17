package io.ably.pubsub.types;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.ably.pubsub.util.Serialisation;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import io.ably.pubsub.util.Log;

/**
 * Contains an individual message that is sent to, or received from, Ably.
 */
public class Message extends BaseMessage {

    /**
     * The event name.
     * <p>
     * Spec: TM2g
     */
    public String name;

    /**
     * A MessageExtras object of arbitrary key-value pairs that may contain metadata, and/or ancillary payloads.
     * Valid payloads include {@link DeltaExtras}, {@link JsonObject}.
     * <p>
     * Spec: TM2i
     */
    public MessageExtras extras;

    /**
     * Key needed only in case one client is publishing this message on behalf of another client.
     * The connectionKey will never be populated for messages received.
     * <p>
     * Spec: TM2h
     */
    public String connectionKey;

    /**
     * (TM2r) serial string – an opaque string that uniquely identifies the message.
     */
    public String serial;

    /**
     * (TM2s) The latest version of the message, containing version-specific metadata.
     */
    public MessageVersion version;

    /**
     * (TM2j) action enum
     */
    public MessageAction action;

    /**
     * (TM2q) Annotations associated with this message.
     */
    public MessageAnnotations annotations;



    private static final String NAME = "name";
    private static final String EXTRAS = "extras";
    private static final String CONNECTION_KEY = "connectionKey";
    private static final String SERIAL = "serial";
    private static final String VERSION = "version";
    private static final String ACTION = "action";
    private static final String ANNOTATIONS = "annotations";

    /**
     * Default constructor
     */
    public Message() {
    }

    /**
     * Construct a Message object with an event name and payload.
     * <p>
     * Spec: TM2
     *
     * @param name The event name.
     * @param data The message payload.
     */
    public Message(String name, Object data) {
        this(name, data, null, null);
    }

    /**
     * Construct a Message object with an event name, payload, and a unique client ID.
     * <p>
     * Spec: TM2
     *
     * @param name The event name.
     * @param data The message payload.
     * @param clientId The client ID of the publisher of this message.
     */
    public Message(String name, Object data, String clientId) {
        this(name, data, clientId, null);
    }

    /**
     * Construct a Message object with an event name, payload, and a extras.
     * <p>
     * Spec: TM2
     *
     * @param name The event name.
     * @param data The message payload.
     * @param extras Extra information to be sent with this message.
     */
    public Message(String name, Object data, MessageExtras extras) {
        this(name, data, null, extras);
    }

    /**
     * Construct a Message object with an event name, payload, extras, and a unique client ID.
     * <p>
     * Spec: TM2
     *
     * @param name The event name.
     * @param data The message payload.
     * @param clientId The client ID of the publisher of this message.
     * @param extras Extra information to be sent with this message.
     */
    public Message(String name, Object data, String clientId, MessageExtras extras) {
        this.name = name;
        this.clientId = clientId;
        this.data = data;
        this.extras = extras;
    }

    /**
     * Generate a String summary of this Message
     * @return string
     */
    public String toString() {
        StringBuilder result = new StringBuilder("{Message");
        super.getDetails(result);
        if(name != null)
            result.append(" name=").append(name);
        result.append('}');
        return result.toString();
    }

    void writeMsgpack(MessagePacker packer) throws IOException {
        int fieldCount = super.countFields();
        if(name != null) ++fieldCount;
        if(extras != null) ++fieldCount;
        if(connectionKey != null) ++fieldCount;
        if(serial != null) ++fieldCount;
        if(version != null) ++fieldCount;
        if(action != null) ++fieldCount;
        if(annotations != null) ++fieldCount;

        packer.packMapHeader(fieldCount);
        super.writeFields(packer);
        if(name != null) {
            packer.packString(NAME);
            packer.packString(name);
        }
        if(extras != null) {
            packer.packString(EXTRAS);
            extras.write(packer);
        }
        if(connectionKey != null) {
            packer.packString(CONNECTION_KEY);
            packer.packString(connectionKey);
        }
        if(serial != null) {
            packer.packString(SERIAL);
            packer.packString(serial);
        }
        if(version != null) {
            packer.packString(VERSION);
            version.writeMsgpack(packer);
        }
        if(action != null) {
            packer.packString(ACTION);
            packer.packInt(action.ordinal());
        }
        if(annotations != null) {
            packer.packString(ANNOTATIONS);
            annotations.writeMsgpack(packer);
        }
    }

    Message readMsgpack(MessageUnpacker unpacker) throws IOException {
        int fieldCount = unpacker.unpackMapHeader();
        for(int i = 0; i < fieldCount; i++) {
            String fieldName = unpacker.unpackString().intern();
            MessageFormat fieldFormat = unpacker.getNextFormat();
            if(fieldFormat.equals(MessageFormat.NIL)) {
                unpacker.unpackNil();
                continue;
            }

            if(super.readField(unpacker, fieldName, fieldFormat)) {
                continue;
            }
            if(fieldName.equals(NAME)) {
                name = unpacker.unpackString();
            } else if (fieldName.equals(EXTRAS)) {
                extras = MessageExtras.read(unpacker);
            } else if (fieldName.equals(CONNECTION_KEY)) {
                connectionKey = unpacker.unpackString();
            } else if (fieldName.equals(SERIAL)) {
                serial = unpacker.unpackString();
            } else if (fieldName.equals(VERSION)) {
                version = MessageVersion.read(unpacker);
            } else if (fieldName.equals(ACTION)) {
                action = MessageAction.tryFindByOrdinal(unpacker.unpackInt());
            } else if (fieldName.equals(ANNOTATIONS)) {
                annotations = MessageAnnotations.read(unpacker);
            }
            else {
                Log.v(TAG, "Unexpected field: " + fieldName);
                unpacker.skipValue();
            }
        }
        return this;
    }

    /**
     * Sets the channel names and message contents to {@link io.ably.pubsub.realtime.RealtimeClient#publishBatch}.
     */
    public static class Batch {
        /**
         * An array of channel names to publish messages to.
         */
        public String[] channels;
        /**
         * An array of {@link Message} objects to publish.
         */
        public Message[] messages;

        public Batch(String channel, Message[] messages) {
            if(channel == null || channel.isEmpty()) throw new IllegalArgumentException("A Batch spec cannot have an empty set of channels");
            if(messages == null || messages.length == 0) throw new IllegalArgumentException("A Batch spec cannot have an empty set of messages");
            this.channels = new String[]{channel};
            this.messages = messages;
        }

        public Batch(String[] channels, Message[] messages) {
            if(channels == null || channels.length == 0) throw new IllegalArgumentException("A Batch spec cannot have an empty set of channels");
            if(messages == null || messages.length == 0) throw new IllegalArgumentException("A Batch spec cannot have an empty set of messages");
            this.channels = channels;
            this.messages = messages;
        }

        public Batch(Collection<String> channels, Collection<Message> messages) {
            this(channels.toArray(new String[0]), messages.toArray(new Message[0]));
        }

        public void writeMsgpack(MessagePacker packer) throws IOException {
            packer.packMapHeader(2);
            packer.packString("channels");
            packer.packArrayHeader(channels.length);
            for(String ch : channels) packer.packString(ch);
            packer.packString("messages");
            MessageSerializer.writeMsgpackArray(messages, packer);
        }
    }

    static Message fromMsgpack(MessageUnpacker unpacker) throws IOException {
        return (new Message()).readMsgpack(unpacker);
    }

    /**
     * A static factory method to create a Message object from a deserialized Message-like object encoded using Ably's wire protocol.
     * <p>
     * Spec: TM3
     * @param messageJson A Message-like deserialized object.
     * @param channelOptions A {@link ChannelOptions} object.
     *                       If you have an encrypted channel, use this to allow the library to decrypt the data.
     * @return A Message object.
     * @throws MessageDecodeException
     */
    public static Message fromEncoded(JsonObject messageJson, ChannelOptions channelOptions) throws MessageDecodeException {
        try {
            Message message = Serialisation.gson.fromJson(messageJson, Message.class);
            message.decode(channelOptions);
            return message;
        } catch(Exception e) {
            Log.e(Message.class.getName(), e.getMessage(), e);
            throw MessageDecodeException.fromDescription(e.getMessage());
        }
    }

    /**
     * A static factory method to create a Message object from a deserialized Message-like object encoded using Ably's wire protocol.
     * <p>
     * Spec: TM3
     * @param messageJson A Message-like deserialized object.
     * @param channelOptions A {@link ChannelOptions} object.
     *                       If you have an encrypted channel, use this to allow the library to decrypt the data.
     * @return A Message object.
     * @throws MessageDecodeException
     */
    public static Message fromEncoded(String messageJson, ChannelOptions channelOptions) throws MessageDecodeException {
        try {
            JsonObject jsonObject = Serialisation.gson.fromJson(messageJson, JsonObject.class);
            return fromEncoded(jsonObject.getAsJsonObject(), channelOptions);
        } catch(Exception e) {
            Log.e(Message.class.getName(), e.getMessage(), e);
            throw MessageDecodeException.fromDescription(e.getMessage());
        }
    }

    /**
     * A static factory method to create an array of Message objects from an array of deserialized
     * Message-like object encoded using Ably's wire protocol.
     * <p>
     * Spec: TM3
     * @param messageArray An array of Message-like deserialized objects.
     * @param channelOptions A {@link ChannelOptions} object.
     *                       If you have an encrypted channel, use this to allow the library to decrypt the data.
     * @return An array of {@link Message} objects.
     * @throws MessageDecodeException
     */
    public static Message[] fromEncodedArray(JsonArray messageArray, ChannelOptions channelOptions) throws MessageDecodeException {
        try {
            Message[] messages = new Message[messageArray.size()];
            for(int index = 0; index < messageArray.size(); index++) {
                JsonElement jsonElement = messageArray.get(index);
                if(!jsonElement.isJsonObject()) {
                    throw new JsonParseException("Not all JSON elements are of type JSON Object.");
                }
                messages[index] = fromEncoded(jsonElement.getAsJsonObject(), channelOptions);
            }
            return messages;
        } catch(Exception e) {
            Log.e(Message.class.getName(), e.getMessage(), e);
            throw MessageDecodeException.fromDescription(e.getMessage());
        }
    }

    /**
     * A static factory method to create an array of Message objects from an array of deserialized
     * Message-like object encoded using Ably's wire protocol.
     * <p>
     * Spec: TM3
     * @param messagesArray An array of Message-like deserialized objects.
     * @param channelOptions A {@link ChannelOptions} object.
     *                       If you have an encrypted channel, use this to allow the library to decrypt the data.
     * @return An array of {@link Message} objects.
     * @throws MessageDecodeException
     */
    public static Message[] fromEncodedArray(String messagesArray, ChannelOptions channelOptions) throws MessageDecodeException {
        try {
            JsonArray jsonArray = Serialisation.gson.fromJson(messagesArray, JsonArray.class);
            return fromEncodedArray(jsonArray, channelOptions);
        } catch(Exception e) {
            Log.e(Message.class.getName(), e.getMessage(), e);
            throw MessageDecodeException.fromDescription(e.getMessage());
        }
    }

    @Override
    protected void read(final JsonObject map) throws MessageDecodeException {
        super.read(map);

        name = readString(map, NAME);

        final JsonElement extrasElement = map.get(EXTRAS);
        if (null != extrasElement) {
            if (!(extrasElement instanceof JsonObject)) {
                throw MessageDecodeException.fromDescription("Message extras is of type \"" + extrasElement.getClass() + "\" when expected a JSON object.");
            }
            extras = MessageExtras.read((JsonObject) extrasElement);
        }
        connectionKey = readString(map, CONNECTION_KEY);

        serial = readString(map, SERIAL);

        final JsonElement versionElement = map.get(VERSION);
        if (versionElement != null) {
            version = MessageVersion.read(versionElement);
        }
        Integer actionOrdinal = readInt(map, ACTION);
        action = actionOrdinal == null ? null : MessageAction.tryFindByOrdinal(actionOrdinal);

        final JsonElement annotationsElement = map.get(ANNOTATIONS);
        if (annotationsElement != null) {
            annotations = MessageAnnotations.read(annotationsElement);
        }
    }

    public static class Serializer implements JsonSerializer<Message>, JsonDeserializer<Message> {
        @Override
        public JsonElement serialize(Message message, Type typeOfMessage, JsonSerializationContext ctx) {
            final JsonObject json = BaseMessage.toJsonObject(message);
            if (message.name != null) {
                json.addProperty(NAME, message.name);
            }
            if (message.extras != null) {
                json.add(EXTRAS, Serialisation.gson.toJsonTree(message.extras));
            }
            if (message.connectionKey != null) {
                json.addProperty(CONNECTION_KEY, message.connectionKey);
            }
            if (message.serial != null) {
                json.addProperty(SERIAL, message.serial);
            }
            if (message.version != null) {
                json.add(VERSION, message.version.toJsonTree());
            }
            if (message.action != null) {
                json.addProperty(ACTION, message.action.ordinal());
            }
            if (message.annotations != null) {
                json.add(ANNOTATIONS, message.annotations.toJsonTree());
            }
            return json;
        }

        @Override
        public Message deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (!(json instanceof JsonObject)) {
                throw new JsonParseException("Expected an object but got \"" + json.getClass() + "\".");
            }

            final Message message = new Message();
            try {
                message.read((JsonObject)json);
            } catch (MessageDecodeException e) {
                Log.e(Message.class.getName(), e.getMessage(), e);
                throw new JsonParseException("Failed to deserialize Message from JSON.", e);
            }
            return message;
        }
    }

    private static final String TAG = Message.class.getName();
}
