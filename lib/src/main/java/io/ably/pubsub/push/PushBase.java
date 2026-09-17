package io.ably.pubsub.push;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ably.pubsub.http.BasePaginatedQuery;
import io.ably.pubsub.http.Http;
import io.ably.pubsub.http.HttpCore;
import io.ably.pubsub.http.HttpScheduler;
import io.ably.pubsub.http.HttpUtils;
import io.ably.pubsub.realtime.CompletionListener;
import io.ably.pubsub.http.PubSubBase;
import io.ably.pubsub.http.DeviceDetails;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.AsyncPaginatedResult;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.PaginatedResult;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.util.Log;
import io.ably.pubsub.util.ParamsUtils;
import io.ably.pubsub.util.Serialisation;
import io.ably.pubsub.util.StringUtils;

import java.util.Arrays;
import java.util.Map;

/**
 * Enables a device to be registered and deregistered from receiving push notifications.
 */
public class PushBase {
    public PushBase(PubSubBase rest) {
        this.rest = rest;
        this.admin = new Admin(rest);
    }

    public static class Admin {
        private static final String TAG = Admin.class.getName();

        /**
         * A {@link DeviceRegistrations} object.
         * <p>
         * Spec: RSH1b
         */
        public final DeviceRegistrations deviceRegistrations;
        /**
         * A {@link ChannelSubscriptions} object.
         * <p>
         * Spec: RSH1c
         */
        public final ChannelSubscriptions channelSubscriptions;

        Admin(PubSubBase rest) {
            this.rest = rest;
            this.deviceRegistrations = new DeviceRegistrations(rest);
            this.channelSubscriptions = new ChannelSubscriptions(rest);
        }

        /**
         * Sends a push notification directly to a device, or a group of devices sharing the same clientId.
         * <p>
         * Spec: RSH1a
         *
         * @param recipient A JSON object containing the recipient details using clientId, deviceId or the underlying notifications service.
         * @param payload A JSON object containing the push notification payload.
         * @throws AblyException
         */
        public void publish(Param[] recipient, JsonObject payload) throws AblyException {
            publishImpl(recipient, payload).sync();
        }

        /**
         * Asynchronously sends a push notification directly to a device, or a group of devices sharing the same clientId.
         * <p>
         * Spec: RSH1a
         *
         * @param recipient A JSON object containing the recipient details using clientId, deviceId or the underlying notifications service.
         * @param payload A JSON object containing the push notification payload.
         * @param listener A listener to be notified of success or failure.
         * <p>
         * This listener is invoked on a background thread.
         * @throws AblyException
         */
        public void publishAsync(Param[] recipient, JsonObject payload, final CompletionListener listener) {
            publishImpl(recipient, payload).async(new CompletionListener.ToCallback<>(listener));
        }

        private Http.Request<Void> publishImpl(final Param[] recipient, final JsonObject payload)  {
            Log.v(TAG, "publishImpl(): recipient=" + Arrays.toString(recipient) + ", payload=" + payload);
            return rest.http.request(new Http.Execute<Void>() {
                @Override
                public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
                    if (recipient == null || recipient.length == 0) {
                        throw AblyException.fromThrowable(new Exception("recipient cannot be empty"));
                    }
                    if (payload == null || payload.entrySet().isEmpty()) {
                        throw AblyException.fromThrowable(new Exception("payload cannot be empty"));
                    }

                    JsonObject recipientJson = new JsonObject();
                    for (Param param : recipient) {
                        recipientJson.addProperty(param.key, param.value);
                    }
                    JsonObject bodyJson = new JsonObject();
                    bodyJson.add("recipient", recipientJson);
                    for (Map.Entry<String, JsonElement> entry : payload.entrySet()) {
                        bodyJson.add(entry.getKey(), entry.getValue());
                    }
                    HttpCore.RequestBody body = HttpUtils.requestBodyFromGson(bodyJson, rest.options.useBinaryProtocol);
                    Param[] params = ParamsUtils.enrichParams(null, rest.options);

                    http.post("/push/publish", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, body, null, true, callback);
                }
            });
        }

        private final PubSubBase rest;
    }

    /**
     * Enables the management of push notification registrations with Ably.
     */
    public static class DeviceRegistrations {
        private static final String TAG = DeviceRegistrations.class.getName();

        /**
         * Registers or updates a {@link DeviceDetails} object with Ably.
         * Returns the new, or updated {@link DeviceDetails} object.
         * <p>
         * Spec: RSH1b3
         *
         * @param device The {@link DeviceDetails} object to create or update.
         * @return A {@link DeviceDetails} object.
         * @throws AblyException
         */
        public DeviceDetails save(DeviceDetails device) throws AblyException {
            return saveImpl(device).sync();
        }

        /**
         * Asynchronously registers or updates a {@link DeviceDetails} object with Ably.
         * Returns the new, or updated {@link DeviceDetails} object.
         * <p>
         * Spec: RSH1b3
         *
         * @param device The {@link DeviceDetails} object to create or update.
         * @param callback A callback returning a {@link DeviceDetails} object.
         */
        public void saveAsync(DeviceDetails device, final Callback<DeviceDetails> callback) {
            saveImpl(device).async(callback);
        }

        protected Http.Request<DeviceDetails> saveImpl(final DeviceDetails device) {
            Log.v(TAG, "saveImpl(): device=" + device);
            final HttpCore.RequestBody body = HttpUtils.requestBodyFromGson(device.toJsonObject(), rest.options.useBinaryProtocol);
            return rest.http.request(new Http.Execute<DeviceDetails>() {
                @Override
                public void execute(HttpScheduler http, Callback<DeviceDetails> callback) {
                    Param[] params = ParamsUtils.enrichParams(null, rest.options);
                    http.put("/push/deviceRegistrations/" + device.id, rest.push.pushRequestHeaders(device.id), params, body, DeviceDetails.httpResponseHandler, true, callback);
                }
            });
        }

        /**
         * Retrieves the {@link DeviceDetails} of a device registered to receive push notifications using its deviceId.
         * <p>
         * Spec: RSH1b1
         *
         * @param deviceId The unique ID of the device.
         * @return A {@link DeviceDetails} object.
         * @throws AblyException
         */
        public DeviceDetails get(String deviceId) throws AblyException {
            return getImpl(deviceId).sync();
        }

        /**
         * Asynchronously retrieves the {@link DeviceDetails} of a device registered to receive push notifications using its deviceId.
         * <p>
         * Spec: RSH1b1
         *
         * @param deviceId The unique ID of the device.
         * @param callback A callback returning a {@link DeviceDetails} object.
         */
        public void getAsync(String deviceId, final Callback<DeviceDetails> callback) {
            getImpl(deviceId).async(callback);
        }

        protected Http.Request<DeviceDetails> getImpl(final String deviceId) {
            Log.v(TAG, "getImpl(): deviceId=" + deviceId);
            return rest.http.request(new Http.Execute<DeviceDetails>() {
                @Override
                public void execute(HttpScheduler http, Callback<DeviceDetails> callback) throws AblyException {
                    Param[] params = ParamsUtils.enrichParams(null, rest.options);
                    http.get("/push/deviceRegistrations/" + deviceId, rest.push.pushRequestHeaders(deviceId), params, DeviceDetails.httpResponseHandler, true, callback);
                }
            });
        }

        /**
         * Retrieves all devices matching the filter params provided.
         * Returns a {@link PaginatedResult} object, containing an array of {@link DeviceDetails} objects.
         * <p>
         * Spec: RSH1b2
         *
         * @param params An object containing key-value pairs to filter devices by.
         *               Can contain clientId, deviceId and a limit on the number of devices returned, up to 1,000.
         * @return A {@link PaginatedResult} object containing an array of {@link DeviceDetails} objects.
         * @throws AblyException
         */
        public PaginatedResult<DeviceDetails> list(Param[] params) throws AblyException {
            return listImpl(params).sync();
        }

        /**
         * Asynchronously retrieves all devices matching the filter params provided.
         * Returns a {@link AsyncPaginatedResult} object, containing an array of {@link DeviceDetails} objects.
         * <p>
         * Spec: RSH1b2
         *
         * @param params An object containing key-value pairs to filter devices by.
         *               Can contain clientId, deviceId and a limit on the number of devices returned, up to 1,000.
         * @param callback A callback returning a {@link AsyncPaginatedResult} object containing an array of {@link DeviceDetails} objects.
         */
        public void listAsync(Param[] params, Callback<AsyncPaginatedResult<DeviceDetails>> callback) {
            listImpl(params).async(callback);
        }

        protected BasePaginatedQuery.ResultRequest<DeviceDetails> listImpl(Param[] params) {
            Log.v(TAG, "listImpl(): params=" + Arrays.toString(params));
            return new BasePaginatedQuery<DeviceDetails>(rest.http, "/push/deviceRegistrations", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, DeviceDetails.httpBodyHandler).get();
        }

        /**
         * Removes a device registered to receive push notifications from Ably using the id property of a {@link DeviceDetails} object.
         * <p>
         * Spec: RSH1b4
         *
         * @param device The {@link DeviceDetails} object containing the id property of the device.
         * @throws AblyException
         */
        public void remove(DeviceDetails device) throws AblyException {
            remove(device.id);
        }

        /**
         * Asynchronously removes a device registered to receive push notifications from Ably using the id property of a {@link DeviceDetails} object.
         * <p>
         * Spec: RSH1b4
         *
         * @param device The {@link DeviceDetails} object containing the id property of the device.
         * @param listener A listener to be notified of success or failure.
         */
        public void removeAsync(DeviceDetails device, CompletionListener listener) {
            removeAsync(device.id, listener);
        }

        /**
         * Removes a device registered to receive push notifications from Ably using its deviceId.
         * <p>
         * Spec: RSH1b4
         *
         * @param deviceId The unique ID of the device.
         * @throws AblyException
         */
        public void remove(String deviceId) throws AblyException {
            removeImpl(deviceId).sync();
        }

        /**
         * Asynchronously removes a device registered to receive push notifications from Ably using its deviceId.
         * <p>
         * Spec: RSH1b4
         *
         * @param deviceId The unique ID of the device.
         * @param listener A listener to be notified of success or failure.
         */
        public void removeAsync(String deviceId, CompletionListener listener) {
            removeImpl(deviceId).async(new CompletionListener.ToCallback<>(listener));
        }

        protected Http.Request<Void> removeImpl(final String deviceId) {
            Log.v(TAG, "removeImpl(): deviceId=" + deviceId);
            return rest.http.request(new Http.Execute<Void>() {
                @Override
                public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
                    Param[] params = ParamsUtils.enrichParams(null, rest.options);
                    http.del("/push/deviceRegistrations/" + deviceId, rest.push.pushRequestHeaders(deviceId), params, null, true, callback);
                }
            });
        }

        /**
         * Removes all devices registered to receive push notifications from Ably matching the filter params provided.
         * <p>
         * Spec: RSH1b5
         *
         * @param params An object containing key-value pairs to filter devices by. Can contain clientId and deviceId.
         * @throws AblyException
         */
        public void removeWhere(Param[] params) throws AblyException {
            removeWhereImpl(params).sync();
        }

        /**
         * Removes all devices registered to receive push notifications from Ably matching the filter params provided.
         * <p>
         * Spec: RSH1b5
         *
         * @param params An object containing key-value pairs to filter devices by. Can contain clientId and deviceId.
         * @param listener A listener to be notified of success or failure.
         */
        public void removeWhereAsync(Param[] params, CompletionListener listener) {
            removeWhereImpl(params).async(new CompletionListener.ToCallback<>(listener));
        }

        protected Http.Request<Void> removeWhereImpl(Param[] params) {
            Log.v(TAG, "removeWhereImpl(): params=" + Arrays.toString(params));
            final Param[] finalParams = ParamsUtils.enrichParams(params, rest.options);
            return rest.http.request(new Http.Execute<Void>() {
                @Override
                public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
                    http.del("/push/deviceRegistrations", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), finalParams, null, true, callback);
                }
            });
        }

        DeviceRegistrations(PubSubBase rest) {
            this.rest = rest;
        }

        private final PubSubBase rest;
    }

    /**
     * Enables device push channel subscriptions.
     */
    public static class ChannelSubscriptions {
        private static final String TAG = ChannelSubscriptions.class.getName();

        /**
         * Subscribes a device, or a group of devices sharing the same clientId to push notifications on a channel.
         * Returns a {@link ChannelSubscription} object.
         * <p>
         * Spec: RSH1c3
         *
         * @param subscription A {@link ChannelSubscription} object.
         * @return A {@link ChannelSubscription} object describing the new or updated subscriptions.
         * @throws AblyException
         */
        public ChannelSubscription save(ChannelSubscription subscription) throws AblyException {
            return saveImpl(subscription).sync();
        }

        /**
         * Asynchronously subscribes a device, or a group of devices sharing the same clientId to push notifications on a channel.
         * Returns a {@link ChannelSubscription} object.
         * <p>
         * Spec: RSH1c3
         *
         * @param subscription A {@link ChannelSubscription} object.
         * @param callback A callback returning {@link ChannelSubscription} object describing the new or updated subscriptions.
         */
        public void saveAsync(ChannelSubscription subscription, final Callback<ChannelSubscription> callback) {
            saveImpl(subscription).async(callback);
        }

        protected Http.Request<ChannelSubscription> saveImpl(final ChannelSubscription subscription) {
            Log.v(TAG, "saveImpl(): subscription=" + subscription);
            final HttpCore.RequestBody body = HttpUtils.requestBodyFromGson(subscription.toJsonObject(), rest.options.useBinaryProtocol);
            return rest.http.request(new Http.Execute<ChannelSubscription>() {
                @Override
                public void execute(HttpScheduler http, Callback<ChannelSubscription> callback) throws AblyException {
                    Param[] params = ParamsUtils.enrichParams(null, rest.options);
                    http.post("/push/channelSubscriptions", rest.push.pushRequestHeaders(subscription.deviceId), params, body, ChannelSubscription.httpResponseHandler, true, callback);
                }
            });
        }

        /**
         * Retrieves all push channel subscriptions matching the filter params provided.
         * Returns a {@link PaginatedResult} object, containing an array of {@link ChannelSubscription} objects.
         * <p>
         * Spec: RSH1c1
         *
         * @param params An object containing key-value pairs to filter subscriptions by.
         *               Can contain channel, clientId, deviceId and a limit on the number of devices returned, up to 1,000.
         * @return A {@link PaginatedResult} object containing an array of {@link ChannelSubscription} objects.
         * @throws AblyException
         */
        public PaginatedResult<ChannelSubscription> list(Param[] params) throws AblyException {
            return listImpl(params).sync();
        }

        /**
         * Asynchronously retrieves all push channel subscriptions matching the filter params provided.
         * Returns a {@link PaginatedResult} object, containing an array of {@link ChannelSubscription} objects.
         * <p>
         * Spec: RSH1c1
         *
         * @param params An object containing key-value pairs to filter subscriptions by.
         *               Can contain channel, clientId, deviceId and a limit on the number of devices returned, up to 1,000.
         * @param callback A callback returning {@link AsyncPaginatedResult} object containing an array of {@link ChannelSubscription} objects.
         * @throws AblyException
         */
        public void listAsync(Param[] params, Callback<AsyncPaginatedResult<ChannelSubscription>> callback) {
            listImpl(params).async(callback);
        }

        protected BasePaginatedQuery.ResultRequest<ChannelSubscription> listImpl(Param[] params) {
            Log.v(TAG, "listImpl(): params=" + Arrays.toString(params));
            String deviceId = HttpUtils.getParam(params, "deviceId");
            return new BasePaginatedQuery<Push.ChannelSubscription>(rest.http, "/push/channelSubscriptions", rest.push.pushRequestHeaders(deviceId), params, ChannelSubscription.httpBodyHandler).get();
        }

        /**
         * Unsubscribes a device, or a group of devices sharing the same clientId from receiving push notifications on a channel.
         * <p>
         * Spec: RSH1c4
         *
         * @param subscription A {@link ChannelSubscription} object.
         * @throws AblyException
         */
        public void remove(ChannelSubscription subscription) throws AblyException {
            removeImpl(subscription).sync();
        }

        /**
         * Asynchronously unsubscribes a device,
         * or a group of devices sharing the same clientId from receiving push notifications on a channel.
         * <p>
         * Spec: RSH1c4
         *
         * @param subscription A {@link ChannelSubscription} object.
         * @param listener A listener to be notified of success or failure.
         * @throws AblyException
         */
        public void removeAsync(ChannelSubscription subscription, CompletionListener listener) {
            removeImpl(subscription).async(new CompletionListener.ToCallback<>(listener));
        }

        protected Http.Request<Void> removeImpl(ChannelSubscription subscription) {
            Log.v(TAG, "removeImpl(): subscription=" + subscription);
            Param[] params = new Param[] { new Param("channel", subscription.channel) };
            if (subscription.deviceId != null) {
                params = Param.push(params, "deviceId", subscription.deviceId);
            } else if (subscription.clientId != null) {
                params = Param.push(params, "clientId", subscription.clientId);
            } else {
                return rest.http.failedRequest(AblyException.fromThrowable(new Exception("ChannelSubscription cannot be for both a deviceId and a clientId")));
            }

            return removeWhereImpl(params);
        }

        /**
         * Unsubscribes all devices from receiving push notifications on a channel that match the filter params provided.
         * <p>
         * Spec: RSH1c5
         *
         * @param params An object containing key-value pairs to filter subscriptions by.
         *               Can contain channel, and optionally either clientId or deviceId.
         * @throws AblyException
         */
        public void removeWhere(Param[] params) throws AblyException {
            removeWhereImpl(params).sync();
        }

        /**
         * Asynchronously unsubscribes all devices from receiving push notifications on a channel that match the filter params provided.
         * <p>
         * Spec: RSH1c5
         *
         * @param params An object containing key-value pairs to filter subscriptions by.
         *               Can contain channel, and optionally either clientId or deviceId.
         * @param listener A listener to be notified of success or failure.
         * @throws AblyException
         */
        public void removeWhereAsync(Param[] params, CompletionListener listener) {
            removeWhereImpl(params).async(new CompletionListener.ToCallback<>(listener));
        }

        protected Http.Request<Void> removeWhereImpl(Param[] params) {
            Log.v(TAG, "removeWhereImpl(): params=" + Arrays.toString(params));
            String deviceId = HttpUtils.getParam(params, "deviceId");
            final Param[] finalParams = ParamsUtils.enrichParams(params, rest.options);
            final Param[] finalHeaders = rest.push.pushRequestHeaders(deviceId);
            return rest.http.request(new Http.Execute<Void>() {
                @Override
                public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
                    http.del("/push/channelSubscriptions", finalHeaders, finalParams, null, true, callback);
                }
            });
        }

        /**
         * Retrieves all channels with at least one device subscribed to push notifications.
         * Returns a {@link PaginatedResult} object, containing an array of channel names.
         * <p>
         * Spec: RSH1c2
         *
         * @param params An object containing key-value pairs to filter channels by.
         *               Can contain a limit on the number of channels returned, up to 1,000.
         * @return A {@link PaginatedResult} object containing an array of channel names.
         * @throws AblyException
         */
        public PaginatedResult<String> listChannels(Param[] params) throws AblyException {
            return listChannelsImpl(params).sync();
        }

        /**
         * Asynchronously retrieves all channels with at least one device subscribed to push notifications.
         * Returns a {@link PaginatedResult} object, containing an array of channel names.
         * <p>
         * Spec: RSH1c2
         *
         * @param params An object containing key-value pairs to filter channels by.
         *               Can contain a limit on the number of channels returned, up to 1,000.
         * @param callback A {@link AsyncPaginatedResult} callback returning object containing an array of channel names.
         * @throws AblyException
         */
        public void listChannelsAsync(Param[] params, Callback<AsyncPaginatedResult<String>> callback) {
            listChannelsImpl(params).async(callback);
        }

        protected BasePaginatedQuery.ResultRequest<String> listChannelsImpl(Param[] params) {
            Log.v(TAG, "listChannelsImpl(): params=" + Arrays.toString(params));
            String deviceId = HttpUtils.getParam(params, "deviceId");
            return new BasePaginatedQuery<String>(rest.http, "/push/channels", rest.push.pushRequestHeaders(deviceId), params, StringUtils.httpBodyHandler).get();
        }

        ChannelSubscriptions(PubSubBase rest) {
            this.rest = rest;
        }

        private final PubSubBase rest;
    }

    /**
     * Contains the subscriptions of a device, or a group of devices sharing the same clientId,
     * has to a channel in order to receive push notifications.
     */
    public static class ChannelSubscription {
        /**
         * The channel the push notification subscription is for.
         * <p>
         * Spec: PCS4
         */
        public final String channel;
        /**
         * The unique ID of the device.
         * <p>
         * Spec: PCS2, PCS5, PCS6
         */
        public final String deviceId;
        /**
         * The ID of the client the device, or devices are associated to.
         * <p>
         * Spec: PCS3, PCS6
         */
        public final String clientId;

        /**
         * A static factory method to create a PushChannelSubscription object for a channel and single device.
         * @param channel The channel name.
         * @param deviceId The unique ID of the device.
         * @return A {@link ChannelSubscription} object.
         */
        public static ChannelSubscription forDevice(String channel, String deviceId) {
            return new ChannelSubscription(channel, deviceId, null);
        }

        /**
         * A static factory method to create a PushChannelSubscription object for a channel and group of devices sharing the same clientId.
         * @param channel The channel name.
         * @param clientId The ID of the client.
         * @return A {@link ChannelSubscription} object.
         */
        public static ChannelSubscription forClientId(String channel, String clientId) {
            return new ChannelSubscription(channel, null, clientId);
        }

        private ChannelSubscription(String channel, String deviceId, String clientId) {
            this.channel = channel;
            this.deviceId = deviceId;
            this.clientId = clientId;
        }

        public JsonObject toJsonObject() {
            JsonObject o = new JsonObject();

            o.addProperty("channel", channel);
            if (clientId != null) {
                o.addProperty("clientId", clientId);
            }
            if (deviceId != null) {
                o.addProperty("deviceId", deviceId);
            }

            return o;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ChannelSubscription)) {
                return false;
            }
            ChannelSubscription other = (ChannelSubscription) o;
            JsonObject thisJson = this.toJsonObject();
            JsonObject otherJson = other.toJsonObject();

            return thisJson.equals(otherJson);
        }

        @Override
        public String toString() {
            return this.toJsonObject().toString();
        }

        public static ChannelSubscription fromJsonObject(JsonObject o) {
            return Serialisation.gson.fromJson(o, ChannelSubscription.class);
        }

        private static Serialisation.FromJsonElement<ChannelSubscription> fromJsonElement = new Serialisation.FromJsonElement<ChannelSubscription>() {
            @Override
            public ChannelSubscription fromJsonElement(JsonElement e) {
                return fromJsonObject((JsonObject) e);
            }
        };

        protected static HttpCore.ResponseHandler<ChannelSubscription> httpResponseHandler = new Serialisation.HttpResponseHandler<ChannelSubscription>(ChannelSubscription.class, fromJsonElement);

        protected static HttpCore.BodyHandler<ChannelSubscription> httpBodyHandler = new Serialisation.HttpBodyHandler<ChannelSubscription>(ChannelSubscription[].class, fromJsonElement);
    }

    Param[] pushRequestHeaders(boolean forLocalDevice) {
        return HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol);
    }

    Param[] pushRequestHeaders(String deviceId) {
        return pushRequestHeaders(false);
    }

    protected final PubSubBase rest;
    /**
     * A {@link PushBase.Admin} object.
     * <p>
     * Spec: RSH1
     */
    public final Admin admin;
}
