package io.ably.pubsub.types;

import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.http.HttpUtils;
import io.ably.pubsub.util.Log;
import io.ably.pubsub.util.Serialisation;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class AnnotationSerializer {

    private static final String TAG = AnnotationSerializer.class.getName();

    public static void writeMsgpackArray(Annotation[] annotations, MessagePacker packer) {
        try {
            int count = annotations.length;
            packer.packArrayHeader(count);
            for (Annotation annotation : annotations) {
                annotation.writeMsgpack(packer);
            }
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static Annotation[] readMsgpackArray(MessageUnpacker unpacker) throws IOException {
        int count = unpacker.unpackArrayHeader();
        Annotation[] result = new Annotation[count];
        for (int i = 0; i < count; i++)
            result[i] = Annotation.fromMsgpack(unpacker);
        return result;
    }

    public static HttpCore.RequestBody asMsgpackRequest(Annotation[] annotations) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            MessagePacker packer = Serialisation.msgpackPackerConfig.newPacker(out);
            int count = annotations.length;
            packer.packArrayHeader(count);
            for (Annotation annotation : annotations) annotation.writeMsgpack(packer);
            packer.flush();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return new HttpUtils.ByteArrayRequestBody(out.toByteArray(), "application/x-msgpack");
    }

    public static HttpCore.RequestBody asJsonRequest(Annotation[] annotations) {
        return new HttpUtils.JsonRequestBody(Serialisation.gson.toJson(annotations));
    }

    public static HttpCore.BodyHandler<Annotation> getAnnotationResponseHandler(ChannelOptions channelOptions) {
        return new AnnotationBodyHandler(channelOptions);
    }

    public static Annotation[] readMsgpack(byte[] packed) throws AblyException {
        try {
            MessageUnpacker unpacker = Serialisation.msgpackUnpackerConfig.newUnpacker(packed);
            return readMsgpackArray(unpacker);
        } catch (IOException ioe) {
            throw AblyException.fromThrowable(ioe);
        }
    }

    public static Annotation[] readMessagesFromJson(byte[] packed) throws MessageDecodeException {
        return Serialisation.gson.fromJson(new String(packed), Annotation[].class);
    }

    private static class AnnotationBodyHandler implements HttpCore.BodyHandler<Annotation> {

        private final ChannelOptions channelOptions;

        AnnotationBodyHandler(ChannelOptions channelOptions) {
            this.channelOptions = channelOptions;
        }

        @Override
        public Annotation[] handleResponseBody(String contentType, byte[] body) throws AblyException {
            try {
                Annotation[] annotations = null;
                if ("application/json".equals(contentType))
                    annotations = readMessagesFromJson(body);
                else if ("application/x-msgpack".equals(contentType))
                    annotations = readMsgpack(body);
                if (annotations != null) {
                    for (Annotation annotation : annotations) {
                        try {
                            if (annotation.data != null) annotation.decode(channelOptions);
                        } catch (MessageDecodeException e) {
                            Log.e(TAG, e.errorInfo.message);
                        }
                    }
                }
                return annotations;
            } catch (MessageDecodeException e) {
                throw AblyException.fromThrowable(e);
            }
        }
    }
}
