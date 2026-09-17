package io.ably.pubsub.types;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.http.HttpUtils;
import io.ably.pubsub.util.Log;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import io.ably.pubsub.util.Serialisation;

/**
 * PresenceSerializer: internal
 * Utility class to convert response bodies in different formats to PresenceMessage
 * and PresenceMessage arrays.
 */
public class PresenceSerializer {

    /****************************************
     *            Msgpack decode
     ****************************************/

    public static PresenceMessage[] readMsgpackArray(MessageUnpacker unpacker) throws IOException {
        int count = unpacker.unpackArrayHeader();
        PresenceMessage[] result = new PresenceMessage[count];
        for(int i = 0; i < count; i++)
            result[i] = PresenceMessage.fromMsgpack(unpacker);
        return result;
    }

    public static PresenceMessage[] readMsgpack(byte[] packed) throws AblyException {
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

    public static byte[] writeMsgpackArray(PresenceMessage[] messages) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MessagePacker packer = Serialisation.msgpackPackerConfig.newPacker(out);
            writeMsgpackArray(messages, packer);
            packer.flush();
            return out.toByteArray();
        } catch(IOException e) { return null; }
    }

    public static void writeMsgpackArray(PresenceMessage[] messages, MessagePacker packer) {
        try {
            int count = messages.length;
            packer.packArrayHeader(count);
            for(PresenceMessage message : messages)
                message.writeMsgpack(packer);
        } catch(IOException e) {}
    }

    /****************************************
     *              JSON decode
     ****************************************/

    private static PresenceMessage[] readJson(byte[] packed) throws IOException {
        return Serialisation.gson.fromJson(new String(packed), PresenceMessage[].class);
    }

    /****************************************
     *            JSON encode
     ****************************************/

    public static HttpCore.RequestBody asJsonRequest(PresenceMessage message) throws AblyException {
        return asJsonRequest(new PresenceMessage[] { message });
    }

    public static HttpCore.RequestBody asJsonRequest(PresenceMessage[] messages) {
        return new HttpUtils.JsonRequestBody(Serialisation.gson.toJson(messages));
    }

    /****************************************
     *              BodyHandler
     ****************************************/

    public static HttpCore.BodyHandler<PresenceMessage> getPresenceResponseHandler(ChannelOptions opts) {
        return opts == null ? presenceResponseHandler : new PresenceBodyHandler(opts);
    }

    private static class PresenceBodyHandler implements HttpCore.BodyHandler<PresenceMessage> {

        PresenceBodyHandler(ChannelOptions opts) { this.opts = opts; }

        @Override
        public PresenceMessage[] handleResponseBody(String contentType, byte[] body) throws AblyException {
            try {
                PresenceMessage[] messages = null;
                if("application/json".equals(contentType))
                    messages = readJson(body);
                else if("application/x-msgpack".equals(contentType))
                    messages = readMsgpack(body);
                if(messages != null) {
                    for (PresenceMessage message : messages) {
                        try {
                            message.decode(opts);
                        } catch (MessageDecodeException e) {
                            Log.e(TAG, e.errorInfo.message);
                        }
                    }
                }
                return messages;
            } catch(IOException e) {
                throw AblyException.fromThrowable(e);
            }
        }

        private ChannelOptions opts;
    }

    private static HttpCore.BodyHandler<PresenceMessage> presenceResponseHandler = new PresenceBodyHandler(null);

    private static final String TAG = PresenceSerializer.class.getName();
}
