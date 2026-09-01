package io.ably.lib.platform;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import io.ably.lib.transport.NetworkConnectivity;
import io.ably.lib.types.ErrorInfo;

import java.util.WeakHashMap;

public class AndroidNetworkConnectivity extends NetworkConnectivity {

    AndroidNetworkConnectivity(Context applicationContext) {
        this.applicationContext = applicationContext;
    }

    public static AndroidNetworkConnectivity getNetworkConnectivity(Context applicationContext) {
        AndroidNetworkConnectivity networkConnectivity;
        synchronized (contexts) {
            networkConnectivity = contexts.get(applicationContext);
            if(networkConnectivity == null) {
                contexts.put(applicationContext, (networkConnectivity = new AndroidNetworkConnectivity(applicationContext)));
            }
        }
        return networkConnectivity;
    }

    protected void onNonempty() {
        activate();
    }

    protected void onEmpty() {
        deactivate();
    }

    private void activate() {
        if(networkStateReceiver == null && applicationContext != null) {
            networkStateReceiver = new NetworkStateReceiver();
            applicationContext.registerReceiver(networkStateReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        }
    }

    private void deactivate() {
        if(networkStateReceiver != null) {
            applicationContext.unregisterReceiver(networkStateReceiver);
            networkStateReceiver = null;
        }
    }

    private class NetworkStateReceiver extends BroadcastReceiver {
        public NetworkStateReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            if(intent == null || intent.getExtras() == null) {
                return;
            }

            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = manager.getActiveNetworkInfo();

            if(ni != null && ni.getState() == NetworkInfo.State.CONNECTED) {
                notifyNetworkAvailable();
            } else if(intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY,Boolean.FALSE)) {
                notifyNetworkUnavailable(new ErrorInfo("No network connection available", 503, 80003));
            }
        }
    }

    private final Context applicationContext;
    private NetworkStateReceiver networkStateReceiver;

    private static WeakHashMap<Context, AndroidNetworkConnectivity> contexts = new WeakHashMap<Context, AndroidNetworkConnectivity>();
}
