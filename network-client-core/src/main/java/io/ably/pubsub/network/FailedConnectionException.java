package io.ably.pubsub.network;

public class FailedConnectionException extends RuntimeException {
    public FailedConnectionException(Throwable cause) {
        super(cause);
    }
}
