package io.ably.pubsub.types;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

import com.google.gson.annotations.JsonAdapter;
import io.ably.pubsub.liveobjects.serialization.ObjectJsonSerializer;
import io.ably.pubsub.liveobjects.serialization.ObjectSerializer;
import org.jetbrains.annotations.Nullable;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import io.ably.pubsub.util.Log;

/**
 * A message sent and received over the Realtime protocol.
 * A ProtocolMessage always relates to a single channel only, but
 * can contain multiple individual Messages or PresenceMessages.
 * ProtocolMessages are serially numbered on a connection.
 * See the Ably client library developer documentation for further
 * details on the members of a ProtocolMessage.
 */
public class ProtocolMessage {
    public enum Action {
        heartbeat, // 0
        ack, // 1
        nack, // 2
        connect, // 3
        connected, // 4
        disconnect, // 5
        disconnected, // 6
        close, // 7
        closed, // 8
        error, // 9
        attach, // 10
        attached, // 11
        detach, // 12
        detached, // 13
        presence, // 14
        message, // 15
        sync, // 16
        auth, // 17
        activate, // 18
        object, // 19
        object_sync, // 20
        annotation; // 21

        public int getValue() { return ordinal(); }
        public static Action findByValue(int value) { return values()[value]; }
    }

    public enum Flag {
        /* Channel attach state flags */
        has_presence(0),
        has_backlog(1),
        resumed(2),
        attach_resume(5),
        /* Has object flag */
        has_objects(7),
        /* Channel mode flags */
        presence(16),
        publish(17),
        subscribe(18),
        presence_subscribe(19),
        // 20 reserved (TR3v)
        /* Annotation flags */
        annotation_publish(21), // (TR3w)
        annotation_subscribe(22), // (TR3x)
        // 23 reserved (TR3v)
        /* Object flags */
        object_subscribe(24), // (TR3y)
        object_publish(25); // (TR3z)

        private final int mask;

        Flag(int offset) {
            this.mask = 1 << offset;
        }

        public int getMask() {
            return this.mask;
        }
    }

    /**
     * (RTN7a)
     */
    public static boolean ackRequired(ProtocolMessage msg) {
        return (msg.action == Action.message || msg.action == Action.presence
            || msg.action == Action.object || msg.action == Action.annotation);
    }

    public ProtocolMessage() {}

    public ProtocolMessage(Action action) {
        this.action = action;
    }

    public ProtocolMessage(Action action, String channel) {
        this.action = action;
        this.channel = channel;
    }

    public Action action;
    public int flags;
    public int count;
    public ErrorInfo error;
    public String id;
    public String channel;
    public String channelSerial;
    public String connectionId;
    public Long msgSerial;
    public long timestamp;
    public Message[] messages;
    public PresenceMessage[] presence;
    public ConnectionDetails connectionDetails;
    public AuthDetails auth;
    public Map<String, String> params;
    public Annotation[] annotations;
    /**
     * This will be null if we skipped decoding this property due to user not requesting Objects functionality
     * JsonAdapter annotation supports java version (1.8) mentioned in build.gradle
     * This is targeted and specific to the state field, so won't affect other fields
     */
    @Nullable
    @JsonAdapter(ObjectJsonSerializer.class)
    public Object[] state;

    public @Nullable PublishResult[] res;

    public boolean hasFlag(final Flag flag) {
        return (flags & flag.getMask()) == flag.getMask();
    }

    public void setFlag(final Flag flag) {
        flags |= flag.getMask();
    }

    public void setFlags(final int flags) {
        this.flags |= flags;
    }

    void writeMsgpack(MessagePacker packer) throws IOException {
        int fieldCount = 1; //action
        if(channel != null) ++fieldCount;
        if(msgSerial != null) ++fieldCount;
        if(messages != null) ++fieldCount;
        if(presence != null) ++fieldCount;
        if(auth != null) ++fieldCount;
        if(flags != 0) ++fieldCount;
        if(params != null) ++fieldCount;
        if(channelSerial != null) ++fieldCount;
        if(annotations != null) ++fieldCount;
        if(state != null && ObjectSerializer.tryGet() != null) ++fieldCount;
        if(res != null) ++fieldCount;
        packer.packMapHeader(fieldCount);
        packer.packString("action");
        packer.packInt(action.getValue());
        if(channel != null) {
            packer.packString("channel");
            packer.packString(channel);
        }
        if(msgSerial != null) {
            packer.packString("msgSerial");
            packer.packLong(msgSerial.longValue());
        }
        if(messages != null) {
            packer.packString("messages");
            MessageSerializer.writeMsgpackArray(messages, packer);
        }
        if(presence != null) {
            packer.packString("presence");
            PresenceSerializer.writeMsgpackArray(presence, packer);
        }
        if(auth != null) {
            packer.packString("auth");
            auth.writeMsgpack(packer);
        }
        if(flags != 0) {
            packer.packString("flags");
            packer.packInt(flags);
        }
        if(params != null) {
            packer.packString("params");
            MessageSerializer.write(params, packer);
        }
        if(channelSerial != null) {
            packer.packString("channelSerial");
            packer.packString(channelSerial);
        }
        if(annotations != null) {
            packer.packString("annotations");
            AnnotationSerializer.writeMsgpackArray(annotations, packer);
        }
        if(state != null) {
            ObjectSerializer objectsSerializer = ObjectSerializer.tryGet();
            if (objectsSerializer != null) {
                packer.packString("state");
                objectsSerializer.writeMsgpackArray(state, packer);
            } else {
                Log.w(TAG, "Skipping 'state' field msgpack serialization because ObjectsSerializer not found");
            }
        }
        if (res != null) {
            packer.packString("res");
            PublishResult.writeMsgpackArray(res, packer);
        }
    }

    ProtocolMessage readMsgpack(MessageUnpacker unpacker) throws IOException {
        int fieldCount = unpacker.unpackMapHeader();
        for(int i = 0; i < fieldCount; i++) {
            String fieldName = unpacker.unpackString().intern();
            MessageFormat fieldFormat = unpacker.getNextFormat();
            if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

            switch(fieldName) {
                case "action":
                    action = Action.findByValue(unpacker.unpackInt());
                    break;
                case "flags":
                    flags = unpacker.unpackInt();
                    break;
                case "count":
                    count = unpacker.unpackInt();
                    break;
                case "error":
                    error = ErrorInfo.fromMsgpack(unpacker);
                    break;
                case "id":
                    id = unpacker.unpackString();
                    break;
                case "channel":
                    channel = unpacker.unpackString();
                    break;
                case "channelSerial":
                    channelSerial = unpacker.unpackString();
                    break;
                case "connectionId":
                    connectionId = unpacker.unpackString();
                    break;
                case "msgSerial":
                    msgSerial = Long.valueOf(unpacker.unpackLong());
                    break;
                case "timestamp":
                    timestamp = unpacker.unpackLong();
                    break;
                case "messages":
                    messages = MessageSerializer.readMsgpackArray(unpacker);
                    break;
                case "presence":
                    presence = PresenceSerializer.readMsgpackArray(unpacker);
                    break;
                case "connectionDetails":
                    connectionDetails = ConnectionDetails.fromMsgpack(unpacker);
                    break;
                case "auth":
                    auth = AuthDetails.fromMsgpack(unpacker);
                    break;
                case "connectionKey":
                    /* deprecated; ignore */
                    unpacker.unpackString();
                    break;
                case "params":
                    params = MessageSerializer.readStringMap(unpacker);
                    break;
                case "annotations":
                    annotations = AnnotationSerializer.readMsgpackArray(unpacker);
                    break;
                case "state":
                    ObjectSerializer objectsSerializer = ObjectSerializer.tryGet();
                    if (objectsSerializer != null) {
                        state = objectsSerializer.readMsgpackArray(unpacker);
                    } else {
                        Log.w(TAG, "Skipping 'state' field msgpack deserialization because ObjectsSerializer not found");
                        unpacker.skipValue();
                    }
                    break;
                case "res":
                    res = PublishResult.readMsgpackArray(unpacker);
                    break;
                default:
                    Log.v(TAG, "Unexpected field: " + fieldName);
                    unpacker.skipValue();
            }
        }
        return this;
    }

    static ProtocolMessage fromMsgpack(MessageUnpacker unpacker) throws IOException {
        return (new ProtocolMessage()).readMsgpack(unpacker);
    }

    public static class ActionSerializer implements JsonSerializer<Action>, JsonDeserializer<Action> {
        @Override
        public Action deserialize(JsonElement json, Type t, JsonDeserializationContext ctx)
                throws JsonParseException {
            return Action.findByValue(json.getAsInt());
        }

        @Override
        public JsonElement serialize(Action action, Type t, JsonSerializationContext ctx) {
            return new JsonPrimitive(action.getValue());
        }
    }

    /**
     * Contains the token string used to authenticate a client with Ably.
     */
    public static class AuthDetails {
        /**
         * The authentication token string.
         * <p>
         * Spec: AD2
         */
        public String accessToken;

        /**
         * Default constructor
         */
        private AuthDetails() { }

        /**
         * Creates AuthDetails object with provided authentication token string.
         * @param s Authentication token string.
         */
        public AuthDetails(String s) { accessToken = s; }

        AuthDetails readMsgpack(MessageUnpacker unpacker) throws IOException {
            int fieldCount = unpacker.unpackMapHeader();
            for(int i = 0; i < fieldCount; i++) {
                String fieldName = unpacker.unpackString().intern();
                MessageFormat fieldFormat = unpacker.getNextFormat();
                if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

                switch(fieldName) {
                    case "accessToken":
                        accessToken = unpacker.unpackString();
                        break;
                    default:
                        Log.v(TAG, "Unexpected field: " + fieldName);
                        unpacker.skipValue();
                }
            }
            return this;
        }

        static AuthDetails fromMsgpack(MessageUnpacker unpacker) throws IOException {
            return (new AuthDetails()).readMsgpack(unpacker);
        }

        void writeMsgpack(MessagePacker packer) throws IOException {
            int fieldCount = 0;
            if(accessToken != null) ++fieldCount;
            packer.packMapHeader(fieldCount);
            if(accessToken != null) {
                packer.packString("accessToken");
                packer.packString(accessToken);
            }
        }
    }

    private static final String TAG = ProtocolMessage.class.getName();
}
