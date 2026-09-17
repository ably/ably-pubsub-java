package io.ably.pubsub.test.common;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import io.ably.pubsub.types.Param;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import io.ably.pubsub.debug.DebugOptions;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;

@RunWith(Parameterized.class)
public class ParameterizedTest {
    @Parameters(name = "{0}")
    public static Iterable<Setup.TestParameters> data() {
        return Arrays.asList(
                Setup.TestParameters.TEXT,
                Setup.TestParameters.BINARY
            );
    }

    @Parameter
    public Setup.TestParameters testParams;

    @Rule
    public Timeout testTimeout = Timeout.seconds(10);

    protected static Setup.TestVars testVars;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        testVars = Setup.getTestVars();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        Setup.clearTestVars();
    }

    protected DebugOptions createOptions() throws AblyException {
        return testVars.createOptions(testParams);
    }

    protected DebugOptions createOptions(String key) throws AblyException {
        return testVars.createOptions(key, testParams);
    }

    protected void fillInOptions(ClientOptions opts) {
        testVars.fillInOptions(opts, testParams);
    }

    /**
     * Helper method to merge auth parameters
     */
    protected static Param[] mergeParams(Param[][] items) {
        Map<String, Param> merged = new HashMap<String, Param>();
        for(Param[] item : items) {
            if(item != null) {
                for(Param param : item) { merged.put(param.key, param); }
            }
        }
        return merged.values().toArray(new Param[merged.size()]);
    }

    protected static Param[] mergeParams(Param[] target, Param[] src) {
        return mergeParams(new Param[][]{target, src});
    }

    final SimpleDateFormat timestampDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Generate a channel name that conforms to Ably's restrictions but is, as far as is
     * reasonably achievable, unique to the test that's running.
     *
     * @see <a href="https://support.ably.io/support/solutions/articles/3000046824-what-restrictions-exist-for-the-name-field-of-a-channel-">What restrictions exist for the name field of a channel?</a>
     */
    protected String createChannelName(final String testTitle) {
        return this.getClass().getCanonicalName() + "/" + testTitle
                + "/" + timestampDateFormat.format(new Date());
    }
}
