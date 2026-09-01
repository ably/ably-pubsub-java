package io.ably.lib.types;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import org.junit.Test;

public class ClientOptionsTest {

    private final ClientOptions clientOptions = new ClientOptions();

    @Test
    public void should_support_idempotent_rest_publishing() {
        // Then
        assertTrue(clientOptions.idempotentRestPublishing);
    }

    @Test
    public void copy_carries_headers_fallbackHosts_transportParams_and_agents() {
        // Given
        clientOptions.headers = new HashMap<>();
        clientOptions.headers.put("X-Custom", "value");
        clientOptions.fallbackHosts = new String[]{"a.example.com", "b.example.com"};
        clientOptions.transportParams = new Param[]{new Param("remainPresentFor", "1000")};
        clientOptions.agents = new HashMap<>();
        clientOptions.agents.put("some-sdk", "1.2.3");

        // When
        ClientOptions copied = clientOptions.copy();

        // Then
        assertSame(clientOptions.headers, copied.headers);
        assertArrayEquals(clientOptions.fallbackHosts, copied.fallbackHosts);
        assertSame(clientOptions.transportParams, copied.transportParams);
        assertSame(clientOptions.agents, copied.agents);
    }
}
