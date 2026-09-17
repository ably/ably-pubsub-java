package io.ably.pubsub.transport;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class HostsTest {

    private final ClientOptions options = new ClientOptions();

    /**
     * Tests for Hosts class (fallback hosts).
     */
    @Test
    public void hosts_fallback() throws AblyException {
        // When
        Hosts hosts = new Hosts(Defaults.HOST_REALTIME, Defaults.HOST_REALTIME, options);

        // Then
        List<String> fallbackHosts = collectFallbackHosts(hosts);
        assertThat(hosts.getPrimaryHost(), is(Defaults.HOST_REALTIME));
        // the returned fallback hosts should have the same elements as default host fallbacks
        assertThat(fallbackHosts, containsInAnyOrder(Defaults.HOST_FALLBACKS));
        // expect the fallback hosts to be shuffled
        assertThat(fallbackHosts, not(contains(Defaults.HOST_FALLBACKS)));
    }

    /**
     * Expect an exception when setting both realtimeHost and environment.
     */
    @Test(expected = AblyException.class)
    public void hosts_host_and_environment() throws AblyException {
        // Given
        options.environment = "myenv";

        // When
        new Hosts("overridden.ably.io", Defaults.HOST_REALTIME, options);
    }

    /**
     * Expect a null, when we provide empty array of fallback hosts
     */
    @Test
    public void hosts_fallback_empty_array() throws AblyException {
        // Given
        options.fallbackHosts = new String[] {};

        // When
        Hosts hosts = new Hosts(Defaults.HOST_REALTIME, Defaults.HOST_REALTIME, options);

        // Then
        assertThat(hosts.getFallback(Defaults.HOST_REALTIME), nullValue());
    }

    /**
     * Expect that returned host is contained within custom host list in options,
     * but in shuffled order and the right number of them.
     */
    @Test
    public void hosts_fallback_custom_hosts() throws AblyException {
        // Given
        String[] customHosts = { "F.ably-realtime.com", "G.ably-realtime.com", "H.ably-realtime.com", "I.ably-realtime.com", "J.ably-realtime.com", "K.ably-realtime.com" };
        options.fallbackHosts = customHosts;

        // When
        Hosts hosts = new Hosts(Defaults.HOST_REALTIME, Defaults.HOST_REALTIME, options);

        // Then
        List<String> fallbackHosts = collectFallbackHosts(hosts);
        assertThat(hosts.getPrimaryHost(), is(Defaults.HOST_REALTIME));
        // the returned fallback hosts should have the same elements as custom host fallbacks
        assertThat(fallbackHosts, containsInAnyOrder(customHosts));
        // expect the fallback hosts to be shuffled
        assertThat(fallbackHosts, not(contains(customHosts)));
    }

    /**
     * Expect that returned host is contained within default host list
     */
    @Test
    public void hosts_fallback_no_custom_hosts() throws AblyException {
        // When
        Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, options);

        // Then
        List<String> fallbackHosts = collectFallbackHosts(hosts);
        assertThat(hosts.getPrimaryHost(), is(Defaults.HOST_REALTIME));
        // the returned fallback hosts should have the same elements as default host fallbacks
        assertThat(fallbackHosts, containsInAnyOrder(Defaults.HOST_FALLBACKS));
        // expect the fallback hosts to be shuffled
        assertThat(fallbackHosts, not(contains(Defaults.HOST_FALLBACKS)));
    }

    /**
     * Expect a null, when realtimeHost is non-default
     */
    @Test
    public void hosts_fallback_overridden_host() throws AblyException {
        // Given
        String host = "overridden.ably.io";

        // When
        Hosts hosts = new Hosts(host, Defaults.HOST_REALTIME, options);

        // Then
        assertThat(hosts.getFallback(host), nullValue());
    }

    /**
     * Expect that returned host is contained within default host list
     * when realtimeHost is non-default and fallbackHostsUseDefault is set
     */
    @Test
    public void hosts_fallback_use_default() throws AblyException {
        // Given
        options.fallbackHostsUseDefault = true;
        String host = "overridden.ably.io";

        // When
        Hosts hosts = new Hosts(host, Defaults.HOST_REALTIME, options);

        // Then
        assertThat(hosts.getPrimaryHost(), is(host));
        // the returned fallback hosts should have the same elements as default host fallbacks
        assertThat(collectFallbackHosts(hosts), containsInAnyOrder(Defaults.HOST_FALLBACKS));
    }

    /**
     * It is not allowed to use default fallback hosts and at the same time
     * provide custom fallback hosts.
     */
    @Test(expected = AblyException.class)
    public void hosts_fallback_use_default_and_set_fallback_hosts() throws AblyException {
        // Given
        options.fallbackHostsUseDefault = true;
        options.fallbackHosts = new String[] { "custom.ably-realtime.com" };

        // When
        new Hosts(null, Defaults.HOST_REALTIME, options);
    }

    /**
     * Expect that returned fallback hosts containing the environment information.
     */
    @Test
    public void hosts_fallback_use_environment() throws AblyException {
        // Given
        options.environment = "sandbox";
        String[] expectedEnvironmentFallbackHosts = Defaults.getEnvironmentFallbackHosts(options.environment);

        // When
        Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, options);

        // Then
        assertThat(hosts.getPrimaryHost(), is("sandbox-" + Defaults.HOST_REALTIME));
        assertThat(collectFallbackHosts(hosts), containsInAnyOrder(expectedEnvironmentFallbackHosts));
    }

    /**
     * Expect that returned default fallback hosts without environment according to RSC15g4.
     */
    @Test
    public void hosts_fallback_use_default_fallback_hosts_and_environment() throws AblyException {
        // Given
        options.fallbackHostsUseDefault = true;
        options.environment = "sandbox";

        // When
        Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, options);

        // Then
        assertThat(hosts.getPrimaryHost(), is("sandbox-" + Defaults.HOST_REALTIME));
        assertThat(collectFallbackHosts(hosts), containsInAnyOrder(Defaults.HOST_FALLBACKS));
    }

    /**
     * Expect no fallback hosts if the custom port is specified.
     */
    @Test
    public void hosts_no_fallback_when_port_is_defined() throws AblyException {
        // Given
        options.port = 8080;

        // When
        Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, options);

        // Then
        assertThat(hosts.getFallback(Defaults.HOST_REALTIME), nullValue());
    }

    /**
     * Expect no fallback hosts if the custom TLS port is specified.
     */
    @Test
    public void hosts_no_fallback_when_tlsport_is_defined() throws AblyException {
        // Given
        options.tlsPort = 8081;

        // When
        Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, options);

        // Then
        assertThat(hosts.getFallback(Defaults.HOST_REALTIME), nullValue());
    }

    /**
     * It should return fallback hosts when custom fallbacks and port are provided.
     */
    @Test
    public void hosts_fallback_when_fallback_hosts_and_port_are_defined() throws AblyException {
        // Given
        options.port = 8081;
        options.fallbackHosts = new String[] { "custom-fallback.ably.com" };

        // When
        Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, options);

        // Then
        assertThat(hosts.getFallback(Defaults.HOST_REALTIME), is("custom-fallback.ably.com"));
    }

    /**
     * It should return fallback hosts when host, custom fallbacks, and port are provided.
     */
    @Test
    public void hosts_fallback_when_host_and_fallback_hosts_and_port_are_defined() throws AblyException {
        // Given
        options.tlsPort = 8081;
        options.fallbackHosts = new String[] { "custom-fallback.ably.com" };

        // When
        Hosts hosts = new Hosts("custom.ably.com", Defaults.HOST_REALTIME, options);

        // Then
        assertThat(hosts.getFallback("custom.ably.com"), is("custom-fallback.ably.com"));
    }

    @Test(expected = AblyException.class)
    public void hosts_use_default_fallback_hosts_and_tlsport_are_defined() throws AblyException {
        // Given
        options.tlsPort = 8081;
        options.fallbackHostsUseDefault = true;

        // When
        new Hosts(null, Defaults.HOST_REALTIME, options);
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
