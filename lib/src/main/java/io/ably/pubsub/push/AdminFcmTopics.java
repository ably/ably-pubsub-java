package io.ably.pubsub.push;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.http.HttpRequester;
import io.ably.pubsub.http.HttpScheduler;
import io.ably.pubsub.http.HttpUtils;
import io.ably.pubsub.http.PubSubBase;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.util.Log;
import io.ably.pubsub.util.ParamsUtils;
import io.ably.pubsub.util.Serialisation;
import io.ably.pubsub.util.StringUtils;

/**
 * Manages FCM topics with push-admin privileges: subscribing any device to a topic,
 * listing a device's topics, and publishing to a topic or a condition combining several topics.
 * <p>
 * FCM topics let FCM fan a single send out to every Android device subscribed to the topic,
 * instead of Ably sending one FCM request per device. Ably resolves devices to their FCM
 * registration tokens and calls FCM with the app's service account, so callers never handle
 * FCM credentials or tokens.
 * <p>
 * A topic exists once its first device subscribes; there is no create or delete call. Topic names
 * must match {@code [a-zA-Z0-9-_.~%]{1,900}}. FCM topics are open: any instance of the app can
 * subscribe to any topic directly through the Firebase SDK, so never put user-specific or
 * sensitive data in a topic message.
 */
public class AdminFcmTopics {
    private static final String TAG = AdminFcmTopics.class.getName();

    private final PubSubBase rest;
    private final PushBase push;

    AdminFcmTopics(PubSubBase rest, PushBase push) {
        this.rest = rest;
        this.push = push;
    }

    /**
     * Returns an {@link AdminFcmTopic} for managing subscriptions to the named topic.
     * An empty topic name is reported as an {@link AblyException} by the operations on the returned object.
     *
     * @param topic The FCM topic name.
     * @return An {@link AdminFcmTopic} object.
     */
    public AdminFcmTopic get(String topic) {
        return new AdminFcmTopic(rest, push, topic);
    }

    /**
     * Retrieves the names of the FCM topics a device is subscribed to,
     * including any it subscribed to other than through Ably.
     *
     * @param deviceId The unique ID of the device.
     * @return The topic names.
     * @throws AblyException
     */
    public String[] list(String deviceId) throws AblyException {
        return listImpl(rest, push.pushRequestHeaders(deviceId), deviceId).sync();
    }

    /**
     * Asynchronously retrieves the names of the FCM topics a device is subscribed to,
     * including any it subscribed to other than through Ably.
     *
     * @param deviceId The unique ID of the device.
     * @param callback A callback returning the topic names.
     * <p>
     * This callback is invoked on a background thread.
     */
    public void listAsync(String deviceId, Callback<String[]> callback) {
        listImpl(rest, push.pushRequestHeaders(deviceId), deviceId).async(callback);
    }

    /**
     * Sends a push notification to the devices a target selects: those subscribed to a topic
     * ({@link FcmTopicTarget#topic(String)}), or those matched by a condition combining several topics
     * ({@link FcmTopicTarget#condition(String)}).
     * <p>
     * The payload carries FCM message fields ({@code data}, {@code notification}, {@code android}),
     * which are passed through to FCM; Ably sets the target itself, and any {@code topic} or
     * {@code condition} in the payload is ignored. {@code android.priority} defaults to {@code high}.
     * FCM requires every value in {@code data} to be a string.
     *
     * @param target The topic or condition to send to.
     * @param payload A JSON object containing the FCM message fields.
     * @return The FCM message ID.
     * @throws AblyException
     */
    public String publish(FcmTopicTarget target, JsonObject payload) throws AblyException {
        return publishImpl(target, payload).sync();
    }

    /**
     * Asynchronously sends a push notification to the devices a target selects.
     * See {@link #publish(FcmTopicTarget, JsonObject)}.
     *
     * @param target The topic or condition to send to.
     * @param payload A JSON object containing the FCM message fields.
     * @param callback A callback returning the FCM message ID.
     * <p>
     * This callback is invoked on a background thread.
     */
    public void publishAsync(FcmTopicTarget target, JsonObject payload, Callback<String> callback) {
        publishImpl(target, payload).async(callback);
    }

    /**
     * Posts an FCM message to {@code /push/fcmTopics/messages}, returning the FCM message ID.
     * The endpoint takes exactly one of {@code topic} or {@code condition}, so the caller's payload
     * is copied rather than modified, with the given target as its only one.
     */
    private HttpRequester.Request<String> publishImpl(FcmTopicTarget target, JsonObject payload) {
        Log.v(TAG, "publishImpl(): target=" + target + ", payload=" + payload);
        if (target == null) {
            return rest.http.failedRequest(missingArgument("target"));
        }
        AblyException invalid = emptyArgument(target.type.field, target.value);
        if (invalid != null) {
            return rest.http.failedRequest(invalid);
        }
        JsonObject message = payload == null ? new JsonObject() : payload.deepCopy();
        message.remove(FcmTopicTarget.Type.TOPIC.field);
        message.remove(FcmTopicTarget.Type.CONDITION.field);
        if (message.entrySet().isEmpty()) {
            return rest.http.failedRequest(missingArgument("payload"));
        }
        message.addProperty(target.type.field, target.value);
        final HttpCore.RequestBody body = HttpUtils.requestBodyFromGson(message, rest.options.useBinaryProtocol);
        return rest.http.request(new HttpRequester.Execute<String>() {
            @Override
            public void execute(HttpScheduler http, Callback<String> callback) throws AblyException {
                Param[] params = ParamsUtils.enrichParams(null, rest.options);
                http.post("/push/fcmTopics/messages", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, body, messageIdResponseHandler, true, callback);
            }
        });
    }

    static String topicPath(String topic) {
        return "/push/fcmTopics/" + HttpUtils.encodeURIComponent(topic);
    }

    static String deviceSubscriptionPath(String topic, String deviceId) {
        return topicPath(topic) + "/subscriptions/" + HttpUtils.encodeURIComponent(deviceId);
    }

    static HttpRequester.Request<Void> subscribeDeviceImpl(final PubSubBase rest, final Param[] headers, final String topic, final String deviceId) {
        Log.v(TAG, "subscribeDeviceImpl(): topic=" + topic + ", deviceId=" + deviceId);
        AblyException invalid = emptyArgument("topic", topic, "deviceId", deviceId);
        if (invalid != null) {
            return rest.http.failedRequest(invalid);
        }
        // The endpoint reads no body, but HTTP clients such as OkHttp reject a POST without one.
        final HttpCore.RequestBody body = HttpUtils.requestBodyFromGson(new JsonObject(), rest.options.useBinaryProtocol);
        return rest.http.request(new HttpRequester.Execute<Void>() {
            @Override
            public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
                Param[] params = ParamsUtils.enrichParams(null, rest.options);
                http.post(deviceSubscriptionPath(topic, deviceId), headers, params, body, null, true, callback);
            }
        });
    }

    static HttpRequester.Request<Void> unsubscribeDeviceImpl(final PubSubBase rest, final Param[] headers, final String topic, final String deviceId) {
        Log.v(TAG, "unsubscribeDeviceImpl(): topic=" + topic + ", deviceId=" + deviceId);
        AblyException invalid = emptyArgument("topic", topic, "deviceId", deviceId);
        if (invalid != null) {
            return rest.http.failedRequest(invalid);
        }
        return rest.http.request(new HttpRequester.Execute<Void>() {
            @Override
            public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
                Param[] params = ParamsUtils.enrichParams(null, rest.options);
                http.del(deviceSubscriptionPath(topic, deviceId), headers, params, null, true, callback);
            }
        });
    }

    static HttpRequester.Request<String[]> listImpl(final PubSubBase rest, final Param[] headers, final String deviceId) {
        Log.v(TAG, "listImpl(): deviceId=" + deviceId);
        AblyException invalid = emptyArgument("deviceId", deviceId);
        if (invalid != null) {
            return rest.http.failedRequest(invalid);
        }
        return rest.http.request(new HttpRequester.Execute<String[]>() {
            @Override
            public void execute(HttpScheduler http, Callback<String[]> callback) throws AblyException {
                Param[] params = ParamsUtils.enrichParams(new Param[] { new Param("deviceId", deviceId) }, rest.options);
                http.get("/push/fcmTopics", headers, params, topicsResponseHandler, true, callback);
            }
        });
    }

    /**
     * Checks arguments given as alternating names and values.
     *
     * @return An error naming the first null or empty argument, or null if there is none.
     */
    static AblyException emptyArgument(String... namesAndValues) {
        for (int i = 0; i < namesAndValues.length; i += 2) {
            if (StringUtils.isNullOrEmpty(namesAndValues[i + 1])) {
                return missingArgument(namesAndValues[i]);
            }
        }
        return null;
    }

    static AblyException missingArgument(String name) {
        return AblyException.fromErrorInfo(new ErrorInfo(name + " cannot be empty", 400, 40000));
    }

    /**
     * Decodes a response body as JSON, throwing the request's error if it failed.
     *
     * @return The decoded body, or null if the response has no body.
     */
    static JsonElement responseJson(HttpCore.Response response, ErrorInfo error) throws AblyException {
        if (error != null) {
            throw AblyException.fromErrorInfo(error);
        }
        if (response == null || response.body == null || response.body.length == 0) {
            return null;
        }
        return Serialisation.httpResponseHandler.handleResponse(response, null);
    }

    /** Reads the FCM message ID from a publish response, which carries it as {@code messageId}. */
    private static final HttpCore.ResponseHandler<String> messageIdResponseHandler = new HttpCore.ResponseHandler<String>() {
        @Override
        public String handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
            // The publish has succeeded once the server accepts it, so a missing or malformed
            // message ID is reported as null rather than as a failure.
            JsonElement body = responseJson(response, error);
            JsonElement messageId = body != null && body.isJsonObject() ? body.getAsJsonObject().get("messageId") : null;
            return messageId != null && messageId.isJsonPrimitive() ? messageId.getAsString() : null;
        }
    };

    /** Reads the list response, a plain (unpaginated) array of topic names. */
    private static final HttpCore.ResponseHandler<String[]> topicsResponseHandler = new HttpCore.ResponseHandler<String[]>() {
        @Override
        public String[] handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
            JsonElement body = responseJson(response, error);
            if (body == null || body.isJsonNull()) {
                return new String[0];
            }
            try {
                if (!body.isJsonArray()) {
                    throw new JsonParseException("expected an array of topic names, got " + body);
                }
                return Serialisation.gson.fromJson(body, String[].class);
            } catch (JsonParseException e) {
                throw AblyException.fromErrorInfo(e, new ErrorInfo("Unexpected response listing FCM topics: " + e.getMessage(), 500, 50000));
            }
        }
    };
}
