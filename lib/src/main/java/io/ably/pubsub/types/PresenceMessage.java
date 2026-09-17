package io.ably.pubsub.types;

import java.io.IOException;
import java.lang.reflect.Type;
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
 * A class representing an individual presence update to be sent or received
 * via the Ably Realtime service.
 */
public class PresenceMessage extends BaseMessage implements Cloneable {

    /**
     * Describes the possible actions members in the presence set can emit.
     */
    public enum Action {
        /**
         * A member is not present in the channel.
         * <p>
         * Spec: TP2
         */
        absent,
        /**
         * When subscribing to presence events on a channel that already has members present,
         * this event is emitted for every member already present on the channel before the subscribe listener was registered.
         * <p>
         * Spec: TP2
         */
        present,
        /**
         * A new member has entered the channel.
         * <p>
         * Spec: TP2
         */
        enter,
        /**
         * A member who was present has now left the channel.
         * This may be a result of an explicit request to leave or implicitly when detaching from the channel.
         * Alternatively, if a member's connection is abruptly disconnected and they do not resume their connection within a minute,
         * Ably treats this as a leave event as the client is no longer present.
         * <p>
         * Spec: TP2
         */
        leave,
        /**
         * An already present member has updated their member data.
         * Being notified of member data updates can be very useful, for example,
         * it can be used to update the status of a user when they are typing a message.
         * <p>
         * Spec: TP2
         */
        update;

        public int getValue() { return ordinal(); }
        public static Action findByValue(int value) { return values()[value]; }
    }

    /**
     * The type of {@link PresenceMessage.Action} the PresenceMessage is for.
     * <p>
     * Spec: TP3b
     */
    public Action action;

    /**
     * Default constructor
     */
    public PresenceMessage() {}

    /**
     * Construct a PresenceMessage from an Action and clientId
     * @param action
     * @param clientId
     */
    public PresenceMessage(Action action, String clientId) {
        this(action, clientId, null);
    }

    /**
     * Generic constructor
     * @param action
     * @param clientId
     * @param data
     */
    public PresenceMessage(Action action, String clientId, Object data) {
        this.action = action;
        this.clientId = clientId;
        this.data = data;
    }

    /**
     * Generate a String summary of this PresenceMessage
     * @return string
     */
    public String toString() {
        StringBuilder result = new StringBuilder("{PresenceMessage");
        super.getDetails(result);
        result.append(" action=").append(action.name());
        result.append('}');
        return result.toString();
    }

    @Override
    public Object clone() {
        PresenceMessage result = new PresenceMessage();
        result.id = id;
        result.timestamp = timestamp;
        result.clientId = clientId;
        result.connectionId = connectionId;
        result.encoding = encoding;
        result.data = data;
        result.action = action;
        return result;
    }

    void writeMsgpack(MessagePacker packer) throws IOException {
        int fieldCount = super.countFields();
        ++fieldCount;
        packer.packMapHeader(fieldCount);
        super.writeFields(packer);
        packer.packString("action");
        packer.packInt(action.getValue());
    }

    PresenceMessage readMsgpack(MessageUnpacker unpacker) throws IOException {
        int fieldCount = unpacker.unpackMapHeader();
        for(int i = 0; i < fieldCount; i++) {
            String fieldName = unpacker.unpackString().intern();
            MessageFormat fieldFormat = unpacker.getNextFormat();
            if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

            if(super.readField(unpacker, fieldName, fieldFormat)) { continue; }
            if(fieldName.equals("action")) {
                action = Action.findByValue(unpacker.unpackInt());
            } else {
                Log.v(TAG, "Unexpected field: " + fieldName);
                unpacker.skipValue();
            }
        }
        return this;
    }

    static PresenceMessage fromMsgpack(MessageUnpacker unpacker) throws IOException {
        return (new PresenceMessage()).readMsgpack(unpacker);
    }

    /**
     * Decodes and decrypts a deserialized PresenceMessage-like object using the cipher in {@link ChannelOptions}.
     * Any residual transforms that cannot be decoded or decrypted will be in the encoding property.
     * Intended for users receiving messages from a source other than a REST or Realtime channel (for example a queue)
     * to avoid having to parse the encoding string.
     * <p>
     * Spec: TP4
     *
     * @param messageJsonObject The deserialized PresenceMessage-like object to decode and decrypt.
     * @param channelOptions A {@link ChannelOptions} object containing the cipher.
     * @return A PresenceMessage object.
     * @throws MessageDecodeException
     */
    public static PresenceMessage fromEncoded(JsonObject messageJsonObject, ChannelOptions channelOptions) throws MessageDecodeException {
        try {
            PresenceMessage presenceMessage = Serialisation.gson.fromJson(messageJsonObject, PresenceMessage.class);
            presenceMessage.decode(channelOptions);
            if(presenceMessage.action == null){
                throw MessageDecodeException.fromDescription("Action cannot be null/empty");
            }
            return presenceMessage;
        } catch(Exception e) {
            Log.e(PresenceMessage.class.getName(), e.getMessage(), e);
            throw MessageDecodeException.fromDescription(e.getMessage());
        }
    }

    /**
     * Decodes and decrypts a deserialized PresenceMessage-like object using the cipher in {@link ChannelOptions}.
     * Any residual transforms that cannot be decoded or decrypted will be in the encoding property.
     * Intended for users receiving messages from a source other than a REST or Realtime channel (for example a queue)
     * to avoid having to parse the encoding string.
     * <p>
     * Spec: TP4
     *
     * @param messageJson The deserialized PresenceMessage-like object to decode and decrypt.
     * @param channelOptions A {@link ChannelOptions} object containing the cipher.
     * @return A PresenceMessage object.
     * @throws MessageDecodeException
     */
    public static PresenceMessage fromEncoded(String messageJson, ChannelOptions channelOptions) throws MessageDecodeException {
        try {
            JsonObject jsonObject = Serialisation.gson.fromJson(messageJson, JsonObject.class);
            return fromEncoded(jsonObject, channelOptions);
        } catch(Exception e) {
            Log.e(PresenceMessage.class.getName(), e.getMessage(), e);
            throw MessageDecodeException.fromDescription(e.getMessage());
        }
    }

    /**
     * Decodes and decrypts an array of deserialized PresenceMessage-like object using the cipher in {@link ChannelOptions}.
     * Any residual transforms that cannot be decoded or decrypted will be in the encoding property.
     * Intended for users receiving messages from a source other than a REST or Realtime channel (for example a queue)
     * to avoid having to parse the encoding string.
     * <p>
     * Spec: TP4
     *
     * @param presenceMsgArray An array of deserialized PresenceMessage-like objects to decode and decrypt.
     * @param channelOptions A {@link ChannelOptions} object containing the cipher.
     * @return An array of PresenceMessage object.
     * @throws MessageDecodeException
     */
    public static PresenceMessage[] fromEncodedArray(JsonArray presenceMsgArray, ChannelOptions channelOptions) throws MessageDecodeException {
        try {
            PresenceMessage[] messages = new PresenceMessage[presenceMsgArray.size()];
            for(int index = 0; index < presenceMsgArray.size(); index++) {
                JsonElement jsonElement = presenceMsgArray.get(index);
                if(!jsonElement.isJsonObject()) {
                    throw new JsonParseException("Not all JSON elements are of type JSON Object.");
                }
                messages[index] = fromEncoded(jsonElement.getAsJsonObject(), channelOptions);
            }
            return messages;
        } catch(Exception e) {
            Log.e(PresenceMessage.class.getName(), e.getMessage(), e);
            throw MessageDecodeException.fromDescription(e.getMessage());
        }
    }

    /**
     * Decodes and decrypts an array of deserialized PresenceMessage-like object using the cipher in {@link ChannelOptions}.
     * Any residual transforms that cannot be decoded or decrypted will be in the encoding property.
     * Intended for users receiving messages from a source other than a REST or Realtime channel (for example a queue)
     * to avoid having to parse the encoding string.
     * <p>
     * Spec: TP4
     *
     * @param presenceMsgArray An array of deserialized PresenceMessage-like objects to decode and decrypt.
     * @param channelOptions A {@link ChannelOptions} object containing the cipher.
     * @return An array of PresenceMessage object.
     * @throws MessageDecodeException
     */
    public static PresenceMessage[] fromEncodedArray(String presenceMsgArray, ChannelOptions channelOptions) throws MessageDecodeException {
        try {
            JsonArray jsonArray = Serialisation.gson.fromJson(presenceMsgArray, JsonArray.class);
            return fromEncodedArray(jsonArray, channelOptions);
        } catch(Exception e) {
            Log.e(PresenceMessage.class.getName(), e.getMessage(), e);
            throw MessageDecodeException.fromDescription(e.getMessage());
        }
    }

    public static class ActionSerializer implements JsonDeserializer<Action> {
        @Override
        public Action deserialize(JsonElement json, Type t, JsonDeserializationContext ctx)
                throws JsonParseException {
            return Action.findByValue(json.getAsInt());
        }
    }

    public static class Serializer implements JsonSerializer<PresenceMessage> {
        @Override
        public JsonElement serialize(PresenceMessage message, Type typeOfMessage, JsonSerializationContext ctx) {
            final JsonObject json = BaseMessage.toJsonObject(message);
            if(message.action != null) json.addProperty("action", message.action.getValue());
            return json;
        }
    }

    /**
     * Combines clientId and connectionId to ensure that multiple connected clients with an identical clientId are uniquely identifiable.
     * A string function that returns the combined clientId and connectionId.
     * <p>
     * Spec: TP3h
     * @return A combination of clientId and connectionId.
     */
    public String memberKey() {
        return connectionId + ':' + clientId;
    }

    private static final String TAG = PresenceMessage.class.getName();
}
