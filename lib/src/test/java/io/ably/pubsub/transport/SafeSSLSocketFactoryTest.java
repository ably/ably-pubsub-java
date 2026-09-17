package io.ably.pubsub.transport;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SafeSSLSocketFactoryTest {
    SafeSSLSocketFactory safeSSLSocketFactory;

    @Before
    public void setup() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);
        safeSSLSocketFactory = new SafeSSLSocketFactory(sslContext.getSocketFactory());
    }

    @Test
    public void should_not_use_unsafe_tls_protocols() throws IOException {
        // given
        Set<String> unsafeProtocols = new HashSet<>(Arrays.asList(
            "SSLv3",
            "TLSv1",
            "TLSv1.1"
        ));

        // when
        SSLSocket sslSocket = (SSLSocket) safeSSLSocketFactory.createSocket();

        // then
        for (String enabledProtocol : sslSocket.getEnabledProtocols()) {
            Assert.assertFalse(
                "Protocol " + enabledProtocol + " is unsafe and should not be enabled",
                unsafeProtocols.contains(enabledProtocol)
            );

        }
    }

    @Test
    public void should_use_at_least_one_safe_tls_protocol() throws IOException {
        // given
        Set<String> safeProtocols = new HashSet<>(Arrays.asList(
            "TLSv1.2",
            "TLSv1.3"
        ));

        // when
        SSLSocket sslSocket = (SSLSocket) safeSSLSocketFactory.createSocket();

        // then
        boolean isUsingSafeProtocol = containsAnySafeProtocol(sslSocket.getEnabledProtocols(), safeProtocols);
        Assert.assertTrue("No safe protocols are enabled", isUsingSafeProtocol);
    }

    private boolean containsAnySafeProtocol(String[] protocols, Set<String> safeProtocols) {
        for (String protocol : protocols) {
            if (safeProtocols.contains(protocol)) {
                return true;
            }
        }
        return false;
    }
}
