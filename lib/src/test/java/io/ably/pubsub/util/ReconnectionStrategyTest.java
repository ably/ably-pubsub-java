package io.ably.pubsub.util;

import org.junit.Test;

import java.util.Arrays;

import static io.ably.pubsub.test.common.Helpers.assertTimeoutBetween;

public class ReconnectionStrategyTest {

    @Test
    public void calculateRetryTimeoutUsingIncrementalBackoffAndJitter() {

        int[] retryAttempts = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        int initialTimeoutValue = 15; // timeout value in seconds

        int[] retryTimeouts = Arrays.stream(retryAttempts).map(attempt -> ReconnectionStrategy.getRetryTime(initialTimeoutValue, attempt)).toArray();

        assertTimeoutBetween(retryTimeouts[0], 12d, 15d);
        assertTimeoutBetween(retryTimeouts[1], 16d, 20d);
        assertTimeoutBetween(retryTimeouts[2], 20d, 25d);

        for (int i = 3; i < retryTimeouts.length; i++) {
            assertTimeoutBetween(retryTimeouts[i], 24d, 30d);
        }

        for (int retryAttempt : retryAttempts) {

            int retryTimeout = ReconnectionStrategy.getRetryTime(initialTimeoutValue, retryAttempt);
            Bounds bounds = calculateRetryBounds(retryAttempt, initialTimeoutValue);

            assertTimeoutBetween(retryTimeout, bounds.lower, bounds.upper);
        }
    }

    public Bounds calculateRetryBounds(int retryAttempt, int initialTimeout) {
        double upperBound = Math.min((retryAttempt + 2) / 3d, 2d) * initialTimeout;
        double lowerBound = 0.8 * upperBound;
        return new Bounds(lowerBound, upperBound);
    }

    static class Bounds {
        Double lower;
        Double upper;
        Bounds(Double lower, Double upper) {
            this.lower = lower;
            this.upper = upper;
        }
    }
}
