package io.ably.pubsub.types;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.http.HttpUtils;
import io.ably.pubsub.util.Log;

import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import io.ably.pubsub.util.Serialisation;

/**
 * MessageReader: internal
 * Utility class to convert response bodies in different formats to Message
 * and Message arrays.
 */
public class MessageSerializer {

    /****************************************
     *            Msgpack decode
     ****************************************/

    public static Message[] readMsgpackArray(MessageUnpacker unpacker) throws IOException {
        int count = unpacker.unpackArrayHeader();
        Message[] result = new Message[count];
        for(int i = 0; i < count; i++)
            result[i] = Message.fromMsgpack(unpacker);
        return result;
    }

    public static Message[] readMsgpack(byte[] packed) throws AblyException {
        try {
            MessageUnpacker unpacker = Serialisation.msgpackUnpackerConfig.newUnpacker(packed);
            return readMsgpackArray(unpacker);
        } catch(IOException ioe) {
            throw AblyException.fromThrowable(ioe);
        }
    }

    /****************************************
     *            Msgpack encode
     ****************************************/

    public static HttpCore.RequestBody asSingleMsgpackRequest(Message message) throws AblyException {
        return new HttpUtils.ByteArrayRequestBody(write(message), "application/x-msgpack");
    }

    public static HttpCore.RequestBody asMsgpackRequest(Message[] messages) {
        return new HttpUtils.ByteArrayRequestBody(writeMsgpackArray(messages), "application/x-msgpack");
    }

    public static byte[] writeMsgpackArray(Message[] messages) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MessagePacker packer = Serialisation.msgpackPackerConfig.newPacker(out);
            writeMsgpackArray(messages, packer);
            packer.flush();
            return out.toByteArray();
        } catch(IOException e) { return null; }
    }

    public static void writeMsgpackArray(Message[] messages, MessagePacker packer) {
        try {
            int count = messages.length;
            packer.packArrayHeader(count);
            for(Message message : messages)
                message.writeMsgpack(packer);
        } catch(IOException e) {}
    }

    public static byte[] write(Message message) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MessagePacker packer = Serialisation.msgpackPackerConfig.newPacker(out);
            message.writeMsgpack(packer);
            packer.flush();
            return out.toByteArray();
        } catch(IOException e) { return null; }
    }

    public static void write(final Map<String, String> map, final MessagePacker packer) throws IOException {
        packer.packMapHeader(map.size());
        for (final Map.Entry<String, String> entry : map.entrySet()) {
            packer.packString(entry.getKey());
            packer.packString(entry.getValue());
        }
    }

    public static Map<String, String> readStringMap(final MessageUnpacker unpacker) throws IOException {
        final Map<String, String> map = new HashMap<>();
        final int fieldCount = unpacker.unpackMapHeader();
        for(int i = 0; i < fieldCount; i++) {
            final String fieldName = unpacker.unpackString();
            final MessageFormat fieldFormat = unpacker.getNextFormat();

            // TODO is this required? It seems to be saying that if we have a null value
            // then nothing should be added to the map, despite the fact that the key
            // was potentially viable.
            if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

            map.put(fieldName, unpacker.unpackString());
        }
        return map;
    }

    public static HttpCore.RequestBody asMsgpackRequest(Message.Batch[] pubSpecs) {
        return new HttpUtils.ByteArrayRequestBody(writeMsgpackArray(pubSpecs), "application/x-msgpack");
    }

    static byte[] writeMsgpackArray(Message.Batch[] pubSpecs) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MessagePacker packer = Serialisation.msgpackPackerConfig.newPacker(out);
            writeMsgpackArray(pubSpecs, packer);
            packer.flush();
            return out.toByteArray();
        } catch(IOException e) { return null; }
    }

    static void writeMsgpackArray(Message.Batch[] pubSpecs, MessagePacker packer) throws IOException {
        try {
            int count = pubSpecs.length;
            packer.packArrayHeader(count);
            for(Message.Batch spec : pubSpecs)
                spec.writeMsgpack(packer);
        } catch(IOException e) {}
    }

    /****************************************
     *              JSON decode
     ****************************************/

    public static Message[] readMessagesFromJson(byte[] packed) throws MessageDecodeException {
        return Serialisation.gson.fromJson(new String(packed), Message[].class);
    }

    /****************************************
     *            JSON encode
     ****************************************/

    public static HttpCore.RequestBody asJsonRequest(Message message) throws AblyException {
        return asJsonRequest(new Message[] { message });
    }

    public static HttpCore.RequestBody asSingleJsonRequest(Message message) {
        return new HttpUtils.JsonRequestBody(Serialisation.gson.toJson(message));
    }

    public static HttpCore.RequestBody asJsonRequest(Message[] messages) {
        return new HttpUtils.JsonRequestBody(Serialisation.gson.toJson(messages));
    }

    public static HttpCore.RequestBody asJSONRequest(Message.Batch[] pubSpecs) {
        return new HttpUtils.JsonRequestBody(Serialisation.gson.toJson(pubSpecs));
    }

    /****************************************
     *              BodyHandler
     ****************************************/

    public static HttpCore.BodyHandler<Message> getMessageResponseHandler(ChannelOptions opts) {
        return opts == null ? messageResponseHandler : new MessageBodyHandler(opts);
    }

    public static HttpCore.BodyHandler<Message> getSingleMessageResponseHandler(ChannelOptions opts) {
        return new SingleMessageBodyHandler(opts);
    }

    private static class MessageBodyHandler implements HttpCore.BodyHandler<Message> {

        MessageBodyHandler(ChannelOptions opts) { this.opts = opts; }

        @Override
        public Message[] handleResponseBody(String contentType, byte[] body) throws AblyException {
            try {
                Message[] messages = null;
                if("application/json".equals(contentType))
                    messages = readMessagesFromJson(body);
                else if("application/x-msgpack".equals(contentType))
                    messages = readMsgpack(body);
                if(messages != null) {
                    for (Message message : messages) {
                        try {
                            message.decode(opts);
                        } catch (MessageDecodeException e) {
                            Log.e(TAG, e.errorInfo.message);
                        }
                    }
                }
                return messages;
            } catch (MessageDecodeException e) {
                throw AblyException.fromThrowable(e);
            }
        }

        private ChannelOptions opts;
    }

    private static class SingleMessageBodyHandler implements HttpCore.BodyHandler<Message> {

        private final ChannelOptions opts;

        SingleMessageBodyHandler(ChannelOptions opts) { this.opts = opts; }

        @Override
        public Message[] handleResponseBody(String contentType, byte[] body) throws AblyException {
            try {
                Message message = null;
                if ("application/json".equals(contentType)) {
                    message = Serialisation.gson.fromJson(new String(body), Message.class);
                } else if ("application/x-msgpack".equals(contentType)) {
                    MessageUnpacker unpacker = Serialisation.msgpackUnpackerConfig.newUnpacker(body);
                    try {
                        message = new Message().readMsgpack(unpacker);
                    } catch (IOException ioe) {
                        throw AblyException.fromThrowable(ioe);
                    }
                }

                if (message != null) {
                    try {
                        message.decode(opts);
                    } catch (MessageDecodeException e) {
                        Log.e(TAG, e.errorInfo.message);
                    }
                }
                return new Message[] { message };
            } catch (MessageDecodeException e) {
                throw AblyException.fromThrowable(e);
            }
        }
    }

    private static HttpCore.BodyHandler<Message> messageResponseHandler = new MessageBodyHandler(null);
    private static final String TAG = MessageSerializer.class.getName();
}
