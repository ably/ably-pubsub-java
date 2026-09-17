package io.ably.pubsub.types;

import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.util.Serialisation;
import org.jetbrains.annotations.Nullable;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;

/**
 * Contains the result of an update or delete message operation.
 */
public class UpdateDeleteResult {

    private static final String VERSION_SERIAL = "versionSerial";

    /**
     * The serial of the new version of the updated or deleted message.
     * Will be null if the message was superseded by a subsequent update before it could be published.
     */
    public final @Nullable String versionSerial;

    public UpdateDeleteResult(@Nullable String versionSerial) {
        this.versionSerial = versionSerial;
    }

    private static UpdateDeleteResult readFromJson(byte[] packed) throws MessageDecodeException {
        return Serialisation.gson.fromJson(new String(packed), UpdateDeleteResult.class);
    }

    private static UpdateDeleteResult readMsgpack(byte[] packed) throws AblyException {
        try {
            MessageUnpacker unpacker = Serialisation.msgpackUnpackerConfig.newUnpacker(packed);
            return readMsgpack(unpacker);
        } catch (IOException ioe) {
            throw AblyException.fromThrowable(ioe);
        }
    }

    private static UpdateDeleteResult readMsgpack(MessageUnpacker unpacker) throws IOException {
        int fieldCount = unpacker.unpackMapHeader();
        String versionSerial = null;
        for (int i = 0; i < fieldCount; i++) {
            String fieldName = unpacker.unpackString().intern();
            MessageFormat fieldFormat = unpacker.getNextFormat();
            if (fieldFormat.equals(MessageFormat.NIL)) {
                unpacker.unpackNil();
                continue;
            }

            if (fieldName.equals(VERSION_SERIAL)) {
                versionSerial = unpacker.unpackString();
            } else {
                unpacker.skipValue();
            }
        }
        return new UpdateDeleteResult(versionSerial);
    }

    public static HttpCore.BodyHandler<UpdateDeleteResult> getBodyHandler() {
        return new UpdateDeleteResultBodyHandler();
    }

    private static class UpdateDeleteResultBodyHandler implements HttpCore.BodyHandler<UpdateDeleteResult> {

        @Override
        public UpdateDeleteResult[] handleResponseBody(String contentType, byte[] body) throws AblyException {
            try {
                UpdateDeleteResult updateDeleteResult = null;
                if ("application/json".equals(contentType))
                    updateDeleteResult = readFromJson(body);
                else if ("application/x-msgpack".equals(contentType))
                    updateDeleteResult = readMsgpack(body);
                return new UpdateDeleteResult[]{updateDeleteResult};
            } catch (MessageDecodeException e) {
                throw AblyException.fromThrowable(e);
            }
        }
    }
}
