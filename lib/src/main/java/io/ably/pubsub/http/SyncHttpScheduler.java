package io.ably.pubsub.http;

import io.ably.pubsub.util.CurrentThreadExecutor;

/**
 * A HttpScheduler that runs everything in the current thread.
 */
public class SyncHttpScheduler extends HttpScheduler {
    public SyncHttpScheduler(HttpCore httpCore) {
        super(httpCore, CurrentThreadExecutor.INSTANCE);
    }
}
