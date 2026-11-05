package io.ably.pubsub.push;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.http.HttpRequester;
import io.ably.pubsub.http.HttpScheduler;
import io.ably.pubsub.http.HttpUtils;
import io.ably.pubsub.http.PubSubBase;
import io.ably.pubsub.realtime.CompletionListener;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.util.Log;
import io.ably.pubsub.util.ParamsUtils;

import java.util.Arrays;

/**
 * A single FCM topic, managed with push-admin privileges. Obtained from {@link AdminFcmTopics#get(String)}.
 */
public class AdminFcmTopic {
    private static final String TAG = AdminFcmTopic.class.getName();

    /**
     * The FCM topic name.
     */
    public final String name;

    private final PubSubBase rest;
    private final PushBase push;

    AdminFcmTopic(PubSubBase rest, PushBase push, String name) {
        this.rest = rest;
        this.push = push;
        this.name = name;
    }

    /**
     * Subscribes a device to the topic. Subscribing a device that is already subscribed succeeds.
     * <p>
     * The device must be registered with Ably for FCM.
     *
     * @param deviceId The unique ID of the device.
     * @throws AblyException
     */
    public void subscribe(String deviceId) throws AblyException {
        AdminFcmTopics.subscribeDeviceImpl(rest, push.pushRequestHeaders(deviceId), name, deviceId).sync();
    }

    /**
     * Asynchronously subscribes a device to the topic. See {@link #subscribe(String)}.
     *
     * @param deviceId The unique ID of the device.
     * @param listener A listener to be notified of success or failure.
     * <p>
     * This listener is invoked on a background thread.
     */
    public void subscribeAsync(String deviceId, CompletionListener listener) {
        AdminFcmTopics.subscribeDeviceImpl(rest, push.pushRequestHeaders(deviceId), name, deviceId)
            .async(new CompletionListener.ToCallback<>(listener));
    }

    /**
     * Unsubscribes a device from the topic. Unsubscribing a device that is not subscribed succeeds.
     *
     * @param deviceId The unique ID of the device.
     * @throws AblyException
     */
    public void unsubscribe(String deviceId) throws AblyException {
        AdminFcmTopics.unsubscribeDeviceImpl(rest, push.pushRequestHeaders(deviceId), name, deviceId).sync();
    }

    /**
     * Asynchronously unsubscribes a device from the topic. See {@link #unsubscribe(String)}.
     *
     * @param deviceId The unique ID of the device.
     * @param listener A listener to be notified of success or failure.
     * <p>
     * This listener is invoked on a background thread.
     */
    public void unsubscribeAsync(String deviceId, CompletionListener listener) {
        AdminFcmTopics.unsubscribeDeviceImpl(rest, push.pushRequestHeaders(deviceId), name, deviceId)
            .async(new CompletionListener.ToCallback<>(listener));
    }

    /**
     * Requests that every FCM device registered for a clientId is subscribed to the topic.
     * <p>
     * Ably accepts the request and subscribes the devices in the background, so success means the
     * request was accepted, not that every device is subscribed. Devices registered for the clientId
     * later are not subscribed.
     *
     * @param clientId The ID of the client whose devices to subscribe.
     * @throws AblyException
     */
    public void subscribeClient(String clientId) throws AblyException {
        subscribeClientImpl(clientId).sync();
    }

    /**
     * Asynchronously requests that every FCM device registered for a clientId is subscribed to the topic.
     * See {@link #subscribeClient(String)}.
     *
     * @param clientId The ID of the client whose devices to subscribe.
     * @param listener A listener to be notified of success or failure.
     * <p>
     * This listener is invoked on a background thread.
     */
    public void subscribeClientAsync(String clientId, CompletionListener listener) {
        subscribeClientImpl(clientId).async(new CompletionListener.ToCallback<>(listener));
    }

    /**
     * Requests that every FCM device subscribed to push notifications on any of the given channels
     * is subscribed to the topic.
     * <p>
     * Ably accepts the request and subscribes the devices in the background, so success means the
     * request was accepted, not that every device is subscribed. This is a one-off batch, not a
     * standing link: devices that subscribe to the channels later are not subscribed to the topic.
     *
     * @param channels The names of the channels whose subscribed devices to subscribe.
     * @throws AblyException
     */
    public void subscribeChannels(String[] channels) throws AblyException {
        subscribeChannelsImpl(channels).sync();
    }

    /**
     * Asynchronously requests that every FCM device subscribed to push notifications on any of the
     * given channels is subscribed to the topic. See {@link #subscribeChannels(String[])}.
     *
     * @param channels The names of the channels whose subscribed devices to subscribe.
     * @param listener A listener to be notified of success or failure.
     * <p>
     * This listener is invoked on a background thread.
     */
    public void subscribeChannelsAsync(String[] channels, CompletionListener listener) {
        subscribeChannelsImpl(channels).async(new CompletionListener.ToCallback<>(listener));
    }

    /**
     * Requests that every FCM device registered for a clientId is unsubscribed from the topic.
     * <p>
     * Ably accepts the request and unsubscribes the devices in the background, so success means the
     * request was accepted, not that every device is unsubscribed.
     *
     * @param clientId The ID of the client whose devices to unsubscribe.
     * @throws AblyException
     */
    public void unsubscribeClient(String clientId) throws AblyException {
        unsubscribeClientImpl(clientId).sync();
    }

    /**
     * Asynchronously requests that every FCM device registered for a clientId is unsubscribed from the topic.
     * See {@link #unsubscribeClient(String)}.
     *
     * @param clientId The ID of the client whose devices to unsubscribe.
     * @param listener A listener to be notified of success or failure.
     * <p>
     * This listener is invoked on a background thread.
     */
    public void unsubscribeClientAsync(String clientId, CompletionListener listener) {
        unsubscribeClientImpl(clientId).async(new CompletionListener.ToCallback<>(listener));
    }

    /**
     * Sends a push notification to every device subscribed to the topic.
     * <p>
     * The payload carries FCM message fields ({@code data}, {@code notification}, {@code android}),
     * which are passed through to FCM; Ably sets the topic itself. {@code android.priority} defaults
     * to {@code high}. FCM requires every value in {@code data} to be a string.
     *
     * @param payload A JSON object containing the FCM message fields.
     * @return The FCM message ID.
     * @throws AblyException
     */
    public String publish(JsonObject payload) throws AblyException {
        return publishImpl(payload).sync();
    }

    /**
     * Asynchronously sends a push notification to every device subscribed to the topic.
     * See {@link #publish(JsonObject)}.
     *
     * @param payload A JSON object containing the FCM message fields.
     * @param callback A callback returning the FCM message ID.
     * <p>
     * This callback is invoked on a background thread.
     */
    public void publishAsync(JsonObject payload, Callback<String> callback) {
        publishImpl(payload).async(callback);
    }

    private HttpRequester.Request<Void> subscribeClientImpl(String clientId) {
        Log.v(TAG, "subscribeClientImpl(): topic=" + name + ", clientId=" + clientId);
        AblyException invalid = AdminFcmTopics.emptyArgument("topic", name, "clientId", clientId);
        if (invalid != null) {
            return rest.http.failedRequest(invalid);
        }
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("clientId", clientId);
        return postSubscriptions(bodyJson);
    }

    private HttpRequester.Request<Void> subscribeChannelsImpl(String[] channels) {
        Log.v(TAG, "subscribeChannelsImpl(): topic=" + name + ", channels=" + Arrays.toString(channels));
        AblyException invalid = AdminFcmTopics.emptyArgument("topic", name);
        if (invalid != null) {
            return rest.http.failedRequest(invalid);
        }
        if (channels == null || channels.length == 0) {
            return rest.http.failedRequest(AdminFcmTopics.missingArgument("channels"));
        }
        JsonArray channelsJson = new JsonArray();
        for (String channel : channels) {
            invalid = AdminFcmTopics.emptyArgument("channel name", channel);
            if (invalid != null) {
                return rest.http.failedRequest(invalid);
            }
            channelsJson.add(channel);
        }
        JsonObject bodyJson = new JsonObject();
        bodyJson.add("channels", channelsJson);
        return postSubscriptions(bodyJson);
    }

    private HttpRequester.Request<Void> postSubscriptions(JsonObject bodyJson) {
        final HttpCore.RequestBody body = HttpUtils.requestBodyFromGson(bodyJson, rest.options.useBinaryProtocol);
        return rest.http.request(new HttpRequester.Execute<Void>() {
            @Override
            public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
                Param[] params = ParamsUtils.enrichParams(null, rest.options);
                http.post(AdminFcmTopics.topicPath(name) + "/subscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, body, null, true, callback);
            }
        });
    }

    private HttpRequester.Request<Void> unsubscribeClientImpl(final String clientId) {
        Log.v(TAG, "unsubscribeClientImpl(): topic=" + name + ", clientId=" + clientId);
        AblyException invalid = AdminFcmTopics.emptyArgument("topic", name, "clientId", clientId);
        if (invalid != null) {
            return rest.http.failedRequest(invalid);
        }
        return rest.http.request(new HttpRequester.Execute<Void>() {
            @Override
            public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
                Param[] params = ParamsUtils.enrichParams(new Param[] { new Param("clientId", clientId) }, rest.options);
                http.del(AdminFcmTopics.topicPath(name) + "/subscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, null, true, callback);
            }
        });
    }

    private HttpRequester.Request<String> publishImpl(JsonObject payload) {
        Log.v(TAG, "publishImpl(): topic=" + name + ", payload=" + payload);
        AblyException invalid = AdminFcmTopics.emptyArgument("topic", name);
        if (invalid != null) {
            return rest.http.failedRequest(invalid);
        }
        if (payload == null || payload.entrySet().isEmpty()) {
            return rest.http.failedRequest(AdminFcmTopics.missingArgument("payload"));
        }
        final HttpCore.RequestBody body = HttpUtils.requestBodyFromGson(payload, rest.options.useBinaryProtocol);
        return rest.http.request(new HttpRequester.Execute<String>() {
            @Override
            public void execute(HttpScheduler http, Callback<String> callback) throws AblyException {
                Param[] params = ParamsUtils.enrichParams(null, rest.options);
                http.post(AdminFcmTopics.topicPath(name) + "/messages", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, body, messageIdResponseHandler, true, callback);
            }
        });
    }

    /** Reads the FCM message ID from the publish response, {@code {"topic": ..., "messageId": ...}}. */
    private static final HttpCore.ResponseHandler<String> messageIdResponseHandler = new HttpCore.ResponseHandler<String>() {
        @Override
        public String handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
            // The publish has succeeded once the server accepts it, so a missing or malformed
            // message ID is reported as null rather than as a failure.
            JsonElement body = AdminFcmTopics.responseJson(response, error);
            JsonElement messageId = body != null && body.isJsonObject() ? body.getAsJsonObject().get("messageId") : null;
            return messageId != null && messageId.isJsonPrimitive() ? messageId.getAsString() : null;
        }
    };
}
