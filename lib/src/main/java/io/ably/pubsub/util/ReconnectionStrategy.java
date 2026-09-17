package io.ably.pubsub.util;

public class ReconnectionStrategy {

    /**
     * Spec: RTB1a
     *
     * @param count The retry count
     * @return The backoff coefficient
     */
    private static float getBackoffCoefficient(int count) {
        return Math.min((count + 2) / 3f, 2f);
    }

    /**
     * Spec: RTB1b
     *
     * @return The jitter coefficient
     */
    private static double getJitterCoefficient() {
        return 1 - Math.random() * 0.2;
    }

    /**
     * Spec: RTB1
     *
     * @param initialTimeout The initial timeout value
     * @param retryAttempt   integer indicating retryAttempt
     * @return RetryTimeout value for given timeout and retryAttempt.
     * If x is the value returned then,
     * Upper bound = min((retryAttempt + 2) / 3, 2) * initialTimeout,
     * Lower bound = 0.8 * Upper bound,
     * Lower bound < x < Upper bound
     */
    public static int getRetryTime(long initialTimeout, int retryAttempt) {
        return Double.valueOf(initialTimeout * getJitterCoefficient() * getBackoffCoefficient(retryAttempt)).intValue();
    }
}
