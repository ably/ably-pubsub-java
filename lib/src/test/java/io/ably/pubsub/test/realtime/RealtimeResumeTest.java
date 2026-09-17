package io.ably.pubsub.test.realtime;

import io.ably.pubsub.debug.DebugOptions;
import io.ably.pubsub.realtime.RealtimeClient;
import io.ably.pubsub.realtime.RealtimeClientFactory;
import io.ably.pubsub.realtime.Channel;
import io.ably.pubsub.realtime.ChannelState;
import io.ably.pubsub.realtime.ConnectionState;
import io.ably.pubsub.test.common.Helpers;
import io.ably.pubsub.test.common.Helpers.ChannelWaiter;
import io.ably.pubsub.test.common.Helpers.CompletionSet;
import io.ably.pubsub.test.common.Helpers.ConnectionWaiter;
import io.ably.pubsub.test.common.Helpers.MessageWaiter;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.test.util.MockWebsocketFactory;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.Message;
import io.ably.pubsub.types.PresenceMessage;
import io.ably.pubsub.types.ProtocolMessage;
import io.ably.pubsub.util.Log;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RealtimeResumeTest extends ParameterizedTest {

    @Rule
    public Timeout testTimeout = Timeout.seconds(60);

    /**
     * Connect to the service and attach a channel.
     * Don't publish any messages; disconnect and then reconnect; verify that
     * the channel is still attached.
     */
    @Test
    public void resume_none() {
        RealtimeClient ably = null;
        String channelName = "resume_none";
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);

            /* create and attach channel */
            final Channel channel = ably.channels.get(channelName);
            System.out.println("Attaching");
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            new Helpers.MutableConnectionManager(ably).disconnectAndSuppressRetries();
            connectionWaiter.waitFor(ConnectionState.disconnected);

            /* reconnect the rx connection */
            ably.connection.connect();
            System.out.println("Waiting for reconnection");
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);

            /* wait */
            System.out.println("Got reconnection; waiting 2s");
            try { Thread.sleep(2000L); } catch(InterruptedException ignored) {}

            /* Check the channel is still attached. */
            assertEquals("Verify channel still attached", channel.state, ChannelState.attached);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception: "+e.getMessage());
        } finally {
            if(ably != null) {
                ably.close();
            }
        }
    }

    /**
     * Connect to the service using two library instances to set
     * up separate send and recv connections.
     * Disconnect and then reconnect one connection; verify that
     * the connection continues to receive messages on attached
     * channels after reconnection.
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void resume_simple() {
        RealtimeClient ablyTx = null;
        RealtimeClient ablyRx = null;
        String channelName = "resume_simple";
        int messageCount = 5;
        long delay = 200;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ablyRx = RealtimeClientFactory.create(opts);
            ablyTx = RealtimeClientFactory.create(opts);

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
                try { Thread.sleep(delay); } catch(InterruptedException ignored){}
            }

            /* wait for the publish callback to be called */
            ErrorInfo[] errors = msgComplete1.waitFor();
            assertEquals("Verify success from all message callbacks", 0, errors.length);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called", messageWaiter.receivedMessages.size(), messageCount);
            messageWaiter.reset();

            /* disconnect the rx connection, without closing;
             * NOTE this depends on knowledge of the internal structure
             * of the library, to simulate a dropped transport without
             * causing the connection itself to be disposed */
            ablyRx.connection.connectionManager.requestState(ConnectionState.disconnected);

            /* wait */
            try { Thread.sleep(2000L); } catch(InterruptedException ignored) {}

            /* reconnect the rx connection */
            ablyRx.connection.connect();

            /* publish further messages to the channel */
            CompletionSet msgComplete2 = new CompletionSet();
            for(int i = 0; i < messageCount; i++) {
                channelTx.publish("test_event", "Test message (resume_simple) " + i, msgComplete2.add());
                try { Thread.sleep(delay); } catch(InterruptedException ignored){}
            }

            /* wait for the publish callback to be called */
            errors = msgComplete2.waitFor();
            assertEquals("Verify success from all message callbacks", 0, errors.length);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called after reconnection", messageWaiter.receivedMessages.size(), messageCount);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ablyTx != null) {
                ablyTx.close();
            }
            if(ablyRx != null) {
                ablyRx.close();
            }
        }
    }

    /**
     * Connect to the service using two library instances to set
     * up separate send and recv connections.
     * Send on one connection while the other is disconnected;
     * verify that the messages sent whilst disconnected are delivered
     * on resume
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void resume_disconnected() {
        RealtimeClient ablyTx = null;
        RealtimeClient ablyRx = null;
        String channelName = "resume_disconnected";
        int messageCount = 5;
        long delay = 200;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ablyRx = RealtimeClientFactory.create(opts);
            ablyTx = RealtimeClientFactory.create(opts);

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
                channelTx.publish("test_event", "Test message (resume_disconnected) " + i, msgComplete1.add());
                try { Thread.sleep(delay); } catch(InterruptedException ignored){}
            }

            /* wait for the publish callback to be called */
            ErrorInfo[] errors = msgComplete1.waitFor();
            assertEquals("Verify success from all message callbacks", 0, errors.length);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called", messageWaiter.receivedMessages.size(), messageCount);
            messageWaiter.reset();

            /* disconnect the rx connection, without closing;
             * NOTE this depends on knowledge of the internal structure
             * of the library, to simulate a dropped transport without
             * causing the connection itself to be disposed */
            ablyRx.connection.connectionManager.requestState(ConnectionState.disconnected);

            /* wait */
            try { Thread.sleep(2000L); } catch(InterruptedException ignored) {}

            /* publish next messages to the channel */
            CompletionSet msgComplete2 = new CompletionSet();
            for(int i = 0; i < messageCount; i++) {
                channelTx.publish("test_event", "Test message (resume_disconnected) " + i, msgComplete2.add());
                try { Thread.sleep(delay); } catch(InterruptedException ignored){}
            }

            /* wait for the publish callback to be called */
            errors = msgComplete2.waitFor();
            assertEquals("Verify success from all message callbacks", 0, errors.length);

            /* reconnect the rx connection, and expect the messages to be delivered */
            ablyRx.connection.connect();
            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called after reconnection", messageWaiter.receivedMessages.size(), messageCount);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ablyTx != null) {
                ablyTx.close();
            }
            if(ablyRx != null) {
                ablyRx.close();
            }
        }
    }

    /**
     * Verify resume behaviour with multiple channels
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void resume_multiple_channel() {
        RealtimeClient ablyTx = null;
        RealtimeClient ablyRx = null;
        String channelName = "resume_multiple_channel";
        int messageCount = 5;
        long delay = 200;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ablyRx = RealtimeClientFactory.create(opts);
            ablyTx = RealtimeClientFactory.create(opts);

            /* create and attach channels to send on */
            final Channel channelTx1 = ablyTx.channels.get(channelName + "_1");
            channelTx1.attach();
            (new ChannelWaiter(channelTx1)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached for tx", channelTx1.state, ChannelState.attached);
            final Channel channelTx2 = ablyTx.channels.get(channelName + "_2");
            channelTx2.attach();
            (new ChannelWaiter(channelTx2)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached for tx", channelTx2.state, ChannelState.attached);

            /* create and attach channel to recv on */
            final Channel channelRx1 = ablyRx.channels.get(channelName + "_1");
            channelRx1.attach();
            (new ChannelWaiter(channelRx1)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached for rx", channelRx1.state, ChannelState.attached);
            final Channel channelRx2 = ablyRx.channels.get(channelName + "_2");
            channelRx2.attach();
            (new ChannelWaiter(channelRx2)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached for rx", channelRx2.state, ChannelState.attached);

            /* subscribe */
            MessageWaiter messageWaiter1 =  new MessageWaiter(channelRx1);
            MessageWaiter messageWaiter2 =  new MessageWaiter(channelRx2);

            /* publish first messages to channels */
            CompletionSet msgComplete1 = new CompletionSet();
            for(int i = 0; i < messageCount; i++) {
                channelTx1.publish("test_event1", "Test message (resume_multiple_channel) " + i, msgComplete1.add());
                channelTx2.publish("test_event2", "Test message (resume_multiple_channel) " + i, msgComplete1.add());
                try { Thread.sleep(delay); } catch(InterruptedException ignored){}
            }

            /* wait for the publish callback to be called */
            ErrorInfo[] errors = msgComplete1.waitFor();
            assertEquals("Verify success from all message callbacks", 0, errors.length);

            /* wait for the subscription callback to be called */
            messageWaiter1.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called", messageWaiter1.receivedMessages.size(), messageCount);
            messageWaiter1.reset();
            messageWaiter2.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called", messageWaiter2.receivedMessages.size(), messageCount);
            messageWaiter2.reset();

            /* disconnect the rx connection, without closing;
             * NOTE this depends on knowledge of the internal structure
             * of the library, to simulate a dropped transport without
             * causing the connection itself to be disposed */
            ablyRx.connection.connectionManager.requestState(ConnectionState.disconnected);

            /* wait */
            try { Thread.sleep(2000L); } catch(InterruptedException ignored) {}

            /* publish next messages to the channel */
            CompletionSet msgComplete2 = new CompletionSet();
            for(int i = 0; i < messageCount; i++) {
                channelTx1.publish("test_event1", "Test message (resume_multiple_channel) " + i, msgComplete2.add());
                channelTx2.publish("test_event2", "Test message (resume_multiple_channel) " + i, msgComplete2.add());
                try { Thread.sleep(delay); } catch(InterruptedException ignored){}
            }

            /* wait for the publish callback to be called */
            errors = msgComplete2.waitFor();
            assertEquals("Verify success from all message callbacks", 0, errors.length);

            /* reconnect the rx connection, and expect the messages to be delivered */
            ablyRx.connection.connect();
            /* wait for the subscription callback to be called */
            messageWaiter1.waitFor(messageCount);
            messageWaiter2.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called after reconnection", messageWaiter1.receivedMessages.size(), messageCount);
            assertEquals("Verify message subscriptions all called after reconnection", messageWaiter2.receivedMessages.size(), messageCount);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ablyTx != null) {
                ablyTx.close();
            }
            if(ablyRx != null) {
                ablyRx.close();
            }
        }
    }

    /**
     * Verify resume behaviour across disconnect periods covering
     * multiple subminute intervals
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void resume_multiple_interval() {
        RealtimeClient ablyTx = null;
        RealtimeClient ablyRx = null;
        String channelName = "resume_multiple_interval";
        int messageCount = 5;
        long delay = 200;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ablyRx = RealtimeClientFactory.create(opts);
            ablyTx = RealtimeClientFactory.create(opts);

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
                channelTx.publish("test_event", "Test message (resume_multiple_interval) " + i, msgComplete1.add());
                try { Thread.sleep(delay); } catch(InterruptedException ignored){}
            }

            /* wait for the publish callback to be called */
            ErrorInfo[] errors = msgComplete1.waitFor();
            assertEquals("Verify success from all message callbacks", 0, errors.length);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called", messageWaiter.receivedMessages.size(), messageCount);
            messageWaiter.reset();

            /* disconnect the rx connection, without closing;
             * NOTE this depends on knowledge of the internal structure
             * of the library, to simulate a dropped transport without
             * causing the connection itself to be disposed */
            ablyRx.connection.connectionManager.requestState(ConnectionState.disconnected);

            /* wait */
            try { Thread.sleep(20000L); } catch(InterruptedException ignored) {}

            /* publish next messages to the channel */
            CompletionSet msgComplete2 = new CompletionSet();
            for(int i = 0; i < messageCount; i++) {
                channelTx.publish("test_event", "Test message (resume_multiple_interval) " + i, msgComplete2.add());
                try { Thread.sleep(delay); } catch(InterruptedException ignored){}
            }

            /* wait for the publish callback to be called */
            errors = msgComplete2.waitFor();
            assertEquals("Verify success from all message callbacks", 0, errors.length);

            /* reconnect the rx connection, and expect the messages to be delivered */
            ablyRx.connection.connect();
            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called after reconnection", messageWaiter.receivedMessages.size(), messageCount);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ablyTx != null) {
                ablyTx.close();
            }
            if(ablyRx != null) {
                ablyRx.close();
            }
        }
    }

    /**
     * Connect to the service using two library instances to set
     * up separate send and recv connections.
     * Disconnect and then reconnect the send connection; verify that
     * each subsequent publish causes a CompletionListener call.
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void resume_verify_publish() {
        RealtimeClient ablyTx = null;
        RealtimeClient ablyRx = null;
        String channelName = "resume_verify_publish";
        int messageCount = 5;
        long delay = 200;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ablyRx = RealtimeClientFactory.create(opts);
            ablyTx = RealtimeClientFactory.create(opts);

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
                try { Thread.sleep(delay); } catch(InterruptedException ignored){}
            }

            /* wait for the publish callback to be called */
            ErrorInfo[] errors = msgComplete1.waitFor();
            assertEquals("Verify success from all message callbacks", 0, errors.length);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called", messageWaiter.receivedMessages.size(), messageCount);
            messageWaiter.reset();

            /* disconnect the tx connection, without closing;
             * NOTE this depends on knowledge of the internal structure
             * of the library, to simulate a dropped transport without
             * causing the connection itself to be disposed */
            System.out.println("*** about to disconnect tx connection");

            new Helpers.MutableConnectionManager(ablyTx).disconnectAndSuppressRetries();
            (new ConnectionWaiter(ablyTx.connection)).waitFor(ConnectionState.disconnected);

            /* wait */
            try { Thread.sleep(2000L); } catch(InterruptedException ignored) {}

            /* reconnect the tx connection */
            System.out.println("*** about to reconnect tx connection");
            ablyTx.connection.connect();
            (new ConnectionWaiter(ablyTx.connection)).waitFor(ConnectionState.connected);

            /* publish further messages to the channel */
            CompletionSet msgComplete2 = new CompletionSet();
            for(int i = 0; i < messageCount; i++) {
                channelTx.publish("test_event", "Test message (resume_simple) " + i, msgComplete2.add());
                try { Thread.sleep(delay); } catch(InterruptedException ignored){}
            }

            /* wait for the publish callback to be called. This never finishes if
             * https://github.com/ably/ably-java/issues/170
             * is not fixed. */
            System.out.println("*** published. About to wait for callbacks");
            errors = msgComplete2.waitFor();
            System.out.println("*** done");
            assertEquals("Verify success from all message callbacks", 0, errors.length);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called after reconnection", messageWaiter.receivedMessages.size(), messageCount);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ablyTx != null) {
                ablyTx.close();
            }
            if(ablyRx != null) {
                ablyRx.close();
            }
        }
    }

    /**
     * Connect to the service using two library instances to set
     * up separate send and recv connections.
     *
     * Send some messages, drop the sender's transport, then send another
     * round of messages which should be queued and published after
     * we reconnect the sender.
     */
    @Test
    public void resume_publish_queue() {
        RealtimeClient receiver = null;
        RealtimeClient sender = null;
        String channelName = "resume_publish_queue";
        int messageCount = 3;
        long delay = 200;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            receiver = RealtimeClientFactory.create(opts);
            sender = RealtimeClientFactory.create(opts);

            /* create and attach channel to send on */
            final Channel senderChannel = sender.channels.get(channelName);
            senderChannel.attach();
            (new ChannelWaiter(senderChannel)).waitFor(ChannelState.attached);
            assertEquals("The sender's channel should be attached",
                senderChannel.state, ChannelState.attached);

            /* create and attach channel to recv on */
            final Channel receiverChannel = receiver.channels.get(channelName);
            receiverChannel.attach();
            (new ChannelWaiter(receiverChannel)).waitFor(ChannelState.attached);
            assertEquals("The receiver's channel should be attached",
                receiverChannel.state, ChannelState.attached);

            /* subscribe */
            MessageWaiter messageWaiter =  new MessageWaiter(receiverChannel);

            /* publish first messages to the channel */
            CompletionSet msgComplete1 = new CompletionSet();
            for(int i = 0; i < messageCount; i++) {
                senderChannel.publish("test_event", "Test message (resume_publish_queue) " + i, msgComplete1.add());
                try { Thread.sleep(delay); } catch(InterruptedException ignored){}
            }

            /* wait for the publish callback to be called */
            ErrorInfo[] errors = msgComplete1.waitFor();
            assertEquals("First round of messages has errors", 0, errors.length);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(messageCount);
            assertEquals("Did not receive the entire first round of messages",
                messageWaiter.receivedMessages.size(), messageCount);
            messageWaiter.reset();

            /* disconnect the sender, without closing;
             * NOTE this depends on knowledge of the internal structure
             * of the library, to simulate a dropped transport without
             * causing the connection itself to be disposed */
            sender.connection.connectionManager.requestState(ConnectionState.disconnected);

            /* wait */
            try { Thread.sleep(2000L); } catch(InterruptedException ignored) {}

            /*
             *  publish further messages to the channel, which should be queued
             *  because the channel is currently disconnected.
             */
            CompletionSet msgComplete2 = new CompletionSet();
            for(int i = 0; i < messageCount; i++) {
                senderChannel.publish("queued_message_" + i, "Test queued message (resume_publish_queue) " + i, msgComplete2.add());
                try { Thread.sleep(delay); } catch(InterruptedException ignored){}
            }

            /* reconnect the sender */
            sender.connection.connect();
            (new ConnectionWaiter(sender.connection)).waitFor(ConnectionState.connected);

            /* wait for the publish callback to be called.*/
            errors = msgComplete2.waitFor();
            assertEquals("Second round of messages (queued) has errors", 0, errors.length);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(messageCount);

            List<Message> received = messageWaiter.receivedMessages;
            assertEquals(
                "Did not receive the entire second round of messages (queued)",
                received.size(), messageCount
            );
            for(int i=0; i<received.size(); i++) {
                assertEquals("Received unexpected queued message", received.get(i).name,
                    "queued_message_" + i);
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(sender != null) {
                sender.close();
            }
            if(receiver != null) {
                receiver.close();
            }
        }
    }

    /**
     * In case of resume success, verify that pending messages are resent. By blocking ack/nacks before sending the
     * message while connected and then disconnect, add some more messages
     * */
    @Test
    public void resume_publish_resend_pending_messages_when_resume_is_successful() {
        final String channelName = "resume_publish_queue";
        RealtimeClient sender = null;
        try {
            final MockWebsocketFactory mockWebsocketFactory = new MockWebsocketFactory();
            String keyStr = testVars.keys[0].keyStr;
            DebugOptions senderOptions = createOptions(keyStr);
            senderOptions.logLevel = Log.VERBOSE;
            senderOptions.queueMessages = true;
            senderOptions.transportFactory = mockWebsocketFactory;
            sender = RealtimeClientFactory.create(senderOptions);

            (new ConnectionWaiter(sender.connection)).waitFor(ConnectionState.connected);
            final Channel senderChannel = sender.channels.get(channelName);
            senderChannel.attach();
            (new ChannelWaiter(senderChannel)).waitFor(ChannelState.attached);
            assertEquals("The sender's channel should be attached",
                senderChannel.state, ChannelState.attached);

            MockWebsocketFactory.MockWebsocketTransport transport = mockWebsocketFactory.getCreatedTransport();

            final CompletionSet senderCompletion = new CompletionSet();
            //send 3 successful messages
            for (int i = 0; i < 3; i++) {
                senderChannel.publish("non_pending messages" + i, "Test pending queued messages " + i,
                    senderCompletion.add());
            }

            /* wait for the publish callback to be called.*/
            ErrorInfo[] errors = senderCompletion.waitFor();
            assertEquals("First completion has errors", 0, errors.length);

            //assert that messages sent till now are sent with correct size and serials
            assertEquals("First round of messages has incorrect size", 3,
                transport.getPublishedMessages().size());

            for (int i = 0; i < transport.getPublishedMessages().size(); i++) {
                ProtocolMessage protocolMessage = transport.getPublishedMessages().get(i);
                assertEquals("Sent serial incorrect", Long.valueOf(i), protocolMessage.msgSerial);
            }

            //now clear published messages - new messages should start with serial 3
            transport.clearPublishedMessages();

            //block ack/nack messages to simulate pending message
            //note that this will only block ack/nack messages received by connection manager
            mockWebsocketFactory.blockReceiveProcessing(message -> message.action == ProtocolMessage.Action.ack ||
                message.action == ProtocolMessage.Action.nack);

            for (int i = 0; i < 3; i++) {
                senderChannel.publish("pending_queued_message_" + i, "Test pending queued messages " + i,
                    senderCompletion.add());
            }
            assertEquals(sender.connection.connectionManager.getPendingMessages().size(),3);

            final String connectionId = sender.connection.id;

            new Helpers.MutableConnectionManager(sender).disconnectAndSuppressRetries();
            (new ConnectionWaiter(sender.connection)).waitFor(ConnectionState.disconnected);

            sender.connection.connectionManager.requestState(ConnectionState.disconnected);
            (new ConnectionWaiter(sender.connection)).waitFor(ConnectionState.disconnected);
            assertEquals("Connection must be disconnected", ConnectionState.disconnected, sender.connection.state);

            System.out.println("resume_publish_test: Disconnected");

            //send 3 more messages while disconnected
            for (int i = 0; i < 3; i++) {
                senderChannel.publish("queued_message_" + i, "Test pending queued messages " + i,
                    senderCompletion.add());
            }

            //now let's unblock the ack nacks and reconnect
            mockWebsocketFactory.blockReceiveProcessing(message -> false);
            sender.connection.connect();
            (new ConnectionWaiter(sender.connection)).waitFor(ConnectionState.connected);
            assertEquals("Connection must be connected", ConnectionState.connected, sender.connection.state);
            //make sure connection id is a resume success
            assertEquals("Connection id has changed", connectionId, sender.connection.id);

            //replace mock transport
            transport = mockWebsocketFactory.getCreatedTransport();

            (new ChannelWaiter(senderChannel)).waitFor(ChannelState.attached);
            /* wait for the publish callback to be called.*/
            ErrorInfo[] senderErrors = senderCompletion.waitFor();
            assertEquals("Second round of send has errors", 0, senderErrors.length);

            assertEquals("Second round of messages has incorrect size", 6, transport.getPublishedMessages().size());
            //make sure they were sent with correct serials
            for (int i = 0; i < transport.getPublishedMessages().size(); i++) {
                ProtocolMessage protocolMessage = transport.getPublishedMessages().get(i);
                assertEquals("Second round sent serial incorrect", Long.valueOf(i+3), protocolMessage.msgSerial);
            }

            //make sure that pending queue is cleared
            assertEquals("There are still pending messages in the queue",
                sender.connection.connectionManager.getPendingMessages().size(),
                0);

        } catch (AblyException e) {
            fail("Unexpected exception: "+e.getMessage());
        }  finally {
            if (sender != null) {
                sender.close();
            }
        }
    }

    /**
     * In case of resume failure verify that messages are being resent
     * */
    @Test
    public void resume_publish_resend_pending_messages_when_resume_failed() throws AblyException {
        final String channelName = "sender_channel";
        final MockWebsocketFactory mockWebsocketFactory = new MockWebsocketFactory();
        final DebugOptions options = createOptions(testVars.keys[0].keyStr);
        options.logLevel = Log.VERBOSE;
        options.realtimeRequestTimeout = 2000L;
        options.transportFactory = mockWebsocketFactory;
        try(RealtimeClient ably = RealtimeClientFactory.create(options)) {
            final long newTtl = 1000L;
            final long newIdleInterval = 1000L;

            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);

            Helpers.MutableConnectionManager connectionManager = new Helpers.MutableConnectionManager(ably);
            connectionManager.setField("connectionStateTtl", newTtl);
            connectionManager.setField("maxIdleInterval", newIdleInterval);

            final Channel senderChannel = ably.channels.get(channelName);
            senderChannel.attach();
            (new ChannelWaiter(senderChannel)).waitFor(ChannelState.attached);
            assertEquals(
                "The sender's channel should be attached",
                senderChannel.state, ChannelState.attached
            );

            MockWebsocketFactory.MockWebsocketTransport transport = mockWebsocketFactory.getCreatedTransport();
            CompletionSet senderCompletion = new CompletionSet();
            //send 3 successful messages
            for (int i = 0; i < 3; i++) {
                senderChannel.publish("non_pending messages" + i, "Test pending queued messages " + i,
                    senderCompletion.add());
            }

            /* wait for the publish callback to be called.*/
            ErrorInfo[] errors = senderCompletion.waitFor();
            assertEquals("First completion has errors", 0, errors.length);

            //assert that messages sent till now are sent with correct size and serials
            assertEquals("First round of messages has incorrect size", 3, transport.getPublishedMessages().size());
            for (int i = 0; i < transport.getPublishedMessages().size(); i++) {
                ProtocolMessage protocolMessage = transport.getPublishedMessages().get(i);
                assertEquals("Sent serial incorrect", Long.valueOf(i), protocolMessage.msgSerial);
            }

            //block acks nacks before send
            mockWebsocketFactory.blockReceiveProcessing(message -> message.action == ProtocolMessage.Action.ack ||
                message.action == ProtocolMessage.Action.nack);
            for (int i = 0; i < 3; i++) {
                senderChannel.publish("pending_queued_message_" + i, "Test pending queued messages " + i,
                    senderCompletion.add());
            }

            final String firstConnectionId = ably.connection.id;

            connectionManager.disconnectAndSuppressRetries();
            connectionWaiter.waitFor(ConnectionState.disconnected);
            assertEquals("Disconnected state was not reached", ConnectionState.disconnected, ably.connection.state);

            //send some more messages while disconnected
            for (int i = 0; i < 3; i++) {
                senderChannel.publish("queued_message_" + i, "Test pending queued messages " + i,
                    senderCompletion.add());
            }
            //now let's unblock the ack nacks and reconnect
            mockWebsocketFactory.blockReceiveProcessing(message -> false);

            /* We want this greater than newTtl + newIdleInterval */
            final long waitInDisconnectedState = 3000L;
            /* Wait for the connection to go stale, then reconnect */
            try {
                Thread.sleep(waitInDisconnectedState);
            } catch (InterruptedException ignored) {
            }
            ably.connection.connect();
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Connected state was not reached", ConnectionState.connected, ably.connection.state);
            //replace transport
            transport = mockWebsocketFactory.getCreatedTransport();
            /* Verify the connection is new */
            assertNotNull(ably.connection.id);
            assertNotEquals("Connection has the same id", firstConnectionId, ably.connection.id);

            // wait for channel to get attached
            (new ChannelWaiter(senderChannel)).waitFor(ChannelState.attached);
            assertEquals("Connection has the same id", ChannelState.attached, senderChannel.state);

            ErrorInfo[] resendErrors = senderCompletion.waitFor();
            assertEquals("Second round of messages (queued) has errors", 0, resendErrors.length);

            assertEquals("Second round of messages has incorrect size", 6, transport.getPublishedMessages().size());
            //make sure they were sent with reset serials
            for (int i = 0; i < transport.getPublishedMessages().size(); i++) {
                ProtocolMessage protocolMessage = transport.getPublishedMessages().get(i);
                assertEquals("Sent serial incorrect", Long.valueOf(i), protocolMessage.msgSerial);
            }
        }
    }

    /**
     * In case of resume failure verify that presence messages are resent
     * */
    @Test
    public void resume_publish_reenter_when_resume_failed() throws AblyException {
        final String channelName = "sender_channel";
        final MockWebsocketFactory mockWebsocketFactory = new MockWebsocketFactory();
        final DebugOptions options = createOptions(testVars.keys[0].keyStr);
        final String[] clients = new String[]{"client1", "client2", "client3", "client4", "client5",
            "client6", "client7", "client8", "client9"};

        options.logLevel = Log.VERBOSE;
        options.realtimeRequestTimeout = 2000L;

        options.transportFactory = mockWebsocketFactory;
        try(RealtimeClient ably = RealtimeClientFactory.create(options)) {

            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);

            final long newTtl = 1000L;
            final long newIdleInterval = 1000L;

            Helpers.MutableConnectionManager connectionManager = new Helpers.MutableConnectionManager(ably);
            connectionManager.setField("connectionStateTtl", newTtl);
            connectionManager.setField("maxIdleInterval", newIdleInterval);

            final Channel senderChannel = ably.channels.get(channelName);
            senderChannel.attach();
            (new ChannelWaiter(senderChannel)).waitFor(ChannelState.attached);
            assertEquals("The sender's channel should be attached", senderChannel.state, ChannelState.attached);

            MockWebsocketFactory.MockWebsocketTransport transport = mockWebsocketFactory.getCreatedTransport();
            CompletionSet presenceCompletion = new CompletionSet();
            //enter first three clients
            for (int i = 0; i < 3; i++) {
                senderChannel.presence.enterClient(clients[i],null,presenceCompletion.add());
            }
            /* wait for the publish callback to be called.*/
            ErrorInfo[] errors = presenceCompletion.waitFor();
            assertEquals("Firstenter has errors", 0, errors.length);

            //assert that messages sent till now are sent with correct size and client ids
            assertEquals("First round of presence messages have incorrect size", 3,
                transport.getSentPresenceMessages().size());
            for (int i = 0; i < transport.getSentPresenceMessages().size(); i++) {
                PresenceMessage presenceMessage = transport.getSentPresenceMessages().get(i);
                assertEquals("Sent presence serial incorrect", clients[i], presenceMessage.clientId);
            }

            //block acks nacks before send
            mockWebsocketFactory.blockReceiveProcessing(message -> message.action == ProtocolMessage.Action.ack ||
                message.action == ProtocolMessage.Action.nack);

            //enter next 3 clients
            for (int i = 3; i < 6; i++) {
                senderChannel.presence.enterClient(clients[i],null,presenceCompletion.add());
            }

            final String firstConnectionId = ably.connection.id;

            connectionManager.disconnectAndSuppressRetries();
            connectionWaiter.waitFor(ConnectionState.disconnected);
            assertEquals("Disconnected state was not reached", ConnectionState.disconnected, ably.connection.state);

            //enter last 3 clients while disconnected
            for (int i = 6; i < 9; i++) {
                senderChannel.presence.enterClient(clients[i],null,presenceCompletion.add());
            }

            /* We want this greater than newTtl + newIdleInterval */
            final long waitInDisconnectedState = 5000L;
            /* Wait for the connection to go stale, then reconnect */
            try {
                Thread.sleep(waitInDisconnectedState);
            } catch (InterruptedException ignored) {
            }

            //now let's unblock the ack nacks and reconnect
            mockWebsocketFactory.blockReceiveProcessing(message -> false);
            /* Wait for the connection to go stale, then reconnect */
            ably.connection.connect();
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Connected state was not reached", ConnectionState.connected, ably.connection.state);
            //replace transport
            transport = mockWebsocketFactory.getCreatedTransport();
            /* Verify the connection is new */
            assertNotNull(ably.connection.id);
            assertNotEquals("Connection has the same id", firstConnectionId, ably.connection.id);

            System.out.println("presence_resume_test: First connection id:"+firstConnectionId);
            System.out.println("presence_resume_test: Second connection id:"+ably.connection.id);

            // wait for channel to get attached
            (new ChannelWaiter(senderChannel)).waitFor(ChannelState.attached);
            assertEquals("Connection has the same id", ChannelState.attached, senderChannel.state);

            // presenceCompletion.add();
            ErrorInfo[] resendErrors = presenceCompletion.waitFor();
            for (ErrorInfo resendError : resendErrors) {
                System.out.println("presence_resume_test: error "+resendError.message);
            }
            assertEquals("Second round of messages (queued) has errors", 0, resendErrors.length);

            for (PresenceMessage presenceMessage: transport.getSentPresenceMessages()) {
                System.out.println("presence_resume_test: sent message with client: "+presenceMessage.clientId +" " +
                    " action:"+presenceMessage.action);
            }

            Set<String> sentClientIds = transport.getSentPresenceMessages().stream()
                .map(it -> it.clientId).collect(Collectors.toSet());

            assertEquals("Second round of messages has incorrect size",
                9,
                sentClientIds.size()
            );
            //make sure they were sent with correct client ids
            final Map<String,PresenceMessage> sentPresenceMap = new HashMap<>();
            for (PresenceMessage presenceMessage: transport.getSentPresenceMessages()){
                sentPresenceMap.put(presenceMessage.clientId, presenceMessage);
            }
            for (String client: clients) {
                assertTrue("Client id isn't there:" + client, sentPresenceMap.containsKey(client));
            }
        }
    }

    //RTL4j2
    @Test
    public void resume_rewind_1 ()
    {
        RealtimeClient receiver1 = null;
        RealtimeClient receiver2 = null;
        RealtimeClient sender = null;
        final String testMessage = "{ foo: \"bar\", count: 1, status: \"active\" }";

        String testName = "resume_rewind_1";
        try {
            ClientOptions common_opts = createOptions(testVars.keys[0].keyStr);
            sender = RealtimeClientFactory.create(common_opts);
            receiver1 = RealtimeClientFactory.create(common_opts);

            DebugOptions receiver2_opts = createOptions(testVars.keys[0].keyStr);
            receiver2_opts.protocolListener = new DebugOptions.RawProtocolListener() {
                @Override
                public void onRawConnect(String url) {}
                @Override
                public void onRawConnectRequested(String url) {}
                @Override
                public void onRawMessageSend(ProtocolMessage message) {
                    if(message.action == ProtocolMessage.Action.attach) {
                        message.setFlag(ProtocolMessage.Flag.attach_resume);
                    }
                }
                @Override
                public void onRawMessageRecv(ProtocolMessage message) {}
            };
            receiver2 = RealtimeClientFactory.create(receiver2_opts);

            Channel receiver1_channel = receiver1.channels.get("[?rewind=1]" + testName);

            Channel receiver2_channel = receiver2.channels.get("[?rewind=1]" + testName);

            Channel sender_channel = sender.channels.get(testName);
            sender_channel.attach();
            (new ChannelWaiter(sender_channel)).waitFor(ChannelState.attached);
            sender_channel.publish("0", testMessage);

            /* subscribe 1*/
            MessageWaiter messageWaiter_1 = new MessageWaiter(receiver1_channel);
            messageWaiter_1.waitFor(1);
            assertEquals("Verify rewound message", testMessage, messageWaiter_1.receivedMessages.get(0).data);

            /* subscribe 2*/
            MessageWaiter messageWaiter_2 = new MessageWaiter(receiver2_channel);
            messageWaiter_2.waitFor(1, 7000);
            assertEquals("Verify no message received on attach_rewind", 0, messageWaiter_2.receivedMessages.size());

        } catch(Exception e) {
            fail(testName + ": Unexpected exception " + e.getMessage());
            e.printStackTrace();
        } finally {
            if(receiver1 != null)
                receiver1.close();

            if(receiver2 != null)
                receiver2.close();

            if(sender != null)
                sender.close();
        }
    }
}
