package io.ably.lib.transport;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.util.MockWebsocketFactory;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class ConnectionManagerClosedErrorTest {

    /**
     * Closing the connection reports Ably code 80017 with HTTP status code 400, both in the
     * state change and when attaching a channel afterwards.
     */
    @Test
    public void closed_connection_error_codes() throws AblyException {
        DebugOptions options = new DebugOptions("appid.keyid:keysecret");
        options.autoConnect = false;
        MockWebsocketFactory mockTransport = new MockWebsocketFactory();
        mockTransport.failConnect();
        options.transportFactory = mockTransport;

        try (AblyRealtime ably = new AblyRealtime(options)) {
            Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ably.connection);
            ably.connection.connect();
            assertThat(connectionWaiter.waitFor(ConnectionState.disconnected, 1, 10000), is(true));
            ably.connection.close();
            assertThat(connectionWaiter.waitFor(ConnectionState.closed, 1, 10000), is(true));

            ErrorInfo closedReason = connectionWaiter.lastStateChange().reason;
            assertThat(closedReason, is(notNullValue()));
            assertThat(closedReason.code, is(80017));
            assertThat(closedReason.statusCode, is(400));

            AtomicReference<ErrorInfo> attachError = new AtomicReference<>();
            ably.channels.get("closed_connection_attach").attach(new CompletionListener() {
                @Override
                public void onSuccess() {}

                @Override
                public void onError(ErrorInfo reason) {
                    attachError.set(reason);
                }
            });
            assertThat(attachError.get(), is(notNullValue()));
            assertThat(attachError.get().code, is(80017));
            assertThat(attachError.get().statusCode, is(400));
        }
    }
}
