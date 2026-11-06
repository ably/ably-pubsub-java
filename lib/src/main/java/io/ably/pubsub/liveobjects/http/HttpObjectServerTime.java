package io.ably.pubsub.liveobjects.http;

import io.ably.pubsub.http.PubSubBase;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.Callback;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.util.Log;
import io.ably.pubsub.util.SystemClock;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * [Internal] Server time for one HTTP client, used by
 * {@link io.ably.pubsub.liveobjects.HttpObject#generateObjectId}: fetched from {@code /time} on
 * first use, then derived from the stored offset to the local clock.
 *
 * <p>The offset is kept per client, so each client queries its own endpoint, and concurrent first
 * callers share one {@code /time} request. A blocking caller fetches on its own thread, and other
 * callers wait for it. An async caller fetches on the HTTP scheduler; later async callers wait for
 * it, but a blocking caller then fetches separately instead of blocking on the scheduler (which
 * could deadlock if called from a scheduler thread). After a failure the next call tries again.
 *
 * <p>Spec: RTO16, RTO16a
 */
public final class HttpObjectServerTime {

    private static final String TAG = HttpObjectServerTime.class.getName();

    private final PubSubBase client;
    private volatile Long offset;

    private enum Fetch { NONE, BLOCKING, ASYNC }

    /** Guards {@link #inFlight} and {@link #waiters}. */
    private final Object lock = new Object();
    /** The kind of {@code /time} request in flight, if any. */
    private Fetch inFlight = Fetch.NONE;
    /** Callers waiting for the in-flight request. */
    private final List<Callback<Long>> waiters = new ArrayList<>();

    public HttpObjectServerTime(@NotNull PubSubBase client) {
        this.client = client;
    }

    /** Blocking: the current server time, fetching it first if needed. */
    long currentTimeMillis() throws AblyException {
        Long known = knownTime();
        if (known != null) return known;

        final Waiter waiter = new Waiter();
        Fetch role;
        synchronized (lock) {
            known = knownTime();
            if (known != null) return known;
            role = inFlight;
            if (role == Fetch.NONE) {
                inFlight = Fetch.BLOCKING; // this thread fetches
            } else if (role == Fetch.BLOCKING) {
                waiters.add(waiter); // the fetching thread does blocking I/O; safe to wait for
            }
        }
        if (role == Fetch.BLOCKING) return waiter.await();
        if (role == Fetch.ASYNC) {
            // Don't wait on the scheduler (could deadlock on a scheduler thread); fetch independently.
            long serverTime = client.time();
            storeOffset(serverTime);
            return serverTime;
        }

        long serverTime;
        try {
            serverTime = client.time();
        } catch (AblyException | RuntimeException e) {
            notifyWaiters(null, e instanceof AblyException ? ((AblyException) e).errorInfo : AblyException.fromThrowable(e).errorInfo);
            throw e;
        }
        storeOffset(serverTime);
        notifyWaiters(serverTime, null);
        return serverTime;
    }

    /**
     * Asynchronous: reports the current server time to {@code callback}, fetching it first if
     * needed. When the time is already known the callback is invoked on the calling thread.
     */
    void currentTimeMillisAsync(@NotNull Callback<Long> callback) {
        Long known = knownTime();
        if (known != null) {
            callback.onSuccess(known);
            return;
        }
        synchronized (lock) {
            known = knownTime();
            if (known == null) {
                waiters.add(callback);
                if (inFlight != Fetch.NONE) return; // the in-flight request notifies this callback
                inFlight = Fetch.ASYNC;
            }
        }
        if (known != null) {
            callback.onSuccess(known);
            return;
        }
        try {
            // A blocking fetch on the scheduler rather than timeAsync, whose callback is never invoked
            // if decoding the /time response throws (e.g. a 200 of [] or HTML), leaving the waiters hung.
            client.http.<Void>request((http, ignored) -> http.execute(this::fetchForWaiters)).async(null);
        } catch (RuntimeException e) {
            // the request never started; release the waiters so later calls can try again
            notifyWaiters(null, AblyException.fromThrowable(e).errorInfo);
        }
    }

    /** The scheduled async fetch: always ends the in-flight request and notifies the waiters. */
    private void fetchForWaiters() {
        long serverTime;
        try {
            serverTime = client.time();
        } catch (AblyException | RuntimeException e) {
            notifyWaiters(null, e instanceof AblyException ? ((AblyException) e).errorInfo : AblyException.fromThrowable(e).errorInfo);
            return;
        }
        storeOffset(serverTime);
        notifyWaiters(serverTime, null);
    }

    /** A blocking caller waiting for another caller's request. */
    private static final class Waiter implements Callback<Long> {
        private final CountDownLatch done = new CountDownLatch(1);
        private volatile Long time;
        private volatile ErrorInfo error;

        @Override
        public void onSuccess(Long serverTime) {
            time = serverTime;
            done.countDown();
        }

        @Override
        public void onError(ErrorInfo reason) {
            error = reason;
            done.countDown();
        }

        long await() throws AblyException {
            try {
                done.await(); // the HTTP client's own timeouts bound the request
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw AblyException.fromThrowable(e);
            }
            if (error != null) throw AblyException.fromErrorInfo(error);
            return time;
        }
    }

    private void storeOffset(long serverTime) {
        offset = serverTime - SystemClock.clockFrom(client.options).currentTimeMillis();
    }

    private Long knownTime() {
        Long known = offset;
        return known != null ? SystemClock.clockFrom(client.options).currentTimeMillis() + known : null;
    }

    /**
     * Ends the in-flight request, then notifies and removes every waiter; one waiter's exception
     * doesn't stop the others.
     */
    private void notifyWaiters(Long serverTime, ErrorInfo error) {
        List<Callback<Long>> toNotify;
        synchronized (lock) {
            toNotify = new ArrayList<>(waiters);
            waiters.clear();
            inFlight = Fetch.NONE;
        }
        for (Callback<Long> waiter : toNotify) {
            try {
                if (error != null) {
                    waiter.onError(error);
                } else {
                    waiter.onSuccess(serverTime);
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Server time callback threw", e);
            }
        }
    }
}
