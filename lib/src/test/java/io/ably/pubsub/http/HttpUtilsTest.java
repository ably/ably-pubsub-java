package io.ably.pubsub.http;

import org.junit.Test;

import java.net.URL;

import static org.junit.Assert.assertEquals;

public class HttpUtilsTest {

    @Test
    public void hostForUrl_ipv6Literal_isBracketed() {
        assertEquals("[::1]", HttpUtils.hostForUrl("::1"));
        assertEquals("[2001:db8::1]", HttpUtils.hostForUrl("2001:db8::1"));
    }

    @Test
    public void hostForUrl_otherHosts_areUnchanged() {
        assertEquals("[::1]", HttpUtils.hostForUrl("[::1]"));
        assertEquals("127.0.0.1", HttpUtils.hostForUrl("127.0.0.1"));
        assertEquals("localhost", HttpUtils.hostForUrl("localhost"));
        assertEquals("main.realtime.ably.net", HttpUtils.hostForUrl("main.realtime.ably.net"));
    }

    @Test
    public void buildURL_ipv6Host_addressesHostAndPort() {
        URL url = HttpUtils.buildURL("http://", "::1", 8080, "/time", null);
        assertEquals("http://[::1]:8080/time", url.toString());
        assertEquals("[::1]", url.getHost());
        assertEquals(8080, url.getPort());
    }
}
