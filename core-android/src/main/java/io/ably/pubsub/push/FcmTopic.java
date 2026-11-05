package io.ably.pubsub.push;

import io.ably.pubsub.http.HttpRequester;
import io.ably.pubsub.http.PubSubBase;
import io.ably.pubsub.realtime.CompletionListener;
import io.ably.pubsub.types.AblyException;

/**
 * A single FCM topic that this device can subscribe to or unsubscribe from.
 * Obtained from {@link FcmTopics#get(String)}.
 */
public class FcmTopic {
    /**
     * The FCM topic name.
     */
    public final String name;

    private final PubSubBase rest;
    private final Push push;

    FcmTopic(PubSubBase rest, Push push, String name) {
        this.rest = rest;
        this.push = push;
        this.name = name;
    }

    /**
     * Subscribes this device to the topic. Subscribing when already subscribed succeeds.
     * <p>
     * If FCM rejects the device's registration token, Ably marks the device registration failed
     * and the request fails; the device must then activate again.
     *
     * @throws AblyException
     */
    public void subscribe() throws AblyException {
        subscribeImpl().sync();
    }

    /**
     * Asynchronously subscribes this device to the topic. See {@link #subscribe()}.
     *
     * @param listener A listener to be notified of success or failure.
     * <p>
     * This listener is invoked on a background thread.
     */
    public void subscribeAsync(CompletionListener listener) {
        subscribeImpl().async(new CompletionListener.ToCallback<>(listener));
    }

    /**
     * Unsubscribes this device from the topic. Unsubscribing when not subscribed succeeds.
     *
     * @throws AblyException
     */
    public void unsubscribe() throws AblyException {
        unsubscribeImpl().sync();
    }

    /**
     * Asynchronously unsubscribes this device from the topic. See {@link #unsubscribe()}.
     *
     * @param listener A listener to be notified of success or failure.
     * <p>
     * This listener is invoked on a background thread.
     */
    public void unsubscribeAsync(CompletionListener listener) {
        unsubscribeImpl().async(new CompletionListener.ToCallback<>(listener));
    }

    private HttpRequester.Request<Void> subscribeImpl() {
        return push.localDeviceRequest((deviceId, headers) -> AdminFcmTopics.subscribeDeviceImpl(rest, headers, name, deviceId));
    }

    private HttpRequester.Request<Void> unsubscribeImpl() {
        return push.localDeviceRequest((deviceId, headers) -> AdminFcmTopics.unsubscribeDeviceImpl(rest, headers, name, deviceId));
    }
}
