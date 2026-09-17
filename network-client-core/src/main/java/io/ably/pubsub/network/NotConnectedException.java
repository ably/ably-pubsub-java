package io.ably.pubsub.network;

public class NotConnectedException extends RuntimeException {
    public NotConnectedException(Throwable cause) {
        super(cause);
    }
}
