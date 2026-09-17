package io.ably.pubsub.test.realtime;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import io.ably.pubsub.debug.DebugOptions;
import io.ably.pubsub.realtime.RealtimeClient;
import io.ably.pubsub.realtime.Channel;
import io.ably.pubsub.realtime.ChannelState;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.http.Auth;
import io.ably.pubsub.http.Auth.TokenParams;
import io.ably.pubsub.test.common.Helpers.ChannelWaiter;
import io.ably.pubsub.test.common.Helpers.CompletionSet;
import io.ably.pubsub.test.common.Helpers.CompletionWaiter;
import io.ably.pubsub.test.common.Helpers.PresenceWaiter;
import io.ably.pubsub.test.common.Helpers.RawProtocolMonitor;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.PaginatedResult;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.types.PresenceMessage;
import io.ably.pubsub.types.ProtocolMessage;
import io.ably.pubsub.types.ProtocolMessage.Action;

import java.util.Locale;

@Ignore("FIXME: fix ably exception")
public class RealtimePresenceHistoryTest extends ParameterizedTest {

    private static final String testClientId = "testClientId";
    private long timeOffset;
    private PubSubHttpClient rest;
    private Auth.TokenDetails token;

    @Rule
    public Timeout testTimeout = Timeout.seconds(300);

    @Before
    public void setUpBefore() throws Exception {
        /* create tokens for specific clientIds */
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        rest = new PubSubHttpClient(opts);
        token = rest.auth.requestToken(new TokenParams() {{ clientId = testClientId; }}, null);

        /* sync */
        long timeFromService = rest.time();
        timeOffset = timeFromService - System.currentTimeMillis();
    }

    /**
     * Send a single message on a channel and verify that it can be
     * retrieved using channel.history() without needing to wait for
     * it to be persisted.
     */
    @Test
    public void presencehistory_simple() {
        RealtimeClient ably = null;
        try {
            ClientOptions rtOpts = createOptions();
            rtOpts.token = token.token;
            rtOpts.clientId = testClientId;
            ably = new RealtimeClient(rtOpts);

            String channelName = "persisted:presencehistory_simple_" + testParams.name;
            String messageText = "Test message (presencehistory_simple)";

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* enter the channel */
            CompletionWaiter msgComplete = new CompletionWaiter();
            channel.presence.enter(messageText, msgComplete);

            /* wait for the enter callback to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.success);

            /* get the presence history for this channel */
            PaginatedResult<PresenceMessage> messages = channel.presence.history(null);
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
     * Publish events with data of various datatypes
     */
    @Test
    public void presencehistory_types() {
        RealtimeClient ably = null;
        try {
            ClientOptions rtOpts = createOptions();
            rtOpts.token = token.token;
            rtOpts.clientId = testClientId;
            ably = new RealtimeClient(rtOpts);

            String channelName = "persisted:presencehistory_types_" + testParams.name;

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish enter events to the channel */
            CompletionWaiter msgComplete = new CompletionWaiter();

            channel.presence.enter("This is a string message payload", msgComplete);
            channel.presence.enter("This is a byte[] message payload".getBytes(), msgComplete);

            /* wait for the enter callback to be called */
            msgComplete.waitFor(2);
            assertTrue("Verify success callback was called", msgComplete.success);

            /* get the history for this channel */
            PaginatedResult<PresenceMessage> messages = channel.presence.history(null);
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 2 messages", messages.items().length, 2);

            /* verify message contents and order */
            assertEquals("Expect messages.asArray()[1] to be expected String", messages.items()[1].data, "This is a string message payload");
            assertEquals("Expect messages.asArray()[0] to be expected byte[]", new String((byte[])messages.items()[0].data), "This is a byte[] message payload");
        } catch (AblyException e) {
            e.printStackTrace();
            fail("presencehistory_types: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Publish events with data of various datatypes
     */
    @Test
    public void presencehistory_types_forward() {
        RealtimeClient ably = null;
        try {
            ClientOptions rtOpts = createOptions();
            rtOpts.token = token.token;
            rtOpts.clientId = testClientId;
            ably = new RealtimeClient(rtOpts);

            String channelName = "persisted:presencehistory_types_forward_" + testParams.name;

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish enter events to the channel */
            CompletionWaiter msgComplete = new CompletionWaiter();

            channel.presence.enter("This is a string message payload", msgComplete);
            channel.presence.enter("This is a byte[] message payload".getBytes(), msgComplete);

            /* wait for the enter callback to be called */
            msgComplete.waitFor(2);
            assertTrue("Verify success callback was called", msgComplete.success);

            /* get the history for this channel */
            PaginatedResult<PresenceMessage> messages = channel.presence.history(new Param[]{new Param("direction", "forwards")});
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 2 messages", messages.items().length, 2);

            /* verify message contents and order */
            assertEquals("Expect messages.asArray()[0] to be expected String", messages.items()[0].data, "This is a string message payload");
            assertEquals("Expect messages.asArray()[1] to be expected byte[]", new String((byte[])messages.items()[1].data), "This is a byte[] message payload");
        } catch (AblyException e) {
            e.printStackTrace();
            fail("presencehistory_types: Unexpected exception");
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
    public void presencehistory_second_channel() {
        RealtimeClient txAbly = null, rxAbly = null;
        try {
            ClientOptions txOpts = createOptions();
            txOpts.token = token.token;
            txOpts.clientId = testClientId;
            txAbly = new RealtimeClient(txOpts);

            ClientOptions rxOpts = createOptions(testVars.keys[0].keyStr);
            rxAbly = new RealtimeClient(rxOpts);
            String channelName = "persisted:presencehistory_second_channel_" + testParams.name;

            /* create a channel */
            final Channel txChannel = txAbly.channels.get(channelName);
            final Channel rxChannel = rxAbly.channels.get(channelName);

            /* attach sender */
            txChannel.attach();
            (new ChannelWaiter(txChannel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", txChannel.state, ChannelState.attached);

            /* enter the channel */
            String messageText = "Test message (presencehistory_second_channel)";
            CompletionWaiter msgComplete = new CompletionWaiter();
            txChannel.presence.enter(messageText, msgComplete);

            /* wait for the enter callback to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.success);

            /* attach receiver */
            rxChannel.attach();
            (new ChannelWaiter(rxChannel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", rxChannel.state, ChannelState.attached);

            /* get the history for this channel */
            PaginatedResult<PresenceMessage> messages = rxChannel.presence.history(null);
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 1 message", messages.items().length, 1);

            /* verify message contents */
            assertEquals("Expect correct message text", messages.items()[0].data, messageText);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("presencehistory_second_channel: Unexpected exception instantiating library");
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
    public void presencehistory_wait_b() {
        RealtimeClient ably = null;
        try {
            ClientOptions rtOpts = createOptions();
            rtOpts.token = token.token;
            rtOpts.clientId = testClientId;
            ably = new RealtimeClient(rtOpts);
            String channelName = "persisted:presencehistory_wait_b_" + testParams.name;
            String messageText = "Test message (presencehistory_wait_b)";

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* enter the channel */
            CompletionWaiter msgComplete = new CompletionWaiter();
            channel.presence.enter(messageText, msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.success);

            /* wait for the history to be persisted */
            try {
                Thread.sleep(16000);
            } catch(InterruptedException ie) {}

            /* get the history for this channel */
            PaginatedResult<PresenceMessage> messages = channel.presence.history(null);
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
    public void presencehistory_wait_f() {
        RealtimeClient ably = null;
        try {
            ClientOptions rtOpts = createOptions();
            rtOpts.token = token.token;
            rtOpts.clientId = testClientId;
            ably = new RealtimeClient(rtOpts);
            String channelName = "persisted:presencehistory_wait_f_" + testParams.name;
            String messageText = "Test message (presencehistory_wait_f)";

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            CompletionWaiter msgComplete = new CompletionWaiter();
            channel.presence.enter(messageText, msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.success);

            /* wait for the history to be persisted */
            try {
                Thread.sleep(16000);
            } catch(InterruptedException ie) {}

            /* get the history for this channel */
            PaginatedResult<PresenceMessage> messages = channel.presence.history(new Param[]{ new Param("direction", "forwards") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 1 message", messages.items().length, 1);

            /* verify message contents */
            assertEquals("Expect correct message text", messages.items()[0].data, messageText);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("presencehistory_wait_binary_f: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Send a single presence message on a channel, wait enough time for it to
     * persist, then send a second message. Verify that both can be
     * retrieved using channel.history() without any further wait.
     */
    @Test
    public void presencehistory_mixed_b() {
        RealtimeClient ably = null;
        try {
            ClientOptions rtOpts = createOptions();
            rtOpts.token = token.token;
            rtOpts.clientId = testClientId;
            ably = new RealtimeClient(rtOpts);
            String channelName = "persisted:presencehistory_mixed_b_" + testParams.name;
            String persistMessageText = "test_event (persisted)";
            String liveMessageText = "test_event (live)";

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            CompletionWaiter msgComplete = new CompletionWaiter();
            channel.presence.enter(persistMessageText, msgComplete);

            /* wait for the history to be persisted */
            try {
                Thread.sleep(16000);
            } catch(InterruptedException ie) {}

            /* publish to the channel */
            channel.presence.enter(liveMessageText, msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor(2);
            assertTrue("Verify success callback was called", msgComplete.success);

            /* get the history for this channel */
            PaginatedResult<PresenceMessage> messages = channel.presence.history(null);
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 2 messages", messages.items().length, 2);

            /* verify message contents */
            assertEquals("Expect correct message data", messages.items()[0].data, liveMessageText);
            assertEquals("Expect correct message data", messages.items()[1].data, persistMessageText);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("presencehistory_mixed_binary_b: Unexpected exception instantiating library");
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
    public void presencehistory_mixed_f() {
        RealtimeClient ably = null;
        try {
            ClientOptions rtOpts = createOptions();
            rtOpts.token = token.token;
            rtOpts.clientId = testClientId;
            ably = new RealtimeClient(rtOpts);
            String channelName = "persisted:presencehistory_mixed_f_" + testParams.name;
            String persistMessageText = "test_event (persisted)";
            String liveMessageText = "test_event (live)";

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            CompletionWaiter msgComplete = new CompletionWaiter();
            channel.presence.enter(persistMessageText, msgComplete);

            /* wait for the history to be persisted */
            try {
                Thread.sleep(16000);
            } catch(InterruptedException ie) {}

            /* publish to the channel */
            channel.presence.enter(liveMessageText, msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor(2);
            assertTrue("Verify success callback was called", msgComplete.success);

            /* get the history for this channel */
            PaginatedResult<PresenceMessage> messages = channel.presence.history(new Param[]{ new Param("direction", "forwards") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 2 messages", messages.items().length, 2);

            /* verify message contents */
            assertEquals("Expect correct message data", messages.items()[0].data, persistMessageText);
            assertEquals("Expect correct message data", messages.items()[1].data, liveMessageText);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("presencehistory_mixed_binary_f: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Publish events, get limited history and check expected order (forwards)
     */
    @Test
    public void presencehistory_limit_f() {
        RealtimeClient ably = null;
        try {
            ClientOptions rtOpts = createOptions();
            rtOpts.token = token.token;
            rtOpts.clientId = testClientId;
            ably = new RealtimeClient(rtOpts);
            String channelName = "persisted:presencehistory_limit_f_" + testParams.name;

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
                    channel.presence.enter(String.valueOf(i), msgComplete.add());
                } catch(AblyException e) {
                    e.printStackTrace();
                    fail("presencehistory_limit_f: Unexpected exception");
                    return;
                }
            }

            /* wait for the publish callbacks to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

            /* get the history for this channel */
            PaginatedResult<PresenceMessage> messages = channel.presence.history(new Param[] { new Param("direction", "forwards"), new Param("limit", "25") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 25 messages", messages.items().length, 25);

            /* verify message order */
            for(int i = 0; i < 25; i++)
                assertEquals("Expect correct message data", messages.items()[i].data, String.valueOf(i));

        } catch (AblyException e) {
            e.printStackTrace();
            fail("presencehistory_limit_f: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Publish events, get limited history and check expected order (backwards)
     */
    @Test
    public void presencehistory_limit_b() {
        RealtimeClient ably = null;
        try {
            ClientOptions rtOpts = createOptions();
            rtOpts.token = token.token;
            rtOpts.clientId = testClientId;
            ably = new RealtimeClient(rtOpts);
            String channelName = "persisted:presencehistory_limit_b_" + testParams.name;

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
                    channel.presence.enter(String.valueOf(i), msgComplete.add());
                } catch(AblyException e) {
                    e.printStackTrace();
                    fail("presencehistory_limit_b: Unexpected exception");
                    return;
                }
            }

            /* wait for the publish callbacks to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

            /* get the history for this channel */
            PaginatedResult<PresenceMessage> messages = channel.presence.history(new Param[] { new Param("direction", "backwards"), new Param("limit", "25") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 25 messages", messages.items().length, 25);

            /* verify message order */
            for(int i = 0; i < 25; i++)
                assertEquals("Expect correct message data", messages.items()[i].data, String.valueOf(49 - i));

        } catch (AblyException e) {
            e.printStackTrace();
            fail("presencehistory_limit_b: Unexpected exception");
            return;
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Publish events and check expected history based on time slice (forwards)
     */
    @Test
    public void presencehistory_time_f() {
        RealtimeClient ably = null;
        try {
            /* first, publish some messages */
            long intervalStart = 0, intervalEnd = 0;
            ClientOptions rtOpts = createOptions();
            rtOpts.token = token.token;
            rtOpts.clientId = testClientId;
            ably = new RealtimeClient(rtOpts);
            String channelName = "persisted:presencehistory_time_f_" + testParams.name;

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* send batches of messages with short inter-message delay */
            CompletionSet msgComplete = new CompletionSet();
            for(int i = 0; i < 20; i++) {
                channel.presence.enter(String.valueOf(i), msgComplete.add());
                Thread.sleep(100L);
            }
            Thread.sleep(1000L);
            intervalStart = timeOffset + System.currentTimeMillis();
            for(int i = 20; i < 40; i++) {
                channel.presence.enter(String.valueOf(i), msgComplete.add());
                Thread.sleep(100L);
            }
            intervalEnd = timeOffset + System.currentTimeMillis() - 1;
            Thread.sleep(1000L);
            for(int i = 40; i < 60; i++) {
                channel.presence.enter(String.valueOf(i), msgComplete.add());
                Thread.sleep(100L);
            }

            /* get the history for this channel */
            PaginatedResult<PresenceMessage> messages = channel.presence.history(new Param[] {
                new Param("direction", "forwards"),
                new Param("start", String.valueOf(intervalStart - 500)),
                new Param("end", String.valueOf(intervalEnd + 500))
            });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 20 messages", messages.items().length, 20);

            /* verify message order */
            for(int i = 20; i < 40; i++)
                assertEquals("Expect correct message data", messages.items()[i - 20].data, String.valueOf(i));

        } catch (AblyException e) {
            e.printStackTrace();
            fail("presencehistory_time_f: Unexpected exception");
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail("presencehistory_time_f: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Publish events and check expected history based on time slice (backwards)
     */
    @Test
    public void presencehistory_time_b() {
        RealtimeClient ably = null;
        try {
            /* first, publish some messages */
            long intervalStart = 0, intervalEnd = 0;
            ClientOptions rtOpts = createOptions();
            rtOpts.token = token.token;
            rtOpts.clientId = testClientId;
            ably = new RealtimeClient(rtOpts);
            String channelName = "persisted:presencehistory_time_b_" + testParams.name;

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* send batches of messages with shprt inter-message delay */
            CompletionSet msgComplete = new CompletionSet();
            for(int i = 0; i < 20; i++) {
                channel.presence.enter(String.valueOf(i), msgComplete.add());
                Thread.sleep(100L);
            }
            Thread.sleep(1000L);
            intervalStart = timeOffset + System.currentTimeMillis();
            for(int i = 20; i < 40; i++) {
                channel.presence.enter(String.valueOf(i), msgComplete.add());
                Thread.sleep(100L);
            }
            intervalEnd = timeOffset + System.currentTimeMillis() - 1;
            Thread.sleep(1000L);
            for(int i = 40; i < 60; i++) {
                channel.presence.enter(String.valueOf(i), msgComplete.add());
                Thread.sleep(100L);
            }

            /* get the history for this channel */
            PaginatedResult<PresenceMessage> messages = channel.presence.history(new Param[] {
                new Param("direction", "backwards"),
                new Param("start", String.valueOf(intervalStart - 500)),
                new Param("end", String.valueOf(intervalEnd + 500))
            });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 20 messages", messages.items().length, 20);

            /* verify message order */
            for(int i = 20; i < 40; i++)
                assertEquals("Expect correct message data", messages.items()[i - 20].data, String.valueOf(59 - i));

        } catch (AblyException e) {
            e.printStackTrace();
            fail("presencehistory_time_b: Unexpected exception");
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail("presencehistory_time_b: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Check query pagination (forwards)
     */
    @Test
    public void presencehistory_paginate_f() {
        RealtimeClient ably = null;
        try {
            ClientOptions rtOpts = createOptions();
            rtOpts.token = token.token;
            rtOpts.clientId = testClientId;
            ably = new RealtimeClient(rtOpts);
            String channelName = "persisted:presencehistory_paginate_f_" + testParams.name;

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
                    channel.presence.enter(String.valueOf(i), msgComplete.add());
                } catch(AblyException e) {
                    e.printStackTrace();
                    fail("presencehistory_paginate_f: Unexpected exception");
                    return;
                }
            }

            /* wait for the publish callbacks to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

            /* get the history for this channel */
            PaginatedResult<PresenceMessage> messages = channel.presence.history(new Param[] { new Param("direction", "forwards"), new Param("limit", "10") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                assertEquals("Expect correct message data", messages.items()[i].data, String.valueOf(i));

            /* get next page */
            messages = messages.next();
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                assertEquals("Expect correct message data", messages.items()[i].data, String.valueOf(i + 10));

            /* get next page */
            messages = messages.next();
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                assertEquals("Expect correct message data", messages.items()[i].data, String.valueOf(i + 20));

        } catch (AblyException e) {
            e.printStackTrace();
            fail("presencehistory_paginate_f: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Check query pagination (backwards)
     */
    @Test
    public void presencehistory_paginate_b() {
        RealtimeClient ably = null;
        try {
            ClientOptions rtOpts = createOptions();
            rtOpts.token = token.token;
            rtOpts.clientId = testClientId;
            ably = new RealtimeClient(rtOpts);
            String channelName = "persisted:presencehistory_paginate_b_" + testParams.name;

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
                    channel.presence.enter(String.valueOf(i), msgComplete.add());
                } catch(AblyException e) {
                    e.printStackTrace();
                    fail("presencehistory_paginate_f: Unexpected exception");
                    return;
                }
            }

            /* wait for the publish callbacks to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

            /* get the history for this channel */
            PaginatedResult<PresenceMessage> messages = channel.presence.history(new Param[] { new Param("direction", "backwards"), new Param("limit", "10") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                assertEquals("Expect correct message data", messages.items()[i].data, String.valueOf(49 - i));

            /* get next page */
            messages = messages.next();
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                assertEquals("Expect correct message data", messages.items()[i].data, String.valueOf(39 - i));

            /* get next page */
            messages = messages.next();
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                assertEquals("Expect correct message data", messages.items()[i].data, String.valueOf(29 - i));

        } catch (AblyException e) {
            e.printStackTrace();
            fail("presencehistory_paginate_b: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Check query pagination "rel=first" (forwards)
     */
    @Test
    public void presencehistory_paginate_first_f() {
        RealtimeClient ably = null;
        try {
            ClientOptions rtOpts = createOptions();
            rtOpts.token = token.token;
            rtOpts.clientId = testClientId;
            ably = new RealtimeClient(rtOpts);
            String channelName = "persisted:presencehistory_paginate_first_f_" + testParams.name;

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
                    channel.presence.enter(String.valueOf(i), msgComplete.add());
                } catch(AblyException e) {
                    e.printStackTrace();
                    fail("presencehistory_paginate_f: Unexpected exception");
                    return;
                }
            }

            /* wait for the publish callbacks to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

            /* get the history for this channel */
            PaginatedResult<PresenceMessage> messages = channel.presence.history(new Param[] { new Param("direction", "forwards"), new Param("limit", "10") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                assertEquals("Expect correct message data", messages.items()[i].data, String.valueOf(i));

            /* get next page */
            messages = messages.next();
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                assertEquals("Expect correct message data", messages.items()[i].data, String.valueOf(i + 10));

            /* get first page */
            messages = messages.first();
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                assertEquals("Expect correct message data", messages.items()[i].data, String.valueOf(i));

        } catch (AblyException e) {
            e.printStackTrace();
            fail("presencehistory_paginate_first_f: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Check query pagination "rel=first" (backwards)
     */
    @Test
    public void presencehistory_paginate_first_b() {
        RealtimeClient ably = null;
        try {
            ClientOptions rtOpts = createOptions();
            rtOpts.token = token.token;
            rtOpts.clientId = testClientId;
            ably = new RealtimeClient(rtOpts);
            String channelName = "persisted:presencehistory_paginate_first_b_" + testParams.name;

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
                    channel.presence.enter(String.valueOf(i), msgComplete.add());
                } catch(AblyException e) {
                    e.printStackTrace();
                    fail("presencehistory_paginate_f: Unexpected exception");
                    return;
                }
            }

            /* wait for the publish callbacks to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

            /* get the history for this channel */
            PaginatedResult<PresenceMessage> messages = channel.presence.history(new Param[] { new Param("direction", "backwards"), new Param("limit", "10") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                assertEquals("Expect correct message data", messages.items()[i].data, String.valueOf(49 - i));

            /* get next page */
            messages = messages.next();
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                assertEquals("Expect correct message data", messages.items()[i].data, String.valueOf(39 - i));

            /* get first page */
            messages = messages.first();
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 10 messages", messages.items().length, 10);

            /* verify message order */
            for(int i = 0; i < 10; i++)
                assertEquals("Expect correct message data", messages.items()[i].data, String.valueOf(49 - i));

        } catch (AblyException e) {
            e.printStackTrace();
            fail("presencehistory_paginate_first_b: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Connect twice to the service.
     * Publish messages on one connection to a given channel; while in progress,
     * attach the second connection to the same channel and verify a message
     * history up to the point of attachment can be obtained.
     */
    @Test
    @Ignore("Fails due to issues in sandbox. See https://github.com/ably/realtime/issues/1845 for details.")
    public void presencehistory_from_attach() {
        RealtimeClient txAbly = null, rxAbly = null;
        try {
            ClientOptions txOpts = createOptions();
            txOpts.token = token.token;
            txOpts.clientId = testClientId;
            txAbly = new RealtimeClient(txOpts);

            DebugOptions rxOpts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(rxOpts);
            RawProtocolMonitor rawPresenceWaiter = RawProtocolMonitor.createReceiver(Action.presence);
            rxOpts.protocolListener = rawPresenceWaiter;
            rxAbly = new RealtimeClient(rxOpts);
            String channelName = "persisted:presencehistory_from_attach_" + testParams.name;

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
                            txChannel.presence.enter(String.valueOf(i), msgComplete.add());
                            try {
                                sleep(100L);
                            } catch(InterruptedException ie) {}
                        } catch(AblyException e) {
                            e.printStackTrace();
                            fail("presencehistory_from_attach: Unexpected exception");
                            return;
                        }
                    }
                    msgComplete.waitFor();
                }
            };
            publisherThread.start();

            /* wait 2 seconds */
            try {
                Thread.sleep(2000L);
            } catch(InterruptedException ie) {
                fail("presencehistory_from_attach: exception in publisher thread");
            }

            /* subscribe; this will trigger the attach */
            PresenceWaiter presenceWaiter =  new PresenceWaiter(rxChannel);

            /* get the channel history from the attachSerial when we get the attach indication */
            (new ChannelWaiter(rxChannel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", rxChannel.state, ChannelState.attached);
            assertNotNull("Verify attachSerial provided", rxChannel.properties.attachSerial);

            /* the subscription callback will be called first on the "sync" presence message
             * delivered immediately following attach; so wait for this and then the first
             * "realtime" message to be received */
            presenceWaiter.waitFor(2);
            PresenceMessage firstReceivedRealtimeMessage = null;
            for(ProtocolMessage msg : rawPresenceWaiter.receivedMessages) {
                if(msg.channelSerial != null) {
                    firstReceivedRealtimeMessage = msg.presence[0];
                    break;
                }
            }

            /* wait for the end of the tx thread */
            try {
                publisherThread.join();
            } catch (InterruptedException e) {}
            assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

            /* get the history for this channel */
            PaginatedResult<PresenceMessage> messages = rxChannel.presence.history(new Param[] { new Param("from_serial", rxChannel.properties.attachSerial)});
            assertNotNull("Expected non-null messages", messages);
            assertTrue("Expected at least one message", messages.items().length >= 1);

            /* verify that the history and received messages meet */
            int earliestReceivedOnConnection = Integer.valueOf((String)firstReceivedRealtimeMessage.data);
            int latestReceivedInHistory = Integer.valueOf((String)messages.items()[0].data);
            assertEquals("Verify that the history and received messages meet", earliestReceivedOnConnection, latestReceivedInHistory + 1);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("presencehistory_from_attach: Unexpected exception instantiating library");
        } finally {
            if(txAbly != null)
                txAbly.close();
            if(rxAbly != null)
                rxAbly.close();
        }
    }

    /**
     * Connect twice to the service, each using the default (binary) protocol.
     * Publish messages on one connection to a given channel; while in progress,
     * attach the second connection to the same channel and verify a message
     * history up to the point of attachment can be obtained.
     */
    @Test
    public void presencehistory_until_attach() {
        RealtimeClient txAbly = null, rxAbly = null;
        try {
            ClientOptions txOpts = createOptions();
            txOpts.token = token.token;
            txOpts.clientId = testClientId;
            txAbly = new RealtimeClient(txOpts);

            DebugOptions rxOpts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(rxOpts);
            RawProtocolMonitor rawPresenceWaiter = RawProtocolMonitor.createReceiver(Action.presence);
            rxOpts.protocolListener = rawPresenceWaiter;
            rxAbly = new RealtimeClient(rxOpts);
            String channelName = "persisted:presencehistory_until_attach_" + testParams.name;

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
                txChannel.presence.enter(String.valueOf(i), msgComplete.add());
            }

            msgComplete.waitFor();

            /* get the channel history from the attachSerial when we get the attach indication */
            rxChannel.attach();
            new ChannelWaiter(rxChannel).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", rxChannel.state, ChannelState.attached);
            assertNotNull("Verify attachSerial provided", rxChannel.properties.attachSerial);

            /* get the history for this channel */
            PaginatedResult<PresenceMessage> messages = rxChannel.presence.history(new Param[] { new Param("untilAttach", "true") });
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
            fail("presencehistory_from_attach: Unexpected exception instantiating library");
        } finally {
            if(txAbly != null)
                txAbly.close();
            if(rxAbly != null)
                rxAbly.close();
        }
    }

    /**
     * Verifies an Exception is thrown, when a presence history is requested
     * with parameter {"untilAttach":"true}" before client is attached to the channel
     *
     * @throws AblyException
     */
    @Test(expected=AblyException.class)
    public void presencehistory_until_attach_before_attached() throws AblyException {
        ClientOptions options = createOptions(testVars.keys[0].keyStr);
        RealtimeClient ably = new RealtimeClient(options);

        ably.channels.get("test").presence.history(new Param[]{ new Param("untilAttach", "true")});
        ably.close();
    }

    /**
     * Verifies an Exception is thrown, when a presence history is requested
     * with invalid "untilAttach" parameter value.
     *
     * @throws AblyException
     */
    @Test(expected=AblyException.class)
    public void presencehistory_until_attach_invalid_value() throws AblyException {
        ClientOptions options = createOptions(testVars.keys[0].keyStr);
        RealtimeClient ably = new RealtimeClient(options);

        ably.channels.get("test").presence.history(new Param[]{ new Param("untilAttach", "affirmative")});
        ably.close();
    }

    /**
     * Publish enough presence to fill 2 pages.
     * Verify that,
     *   - {@code PaginatedQuery#isLast} returns false, when we are at the first page.
     *   - {@code PaginatedQuery#isLast} returns true, when we are at the second page.
     */
    @Test
    public void presencehistory_islast() throws AblyException {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.token = token.token;
            opts.clientId = testClientId;
            ably = new RealtimeClient(opts);
            String channelName = "persisted:presencehistory_islast_" + testParams.name;
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
                channel.presence.update(String.valueOf(i), msgComplete.add());
            }

            /* wait for the publish callbacks to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

            /* get the history for this channel */
            PaginatedResult<PresenceMessage> messages = channel.presence.history(new Param[]{new Param("limit", String.format(Locale.ENGLISH, "%d", pageMessageCount))});
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected " + pageMessageCount + " messages", messages.items().length, pageMessageCount);

            /* Verify that current page is not the last */
            assertThat(messages.isLast(), is(false));

            /* get next page */
            messages = messages.next();
            --pageMessageCount;
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected " + pageMessageCount + " messages", messages.items().length, pageMessageCount);

            /* Verify that current page is the last */
            assertThat(messages.isLast(), is(true));
        } finally {
            if (ably != null)
                ably.close();
        }
    }
}
