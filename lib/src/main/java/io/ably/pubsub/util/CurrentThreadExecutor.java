package io.ably.pubsub.util;

import io.ably.pubsub.http.CloseableExecutor;

public class CurrentThreadExecutor implements CloseableExecutor {
    public static CurrentThreadExecutor INSTANCE = new CurrentThreadExecutor();

    @Override
    public void execute(Runnable runnable) {
        runnable.run();
    }

    @Override
    public void close() throws Exception {
        // nothing to do
    }
}
