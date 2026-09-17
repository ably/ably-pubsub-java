package io.ably.pubsub.transport;

import io.ably.pubsub.network.WebSocketClient;
import io.ably.pubsub.network.WebSocketEngine;
import io.ably.pubsub.network.WebSocketListener;
import io.ably.pubsub.test.common.Helpers;
import io.ably.pubsub.test.util.EmptyPlatformAgentProvider;
import io.ably.pubsub.transport.ITransport.TransportParams;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.Param;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WebSocketTransport, specifically testing activity timer behavior
 * when WebSocket close operations get stuck or fail to trigger onClose handlers.
 */
public class WebSocketTransportTest {

    private ConnectionManager mockConnectionManager;

    private WebSocketEngine mockEngine;

    private WebSocketTransport transport;

    private WebSocketClient mockWebSocketClient;

    private TransportParams transportParams;

    @Before
    public void setUp() throws Exception {
        mockConnectionManager = mock(ConnectionManager.class);
        mockEngine = mock(WebSocketEngine.class);
        mockWebSocketClient = mock(WebSocketClient.class);
        when(mockEngine.isPingListenerSupported()).thenReturn(true);
        when(mockEngine.create(any(), any())).thenReturn(mockWebSocketClient);
        when(mockConnectionManager.getAuthParams()).thenReturn(new Param[]{});

        mockConnectionManager.maxIdleInterval = 10;

        // Setup transport params
        transportParams = new TransportParams(new ClientOptions(), new EmptyPlatformAgentProvider());
        transportParams.host = "realtime.ably.io";
        transportParams.port = 443;
        transportParams.options.realtimeRequestTimeout = 10;
    }

    private WebSocketTransport createWebSocketTransport() {
        WebSocketTransport transport = new WebSocketTransport(transportParams, mockConnectionManager);
        Helpers.setPrivateField(transport, "webSocketEngine", mockEngine);
        return transport;
    }

    @Test
    public void throwExceptionsIfConnectCalledTwice() {
        final WebSocketTransport transport = createWebSocketTransport();
        ITransport.ConnectListener connectListener = mock(ITransport.ConnectListener.class);
        transport.connect(connectListener);
        assertThrows(IllegalStateException.class, () ->
                transport.connect(connectListener)
        );
    }

    @Test
    public void shouldCallCancelIfNotClosedGracefully() {
        AtomicReference<WebSocketListener> webSocketListenerRef = new AtomicReference<>();

        when(mockEngine.create(any(), any())).thenAnswer(invocation -> {
            webSocketListenerRef.set(invocation.getArgumentAt(1, WebSocketListener.class));
            return mockWebSocketClient;
        });

        doAnswer(invocation -> {
            webSocketListenerRef.get().onClose(
                    invocation.getArgumentAt(0, Integer.class),
                    invocation.getArgumentAt(1, String.class)
            );
            return null;
        }).when(mockWebSocketClient).cancel(anyInt(), anyString());

        final WebSocketTransport transport = createWebSocketTransport();
        ITransport.ConnectListener connectListener = mock(ITransport.ConnectListener.class);
        transport.connect(connectListener);
        transport.close();
        // check that we tried to close gracefully
        verify(mockWebSocketClient).close();
        // check that we closed forcibly at the end
        verify(mockWebSocketClient, timeout(1_000)).cancel(eq(1006), anyString());
        // verify that we call listener at the end
        verify(connectListener).onTransportUnavailable(eq(transport), any());
    }

    /**
     * `onClose` can be called twice, e.g. from activity timer force close and from manual `close()`
     * It shouldn't result in any exceptions
     */
    @Test
    public void shouldNotThrowExceptionIfSeveralCloseEventsHappened() {
        AtomicReference<WebSocketListener> listenerRef = new AtomicReference<>();

        when(mockEngine.create(any(), any())).thenAnswer(invocation -> {
            listenerRef.set(invocation.getArgumentAt(1, WebSocketListener.class));
            return mockWebSocketClient;
        });


        final WebSocketTransport transport = createWebSocketTransport();
        ITransport.ConnectListener connectListener = mock(ITransport.ConnectListener.class);
        transport.connect(connectListener);

        listenerRef.get().onClose(1000, "OK");
        listenerRef.get().onClose(1006, "Abnormal close");

        verify(connectListener, times(2)).onTransportUnavailable(eq(transport), any());
    }

    /**
     * Calling `close()` on transport triggers the activity timer.
     * Test checks that if it has been disposed it won't do anything.
     */
    @Test
    public void shouldNotThrowExceptionIfCloseCalledOnAlreadyClosedTransport() {
        AtomicReference<WebSocketListener> listenerRef = new AtomicReference<>();

        when(mockEngine.create(any(), any())).thenAnswer(invocation -> {
            listenerRef.set(invocation.getArgumentAt(1, WebSocketListener.class));
            return mockWebSocketClient;
        });


        final WebSocketTransport transport = createWebSocketTransport();
        ITransport.ConnectListener connectListener = mock(ITransport.ConnectListener.class);
        transport.connect(connectListener);

        listenerRef.get().onClose(1006, "Abnormal close");
        transport.close();
        verify(connectListener, timeout(1_000)).onTransportUnavailable(eq(transport), any());
    }
}
