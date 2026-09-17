package io.ably.pubsub.test.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.http.Channel;
import io.ably.pubsub.test.common.Setup;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import org.junit.Test;

/**
 * Test for basic Channel operation not related to publish or history
 */
public class HttpChannelTest {
    /**
     * Test if ably.channel.get() returns same object if the same name is given
     * Tests RTN2, RTN3, RSN3a, RSN4a
     */
    @Test
    public void channel_object_caching() throws AblyException {
        Setup.TestVars testVars = Setup.getTestVars();
        ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
        PubSubHttpClient pubSubHttpClient = new PubSubHttpClient(opts);

        Channel channel1 = pubSubHttpClient.channels.get("channel_1");
        Channel channel2 = pubSubHttpClient.channels.get("channel_2");

        Channel channel1_copy = pubSubHttpClient.channels.get("channel_1");

        assertEquals("Verify channel objects are cached", channel1, channel1_copy);
        assertNotEquals("Verify channel objects are different if different names are requested", channel1, channel2);

        /* Test channel enumeration */
        assertEquals("Verify total number of channels", pubSubHttpClient.channels.size(), 2);
        assertTrue("Verify there is channel 1 in the list", pubSubHttpClient.channels.containsKey("channel_1"));
        assertTrue("Verify there is channel 2 in the list", pubSubHttpClient.channels.containsKey("channel_2"));
        for (final Channel channel : pubSubHttpClient.channels.values()) {
            assertTrue("Verify correct channels are in the hashmap",
                    channel == channel1 || channel == channel2);
        }
        pubSubHttpClient.channels.release("channel_1");
        channel1_copy = pubSubHttpClient.channels.get("channel_1");
        assertNotEquals("Verify channel is re-created after release", channel1, channel1_copy);
    }
}
