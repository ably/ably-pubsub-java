package io.ably.pubsub.test.realtime;

import io.ably.pubsub.debug.DebugOptions;
import io.ably.pubsub.realtime.RealtimeClient;
import io.ably.pubsub.realtime.RealtimeClientFactory;
import io.ably.pubsub.realtime.Channel;
import io.ably.pubsub.realtime.Channel.MessageListener;
import io.ably.pubsub.realtime.ChannelEvent;
import io.ably.pubsub.realtime.ChannelState;
import io.ably.pubsub.realtime.ChannelStateListener;
import io.ably.pubsub.realtime.ConnectionEvent;
import io.ably.pubsub.realtime.CompletionListener;
import io.ably.pubsub.realtime.ConnectionState;
import io.ably.pubsub.test.common.Helpers;
import io.ably.pubsub.test.common.Helpers.ChannelWaiter;
import io.ably.pubsub.test.common.Helpers.ConnectionWaiter;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.test.util.MockWebsocketFactory;
import io.ably.pubsub.transport.ConnectionManager;
import io.ably.pubsub.transport.Defaults;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ChannelMode;
import io.ably.pubsub.types.ChannelOptions;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.types.Message;
import io.ably.pubsub.types.ProtocolMessage;
import io.ably.pubsub.util.Log;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RealtimeChannelTest extends ParameterizedTest {

    private Comparator<Message> messageComparator = new Comparator<Message>() {
        @Override
        public int compare(Message o1, Message o2) {
            int result = o1.name.compareTo(o2.name);
            return (result == 0)?(((String) o1.data).compareTo((String) o2.data)):(result);
        }
    };


    /**
     * Connect to the service and attach to a channel,
     * confirming that the attached state is reached.
     */
    @Test
    public void attach() {
        String channelName = "attach_" + testParams.name;
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Connect to the service using the default (binary) protocol
     * and attach before the connected state is reached.
     */
    @Test
    public void attach_before_connect() {
        String channelName = "attach_before_connect_" + testParams.name;
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Connect to the service using the default (binary) protocol
     * and attach, then detach
     */
    @Test
    public void attach_detach() {
        String channelName = "attach_detach_" + testParams.name;
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* detach */
            channel.detach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.detached);
            assertEquals("Verify detached state reached", channel.state, ChannelState.detached);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    @Test
    public void attach_with_channel_params_channels_get() {
        String channelName = "attach_with_channel_params_channels_get_" + testParams.name;
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ConnectionState.connected, ably.connection.state);

            ChannelOptions options = new ChannelOptions();
            options.params = new HashMap<>();
            options.params.put("modes", "subscribe");
            options.params.put("delta", "vcdiff");

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName, options);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", ChannelState.attached, channel.state);
            assertEquals("Verify channel params", options.params, channel.getParams());
            assertArrayEquals("Verify channel modes", new ChannelMode[] { ChannelMode.subscribe }, channel.getModes());
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    @Test
    public void attach_with_channel_params_set_options() {
        String channelName = "attach_with_channel_params_set_options_" + testParams.name;
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ConnectionState.connected, ably.connection.state);

            ChannelOptions options = new ChannelOptions();
            options.params = new HashMap<>();
            options.params.put("modes", "subscribe");
            options.params.put("delta", "vcdiff");

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName);
            channel.setOptions(options);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", ChannelState.attached, channel.state);
            assertEquals("Verify channel params", options.params, channel.getParams());
            assertArrayEquals("Verify channel modes", new ChannelMode[] { ChannelMode.subscribe }, channel.getModes());
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    @Test
    public void channels_get_should_throw_when_would_cause_reattach() {
        String channelName = "channels_get_should_throw_when_would_cause_reattach_" + testParams.name;
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ConnectionState.connected, ably.connection.state);

            ChannelOptions options = new ChannelOptions() {{
                    params = new HashMap<>();
                    params.put("modes", "subscribe");
                    params.put("delta", "vcdiff");
            }};

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName, options);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);

            RealtimeClient finalAbly = ably;
            AblyException exception = assertThrows(AblyException.class, () ->
                finalAbly.channels.get(channelName, options));

            assertEquals("Verify error code", 400, exception.errorInfo.code);
            assertEquals("Verify error status code", 40000, exception.errorInfo.statusCode);
            assertTrue("Verify error message", exception.errorInfo.message.contains("use Channel.setOptions()"));

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    @Test
    public void attach_with_channel_params_modes_and_channel_modes() {
        String channelName = "attach_with_channel_params_modes_and_channel_modes_" + testParams.name;
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ConnectionState.connected, ably.connection.state);

            ChannelOptions options = new ChannelOptions();
            options.params = new HashMap<String, String>();
            options.params.put("modes", "presence,subscribe");
            options.modes = new ChannelMode[] {
                ChannelMode.publish,
                ChannelMode.presence_subscribe
            };

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName, options);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", ChannelState.attached, channel.state);
            assertEquals("Verify channel params", channel.getParams(), options.params);
            // modes in params overrides individual modes in options
            assertThat("Verify channel modes", Arrays.asList(channel.getModes()),
                Matchers.containsInAnyOrder(ChannelMode.presence, ChannelMode.subscribe));

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    @Test
    public void attach_with_channel_modes() {
        String channelName = "attach_with_channel_modes_" + testParams.name;
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ConnectionState.connected, ably.connection.state);

            ChannelOptions options = new ChannelOptions();
            options.modes = new ChannelMode[] {
                ChannelMode.publish,
                ChannelMode.presence_subscribe,
            };

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName, options);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", ChannelState.attached, channel.state);
            assertEquals(2, channel.getModes().length);

            assertThat("Verify channel modes",
                Arrays.asList(channel.getModes()),
                Matchers.containsInAnyOrder(ChannelMode.publish, ChannelMode.presence_subscribe));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    @Test
    public void attach_with_params_delta_and_channel_modes() {
        String channelName = "attach_with_params_delta_and_channel_modes_" + testParams.name;
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ConnectionState.connected, ably.connection.state);

            ChannelOptions options = new ChannelOptions();
            options.params = new HashMap<>();
            options.params.put("delta", "vcdiff");
            options.modes = new ChannelMode[] {
                ChannelMode.publish,
                ChannelMode.subscribe,
                ChannelMode.presence_subscribe,
            };

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName, options);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", ChannelState.attached, channel.state);

            assertEquals("Verify channel params", options.params, channel.getParams());
            assertThat("Verify channel modes",
                Arrays.asList(channel.getModes()),
                Matchers.containsInAnyOrder(ChannelMode.publish, ChannelMode.subscribe, ChannelMode.presence_subscribe));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Connect to the service and attach, then subscribe and unsubscribe
     */
    @Test
    public void subscribe_unsubscribe() {
        String channelName = "subscribe_unsubscribe_" + testParams.name;
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* subscribe */
            MessageListener testListener = new MessageListener() {
                @Override
                public void onMessage(Message message) {
                }};
            channel.subscribe("test_event", testListener);
            /* unsubscribe */
            channel.unsubscribe("test_event", testListener);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * <p>
     * Validates a client can subscribe to messages without implicit channel attach
     * Refer Spec TB4, RTL7g, RTL7h
     * </p>
     * @throws AblyException
     */
    @Test
    public void subscribe_without_implicit_attach() {
        String channelName = "subscribe_" + testParams.name;
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* create a channel and set attachOnSubscribe to false */
            final Channel channel = ably.channels.get(channelName);
            ChannelOptions chOpts = new ChannelOptions();
            chOpts.attachOnSubscribe = false;
            channel.setOptions(chOpts);

            List<Boolean> receivedMsg = Collections.synchronizedList(new ArrayList<>());

            /* Check for all subscriptions without ATTACHING state */
            channel.subscribe(message -> receivedMsg.add(true));
            assertEquals(ChannelState.initialized, channel.state);

            channel.subscribe("test_event", message -> receivedMsg.add(true));
            assertEquals(ChannelState.initialized, channel.state);

            channel.subscribe(new String[]{"test_event1", "test_event2"}, message -> receivedMsg.add(true));
            assertEquals(ChannelState.initialized, channel.state);

            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);

            channel.publish("test_event", "hi there");
            // Expecting two msg: one from the wildcard subscription and one from test_event subscription
            Exception conditionError = new Helpers.ConditionalWaiter().
                wait(() -> receivedMsg.size() == 2, 5000);
            assertNull(conditionError);

            receivedMsg.clear();
            channel.publish("test_event1", "hi there");
            // Expecting two msg: one from the wildcard subscription and one from test_event1 subscription
            conditionError = new Helpers.ConditionalWaiter().
                wait(() -> receivedMsg.size() == 2, 5000);
            assertNull(conditionError);

            receivedMsg.clear();
            channel.publish("test_event2", "hi there");
            // Expecting two msg: one from the wildcard subscription and one from test_event2 subscription
            conditionError = new Helpers.ConditionalWaiter().
                wait(() -> receivedMsg.size() == 2, 5000);
            assertNull(conditionError);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("subscribe_without_implicit_attach: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * <p>
     * Verifies that unsubscribe call with no argument removes all listeners,
     * and any of the previously subscribed listeners doesn't receive any message
     * after that.
     * </p>
     * <p>
     * Spec: RTL8a
     * </p>
     */
    @Test
    public void unsubscribe_all() throws AblyException {
        /* Ably instance that will emit messages */
        RealtimeClient ably1 = null;
        /* Ably instance that will receive messages */
        RealtimeClient ably2 = null;

        String channelName = "test.channel.unsubscribe.all" + System.currentTimeMillis();
        Message[] messages = new Message[] {
                new Message("name1", "Lorem ipsum dolor sit amet"),
                new Message("name2", "Consectetur adipiscing elit."),
                new Message("name3", "Pellentesque nulla lorem"),
                new Message("name4", "Efficitur ac consequat a, commodo ut orci."),
        };

        try {
            ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
            option1.clientId = "emitter client";
            ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
            option2.clientId = "receiver client";

            ably1 = RealtimeClientFactory.create(option1);
            ably2 = RealtimeClientFactory.create(option2);

            Channel channel1 = ably1.channels.get(channelName);
            channel1.attach();
            new ChannelWaiter(channel1).waitFor(ChannelState.attached);

            Channel channel2 = ably2.channels.get(channelName);
            channel2.attach();
            new ChannelWaiter(channel2).waitFor(ChannelState.attached);

            /* Create a listener that collect received messages */
            ArrayList<Message> receivedMessageStack = new ArrayList<>();
            MessageListener listener = new MessageListener() {
                List<Message> messageStack;

                @Override
                public void onMessage(Message message) {
                    messageStack.add(message);
                }

                public MessageListener setMessageStack(List<Message> messageStack) {
                    this.messageStack = messageStack;
                    return this;
                }
            }.setMessageStack(receivedMessageStack);

            /* Subscribe using various alternatives of {@code Channel#subscribe()} */
            channel2.subscribe(listener);
            channel2.subscribe(messages[0].name, listener);
            channel2.subscribe(new String[] {messages[1].name, messages[2].name}, listener);

            /* Unsubscribe */
            channel2.unsubscribe();

            /* Start emitting channel with ably client 1 (emitter) */
            Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
            channel1.publish(messages, waiter);
            waiter.waitFor();

            /* Validate that we didn't received anything
             */
            assertThat(receivedMessageStack, Matchers.is(Matchers.emptyCollectionOf(Message.class)));
        } finally {
            if (ably1 != null) ably1.close();
            if (ably2 != null) ably2.close();
        }
    }

    /**
     * <p>
     * Validates channel removes a subscriber,
     * when {@code Channel#unsubscribe()} gets called with a listener argument.
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void unsubscribe_single() throws AblyException {
        /* Ably instance that will emit messages */
        RealtimeClient ably1 = null;
        /* Ably instance that will receive messages */
        RealtimeClient ably2 = null;

        String channelName = "test.channel.unsubscribe.single" + System.currentTimeMillis();
        Message[] messages = new Message[] {
                new Message("name1", "Lorem ipsum dolor sit amet"),
                new Message("name2", "Consectetur adipiscing elit."),
                new Message("name3", "Pellentesque nulla lorem"),
                new Message("name4", "Efficitur ac consequat a, commodo ut orci."),
        };

        try {
            ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
            option1.clientId = "emitter client";
            ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
            option2.clientId = "receiver client";

            ably1 = RealtimeClientFactory.create(option1);
            ably2 = RealtimeClientFactory.create(option2);

            Channel channel1 = ably1.channels.get(channelName);
            channel1.attach();
            new ChannelWaiter(channel1).waitFor(ChannelState.attached);

            Channel channel2 = ably2.channels.get(channelName);
            channel2.attach();
            new ChannelWaiter(channel2).waitFor(ChannelState.attached);

            /* Create a listener that collect received messages */
            ArrayList<Message> receivedMessageStack = new ArrayList<>();
            MessageListener listener = new MessageListener() {
                List<Message> messageStack;

                @Override
                public void onMessage(Message message) {
                    messageStack.add(message);
                }

                public MessageListener setMessageStack(List<Message> messageStack) {
                    this.messageStack = messageStack;
                    return this;
                }
            }.setMessageStack(receivedMessageStack);

            /* Subscribe using various alternatives of {@code Channel#subscribe()} */
            channel2.subscribe(listener);
            channel2.subscribe(messages[0].name, listener);
            channel2.subscribe(new String[] {messages[1].name, messages[2].name}, listener);

            /* Unsubscribe */
            channel2.unsubscribe(listener);

            /* Start emitting channel with ably client 1 (emitter) */
            Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
            channel1.publish(messages, waiter);
            waiter.waitFor();

            /* Validate that we didn't received anything
             */
            assertThat(receivedMessageStack, Matchers.is(Matchers.emptyCollectionOf(Message.class)));
        } finally {
            if (ably1 != null) ably1.close();
            if (ably2 != null) ably2.close();
        }
    }

    /**
     * <p>
     * Validates a client can observe channel messages of other client,
     * when they entered to the same channel and observing client subscribed
     * to all messages.
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void subscribe_all() throws AblyException {
        /* Ably instance that will emit channel messages */
        RealtimeClient ably1 = null;
        /* Ably instance that will receive channel messages */
        RealtimeClient ably2 = null;

        String channelName = "test.channel.subscribe.all" + System.currentTimeMillis();
        Message[] messages = new Message[]{
                new Message("name1", "Lorem ipsum dolor sit amet,"),
                new Message("name2", "Consectetur adipiscing elit."),
                new Message("name3", "Pellentesque nulla lorem.")
        };

        try {
            ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
            option1.clientId = "emitter client";
            ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
            option2.clientId = "receiver client";

            ably1 = RealtimeClientFactory.create(option1);
            ably2 = RealtimeClientFactory.create(option2);

            Channel channel1 = ably1.channels.get(channelName);
            channel1.attach();
            new ChannelWaiter(channel1).waitFor(ChannelState.attached);

            Channel channel2 = ably2.channels.get(channelName);
            channel2.attach();
            new ChannelWaiter(channel2).waitFor(ChannelState.attached);

            /* Create a listener that collects received messages */
            ArrayList<Message> receivedMessageStack = new ArrayList<>();
            MessageListener listener = new MessageListener() {
                List<Message> messageStack;

                @Override
                public void onMessage(Message message) {
                    messageStack.add(message);
                }

                public MessageListener setMessageStack(List<Message> messageStack) {
                    this.messageStack = messageStack;
                    return this;
                }
            }.setMessageStack(receivedMessageStack);

            channel2.subscribe(listener);

            /* Start emitting channel with ably client 1 (emitter) */
            channel1.publish(messages);

            /* Wait until receiver client (ably2) observes {@code }
             * is emitted from emitter client (ably1)
             */
            new Helpers.MessageWaiter(channel2).waitFor(messages.length);

            /* Validate that,
             *  - we received every message that has been published
             */
            assertThat(receivedMessageStack.size(), is(equalTo(messages.length)));

            Collections.sort(receivedMessageStack, messageComparator);
            for (int i = 0; i < messages.length; i++) {
                Message message = messages[i];
                if(Collections.binarySearch(receivedMessageStack, message, messageComparator) < 0) {
                    fail("Unable to find expected message: " + message);
                }
            }
        } finally {
            if (ably1 != null) ably1.close();
            if (ably2 != null) ably2.close();
        }
    }

    /**
     * <p>
     * Validates a client can observe channel messages of other client,
     * when they entered to the same channel and observing client subscribed
     * to multiple messages.
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void subscribe_multiple() throws AblyException {
        /* Ably instance that will emit channel messages */
        RealtimeClient ably1 = null;
        /* Ably instance that will receive channel messages */
        RealtimeClient ably2 = null;

        String channelName = "test.channel.subscribe.multiple" + System.currentTimeMillis();
        Message[] messages = new Message[] {
                new Message("name1", "Lorem ipsum dolor sit amet,"),
                new Message("name2", "Consectetur adipiscing elit."),
                new Message("name3", "Pellentesque nulla lorem.")
        };

        String[] messageNames = new String[] {
                messages[0].name,
                messages[1].name,
                messages[2].name
        };

        try {
            ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
            option1.clientId = "emitter client";
            ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
            option2.clientId = "receiver client";

            ably1 = RealtimeClientFactory.create(option1);
            ably2 = RealtimeClientFactory.create(option2);

            Channel channel1 = ably1.channels.get(channelName);
            channel1.attach();
            new ChannelWaiter(channel1).waitFor(ChannelState.attached);

            Channel channel2 = ably2.channels.get(channelName);
            channel2.attach();
            new ChannelWaiter(channel2).waitFor(ChannelState.attached);

            /* Create a listener that collect received messages */
            ArrayList<Message> receivedMessageStack = new ArrayList<>();
            MessageListener listener = new MessageListener() {
                List<Message> messageStack;

                @Override
                public void onMessage(Message message) {
                    messageStack.add(message);
                }

                public MessageListener setMessageStack(List<Message> messageStack) {
                    this.messageStack = messageStack;
                    return this;
                }
            }.setMessageStack(receivedMessageStack);
            channel2.subscribe(messageNames, listener);

            /* Start emitting channel with ably client 1 (emitter) */
            channel1.publish("nonTrackedMessageName", "This message should be ignore by second client (ably2).");
            channel1.publish(messages);
            channel1.publish("nonTrackedMessageName", "This message should be ignore by second client (ably2).");

            /* Wait until receiver client (ably2) observes {@code Message}
             * on subscribed channel (channel2) emitted by emitter client (ably1)
             */
            new Helpers.MessageWaiter(channel2).waitFor(messages.length + 2);

            /* Validate that,
             *  - we received specific messages
             */
            assertThat(receivedMessageStack.size(), is(equalTo(messages.length)));

            Collections.sort(receivedMessageStack, messageComparator);
            for (int i = 0; i < messages.length; i++) {
                Message message = messages[i];
                if(Collections.binarySearch(receivedMessageStack, message, messageComparator) < 0) {
                    fail("Unable to find expected message: " + message);
                }
            }
        } finally {
            if (ably1 != null) ably1.close();
            if (ably2 != null) ably2.close();
        }
    }

    /**
     * <p>
     * Validates a client can observe channel messages of other client,
     * when they entered to the same channel and observing client subscribed
     * to a single message.
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void subscribe_single() throws AblyException {
        /* Ably instance that will emit channel messages */
        RealtimeClient ably1 = null;
        /* Ably instance that will receive channel messages */
        RealtimeClient ably2 = null;

        String channelName = "test.channel.subscribe.single" + System.currentTimeMillis();
        String messageName = "name";
        Message[] messages = new Message[] {
                new Message(messageName, "Lorem ipsum dolor sit amet,"),
                new Message(messageName, "Consectetur adipiscing elit."),
                new Message(messageName, "Pellentesque nulla lorem.")
        };

        try {
            ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
            option1.clientId = "emitter client";
            ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
            option2.clientId = "receiver client";

            ably1 = RealtimeClientFactory.create(option1);
            ably2 = RealtimeClientFactory.create(option2);

            Channel channel1 = ably1.channels.get(channelName);
            channel1.attach();
            new ChannelWaiter(channel1).waitFor(ChannelState.attached);

            Channel channel2 = ably2.channels.get(channelName);
            channel2.attach();
            new ChannelWaiter(channel2).waitFor(ChannelState.attached);

            ArrayList<Message> receivedMessageStack = new ArrayList<>();
            MessageListener listener = new MessageListener() {
                List<Message> messageStack;

                @Override
                public void onMessage(Message message) {
                    messageStack.add(message);
                }

                public MessageListener setMessageStack(List<Message> messageStack) {
                    this.messageStack = messageStack;
                    return this;
                }
            }.setMessageStack(receivedMessageStack);
            channel2.subscribe(messageName, listener);

            /* Start emitting channel with ably client 1 (emitter) */
            channel1.publish("nonTrackedMessageName", "This message should be ignore by second client (ably2).");
            channel1.publish(messages);
            channel1.publish("nonTrackedMessageName", "This message should be ignore by second client (ably2).");

            /* Wait until receiver client (ably2) observes {@code Message}
             * on subscribed channel (channel2) emitted by emitter client (ably1)
             */
            new Helpers.MessageWaiter(channel2).waitFor(messages.length + 2);

            /* Validate that,
             *  - received same amount of emitted specific message
             *  - received messages are the ones we emitted
             */
            assertThat(receivedMessageStack.size(), is(equalTo(messages.length)));

            Collections.sort(receivedMessageStack, messageComparator);
            for (int i = 0; i < messages.length; i++) {
                Message message = messages[i];
                if(Collections.binarySearch(receivedMessageStack, message, messageComparator) < 0) {
                    fail("Unable to find expected message: " + message);
                }
            }
        } finally {
            if (ably1 != null) ably1.close();
            if (ably2 != null) ably2.close();
        }
    }


    /**
     * Connect to the service using the default (binary) protocol
     * and attempt to attach to a channel with credentials that do
     * not have access, confirming that the failed state is reached.
     */
    @Test
    public void attach_fail() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[1].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("attach_fail");
            channel.attach();
            ErrorInfo fail = (new ChannelWaiter(channel)).waitFor(ChannelState.failed);
            assertEquals("Verify failed state reached", channel.state, ChannelState.failed);
            assertEquals("Verify reason code gives correct failure reason", fail.statusCode, 401);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * When client attaches to a channel successfully, verify
     * attach {@code CompletionListener#onSuccess()} gets called.
     */
    @Test
    public void attach_success_callback() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("attach_success");
            Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
            channel.attach(waiter);
            new ChannelWaiter(channel).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* Verify onSuccess callback gets called */
            waiter.waitFor();
            assertThat(waiter.success, is(true));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Spec: RTL4g
     */
    @Test
    public void attach_success_callback_for_channel_in_failed_state() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("attach_success");
            ChannelWaiter channelWaiter = new ChannelWaiter(channel);
            channel.attach();
            channelWaiter.waitFor(ChannelState.attached);

            // Simulate connection failure
            ably.connection.connectionManager.requestState(
                new ConnectionManager.StateIndication(
                    ConnectionState.failed,
                    new ErrorInfo("Simulated connection failure", 40000)
                )
            );

            // Wait for the channel to reach the failed state
            channelWaiter.waitFor(ChannelState.failed);

            assertNotNull(channel.reason);
            assertEquals("Simulated connection failure", channel.reason.message);

            ably.connect();
            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);

            Helpers.CompletionWaiter attachListener = new Helpers.CompletionWaiter();
            channel.attach(attachListener);

            channelWaiter.waitFor(ChannelState.attaching);
            assertNull(channel.reason);
            channelWaiter.waitFor(ChannelState.attached);

            assertEquals("Verify attached state reached", ChannelState.attached, channel.state);

            /* Verify onSuccess callback gets called */
            attachListener.waitFor();
            assertTrue(attachListener.success);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * When client failed to attach to a channel, verify
     * attach {@code CompletionListener#onError(ErrorInfo)}
     * gets called.
     */
    @Test
    public void attach_fail_callback() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[1].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("attach_fail");
            Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
            channel.attach(waiter);
            ErrorInfo fail = (new ChannelWaiter(channel)).waitFor(ChannelState.failed);
            assertEquals("Verify failed state reached", channel.state, ChannelState.failed);
            assertEquals("Verify reason code gives correct failure reason", fail.statusCode, 401);

            /* Verify error callback gets called with correct status code */
            waiter.waitFor();
            assertThat(waiter.error.statusCode, is(equalTo(401)));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * When client detaches from a channel successfully after initialized state,
     * verify attach {@code CompletionListener#onSuccess()} gets called.
     * Spec: RTL5a
     */
    @Test
    public void detach_success_callback_initialized() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("detach_success");
            assertEquals("Verify failed state reached", channel.state, ChannelState.initialized);

            /* detach */
            Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
            channel.detach(waiter);

            /* Verify onSuccess callback gets called */
            waiter.waitFor();
            assertThat(waiter.success, is(true));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Spec: RTL5j
     */
    @Test
    public void detach_success_callback_on_suspended_state() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("detach_success");
            ChannelWaiter channelWaiter = new ChannelWaiter(channel);
            channel.attach();
            channelWaiter.waitFor(ChannelState.attached);

            ably.connection.connectionManager.requestState(ConnectionState.suspended);

            channelWaiter.waitFor(ChannelState.suspended);
            assertEquals("Verify suspended state reached", ChannelState.suspended, channel.state);

            /* detach */
            Helpers.CompletionWaiter detachWaiter = new Helpers.CompletionWaiter();
            channel.detach(detachWaiter);

            /* Verify onSuccess callback gets called */
            detachWaiter.waitFor();
            assertTrue(detachWaiter.success);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Spec: RTL5b
     */
    @Test
    public void detach_failure_callback_on_failed_state() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("detach_failure");
            ChannelWaiter channelWaiter = new ChannelWaiter(channel);
            channel.attach();
            channelWaiter.waitFor(ChannelState.attached);

            // Simulate connection failure
            ably.connection.connectionManager.requestState(ConnectionState.failed);

            channelWaiter.waitFor(ChannelState.failed);
            assertEquals("Verify failed state reached", ChannelState.failed, channel.state);

            /* detach */
            Helpers.CompletionWaiter detachWaiter = new Helpers.CompletionWaiter();
            channel.detach(detachWaiter);

            /* Verify onSuccess callback gets called */
            detachWaiter.waitFor();
            assertFalse(detachWaiter.success);
            assertNotNull(detachWaiter.error);
            assertEquals("Channel state is failed", detachWaiter.error.message);
            assertEquals(90000, detachWaiter.error.code);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * When connection is in failed or suspended, set error in callback
     * Spec: RTL5g
     */
    @Test
    public void detach_fail_callback_for_connection_invalid_state() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);
            ConnectionWaiter connWaiter = new ConnectionWaiter(ably.connection);

            /* wait until connected */
            connWaiter.waitFor(ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("detach_failure");
            ChannelWaiter channelWaiter = new ChannelWaiter(channel);
            channel.attach();
            channelWaiter.waitFor(ChannelState.attached);

            // Simulate connection closing from outside
            ably.connection.connectionManager.requestState(new ConnectionManager.StateIndication(
                ConnectionState.closing,
                new ErrorInfo("Connection is closing", 80001)
            ));
            /* wait until connection closing */
            connWaiter.waitFor(ConnectionState.closing);

            // channel state is ATTACHED despite closing connection state
            assertEquals(ChannelState.attached, channel.state);

            /* detach */
            Helpers.CompletionWaiter detachWaiter1 = new Helpers.CompletionWaiter();
            channel.detach(detachWaiter1);

            /* Verify onSuccess callback gets called */
            detachWaiter1.waitFor();
            assertFalse(detachWaiter1.success);
            assertNotNull(detachWaiter1.error);
            assertEquals("Connection is closing", detachWaiter1.error.message);
            assertEquals(80001, detachWaiter1.error.code);

            // Simulate connection failure
            ably.connection.connectionManager.requestState(ConnectionState.failed);
            /* wait until connection failed */
            connWaiter.waitFor(ConnectionState.failed);

            // Mock channel state to ATTACHED despite failed connection state
            channelWaiter.waitFor(ChannelState.failed);
            channel.state = ChannelState.attached;
            assertEquals(ChannelState.attached, channel.state);

            /* detach */
            Helpers.CompletionWaiter detachWaiter2 = new Helpers.CompletionWaiter();
            channel.detach(detachWaiter2);

            /* Verify onSuccess callback gets called */
            detachWaiter2.waitFor();
            assertFalse(detachWaiter2.success);
            assertNotNull(detachWaiter2.error);
            assertEquals("Connection failed", detachWaiter2.error.message);
            assertEquals(80000, detachWaiter2.error.code);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * When client detaches from a channel successfully after attached state,
     * verify attach {@code CompletionListener#onSuccess()} gets called.
     */
    @Test
    public void detach_success_callback_attached() throws AblyException {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("detach_success");
            channel.attach();
            new ChannelWaiter(channel).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* detach */
            Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
            channel.detach(waiter);

            /* Verify onSuccess callback gets called */
            waiter.waitFor();
            assertThat(waiter.success, is(true));
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * When client detaches from a channel successfully after detaching state,
     * verify attach {@code CompletionListener#onSuccess()} gets called.
     * Spec: RTL5i
     */
    @Test
    public void detach_success_callback_detaching() throws AblyException {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("detach_success");
            channel.attach();
            new ChannelWaiter(channel).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* detach */
            channel.detach();
            assertEquals("Verify detaching state reached", channel.state, ChannelState.detaching);
            Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
            channel.detach(waiter);

            /* Verify onSuccess callback gets called */
            waiter.waitFor();
            assertThat(waiter.success, is(true));
        } finally {
            if(ably != null)
                ably.close();
        }
    }


    /**
     * When client attaches to a channel in detaching state, verify that attach call will be done after detach
     * response is received
     * verify attach {@code CompletionListener#onSuccess()} gets called.
     */
    // Spec: RTL4h
    // https://github.com/ably/ably-java/issues/885
    @Test
    public void attach_when_channel_in_detaching_state() throws AblyException {
        RealtimeClient ably = null;
        try {
            final DebugOptions opts = createOptions(testVars.keys[0].keyStr);
            final MockWebsocketFactory transportFactory = new MockWebsocketFactory();
            opts.transportFactory = transportFactory;
            opts.logLevel = Log.VERBOSE;
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ConnectionState.connected, ably.connection.state);
            final MockWebsocketFactory.MockWebsocketTransport transport = transportFactory.getCreatedTransport();
            /* create a channel and attach */
            final String channelName = "attach_channel";
            final Channel channel = ably.channels.get(channelName);
            channel.attach();
            new ChannelWaiter(channel).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", ChannelState.attached, channel.state);

            //block detached so we can ensure that we are in detaching state but unblock immediately after assertion
            transportFactory.blockReceiveProcessingAndQueueBlockedMessages(message -> message.action == ProtocolMessage.Action.detached);
            /* detach */
            final Helpers.CompletionWaiter detachCompletionWaiter = new Helpers.CompletionWaiter();
            channel.detach(detachCompletionWaiter);
            assertEquals("Verify detaching state reached", ChannelState.detaching, channel.state);

            //now we can send an attach as we previously blocked detaching
            final Helpers.CompletionWaiter attachCompletionWaiter = new Helpers.CompletionWaiter();
            //attempt to attach while detaching without blocking attached
            channel.attach(attachCompletionWaiter);

            //unblock and let the queued messages arrive
            transportFactory.allowReceiveProcessing(message -> true);

            detachCompletionWaiter.waitFor();
            assertThat(detachCompletionWaiter.success, is(true));
            assertThat(channel.state, is(ChannelState.detached));
            //verify reattach - after detach
            attachCompletionWaiter.waitFor();
            assertThat(attachCompletionWaiter.success,is(true));
            assertThat(channel.state, is(ChannelState.attached));
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * When client detaches from a channel in attaching state, verify that detach call will be done after attach
     * response is received
     * verify attach {@code CompletionListener#onSuccess()} gets called.
     */
    // Spec: RTL5i
    // https://github.com/ably/ably-java/issues/885
    @Test
    public void detach_when_channel_in_attaching_state() throws AblyException {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.logLevel = Log.VERBOSE;
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ConnectionState.connected, ably.connection.state);

            /* create a channel and attach */
            final String channelName = "attach_channel";
            final Channel channel = ably.channels.get(channelName);
            final Helpers.CompletionWaiter attachCompletionWaiter = new Helpers.CompletionWaiter();
            channel.attach(attachCompletionWaiter);
            assertEquals("Verify detaching state reached", ChannelState.attaching, channel.state);
            //immediately start detach operation
            final Helpers.CompletionWaiter detachCompletionWaiter = new Helpers.CompletionWaiter();
            channel.detach(detachCompletionWaiter);

            new ChannelWaiter(channel).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", ChannelState.attached, channel.state);

            //now wait for detach to complete
            (new ChannelWaiter(channel)).waitFor(ChannelState.detached);
            detachCompletionWaiter.waitFor();

            assertThat(detachCompletionWaiter.success,is(true));
            assertThat(channel.state, is(ChannelState.detached));
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * When client detaches from a channel successfully after detached state,
     * verify attach {@code CompletionListener#onSuccess()} gets called.
     */
    @Test
    public void detach_success_callback_detached() throws AblyException {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("detach_success");
            channel.attach();
            new ChannelWaiter(channel).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* detach */
            channel.detach();
            new ChannelWaiter(channel).waitFor(ChannelState.detached);

            Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
            channel.detach(waiter);

            /* Verify onSuccess callback gets called */
            waiter.waitFor();
            assertThat(waiter.success, is(true));
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * <p>
     * Validate publish will succeed without triggering an attach when connected
     * if not already attached
     * </p>
     * <p>
     * Spec: RTL6c1
     * </p>
     *
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void transient_publish_connected() throws AblyException {
        RealtimeClient pubAbly = null, subAbly = null;
        String channelName = "transient_publish_connected_" + testParams.name;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            subAbly = RealtimeClientFactory.create(opts);

            /* wait until connected */
            new ConnectionWaiter(subAbly.connection).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", subAbly.connection.state, ConnectionState.connected);

            /* create a channel and subscribe */
            final Channel subChannel = subAbly.channels.get(channelName);
            Helpers.MessageWaiter messageWaiter = new Helpers.MessageWaiter(subChannel);
            new ChannelWaiter(subChannel).waitFor(ChannelState.attached);

            pubAbly = RealtimeClientFactory.create(opts);
            new ConnectionWaiter(pubAbly.connection).waitFor(ConnectionState.connected);
            Helpers.CompletionWaiter completionWaiter = new Helpers.CompletionWaiter();
            final Channel pubChannel = pubAbly.channels.get(channelName);
            pubChannel.publish("Lorem", "Ipsum!", completionWaiter);
            assertEquals("Verify channel remains in initialized state", pubChannel.state, ChannelState.initialized);

            ErrorInfo errorInfo = completionWaiter.waitFor();
            assertEquals("Verify channel remains in initialized state", pubChannel.state, ChannelState.initialized);

            messageWaiter.waitFor(1);
            assertEquals("Verify expected message received", messageWaiter.receivedMessages.get(0).name, "Lorem");
        } finally {
            if(pubAbly != null) {
                pubAbly.close();
            }
            if(subAbly != null) {
                subAbly.close();
            }
        }
    }

    /**
     * <p>
     * Validate publish will succeed without triggering an attach when connecting
     * if not already attached
     * </p>
     * <p>
     * Spec: RTL6c2
     * </p>
     *
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void transient_publish_connecting() throws AblyException {
        RealtimeClient pubAbly = null, subAbly = null;
        String channelName = "transient_publish_connecting_" + testParams.name;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            subAbly = RealtimeClientFactory.create(opts);

            /* wait until connected */
            new ConnectionWaiter(subAbly.connection).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", subAbly.connection.state, ConnectionState.connected);

            /* create a channel and subscribe */
            final Channel subChannel = subAbly.channels.get(channelName);
            Helpers.ChannelWaiter channelWaiter = new Helpers.ChannelWaiter(subChannel);
            Helpers.MessageWaiter messageWaiter = new Helpers.MessageWaiter(subChannel);
            channelWaiter.waitFor(ChannelState.attached);

            pubAbly = RealtimeClientFactory.create(opts);
            final Channel pubChannel = pubAbly.channels.get(channelName);
            Helpers.CompletionWaiter completionWaiter = new Helpers.CompletionWaiter();
            pubChannel.publish("Lorem", "Ipsum!", completionWaiter);
            assertEquals("Verify channel remains in initialized state", pubChannel.state, ChannelState.initialized);

            ErrorInfo errorInfo = completionWaiter.waitFor();
            assertNull(errorInfo);
            assertEquals("Verify channel remains in initialized state", pubChannel.state, ChannelState.initialized);

            messageWaiter.waitFor(1);
            assertEquals("Verify expected message received", messageWaiter.receivedMessages.get(0).name, "Lorem");
        } finally {
            if(pubAbly != null) {
                pubAbly.close();
            }
            if(subAbly != null) {
                subAbly.close();
            }
        }
    }

    /**
     * <p>
     * Validate publish will fail when connection is failed
     * </p>
     * <p>
     * Spec: RTL6c4
     * </p>
     *
     */
    @Test
    public void transient_publish_connection_failed() {
        RealtimeClient pubAbly = null;
        String channelName = "transient_publish_connection_failed_" + testParams.name;
        try {
            ClientOptions opts = createOptions("not:a.key");
            pubAbly = RealtimeClientFactory.create(opts);
            new ConnectionWaiter(pubAbly.connection).waitFor(ConnectionState.failed);
            assertEquals("Verify failed state reached", pubAbly.connection.state, ConnectionState.failed);

            final Channel pubChannel = pubAbly.channels.get(channelName);
            Helpers.CompletionWaiter completionWaiter = new Helpers.CompletionWaiter();
            try {
                pubChannel.publish("Lorem", "Ipsum!", completionWaiter);
                fail("failed to raise expected exception");
            } catch(AblyException ignored) {
            }
        } catch(AblyException e) {
            fail("unexpected exception");
        } finally {
            if(pubAbly != null) {
                pubAbly.close();
            }
        }
    }

    /**
     * <p>
     * Validate publish will fail when channel is failed
     * </p>
     * <p>
     * Spec: RTL6c4
     * </p>
     *
     */
    @Test
    public void transient_publish_channel_failed() {
        RealtimeClient pubAbly = null;
        String channelName = "transient_publish_channel_failed_" + testParams.name;
        try {
            ClientOptions opts = createOptions(testVars.keys[1].keyStr);
            pubAbly = RealtimeClientFactory.create(opts);
            new ConnectionWaiter(pubAbly.connection).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", pubAbly.connection.state, ConnectionState.connected);

            final Channel pubChannel = pubAbly.channels.get(channelName);
            Helpers.ChannelWaiter channelWaiter = new Helpers.ChannelWaiter(pubChannel);
            pubChannel.attach();
            channelWaiter.waitFor(ChannelState.failed);

            Helpers.CompletionWaiter completionWaiter = new Helpers.CompletionWaiter();
            try {
                pubChannel.publish("Lorem", "Ipsum!", completionWaiter);
                fail("failed to raise expected exception");
            } catch(AblyException e) {
                assertEquals(pubChannel.state, ChannelState.failed);
            }
        } catch(AblyException e) {
            fail("unexpected exception");
        } finally {
            if(pubAbly != null) {
                pubAbly.close();
            }
        }
    }

    /**
     * <p>
     * Validate subscribe will result in an error, when the channel moves
     * to the FAILED state before the operation succeeds
     * </p>
     * <p>
     * Spec: RTL7c
     * </p>
     *
     */
    @Test
    public void attach_implicit_subscribe_fail() throws AblyException {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[1].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and subscribe */
            final Channel channel = ably.channels.get("subscribe_fail");
            channel.subscribe(null);
            assertEquals("Verify attaching state reached", channel.state, ChannelState.attaching);

            ErrorInfo fail = new ChannelWaiter(channel).waitFor(ChannelState.failed);
            assertEquals("Verify failed state reached", channel.state, ChannelState.failed);
            assertEquals("Verify reason code gives correct failure reason", fail.statusCode, 401);
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    @Test
    public void ensure_detach_with_error_does_not_move_to_failed() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("test");
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            ProtocolMessage protoMessage = new ProtocolMessage(ProtocolMessage.Action.detach, "test");
            protoMessage.error = new ErrorInfo("test error", 123);

            ConnectionManager connectionManager = ably.connection.connectionManager;
            connectionManager.onMessage(null, protoMessage);

            /* Because of (RTL13) channel should now be in either attaching or attached state */
            assertNotEquals("channel state shouldn't be failed", channel.state, ChannelState.failed);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    @Test
    public void channel_state_on_connection_suspended() {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);

            ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final String channelName = "test_state_channel";
            final Channel channel = ably.channels.get(channelName);
            channel.attach();

            ChannelWaiter channelWaiter = new ChannelWaiter(channel);
            channelWaiter.waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* switch to suspended state */
            ably.connection.connectionManager.requestState(ConnectionState.suspended);

            channelWaiter.waitFor(ChannelState.suspended);
            assertEquals("Verify suspended state reached", channel.state, ChannelState.suspended);

            /* switch to connected state */
            ably.connection.connectionManager.requestState(ConnectionState.connected);

            channelWaiter.waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* switch to failed state */
            ably.connection.connectionManager.requestState(ConnectionState.failed);

            channelWaiter.waitFor(ChannelState.failed);
            assertEquals("Verify failed state reached", channel.state, ChannelState.failed);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception");
        } finally {
            if (ably != null)
                ably.close();
        }
    }

    /*
     * Establish connection, attach channel, simulate sending attached message
     * from the server, test correct behaviour
     *
     * Tests RTL12
     */
    @Test
    public void channel_server_initiated_attached() throws AblyException {
        RealtimeClient ably = null;
        long oldRealtimeTimeout = Defaults.realtimeRequestTimeout;
        final String channelName = "channel_server_initiated_attach_detach";

        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);

            /* Make test faster */
            Defaults.realtimeRequestTimeout = 1000;
            opts.channelRetryTimeout = 1000;

            ably = RealtimeClientFactory.create(opts);
            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);

            Channel channel = ably.channels.get(channelName);
            ChannelWaiter channelWaiter = new ChannelWaiter(channel);

            channel.attach();
            channelWaiter.waitFor(ChannelState.attached);

            /* Inject attached message as if received from the server */
            ProtocolMessage attachedMessage = new ProtocolMessage() {{
                action = Action.attached;
                channel = channelName;
            }};
            ably.connection.connectionManager.onMessage(null, attachedMessage);

            ChannelStateListener.ChannelStateChange channelUpdateEvent = channelWaiter.waitFor(ChannelEvent.update);
            assertEquals(ChannelEvent.update, channelUpdateEvent.event);
            assertEquals(ChannelState.attached, channelUpdateEvent.previous);
            assertEquals(ChannelState.attached, channelUpdateEvent.current);
            assertFalse(channelUpdateEvent.resumed);
            assertNull(channelUpdateEvent.reason);

        } finally {
            if (ably != null)
                ably.close();
            Defaults.realtimeRequestTimeout = oldRealtimeTimeout;
        }
    }

    /*
     * Establish connection, attach channel, simulate sending detached messages
     * from the server for channel in attached state.
     *
     * Tests RTL13a
     */
    @Test
    public void server_initiated_detach_for_attached_channel() throws AblyException {
        RealtimeClient ably = null;
        long oldRealtimeTimeout = Defaults.realtimeRequestTimeout;
        final String channelName = "channel_server_initiated_detach_for_attached_channel";

        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);

            /* Make test faster */
            Defaults.realtimeRequestTimeout = 1000;
            opts.channelRetryTimeout = 1000;

            ably = RealtimeClientFactory.create(opts);
            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);

            Channel channel = ably.channels.get(channelName);
            ChannelWaiter channelWaiter = new ChannelWaiter(channel);

            channel.attach();
            channelWaiter.waitFor(ChannelState.attached);

            /* Inject detached message as if from the server */
            ProtocolMessage detachedMessage = new ProtocolMessage() {{
                action = Action.detached;
                channel = channelName;
                error = new ErrorInfo("Simulated detach", 40000);
            }};
            ably.connection.connectionManager.onMessage(null, detachedMessage);

            /* Channel should transition to attaching, then to attached */
            ErrorInfo detachErr = channelWaiter.waitFor(ChannelState.attaching);
            Assert.assertNotNull(detachErr);
            Assert.assertEquals(40000, detachErr.code);
            Assert.assertEquals("Simulated detach", detachErr.message);

            channelWaiter.waitFor(ChannelState.attached);

            List<ChannelState> channelStates = channelWaiter.getRecordedStates();
            Assert.assertEquals(4, channelStates.size());
            Assert.assertEquals(ChannelState.attaching, channelStates.get(0));
            Assert.assertEquals(ChannelState.attached, channelStates.get(1));
            Assert.assertEquals(ChannelState.attaching, channelStates.get(2));
            Assert.assertEquals(ChannelState.attached, channelStates.get(3));

        } finally {
            if (ably != null)
                ably.close();
            Defaults.realtimeRequestTimeout = oldRealtimeTimeout;
        }
    }

    /*
     * Establish connection, attach channel, simulate sending detached messages
     * from the server for channel in suspended state.
     *
     * Tests RTL13a
     */
    @Test
    public void server_initiated_detach_for_suspended_channel() throws AblyException {
        RealtimeClient ably = null;
        long oldRealtimeTimeout = Defaults.realtimeRequestTimeout;
        final String channelName = "channel_server_initiated_detach_for_suspended_channel";

        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);

            /* Make test faster */
            Defaults.realtimeRequestTimeout = 1000;
            opts.channelRetryTimeout = 1000;

            ably = RealtimeClientFactory.create(opts);
            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);

            Channel channel = ably.channels.get(channelName);
            ChannelWaiter channelWaiter = new ChannelWaiter(channel);

            channel.attach();
            channelWaiter.waitFor(ChannelState.attached);

            channel.setSuspended(new ErrorInfo("Set state to suspended", 400), true);
            channelWaiter.waitFor(ChannelState.suspended);

            /* Inject detached message as if from the server */
            ProtocolMessage detachedMessage = new ProtocolMessage() {{
                action = Action.detached;
                channel = channelName;
                error = new ErrorInfo("Simulated detach", 40000);
            }};
            ably.connection.connectionManager.onMessage(null, detachedMessage);

            /* Channel should transition to attaching, then to attached */
            ErrorInfo detachError = channelWaiter.waitFor(ChannelState.attaching);
            Assert.assertNotNull(detachError);
            Assert.assertEquals(40000, detachError.code);
            Assert.assertEquals("Simulated detach", detachError.message);

            channelWaiter.waitFor(ChannelState.attached);

            List<ChannelState> channelStates = channelWaiter.getRecordedStates();
            Assert.assertEquals(5, channelStates.size());
            Assert.assertEquals(ChannelState.attaching, channelStates.get(0));
            Assert.assertEquals(ChannelState.attached, channelStates.get(1));
            Assert.assertEquals(ChannelState.suspended, channelStates.get(2));
            Assert.assertEquals(ChannelState.attaching, channelStates.get(3));
            Assert.assertEquals(ChannelState.attached, channelStates.get(4));

        } finally {
            if (ably != null)
                ably.close();
            Defaults.realtimeRequestTimeout = oldRealtimeTimeout;
        }
    }

    /*
     * Establish connection, attach channel, disconnection and failed resume
     * verify that subsequent attaches are performed, and give rise to update events
     *
     * Tests RTN15c6
     */
    @Test
    public void channel_valid_resume_reattach_channels() throws AblyException {
        RealtimeClient ably = null;

        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            ably.connect();
            connectionWaiter.waitFor(ConnectionState.connected);
            String originalConnectionId = ably.connection.id;

            /* prepare channels */
            Channel attachedChannel = ably.channels.get("attached_channel");
            ChannelWaiter attachedChannelWaiter = new ChannelWaiter(attachedChannel);
            attachedChannel.attach();
            attachedChannelWaiter.waitFor(ChannelState.attached);
            attachedChannel.publish("chat", "message");

            Channel suspendedChannel = ably.channels.get("suspended_channel");
            ChannelWaiter suspendedChannelWaiter = new ChannelWaiter(suspendedChannel);
            suspendedChannel.attach();
            suspendedChannelWaiter.waitFor(ChannelState.attached);
            suspendedChannel.setSuspended(null, true);
            suspendedChannelWaiter.waitFor(ChannelState.suspended);

            assertEquals(ably.connection.connectionManager.msgSerial, 1);

            new Helpers.MutableConnectionManager(ably).disconnectAndSuppressRetries();
            connectionWaiter.waitFor(ConnectionState.disconnected);
            assertEquals("Verify disconnected state is reached", ConnectionState.disconnected, ably.connection.state);

            /* wait for connection to be reestablished */
            System.out.println("channel_resume_lost_continuity: initiating reconnection (resume)");
            ably.connection.connect();

            ErrorInfo resumeError = connectionWaiter.waitFor(ConnectionState.connected);
            assertNull(resumeError);
            assertNull(ably.connection.connectionManager.getStateErrorInfo());
            assertEquals("Same connection is used", originalConnectionId, ably.connection.id);
            assertEquals(ably.connection.connectionManager.msgSerial, 1);

            attachedChannelWaiter.waitFor(ChannelState.attaching, ChannelState.attached);
            suspendedChannelWaiter.waitFor(ChannelState.attached);

            assertFalse("Verify channel was not suspended",
                attachedChannelWaiter.hasStates(ChannelState.suspended));
            assertTrue("Verify channel was attaching and attached",
                attachedChannelWaiter.hasFinalStates(ChannelState.attaching, ChannelState.attached));

            ChannelStateListener.ChannelStateChange stateChange = attachedChannelWaiter.getLastStateChange();
            assertEquals(ChannelState.attached, stateChange.current);
            assertEquals(ChannelState.attaching, stateChange.previous);

            assertTrue("Verify channel was attaching",
                suspendedChannelWaiter.hasFinalStates(ChannelState.attaching, ChannelState.attached));

            stateChange = suspendedChannelWaiter.getLastStateChange();
            assertEquals(ChannelState.attached, stateChange.current);
            assertEquals(ChannelState.attaching, stateChange.previous);

        } finally {
            if (ably != null)
                ably.close();
        }
    }

    /*
     * Establish connection, attach channel, disconnection and failed resume
     * verify that subsequent attaches are performed, and give rise to update events
     *
     * Tests RTN15c7
     */
    @Test
    public void channel_invalid_resume_reattach_channels() throws AblyException {
        RealtimeClient ably = null;
        final String attachedChannelName = "channel_resume_lost_continuity_attached";
        final String suspendedChannelName = "channel_resume_lost_continuity_suspended";

        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            ably.connect();
            connectionWaiter.waitFor(ConnectionState.connected);
            String originalConnectionId = ably.connection.id;

            /* prepare channels */
            Channel attachedChannel = ably.channels.get(attachedChannelName);
            ChannelWaiter attachedChannelWaiter = new ChannelWaiter(attachedChannel);
            attachedChannel.attach();
            attachedChannelWaiter.waitFor(ChannelState.attached);
            attachedChannel.publish("chat", "message");

            Channel suspendedChannel = ably.channels.get(suspendedChannelName);
            ChannelWaiter suspendedChannelWaiter = new ChannelWaiter(suspendedChannel);
            suspendedChannel.attach();
            suspendedChannelWaiter.waitFor(ChannelState.attached);
            suspendedChannel.setSuspended(null, true);
            suspendedChannelWaiter.waitFor(ChannelState.suspended);

            assertEquals(ably.connection.connectionManager.msgSerial, 1);

            new Helpers.MutableConnectionManager(ably).disconnectAndSuppressRetries();
            connectionWaiter.waitFor(ConnectionState.disconnected);
            assertEquals("Verify disconnected state is reached", ConnectionState.disconnected, ably.connection.state);

            /* disconnect, and sabotage the resume */
            ably.connection.key = "_____!ably___test_fake-key____";

            /* wait for connection to be reestablished */
            System.out.println("channel_resume_lost_continuity: initiating reconnection (resume)");
            ably.connection.connect();
            /* verify a new connection was assigned */

            ErrorInfo resumeError = connectionWaiter.waitFor(ConnectionState.connected);
            assertNotNull(resumeError);
            assertEquals("Verify error code indicates invalid connection key", resumeError.code, 80018);
            assertSame(resumeError, ably.connection.connectionManager.getStateErrorInfo());
            assertNotEquals("A new connection was created", originalConnectionId, ably.connection.id);

            RealtimeClient finalAbly = ably;
            Exception conditionError = new Helpers.ConditionalWaiter().
                wait(() -> finalAbly.connection.connectionManager.msgSerial == 0, 10000);
            assertNull(conditionError);

            attachedChannelWaiter.waitFor(ChannelState.attaching, ChannelState.attached);
            suspendedChannelWaiter.waitFor(ChannelState.attached);

            assertFalse("Verify channel was not suspended",
                attachedChannelWaiter.hasStates(ChannelState.suspended));
            assertTrue("Verify channel was attaching and attached",
                attachedChannelWaiter.hasFinalStates(ChannelState.attaching, ChannelState.attached));

            ChannelStateListener.ChannelStateChange stateChange = attachedChannelWaiter.getLastStateChange();
            assertEquals(ChannelState.attached, stateChange.current);
            assertEquals(ChannelState.attaching, stateChange.previous);

            assertTrue("Verify channel was attaching",
                suspendedChannelWaiter.hasFinalStates(ChannelState.attaching, ChannelState.attached));

            stateChange = suspendedChannelWaiter.getLastStateChange();
            assertEquals(ChannelState.attached, stateChange.current);
            assertEquals(ChannelState.attaching, stateChange.previous);

        } finally {
            if (ably != null)
                ably.close();
        }
    }

    /*
     * Initiate connection, block send on transport to simulate network packet loss, try to attach, wait for
     * channel to eventually attach when send is re-enabled on transport.
     *
     * Then suspend connection, resume it and immediately block sending packets failing channel
     * reattach. Verify that the channel goes back to suspended state on timeout with correct error code.
     *
     * Try to detach channel while blocking send, channel should go back to attached state through detaching
     *
     * Tests features RTL4c, RTL4f, RTL5f, RTL5e
     */
    @Test
    public void channel_attach_retry_failed() {
        RealtimeClient ably = null;
        String channelName = "channel_attach_retry_failed_" + testParams.name;
        long oldRealtimeTimeout = Defaults.realtimeRequestTimeout;
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            opts.channelRetryTimeout = 1000;

            /* Mock transport to block send */
            final MockWebsocketFactory mockTransport = new MockWebsocketFactory();
            opts.transportFactory = mockTransport;
            mockTransport.allowSend();

            /* Reduce timeout for test to run faster */
            Defaults.realtimeRequestTimeout = 1000;

            ably = RealtimeClientFactory.create(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);

            /* Block send() and attach */
            mockTransport.blockSend();

            Channel channel = ably.channels.get(channelName);
            ChannelWaiter channelWaiter = new ChannelWaiter(channel);
            channel.attach();

            channelWaiter.waitFor(ChannelState.attaching);

            /* Should get to suspended soon because send() is blocked */
            channelWaiter.waitFor(ChannelState.suspended);

            /* Re-enable send() and wait for channel to attach */
            mockTransport.allowSend();
            channelWaiter.waitFor(ChannelState.attached);

            /* Suspend connection: channel state should change to suspended */
            ably.connection.connectionManager.requestState(ConnectionState.suspended);
            channelWaiter.waitFor(ChannelState.suspended);

            /* Block transport's send() before reconnecting so that channel reattach fails.
             * (Channel transitions to attaching before the connected event fires, so send
             * must be blocked before requestState is called.) */
            mockTransport.blockSend();
            ably.connection.connectionManager.requestState(ConnectionState.connected);

            /* Channel should move to attaching state */
            channelWaiter.waitFor(ChannelState.attaching);
            /*
             * Then within realtimeRequestTimeout interval it should get back to suspended
             */
            channelWaiter.waitFor(ChannelState.suspended);

            /* In channelRetryTimeout we should get back to attaching state again */
            channelWaiter.waitFor(ChannelState.attaching);
            /* And then suspended in another 1 second */
            channelWaiter.waitFor(ChannelState.suspended);

            /* Enable message sending again */
            mockTransport.allowSend();

            /* And wait for attached state of the channel */
            channelWaiter.waitFor(ChannelState.attached);

            final ErrorInfo[] errorDetaching = new ErrorInfo[] {null};

            /* Block and detach */
            mockTransport.blockSend();
            channel.detach(new CompletionListener() {
                @Override
                public void onSuccess() {
                    fail("Detach succeeded");
                }

                @Override
                public void onError(ErrorInfo reason) {
                    synchronized (errorDetaching) {
                        errorDetaching[0] = reason;
                        errorDetaching.notify();
                    }
                }
            });

            /* Should get to detaching first */
            channelWaiter.waitFor(ChannelState.detaching);
            /* And then back to attached on timeout */
            channelWaiter.waitFor(ChannelState.attached);
            try {
                synchronized (errorDetaching) {
                    if (errorDetaching[0] != null)
                        errorDetaching.wait(1000);
                }
            } catch (InterruptedException ignored) {}

            assertNotNull("Verify detach operation failed", errorDetaching[0]);

        } catch(AblyException e)  {
            e.printStackTrace();
            fail("Unexpected exception");
        } finally {
            if (ably != null)
                ably.close();
            /* Restore default values to run other tests */
            Defaults.realtimeRequestTimeout = oldRealtimeTimeout;
        }
    }

    /*
     * Initiate connection, and attach to a channel. Simulate a server-initiated detach of the channel
     * and verify that the client attempts to reattach. Block the transport to prevent that from
     * succeeding. Verify that the channel enters the suspended state with the appropriate error.
     *
     * Tests features RTL13b
     */
    @Test
    public void channel_reattach_failed_timeout() {
        RealtimeClient ably = null;
        final String channelName = "channel_reattach_failed_timeout_" + testParams.name;
        long oldRealtimeTimeout = Defaults.realtimeRequestTimeout;
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            opts.channelRetryTimeout = 1000;

            /* Mock transport to block send */
            final MockWebsocketFactory mockTransport = new MockWebsocketFactory();
            opts.transportFactory = mockTransport;
            mockTransport.allowSend();

            /* Reduce timeout for test to run faster */
            Defaults.realtimeRequestTimeout = 1000;

            ably = RealtimeClientFactory.create(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);

            Channel channel = ably.channels.get(channelName);
            ChannelWaiter channelWaiter = new ChannelWaiter(channel);
            channel.attach();

            channelWaiter.waitFor(ChannelState.attached);

            /* Block send() */
            mockTransport.blockSend();

            /* Inject detached message as if from the server */
            ProtocolMessage detachedMessage = new ProtocolMessage() {{
                action = Action.detached;
                channel = channelName;
            }};
            ably.connection.connectionManager.onMessage(null, detachedMessage);

            /* Should get to suspended soon because send() is blocked */
            ErrorInfo suspendReason = channelWaiter.waitFor(ChannelState.suspended);
            assertEquals("Verify the suspended event contains the detach reason", 90007, suspendReason.code);

            /* Unblock send(), and expect a transition to attached */
            mockTransport.allowSend();
            channelWaiter.waitFor(ChannelState.attached);

        } catch(AblyException e)  {
            e.printStackTrace();
            fail("channel_reattach_failed_timeout: unexpected exception");
        } finally {
            if (ably != null) {
                ably.close();
            }
            /* Restore default values to run other tests */
            Defaults.realtimeRequestTimeout = oldRealtimeTimeout;
        }
    }

    /*
     * Initiate connection, and attach to a channel. Simulate a server-initiated detach of the channel
     * and verify that the client attempts to reattach. Block the transport and respond instead with
     * an attach error. Verify that the channel enters the suspended state with the appropriate error.
     *
     * Tests features RTL13b
     */
    @Test
    public void channel_reattach_failed_error() {
        RealtimeClient ably = null;
        final String channelName = "channel_reattach_failed_error_" + testParams.name;
        final int errorCode = 12345;
        long oldRealtimeTimeout = Defaults.realtimeRequestTimeout;
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            opts.channelRetryTimeout = 1000;

            /* Mock transport to block send */
            final MockWebsocketFactory mockTransport = new MockWebsocketFactory();
            opts.transportFactory = mockTransport;
            mockTransport.allowSend();

            /* Reduce timeout for test to run faster */
            Defaults.realtimeRequestTimeout = 5000;

            ably = RealtimeClientFactory.create(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);

            Channel channel = ably.channels.get(channelName);
            ChannelWaiter channelWaiter = new ChannelWaiter(channel);
            channel.attach();

            channelWaiter.waitFor(ChannelState.attached);

            /* Block send() */
            mockTransport.blockSend();

            /* Inject detached message as if from the server */
            ProtocolMessage detachedMessage = new ProtocolMessage() {{
                action = Action.detached;
                channel = channelName;
                error = new ErrorInfo("Test error", errorCode);
            }};
            ably.connection.connectionManager.onMessage(null, detachedMessage);

            /* wait for the client reattempt attachment */
            channelWaiter.waitFor(ChannelState.attaching);

            /* Inject detached+error message as if from the server */
            ProtocolMessage errorMessage = new ProtocolMessage() {{
                action = Action.detached;
                channel = channelName;
                error = new ErrorInfo("Test error", errorCode);
            }};
            ably.connection.connectionManager.onMessage(null, errorMessage);

            /* Should get to suspended soon because there was an error response to the attach attempt */
            ErrorInfo suspendReason = channelWaiter.waitFor(ChannelState.suspended);
            assertEquals("Verify the suspended event contains the detach reason", errorCode, suspendReason.code);

            /* Unblock send(), and expect a transition to attached */
            mockTransport.allowSend();
            channelWaiter.waitFor(ChannelState.attached);

        } catch(AblyException e)  {
            e.printStackTrace();
            fail("Unexpected exception");
        } finally {
            if (ably != null)
                ably.close();
            /* Restore default values to run other tests */
            Defaults.realtimeRequestTimeout = oldRealtimeTimeout;
        }
    }

    /**
     * Initiate an attach when not connected; verify that the given listener is called
     * with the attach error
     */
    @Test
    public void attach_exception_listener_called() {
        try {
            final String channelName = "attach_exception_listener_called_" + testParams.name;

            /* init Ably */
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            RealtimeClient ably = RealtimeClientFactory.create(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel; put into failed state */
            ably.connection.connectionManager.requestState(new ConnectionManager.StateIndication(ConnectionState.failed, new ErrorInfo("Test error", 400, 12345)));
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.failed);
            assertEquals("Verify failed state reached", ably.connection.state, ConnectionState.failed);

            /* attempt to attach */
            Channel channel = ably.channels.get(channelName);
            final ErrorInfo[] listenerError = new ErrorInfo[1];
            synchronized(listenerError) {
                channel.attach(new CompletionListener() {
                    @Override
                    public void onSuccess() {
                        synchronized (listenerError) {
                            listenerError.notify();
                        }
                        fail("Unexpected attach success");
                    }

                    @Override
                    public void onError(ErrorInfo reason) {
                        synchronized (listenerError) {
                            listenerError[0] = reason;
                            listenerError.notify();
                        }
                    }
                });

                /* wait until the listener is called */
                while(listenerError[0] == null) {
                    try { listenerError.wait(); } catch(InterruptedException ignored) {}
                }
            }

            /* verify that the listener was called with an error */
            assertNotNull("Verify the error callback was called", listenerError[0]);
            assertEquals("Verify the given error is indicated", listenerError[0].code, 12345);

            /* tidy */
            ably.close();
        } catch(AblyException e) {
            fail(e.getMessage());
        }

    }

    @Test
    public void no_messages_when_channel_state_not_attached() {

        RealtimeClient senderReceiver = null;
        final String testMessage1 = "{ foo: \"bar\", count: 1, status: \"active\" }";
        final String testMessage2 = "{ foo: \"bar\", count: 2, status: \"active\" }";

        String testName = "no_messages_when_channel_state_not_attached";
        try {

            DebugOptions common_opts = createOptions(testVars.keys[0].keyStr);
            common_opts.protocolListener = new DetachingProtocolListener();
            senderReceiver = RealtimeClientFactory.create(common_opts);

            Channel sender_channel = senderReceiver.channels.get(testName);
            ((DetachingProtocolListener)common_opts.protocolListener).theChannel = sender_channel;


            sender_channel.attach();
            (new ChannelWaiter(sender_channel)).waitFor(ChannelState.attached);

            Helpers.MessageWaiter messageWaiter_1 = new Helpers.MessageWaiter(sender_channel);

            sender_channel.publish("1", testMessage1);

            messageWaiter_1.waitFor(1);
            assertEquals("Verify rewound message", testMessage1, messageWaiter_1.receivedMessages.get(0).data);
            messageWaiter_1.reset();

            sender_channel.publish("2", testMessage2);
            messageWaiter_1.waitFor(1, 7000);
            assertEquals("Verify no message received on attach_rewind", 0, messageWaiter_1.receivedMessages.size());

        } catch(Exception e) {
            fail(testName + ": Unexpected exception " + e.getMessage());
            e.printStackTrace();
        } finally {
            if(senderReceiver != null)
                senderReceiver.close();
        }
    }

    /*
     * Checks that the DETACHED message sent by the server when a channel is released is dropped.
     */
    @Test
    public void detach_message_to_released_channel_is_dropped() throws AblyException {
        RealtimeClient ably = null;
        long oldRealtimeTimeout = Defaults.realtimeRequestTimeout;
        final String channelName = "detach_message_to_released_channel_is_dropped";

        try {
            DebugOptions opts = createOptions(testVars.keys[0].keyStr);
            Helpers.RawProtocolMonitor monitor = Helpers.RawProtocolMonitor.createReceiver(ProtocolMessage.Action.detached);
            opts.protocolListener = monitor;

            /* Make test faster */
            Defaults.realtimeRequestTimeout = 1000;
            opts.channelRetryTimeout = 1000;

            ably = RealtimeClientFactory.create(opts);
            Channel channel = ably.channels.get(channelName);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);

            // Listen for detach messages and release the channel
            ably.channels.release(channelName);
            monitor.waitForRecv(1, 10000);

            assertFalse(ably.channels.containsKey("messages_to_non_existent_channels_are_dropped"));
        } finally {
            if (ably != null)
                ably.close();
            Defaults.realtimeRequestTimeout = oldRealtimeTimeout;
        }
    }

    /*
     * Spec: RTN11d
     * Checks that all channels become if the state is CLOSED transitions all the channels to
     * INITIALIZED and unsets:
     *  - RealtimeChannel.errorReason
     *  - Connection.errorReason
     *  - msgSerial
     */
    @Test
    public void connect_on_closed_client_should_reinitialize_channels() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        try (RealtimeClient ably = RealtimeClientFactory.create(opts)) {

            /* wait until connected */
            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("channel");
            channel.attach();
            new ChannelWaiter(channel).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* push a message to increase msgSerial */
            channel.publish("test", "test");
            assertEquals(1, ably.connection.connectionManager.msgSerial);

            ably.close();
            new ChannelWaiter(channel).waitFor(ChannelState.detached);
            assertEquals(ConnectionState.closed, ably.connection.state);
            assertEquals(1, ably.connection.connectionManager.msgSerial);

            ably.connect();

            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
            assertEquals(ChannelState.initialized, channel.state);

            assertNull(channel.reason);
            assertNull(ably.connection.reason);
            assertEquals(ChannelState.initialized, channel.state);
            assertEquals(0, ably.connection.connectionManager.msgSerial);
        }
    }

    /*
     * Spec: RTN11b
     * Checks that all channels become if the state is CLOSING transitions all the channels to
     * INITIALIZED and unsets:
     *  - RealtimeChannel.errorReason
     *  - Connection.errorReason
     *  - msgSerial
     */
    @Test
    public void connect_on_closing_client_should_reinitialize_channels() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        try (RealtimeClient ably = RealtimeClientFactory.create(opts)) {

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("channel");
            channel.attach();
            new ChannelWaiter(channel).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* push a message to increase msgSerial */
            channel.publish("test", "test");
            assertEquals(1, ably.connection.connectionManager.msgSerial);

            List<ChannelState> observedChannelStates = new ArrayList<>();
            channel.on(stateChange -> observedChannelStates.add(stateChange.current));

            List<ConnectionState> observedConnectionStates = new ArrayList<>();
            ably.connection.on(stateChange -> observedConnectionStates.add(stateChange.current));

            ably.close();
            ably.connect();

            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.closing);
            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);

            assertEquals(Arrays.asList(ConnectionState.closing, ConnectionState.connecting, ConnectionState.connected), observedConnectionStates);
            assertEquals(ChannelState.initialized, channel.state);

            channel.attach();
            new ChannelWaiter(channel).waitFor(ChannelState.attached);

            assertNull(channel.reason);
            assertEquals(0, ably.connection.connectionManager.msgSerial);
            assertEquals(Arrays.asList(ChannelState.detached, ChannelState.initialized, ChannelState.attaching, ChannelState.attached), observedChannelStates);
        }
    }

    @Test
    public void release_should_not_prevent_graceful_test_end() throws Exception {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        try (RealtimeClient ably = RealtimeClientFactory.create(opts)) {
            ably.channels.get("channel_should_be_released");
            ably.channels.release("channel_should_be_released");
        }

        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        List<Thread> timers = threads.stream()
            .filter(t -> t.getName().startsWith("Timer-"))
            .toList();

        long timeout = 1_000;
        long start = System.currentTimeMillis();

        while(timers.stream().anyMatch(Thread::isAlive) && System.currentTimeMillis() - start < timeout) {
            Thread.sleep(100);
        }

        String activeTimers = timers.stream()
            .filter(Thread::isAlive)
            .map(this::stringStackTrace)
            .collect(Collectors.joining("\n\n"));

        assertFalse(
            "Found orphan Timer threads:\n" + activeTimers,
            timers.stream().anyMatch(Thread::isAlive)
        );
    }

    private String stringStackTrace(Thread thread) {
        return Arrays.stream(thread.getStackTrace())
            .map(StackTraceElement::toString)
            .collect(Collectors.joining("\n"));
    }

    /**
     * This test ensures that when the connection is manually triggered, the channel can successfully
     * transition to the attached state without interference or rewriting of its immediate attach action.
     */
    @Test
    public void connect_should_not_rewrite_immediate_attach() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        try (RealtimeClient ably = RealtimeClientFactory.create(opts)) {
            ably.close();
            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.closed);
            assertEquals("Verify closed state reached", ConnectionState.closed, ably.connection.state);
            /* create a channel connect and attach */
            final Channel channel = ably.channels.get("channel");
            ably.connect();
            channel.attach();
            new ChannelWaiter(channel).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", ChannelState.attached, channel.state);
        }
    }

    @Test
    public void channel_get_objects_throws_exception() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        try (RealtimeClient ably = RealtimeClientFactory.create(opts)) {

            /* wait until connected */
            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("channel");
            channel.attach();
            new ChannelWaiter(channel).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> channel.object.get());
            assertNotNull(exception);
            assertTrue(exception.getMessage().contains("LiveObjects plugin hasn't been installed"));

            AblyException cause = (AblyException) exception.getCause();
            assertNotNull(cause);
            assertEquals(40019, cause.errorInfo.code);
            assertEquals(400, cause.errorInfo.statusCode);
        }
    }

    /*
     * @spec RTL3d1
     *
     * Given: an attached channel
     * When: the connection drops and then reconnects
     * Then: at the moment the connection fires onConnected, the channel state is already attaching
     */
    @Test
    public void channel_state_is_attaching_on_reconnected() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        RealtimeClient ably = RealtimeClientFactory.create(opts);
        ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

        /* attach channel and wait for it to be fully attached */
        final Channel channel = ably.channels.get("channel_state_is_attaching_on_reconnected_" + testParams.name);
        ChannelWaiter channelWaiter = new ChannelWaiter(channel);
        channel.attach();
        channelWaiter.waitFor(ChannelState.attached);
        assertEquals("Channel should be attached", ChannelState.attached, channel.state);

        /* capture channel state at the moment the connection becomes connected */
        final ChannelState[] channelStateOnConnected = new ChannelState[1];

        /* drop the connection */
        new Helpers.MutableConnectionManager(ably).disconnectAndSuppressRetries();
        connectionWaiter.waitFor(ConnectionState.disconnected);
        assertEquals("Connection should be disconnected", ConnectionState.disconnected, ably.connection.state);

        Helpers.CompletionWaiter completionWaiter = new Helpers.CompletionWaiter();

        ably.connection.on(ConnectionEvent.connected, stateChange -> {
            synchronized (channelStateOnConnected) {
                channelStateOnConnected[0] = channel.state;
                completionWaiter.onSuccess();
            }
        });

        /* reconnect */
        ably.connection.connect();

        completionWaiter.waitFor();

        /* verify channel was attaching at the moment the connection became connected */
        assertEquals(
            "Channel state should be attaching when connection fires connected",
            ChannelState.attaching,
            channelStateOnConnected[0]
        );
    }

    static class DetachingProtocolListener implements DebugOptions.RawProtocolListener {

        public Channel theChannel;
        boolean messageReceived;

        DetachingProtocolListener() {
            messageReceived = false;
        }

        @Override
        public void onRawConnect(String url) {}
        @Override
        public void onRawConnectRequested(String url) {}
        @Override
        public void onRawMessageSend(ProtocolMessage message) {
        }
        @Override
        public void onRawMessageRecv(ProtocolMessage message) {
            if(message.action == ProtocolMessage.Action.message) {
                if (!messageReceived) {
                    messageReceived = true;
                    return;
                }

                theChannel.state = ChannelState.attaching;
            }
        }
    };
}
