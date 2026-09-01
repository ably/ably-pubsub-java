package io.ably.lib.push;

import com.google.gson.JsonObject;
import io.ably.lib.http.BasePaginatedQuery;
import io.ably.lib.http.Http;
import io.ably.lib.http.HttpCore;
import io.ably.lib.http.HttpScheduler;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Channel;
import io.ably.lib.rest.DeviceDetails;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.AsyncPaginatedResult;
import io.ably.lib.types.Callback;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import io.ably.lib.util.ParamsUtils;

/**
 * Enables devices to subscribe to push notifications for a channel.
 */
public class PushChannel {
    protected final Channel channel;
    protected final AblyRest rest;

    public PushChannel(Channel channel, AblyRest rest) {
        this.channel = channel;
        this.rest = rest;
    }

    /**
     * Subscribes all devices associated with the current device's clientId to push notifications for the channel.
     * <p>
     * Spec: RSH7b
     * @throws AblyException
     */
    public void subscribeClient() throws AblyException {
        subscribeClientImpl().sync();
    }

    /**
     * Asynchronously subscribes all devices associated with the current device's clientId to push notifications for the channel.
     * <p>
     * Spec: RSH7b
     * @param listener A listener may optionally be passed in to this call to be notified of success or failure.
     * <p>
     * This listener is invoked on a background thread.
     * @throws AblyException
     */
    public void subscribeClientAsync(CompletionListener listener) {
        subscribeClientImpl().async(new CompletionListener.ToCallback<>(listener));
    }

    protected Http.Request<Void> subscribeClientImpl() {
        JsonObject bodyJson = new JsonObject();
        try {
            bodyJson.addProperty("clientId", getClientId());
        } catch (AblyException e) {
            return rest.http.failedRequest(e);
        }

        return postSubscription(bodyJson);
    }

    /**
     * Subscribes the device to push notifications for the channel.
     * <p>
     * Spec: RSH7a
     * @throws AblyException
     */
    public void subscribeDevice() throws AblyException {
        subscribeDeviceImpl().sync();
    }

    /**
     * Asynchronously subscribes the device to push notifications for the channel.
     * <p>
     * Spec: RSH7a
     * @param listener A listener may optionally be passed in to this call to be notified of success or failure.
     * <p>
     * This listener is invoked on a background thread.
     * @throws AblyException
     */
    public void subscribeDeviceAsync(CompletionListener listener) {
        subscribeDeviceImpl().async(new CompletionListener.ToCallback<>(listener));
    }

    protected Http.Request<Void> subscribeDeviceImpl() {
        try {
            DeviceDetails device = getDevice();
            JsonObject bodyJson = new JsonObject();
            bodyJson.addProperty("deviceId", device.id);

            return postSubscription(bodyJson);
        } catch(AblyException e) {
            return rest.http.failedRequest(e);
        }
    }

    protected Http.Request<Void> postSubscription(JsonObject bodyJson) {
        bodyJson.addProperty("channel", channel.name);
        final HttpCore.RequestBody body = HttpUtils.requestBodyFromGson(bodyJson, rest.options.useBinaryProtocol);

        return rest.http.request(new Http.Execute<Void>() {
            @Override
            public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
                Param[] params = ParamsUtils.enrichParams(null, rest.options);
                http.post("/push/channelSubscriptions", rest.push.pushRequestHeaders(true), params, body, null, true, callback);
            }
        });
    }

    /**
     * Unsubscribes all devices associated with the current device's clientId from receiving push notifications for the channel.
     * <p>
     * Spec: RSH7d
     * @throws AblyException
     */
    public void unsubscribeClient() throws AblyException {
        unsubscribeClientImpl().sync();
    }

    /**
     * Asynchronously unsubscribes all devices associated with the current device's clientId from receiving push notifications for the channel.
     * <p>
     * Spec: RSH7d
     * @param listener A listener may optionally be passed in to this call to be notified of success or failure.
     * <p>
     * This listener is invoked on a background thread.
     * @throws AblyException
     */
    public void unsubscribeClientAsync(CompletionListener listener) {
        unsubscribeClientImpl().async(new CompletionListener.ToCallback<>(listener));
    }

    protected Http.Request<Void> unsubscribeClientImpl() {
        try {
            Param[] params = new Param[] { new Param("channel", channel.name), new Param("clientId", getClientId()) };
            return delSubscription(params);
        } catch(AblyException e) {
            return rest.http.failedRequest(e);
        }
    }

    /**
     * Unsubscribes the device from receiving push notifications for the channel.
     * <p>
     * Spec: RSH7c
     * @throws AblyException
     */
    public void unsubscribeDevice() throws AblyException {
        unsubscribeDeviceImpl().sync();
    }

    /**
     * Unsubscribes the device from receiving push notifications for the channel.
     * <p>
     * Spec: RSH7c
     * @param listener A listener may optionally be passed in to this call to be notified of success or failure.
     * <p>
     * This listener is invoked on a background thread.
     * @throws AblyException
     */
    public void unsubscribeDeviceAsync(CompletionListener listener) {
        unsubscribeDeviceImpl().async(new CompletionListener.ToCallback<>(listener));
    }

    protected Http.Request<Void> unsubscribeDeviceImpl() {
        try {
            DeviceDetails device = getDevice();
            Param[] params = new Param[] { new Param("channel", channel.name), new Param("deviceId", device.id) };
            return delSubscription(params);
        } catch(AblyException e) {
            return rest.http.failedRequest(e);
        }
    }

    protected Http.Request<Void> delSubscription(Param[] params) {
        final Param[] finalParams = ParamsUtils.enrichParams(params, rest.options);
        return rest.http.request(new Http.Execute<Void>() {
            @Override
            public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
                http.del("/push/channelSubscriptions", rest.push.pushRequestHeaders(true), finalParams, null, true, callback);
            }
        });
    }

    /**
     * Retrieves all push subscriptions for the channel.
     * <p>
     * Spec: RSH7e
     * @return A {@link PaginatedResult} object containing an array of {@link Push.ChannelSubscription} objects.
     * @throws AblyException
     */
    public PaginatedResult<Push.ChannelSubscription> listSubscriptions() throws AblyException {
        return listSubscriptions(new Param[] {});
    }

    /**
     * Retrieves all push subscriptions for the channel.
     * Subscriptions can be filtered using a params object.
     * <p>
     * Spec: RSH7e
     * @param params An array of {@link Param} objects.
     * @return A {@link PaginatedResult} object containing an array of {@link Push.ChannelSubscription} objects.
     * @throws AblyException
     */
    public PaginatedResult<Push.ChannelSubscription> listSubscriptions(Param[] params) throws AblyException {
        return listSubscriptionsImpl(params).sync();
    }

    /**
     * Asynchronously retrieves all push subscriptions for the channel.
     * <p>
     * Spec: RSH7e
     * @param callback A Callback returning {@link AsyncPaginatedResult} object containing an array of {@link Push.ChannelSubscription} objects.
     * @throws AblyException
     */
    public void listSubscriptionsAsync(Callback<AsyncPaginatedResult<Push.ChannelSubscription>> callback) {
        listSubscriptionsAsync(new Param[] {}, callback);
    }

    /**
     * Asynchronously retrieves all push subscriptions for the channel.
     * Subscriptions can be filtered using a params object.
     * <p>
     * Spec: RSH7e
     * @param params An array of {@link Param} objects.
     * @param callback A Callback returning {@link AsyncPaginatedResult} object containing an array of {@link Push.ChannelSubscription} objects.
     * @throws AblyException
     */
    public void listSubscriptionsAsync(Param[] params, Callback<AsyncPaginatedResult<Push.ChannelSubscription>> callback) {
        listSubscriptionsImpl(params).async(callback);
    }

    protected BasePaginatedQuery.ResultRequest<Push.ChannelSubscription> listSubscriptionsImpl(Param[] params) {
        params = Param.set(params, "concatFilters", "true");

        return new BasePaginatedQuery<Push.ChannelSubscription>(rest.http, "/push/channelSubscriptions", rest.push.pushRequestHeaders(true), params, Push.ChannelSubscription.httpBodyHandler).get();
    }

    protected String getClientId() throws AblyException {
        String clientId = getDevice().clientId;
        if (clientId == null) {
            throw AblyException.fromThrowable(new Exception("cannot subscribe with null client ID"));
        }
        return clientId;
    }

    protected DeviceDetails getDevice() throws AblyException {
        LocalDevice localDevice = rest.push.getActivationContext().getLocalDevice();
        if (localDevice == null || localDevice.deviceIdentityToken == null) {
            // Alternatively, we could store a queue of pending subscriptions in the
            // device storage. But then, in order to know if this subscription operation
            // succeeded, you would have to add a BroadcastReceiver in AndroidManifest.xml.
            // Arguably that encourages just ignoring any errors, and forcing you to listen
            // to the broadcast after push.activate has finished before subscribing is
            // more robust.
            throw AblyException.fromThrowable(new Exception("cannot use device before AblyRest.push.activate has finished"));
        }
        return localDevice;
    }
}
