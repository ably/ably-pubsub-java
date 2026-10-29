package io.ably.pubsub.push;

import io.ably.pubsub.types.RegistrationToken;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class LocalDeviceTokenTypeMigrationTest {
    private static final String TOKEN_TYPE = "ABLY_REGISTRATION_TOKEN_TYPE";
    private static final String TOKEN = "ABLY_REGISTRATION_TOKEN";

    /** Mimics SharedPreferences: reading a key with the wrong type throws ClassCastException. */
    private static class FakeStorage implements Storage {
        final Map<String, Object> map = new HashMap<>();

        @Override
        public void put(String key, String value) {
            map.put(key, value);
        }

        @Override
        public void put(String key, int value) {
            map.put(key, value);
        }

        @Override
        public String get(String key, String defaultValue) {
            Object v = map.get(key);
            return v != null ? (String) v : defaultValue;
        }

        @Override
        public int get(String key, int defaultValue) {
            Object v = map.get(key);
            return v != null ? (Integer) v : defaultValue;
        }

        @Override
        public void clear(String[] keys) {
            for (String key : keys) {
                map.remove(key);
            }
        }
    }

    @Test
    public void nameRoundTrip() {
        FakeStorage storage = new FakeStorage();
        storage.put(TOKEN_TYPE, RegistrationToken.Type.FCM.toName());
        assertEquals(RegistrationToken.Type.FCM, LocalDevice.readPersistedTokenType(storage));
    }

    @Test
    public void nothingPersisted() {
        assertNull(LocalDevice.readPersistedTokenType(new FakeStorage()));
    }

    @Test
    public void legacyFcmOrdinalIsKeptAndRewrittenByName() {
        FakeStorage storage = new FakeStorage();
        storage.put(TOKEN_TYPE, 1);
        storage.put(TOKEN, "t");
        assertEquals(RegistrationToken.Type.FCM, LocalDevice.readPersistedTokenType(storage));
        assertEquals("fcm", storage.map.get(TOKEN_TYPE));
        assertEquals("t", storage.map.get(TOKEN));
    }

    @Test
    public void legacyGcmOrdinalIsDropped() {
        FakeStorage storage = new FakeStorage();
        storage.put(TOKEN_TYPE, 0);
        storage.put(TOKEN, "t");
        assertNull(LocalDevice.readPersistedTokenType(storage));
        assertFalse(storage.map.containsKey(TOKEN_TYPE));
        assertFalse(storage.map.containsKey(TOKEN));
    }

    @Test
    public void garbageIsDropped() {
        FakeStorage storage = new FakeStorage();
        storage.put(TOKEN_TYPE, 7);
        storage.put(TOKEN, "t");
        assertNull(LocalDevice.readPersistedTokenType(storage));
        assertFalse(storage.map.containsKey(TOKEN));

        storage.put(TOKEN_TYPE, "gcm");
        storage.put(TOKEN, "t");
        assertNull(LocalDevice.readPersistedTokenType(storage));
        assertFalse(storage.map.containsKey(TOKEN_TYPE));
        assertFalse(storage.map.containsKey(TOKEN));
    }
}
