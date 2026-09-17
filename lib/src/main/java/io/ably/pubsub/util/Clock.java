package io.ably.pubsub.util;

/**
 * Abstraction over time-related operations used throughout the SDK.
 *
 * <p>The default implementation, {@link SystemClock}, delegates to the real system clock and
 * standard Java concurrency primitives. Tests and debug builds can supply an alternative
 * implementation (e.g. a fake/controllable clock) via {@link io.ably.pubsub.debug.DebugOptions#clock}
 * to drive time-dependent behaviour deterministically without sleeping.
 */
public interface Clock {

    /**
     * Returns the current wall-clock time in milliseconds since the Unix epoch
     * (1 January 1970 00:00:00 UTC), analogous to {@link System#currentTimeMillis()}.
     *
     * @return current time in milliseconds
     */
    long currentTimeMillis();

    /**
     * Returns the current wall-clock time in nanoseconds since the Unix epoch
     * (1 January 1970 00:00:00 UTC), analogous to {@link System#nanoTime()}.
     *
     * @return current time in nanoseconds
     */
    long nanoTime();

    /**
     * Creates a new {@link AblyTimer} backed by this clock.
     *
     * <p>The name is used for diagnostic and logging purposes (e.g. as the underlying
     * {@link java.util.Timer} thread name).
     *
     * @param name a human-readable label for the timer; must not be {@code null}
     * @return a new {@link AblyTimer} instance ready to schedule tasks
     */
    AblyTimer newTimer(String name);

    /**
     * Causes the current thread to wait until either another thread calls
     * {@link Object#notify()} / {@link Object#notifyAll()} on {@code target}, or the
     * specified timeout elapses — analogous to {@link Object#wait(long)}.
     *
     * <p>The caller must hold the monitor of {@code target} before invoking this method,
     * exactly as required by {@link Object#wait(long)}.
     *
     * @param target  the object whose monitor the current thread holds and will wait on;
     *                must not be {@code null}
     * @param timeout maximum time to wait in milliseconds; {@code 0} means wait indefinitely
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    void waitOn(Object target, long timeout) throws InterruptedException;
}
