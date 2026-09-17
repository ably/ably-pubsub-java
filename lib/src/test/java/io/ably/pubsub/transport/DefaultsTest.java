package io.ably.pubsub.transport;

import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class DefaultsTest {

    @Test
    public void protocol_version_CSV2() {
        assertThat(Defaults.ABLY_PROTOCOL_VERSION, is("6"));
    }

    @Test
    public void environmentFallbackHosts() {
        assertThat(Defaults.getEnvironmentFallbackHosts("sandbox"), is(new String[] {
            "sandbox-a-fallback.ably-realtime.com",
            "sandbox-b-fallback.ably-realtime.com",
            "sandbox-c-fallback.ably-realtime.com",
            "sandbox-d-fallback.ably-realtime.com",
            "sandbox-e-fallback.ably-realtime.com"
        }));
    }
}
