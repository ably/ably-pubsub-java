package io.ably.pubsub.types;

import io.ably.pubsub.util.Serialisation;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessageUnpacker;

import io.ably.pubsub.util.Log;

import java.io.IOException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;

/**
 * A generic Ably error object that contains an Ably-specific status code, and a generic status code.
 * Errors returned from the Ably server are compatible with the ErrorInfo structure and should result in errors that inherit from ErrorInfo.
 */
public class ErrorInfo {

    /**
     * Ably <a href="https://github.com/ably/ably-common/blob/main/protocol/errors.json">error code</a>.
     * <p>
     * Spec: TI1
     */
    public int code;

    /**
     * HTTP Status Code corresponding to this error, where applicable.
     * <p>
     * Spec: TI1
     */
    public int statusCode;

    /**
     * Additional message information, where available.
     * <p>
     * Spec: TI1
     */
    public String message;

    /**
     * This is included for REST responses to provide a URL for additional help on the error code.
     * <p>
     * Spec: TI4
     */
    public String href;

    /**
     * Public no-argument constructor for msgpack
     */
    public ErrorInfo() {}

    /**
     * Construct an ErrorInfo from message and code
     * @param message Additional message information, where available.
     * @param code Ably <a href="https://github.com/ably/ably-common/blob/main/protocol/errors.json">error code</a>.
     */
    public ErrorInfo(String message, int code) {
        this.code = code;
        this.message = message;
    }

    /**
     * Construct an ErrorInfo from message, statusCode, and code
     * @param message Additional message information, where available.
     * @param statusCode HTTP Status Code corresponding to this error, where applicable.
     * @param code Ably <a href="https://github.com/ably/ably-common/blob/main/protocol/errors.json">error code</a>.
     */
    public ErrorInfo(String message, int statusCode, int code) {
        this(message, code);
        this.statusCode = statusCode;
        if(code > 0) {
            this.href = href(code);
        }
    }

    public String toString() {
        StringBuilder result = new StringBuilder("{ErrorInfo");
        result.append(" message=").append(logMessage());
        if(code > 0) {
            result.append(" code=").append(code);
        }
        if(statusCode > 0) {
            result.append(" statusCode=").append(statusCode);
        }
        if(href != null) {
            result.append(" href=").append(href);
        }
        result.append('}');
        return result.toString();
    }

    ErrorInfo readMsgpack(MessageUnpacker unpacker) throws IOException {
        int fieldCount = unpacker.unpackMapHeader();
        for(int i = 0; i < fieldCount; i++) {
            String fieldName = unpacker.unpackString().intern();
            MessageFormat fieldFormat = unpacker.getNextFormat();
            if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

            switch(fieldName) {
                case "message":
                    message = unpacker.unpackString();
                    break;
                case "code":
                    code = unpacker.unpackInt();
                    break;
                case "statusCode":
                    statusCode = unpacker.unpackInt();
                    break;
                case "href":
                    href = unpacker.unpackString();
                    break;
                default:
                    Log.v(TAG, "Unexpected field: " + fieldName);
                    unpacker.skipValue();
            }
        }
        return this;
    }

    public static ErrorInfo fromMsgpackBody(byte[] msgpack) throws IOException {
        MessageUnpacker unpacker = Serialisation.msgpackUnpackerConfig.newUnpacker(msgpack);
        return fromMsgpackBody(unpacker);
    }

    private static ErrorInfo fromMsgpackBody(MessageUnpacker unpacker) throws IOException {
        int fieldCount = unpacker.unpackMapHeader();
        ErrorInfo error = null;
        for(int i = 0; i < fieldCount; i++) {
            String fieldName = unpacker.unpackString().intern();
            MessageFormat fieldFormat = unpacker.getNextFormat();
            if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

            switch(fieldName) {
                case "error":
                    error = ErrorInfo.fromMsgpack(unpacker);
                    break;
                default:
                    Log.v(TAG, "Unexpected field: " + fieldName);
                    unpacker.skipValue();
            }
        }
        return error;
    }

    static ErrorInfo fromMsgpack(MessageUnpacker unpacker) throws IOException {
        return (new ErrorInfo()).readMsgpack(unpacker);
    }

    public static ErrorInfo fromThrowable(Throwable throwable) {
        ErrorInfo errorInfo;
        if(throwable instanceof UnknownHostException
                || throwable instanceof NoRouteToHostException) {
            errorInfo = new ErrorInfo(throwable.getLocalizedMessage(), 500, 50002);
        }
        else if(throwable instanceof IOException) {
            errorInfo = new ErrorInfo(throwable.getLocalizedMessage(), 500, 50000);
        }
        else {
            errorInfo = new ErrorInfo("Unexpected exception: " + throwable.getLocalizedMessage(), 50000, 500);
        }

        return errorInfo;
    }

    public static ErrorInfo fromResponseStatus(String statusLine, int statusCode) {
        return new ErrorInfo(statusLine, statusCode, statusCode * 100);
    }

    /* Spec: TI5 */
    private String logMessage() {
        String errHref = null, logMessage = message == null ? "" : message;
        if(href != null) {
            errHref = href;
        } else if(code > 0) {
            errHref = href(code);
        }
        if(errHref != null && !logMessage.contains(errHref)) {
            logMessage += " (See " + errHref + ")";
        }
        return logMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ErrorInfo)) {
            return false;
        }
        ErrorInfo other = (ErrorInfo) o;
        return code == other.code &&
                statusCode == other.statusCode &&
                (message == other.message || (message != null && message.equals(other.message)));
    }

    private static String href(int code) { return HREF_BASE + code; }
    private static final String HREF_BASE = "https://help.ably.io/error/";
    private static final String TAG = ErrorInfo.class.getName();
}
