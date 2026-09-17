package io.ably.pubsub.test.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.http.Channel;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.Message;
import io.ably.pubsub.types.PaginatedResult;
import io.ably.pubsub.types.Param;

public class HttpChannelHistoryTest extends ParameterizedTest {

    private PubSubHttpClient ably;
    private long timeOffset;

    @Rule
    public Timeout testTimeout = Timeout.seconds(300);

    @Before
    public void setUpBefore() throws Exception {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        opts.useBinaryProtocol = false;
        ably = new PubSubHttpClient(opts);
        long timeFromService = ably.time();
        timeOffset = timeFromService - System.currentTimeMillis();
    }

    /**
     * Publish events with data of various datatypes
     */
    @Test
    public void channelhistory_types() {
        /* first, publish some messages */
        Channel history0 = ably.channels.get("persisted:channelhistory_types_" + UUID.randomUUID().toString() + "_" + testParams.name);
        try {
            history0.publish("history0", "This is a string message payload");
            history0.publish("history1", "This is a byte[] message payload".getBytes());
        } catch(AblyException e) {
            e.printStackTrace();
            fail("channelhistory0: Unexpected exception");
            return;
        }

        /* get the history for this channel */
        try {
            PaginatedResult<Message> messages = history0.history(null);
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
            fail("channelhistory0: Unexpected exception");
            return;
        }
    }

    /**
     * Publish events and check expected order (forwards)
     */
    @Test
    public void channelhistory_multi_50_f() {
        /* first, publish some messages */
        Channel history1 = ably.channels.get("persisted:channelhistory_multi_50_f_" + testParams.name);
        for(int i = 0; i < 50; i++)
        try {
            history1.publish("history" + i,  String.valueOf(i));
        } catch(AblyException e) {
            e.printStackTrace();
            fail("channelhistory1: Unexpected exception");
            return;
        }

        /* get the history for this channel */
        try {
            PaginatedResult<Message> messages = history1.history(new Param[] { new Param("direction", "forwards") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 50 messages", messages.items().length, 50);
            HashMap<String, Message> messageContents = new HashMap<String, Message>();
            for(Message message : messages.items())
                messageContents.put(message.name, message);
            /* verify message order */
            Message[] expectedMessageHistory = new Message[50];
            for(int i = 0; i < 50; i++)
                expectedMessageHistory[i] = messageContents.get("history" + i);
            Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("channelhistory1: Unexpected exception");
            return;
        }
    }

    /**
     * Publish events and check expected order (backwards)
     */
    @Test
    public void channelhistory_multi_50_b() {
        /* first, publish some messages */
        Channel history2 = ably.channels.get("persisted:channelhistory_multi_50_b_" + testParams.name);
        for(int i = 0; i < 50; i++)
        try {
            history2.publish("history" + i,  String.valueOf(i));
        } catch(AblyException e) {
            e.printStackTrace();
            fail("channelhistory2: Unexpected exception");
            return;
        }

        /* get the history for this channel */
        try {
            PaginatedResult<Message> messages = history2.history(new Param[] { new Param("direction", "backwards") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 50 messages", messages.items().length, 50);
            HashMap<String, Message> messageContents = new HashMap<String, Message>();
            for(Message message : messages.items())
                messageContents.put(message.name, message);
            /* verify message order */
            Message[] expectedMessageHistory = new Message[50];
            for(int i = 0; i < 50; i++)
                expectedMessageHistory[i] = messageContents.get("history" +  (49 - i));
            Assert.assertArrayEquals("Expect messages in reverse order", messages.items(), expectedMessageHistory);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("channelhistory2: Unexpected exception");
            return;
        }
    }

    /**
     * Publish events, get limited history and check expected order (forwards)
     */
    @Test
    public void channelhistory_limit_f() {
        /* first, publish some messages */
        Channel history3 = ably.channels.get("persisted:channelhistory_limit_f_" + testParams.name);
        for(int i = 0; i < 50; i++)
        try {
            history3.publish("history" + i,  String.valueOf(i));
        } catch(AblyException e) {
            e.printStackTrace();
            fail("channelhistory3: Unexpected exception");
            return;
        }

        /* get the history for this channel */
        try {
            PaginatedResult<Message> messages = history3.history(new Param[] { new Param("direction", "forwards"), new Param("limit", "25") });
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
            fail("channelhistory3: Unexpected exception");
            return;
        }
    }

    /**
     * Publish events, get limited history and check expected order (backwards)
     */
    @Test
    public void channelhistory_limit_b() {
        /* first, publish some messages */
        Channel history4 = ably.channels.get("persisted:channelhistory_limit_b_" + testParams.name);
        for(int i = 0; i < 50; i++)
        try {
            history4.publish("history" + i,  String.valueOf(i));
        } catch(AblyException e) {
            e.printStackTrace();
            fail("channelhistory4: Unexpected exception");
            return;
        }

        /* get the history for this channel */
        try {
            PaginatedResult<Message> messages = history4.history(new Param[] { new Param("direction", "backwards"), new Param("limit", "25") });
            assertNotNull("Expected non-null messages", messages);
            assertEquals("Expected 25 messages", messages.items().length, 25);
            HashMap<String, Message> messageContents = new HashMap<String, Message>();
            for(Message message : messages.items())
                messageContents.put(message.name, message);
            /* verify message order */
            Message[] expectedMessageHistory = new Message[25];
            for(int i = 0; i < 25; i++)
                expectedMessageHistory[i] = messageContents.get("history" + (49 - i));
            Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("channelhistory4: Unexpected exception");
            return;
        }
    }

    /**
     * Publish events and check expected history based on time slice (forwards)
     */
    @Test
    @Ignore("Fails in sandbox due to timing issues")
    public void channelhistory_time_f() {
        /* first, publish some messages */
        long intervalStart = 0, intervalEnd = 0;
        Channel history5 = ably.channels.get("persisted:channelhistory_time_f_" + UUID.randomUUID().toString() + "_" + testParams.name);
        /* send batches of messages with short inter-message delay */
        try {
            for(int i = 0; i < 20; i++) {
                history5.publish("history" + i, String.valueOf(i));
                Thread.sleep(100L);
            }
            Thread.sleep(1000L);
            intervalStart = timeOffset + System.currentTimeMillis();
            for(int i = 20; i < 40; i++) {
                history5.publish("history" + i, String.valueOf(i));
                Thread.sleep(100L);
            }
            intervalEnd = timeOffset + System.currentTimeMillis() - 1;
            Thread.sleep(1000L);
            for(int i = 40; i < 60; i++) {
                history5.publish("history" + i, String.valueOf(i));
                Thread.sleep(100L);
            }
        } catch(AblyException e) {
            e.printStackTrace();
            fail("channelhistory1: Unexpected exception");
            return;
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail("channelhistory1: Unexpected exception");
        }

        /* get the history for this channel */
        try {
            PaginatedResult<Message> messages = history5.history(new Param[] {
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
        } catch (AblyException e) {
            e.printStackTrace();
            fail("channelhistory5: Unexpected exception");
            return;
        }
    }

    /**
     * Publish events and check expected history based on time slice (backwards)
     */
    @Test
    public void channelhistory_time_b() {
        /* first, publish some messages */
        long intervalStart = 0, intervalEnd = 0;
        Channel history6 = ably.channels.get("persisted:channelhistory_time_b_" + testParams.name);
        /* send batches of messages with shprt inter-message delay */
        try {
            for(int i = 0; i < 20; i++) {
                history6.publish("history" + i,  String.valueOf(i));
                Thread.sleep(100L);
            }
            Thread.sleep(1000L);
            intervalStart = timeOffset + System.currentTimeMillis();
            for(int i = 20; i < 40; i++) {
                history6.publish("history" + i,  String.valueOf(i));
                Thread.sleep(100L);
            }
            intervalEnd = timeOffset + System.currentTimeMillis() - 1;
            Thread.sleep(1000L);
            for(int i = 40; i < 60; i++) {
                history6.publish("history" + i,  String.valueOf(i));
                Thread.sleep(100L);
            }
        } catch(AblyException e) {
            e.printStackTrace();
            fail("channelhistory6: Unexpected exception");
            return;
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail("channelhistory6: Unexpected exception");
        }

        /* get the history for this channel */
        try {
            PaginatedResult<Message> messages = history6.history(new Param[] {
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
        } catch (AblyException e) {
            e.printStackTrace();
            fail("channelhistory6: Unexpected exception");
            return;
        }
    }

    /**
     * Check query pagination (forwards)
     */
    @Test
    public void channelhistory_paginate_f() {
        /* first, publish some messages */
        Channel history3 = ably.channels.get("persisted:channelhistory_paginate_f_" + testParams.name);
        for(int i = 0; i < 50; i++)
        try {
            history3.publish("history" + i,  String.valueOf(i));
        } catch(AblyException e) {
            e.printStackTrace();
            fail("channelhistory3: Unexpected exception");
            return;
        }

        /* get the history for this channel */
        try {
            PaginatedResult<Message> messages = history3.history(new Param[] { new Param("direction", "forwards"), new Param("limit", "10") });
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
            fail("channelhistory3: Unexpected exception");
            return;
        }
    }

    /**
     * Check query pagination (backwards)
     */
    @Test
    public void channelhistory_paginate_b() {
        /* first, publish some messages */
        Channel history3 = ably.channels.get("persisted:channelhistory_paginate_b_" + testParams.name);
        for(int i = 0; i < 50; i++)
        try {
            history3.publish("history" + i,  String.valueOf(i));
        } catch(AblyException e) {
            e.printStackTrace();
            fail("channelhistory3: Unexpected exception");
            return;
        }

        /* get the history for this channel */
        try {
            PaginatedResult<Message> messages = history3.history(new Param[] { new Param("direction", "backwards"), new Param("limit", "10") });
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
            fail("channelhistory3: Unexpected exception");
            return;
        }
    }

    /**
     * Check query pagination "rel=first" (forwards)
     */
    @Test
    public void channelhistory_paginate_first_f() {
        /* first, publish some messages */
        Channel history3 = ably.channels.get("persisted:channelhistory_paginate_first_f_" + testParams.name);
        for(int i = 0; i < 50; i++)
        try {
            history3.publish("history" + i,  String.valueOf(i));
        } catch(AblyException e) {
            e.printStackTrace();
            fail("channelhistory3: Unexpected exception");
            return;
        }

        /* get the history for this channel */
        try {
            PaginatedResult<Message> messages = history3.history(new Param[] { new Param("direction", "forwards"), new Param("limit", "10") });
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
            fail("channelhistory3: Unexpected exception");
            return;
        }
    }

    /**
     * Check query pagination "rel=first" (backwards)
     */
    @Test
    public void channelhistory_paginate_first_b() {
        /* first, publish some messages */
        Channel history3 = ably.channels.get("persisted:channelhistory_paginate_first_b_" + testParams.name);
        for(int i = 0; i < 50; i++)
        try {
            history3.publish("history" + i,  String.valueOf(i));
        } catch(AblyException e) {
            e.printStackTrace();
            fail("channelhistory3: Unexpected exception");
            return;
        }

        /* get the history for this channel */
        try {
            PaginatedResult<Message> messages = history3.history(new Param[] { new Param("direction", "backwards"), new Param("limit", "10") });
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
            fail("channelhistory3: Unexpected exception");
            return;
        }
    }
}
