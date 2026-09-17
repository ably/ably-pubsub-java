package io.ably.pubsub.test.http;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import io.ably.pubsub.test.common.Setup;

@RunWith(Suite.class)
@SuiteClasses({
    HttpTest.class,
    HttpHeaderTest.class,
    HttpRequestTest.class,
    HttpAppStatsTest.class,
    HttpInitTest.class,
    HttpTimeTest.class,
    HttpAuthTest.class,
    HttpAuthAttributeTest.class,
    HttpTokenTest.class,
    HttpJWTTest.class,
    HttpCapabilityTest.class,
    HttpChannelTest.class,
    HttpChannelHistoryTest.class,
    HttpChannelPublishTest.class,
    HttpChannelBulkPublishTest.class,
    HttpChannelMessageEditTest.class,
    HttpCryptoTest.class,
    HttpPresenceTest.class,
    HttpProxyTest.class,
    HttpErrorTest.class,
    HttpPushTest.class
})
public class HttpSuite {

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        Setup.getTestVars();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        Setup.clearTestVars();
    }

    public static void main(String[] args) {
        Result result = JUnitCore.runClasses(HttpSuite.class);
        for(Failure failure : result.getFailures()) {
          System.out.println(failure.toString());
        }
    }
}
