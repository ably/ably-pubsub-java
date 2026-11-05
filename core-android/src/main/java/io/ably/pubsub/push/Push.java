package io.ably.pubsub.push;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import io.ably.pubsub.http.HttpRequester;
import io.ably.pubsub.http.HttpUtils;
import io.ably.pubsub.http.PubSubBase;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.util.Log;

import java.util.Arrays;

/**
 * Enables a device to be registered and deregistered from receiving push notifications.
 */
public class Push extends PushBase {

    /**
     * An {@link FcmTopics} object, for managing this device's FCM topic subscriptions.
     */
    public final FcmTopics fcmTopics;

    public Push(PubSubBase rest) {
        super(rest);
        this.fcmTopics = new FcmTopics(rest, this);
    }

    /**
     * Activates the device for push notifications with FCM or APNS, obtaining a unique identifier from them.
     * Subsequently registers the device with Ably and stores the deviceIdentityToken in local storage.
     * <p>
     * Spec: RSH2a
     * @throws AblyException
     */
    public void activate() throws AblyException {
        activate(false);
    }

    /**
     * Activates the device for push notifications with FCM or APNS, obtaining a unique identifier from them.
     * Subsequently registers the device with Ably and stores the deviceIdentityToken in local storage.
     * <p>
     * Spec: RSH2a
     * @param useCustomRegistrar
     * @throws AblyException
     */
    public void activate(boolean useCustomRegistrar) throws AblyException {
        Log.v(TAG, "activate(): useCustomRegistrar=" + useCustomRegistrar);
        Context context = getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        getStateMachine().handleEvent(ActivationStateMachine.CalledActivate.useCustomRegistrar(useCustomRegistrar, prefs));
    }

    /**
     * Deactivates the device from receiving push notifications with Ably and FCM or APNS.
     * <p>
     * Spec: RSH2b
     * @throws AblyException
     */
    public void deactivate() throws AblyException {
        deactivate(false);
    }

    /**
     * Deactivates the device from receiving push notifications with Ably and FCM or APNS.
     * <p>
     * Spec: RSH2b
     * @param useCustomRegistrar
     * @throws AblyException
     */
    public void deactivate(boolean useCustomRegistrar) throws AblyException {
        Log.v(TAG, "deactivate(): useCustomRegistrar=" + useCustomRegistrar);
        Context context = getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        getStateMachine().handleEvent(ActivationStateMachine.CalledDeactivate.useCustomRegistrar(useCustomRegistrar, prefs));
    }

    synchronized ActivationStateMachine getStateMachine() throws AblyException {
        return getActivationContext().getActivationStateMachine();
    }

    public void tryRequestRegistrationToken() {
        try {
            if (getLocalDevice().isRegistered()) {
                Log.v(TAG, "Local device is registered.");
                getStateMachine().getRegistrationToken();
            } else {
                Log.v(TAG, "Local device is not registered.");
            }
        } catch (AblyException e) {
            Log.e(TAG, "couldn't validate existing push recipient device details", e);
        }
    }

    Context getApplicationContext() throws AblyException {
        Context applicationContext = rest.platform.getApplicationContext();
        if(applicationContext == null) {
            Log.e(TAG, "getApplicationContext(): Unable to get application context; not set");
            throw AblyException.fromErrorInfo(new ErrorInfo("Unable to get application context; not set", 400, 40000));
        }
        return applicationContext;
    }

    protected ActivationContext activationContext = null;

    public ActivationContext getActivationContext() throws AblyException {
        if (activationContext == null) {
            Log.v(TAG, "getActivationContext(): creating a new context and returning that");
            Context applicationContext = getApplicationContext();
            activationContext = ActivationContext.getActivationContext(applicationContext, (PubSubHttpClient)rest);
        } else {
            Log.v(TAG, "getActivationContext(): returning existing content");
        }
        return activationContext;
    }

    /**
     * Retrieves a {@link LocalDevice} object that represents the current state of the device as a target for push notifications.
     * <p>
     * Spec: RSH8
     * @return A {@link LocalDevice} object.
     * @throws AblyException
     */
    public LocalDevice getLocalDevice() throws AblyException {
        return getActivationContext().getLocalDevice();
    }

    /**
     * Returns the local device, failing if push activation has not completed, since the device has
     * no ID or identity token to authenticate push requests with until then.
     */
    LocalDevice requireActivatedDevice() throws AblyException {
        LocalDevice localDevice = getLocalDevice();
        if (localDevice == null || localDevice.deviceIdentityToken == null) {
            // Alternatively, we could store a queue of pending subscriptions in the
            // device storage. But then, in order to know if this subscription operation
            // succeeded, you would have to add a BroadcastReceiver in AndroidManifest.xml.
            // Arguably that encourages just ignoring any errors, and forcing you to listen
            // to the broadcast after push.activate has finished before subscribing is
            // more robust.
            throw AblyException.fromThrowable(new Exception("cannot use device before PubSubHttpClient.push.activate has finished"));
        }
        return localDevice;
    }

    /**
     * Builds a request for the local device, authenticated as it.
     */
    interface LocalDeviceRequest<T> {
        HttpRequester.Request<T> build(String deviceId, Param[] headers);
    }

    /**
     * Builds a request authenticated as the local device, or a failed request if push activation has
     * not completed. The device ID and identity headers come from the same device read, so they
     * cannot disagree if the device is reset concurrently.
     */
    <T> HttpRequester.Request<T> localDeviceRequest(LocalDeviceRequest<T> request) {
        LocalDevice device;
        try {
            device = requireActivatedDevice();
        } catch (AblyException e) {
            return rest.http.failedRequest(e);
        }
        Param[] headers = HttpUtils.mergeHeaders(super.pushRequestHeaders(false), device.deviceIdentityHeaders());
        return request.build(device.id, headers);
    }

    @Override
    Param[] pushRequestHeaders(boolean forLocalDevice) {
        Param[] headers = super.pushRequestHeaders(forLocalDevice);
        if(forLocalDevice) {
            try {
                final Param[] deviceIdentityHeaders = getLocalDevice().deviceIdentityHeaders();
                if(deviceIdentityHeaders != null) {
                    Log.v(TAG, "pushRequestHeaders(): deviceIdentityHeaders=" + Arrays.toString(deviceIdentityHeaders));
                    headers = HttpUtils.mergeHeaders(headers, deviceIdentityHeaders);
                } else {
                    Log.w(TAG, "pushRequestHeaders(): Local device returned null device identity headers!");
                }
            } catch (AblyException e) {
                Log.w(TAG, "pushRequestHeaders(): Failed to get device identity headers. forLocalDevice=" + forLocalDevice, e);
            }
        }
        return headers;
    }

    Param[] pushRequestHeaders(String deviceId) {
        boolean forLocalDevice = false;
        try {
            forLocalDevice = deviceId != null && deviceId.equals(getLocalDevice().id);
        } catch (AblyException e) {
            Log.w(TAG, "pushRequestHeaders(): deviceId=" + deviceId, e);
        }
        return pushRequestHeaders(forLocalDevice);
    }

    private static final String TAG = Push.class.getName();
}
