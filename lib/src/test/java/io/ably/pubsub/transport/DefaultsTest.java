package io.ably.pubsub.transport;

import org.junit.Test;

import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class DefaultsTest {

    @Test
    public void protocol_version_CSV2() {
        assertThat(Defaults.ABLY_PROTOCOL_VERSION, is("6"));
    }

    /**
     * Spec: REC1a, REC2c1
     */
    @Test
    public void default_endpoint() {
        assertThat(Defaults.getPrimaryDomain(null), is("main.realtime.ably.net"));
        assertThat(Defaults.getEndpointFallbackHosts(null), is(new String[] {
            "main.a.fallback.ably-realtime.com",
            "main.b.fallback.ably-realtime.com",
            "main.c.fallback.ably-realtime.com",
            "main.d.fallback.ably-realtime.com",
            "main.e.fallback.ably-realtime.com"
        }));
        assertThat(Defaults.HOST_FALLBACKS, is(Defaults.getEndpointFallbackHosts(null)));
    }

    /**
     * Spec: REC1b4, REC2c4
     */
    @Test
    public void routing_policy_endpoint() {
        assertThat(Defaults.getPrimaryDomain("acme"), is("acme.realtime.ably.net"));
        assertThat(Defaults.getEndpointFallbackHosts("acme"), is(new String[] {
            "acme.a.fallback.ably-realtime.com",
            "acme.b.fallback.ably-realtime.com",
            "acme.c.fallback.ably-realtime.com",
            "acme.d.fallback.ably-realtime.com",
            "acme.e.fallback.ably-realtime.com"
        }));
    }

    /**
     * Spec: REC1b3, REC2c3
     */
    @Test
    public void nonprod_routing_policy_endpoint() {
        assertThat(Defaults.getPrimaryDomain("nonprod:sandbox"), is("sandbox.realtime.ably-nonprod.net"));
        assertThat(Defaults.getEndpointFallbackHosts("nonprod:sandbox"), is(new String[] {
            "sandbox.a.fallback.ably-realtime-nonprod.com",
            "sandbox.b.fallback.ably-realtime-nonprod.com",
            "sandbox.c.fallback.ably-realtime-nonprod.com",
            "sandbox.d.fallback.ably-realtime-nonprod.com",
            "sandbox.e.fallback.ably-realtime-nonprod.com"
        }));
    }

    /**
     * Spec: REC1b2, REC2c2
     */
    @Test
    public void hostname_endpoint() {
        for (String hostname : new String[] { "foo.example.com", "localhost", "127.0.0.1", "::1" }) {
            assertThat(Defaults.getPrimaryDomain(hostname), is(hostname));
            assertThat(Defaults.getEndpointFallbackHosts(hostname), is(emptyArray()));
        }
    }
}
