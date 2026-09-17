package io.ably.pubsub.test.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.test.common.Helpers.AsyncWaiter;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;

public class HttpTimeTest extends ParameterizedTest {

    /**
     * Verify accuracy of time (to within 60 seconds of actual time)
     */
    @Test
    public void time0() {
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            PubSubHttpClient ably = new PubSubHttpClient(opts);
            long reportedTime = ably.time();
            long actualTime = System.currentTimeMillis();
            assertTrue(Math.abs(actualTime - reportedTime) < 60000);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("time0: Unexpected exception getting time");
        }
    }

    /**
     * Verify time can be obtained without any valid key or token
     */
    @Test
    public void time1() {
        try {
            ClientOptions opts = createOptions("not:a.key");
            PubSubHttpClient ablyNoAuth = new PubSubHttpClient(opts);
            ablyNoAuth.time();
        } catch (AblyException e) {
            e.printStackTrace();
            fail("time1: Unexpected exception getting time");
        }
    }

    /**
     * Verify time fails without valid restHost
     */
    @Test
    public void time2() {
        try {
            ClientOptions opts = createOptions("not:a.key");
            opts.environment = null;
            opts.restHost = "this.restHost.does.not.exist";
            PubSubHttpClient ably = new PubSubHttpClient(opts);
            ably.time();
            fail("time2: Unexpected success getting time");
        } catch (AblyException e) {
            assertEquals("time2: Unexpected error code", e.errorInfo.statusCode, 500);
        }
    }

    /**
     * Verify accuracy with async method
     */
    @Test
    public void time_async() {
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            final PubSubHttpClient ably = new PubSubHttpClient(opts);
            AsyncWaiter<Long> callback = new AsyncWaiter<Long>();
            ably.timeAsync(callback);
            callback.waitFor();

            if(callback.error != null) {
                fail("time_async: Unexpected error getting time");
            } else if(callback.result == null) {
                fail("time_async: No time value returned");
            } else {
                long actualTime = System.currentTimeMillis();
                assertTrue(Math.abs(actualTime - callback.result) < 60000);
            }
        } catch(AblyException e) {
            fail("time_async: Unexpected exception instancing Ably REST library");
        }
    }
}
