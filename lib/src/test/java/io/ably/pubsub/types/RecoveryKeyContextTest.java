package io.ably.pubsub.types;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RecoveryKeyContextTest {

    /**
     * Spec: RTN16i, RTN16f, RTN16j
     */
    @Test
    public void should_encode_recovery_key_context_object() {
        String expectedRecoveryKey =
            "{\"connectionKey\":\"uniqueKey\",\"msgSerial\":1,\"channelSerials\":{\"channel1\":\"1\",\"channel2\":\"2\",\"channel3\":\"3\"}}";
        Map<String, String> serials = new HashMap<>();
        serials.put("channel1", "1");
        serials.put("channel2", "2");
        serials.put("channel3", "3");
        RecoveryKeyContext recoveryKey = new RecoveryKeyContext("uniqueKey", 1, serials);
        String encodedRecoveryKey = recoveryKey.encode();
        assertEquals(expectedRecoveryKey, encodedRecoveryKey);
    }

    /**
     * Spec: RTN16i, RTN16f, RTN16j
     */
    @Test
    public void should_decode_recoverykey_to_recoveryKeyContextObject() {
         String recoveryKey =
            "{\"connectionKey\":\"key2\",\"msgSerial\":5,\"channelSerials\":{\"channel1\":\"98\",\"channel2\":\"32\",\"channel3\":\"09\"}}";
        RecoveryKeyContext recoveryKeyContext = RecoveryKeyContext.decode(recoveryKey);
        assertEquals("key2", recoveryKeyContext.getConnectionKey());
        assertEquals(5, recoveryKeyContext.getMsgSerial());
        Map<String, String> expectedChannelSerials = new HashMap<String, String>()
        {{
            put("channel1", "98");
            put("channel2", "32");
            put("channel3", "09");
        }};
        assertEquals(expectedChannelSerials, recoveryKeyContext.getChannelSerials());
    }

    /**
     * Spec: RTN16i, RTN16f, RTN16j
     */
    @Test
    public void should_return_null_recovery_context_while_decoding_faulty_recovery_key() {
        String recoveryKey =
            "{\"connectionKey\":\"key2\",\"msgSerial\":\"incorrectStringSerial\",\"channelSerials\":{\"channel1\":\"98\",\"channel2\":\"32\",\"channel3\":\"09\"}}";
        RecoveryKeyContext recoveryKeyContext = RecoveryKeyContext.decode(recoveryKey);
        assertNull(recoveryKeyContext);
    }

}
