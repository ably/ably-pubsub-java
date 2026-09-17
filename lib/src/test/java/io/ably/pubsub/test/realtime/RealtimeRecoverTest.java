package io.ably.pubsub.test.realtime;

import io.ably.pubsub.debug.DebugOptions;
import io.ably.pubsub.realtime.RealtimeClient;
import io.ably.pubsub.realtime.Channel;
import io.ably.pubsub.realtime.ChannelState;
import io.ably.pubsub.realtime.ConnectionState;
import io.ably.pubsub.test.common.Helpers.ChannelWaiter;
import io.ably.pubsub.test.common.Helpers.CompletionSet;
import io.ably.pubsub.test.common.Helpers.ConnectionWaiter;
import io.ably.pubsub.test.common.Helpers.MessageWaiter;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.transport.ConnectionManager;
import io.ably.pubsub.transport.ITransport;
import io.ably.pubsub.transport.WebSocketTransport;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.ProtocolMessage;
import org.junit.Ignore;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RealtimeRecoverTest extends ParameterizedTest {

    private static final String TAG = RealtimeRecoverTest.class.getName();

    /**
     * Connect to the service using two library instances to set
     * up separate send and recv connections.
     * Send on one connection while the other is disconnected;
     * Open a third connection to inherit from the disconnected connection
     * and explicitly wait for the connection before re-attaching the channel.
     * verify that the messages sent whilst disconnected are delivered
     * on recover
     * Spec: RTN16a,RTN16b
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void recover_disconnected() {
        RealtimeClient ablyRx = null, ablyTx = null, ablyRxRecover = null;
        String channelName = "recover_disconnected";
        int messageCount = 5;
        long delay = 200;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ablyRx = new RealtimeClient(opts);
            ablyTx = new RealtimeClient(opts);

            /* create and attach channel to send on */
            final Channel channelTx = ablyTx.channels.get(channelName);
            channelTx.attach();
            (new ChannelWaiter(channelTx)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached for tx", channelTx.state, ChannelState.attached);

            /* create and attach channel to recv on */
            final Channel channelRx = ablyRx.channels.get(channelName);
            channelRx.attach();
            (new ChannelWaiter(channelRx)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached for rx", channelRx.state, ChannelState.attached);

            /* subscribe */
            MessageWaiter messageWaiter =  new MessageWaiter(channelRx);

            /* publish first messages to the channel */
            CompletionSet msgComplete1 = new CompletionSet();
            for(int i = 0; i < messageCount; i++) {
                channelTx.publish("test_event", "Test message (recover_disconnected) " + i, msgComplete1.add());
                try { Thread.sleep(delay); } catch(InterruptedException e){}
            }

            /* wait for the publish callback to be called */
            ErrorInfo[] errors = msgComplete1.waitFor();
            assertTrue("Verify success from all message callbacks", errors.length == 0);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called", messageWaiter.receivedMessages.size(), messageCount);

            /* disconnect the rx connection, without closing;
             * NOTE this depends on knowledge of the internal structure
             * of the library, to simulate a dropped transport without
             * causing the connection itself to be disposed */
            String recoverConnectionKey = ablyRx.connection.recoveryKey;
            ablyRx.connection.connectionManager.requestState(ConnectionState.failed);

            /* wait */
            try { Thread.sleep(2000L); } catch(InterruptedException e) {}

            /* publish next messages to the channel */
            CompletionSet msgComplete2 = new CompletionSet();
            for(int i = 0; i < messageCount; i++) {
                channelTx.publish("test_event", "Test message (recover_disconnected) " + i, msgComplete2.add());
                try { Thread.sleep(delay); } catch(InterruptedException e){}
            }

            /* wait for the publish callback to be called */
            errors = msgComplete2.waitFor();
            assertTrue("Verify success from all message callbacks", errors.length == 0);

            /* establish a new rx connection with recover string, and wait for connection */
            ClientOptions recoverOpts = createOptions(testVars.keys[0].keyStr);
            recoverOpts.recover = recoverConnectionKey;
            ablyRxRecover = new RealtimeClient(recoverOpts);
            (new ConnectionWaiter(ablyRxRecover.connection)).waitFor(ConnectionState.connected);

            /* subscribe to channel */
            final Channel channelRxRecover = ablyRxRecover.channels.get(channelName);
            MessageWaiter messageWaiterRecover =  new MessageWaiter(channelRxRecover);

            /* wait for the subscription callback to be called */
            messageWaiterRecover.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called after recovery", messageWaiterRecover.receivedMessages.size(), messageCount);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ablyTx != null)
                ablyTx.close();
            if(ablyRx != null)
                ablyRx.close();
            if(ablyRxRecover != null)
                ablyRxRecover.close();
        }
    }

    /**
     * Connect to the service using two library instances to set
     * up separate send and recv connections.
     * Send on one connection while the other is disconnected;
     * Open a third connection to inherit from the disconnected connection
     * without an explicit wait for connection.
     * verify that the messages sent whilst disconnected are delivered
     * on recover
     * Spec: RTN16a,RTN16b
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void recover_implicit_connect() {
        RealtimeClient ablyRx = null, ablyTx = null, ablyRxRecover = null;
        String channelName = "recover_implicit_connect";
        int messageCount = 5;
        long delay = 200;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ablyRx = new RealtimeClient(opts);
            ablyTx = new RealtimeClient(opts);

            /* create and attach channel to send on */
            final Channel channelTx = ablyTx.channels.get(channelName);
            channelTx.attach();
            (new ChannelWaiter(channelTx)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached for tx", channelTx.state, ChannelState.attached);

            /* create and attach channel to recv on */
            final Channel channelRx = ablyRx.channels.get(channelName);
            channelRx.attach();
            (new ChannelWaiter(channelRx)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached for rx", channelRx.state, ChannelState.attached);

            /* subscribe */
            MessageWaiter messageWaiter = new MessageWaiter(channelRx);

            /* publish first messages to the channel */
            CompletionSet msgComplete1 = new CompletionSet();
            for(int i = 0; i < messageCount; i++) {
                channelTx.publish("test_event", "Test message (recover_implicit_connect) " + i, msgComplete1.add());
                try { Thread.sleep(delay); } catch(InterruptedException e){}
            }

            /* wait for the publish callback to be called */
            ErrorInfo[] errors = msgComplete1.waitFor();
            assertTrue("Verify success from all message callbacks", errors.length == 0);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called", messageWaiter.receivedMessages.size(), messageCount);

            /* disconnect the rx connection, without closing;
             * NOTE this depends on knowledge of the internal structure
             * of the library, to simulate a dropped transport without
             * causing the connection itself to be disposed */
            String recoverConnectionKey = ablyRx.connection.recoveryKey;
            ablyRx.connection.connectionManager.requestState(ConnectionState.failed);

            /* wait */
            try { Thread.sleep(2000L); } catch(InterruptedException e) {}

            /* publish next messages to the channel */
            CompletionSet msgComplete2 = new CompletionSet();
            for(int i = 0; i < messageCount; i++) {
                channelTx.publish("test_event", "Test message (recover_implicit_connect) " + i, msgComplete2.add());
                try { Thread.sleep(delay); } catch(InterruptedException e){}
            }

            /* wait for the publish callback to be called */
            errors = msgComplete2.waitFor();
            assertTrue("Verify success from all message callbacks", errors.length == 0);

            /* establish a new rx connection with recover string, and wait for connection */
            ClientOptions recoverOpts = createOptions(testVars.keys[0].keyStr);
            recoverOpts.recover = recoverConnectionKey;
            ablyRxRecover = new RealtimeClient(recoverOpts);

            /* subscribe to channel */
            final Channel channelRxRecover = ablyRxRecover.channels.get(channelName);
            MessageWaiter messageWaiterRecover =  new MessageWaiter(channelRxRecover);

            /* wait for the subscription callback to be called */
            messageWaiterRecover.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called after recovery", messageWaiterRecover.receivedMessages.size(), messageCount);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ablyTx != null)
                ablyTx.close();
            if(ablyRx != null)
                ablyRx.close();
            if(ablyRxRecover != null)
                ablyRxRecover.close();
        }
    }

    /**
     * Connect to the service using two library instances to set
     * up separate send and recv connections.
     * Disconnect+suspend and then reconnect the send connection; verify that
     * each subsequent publish causes a CompletionListener call.
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void recover_verify_publish() {
        RealtimeClient ablyRx = null, ablyTx = null;
        String channelName = "recover_verify_publish";
        int messageCount = 5;
        long delay = 200;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ablyRx = new RealtimeClient(opts);
            ablyTx = new RealtimeClient(opts);

            /* create and attach channel to send on */
            final Channel channelTx = ablyTx.channels.get(channelName);
            channelTx.attach();
            (new ChannelWaiter(channelTx)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached for tx", channelTx.state, ChannelState.attached);

            /* create and attach channel to recv on */
            final Channel channelRx = ablyRx.channels.get(channelName);
            channelRx.attach();
            (new ChannelWaiter(channelRx)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached for rx", channelRx.state, ChannelState.attached);

            /* subscribe */
            MessageWaiter messageWaiter =  new MessageWaiter(channelRx);

            /* publish first messages to the channel */
            CompletionSet msgComplete1 = new CompletionSet();
            for(int i = 0; i < messageCount; i++) {
                channelTx.publish("test_event", "Test message (resume_simple) " + i, msgComplete1.add());
                try { Thread.sleep(delay); } catch(InterruptedException e){}
            }

            /* wait for the publish callback to be called */
            ErrorInfo[] errors = msgComplete1.waitFor();
            assertTrue("Verify success from all message callbacks", errors.length == 0);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called", messageWaiter.receivedMessages.size(), messageCount);
            messageWaiter.reset();

            /* suspend the tx connection, without closing;
             * NOTE this depends on knowledge of the internal structure
             * of the library, to simulate a dropped transport without
             * causing the connection itself to be disposed */
            System.out.println("*** about to suspend tx connection");
            ablyTx.connection.connectionManager.requestState(ConnectionState.suspended);

            /* wait */
            try { Thread.sleep(2000L); } catch(InterruptedException e) {}

            /* reconnect the tx connection */
            System.out.println("*** about to reconnect tx connection");
            ablyTx.connection.connect();
            (new ConnectionWaiter(ablyTx.connection)).waitFor(ConnectionState.connected);

            /* need to manually attach the tx channel as connection was suspended */
            System.out.println("*** tx connection now connected. About to recover channel");
            channelTx.attach();
            (new ChannelWaiter(channelTx)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached for tx again", channelTx.state, ChannelState.attached);
            System.out.println("*** tx channel now attached. About to publish");

            /* publish further messages to the channel */
            CompletionSet msgComplete2 = new CompletionSet();
            for(int i = 0; i < messageCount; i++) {
                channelTx.publish("test_event", "Test message (resume_simple) " + i, msgComplete2.add());
                try { Thread.sleep(delay); } catch(InterruptedException e){}
            }

            /* wait for the publish callback to be called. This never finishes if
             * https://github.com/ably/ably-java/issues/170
             * is not fixed. */
            System.out.println("*** published. About to wait for callbacks");
            errors = msgComplete2.waitFor();
            System.out.println("*** done");
            assertTrue("Verify success from all message callbacks", errors.length == 0);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called after reconnection", messageWaiter.receivedMessages.size(), messageCount);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ablyTx != null)
                ablyTx.close();
            if(ablyRx != null)
                ablyRx.close();
        }
    }

    /**
     * Test if ConnectionManager behaves correctly after exception thrown from ITransport.send()
     */
    @Test
    public void recover_transport_send_exception() {
        RealtimeClient ably = null;
        final String channelName = "recover_transport_send_exception";
        try {
            MockWebsocketFactory mockTransport = new MockWebsocketFactory();
            mockTransport.throwOnSend = false;
            mockTransport.exceptionsThrown = 0;

            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            opts.transportFactory = mockTransport;

            opts.autoConnect = false;
            ably = new RealtimeClient(opts);
            ConnectionWaiter connWaiter = new ConnectionWaiter(ably.connection);
            ably.connection.connect();
            connWaiter.waitFor(ConnectionState.connected);

            Channel channel = ably.channels.get(channelName);
            channel.attach();
            new ChannelWaiter(channel).waitFor(ChannelState.attached);

            ably.connection.connectionManager.requestState(ConnectionState.disconnected);
            connWaiter.waitFor(ConnectionState.connecting, 2);

            /* Start throwing exceptions in the send() */
            mockTransport.throwOnSend = true;
            for (int i=0; i<5; i++) {
                try {
                    channel.publish("name", "data");
                } catch (Throwable e) {
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {}

            /* Stop exceptions */
            mockTransport.throwOnSend = false;
            channel.publish("name2", "data2");

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {}

            /* It is hard to predict how many exception would be thrown but it shouldn't be hundreds */
            assertThat("", mockTransport.exceptionsThrown, is(lessThan(10)));

        } catch (AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception");
        } finally {
            if (ably != null)
                ably.close();
        }
    }

    public static class MockWebsocketFactory implements ITransport.Factory {
        boolean throwOnSend = false;
        int exceptionsThrown = 0;

        /*
         * Special transport class that allows throwing exceptions from send()
         */
        private class MockWebsocketTransport extends WebSocketTransport {
            private MockWebsocketTransport(TransportParams transportParams, ConnectionManager connectionManager) {
                super(transportParams, connectionManager);
            }

            @Override
            public void send(ProtocolMessage msg) throws AblyException {
                if (throwOnSend) {
                    exceptionsThrown++;
                    throw AblyException.fromErrorInfo(new ErrorInfo("TestException", 40000));
                } else {
                    super.send(msg);
                }
            }
        }

        @Override
        public ITransport getTransport(ITransport.TransportParams transportParams, ConnectionManager connectionManager) {
            return new MockWebsocketTransport(transportParams, connectionManager);
        }
    }
}
