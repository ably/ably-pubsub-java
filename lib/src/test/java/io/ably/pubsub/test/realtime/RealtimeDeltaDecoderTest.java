package io.ably.pubsub.test.realtime;

import com.google.gson.JsonObject;
import io.ably.pubsub.debug.DebugOptions;
import io.ably.pubsub.realtime.RealtimeClient;
import io.ably.pubsub.realtime.Channel;
import io.ably.pubsub.realtime.ChannelState;
import io.ably.pubsub.test.common.Helpers.ChannelWaiter;
import io.ably.pubsub.test.common.Helpers.MessageWaiter;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.transport.ConnectionManager;
import io.ably.pubsub.transport.ITransport;
import io.ably.pubsub.transport.WebSocketTransport;
import io.ably.pubsub.types.ChannelOptions;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.Message;
import io.ably.pubsub.types.MessageExtras;
import io.ably.pubsub.types.ProtocolMessage;
import io.ably.pubsub.util.Base64Coder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.Collections;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class RealtimeDeltaDecoderTest extends ParameterizedTest {
    private static final String[] testData = new String[] {
        "{ foo: \"bar\", count: 1, status: \"active\" }",
        "{ foo: \"bar\", count: 2, status: \"active\" }",
        "{ foo: \"bar\", count: 2, status: \"inactive\" }",
        "{ foo: \"bar\", count: 3, status: \"inactive\" }",
        "{ foo: \"bar\", count: 3, status: \"active\" }"
    };

    @Rule
    public Timeout testTimeout = Timeout.seconds(300);

    @Test
    public void simple_delta_codec() {
        RealtimeClient ably = null;
        String testName = "simple_delta_codec";
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);

            ably = new RealtimeClient(opts);
            Channel channel = ably.channels.get("[?delta=vcdiff]" + testName);

            /* subscribe */
            MessageWaiter messageWaiter = new MessageWaiter(channel);

            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);

            assertEquals("Verify channel params", Collections.singletonMap("delta", "vcdiff"), channel.getParams());

            for (int i = 0; i < testData.length; i++) {
                channel.publish(Integer.toString(i), testData[i]);
            }

            messageWaiter.waitFor(testData.length);
            assertEquals("Verify number of received messages", testData.length, messageWaiter.receivedMessages.size());
            for (int i = 0; i < messageWaiter.receivedMessages.size(); i++) {
                Message message = messageWaiter.receivedMessages.get(i);
                int messageIndex = Integer.parseInt(message.name);
                assertEquals("Verify message order", i, messageIndex);
                assertEquals("Verify message data", testData[messageIndex], message.data);
            }
        } catch(Exception e) {
            fail(testName + ": Unexpected exception " + e.getMessage());
            e.printStackTrace();
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    @Test
    public void delta_out_of_order_failure_recovery() {
        delta_failure_recovery(new OutOfOrderDeltasWebsocketFactory(), "delta_out_of_order_failure_recovery");
    }

    @Test
    public void delta_decode_failure_recovery() {
        delta_failure_recovery(new FailingDeltasWebsocketFactory(), "delta_decode_failure_recovery");
    }

    private void delta_failure_recovery(final ITransport.Factory websocketFactory, String testName) {
        RealtimeClient ably = null;
        try {
            DebugOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.transportFactory = websocketFactory;
            ably = new RealtimeClient(opts);

            ChannelOptions options = new ChannelOptions();
            options.params = Collections.singletonMap("delta", "vcdiff");
            /* create a channel with channelOptions set to vcdiff*/
            final Channel channel = ably.channels.get(testName, options);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            assertEquals("Verify channel params", Collections.singletonMap("delta", "vcdiff"), channel.getParams());

            /* subscribe */
            MessageWaiter messageWaiter = new MessageWaiter(channel);

            /* publish to the channel */
            for (int i = 0; i < testData.length; i++) {
                channel.publish(Integer.toString(i), testData[i]);
            }

            /* wait for the messages */
            messageWaiter.waitFor(testData.length);
            for (Message message: messageWaiter.receivedMessages) {
                assertEquals("Verify message data", testData[Integer.parseInt(message.name)], message.data);
            }

        } catch(Exception e) {
            fail(testName + ": Unexpected exception " + e.getMessage());
            e.printStackTrace();
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    public static class OutOfOrderDeltasWebsocketFactory implements ITransport.Factory {
        @Override
        public ITransport getTransport(ITransport.TransportParams transportParams, ConnectionManager connectionManager) {
            return new OutOfOrderDeltasWebsocketTransportMock(transportParams, connectionManager);
        }
    }

    /*
     * Special transport class that corrupts the order bookkeeping of delta messages to allow testing delta recovery.
     */
    private static class OutOfOrderDeltasWebsocketTransportMock extends WebSocketTransport {
        private static final String DELTA = "delta";

        private OutOfOrderDeltasWebsocketTransportMock(TransportParams transportParams, ConnectionManager connectionManager) {
            super(transportParams, connectionManager);
        }

        @Override
        protected void preProcessReceivedMessage(ProtocolMessage protocolMessage) {
            if(protocolMessage.action == ProtocolMessage.Action.message) {
                for (final Message message : protocolMessage.messages) {
                    final MessageExtras extras = message.extras;
                    if (extras != null) {
                        final JsonObject json = message.extras.asJsonObject();

                        if (json.has(DELTA)) {
                            // This MessageExtras (json) has DeltaExtras.
                            // Corrupt it by replacing the value at the from key with an empty string.
                            json.getAsJsonObject(DELTA).addProperty("from", "");
                        }
                    }
                }
            }
        }
    }

    public static class FailingDeltasWebsocketFactory implements ITransport.Factory {
        @Override
        public ITransport getTransport(ITransport.TransportParams transportParams, ConnectionManager connectionManager) {
            return new FailingDeltasWebsocketTransportMock(transportParams, connectionManager);
        }
    }

    /*
     * Special transport class that mangles delta messages to allow testing delta recovery
     */
    private static class FailingDeltasWebsocketTransportMock extends WebSocketTransport {


        private FailingDeltasWebsocketTransportMock(TransportParams transportParams, ConnectionManager connectionManager) {
            super(transportParams, connectionManager);
        }

        @Override
        protected void preProcessReceivedMessage(ProtocolMessage message) {
            if(message.action == ProtocolMessage.Action.message &&
                message.messages[0].extras != null &&
                message.messages[0].extras.getDelta() != null &&
                Objects.equals(message.messages[0].extras.getDelta().getFormat(), "vcdiff")) {

                if(message.messages[0].data instanceof String) {
                    byte[] delta = Base64Coder.decode((String)message.messages[0].data);
                    delta[0] = 0;
                    message.messages[0].data = Base64Coder.encodeToString(delta);
                }
                else
                    ((byte[])message.messages[0].data)[0] = 0;
            }
        }
    }
}
