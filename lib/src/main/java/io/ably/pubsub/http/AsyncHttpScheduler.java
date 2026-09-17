package io.ably.pubsub.http;

import io.ably.pubsub.types.ClientOptions;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.ably.pubsub.util.Log;

/**
 * A HttpScheduler that uses a thread pool to run HTTP operations.
 */
public class AsyncHttpScheduler extends HttpScheduler {
    public AsyncHttpScheduler(HttpCore httpCore, ClientOptions options) {
        super(httpCore, new CloseableThreadPoolExecutor(options));
    }

    private AsyncHttpScheduler(HttpCore httpCore, CloseableExecutor executor) {
        super(httpCore, executor);
    }

    private static final long KEEP_ALIVE_TIME = 2000L;

    protected static final String TAG = AsyncHttpScheduler.class.getName();

    /**
     * [Internal Method]
     * <p>
     * We use this method to implement proxy Realtime / HTTP clients that add additional data to the underlying client.
     */
    public AsyncHttpScheduler exchangeHttpCore(HttpCore httpCore) {
        return new AsyncHttpScheduler(httpCore, this.executor);
    }

    public void connect() {
        ((CloseableThreadPoolExecutor) executor).connect();
    }

    private static class CloseableThreadPoolExecutor implements CloseableExecutor {
        // can be accessed by multiple threads, so needs to be volatile
        private volatile ThreadPoolExecutor executor;
        private final ClientOptions options;

        CloseableThreadPoolExecutor(final ClientOptions options) {
            this.options = options;
            executor = new ThreadPoolExecutor(
                options.asyncHttpThreadpoolSize,
                options.asyncHttpThreadpoolSize,
                KEEP_ALIVE_TIME,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>()
            );
        }

        @Override
        public void execute(final Runnable command) {
            executor.execute(command);
        }

        @Override
        public void close() throws Exception {
            final int drainedCount = executor.shutdownNow().size();
            if (drainedCount > 0) {
                Log.w(TAG, "close() drained (cancelled) task count: " + drainedCount);
            }
        }

        public void connect() {
            if (executor.isShutdown()) {
                executor = new ThreadPoolExecutor(
                    options.asyncHttpThreadpoolSize,
                    options.asyncHttpThreadpoolSize,
                    KEEP_ALIVE_TIME,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>()
                );
            }
        }
    }
}
