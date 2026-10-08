package io.ably.pubsub.transport;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import io.ably.pubsub.types.ClientOptions;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class HostsTest {

    private final ClientOptions options = new ClientOptions();

    /**
     * Expect the default primary host and shuffled default fallback hosts when no endpoint is set.
     */
    @Test
    public void hosts_fallback_no_endpoint() {
        // When
        Hosts hosts = new Hosts(options);

        // Then
        List<String> fallbackHosts = collectFallbackHosts(hosts);
        assertThat(hosts.getPrimaryHost(), is("main.realtime.ably.net"));
        // the returned fallback hosts should have the same elements as default host fallbacks
        assertThat(fallbackHosts, containsInAnyOrder(Defaults.HOST_FALLBACKS));
        // expect the fallback hosts to be shuffled
        assertThat(fallbackHosts, not(contains(Defaults.HOST_FALLBACKS)));
    }

    /**
     * Expect a null, when we provide empty array of fallback hosts
     */
    @Test
    public void hosts_fallback_empty_array() {
        // Given
        options.fallbackHosts = new String[] {};

        // When
        Hosts hosts = new Hosts(options);

        // Then
        assertThat(hosts.getFallback(hosts.getPrimaryHost()), nullValue());
    }

    /**
     * Expect that returned host is contained within custom host list in options,
     * but in shuffled order and the right number of them.
     */
    @Test
    public void hosts_fallback_custom_hosts() {
        // Given
        String[] customHosts = { "F.ably-realtime.com", "G.ably-realtime.com", "H.ably-realtime.com", "I.ably-realtime.com", "J.ably-realtime.com", "K.ably-realtime.com" };
        options.fallbackHosts = customHosts;

        // When
        Hosts hosts = new Hosts(options);

        // Then
        List<String> fallbackHosts = collectFallbackHosts(hosts);
        assertThat(hosts.getPrimaryHost(), is("main.realtime.ably.net"));
        // the returned fallback hosts should have the same elements as custom host fallbacks
        assertThat(fallbackHosts, containsInAnyOrder(customHosts));
        // expect the fallback hosts to be shuffled
        assertThat(fallbackHosts, not(contains(customHosts)));
    }

    /**
     * Expect the routing policy's primary host and fallback hosts.
     * <p>
     * Spec: REC1b4, REC2c4
     */
    @Test
    public void hosts_routing_policy_endpoint() {
        // Given
        options.endpoint = "acme";

        // When
        Hosts hosts = new Hosts(options);

        // Then
        assertThat(hosts.getPrimaryHost(), is("acme.realtime.ably.net"));
        assertThat(collectFallbackHosts(hosts), containsInAnyOrder(Defaults.getEndpointFallbackHosts("acme")));
    }

    /**
     * Expect the nonprod routing policy's primary host and fallback hosts.
     * <p>
     * Spec: REC1b3, REC2c3
     */
    @Test
    public void hosts_nonprod_endpoint() {
        // Given
        options.endpoint = "nonprod:sandbox";

        // When
        Hosts hosts = new Hosts(options);

        // Then
        assertThat(hosts.getPrimaryHost(), is("sandbox.realtime.ably-nonprod.net"));
        assertThat(collectFallbackHosts(hosts), containsInAnyOrder(
            "sandbox.a.fallback.ably-realtime-nonprod.com",
            "sandbox.b.fallback.ably-realtime-nonprod.com",
            "sandbox.c.fallback.ably-realtime-nonprod.com",
            "sandbox.d.fallback.ably-realtime-nonprod.com",
            "sandbox.e.fallback.ably-realtime-nonprod.com"
        ));
    }

    /**
     * Expect a null fallback when the endpoint is a hostname.
     * <p>
     * Spec: REC1b2, REC2c2
     */
    @Test
    public void hosts_no_fallback_for_hostname_endpoint() {
        // Given
        String host = "overridden.ably.io";
        options.endpoint = host;

        // When
        Hosts hosts = new Hosts(options);

        // Then
        assertThat(hosts.getPrimaryHost(), is(host));
        assertThat(hosts.getFallback(host), nullValue());
    }

    /**
     * Expect custom fallback hosts to be used even when the endpoint is a hostname.
     * <p>
     * Spec: REC2a2
     */
    @Test
    public void hosts_fallback_for_hostname_endpoint_and_fallback_hosts() {
        // Given
        options.endpoint = "custom.ably.com";
        options.fallbackHosts = new String[] { "custom-fallback.ably.com" };

        // When
        Hosts hosts = new Hosts(options);

        // Then
        assertThat(hosts.getFallback("custom.ably.com"), is("custom-fallback.ably.com"));
    }

    /**
     * Expect default fallback hosts even when a custom port is specified.
     * <p>
     * Spec: REC2c1
     */
    @Test
    public void hosts_fallback_when_port_is_defined() {
        // Given
        options.port = 8080;
        options.tlsPort = 8081;

        // When
        Hosts hosts = new Hosts(options);

        // Then
        assertThat(collectFallbackHosts(hosts), containsInAnyOrder(Defaults.HOST_FALLBACKS));
    }

    private List<String> collectFallbackHosts(Hosts hosts) {
        List<String> fallbackHosts = new ArrayList<>();
        String host = hosts.getPrimaryHost();
        while ((host = hosts.getFallback(host)) != null){
            fallbackHosts.add(host);
        }
        return fallbackHosts;
    }
}
