package io.ably.lib.push;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.rest.AblyBase;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Param;
import io.ably.lib.util.Log;

import java.util.Arrays;

/**
 * Enables a device to be registered and deregistered from receiving push notifications.
 */
public class Push extends PushBase {

    public Push(AblyBase rest) {
        super(rest);
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
            throw AblyException.fromErrorInfo(new ErrorInfo("Unable to get application context; not set", 40000, 400));
        }
        return applicationContext;
    }

    protected ActivationContext activationContext = null;

    public ActivationContext getActivationContext() throws AblyException {
        if (activationContext == null) {
            Log.v(TAG, "getActivationContext(): creating a new context and returning that");
            Context applicationContext = getApplicationContext();
            activationContext = ActivationContext.getActivationContext(applicationContext, (AblyRest)rest);
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
