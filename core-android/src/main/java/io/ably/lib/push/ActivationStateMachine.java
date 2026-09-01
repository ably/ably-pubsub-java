package io.ably.lib.push;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.ably.lib.http.Http;
import io.ably.lib.http.HttpCore;
import io.ably.lib.http.HttpScheduler;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.DeviceDetails;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Param;
import io.ably.lib.types.RegistrationToken;
import io.ably.lib.util.IntentUtils;
import io.ably.lib.util.Log;
import io.ably.lib.util.ParamsUtils;
import io.ably.lib.util.Serialisation;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Locale;

public class ActivationStateMachine {
    public static class CalledActivate extends ActivationStateMachine.Event {
        public static final String NAME = "CalledActivate";

        public static ActivationStateMachine.CalledActivate useCustomRegistrar(boolean useCustomRegistrar, SharedPreferences prefs) {
            prefs.edit().putBoolean(ActivationStateMachine.PersistKeys.PUSH_CUSTOM_REGISTRAR, useCustomRegistrar).apply();
            return new ActivationStateMachine.CalledActivate();
        }

        @Override
        public String getPersistedName() {
            return NAME;
        }

        @Override
        public String toString() {
            return NAME;
        }
    }

    public static class CalledDeactivate extends ActivationStateMachine.Event {
        public static final String NAME = "CalledDeactivate";

        static ActivationStateMachine.CalledDeactivate useCustomRegistrar(boolean useCustomRegistrar, SharedPreferences prefs) {
            prefs.edit().putBoolean(ActivationStateMachine.PersistKeys.PUSH_CUSTOM_REGISTRAR, useCustomRegistrar).apply();
            return new ActivationStateMachine.CalledDeactivate();
        }

        @Override
        public String getPersistedName() {
            return NAME;
        }

        @Override
        public String toString() {
            return NAME;
        }
    }

    public static class GotPushDeviceDetails extends ActivationStateMachine.Event {
        public static final String NAME = "GotPushDeviceDetails";

        @Override
        public String getPersistedName() {
            return NAME;
        }

        @Override
        public String toString() {
            return NAME;
        }
    }

    public static class GotDeviceRegistration extends ActivationStateMachine.Event {
        final String deviceId;
        final String deviceIdentityToken;

        public GotDeviceRegistration(String deviceId, String token) {
            this.deviceId = deviceId;
            this.deviceIdentityToken = token;
        }

        @Override
        public String toString() {
            return "GotDeviceRegistration{" +
                "deviceIdentityToken='" + deviceIdentityToken + '\'' +
                '}';
        }
    }

    public static class GettingDeviceRegistrationFailed extends ActivationStateMachine.ErrorEvent {
        public GettingDeviceRegistrationFailed(ErrorInfo reason) { super(reason); }

        @Override
        public String toString() {
            return "GettingDeviceRegistrationFailed: " + super.toString();
        }
    }

    public static class GettingPushDeviceDetailsFailed extends ActivationStateMachine.ErrorEvent {
        public GettingPushDeviceDetailsFailed(ErrorInfo reason) { super(reason); }

        @Override
        public String toString() {
            return "GettingPushDeviceDetailsFailed: " + super.toString();
        }
    }

    public static class RegistrationSynced extends ActivationStateMachine.Event {
        public static final String NAME = "RegistrationSynced";

        @Override
        public String getPersistedName() {
            return NAME;
        }

        @Override
        public String toString() {
            return NAME;
        }
    }

    public static class SyncRegistrationFailed extends ActivationStateMachine.ErrorEvent {
        public SyncRegistrationFailed(ErrorInfo reason) { super(reason); }

        @Override
        public String toString() {
            return "SyncRegistrationFailed: " + super.toString();
        }
    }

    public static class Deregistered extends ActivationStateMachine.Event {
        public static final String NAME = "Deregistered";

        @Override
        public String getPersistedName() {
            return NAME;
        }

        @Override
        public String toString() {
            return NAME;
        }
    }

    public static class DeregistrationFailed extends ActivationStateMachine.ErrorEvent {
        public DeregistrationFailed(ErrorInfo reason) { super(reason); }

        @Override
        public String toString() {
            return "DeregistrationFailed: " + super.toString();
        }
    }

    public abstract static class Event {
        /**
         * The name to be used when persisting this class, or null if this class should not be persisted.
         */
        public String getPersistedName() {
            return null;
        }

        /**
         * @param className The name of the class to rehydrate.
         * @return A new Event instance, or null if className is not supported.
         */
        public static Event constructEventByName(String className) {
            switch (className) {
                case CalledActivate.NAME:
                    return new CalledActivate();

                case CalledDeactivate.NAME:
                    return new CalledDeactivate();

                case GotPushDeviceDetails.NAME:
                    return new GotPushDeviceDetails();

                case RegistrationSynced.NAME:
                    return new RegistrationSynced();

                case Deregistered.NAME:
                    return new Deregistered();
            }

            // the class name provided was not recognised
            return null;
        }
    }

    public abstract static class ErrorEvent extends ActivationStateMachine.Event {
        public final ErrorInfo reason;
        ErrorEvent(ErrorInfo reason) { this.reason = reason; }

        @Override
        public String toString() {
            return "ErrorEvent{" +
                "reason=" + reason +
                '}';
        }
    }

    public static class NotActivated extends ActivationStateMachine.PersistentState {
        public NotActivated(ActivationStateMachine machine) { super(machine); }

        public static final String NAME = "NotActivated";

        @Override
        String getPersistedName() {
            return NAME;
        }

        @Override
        public String toString() {
            return NAME;
        }

        public ActivationStateMachine.State transition(ActivationStateMachine.Event event) {
            if (event instanceof ActivationStateMachine.CalledDeactivate) {
                LocalDevice device = machine.getDevice();

                // RSH3a1c
                if (device.isRegistered()) {
                    machine.deregister();
                    return new ActivationStateMachine.WaitingForDeregistration(machine, this);
                // RSH3a1d
                } else {
                    device.reset();
                    machine.callDeactivatedCallback(null);
                    return this;
                }
            } else if (event instanceof ActivationStateMachine.CalledActivate) {
                LocalDevice device = machine.getDevice();

                if (device.isRegistered()) {
                    machine.validateRegistration();
                    return new ActivationStateMachine.WaitingForRegistrationSync(machine, event);
                }

                if (device.getRegistrationToken() != null) {
                    machine.pendingEvents.add(new ActivationStateMachine.GotPushDeviceDetails());
                } else {
                    machine.getRegistrationToken();
                }

                if(!device.isCreated()) {
                    device.create();
                }

                return new ActivationStateMachine.WaitingForPushDeviceDetails(machine);
            } else if (event instanceof ActivationStateMachine.GotPushDeviceDetails) {
                return this;
            }
            return null;
        }
    }

    public static class WaitingForPushDeviceDetails extends ActivationStateMachine.PersistentState {
        public WaitingForPushDeviceDetails(ActivationStateMachine machine) { super(machine); }

        public static final String NAME = "WaitingForPushDeviceDetails";

        @Override
        String getPersistedName() {
            return NAME;
        }

        @Override
        public String toString() {
            return NAME;
        }

        public ActivationStateMachine.State transition(final ActivationStateMachine.Event event) {
            if (event instanceof ActivationStateMachine.CalledActivate) {
                return this;
            } else if (event instanceof ActivationStateMachine.CalledDeactivate) {
                machine.callDeactivatedCallback(null);
                return new ActivationStateMachine.NotActivated(machine);
            } else if (event instanceof ActivationStateMachine.GettingPushDeviceDetailsFailed) {
                machine.callDeactivatedCallback(((ActivationStateMachine.GettingPushDeviceDetailsFailed)event).reason);
                return new ActivationStateMachine.NotActivated(machine);
            } else if (event instanceof ActivationStateMachine.GotPushDeviceDetails) {
                final ActivationContext activationContext = machine.activationContext;
                final LocalDevice device = activationContext.getLocalDevice();

                boolean useCustomRegistrar = activationContext.getPreferences().getBoolean(ActivationStateMachine.PersistKeys.PUSH_CUSTOM_REGISTRAR, false);
                if (useCustomRegistrar) {
                    machine.invokeCustomRegistration(device, true);
                } else {
                    final AblyRest ably;
                    try {
                        ably = activationContext.getAbly();
                    } catch(AblyException ae) {
                        ErrorInfo reason = ae.errorInfo;
                        Log.e(TAG, "exception registering " + device.id + ": " + reason.toString());
                        machine.handleEvent(new ActivationStateMachine.GettingDeviceRegistrationFailed(reason));
                        return new ActivationStateMachine.NotActivated(machine);
                    }
                    final HttpCore.RequestBody body = HttpUtils.requestBodyFromGson(device.toJsonObject(), ably.options.useBinaryProtocol);
                    ably.http.request(new Http.Execute<JsonObject>() {
                        @Override
                        public void execute(HttpScheduler http, Callback<JsonObject> callback) throws AblyException {
                            Param[] params = ParamsUtils.enrichParams(null, ably.options);
                            /* this is authenticated using the Ably library credentials, plus the deviceSecret in the request body */
                            http.post("/push/deviceRegistrations", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, body, new Serialisation.HttpResponseHandler<JsonObject>(), true, callback);
                        }
                    }).async(new Callback<JsonObject>() {
                        @Override
                        public void onSuccess(JsonObject response) {
                            Log.i(TAG, "registered " + device.id);
                            JsonObject deviceIdentityTokenJson = response.getAsJsonObject("deviceIdentityToken");
                            if(deviceIdentityTokenJson == null) {
                                Log.e(TAG, "invalid device registration response (no deviceIdentityToken); deviceId = " + device.id);
                                machine.handleEvent(new ActivationStateMachine.GettingDeviceRegistrationFailed(new ErrorInfo("Invalid deviceIdentityToken in response", 40000, 400)));
                                return;
                            }
                            JsonPrimitive responseClientIdJson = response.getAsJsonPrimitive("clientId");
                            if(responseClientIdJson != null) {
                                String responseClientId = responseClientIdJson.getAsString();
                                if(device.clientId == null) {
                                    /* Spec RSH8f: there is an implied clientId in our credentials that we didn't know about */
                                    activationContext.setClientId(responseClientId, false);
                                }
                            }
                            machine.handleEvent(new ActivationStateMachine.GotDeviceRegistration(device.id, deviceIdentityTokenJson.getAsJsonPrimitive("token").getAsString()));
                        }
                        @Override
                        public void onError(ErrorInfo reason) {
                            Log.e(TAG, "error registering " + device.id + ": " + reason.toString());
                            machine.handleEvent(new ActivationStateMachine.GettingDeviceRegistrationFailed(reason));
                        }
                    });
                }

                return new ActivationStateMachine.WaitingForDeviceRegistration(machine);
            }
            return null;
        }
    }

    public static class WaitingForDeviceRegistration extends ActivationStateMachine.State {
        public WaitingForDeviceRegistration(ActivationStateMachine machine) { super(machine); }

        @Override
        public String toString() {
            return "WaitingForDeviceRegistration";
        }

        public ActivationStateMachine.State transition(ActivationStateMachine.Event event) {
            if (event instanceof ActivationStateMachine.CalledActivate) {
                return this;
            } else if (event instanceof ActivationStateMachine.GotDeviceRegistration) {
                LocalDevice device = machine.getDevice();
                ActivationStateMachine.GotDeviceRegistration gotDeviceRegistration = (ActivationStateMachine.GotDeviceRegistration) event;
                if (device.id.equals(gotDeviceRegistration.deviceId)) {
                    device.setDeviceIdentityToken(gotDeviceRegistration.deviceIdentityToken);
                } else {
                    Log.e(TAG, "error registering " + device.id + ": " + "deviceId has been changed during registration, it was " + gotDeviceRegistration.deviceId);
                    throw new IllegalStateException("DeviceId has been changed during registration");
                }
                machine.callActivatedCallback(null);
                return new ActivationStateMachine.WaitingForNewPushDeviceDetails(machine);
            } else if (event instanceof ActivationStateMachine.GettingDeviceRegistrationFailed) {
                machine.callActivatedCallback(((ActivationStateMachine.GettingDeviceRegistrationFailed) event).reason);
                return new ActivationStateMachine.NotActivated(machine);
            }
            return null;
        }
    }

    public static class WaitingForNewPushDeviceDetails extends ActivationStateMachine.PersistentState {
        public WaitingForNewPushDeviceDetails(ActivationStateMachine machine) { super(machine); }

        public static final String NAME = "WaitingForNewPushDeviceDetails";

        @Override
        String getPersistedName() {
            return NAME;
        }

        @Override
        public String toString() {
            return "WaitingForNewPushDeviceDetails";
        }

        public ActivationStateMachine.State transition(ActivationStateMachine.Event event) {
            if (event instanceof ActivationStateMachine.CalledActivate) {
                machine.callActivatedCallback(null);
                return this;
            } else if (event instanceof ActivationStateMachine.CalledDeactivate) {
                machine.deregister();
                return new ActivationStateMachine.WaitingForDeregistration(machine, this);
            } else if (event instanceof ActivationStateMachine.GotPushDeviceDetails) {
                machine.getDevice();
                machine.updateRegistration();

                return new WaitingForRegistrationSync(machine, event);
            }
            return null;
        }
    }

    public static class WaitingForRegistrationSync extends ActivationStateMachine.State {
        private final Event fromEvent;

        public WaitingForRegistrationSync(ActivationStateMachine machine, Event fromEvent) {
            super(machine);
            this.fromEvent = fromEvent;
        }

        @Override
        public String toString() {
            return "WaitingForRegistrationSync{" +
                "fromEvent=" + fromEvent +
                '}';
        }

        public ActivationStateMachine.State transition(ActivationStateMachine.Event event) {
            if (event instanceof ActivationStateMachine.CalledActivate) {
                if (fromEvent instanceof CalledActivate) {
                    // Don't handle; there's a CalledActivate ongoing already, so this one should
                    // be enqueued for when that one finishes.
                    return null;
                }
                machine.callActivatedCallback(null);
                return this;
            } else if (event instanceof RegistrationSynced) {
                if (fromEvent instanceof CalledActivate) {
                    machine.callActivatedCallback(null);
                }
                return new ActivationStateMachine.WaitingForNewPushDeviceDetails(machine);
            } else if (event instanceof SyncRegistrationFailed) {
                // TODO: Here we could try to recover ourselves if the error is e. g.
                // a networking error. Just notify the user for now.
                ErrorInfo reason = ((SyncRegistrationFailed) event).reason;
                if (fromEvent instanceof CalledActivate) {
                    machine.callActivatedCallback(reason);
                } else {
                    machine.callSyncRegistrationFailedCallback(reason);
                }
                return new AfterRegistrationSyncFailed(machine);
            }
            return null;
        }
    }

    public static class AfterRegistrationSyncFailed extends ActivationStateMachine.PersistentState {
        public AfterRegistrationSyncFailed(ActivationStateMachine machine) { super(machine); }

        public static final String NAME = "AfterRegistrationSyncFailed";

        @Override
        String getPersistedName() {
            return NAME;
        }

        @Override
        public String toString() {
            return NAME;
        }

        public ActivationStateMachine.State transition(ActivationStateMachine.Event event) {
            if (event instanceof ActivationStateMachine.CalledActivate || event instanceof ActivationStateMachine.GotPushDeviceDetails) {
                machine.validateRegistration();
                return new WaitingForRegistrationSync(machine, event);
            } else if (event instanceof ActivationStateMachine.CalledDeactivate) {
                machine.deregister();
                return new ActivationStateMachine.WaitingForDeregistration(machine, this);
            }
            return null;
        }
    }

    public static class WaitingForDeregistration extends ActivationStateMachine.State {
        private ActivationStateMachine.State previousState;

        public WaitingForDeregistration(ActivationStateMachine machine, ActivationStateMachine.State previousState) {
            super(machine);
            this.previousState = previousState;
        }

        @Override
        public String toString() {
            return "WaitingForDeregistration{" +
                "previousState=" + previousState +
                '}';
        }

        public ActivationStateMachine.State transition(ActivationStateMachine.Event event) {
            if (event instanceof ActivationStateMachine.CalledDeactivate) {
                return this;
            } else if (event instanceof ActivationStateMachine.Deregistered) {
                LocalDevice device = machine.getDevice();
                device.reset();
                machine.callDeactivatedCallback(null);
                return new ActivationStateMachine.NotActivated(machine);
            } else if (event instanceof ActivationStateMachine.DeregistrationFailed) {
                machine.callDeactivatedCallback(((ActivationStateMachine.DeregistrationFailed) event).reason);
                return previousState;
            }
            return null;
        }
    }

    private LocalDevice getDevice() {
        return activationContext.getLocalDevice();
    }

    public static abstract class State {
        protected final ActivationStateMachine machine;

        public State(ActivationStateMachine machine) {
            this.machine = machine;
        }

        public abstract ActivationStateMachine.State transition(ActivationStateMachine.Event event);
    }

    private static abstract class PersistentState extends ActivationStateMachine.State {
        PersistentState(ActivationStateMachine machine) { super(machine); }

        /**
         * @param className The name of the class to rehydrate.
         * @return A new Event instance, or null if className is not supported.
         */
        public static State constructStateByName(final String className, final ActivationStateMachine machine) {
            switch (className) {
                case NotActivated.NAME:
                    return new NotActivated(machine);

                case WaitingForPushDeviceDetails.NAME:
                    return new WaitingForPushDeviceDetails(machine);

                case WaitingForNewPushDeviceDetails.NAME:
                    return new WaitingForNewPushDeviceDetails(machine);

                case AfterRegistrationSyncFailed.NAME:
                    return new AfterRegistrationSyncFailed(machine);
            }

            return null;
        }

        abstract String getPersistedName();
    }

    private void callActivatedCallback(ErrorInfo reason) {
        sendErrorIntent("PUSH_ACTIVATE", reason);
    }

    private void callDeactivatedCallback(ErrorInfo reason) {
        sendErrorIntent("PUSH_DEACTIVATE", reason);
    }

    private void callSyncRegistrationFailedCallback(ErrorInfo reason) {
        sendErrorIntent("PUSH_UPDATE_FAILED", reason);
    }

    private void sendErrorIntent(String name, ErrorInfo error) {
        Intent intent = new Intent();
        IntentUtils.addErrorInfo(intent, error);
        sendIntent(name, intent);
    }

    private void invokeCustomRegistration(final DeviceDetails device, final boolean isNew) {
        final String deviceId = device.id;
        registerOnceReceiver("PUSH_DEVICE_REGISTERED", new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ErrorInfo error = IntentUtils.getErrorInfo(intent);
                if (error == null) {
                    Log.i(TAG, "custom registration for " + deviceId);
                    if (isNew) {
                        handleEvent(new ActivationStateMachine.GotDeviceRegistration(deviceId, intent.getStringExtra("deviceIdentityToken")));
                    } else {
                        handleEvent(new RegistrationSynced());
                    }
                } else {
                    Log.e(TAG, "error from custom registration for " + deviceId + ": " + error.toString());
                    if (isNew) {
                        handleEvent(new ActivationStateMachine.GettingDeviceRegistrationFailed(error));
                    } else {
                        handleEvent(new SyncRegistrationFailed(error));
                    }
                }
            }
        });

        Intent intent = new Intent();
        intent.putExtra("isNew", isNew);
        sendIntent("PUSH_REGISTER_DEVICE", intent);
    }

    private void invokeCustomDeregistration(final DeviceDetails device) {
        registerOnceReceiver("PUSH_DEVICE_DEREGISTERED", new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ErrorInfo error = IntentUtils.getErrorInfo(intent);
                if (error == null) {
                    Log.i(TAG, "custom deregistration for " + device.id);
                    handleEvent(new ActivationStateMachine.Deregistered());
                } else {
                    Log.e(TAG, "error from custom deregisterer for " + device.id + ": " + error.toString());
                    handleEvent(new ActivationStateMachine.DeregistrationFailed(error));
                }
            }
        });

        Intent intent = new Intent();
        sendIntent("PUSH_DEREGISTER_DEVICE", intent);
    }

    private void sendIntent(String name, Intent intent) {
        intent.setAction("io.ably.broadcast." + name);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private void registerOnceReceiver(String name, final BroadcastReceiver receiver) {
        BroadcastReceiver onceReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                LocalBroadcastManager.getInstance(context.getApplicationContext()).unregisterReceiver(this);
                receiver.onReceive(context, intent);
            }
        };
        IntentFilter filter = new IntentFilter("io.ably.broadcast." + name);
        LocalBroadcastManager.getInstance(context).registerReceiver(onceReceiver, filter);
    }

    protected void getRegistrationToken() {
        activationContext.getRegistrationToken(new Callback<String>() {
            @Override
            public void onSuccess(String token) {
                Log.i(TAG, "getInstanceId completed with new token");
                activationContext.onNewRegistrationToken(RegistrationToken.Type.FCM, token);
            }

            @Override
            public void onError(ErrorInfo error) {
                Log.e(TAG, "getInstanceId failed", AblyException.fromErrorInfo(error));
                handleEvent(new ActivationStateMachine.GettingPushDeviceDetailsFailed(error));

            }
        });
    }

    private void updateRegistration() {
        final LocalDevice device = activationContext.getLocalDevice();
        boolean useCustomRegistrar = activationContext.getPreferences().getBoolean(ActivationStateMachine.PersistKeys.PUSH_CUSTOM_REGISTRAR, false);
        if (useCustomRegistrar) {
            invokeCustomRegistration(device, false);
        } else {
            final AblyRest ably;
            try {
                ably = activationContext.getAbly();
            } catch(AblyException ae) {
                ErrorInfo reason = ae.errorInfo;
                Log.e(TAG, "exception registering " + device.id + ": " + reason.toString());
                handleEvent(new SyncRegistrationFailed(reason));
                return;
            }
            final HttpCore.RequestBody body = HttpUtils.requestBodyFromGson(device.pushRecipientJsonObject(), ably.options.useBinaryProtocol);
            ably.http.request(new Http.Execute<Void>() {
                @Override
                public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
                    Param[] params = ParamsUtils.enrichParams(null, ably.options);
                    http.patch("/push/deviceRegistrations/" + device.id, ably.push.pushRequestHeaders(true), params, body, null, false, callback);
                }
            }).async(new Callback<Void>() {
                @Override
                public void onSuccess(Void response) {
                    Log.i(TAG, "updated registration " + device.id);
                    handleEvent(new RegistrationSynced());
                }
                @Override
                public void onError(ErrorInfo reason) {
                    Log.e(TAG, "error updating registration " + device.id + ": " + reason.toString());
                    handleEvent(new SyncRegistrationFailed(reason));
                }
            });
        }
    }

    private void validateRegistration() {
        final LocalDevice device = activationContext.getLocalDevice();
        final AblyRest ably;
        try {
            ably = activationContext.getAbly();
        } catch(AblyException ae) {
            ErrorInfo reason = ae.errorInfo;
            Log.e(TAG, "exception validating registration for " + device.id + ": " + reason.toString());
            handleEvent(new SyncRegistrationFailed(reason));
            return;
        }
        /* Spec: RSH3a2a1, RSH8g: verify that the existing registration is compatible with the present credentials */
        String presentClientId = ably.auth.clientId;
        if(presentClientId != null && device.clientId != null && !presentClientId.equals(device.clientId)) {
            ErrorInfo clientIdErr = new ErrorInfo("Activation failed: present clientId is not compatible with existing device registration", 400, 61002);
            handleEvent(new SyncRegistrationFailed(clientIdErr));
            return;
        }

        boolean useCustomRegistrar = activationContext.getPreferences().getBoolean(ActivationStateMachine.PersistKeys.PUSH_CUSTOM_REGISTRAR, false);
        if (useCustomRegistrar) {
            invokeCustomRegistration(device, false);
        } else {
            ably.http.request(new Http.Execute<JsonObject>() {
                @Override
                public void execute(HttpScheduler http, Callback<JsonObject> callback) throws AblyException {
                    Param[] params = ParamsUtils.enrichParams(null, ably.options);
                    final HttpCore.RequestBody body = HttpUtils.requestBodyFromGson(device.toJsonObject(), ably.options.useBinaryProtocol);
                    http.put("/push/deviceRegistrations/" + device.id, ably.push.pushRequestHeaders(true), params, body, new Serialisation.HttpResponseHandler<JsonObject>(), true, callback);
                }
            }).async(new Callback<JsonObject>() {
                @Override
                public void onSuccess(JsonObject response) {
                    Log.i(TAG, "updated registration " + device.id);
                    JsonPrimitive responseClientIdJson = response.getAsJsonPrimitive("clientId");
                    if(responseClientIdJson != null) {
                        String responseClientId = responseClientIdJson.getAsString();
                        if(device.clientId == null) {
                            /* Spec RSH8f: there is an implied clientId in our credentials that we didn't know about */
                            activationContext.setClientId(responseClientId, false);
                        }
                    }
                    handleEvent(new RegistrationSynced());
                }
                @Override
                public void onError(ErrorInfo reason) {
                    Log.e(TAG, "error validating registration " + device.id + ": " + reason.toString());
                    handleEvent(new SyncRegistrationFailed(reason));
                }
            });
        }
    }

    private void deregister() {
        final LocalDevice device = activationContext.getLocalDevice();
        if (activationContext.getPreferences().getBoolean(ActivationStateMachine.PersistKeys.PUSH_CUSTOM_REGISTRAR, false)) {
            invokeCustomDeregistration(device);
        } else {
            final AblyRest ably;
            try {
                // RSH3d2b: use `deviceIdentityToken` to perform request
                ably = activationContext.getDeviceIdentityTokenBasedAblyClient(device.deviceIdentityToken);
            } catch(AblyException ae) {
                ErrorInfo reason = ae.errorInfo;
                Log.e(TAG, "exception registering " + device.id + ": " + reason.toString());
                handleEvent(new ActivationStateMachine.DeregistrationFailed(reason));
                return;
            }
            ably.http.request(new Http.Execute<Void>() {
                @Override
                public void execute(HttpScheduler http, Callback<Void> callback) {
                    Param[] params = ParamsUtils.enrichParams(new Param[0], ably.options);
                    Param[] headers = HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol);
                    final Param[] deviceIdentityHeaders = device.deviceIdentityHeaders();
                    http.del("/push/deviceRegistrations/" + device.id, HttpUtils.mergeHeaders(headers, deviceIdentityHeaders), params, null, true, callback);
                }
            }).async(new Callback<Void>() {
                @Override
                public void onSuccess(Void response) {
                    Log.i(TAG, "deregistered " + device.id);
                    handleEvent(new ActivationStateMachine.Deregistered());
                }
                @Override
                public void onError(ErrorInfo reason) {
                    // RSH3d2c1: ignore unauthorized or invalid credentials errors
                    if (reason.statusCode == 401 || reason.code == 40005) {
                        Log.w(TAG, "unauthorized error during deregistration " + device.id + ": " + reason);
                        handleEvent(new ActivationStateMachine.Deregistered());
                    } else {
                        Log.e(TAG, "error deregistering " + device.id + ": " + reason);
                        handleEvent(new ActivationStateMachine.DeregistrationFailed(reason));
                    }
                }
            });
        }
    }

    protected final ActivationContext activationContext;
    private final Context context;
    public ActivationStateMachine.State current;
    public ArrayDeque<ActivationStateMachine.Event> pendingEvents;
    protected boolean handlingEvent;

    public ActivationStateMachine(ActivationContext activationContext) {
        this.activationContext = activationContext;
        this.context = activationContext.getContext();
        loadPersisted();
        handlingEvent = false;
    }

    private void loadPersisted() {
        current = getPersistedState();
        pendingEvents = getPersistedPendingEvents();
    }

    private void enqueueEvent(ActivationStateMachine.Event event) {
        Log.d(TAG, "enqueuing event: " + event);
        pendingEvents.add(event);
    }

    public synchronized boolean handleEvent(ActivationStateMachine.Event event) {
        if (handlingEvent) {
            // An event's side effects may end up synchronously calling handleEvent while it's
            // itself being handled. In that case, enqueue it so it's handled next (and still
            // synchronously).
            //
            // We don't need to persist here, as the handleEvent call up the stack will eventually
            // persist when done with the synchronous transitions.
            enqueueEvent(event);
            return true;
        }

        handlingEvent = true;
        try {
            Log.d(TAG, "handling event " + event + " from state " + current);

            ActivationStateMachine.State maybeNext = current.transition(event);
            if (maybeNext == null) {
                enqueueEvent(event);
                return persist();
            }

            Log.d(TAG, "transition: " + current + " -(" + event + ")-> " + maybeNext + ".");
            current = maybeNext;

            while (true) {
                ActivationStateMachine.Event pending = pendingEvents.peek();
                if (pending == null) {
                    break;
                }

                Log.d(TAG, "attempting to consume pending event: " + pending);

                maybeNext = current.transition(pending);
                if (maybeNext == null) {
                    break;
                }
                pendingEvents.poll();

                Log.d(TAG, "transition: " + current + " -(" + pending + ")-> " + maybeNext + ".");
                current = maybeNext;
            }

            return persist();
        } finally {
            handlingEvent = false;
        }
    }

    public boolean reset() {
        SharedPreferences.Editor editor = activationContext.getPreferences().edit();
        for (Field f : ActivationStateMachine.PersistKeys.class.getDeclaredFields()) {
                try {
                    editor.remove((String) f.get(null));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
        }
        try {
            return editor.commit();
        } finally {
            loadPersisted();
        }
    }

    private boolean persist() {
        SharedPreferences.Editor editor = activationContext.getPreferences().edit();

        if (current instanceof ActivationStateMachine.PersistentState) {
            final PersistentState persistableState = (PersistentState)current;
            editor.putString(ActivationStateMachine.PersistKeys.CURRENT_STATE, persistableState.getPersistedName());
        }

        editor.putInt(ActivationStateMachine.PersistKeys.PENDING_EVENTS_LENGTH, pendingEvents.size());
        int i = 0;
        for (ActivationStateMachine.Event e : pendingEvents) {
            final String name = e.getPersistedName();
            if (name != null) {
                editor.putString(
                    String.format(Locale.ROOT, "%s[%d]", ActivationStateMachine.PersistKeys.PENDING_EVENTS_PREFIX, i),
                    name
                );
            }
            i++;
        }

        return editor.commit();
    }

    /**
     * Returns persisted state or `NotActivated` if there is no persisted state or the name of the currently persisted
     * state is not recognised.
     */
    private ActivationStateMachine.State getPersistedState() {
        final String className = activationContext.getPreferences().getString(ActivationStateMachine.PersistKeys.CURRENT_STATE, "");
        final State instance = PersistentState.constructStateByName(className, this);
        return instance == null ? new ActivationStateMachine.NotActivated(this) : instance;
    }

    private ArrayDeque<ActivationStateMachine.Event> getPersistedPendingEvents() {
        int length = activationContext.getPreferences().getInt(ActivationStateMachine.PersistKeys.PENDING_EVENTS_LENGTH, 0);
        ArrayDeque<ActivationStateMachine.Event> deque = new ArrayDeque<>(length);
        for (int i = 0; i < length; i++) {
            String className = activationContext.getPreferences().getString(String.format(Locale.ROOT, "%s[%d]", ActivationStateMachine.PersistKeys.PENDING_EVENTS_PREFIX, i), "");
            ActivationStateMachine.Event event = Event.constructEventByName(className);
            if (event != null) {
                deque.add(event);
            } else {
                // This is likely to be a difference between builds of the SDK. Perhaps related to obfuscated event
                // names having been previously persisted on this device. See:
                // https://github.com/ably/ably-java/issues/686
                Log.w(TAG, "Failed to construct push activation state machine event from persisted class name '" + className + "'.");
            }
        }
        return deque;
    }

    public static class PersistKeys {
        public static final String CURRENT_STATE = "ABLY_PUSH_CURRENT_STATE";
        static final String PENDING_EVENTS_LENGTH = "ABLY_PUSH_PENDING_EVENTS_LENGTH";
        static final String PENDING_EVENTS_PREFIX = "ABLY_PUSH_PENDING_EVENTS";
        static final String PUSH_CUSTOM_REGISTRAR = "ABLY_PUSH_REGISTRATION_HANDLER";
    }

    private static final String TAG = "AblyActivation";
}
