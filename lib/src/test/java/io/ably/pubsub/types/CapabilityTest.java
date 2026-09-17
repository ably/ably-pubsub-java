package io.ably.pubsub.types;

import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CapabilityTest {

    @Test
    public void c14n_sendNull_returnsNull() throws AblyException {
        String returnedValue = Capability.c14n(null);

        assertNull(returnedValue);
    }

    @Test
    public void c14n_sendEmptyString_returnsEmptyString() throws AblyException {
        String returnedValue = Capability.c14n("");

        assertNull(returnedValue);
    }
}
