package io.ably.pubsub.push;

import io.ably.pubsub.http.HttpRequester;
import io.ably.pubsub.http.PubSubBase;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.Callback;

/**
 * Manages this device's FCM topic subscriptions.
 * <p>
 * FCM topics let FCM fan a single send out to every device subscribed to the topic. A topic exists
 * once its first device subscribes; there is no create or delete call. Topic names must match
 * {@code [a-zA-Z0-9-_.~%]{1,900}}. FCM allows at most 2,000 topic subscriptions per device and never
 * removes them automatically, so unsubscribe when the device no longer needs a topic.
 * <p>
 * The device must be activated for push with FCM ({@link Push#activate()} must have completed) before
 * using these methods. Requests authenticate as the device, so no push-admin capability is needed.
 */
public class FcmTopics {
    private final PubSubBase rest;
    private final Push push;

    FcmTopics(PubSubBase rest, Push push) {
        this.rest = rest;
        this.push = push;
    }

    /**
     * Returns an {@link FcmTopic} for subscribing this device to, or unsubscribing it from, the named topic.
     * An empty topic name is reported as an {@link AblyException} by the operations on the returned object.
     *
     * @param topic The FCM topic name.
     * @return An {@link FcmTopic} object.
     */
    public FcmTopic get(String topic) {
        return new FcmTopic(rest, push, topic);
    }

    /**
     * Retrieves the names of the FCM topics this device is subscribed to,
     * including any it subscribed to other than through Ably.
     *
     * @return The topic names.
     * @throws AblyException
     */
    public String[] list() throws AblyException {
        return listImpl().sync();
    }

    /**
     * Asynchronously retrieves the names of the FCM topics this device is subscribed to,
     * including any it subscribed to other than through Ably.
     *
     * @param callback A callback returning the topic names.
     * <p>
     * This callback is invoked on a background thread.
     */
    public void listAsync(Callback<String[]> callback) {
        listImpl().async(callback);
    }

    private HttpRequester.Request<String[]> listImpl() {
        return push.localDeviceRequest((deviceId, headers) -> AdminFcmTopics.listImpl(rest, headers, deviceId));
    }
}
