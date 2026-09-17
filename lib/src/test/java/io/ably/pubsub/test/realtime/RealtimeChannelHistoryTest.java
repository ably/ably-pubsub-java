package io.ably.pubsub.test.realtime;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import io.ably.pubsub.realtime.RealtimeClient;
import io.ably.pubsub.realtime.Channel;
import io.ably.pubsub.realtime.ChannelState;
import io.ably.pubsub.test.common.Helpers.ChannelWaiter;
import io.ably.pubsub.test.common.Helpers.CompletionSet;
import io.ably.pubsub.test.common.Helpers.CompletionWaiter;
import io.ably.pubsub.test.common.Helpers.MessageWaiter;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.Message;
import io.ably.pubsub.types.PaginatedResult;
import io.ably.pubsub.types.Param;

@Ignore("FIXME: fix exceptions")
public class RealtimeChannelHistoryTest extends ParameterizedTest {

    private RealtimeClient ably;
    private long timeOffset;

    @Rule
    public Timeout testTimeout = Timeout.seconds(300);

    @Before
    public void setUpBefore() throws Exception {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        ably = new RealtimeClient(opts);
        long timeFromService = ably.time();
        timeOffset = timeFromService - System.currentTimeMillis();
    }

    /**
     * Send a single message on a channel and verify that it can be
     * retrieved using channel.history() without needing to wait for
     * it to be persisted.
     */
    @Test
    public void channelhistory_simple() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new RealtimeClient(opts);
            String channelName = "persisted:channelhistory_simple_" + testParams.name;
            String messageText = "Test message (channelhistory_simple)";

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            CompletionWaiter msgComplete = new CompletionWaiter();
            channel.publish("test_event", messageText, msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.success);

            /* get the history for this channel */
            PaginatedResult<Message> messages = channel.history(null);
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 1 message", messages.items().length, 1);

            /* verify message contents */
            assertEquals("Expect correct message text", messages.items()[0].data, messageText);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("single_history_binary: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Send couple of messages without listener on a channel and
     * verify that it can be retrieved using channel.history()
     * without needing to wait for it to be persisted.
     */
    @Test
    public void channelhistory_simple_withoutlistener() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new RealtimeClient(opts);
            String channelName = "persisted:channelhistory_simple_withoutlistener_" + testParams.name;
            String message1Text = "Test message 1 (channelhistory_simple_withoutlistener)";
            Message message2 = new Message("test_event", "Test message 2 (channelhistory_simple_withoutlistener)");
            Message[] messages34 = new Message[] {
                    new Message("test_event", "Test message 3 (channelhistory_simple_withoutlistener)"),
                    new Message("test_event", "Test message 4 (channelhistory_simple_withoutlistener)")
            };

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            channel.publish("test_event", message1Text);
            channel.publish(message2);
            channel.publish(messages34);

            /* Get history for the channel. Wait for no longer than 2 seconds for the history to be populated */
            PaginatedResult<Message> messages;
            int n = 0;
            do {
                messages = channel.history(null);
                if (messages.items().length < 4) {
                    try { Thread.sleep(100); } catch (InterruptedException e) {}
                }
            } while (messages.items().length < 4 && ++n < 20);

            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 4 message", messages.items().length, 4);

            /* verify we received message history from most recent to older */
            assertEquals("Expect correct message text", messages.items()[0].data, messages34[1].data);
            assertEquals("Expect correct message text", messages.items()[1].data, messages34[0].data);
            assertEquals("Expect correct message text", messages.items()[2].data, message2.data);
            assertEquals("Expect correct message text", messages.items()[3].data, message1Text);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("channelhistory_simple_binary_withoutlistener: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Publish events with data of various datatypes
     */
    @Test
    public void channelhistory_types() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new RealtimeClient(opts);
            String channelName = "persisted:channelhistory_types_" + testParams.name;

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            CompletionSet msgComplete = new CompletionSet();
            channel.publish("history0", "This is a string message payload", msgComplete.add());
            channel.publish("history1", "This is a byte[] message payload".getBytes(), msgComplete.add());

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

            /* get the history for this channel */
            PaginatedResult<Message> messages = channel.history(null);
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 2 messages", messages.items().length, 2);
            HashMap<String, Message> messageContents = new HashMap<String, Message>();

            /* verify message contents */
            for(Message message : messages.items())
                messageContents.put(message.name, message);
            assertEquals("Expect history0 to be expected String", messageContents.get("history0").data, "This is a string message payload");
            assertEquals("Expect history1 to be expected byte[]", new String((byte[])messageContents.get("history1").data), "This is a byte[] message payload");

            /* verify message order */
            Message[] expectedMessageHistory = new Message[]{
                messageContents.get("history1"),
                messageContents.get("history0")
            };
            Assert.assertArrayEquals("Expect messages in reverse order", messages.items(), expectedMessageHistory);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("channelhistory_types: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Publish events with data of various datatypes
     */
    @Test
    public void channelhistory_types_forward() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new RealtimeClient(opts);
            String channelName = "persisted:channelhistory_types_forward_" + testParams.name;

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            CompletionSet msgComplete = new CompletionSet();
            channel.publish("history0", "This is a string message payload", msgComplete.add());
            channel.publish("history1", "This is a byte[] message payload".getBytes(), msgComplete.add());

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

            /* get the history for this channel */
            PaginatedResult<Message> messages = channel.history(new Param[] { new Param("direction", "forwards") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 2 messages", messages.items().length, 2);
            HashMap<String, Message> messageContents = new HashMap<String, Message>();

            /* verify message contents */
            for(Message message : messages.items())
                messageContents.put(message.name, message);
            assertEquals("Expect history0 to be expected String", messageContents.get("history0").data, "This is a string message payload");
            assertEquals("Expect history1 to be expected byte[]", new String((byte[])messageContents.get("history1").data), "This is a byte[] message payload");

            /* verify message order */
            Message[] expectedMessageHistory = new Message[]{
                messageContents.get("history0"),
                messageContents.get("history1")
            };
            Assert.assertArrayEquals("Expect messages in sent order", messages.items(), expectedMessageHistory);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("channelhistory_types_forward: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Connect twice to the service, each using the default (binary) protocol.
     * Publish messages on one connection to a given channel; then attach
     * the second connection to the same channel and verify a complete message
     * history can be obtained.
     */
    @Test
    public void channelhistory_second_channel() {
        RealtimeClient txAbly = null, rxAbly = null;
        try {
            ClientOptions txOpts = createOptions(testVars.keys[0].keyStr);
            txAbly = new RealtimeClient(txOpts);
            ClientOptions rxOpts = createOptions(testVars.keys[0].keyStr);
            rxAbly = new RealtimeClient(rxOpts);
            String channelName = "persisted:channelhistory_second_channel_" + testParams.name;

            /* create a channel */
            final Channel txChannel = txAbly.channels.get(channelName);
            final Channel rxChannel = rxAbly.channels.get(channelName);

            /* attach sender */
            txChannel.attach();
            (new ChannelWaiter(txChannel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", txChannel.state, ChannelState.attached);

            /* publish to the channel */
            String messageText = "Test message (channelhistory_second_channel)";
            CompletionWaiter msgComplete = new CompletionWaiter();
            txChannel.publish("test_event", messageText, msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.success);

            /* attach receiver */
            rxChannel.attach();
            (new ChannelWaiter(rxChannel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", rxChannel.state, ChannelState.attached);

            /* get the history for this channel */
            PaginatedResult<Message> messages = rxChannel.history(null);
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 1 message", messages.items().length, 1);

            /* verify message contents */
            assertEquals("Expect correct message text", messages.items()[0].data, messageText);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(txAbly != null)
                txAbly.close();
            if(rxAbly != null)
                rxAbly.close();
        }
    }

    /**
     * Send a single message on a channel and verify that it can be
     * retrieved using channel.history() after waiting for it to be
     * persisted.
     */
    @Test
    public void channelhistory_wait_b() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new RealtimeClient(opts);
            String channelName = "persisted:channelhistory_wait_b_" + testParams.name;
            String messageText = "Test message (channelhistory_wait_b)";

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            CompletionWaiter msgComplete = new CompletionWaiter();
            channel.publish("test_event", messageText, msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.success);

            /* wait for the history to be persisted */
            try {
                Thread.sleep(16000);
            } catch(InterruptedException ignored) {}

            /* get the history for this channel */
            PaginatedResult<Message> messages = channel.history(null);
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 1 message", messages.items().length, 1);

            /* verify message contents */
            assertEquals("Expect correct message text", messages.items()[0].data, messageText);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("single_history_binary: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Send a single message on a channel and verify that it can be
     * retrieved using channel.history(direction=forwards) after waiting
     * for it to be persisted.
     */
    @Test
    public void channelhistory_wait_f() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new RealtimeClient(opts);
            String channelName = "persisted:channelhistory_wait_f_" + testParams.name;
            String messageText = "Test message (channelhistory_wait_f)";

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            CompletionWaiter msgComplete = new CompletionWaiter();
            channel.publish("test_event", messageText, msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.success);

            /* wait for the history to be persisted */
            try {
                Thread.sleep(16000);
            } catch(InterruptedException ie) {}

            /* get the history for this channel */
            PaginatedResult<Message> messages = channel.history(new Param[]{ new Param("direction", "forwards") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 1 message", messages.items().length, 1);

            /* verify message contents */
            assertEquals("Expect correct message text", messages.items()[0].data, messageText);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("single_history_binary: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Send a single message on a channel, wait enough time for it to
     * persist, then send a second message. Verify that both can be
     * retrieved using channel.history() without any further wait.
     */
    @Test
    public void channelhistory_mixed_b() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new RealtimeClient(opts);
            String channelName = "persisted:channelhistory_mixed_b_" + testParams.name;
            String messageText = "Test message (channelhistory_mixed_b)";
            String persistEventName = "test_event (persisted)";
            String liveEventName = "test_event (live)";

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            CompletionWaiter msgComplete = new CompletionWaiter();
            channel.publish(persistEventName, messageText, msgComplete);

            /* wait for the history to be persisted */
            try {
                Thread.sleep(16000);
            } catch(InterruptedException ignored) {}

            /* publish to the channel */
            msgComplete = new CompletionWaiter();
            channel.publish(liveEventName, messageText, msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.success);

            /* get the history for this channel */
            PaginatedResult<Message> messages = channel.history(null);
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 2 messages", messages.items().length, 2);

            /* verify message contents */
            assertEquals("Expect correct message event", messages.items()[0].name, liveEventName);
            assertEquals("Expect correct message text", messages.items()[0].data, messageText);
            assertEquals("Expect correct message event", messages.items()[1].name, persistEventName);
            assertEquals("Expect correct message text", messages.items()[1].data, messageText);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("single_history_binary: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Send a single message on a channel, wait enough time for it to
     * persist, then send a second message. Verify that both can be
     * retrieved using channel.history(direction=forwards) without any
     * further wait.
     */
    @Test
    public void channelhistory_mixed_f() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new RealtimeClient(opts);
            String channelName = "persisted:channelhistory_mixed_f_" + testParams.name;
            String messageText = "Test message (channelhistory_mixed_f)";
            String persistEventName = "test_event (persisted)";
            String liveEventName = "test_event (live)";

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            CompletionWaiter msgComplete = new CompletionWaiter();
            channel.publish(persistEventName, messageText, msgComplete);

            /* wait for the history to be persisted */
            try {
                Thread.sleep(16000);
            } catch(InterruptedException ignored) {}

            /* publish to the channel */
            msgComplete = new CompletionWaiter();
            channel.publish(liveEventName, messageText, msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.success);

            /* get the history for this channel */
            PaginatedResult<Message> messages = channel.history(new Param[]{ new Param("direction", "forwards") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 2 messages", messages.items().length, 2);

            /* verify message contents */
            assertEquals("Expect correct message event", messages.items()[0].name, persistEventName);
            assertEquals("Expect correct message text", messages.items()[0].data, messageText);
            assertEquals("Expect correct message event", messages.items()[1].name, liveEventName);
            assertEquals("Expect correct message text", messages.items()[1].data, messageText);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("single_history_binary: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Publish events, get limited history and check expected order (forwards)
     */
    @Test
    public void channelhistory_limit_f() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new RealtimeClient(opts);
            String channelName = "persisted:channelhistory_limit_f_" + testParams.name;

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            CompletionSet msgComplete = new CompletionSet();
            for(int i = 0; i < 50; i++) {
                try {
                    channel.publish("history" + i, String.valueOf(i), msgComplete.add());
                } catch(AblyException e) {
                    e.printStackTrace();
                    fail("channelhistory_limit_f: Unexpected exception");
                    return;
                }
            }

            /* wait for the publish callbacks to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

            /* get the history for this channel */
            PaginatedResult<Message> messages = channel.history(new Param[] { new Param("direction", "forwards"), new Param("limit", "25") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 25 messages", messages.items().length, 25);
            HashMap<String, Message> messageContents = new HashMap<String, Message>();
            for(Message message : messages.items())
                messageContents.put(message.name, message);
            /* verify message order */
            Message[] expectedMessageHistory = new Message[25];
            for(int i = 0; i < 25; i++)
                expectedMessageHistory[i] = messageContents.get("history" + i);
            Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("channelhistory_limit_f: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Publish events, get limited history and check expected order (backwards)
     */
    @Test
    public void channelhistory_limit_b() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new RealtimeClient(opts);
            String channelName = "persisted:channelhistory_limit_b_" + testParams.name;

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            CompletionSet msgComplete = new CompletionSet();
            for(int i = 0; i < 50; i++) {
                try {
                    channel.publish("history" + i, String.valueOf(i), msgComplete.add());
                } catch(AblyException e) {
                    e.printStackTrace();
                    fail("channelhistory_limit_b: Unexpected exception");
                    return;
                }
            }

            /* wait for the publish callbacks to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

            /* get the history for this channel */
            PaginatedResult<Message> messages = channel.history(new Param[] { new Param("direction", "backwards"), new Param("limit", "25") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 25 messages", messages.items().length, 25);
            HashMap<String, Message> messageContents = new HashMap<String, Message>();
            for(Message message : messages.items())
                messageContents.put(message.name, message);

            /* verify message order */
            Message[] expectedMessageHistory = new Message[25];
            for(int i = 0; i < 25; i++)
                expectedMessageHistory[i] = messageContents.get("history" + (49 - i));
            Assert.assertArrayEquals("Expect messages in backward order", messages.items(), expectedMessageHistory);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("channelhistory_limit_b: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Publish events and check expected history based on time slice (forwards)
     */
    @Test
    public void channelhistory_time_f() {
        RealtimeClient ably = null;
        try {
            /* first, publish some messages */
            long intervalStart = 0, intervalEnd = 0;
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new RealtimeClient(opts);
            String channelName = "persisted:channelhistory_time_f_" + testParams.name;

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* send batches of messages with shprt inter-message delay */
            CompletionSet msgComplete = new CompletionSet();
            for(int i = 0; i < 20; i++) {
                channel.publish("history" + i, String.valueOf(i), msgComplete.add());
                Thread.sleep(100L);
            }
            Thread.sleep(1000L);
            intervalStart = timeOffset + System.currentTimeMillis();
            for(int i = 20; i < 40; i++) {
                channel.publish("history" + i, String.valueOf(i), msgComplete.add());
                Thread.sleep(100L);
            }
            intervalEnd = timeOffset + System.currentTimeMillis() - 1;
            Thread.sleep(1000L);
            for(int i = 40; i < 60; i++) {
                channel.publish("history" + i, String.valueOf(i), msgComplete.add());
                Thread.sleep(100L);
            }

            /* wait for message callbacks */
            msgComplete.waitFor();

            /* get the history for this channel */
            PaginatedResult<Message> messages = channel.history(new Param[] {
                new Param("direction", "forwards"),
                new Param("start", String.valueOf(intervalStart - 500)),
                new Param("end", String.valueOf(intervalEnd + 500))
            });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 20 messages", messages.items().length, 20);
            HashMap<String, Message> messageContents = new HashMap<String, Message>();
            for(Message message : messages.items())
                messageContents.put(message.name, message);
            /* verify message order */
            Message[] expectedMessageHistory = new Message[20];
            for(int i = 20; i < 40; i++)
                expectedMessageHistory[i - 20] = messageContents.get("history" + i);
            Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);
        } catch (AblyException | InterruptedException e) {
            e.printStackTrace();
            fail("channelhistory_time_f: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Publish events and check expected history based on time slice (backwards)
     */
    @Test
    public void channelhistory_time_b() {
        RealtimeClient ably = null;
        try {
            /* first, publish some messages */
            long intervalStart = 0, intervalEnd = 0;
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new RealtimeClient(opts);
            String channelName = "persisted:channelhistory_time_b_" + testParams.name;

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* send batches of messages with shprt inter-message delay */
            CompletionSet msgComplete = new CompletionSet();
            for(int i = 0; i < 20; i++) {
                channel.publish("history" + i, String.valueOf(i), msgComplete.add());
                Thread.sleep(100L);
            }
            Thread.sleep(1000L);
            intervalStart = timeOffset + System.currentTimeMillis();
            for(int i = 20; i < 40; i++) {
                channel.publish("history" + i, String.valueOf(i), msgComplete.add());
                Thread.sleep(100L);
            }
            intervalEnd = timeOffset + System.currentTimeMillis() - 1;
            Thread.sleep(1000L);
            for(int i = 40; i < 60; i++) {
                channel.publish("history" + i, String.valueOf(i), msgComplete.add());
                Thread.sleep(100L);
            }

            /* wait for message callbacks */
            msgComplete.waitFor();

            /* get the history for this channel */
            PaginatedResult<Message> messages = channel.history(new Param[] {
                new Param("direction", "backwards"),
                new Param("start", String.valueOf(intervalStart - 500)),
                new Param("end", String.valueOf(intervalEnd + 500))
            });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 20 messages", messages.items().length, 20);
            HashMap<String, Message> messageContents = new HashMap<String, Message>();
            for(Message message : messages.items())
                messageContents.put(message.name, message);
            /* verify message order */
            Message[] expectedMessageHistory = new Message[20];
            for(int i = 20; i < 40; i++)
                expectedMessageHistory[i - 20] = messageContents.get("history" + (59 - i));
            Assert.assertArrayEquals("Expect messages in backwards order", messages.items(), expectedMessageHistory);
        } catch (AblyException | InterruptedException e) {
            e.printStackTrace();
            fail("channelhistory_time_b: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Check query pagination (forwards)
     */
    @Test
    public void channelhistory_paginate_f() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new RealtimeClient(opts);
            String channelName = "persisted:channelhistory_paginate_f_" + testParams.name;

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            CompletionSet msgComplete = new CompletionSet();
            for(int i = 0; i < 50; i++) {
                try {
                    channel.publish("history" + i, String.valueOf(i), msgComplete.add());
                } catch(AblyException e) {
                    e.printStackTrace();
                    fail("channelhistory_paginate_f: Unexpected exception");
                    return;
                }
            }

            /* wait for the publish callbacks to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

            /* get the history for this channel */
            PaginatedResult<Message> messages = channel.history(new Param[] { new Param("direction", "forwards"), new Param("limit", "10") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* log all messages */
            HashMap<String, Message> messageContents = new HashMap<String, Message>();
            for(Message message : messages.items())
                messageContents.put(message.name, message);

            /* verify message order */
            Message[] expectedMessageHistory = new Message[10];
            for(int i = 0; i < 10; i++)
                expectedMessageHistory[i] = messageContents.get("history" + i);
            Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

            /* get next page */
            messages = messages.next();
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* log all messages */
            for(Message message : messages.items())
                messageContents.put(message.name, message);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                expectedMessageHistory[i] = messageContents.get("history" + (i + 10));
            Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

            /* get next page */
            messages = messages.next();
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* log all messages */
            for(Message message : messages.items())
                messageContents.put(message.name, message);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                expectedMessageHistory[i] = messageContents.get("history" + (i + 20));
            Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("channelhistory_paginate_f: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Check query pagination (backwards)
     */
    @Test
    public void channelhistory_paginate_b() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new RealtimeClient(opts);
            String channelName = "persisted:channelhistory_paginate_b_" + testParams.name;

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            CompletionSet msgComplete = new CompletionSet();
            for(int i = 0; i < 50; i++) {
                try {
                    channel.publish("history" + i, String.valueOf(i), msgComplete.add());
                } catch(AblyException e) {
                    e.printStackTrace();
                    fail("channelhistory_paginate_b: Unexpected exception");
                    return;
                }
            }

            /* wait for the publish callbacks to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

            /* get the history for this channel */
            PaginatedResult<Message> messages = channel.history(new Param[] { new Param("direction", "backwards"), new Param("limit", "10") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* log all messages */
            HashMap<String, Message> messageContents = new HashMap<String, Message>();
            for(Message message : messages.items())
                messageContents.put(message.name, message);

            /* verify message order */
            Message[] expectedMessageHistory = new Message[10];
            for(int i = 0; i < 10; i++)
                expectedMessageHistory[i] = messageContents.get("history" + (49 - i));
            Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

            /* get next page */
            messages = messages.next();
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* log all messages */
            for(Message message : messages.items())
                messageContents.put(message.name, message);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                expectedMessageHistory[i] = messageContents.get("history" + (39 - i));
            Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

            /* get next page */
            messages = messages.next();
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* log all messages */
            for(Message message : messages.items())
                messageContents.put(message.name, message);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                expectedMessageHistory[i] = messageContents.get("history" + (29 - i));
            Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("channelhistory_paginate_b: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Check query pagination "rel=first" (forwards)
     */
    @Test
    public void channelhistory_paginate_first_f() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new RealtimeClient(opts);
            String channelName = "persisted:channelhistory_paginate_first_f_" + testParams.name;

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            CompletionSet msgComplete = new CompletionSet();
            for(int i = 0; i < 50; i++) {
                try {
                    channel.publish("history" + i, String.valueOf(i), msgComplete.add());
                } catch(AblyException e) {
                    e.printStackTrace();
                    fail("channelhistory_paginate_f: Unexpected exception");
                    return;
                }
            }

            /* wait for the publish callbacks to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

            /* get the history for this channel */
            PaginatedResult<Message> messages = channel.history(new Param[] { new Param("direction", "forwards"), new Param("limit", "10") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* log all messages */
            HashMap<String, Message> messageContents = new HashMap<String, Message>();
            for(Message message : messages.items())
                messageContents.put(message.name, message);

            /* verify message order */
            Message[] expectedMessageHistory = new Message[10];
            for(int i = 0; i < 10; i++)
                expectedMessageHistory[i] = messageContents.get("history" + i);
            Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

            /* get next page */
            messages = messages.next();
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* log all messages */
            for(Message message : messages.items())
                messageContents.put(message.name, message);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                expectedMessageHistory[i] = messageContents.get("history" + (i + 10));
            Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

            /* get first page */
            messages = messages.first();
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* log all messages */
            for(Message message : messages.items())
                messageContents.put(message.name, message);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                expectedMessageHistory[i] = messageContents.get("history" + i);
            Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("channelhistory_paginate_first_f: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Check query pagination "rel=first" (backwards)
     */
    @Test
    public void channelhistory_paginate_first_b() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new RealtimeClient(opts);
            String channelName = "persisted:channelhistory_paginate_first_b_" + testParams.name;

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            CompletionSet msgComplete = new CompletionSet();
            for(int i = 0; i < 50; i++) {
                try {
                    channel.publish("history" + i, String.valueOf(i), msgComplete.add());
                } catch(AblyException e) {
                    e.printStackTrace();
                    fail("channelhistory_paginate_f: Unexpected exception");
                    return;
                }
            }

            /* wait for the publish callbacks to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

            /* get the history for this channel */
            PaginatedResult<Message> messages = channel.history(new Param[] { new Param("direction", "backwards"), new Param("limit", "10") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* log all messages */
            HashMap<String, Message> messageContents = new HashMap<String, Message>();
            for(Message message : messages.items())
                messageContents.put(message.name, message);

            /* verify message order */
            Message[] expectedMessageHistory = new Message[10];
            for(int i = 0; i < 10; i++)
                expectedMessageHistory[i] = messageContents.get("history" + (49 - i));
            Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

            /* get next page */
            messages = messages.next();
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* log all messages */
            for(Message message : messages.items())
                messageContents.put(message.name, message);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                expectedMessageHistory[i] = messageContents.get("history" + (39 - i));
            Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

            /* get first page */
            messages = messages.first();
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* log all messages */
            for(Message message : messages.items())
                messageContents.put(message.name, message);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                expectedMessageHistory[i] = messageContents.get("history" + (49 - i));
            Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("channelhistory_paginate_first_b: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Connect twice to the service, each using the default (binary) protocol.
     * Publish messages on one connection to a given channel; while in progress,
     * attach the second connection to the same channel and verify a message
     * history up to the point of attachment can be obtained.
     */
    @Test
    @Ignore("Fails due to issues in sandbox. See https://github.com/ably/realtime/issues/1834 for details.")
    public void channelhistory_from_attach() {
        RealtimeClient txAbly = null, rxAbly = null;
        try {
            ClientOptions txOpts = createOptions(testVars.keys[0].keyStr);
            txAbly = new RealtimeClient(txOpts);
            ClientOptions rxOpts = createOptions(testVars.keys[0].keyStr);
            rxAbly = new RealtimeClient(rxOpts);
            String channelName = "persisted:channelhistory_from_attach_" + testParams.name;

            /* create a channel */
            final Channel txChannel = txAbly.channels.get(channelName);
            final Channel rxChannel = rxAbly.channels.get(channelName);

            /* attach sender */
            txChannel.attach();
            (new ChannelWaiter(txChannel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", txChannel.state, ChannelState.attached);

            /* publish messages to the channel */
            final CompletionSet msgComplete = new CompletionSet();
            Thread publisherThread = new Thread() {
                @Override
                public void run() {
                    for(int i = 0; i < 50; i++) {
                        try {
                            txChannel.publish("history" + i, String.valueOf(i), msgComplete.add());
                            try {
                                sleep(100L);
                            } catch(InterruptedException ie) {}
                        } catch(AblyException e) {
                            e.printStackTrace();
                            fail("channelhistory_from_attach: Unexpected exception");
                            return;
                        }
                    }
                }
            };
            publisherThread.start();

            /* wait 2 seconds */
            try {
                Thread.sleep(2000L);
            } catch(InterruptedException ignored) {}

            /* subscribe; this will trigger the attach */
            MessageWaiter messageWaiter =  new MessageWaiter(rxChannel);

            /* get the channel history from the attachSerial when we get the attach indication */
            (new ChannelWaiter(rxChannel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", rxChannel.state, ChannelState.attached);
            assertNotNull("Verify attachSerial provided", rxChannel.properties.attachSerial);

            /* wait for the subscription callback to be called on the first received message */
            messageWaiter.waitFor(1);

            /* wait for the publisher thread to complete */
            try {
                publisherThread.join();
            } catch (InterruptedException e) {
                fail("channelhistory_from_attach: exception in publisher thread");
            }

            /* get the history for this channel */
            PaginatedResult<Message> messages = rxChannel.history(new Param[] { new Param("from_serial", rxChannel.properties.attachSerial)});
            assertNotNull("Expected non-null messages", messages);
            assertTrue("Expected at least one message", messages.items().length >= 1);

            /* verify that the history and received messages meet */
            int earliestReceivedOnConnection = Integer.valueOf((String)messageWaiter.receivedMessages.get(0).data).intValue();
            int latestReceivedInHistory = Integer.valueOf((String)messages.items()[0].data).intValue();
            assertEquals("Verify that the history and received messages meet", earliestReceivedOnConnection, latestReceivedInHistory + 1);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("channelhistory_from_attach: Unexpected exception instantiating library");
        } finally {
            if(txAbly != null)
                txAbly.close();
            if(rxAbly != null)
                rxAbly.close();
        }
    }

    /**
     * Connect twice to the service.
     * Publish messages on one connection to a given channel; while in progress,
     * attach the second connection to the same channel and verify a message
     * history up to the point of attachment can be obtained.
     */
    @Test
    public void channelhistory_until_attach() {
        RealtimeClient txAbly = null, rxAbly = null;
        try {
            ClientOptions txOpts = createOptions(testVars.keys[0].keyStr);
            txAbly = new RealtimeClient(txOpts);
            ClientOptions rxOpts = createOptions(testVars.keys[0].keyStr);
            rxAbly = new RealtimeClient(rxOpts);
            String channelName = "persisted:channelhistory_until_attach_" + testParams.name;

            /* create a channel */
            final Channel txChannel = txAbly.channels.get(channelName);
            final Channel rxChannel = rxAbly.channels.get(channelName);

            /* attach sender */
            txChannel.attach();
            (new ChannelWaiter(txChannel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", txChannel.state, ChannelState.attached);

            /* publish messages to the channel */
            CompletionSet msgComplete = new CompletionSet();
            int messageCount = 25;
            for (int i = 0; i < messageCount; i++) {
                txChannel.publish("history" + i,  String.valueOf(i), msgComplete.add());
            }

            msgComplete.waitFor();

            /* get the channel history from the attachSerial when we get the attach indication */
            rxChannel.attach();
            new ChannelWaiter(rxChannel).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", rxChannel.state, ChannelState.attached);
            assertNotNull("Verify attachSerial provided", rxChannel.properties.attachSerial);

            /* get the history for this channel */
            PaginatedResult<Message> messages = rxChannel.history(new Param[] { new Param("untilAttach", "true") });
            assertNotNull("Expected non-null messages", messages);
            assertTrue("Expected at least one message", messages.items().length >= 1);

            /* verify that the history and received messages meet */
            for (int i = 0; i < messageCount; i++) {
                /* 0 --> "24"
                 * 1 --> "23"
                 * ...
                 * 24 --> "0"
                 */
                String actual = (String) messages.items()[messageCount - 1 - i].data;
                String expected = String.valueOf(i);
                assertThat(actual, is(equalTo(expected)));
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("channelhistory_from_attach: Unexpected exception instantiating library");
        } finally {
            if(txAbly != null)
                txAbly.close();
            if(rxAbly != null)
                rxAbly.close();
        }
    }

    /**
     * Verifies an Exception is thrown, when a channel history is requested
     * with parameter {"untilAttach":"true}" before client is attached to the channel
     *
     * @throws AblyException
     */
    @Test(expected=AblyException.class)
    public void channelhistory_until_attach_before_attached() throws AblyException {
        ClientOptions options = createOptions(testVars.keys[0].keyStr);
        RealtimeClient ably = new RealtimeClient(options);

        ably.channels.get("test").history(new Param[]{ new Param("untilAttach", "true") });
    }

    /**
     * Verifies an Exception is thrown, when a channel history is requested
     * with invalid "untilAttach" parameter value.
     *
     * @throws AblyException
     */
    @Test(expected=AblyException.class)
    public void channelhistory_until_attach_invalid_value() throws AblyException {
        ClientOptions options = createOptions(testVars.keys[0].keyStr);
        RealtimeClient ably = new RealtimeClient(options);

        ably.channels.get("test").history(new Param[]{ new Param("untilAttach", "affirmative")});
    }

    /**
     * Publish enough message to fill 2 pages.
     * Verify that,
     *   - {@code PaginatedQuery#isLast} returns false, when we are at the first page.
     *   - {@code PaginatedQuery#isLast} returns true, when we are at the second page.
     */
    @Test
    public void channelhistory_islast() throws AblyException {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new RealtimeClient(opts);
            String channelName = "persisted:channelhistory_islast_" + testParams.name;
            int pageMessageCount = 10;

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            new ChannelWaiter(channel).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            CompletionSet msgComplete = new CompletionSet();
            for (int i = 0; i < (pageMessageCount * 2 - 1); i++) {
                channel.publish("history" + i, String.valueOf(i), msgComplete.add());
            }

            /* wait for the publish callbacks to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

            /* get the history for this channel */
            PaginatedResult<Message> messages = channel.history(new Param[]{new Param("limit", String.format(Locale.ENGLISH, "%d", pageMessageCount))});
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, pageMessageCount);

            /* Verify that current page is the last */
            assertThat(messages.isLast(), is(false));

            /* get next page */
            messages = messages.next();
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 9 messages", messages.items().length, pageMessageCount - 1);

            /* Verify that current page is the last */
            assertThat(messages.isLast(), is(true));
        } finally {
            if (ably != null)
                ably.close();
        }
    }
}
