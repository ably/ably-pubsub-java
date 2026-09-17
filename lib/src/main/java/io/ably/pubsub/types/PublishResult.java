package io.ably.pubsub.types;

import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.util.Serialisation;
import org.jetbrains.annotations.Nullable;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;

/**
 * Contains the result of a publish operation.
 */
public class PublishResult {

    private static final String SERIALS = "serials";

    /**
     * An array of message serials corresponding 1:1 to the messages that were published.
     * A serial may be null if the message was discarded due to a configured conflation rule.
     */
    public final @Nullable String[] serials;

    public PublishResult(@Nullable String[] serials) {
        this.serials = serials;
    }

    private static PublishResult readFromJson(byte[] packed) throws MessageDecodeException {
        return Serialisation.gson.fromJson(new String(packed), PublishResult.class);
    }

    private static PublishResult readMsgpack(byte[] packed) throws AblyException {
        try {
            MessageUnpacker unpacker = Serialisation.msgpackUnpackerConfig.newUnpacker(packed);
            return readMsgpack(unpacker);
        } catch (IOException ioe) {
            throw AblyException.fromThrowable(ioe);
        }
    }

    private static PublishResult readMsgpack(MessageUnpacker unpacker) throws IOException {
        int fieldCount = unpacker.unpackMapHeader();
        for (int i = 0; i < fieldCount; i++) {
            String fieldName = unpacker.unpackString();
            MessageFormat fieldFormat = unpacker.getNextFormat();
            if (fieldFormat.equals(MessageFormat.NIL)) {
                unpacker.unpackNil();
                continue;
            }

            if (fieldName.equals(SERIALS)) {
                int count = unpacker.unpackArrayHeader();
                String[] serials = new String[count];
                for (int j = 0; j < count; j++) {
                    if (unpacker.getNextFormat().equals(MessageFormat.NIL)) {
                        unpacker.unpackNil();
                        serials[j] = null;
                    } else {
                        serials[j] = unpacker.unpackString();
                    }
                }
                return new PublishResult(serials);
            } else {
                unpacker.skipValue();
            }
        }
        return new PublishResult(new String[]{});
    }

    static void writeMsgpackArray(PublishResult[] results, MessagePacker packer) {
        try {
            int count = results.length;
            packer.packArrayHeader(count);
            for (PublishResult result : results) {
                if (result != null) {
                    result.writeMsgpack(packer);
                } else {
                    packer.packNil();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    static PublishResult[] readMsgpackArray(MessageUnpacker unpacker) throws IOException {
        int count = unpacker.unpackArrayHeader();
        PublishResult[] results = new PublishResult[count];
        for (int i = 0; i < count; i++) {
            results[i] = readMsgpack(unpacker);
        }
        return results;
    }

    public static HttpCore.BodyHandler<String> getBodyHandler() {
        return new PublishResultBodyHandler();
    }

    private void writeMsgpack(MessagePacker packer) throws IOException {
        int fieldCount = 0;
        if (serials != null) ++fieldCount;
        packer.packMapHeader(fieldCount);
        if (serials != null) {
            packer.packString(SERIALS);
            packer.packArrayHeader(serials.length);
            for (String serial : serials) {
                if (serial == null) {
                    packer.packNil();
                } else {
                    packer.packString(serial);
                }
            }
        }
    }

    private static class PublishResultBodyHandler implements HttpCore.BodyHandler<String> {

        @Override
        public String[] handleResponseBody(String contentType, byte[] body) throws AblyException {
            try {
                PublishResult publishResult = null;
                if ("application/json".equals(contentType))
                    publishResult = readFromJson(body);
                else if ("application/x-msgpack".equals(contentType))
                    publishResult = readMsgpack(body);
                return publishResult != null ? publishResult.serials : new String[]{};
            } catch (MessageDecodeException e) {
                throw AblyException.fromThrowable(e);
            }
        }
    }
}

