package io.ably.pubsub.types;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RegistrationTokenTypeTest {

    @Test
    public void fromNameParseCorrectly() {
        assertEquals(RegistrationToken.Type.FCM, RegistrationToken.Type.fromName("FCM"));
        assertEquals(RegistrationToken.Type.FCM, RegistrationToken.Type.fromName("fCM"));
        assertEquals(RegistrationToken.Type.FCM, RegistrationToken.Type.fromName("fcM"));
        assertEquals(RegistrationToken.Type.FCM, RegistrationToken.Type.fromName("fcm"));
        assertEquals(RegistrationToken.Type.FCM, RegistrationToken.Type.fromName("FcM"));
        assertEquals(RegistrationToken.Type.FCM, RegistrationToken.Type.fromName("Fcm"));
        assertEquals(RegistrationToken.Type.FCM, RegistrationToken.Type.fromName("FCm"));
        assertEquals(RegistrationToken.Type.FCM, RegistrationToken.Type.fromName("fCm"));
    }

    @Test
    public void fromNameParseFailure() {
        assertNull(RegistrationToken.Type.fromName("FCM "));
        assertNull(RegistrationToken.Type.fromName(" FCM "));
        assertNull(RegistrationToken.Type.fromName("\tFCM "));
        assertNull(RegistrationToken.Type.fromName("FĆM"));
        assertNull(RegistrationToken.Type.fromName("FĆM\t"));
        assertNull(RegistrationToken.Type.fromName("FCM\\"));
        assertNull(RegistrationToken.Type.fromName("FĆM\'"));
        assertNull(RegistrationToken.Type.fromName("FĆM\""));
        assertNull(RegistrationToken.Type.fromName("\nFCM"));
        assertNull(RegistrationToken.Type.fromName("GCM"));
        assertNull(RegistrationToken.Type.fromName("gcm"));
        assertNull(RegistrationToken.Type.fromName("GÇM"));
        assertNull(RegistrationToken.Type.fromName("\\GCM"));
        assertNull(RegistrationToken.Type.fromName("\'GCM"));
        assertNull(RegistrationToken.Type.fromName("    GCM"));
        assertNull(RegistrationToken.Type.fromName("\"GCM"));
        assertNull(RegistrationToken.Type.fromName("GÇM\r"));
        assertNull(RegistrationToken.Type.fromName("GÇM\f"));
        assertNull(RegistrationToken.Type.fromName("GÇM\n"));
        assertNull(RegistrationToken.Type.fromName(null));
    }

    @Test
    public void toNameProducesNameCorrectly() {
        assertEquals("fcm", new RegistrationToken(RegistrationToken.Type.FCM, "token").type.toName());
    }

}
