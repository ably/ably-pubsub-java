package io.ably.pubsub.types;

import com.davidehrmann.vcdiff.VCDiffDecoder;
import com.davidehrmann.vcdiff.VCDiffDecoderBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import io.ably.pubsub.util.Base64Coder;
import io.ably.pubsub.util.Crypto;
import io.ably.pubsub.util.Crypto.EncryptingChannelCipher;
import io.ably.pubsub.util.Crypto.DecryptingChannelCipher;
import io.ably.pubsub.util.Log;
import io.ably.pubsub.util.Serialisation;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BaseMessage implements Cloneable {
    /**
     * A Unique ID assigned by Ably to this message.
     * <p>
     * Spec: TM2a
     */
    public String id;

    /**
     * Timestamp of when the message was received by Ably, as milliseconds since the Unix epoch.
     * <p>
     * Spec: TM2f
     */
    public long timestamp;

    /**
     * The client ID of the publisher of this message.
     * <p>
     * Spec: RSL1g1, TM2b
     */
    public String clientId;

    /**
     * The connection ID of the publisher of this message.
     * <p>
     * Spec: TM2c
     */
    public String connectionId;

    /**
     * This is typically empty, as all messages received from Ably are automatically decoded client-side using this value.
     * However, if the message encoding cannot be processed, this attribute contains the remaining transformations
     * not applied to the data payload.
     * <p>
     * Spec: TM2e
     */
    public String encoding;

    /**
     * The message payload, if provided.
     * <p>
     * Spec: TM2d
     */
    public Object data;

    private static final String TIMESTAMP = "timestamp";
    private static final String ID = "id";
    private static final String CLIENT_ID = "clientId";
    private static final String CONNECTION_ID = "connectionId";
    private static final String ENCODING = "encoding";
    private static final String DATA = "data";

    /**
     * Generate a String summary of this BaseMessage.
     */
    public void getDetails(StringBuilder builder) {
        if(clientId != null)
            builder.append(" clientId=").append(clientId);
        if(connectionId != null)
            builder.append(" connectionId=").append(connectionId);
        if(data != null)
            builder.append(" data=").append(data);
        if(encoding != null)
            builder.append(" encoding=").append(encoding);
        if(id != null)
            builder.append(" id=").append(id);
    }

    public void decode(ChannelOptions opts) throws MessageDecodeException {

        this.decode(opts, new DecodingContext());
    }

    private final static ThreadLocal<VCDiffDecoder> vcdiffDecoder = new ThreadLocal<VCDiffDecoder>() {
        @Override
        protected VCDiffDecoder initialValue() {
            return VCDiffDecoderBuilder.builder().buildSimple();
        }
    };

    private static byte[] vcdiffApply(byte[] delta, byte[] base) throws MessageDecodeException {
        try {
            ByteArrayOutputStream decoded = new ByteArrayOutputStream();
            vcdiffDecoder.get().decode(base, delta, decoded);
            return decoded.toByteArray();
        } catch (Throwable t) {
            throw MessageDecodeException.fromThrowableAndErrorInfo(t, new ErrorInfo("VCDIFF delta decode failed", 400, 40018));
        }
    }

    public void decode(ChannelOptions opts,  DecodingContext context) throws MessageDecodeException {

        Object lastPayload = data;

        if(encoding != null) {
            String[] xforms = encoding.split("\\/");
            int lastProcessedEncodingIndex = 0, encodingsToProcess  = xforms.length;
            try {
                while((lastProcessedEncodingIndex  = encodingsToProcess ) > 0) {
                    Matcher match = xformPattern.matcher(xforms[--encodingsToProcess ]);
                    if(!match.matches()) break;
                    switch(match.group(1)) {
                        case "base64":
                            try {
                                data = Base64Coder.decode((String) data);
                            } catch (IllegalArgumentException e) {
                                throw MessageDecodeException.fromDescription("Invalid base64 data received");
                            }
                            if(lastProcessedEncodingIndex == xforms.length) {
                                lastPayload = data;
                            }
                            continue;

                        case "utf-8":
                            try { data = new String((byte[])data, "UTF-8"); } catch(UnsupportedEncodingException|ClassCastException e) {}
                            continue;

                        case "json":
                            try {
                                String jsonText = ((String)data).trim();
                                data = Serialisation.gsonParser.parse(jsonText);
                            } catch(JsonParseException e) {
                                throw MessageDecodeException.fromDescription("Invalid JSON data received");
                            }
                            continue;

                        case "cipher":
                            if(opts != null && opts.encrypted) {
                                try {
                                    DecryptingChannelCipher cipher = Crypto.createChannelDecipher(opts.getCipherParamsOrDefault());
                                    data = cipher.decrypt((byte[]) data);
                                } catch(AblyException e) {
                                    throw MessageDecodeException.fromDescription(e.errorInfo.message);
                                }
                                continue;
                            }
                            else {
                                throw MessageDecodeException.fromDescription("Encrypted message received but encryption is not set up");
                            }
                        case "vcdiff":
                            data = vcdiffApply((byte[]) data, context.getLastMessageData());
                            lastPayload = data;

                            continue;
                    }
                    break;
                }
            } finally {
                encoding = (lastProcessedEncodingIndex  <= 0) ? null : join(xforms, '/', 0, lastProcessedEncodingIndex );
            }
        }

        //last message bookkeping
        if (lastPayload instanceof String)
            context.setLastMessageData((String)lastPayload);
        else if (lastPayload instanceof byte[])
            context.setLastMessageData((byte[])lastPayload);
        else if (lastPayload != null)
            throw MessageDecodeException.fromDescription("Message data neither String nor byte[]. Unsupported message data type.");
    }

    public void encode(ChannelOptions opts) throws AblyException {
        EncodedMessageData encodedData = encodeData(opts);
        this.data = encodedData.data;
        this.encoding = encodedData.encoding;
    }

    EncodedMessageData encodeData(ChannelOptions opts) throws AblyException {
        Object decodedData = this.data;
        String encoding = this.encoding;

        if (decodedData != null) {
            if (decodedData instanceof JsonElement) {
                decodedData = Serialisation.gson.toJson((JsonElement) decodedData);
                encoding = ((encoding == null) ? "" : encoding + "/") + "json";
            }
            if (decodedData instanceof String) {
                if (opts != null && opts.encrypted) {
                    try { decodedData = ((String)decodedData).getBytes("UTF-8"); } catch(UnsupportedEncodingException e) {}
                    encoding = ((encoding == null) ? "" : encoding + "/") + "utf-8";
                }
            } else if (!(decodedData instanceof byte[])) {
                Log.d(TAG, "Message data must be either `byte[]`, `String` or `JSONElement`; implicit coercion of other types to String is deprecated");
                throw AblyException.fromErrorInfo(new ErrorInfo("Invalid message data or encoding", 400, 40013));
            }
        }

        if (opts != null && opts.encrypted) {
            EncryptingChannelCipher cipher = Crypto.createChannelEncipher(opts.getCipherParamsOrDefault());
            decodedData = cipher.encrypt((byte[]) decodedData);
            encoding = ((encoding == null) ? "" : encoding + "/") + "cipher+" + cipher.getAlgorithm();
        }

        return new EncodedMessageData(decodedData, encoding);
    }

    /* trivial utilities for processing encoding string */
    private static Pattern xformPattern = Pattern.compile("([\\-\\w]+)(\\+([\\-\\w]+))?");
    private String join(String[] elements, char separator, int start, int end) {
        StringBuilder result = new StringBuilder(elements[start++]);
        for(int i = start; i < end; i++)
            result.append(separator).append(elements[i]);
        return result.toString();
    }

    /**
     * Base for gson serialisers.
     */
    public static JsonObject toJsonObject(final BaseMessage message) {
        JsonObject json = new JsonObject();
        Object data = message.data;
        String encoding = message.encoding;
        if(data != null) {
            if(data instanceof byte[]) {
                byte[] dataBytes = (byte[])data;
                json.addProperty(DATA, new String(Base64Coder.encode(dataBytes)));
                encoding = (encoding == null) ? "base64" : encoding + "/base64";
            } else {
                json.addProperty(DATA, data.toString());
            }
            if(encoding != null) json.addProperty(ENCODING, encoding);
        }
        if(message.id != null) json.addProperty(ID, message.id);
        if(message.clientId != null) json.addProperty(CLIENT_ID, message.clientId);
        if(message.connectionId != null) json.addProperty(CONNECTION_ID, message.connectionId);
        return json;
    }

    /**
     * Populate fields from JSON.
     */
    protected void read(final JsonObject map) throws MessageDecodeException {
        final Long optionalTimestamp = readLong(map, TIMESTAMP);
        if (null != optionalTimestamp) {
            timestamp = optionalTimestamp; // unbox
        }

        id = readString(map, ID);
        clientId = readString(map, CLIENT_ID);
        connectionId = readString(map, CONNECTION_ID);
        encoding = readString(map, ENCODING);
        data = readString(map, DATA);
    }

    /**
     * Read an optional textual value.
     * @return The value, or null if the key was not present in the map.
     * @throws ClassCastException if an element exists for that key and that element is not a {@link JsonPrimitive}
     * or is not a valid string value.
     */
    protected String readString(final JsonObject map, final String key) {
        final JsonElement element = map.get(key);
        if (null == element || element instanceof JsonNull) {
            return null;
        }
        return element.getAsString();
    }

    /**
     * Read an optional numerical value.
     * @return The value, or null if the key was not present in the map.
     * @throws ClassCastException if an element exists for that key and that element is not a {@link JsonPrimitive}
     * or is not a valid long value.
     */
    protected Long readLong(final JsonObject map, final String key) {
        final JsonElement element = map.get(key);
        if (null == element || element instanceof JsonNull) {
            return null;
        }
        return element.getAsLong();
    }

    /**
     * Read an optional numerical value.
     * @return The value, or null if the key was not present in the map.
     * @throws ClassCastException if an element exists for that key and that element is not a {@link JsonPrimitive}
     * or is not a valid int value.
     */
    protected Integer readInt(final JsonObject map, final String key) {
        final JsonElement element = map.get(key);
        if (null == element || element instanceof JsonNull) {
            return null;
        }
        return element.getAsInt();
    }

    /* Msgpack processing */
    boolean readField(MessageUnpacker unpacker, String fieldName, MessageFormat fieldType) throws IOException {
        boolean result = true;
        switch (fieldName) {
            case TIMESTAMP:
                timestamp = unpacker.unpackLong(); break;
            case ID:
                id = unpacker.unpackString(); break;
            case CLIENT_ID:
                clientId = unpacker.unpackString(); break;
            case CONNECTION_ID:
                connectionId = unpacker.unpackString(); break;
            case ENCODING:
                encoding = unpacker.unpackString(); break;
            case DATA:
                if(fieldType.getValueType().isBinaryType()) {
                    byte[] byteData = new byte[unpacker.unpackBinaryHeader()];
                    unpacker.readPayload(byteData);
                    data = byteData;
                } else {
                    data = unpacker.unpackString();
                }
                break;
            default:
                result = false;
                break;
        }
        return result;
    }

    protected int countFields() {
        int fieldCount = 0;
        if(timestamp > 0) ++fieldCount;
        if(id != null) ++fieldCount;
        if(clientId != null) ++fieldCount;
        if(connectionId != null) ++fieldCount;
        if(encoding != null) ++fieldCount;
        if(data != null) ++fieldCount;
        return fieldCount;
    }

    void writeFields(MessagePacker packer) throws IOException {
        if(timestamp > 0) {
            packer.packString(TIMESTAMP);
            packer.packLong(timestamp);
        }
        if(id != null) {
            packer.packString(ID);
            packer.packString(id);
        }
        if(clientId != null) {
            packer.packString(CLIENT_ID);
            packer.packString(clientId);
        }
        if(connectionId != null) {
            packer.packString(CONNECTION_ID);
            packer.packString(connectionId);
        }
        if(encoding != null) {
            packer.packString(ENCODING);
            packer.packString(encoding);
        }
        if(data != null) {
            packer.packString(DATA);
            if(data instanceof byte[]) {
                byte[] byteData = (byte[])data;
                packer.packBinaryHeader(byteData.length);
                packer.writePayload(byteData);
            } else {
                packer.packString(data.toString());
            }
        }
    }

    private static final String TAG = BaseMessage.class.getName();

    static class EncodedMessageData {
        public final Object data;
        public final String encoding;

        EncodedMessageData(Object data, String encoding) {
            this.data = data;
            this.encoding = encoding;
        }
    }
}
