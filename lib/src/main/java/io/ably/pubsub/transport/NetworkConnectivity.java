package io.ably.pubsub.transport;

import io.ably.pubsub.types.ErrorInfo;

import java.util.HashSet;
import java.util.Set;

public abstract class NetworkConnectivity {

    public interface NetworkConnectivityListener {
        void onNetworkAvailable();
        void onNetworkUnavailable(ErrorInfo reason);
    }

    public void addListener(NetworkConnectivityListener listener) {
        boolean wasEmpty;
        synchronized (this) {
            wasEmpty = listeners.isEmpty();
            listeners.add(listener);
        }
        if(wasEmpty) {
            onNonempty();
        }
    }

    public void removeListener(NetworkConnectivityListener listener) {
        boolean isEmpty;
        synchronized (this) {
            listeners.remove(listener);
            isEmpty = listeners.isEmpty();
        }
        if(isEmpty) {
            onEmpty();
        }
    }

    protected void notifyNetworkAvailable() {
        NetworkConnectivityListener[] allListeners;
        synchronized(this) {
            allListeners = listeners.toArray(new NetworkConnectivityListener[listeners.size()]);
        }
        for(NetworkConnectivityListener listener: allListeners) {
            listener.onNetworkAvailable();
        }
    }

    protected void notifyNetworkUnavailable(ErrorInfo reason) {
        NetworkConnectivityListener[] allListeners;
        synchronized(this) {
            allListeners = listeners.toArray(new NetworkConnectivityListener[listeners.size()]);
        }
        for(NetworkConnectivityListener listener: allListeners) {
            listener.onNetworkUnavailable(reason);
        }
    }

    protected synchronized boolean isEmpty() {
        return listeners.isEmpty();
    }

    protected void onEmpty() {}

    protected void onNonempty() {}

    protected Set<NetworkConnectivityListener> listeners = new HashSet<NetworkConnectivityListener>();

    public static class DefaultNetworkConnectivity extends NetworkConnectivity {}

    public static class DelegatedNetworkConnectivity extends NetworkConnectivity implements NetworkConnectivityListener {
        @Override
        public void onNetworkAvailable() {
            notifyNetworkAvailable();
        }
        @Override
        public void onNetworkUnavailable(ErrorInfo reason) {
            notifyNetworkUnavailable(reason);
        }
    }
}
