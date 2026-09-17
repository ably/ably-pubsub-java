package io.ably.pubsub.http;

import android.content.Context;
import io.ably.pubsub.push.LocalDevice;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.util.AndroidPlatformAgentProvider;
import io.ably.pubsub.util.Log;

/**
 * A client that offers a simple stateless API to interact directly with Ably's REST API.
 *
 * This class implements {@link AutoCloseable} so you can use it in
 * try-with-resources constructs and have the JDK close it for you.
 */
public class PubSubHttpClient extends PubSubBase {
    /**
     * Construct a client object using an Ably {@link ClientOptions} object.
     * <p>
     * Not public: a client is obtained from the builders of the {@code io.ably.pubsub:device} or
     * {@code io.ably.pubsub:server} artifact, which name the side of the connection your code
     * runs on. {@link HttpClientFactory} is the seam those builders construct through.
     * <p>
     * Spec: RSC1
     * @param options A {@link ClientOptions} object to configure the client connection to Ably.
     * @throws AblyException
     */
    protected PubSubHttpClient(ClientOptions options) throws AblyException {
        super(options, new AndroidPlatformAgentProvider());
    }

    /**
     * Retrieves a {@link LocalDevice} object that represents the current state of the device as a target for push notifications.
     * <p>
     * Spec: RSH8
     * @return A {@link LocalDevice} object.
     * @throws AblyException
     */
    public LocalDevice device() throws AblyException {
        return this.push.getLocalDevice();
    }

    /**
     * Set the Android Context for this instance
     */
    public void setAndroidContext(Context context) throws AblyException {
        Log.v(TAG, "setAndroidContext(): context=" + context);
        this.platform.setAndroidContext(context);
        this.push.tryRequestRegistrationToken();
    }

    /**
     * clientId set by late initialisation
     */
    protected void onClientIdSet(String clientId) {
        Log.v(TAG, "onClientIdSet(): clientId=" + clientId);
        /* we only need to propagate any update to clientId if this is a late init */
        if(push != null && platform.hasApplicationContext()) {
            try {
                push.getActivationContext().setClientId(clientId, true);
            } catch(AblyException ae) {
                Log.e(TAG, "unable to update local device state");
            }
        }
    }

    private static final String TAG = PubSubHttpClient.class.getName();
}
