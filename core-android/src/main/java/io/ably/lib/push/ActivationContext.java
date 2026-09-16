package io.ably.lib.push;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.VisibleForTesting;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.WeakHashMap;

import io.ably.lib.rest.AblyRest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.RegistrationToken;
import io.ably.lib.util.Log;

public class ActivationContext {
    public ActivationContext(Context context) {
        this.context = context;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    Context getContext() {
        return context;
    }
    SharedPreferences getPreferences() { return prefs; }

    public synchronized LocalDevice getLocalDevice() {
        if(localDevice == null) {
            Log.v(TAG, "getLocalDevice(): creating new instance and returning that");
            Storage storage = ably != null ? ably.options.localStorage : null;

            localDevice = new LocalDevice(this, storage);
        } else {
            Log.v(TAG, "getLocalDevice(): returning existing instance");
        }
        return localDevice;
    }

    public synchronized void setActivationStateMachine(ActivationStateMachine activationStateMachine) {
        Log.v(TAG, "setActivationStateMachine(): activationStateMachine=" + activationStateMachine);
        this.activationStateMachine = activationStateMachine;
    }

    public synchronized ActivationStateMachine getActivationStateMachine() {
        if(activationStateMachine == null) {
            Log.v(TAG, "getActivationStateMachine(): creating new instance and returning that");
            activationStateMachine = new ActivationStateMachine(this);
        } else {
            Log.v(TAG, "getActivationStateMachine(): returning existing instance");
        }
        return activationStateMachine;
    }

    public void setAbly(AblyRest ably) {
        this.ably = ably;
        this.clientId = ably.auth.clientId;
    }

    AblyRest getAbly() throws AblyException {
        if(ably != null) {
            Log.v(TAG, "getAbly(): returning existing Ably instance");
            return ably;
        } else {
            // In this case, we received a new FCM token while the app is offline,
            // so we have to initialize the Ably client to send it to the server.
            Log.v(TAG, "getAbly(): creating new Ably instance");
        }

        String deviceIdentityToken = getLocalDevice().deviceIdentityToken;
        if(deviceIdentityToken == null) {
            Log.e(TAG, "getAbly(): unable to create Ably instance using deviceIdentityToken");
            throw AblyException.fromErrorInfo(new ErrorInfo("Unable to get Ably library instance; no device identity token", 40000, 400));
        }
        Log.v(TAG, "getAbly(): returning Ably instance using deviceIdentityToken");
        // TODO: We need to persist Ably client options such as the environment with `deviceIdentityToken` and use these options during initialization.
        return (ably = new AblyRest(deviceIdentityToken));
    }

    /**
     * @return AblyRest instance with device identity token auth. We use this instance to perform
     * deregistration calls in push activation flow.
     */
    AblyRest getDeviceIdentityTokenBasedAblyClient(String deviceIdentityToken) throws AblyException {
        ClientOptions clientOptions = ably.options.copy();
        clientOptions.clearAuthOptions();
        clientOptions.token = deviceIdentityToken;
        return new AblyRest(clientOptions);
    }

    public boolean setClientId(String clientId, boolean propagateGotPushDeviceDetails) {
        Log.v(TAG, "setClientId(): clientId=" + clientId + ", propagateGotPushDeviceDetails=" + propagateGotPushDeviceDetails);
        boolean updated = !clientId.equals(this.clientId);
        if(updated) {
            this.clientId = clientId;
            if(localDevice != null) {
                Log.v(TAG, "setClientId(): local device exists");
                /* Spec: RSH8d */
                localDevice.setClientId(clientId);
                if(localDevice.isRegistered() && activationStateMachine != null && propagateGotPushDeviceDetails) {
                    /* Spec: RSH8e */
                    activationStateMachine.handleEvent(new ActivationStateMachine.GotPushDeviceDetails());
                }
            } else {
                Log.v(TAG, "setClientId(): local device doest not exist");
            }
        }
        return updated;
    }

    public void onNewRegistrationToken(RegistrationToken.Type type, String token) {
        Log.v(TAG, "onNewRegistrationToken(): type=" + type + ", token=" + token);
        LocalDevice localDevice = getLocalDevice();
        RegistrationToken previous = localDevice.getRegistrationToken();
        if (previous != null) {
            if (previous.type != type) {
                Log.e(TAG, "trying to register device with " + type + ", but it was already registered with " + previous.type);
                return;
            }
            if (previous.token.equals(token)) {
                return;
            }
        }
        Log.v(TAG, "onNewRegistrationToken(): updating token");
        localDevice.setAndPersistRegistrationToken(new RegistrationToken(type, token));
        getActivationStateMachine().handleEvent(new ActivationStateMachine.GotPushDeviceDetails());
    }

    /**
     * Should be used in tests only
     */
    @VisibleForTesting
    public void reset() {
        Log.v(TAG, "reset()");

        ably = null;

        getActivationStateMachine().reset();
        activationStateMachine = null;

        getLocalDevice().reset();
        localDevice = null;
    }

    public static ActivationContext getActivationContext(Context applicationContext) {
        return getActivationContext(applicationContext, null);
    }

    public static ActivationContext getActivationContext(Context applicationContext, AblyRest ably) {
        ActivationContext activationContext;
        synchronized (activationContexts) {
            activationContext = activationContexts.get(applicationContext);
            if(activationContext == null) {
                Log.v(TAG, "getActivationContext(): creating new ActivationContext for this application");
                activationContexts.put(applicationContext, (activationContext = new ActivationContext(applicationContext)));
            } else {
                Log.v(TAG, "getActivationContext(): returning existing ActivationContext for this application");
            }
            if(ably != null) {
                Log.v(TAG, "Setting Ably instance on the activation context");
                activationContext.setAbly(ably);
            } else {
                Log.v(TAG, "Not setting Ably instance on the activation context");
            }
        }
        return activationContext;
    }

    protected void getRegistrationToken(final Callback<String> callback) {
        Log.v(TAG, "getRegistrationToken(): callback=" + callback);
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            Log.v(TAG, "getRegistrationToken(): FirebaseMessaging#getToken() completed: task=" + task);
            if(task.isSuccessful()) {
                String registrationToken = task.getResult();
                callback.onSuccess(registrationToken);
            } else {
                callback.onError(ErrorInfo.fromThrowable(task.getException()));
            }
        });
    }

    public static void setActivationContext(Context applicationContext, ActivationContext activationContext) {
        Log.v(TAG, "setActivationContext(): applicationContext=" + applicationContext + ", activationContext=" + activationContext);
        activationContexts.put(applicationContext, activationContext);
    }

    protected AblyRest ably;
    protected String clientId;
    protected ActivationStateMachine activationStateMachine;
    protected LocalDevice localDevice;
    protected final SharedPreferences prefs;
    protected final Context context;

    private static final WeakHashMap<Context, ActivationContext> activationContexts = new WeakHashMap<>();
    private static final String TAG = ActivationContext.class.getName();
}
