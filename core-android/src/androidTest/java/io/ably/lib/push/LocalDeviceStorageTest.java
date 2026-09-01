package io.ably.lib.push;

import android.content.Context;
import android.support.test.runner.AndroidJUnit4;
import io.ably.lib.types.RegistrationToken;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.HashMap;

import static android.support.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class LocalDeviceStorageTest {
    private static Context context;
    private static ActivationContext activationContext;


    private HashMap<String, Object> hashMap = new HashMap<>();

    private Storage inMemoryStorage = new Storage() {
        @Override
        public void put(String key, String value) {
            hashMap.put(key, value);
        }

        @Override
        public void put(String key, int value) {
            hashMap.put(key, value);
        }

        @Override
        public String get(String key, String defaultValue) {
            Object value = hashMap.get(key);
            return value != null ? (String) value : defaultValue;
        }

        @Override
        public int get(String key, int defaultValue) {
            Object value = hashMap.get(key);
            return value != null ? (int) value : defaultValue;
        }

        @Override
        public void clear(String[] keys) {
            hashMap = new HashMap<>();
        }
    };

    @BeforeClass
    public static void setUp() {
        context = getContext();
        activationContext = new ActivationContext(context.getApplicationContext());
    }

    @Test
    public void shared_preferences_storage_used_by_default() {
        LocalDevice localDevice = new LocalDevice(activationContext, null);
        /* initialize properties in storage */
        localDevice.create();

        /* verify custom storage is not used */
        assertTrue(hashMap.isEmpty());

        /* load properties */
        assertNotNull(localDevice.id);
        assertNotNull(localDevice.deviceSecret);
    }

    @Test
    public void shared_preferences_storage_works_correctly() {
        LocalDevice localDevice = new LocalDevice(activationContext, null);

        RegistrationToken registrationToken= new RegistrationToken(RegistrationToken.Type.FCM, "ABLY");
        /* initialize properties in storage */
        localDevice.create();
        localDevice.setAndPersistRegistrationToken(registrationToken);

        /* verify custom storage is not used */
        assertTrue(hashMap.isEmpty());

        /* load properties */
        assertNotNull(localDevice.id);
        assertNotNull(localDevice.deviceSecret);
        assertTrue(localDevice.isCreated());
        assertEquals("FCM", localDevice.getRegistrationToken().type.name());
        assertEquals("ABLY", localDevice.getRegistrationToken().token);

        /* reset all properties */
        localDevice.reset();

        /* properties were cleared */
        assertNull(localDevice.id);
        assertNull(localDevice.deviceSecret);
        assertNull(localDevice.getRegistrationToken());
    }

    @Test
    public void custom_storage_used_if_provided() {
        LocalDevice localDevice = new LocalDevice(activationContext, inMemoryStorage);
        /* initialize properties in storage */
        localDevice.create();

        /* verify in memory storage is used */
        assertFalse(hashMap.isEmpty());

        /* load properties */
        assertNotNull(localDevice.id);
        assertNotNull(localDevice.deviceSecret);

        String deviceId = localDevice.id;
        String deviceSecret = localDevice.deviceSecret;

        /* values are the same */
        assertEquals(deviceId, hashMap.get("ABLY_DEVICE_ID"));
        assertEquals(deviceSecret, hashMap.get("ABLY_DEVICE_SECRET"));
    }

    @Test
    public void custom_storage_works_correctly() {
        LocalDevice localDevice = new LocalDevice(activationContext, inMemoryStorage);

        RegistrationToken registrationToken= new RegistrationToken(RegistrationToken.Type.FCM, "ABLY");
        /* initialize properties in storage */
        localDevice.create();
        localDevice.setAndPersistRegistrationToken(registrationToken);

        /* verify custom storage is used */
        assertFalse(hashMap.isEmpty());

        /* load properties */
        assertNotNull(localDevice.id);
        assertNotNull(localDevice.deviceSecret);
        assertTrue(localDevice.isCreated());
        assertEquals("FCM", localDevice.getRegistrationToken().type.name());
        assertEquals("ABLY", localDevice.getRegistrationToken().token);

        /* reset all properties */
        localDevice.reset();
        /* verify custom storage was cleared out */
        assertTrue(hashMap.isEmpty());

        /* properties were cleared */
        assertNull(localDevice.id);
        assertNull(localDevice.deviceSecret);
        assertNull(localDevice.getRegistrationToken());
    }
}
