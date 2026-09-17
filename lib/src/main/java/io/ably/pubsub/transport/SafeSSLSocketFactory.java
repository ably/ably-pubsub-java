package io.ably.pubsub.transport;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This is a decorator for the {@link SSLSocketFactory} which modifies the enabled TLS protocols
 * for each created {@link SSLSocket} to only use the protocols which are considered to be safe.
 * <p>
 * This class was created because the {@code SSLContext.getInstance()} method does not allow specifying
 * precisely which TLS protocols can be used and which cannot.
 */
public class SafeSSLSocketFactory extends SSLSocketFactory {
    /**
     * The protocols that are considered to be safe.
     */
    private final String[] SAFE_PROTOCOLS = {
        "TLSv1.2",
        "TLSv1.3"
    };

    /**
     * All API calls should be delegated to this factory instance.
     */
    private final SSLSocketFactory factory;

    public SafeSSLSocketFactory(SSLSocketFactory factory) {
        this.factory = factory;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return factory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return factory.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return getSocketWithOnlySafeProtocolsEnabled(factory.createSocket());
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        return getSocketWithOnlySafeProtocolsEnabled(factory.createSocket(socket, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return getSocketWithOnlySafeProtocolsEnabled(factory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
        return getSocketWithOnlySafeProtocolsEnabled(factory.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return getSocketWithOnlySafeProtocolsEnabled(factory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return getSocketWithOnlySafeProtocolsEnabled(factory.createSocket(address, port, localAddress, localPort));
    }

    /**
     * Modifies the socket's enabled protocols list to only support the safe ones.
     * If no safe protocol is supported then the socket won't have any protocols enabled.
     */
    private Socket getSocketWithOnlySafeProtocolsEnabled(Socket socket) {
        if (!(socket instanceof SSLSocket)) {
            throw new IllegalArgumentException("The socket is not an instance of the SSL socket");
        }
        SSLSocket sslSocket = (SSLSocket) socket;
        Set<String> allSupportedProtocols = new HashSet<>(Arrays.asList(sslSocket.getSupportedProtocols()));
        List<String> safeSupportedProtocols = new ArrayList<>();
        for (String safeProtocol : SAFE_PROTOCOLS) {
            if (allSupportedProtocols.contains(safeProtocol)) {
                safeSupportedProtocols.add(safeProtocol);
            }
        }
        if (safeSupportedProtocols.isEmpty()) {
            throw new SecurityException("No safe protocol version is supported for this SSL socket");
        }
        sslSocket.setEnabledProtocols(safeSupportedProtocols.toArray(new String[0]));
        return sslSocket;
    }
}
