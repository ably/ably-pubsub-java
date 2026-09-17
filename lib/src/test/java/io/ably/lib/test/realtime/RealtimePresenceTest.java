package io.ably.lib.test.realtime;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isOneOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.ably.lib.debug.DebugOptions;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelEvent;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.ChannelStateListener;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.realtime.ConnectionEvent;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.realtime.ConnectionStateListener;
import io.ably.lib.realtime.Presence;
import io.ably.lib.test.util.AblyCommonsReader;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Capability;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import io.ably.lib.types.PresenceMessage;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.util.Serialisation;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth;
import io.ably.lib.rest.Auth.TokenParams;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.Helpers.ChannelWaiter;
import io.ably.lib.test.common.Helpers.CompletionSet;
import io.ably.lib.test.common.Helpers.CompletionWaiter;
import io.ably.lib.test.common.Helpers.ConnectionWaiter;
import io.ably.lib.test.common.Helpers.PresenceWaiter;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.test.util.MockWebsocketFactory;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.types.PresenceMessage.Action;

public class RealtimePresenceTest extends ParameterizedTest {

    private static final String testMessagesEncodingFile = "test-resources/presence-messages-encoding.json";
    private static final String testClientId1 = "testClientId1";
    private static final String testClientId2 = "testClientId2";
    private Auth.TokenDetails token1;
    private Auth.TokenDetails token2;
    private Auth.TokenDetails wildcardToken;

    private static PresenceMessage contains(PresenceMessage[] messages, String clientId) {
        for(PresenceMessage message : messages)
            if(clientId.equals(message.clientId))
                return message;
        return null;
    }

    private PresenceMessage contains(PresenceMessage[] messages, String clientId, PresenceMessage.Action action) {
        for(PresenceMessage message : messages)
            if(clientId.equals(message.clientId) && action == message.action)
                return message;
        return null;
    }

    private static String random() {
        return UUID.randomUUID().toString();
    }

    private class TestChannel {
        TestChannel() {
            try {
                ClientOptions opts = createOptions(testVars.keys[0].keyStr);
                rest = new AblyRest(opts);
                restChannel = rest.channels.get(channelName);
                realtime = new AblyRealtime(opts);
                realtimeChannel = realtime.channels.get(channelName);
                realtimeChannel.attach();
                (new ChannelWaiter(realtimeChannel)).waitFor(ChannelState.attached);
            } catch(AblyException ae) {}
        }

        void dispose() {
            if(realtime != null)
                realtime.close();
        }

        String channelName = random();
        AblyRest rest;
        AblyRealtime realtime;
        io.ably.lib.rest.Channel restChannel;
        io.ably.lib.realtime.Channel realtimeChannel;
    }

    @Rule
    public Timeout testTimeout = Timeout.seconds(300);

    @Before
    public void setUpBefore() throws Exception {
        /* create tokens for specific clientIds */
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        AblyRest rest = new AblyRest(opts);
        token1 = rest.auth.requestToken(new TokenParams() {{ clientId = testClientId1; }}, null);
        token2 = rest.auth.requestToken(new TokenParams() {{ clientId = testClientId2; }}, null);
        wildcardToken = rest.auth.requestToken(new TokenParams() {{ clientId = "*"; }}, null);
    }

    /**
     * Attach to channel, enter presence channel and await entered event
     */
    @Test
    public void enter_simple() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            client1Channel.attach();
            (new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

            /* let client1 enter the channel and wait for the entered event to be delivered */
            CompletionWaiter enterComplete = new CompletionWaiter();
            String enterString = "Test data (enter_simple)";
            client1Channel.presence.enter(enterString, enterComplete);
            presenceWaiter.waitFor(testClientId1, Action.enter);
            assertNotNull(presenceWaiter.contains(testClientId1, Action.enter));
            assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);

            /* verify enter callback called on completion */
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);
        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Enter presence channel without prior attach and await entered event
     */
    @Test
    public void enter_before_attach() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            /* get channel */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

            /* let client1 enter the channel and wait for the entered event to be delivered */
            CompletionWaiter enterComplete = new CompletionWaiter();
            String enterString = "Test data (enter_before_attach)";
            client1Channel.presence.enter(enterString, enterComplete);
            presenceWaiter.waitFor(testClientId1, Action.enter);
            PresenceMessage expectedPresent = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.enter);
            assertNotNull(expectedPresent);
            assertEquals(expectedPresent.data, enterString);

            /* verify enter callback called on completion */
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);
        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Enter presence channel without prior connect and await entered event
     */
    @Test
    public void enter_before_connect() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* get channel */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

            /* let client1 enter the channel and wait for the entered event to be delivered */
            CompletionWaiter enterComplete = new CompletionWaiter();
            String enterString = "Test data (enter_before_connect)";
            client1Channel.presence.enter(enterString, enterComplete);
            presenceWaiter.waitFor(testClientId1, Action.enter);
            PresenceMessage expectedPresent = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.enter);
            assertNotNull(expectedPresent);
            assertEquals(expectedPresent.data, enterString);

            /* verify enter callback called on completion */
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);
        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Enter, then leave, presence channel and await leave event
     * Verify that the item is removed from the presence map (RTP2e)
     */
    @Test
    public void enter_leave_simple() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* get channel */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

            /* let client1 enter the channel and wait for the entered event to be delivered */
            CompletionWaiter enterComplete = new CompletionWaiter();
            String enterString = "Test data (enter_before_connect)";
            client1Channel.presence.enter(enterString, enterComplete);
            presenceWaiter.waitFor(testClientId1, Action.enter);
            assertNotNull(presenceWaiter.contains(testClientId1, Action.enter));
            assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);
            presenceWaiter.reset();

            /* verify enter callback called on completion */
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);

            /* let client1 leave the channel and wait for the leave event to be delivered */
            CompletionWaiter leaveComplete = new CompletionWaiter();
            String leaveString = "Test data (enter_before_connect), leaving";
            client1Channel.presence.leave(leaveString, leaveComplete);
            presenceWaiter.waitFor(testClientId1, Action.leave);
            PresenceMessage expectedLeft = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.leave);
            assertNotNull(expectedLeft);
            assertEquals(expectedLeft.data, leaveString);

            /* verify leave callback called on completion */
            leaveComplete.waitFor();
            assertTrue("Verify leave callback called on completion", leaveComplete.success);

            assertEquals("Verify item is removed from the presence map", client1Channel.presence.get(testClientId1, false).length, 0);

        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            testChannel.dispose();
        }
    }

    /**
     * Enter, then enter again, expecting update event
     */
    @Test
    public void enter_enter_simple() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* get channel */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

            /* let client1 enter the channel and wait for the entered event to be delivered */
            CompletionWaiter enterComplete = new CompletionWaiter();
            String enterString = "Test data (enter_enter_simple)";
            client1Channel.presence.enter(enterString, enterComplete);
            presenceWaiter.waitFor(testClientId1, Action.enter);
            assertNotNull(presenceWaiter.contains(testClientId1, Action.enter));
            assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);
            presenceWaiter.reset();

            /* verify enter callback called on completion */
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);

            /* let client1 reenter the channel and wait for the update event to be delivered */
            CompletionWaiter reenterComplete = new CompletionWaiter();
            String reenterString = "Test data (enter_enter_simple), reentering";
            client1Channel.presence.enter(reenterString, reenterComplete);
            presenceWaiter.waitFor(testClientId1, Action.update);
            assertNotNull(presenceWaiter.contains(testClientId1, Action.update));
            assertEquals(presenceWaiter.receivedMessages.get(0).data, reenterString);

            /* verify reenter callback called on completion */
            reenterComplete.waitFor();
            assertTrue("Verify reenter callback called on completion", reenterComplete.success);

            /* let client1 leave the channel and wait for the leave event to be delivered */
            CompletionWaiter leaveComplete = new CompletionWaiter();
            String leaveString = "Test data (enter_enter_simple), leaving";
            client1Channel.presence.leave(leaveString, leaveComplete);
            presenceWaiter.waitFor(testClientId1, Action.leave);
            PresenceMessage expectedLeft = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.leave);
            assertNotNull(expectedLeft);
            assertEquals(expectedLeft.data, leaveString);

            /* verify leave callback called on completion */
            leaveComplete.waitFor();
            assertTrue("Verify leave callback called on completion", leaveComplete.success);
        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            testChannel.dispose();
        }
    }

    /**
     * Enter, then update, expecting update event
     */
    @Test
    public void enter_update_simple() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* get channel */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

            /* let client1 enter the channel and wait for the entered event to be delivered */
            CompletionWaiter enterComplete = new CompletionWaiter();
            String enterString = "Test data (enter_update_simple)";
            client1Channel.presence.enter(enterString, enterComplete);
            presenceWaiter.waitFor(testClientId1, Action.enter);
            assertNotNull(presenceWaiter.contains(testClientId1, Action.enter));
            assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);
            presenceWaiter.reset();

            /* verify enter callback called on completion */
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);

            /* let client1 update the channel and wait for the update event to be delivered */
            CompletionWaiter updateComplete = new CompletionWaiter();
            String reenterString = "Test data (enter_update_simple), updating";
            client1Channel.presence.enter(reenterString, updateComplete);
            presenceWaiter.waitFor(testClientId1, Action.update);
            assertNotNull(presenceWaiter.contains(testClientId1, Action.update));
            assertEquals(presenceWaiter.receivedMessages.get(0).data, reenterString);

            /* verify reenter callback called on completion */
            updateComplete.waitFor();
            assertTrue("Verify reenter callback called on completion", updateComplete.success);

            /* let client1 leave the channel and wait for the leave event to be delivered */
            CompletionWaiter leaveComplete = new CompletionWaiter();
            String leaveString = "Test data (enter_update_simple), leaving";
            client1Channel.presence.leave(leaveString, leaveComplete);
            presenceWaiter.waitFor(testClientId1, Action.leave);
            PresenceMessage expectedLeft = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.leave);
            assertNotNull(expectedLeft);
            assertEquals(expectedLeft.data, leaveString);

            /* verify leave callback called on completion */
            leaveComplete.waitFor();
            assertTrue("Verify leave callback called on completion", leaveComplete.success);
        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            testChannel.dispose();
        }
    }

    /**
     * Enter, then update with null data, expecting previous data to be superseded
     */
    @Test
    public void enter_update_null() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            client1Opts.useBinaryProtocol = true;
            clientAbly1 = new AblyRealtime(client1Opts);

            /* get channel */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

            /* let client1 enter the channel and wait for the entered event to be delivered */
            CompletionWaiter enterComplete = new CompletionWaiter();
            String enterString = "Test data (enter_update_null)";
            client1Channel.presence.enter(enterString, enterComplete);
            presenceWaiter.waitFor(testClientId1, Action.enter);
            assertNotNull(presenceWaiter.contains(testClientId1, Action.enter));
            assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);
            presenceWaiter.reset();

            /* verify enter callback called on completion */
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);

            /* let client1 update the channel and wait for the update event to be delivered */
            CompletionWaiter updateComplete = new CompletionWaiter();
            String updateString = null;
            client1Channel.presence.enter(updateString, updateComplete);
            presenceWaiter.waitFor(testClientId1, Action.update);
            assertNotNull(presenceWaiter.contains(testClientId1, Action.update));
            assertEquals(presenceWaiter.receivedMessages.get(0).data, updateString);

            /* verify reenter callback called on completion */
            updateComplete.waitFor();
            assertTrue("Verify reenter callback called on completion", updateComplete.success);

            /* let client1 leave the channel and wait for the leave event to be delivered */
            CompletionWaiter leaveComplete = new CompletionWaiter();
            String leaveString = "Test data (enter_update_null), leaving";
            client1Channel.presence.leave(leaveString, leaveComplete);
            presenceWaiter.waitFor(testClientId1, Action.leave);
            PresenceMessage expectedLeft = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.leave);
            assertNotNull(expectedLeft);
            assertEquals(expectedLeft.data, leaveString);

            /* verify leave callback called on completion */
            leaveComplete.waitFor();
            assertTrue("Verify leave callback called on completion", leaveComplete.success);
        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            testChannel.dispose();
        }
    }

    /**
     * Update without having first entered, expecting enter event
     */
    @Test
    public void update_noenter() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* get channel */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

            /* let client1 enter the channel and wait for the entered event to be delivered */
            CompletionWaiter enterComplete = new CompletionWaiter();
            String updateString = "Test data (update_noenter)";
            client1Channel.presence.update(updateString, enterComplete);
            presenceWaiter.waitFor(testClientId1, Action.enter);
            assertNotNull(presenceWaiter.contains(testClientId1, Action.enter));
            assertEquals(presenceWaiter.receivedMessages.get(0).data, updateString);
            presenceWaiter.reset();

            /* verify enter callback called on completion */
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);

            /* let client1 leave the channel and wait for the leave event to be delivered */
            CompletionWaiter leaveComplete = new CompletionWaiter();
            String leaveString = "Test data (update_noenter), leaving";
            client1Channel.presence.leave(leaveString, leaveComplete);
            presenceWaiter.waitFor(testClientId1, Action.leave);
            PresenceMessage expectedLeft = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.leave);
            assertNotNull(expectedLeft);
            assertEquals(expectedLeft.data, leaveString);

            /* verify leave callback called on completion */
            leaveComplete.waitFor();
            assertTrue("Verify leave callback called on completion", leaveComplete.success);
        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Enter, then leave (with no data) and await leave event,
     * expecting enter data to be in leave event
     */
    @Test
    public void enter_leave_nodata() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* get channel */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

            /* let client1 enter the channel and wait for the entered event to be delivered */
            CompletionWaiter enterComplete = new CompletionWaiter();
            String enterString = "Test data (enter_leave_nodata)";
            client1Channel.presence.enter(enterString, enterComplete);
            presenceWaiter.waitFor(testClientId1, Action.enter);
            assertNotNull(presenceWaiter.contains(testClientId1, Action.enter));
            assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);
            presenceWaiter.reset();

            /* verify enter callback called on completion */
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);

            /* let client1 leave the channel and wait for the leave event to be delivered */
            CompletionWaiter leaveComplete = new CompletionWaiter();
            client1Channel.presence.leave(leaveComplete);
            presenceWaiter.waitFor(testClientId1, Action.leave);
            assertNotNull(presenceWaiter.contains(testClientId1, Action.leave));
            assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);

            /* verify leave callback called on completion */
            leaveComplete.waitFor();
            assertTrue("Verify leave callback called on completion", leaveComplete.success);

        } catch(AblyException e) {
            e.printStackTrace();
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Attach to channel, enter presence channel and get presence using realtime get()
     */
    @Test
    public void realtime_get_simple() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);

            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            client1Channel.attach();
            (new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

            /* let client1 enter the channel and wait for the success callback */
            CompletionWaiter enterComplete = new CompletionWaiter();
            String enterString = "Test data (get_simple)";
            client1Channel.presence.enter(enterString, enterComplete);
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);

            /* get presence set and verify client present */
            presenceWaiter.waitFor(testClientId1);
            PresenceMessage[] presences = testChannel.realtimeChannel.presence.get(false);
            PresenceMessage expectedPresent = contains(presences, testClientId1, Action.present);
            assertNotNull("Verify expected client is in presence set", expectedPresent);
            assertEquals(expectedPresent.data, enterString);

        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Attach to channel, enter+leave presence channel and get presence with realtime get()
     */
    @Test
    public void realtime_get_leave() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            client1Channel.attach();
            (new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

            /* let client1 enter the channel and wait for the success callback */
            CompletionWaiter enterComplete = new CompletionWaiter();
            client1Channel.presence.enter("Test data (get_leave)", enterComplete);
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);

            /* let client1 leave the channel; wait for the success callback and event */
            CompletionWaiter leaveComplete = new CompletionWaiter();
            client1Channel.presence.leave(leaveComplete);
            leaveComplete.waitFor();
            assertTrue("Verify leave callback called on completion", leaveComplete.success);
            presenceWaiter.waitFor(testClientId1, Action.leave);
            assertTrue("Verify leave callback called on completion", leaveComplete.success);

            /* get presence set and verify client absent */
            PresenceMessage[] presences = testChannel.realtimeChannel.presence.get(false);
            assertNull("Verify expected client is in presence set", contains(presences, testClientId1));

        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Attach to channel, enter presence channel, then initiate second
     * connection, seeing existing member in message subsequent to second attach response
     */
    @Test
    public void attach_enter_simple() {
        AblyRealtime clientAbly1 = null;
        AblyRealtime clientAbly2 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            client1Channel.attach();
            (new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

            /* let client1 enter the channel and wait for the success callback */
            CompletionWaiter enterComplete = new CompletionWaiter();
            String enterString = "Test data (attach_enter)";
            client1Channel.presence.enter(enterString, enterComplete);
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);

            /* set up a second connection with different clientId */
            ClientOptions client2Opts = new ClientOptions() {{
                tokenDetails = token2;
                clientId = testClientId2;
            }};
            fillInOptions(client2Opts);
            clientAbly2 = new AblyRealtime(client2Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly2.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly2.connection.state, ConnectionState.connected);

            /* get channel and subscribe to presence */
            Channel client2Channel = clientAbly2.channels.get(testChannel.channelName);
            PresenceWaiter client2Waiter = new PresenceWaiter(client2Channel);
            client2Waiter.waitFor(testClientId1, Action.present);

            /* get presence set and verify client present */
            PresenceMessage[] presences = client2Channel.presence.get(false);
            PresenceMessage expectedPresent = contains(presences, testClientId1, Action.present);
            assertNotNull("Verify expected client is in presence set", expectedPresent);
            assertEquals(expectedPresent.data, enterString);

        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(clientAbly2 != null)
                clientAbly2.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Attach to channel, enter presence channel with large number of clientIds,
     * then initiate second connection, seeing existing members in sync subsequent
     * to second attach response
     *
     * Test RTP4
     */
    @Test
    public void attach_enter_multiple() {
        AblyRealtime clientAbly1 = null;
        AblyRealtime clientAbly2 = null;
        TestChannel testChannel = new TestChannel();
        int clientCount = 250;
        try {
            /* subscribe for presence events in the anonymous connection */
            new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = wildcardToken;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            client1Channel.attach();
            (new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

            /* let client1 enter the channel for multiple clients and wait for the success callback */
            CompletionSet enterComplete = new CompletionSet();
            for(int i = 0; i < clientCount; i++) {
                client1Channel.presence.enterClient("client" + i, "Test data (attach_enter_multiple) " + i, enterComplete.add());
            }
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.pending.isEmpty());
            assertTrue("Verify no enter errors", enterComplete.errors.isEmpty());

            /* set up a second connection with different clientId */
            ClientOptions client2Opts = new ClientOptions() {{
                tokenDetails = token2;
                clientId = testClientId2;
            }};
            fillInOptions(client2Opts);
            clientAbly2 = new AblyRealtime(client2Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly2.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly2.connection.state, ConnectionState.connected);

            /* get channel */
            Channel client2Channel = clientAbly2.channels.get(testChannel.channelName);
            client2Channel.attach();
            (new ChannelWaiter(client2Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client2Channel.state, ChannelState.attached);

            /* get presence set and verify client present */
            HashMap<String, PresenceMessage> memberIndex = new HashMap<String, PresenceMessage>();
            PresenceMessage[] members = client2Channel.presence.get(true);
            assertNotNull("Expected non-null messages", members);
            assertEquals("Expected " + clientCount + " messages", members.length, clientCount);

            /* index received messages */
            for(PresenceMessage member: members)
                memberIndex.put(member.clientId, member);

            /* verify that all clientIds were received */
            assertEquals("Expected " + clientCount + " members", memberIndex.size(), clientCount);
            for(int i = 0; i < clientCount; i++) {
                String clientId = "client" + i;
                assertTrue("Expected client with id " + clientId, memberIndex.containsKey(clientId));
            }
        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(clientAbly2 != null)
                clientAbly2.close();
            testChannel.dispose();
        }
    }

    /**
     * Attach and enter channel on two connections, seeing
     * both members in presence returned by realtime get() */
    @Test
    public void realtime_enter_multiple() {
        AblyRealtime clientAbly1 = null;
        AblyRealtime clientAbly2 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter waiter = new PresenceWaiter(testChannel.realtimeChannel);

            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            CompletionWaiter enter1Complete = new CompletionWaiter();
            String enterString1 = "Test data (enter_multiple, clientId1)";
            client1Channel.presence.enter(enterString1, enter1Complete);
            enter1Complete.waitFor();
            assertTrue("Verify enter callback called on completion", enter1Complete.success);

            /* set up a second connection with different clientId */
            ClientOptions client2Opts = new ClientOptions() {{
                tokenDetails = token2;
                clientId = testClientId2;
            }};
            fillInOptions(client2Opts);
            clientAbly2 = new AblyRealtime(client2Opts);

            /* get channel and subscribe to presence */
            Channel client2Channel = clientAbly2.channels.get(testChannel.channelName);
            CompletionWaiter enter2Complete = new CompletionWaiter();
            String enterString2 = "Test data (enter_multiple, clientId2)";
            client2Channel.presence.enter(enterString2, enter2Complete);
            enter2Complete.waitFor();
            assertTrue("Verify enter callback called on completion", enter2Complete.success);

            /* verify enter events for both clients are received */
            waiter.waitFor(testClientId1, Action.enter);
            waiter.waitFor(testClientId2, Action.enter);

            /* get presence set and verify clients present */
            PresenceMessage[] presences = testChannel.realtimeChannel.presence.get(false);
            PresenceMessage expectedPresent1 = contains(presences, testClientId1, Action.present);
            PresenceMessage expectedPresent2 = contains(presences, testClientId2, Action.present);
            assertNotNull("Verify expected clients are in presence set", expectedPresent1);
            assertNotNull("Verify expected clients are in presence set", expectedPresent2);
            assertEquals(expectedPresent1.data, enterString1);
            assertEquals(expectedPresent2.data, enterString2);

        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(clientAbly2 != null)
                clientAbly2.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Attach to channel, enter presence channel and get presence using rest get()
     */
    @Test
    public void rest_get_simple() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            client1Channel.attach();
            (new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

            /* let client1 enter the channel and wait for the success callback */
            CompletionWaiter enterComplete = new CompletionWaiter();
            String enterString = "Test data (get_simple)";
            client1Channel.presence.enter(enterString, enterComplete);
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);

            /* get presence set and verify client present */
            PresenceMessage[] presences = testChannel.restChannel.presence.get(null).items();
            PresenceMessage expectedPresent = contains(presences, testClientId1, Action.present);
            assertNotNull("Verify expected client is in presence set", expectedPresent);
            assertEquals(expectedPresent.data, enterString);

        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Attach to channel, enter+leave presence channel and get presence with rest get()
     */
    @Test
    public void rest_get_leave() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            client1Channel.attach();
            (new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

            /* let client1 enter the channel and wait for the success callback */
            CompletionWaiter enterComplete = new CompletionWaiter();
            client1Channel.presence.enter("Test data (get_leave)", enterComplete);
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);

            /* let client1 leave the channel; wait for the success callback and event */
            CompletionWaiter leaveComplete = new CompletionWaiter();
            client1Channel.presence.leave(leaveComplete);
            leaveComplete.waitFor();
            assertTrue("Verify leave callback called on completion", leaveComplete.success);
            presenceWaiter.waitFor(testClientId1, Action.leave);
            assertTrue("Verify leave callback called on completion", leaveComplete.success);

            /* get presence set and verify client absent */
            PresenceMessage[] presences = testChannel.restChannel.presence.get(null).items();
            assertNull("Verify expected client is in presence set", contains(presences, testClientId1));

        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Attach and enter channel on two connections, seeing
     * both members in presence returned by rest get() */
    @Test
    public void rest_enter_multiple() {
        AblyRealtime clientAbly1 = null;
        AblyRealtime clientAbly2 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            CompletionWaiter enter1Complete = new CompletionWaiter();
            String enterString1 = "Test data (enter_multiple, clientId1)";
            client1Channel.presence.enter(enterString1, enter1Complete);
            enter1Complete.waitFor();
            assertTrue("Verify enter callback called on completion", enter1Complete.success);

            /* set up a second connection with different clientId */
            ClientOptions client2Opts = new ClientOptions() {{
                tokenDetails = token2;
                clientId = testClientId2;
            }};
            fillInOptions(client2Opts);
            clientAbly2 = new AblyRealtime(client2Opts);

            /* get channel and subscribe to presence */
            Channel client2Channel = clientAbly2.channels.get(testChannel.channelName);
            CompletionWaiter enter2Complete = new CompletionWaiter();
            String enterString2 = "Test data (enter_multiple, clientId2)";
            client2Channel.presence.enter(enterString2, enter2Complete);
            enter2Complete.waitFor();
            assertTrue("Verify enter callback called on completion", enter2Complete.success);

            /* get presence set and verify client present */
            PresenceMessage[] presences = testChannel.restChannel.presence.get(null).items();
            PresenceMessage expectedPresent1 = contains(presences, testClientId1, Action.present);
            PresenceMessage expectedPresent2 = contains(presences, testClientId2, Action.present);
            assertNotNull("Verify expected clients are in presence set", expectedPresent1);
            assertNotNull("Verify expected clients are in presence set", expectedPresent2);
            assertEquals(expectedPresent1.data, enterString1);
            assertEquals(expectedPresent2.data, enterString2);

        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(clientAbly2 != null)
                clientAbly2.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Attach and enter channel multiple times on a single connection,
     * retrieving members using paginated rest get() */
    @Test
    public void rest_paginated_get() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        int clientCount = 30;
        long delay = 100L;
        try {
            /* subscribe for presence events in the anonymous connection */
            new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = wildcardToken;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

            /* enter multiple clients */
            CompletionSet enterComplete = new CompletionSet();
            for(int i = 0; i < clientCount; i++) {
                client1Channel.presence.enterClient("client" + i, "Test data (rest_paginated_get) " + i, enterComplete.add());
                try { Thread.sleep(delay); } catch(InterruptedException e){}
            }
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.errors.isEmpty());

            /* get the presence for this channel */
            HashMap<String, PresenceMessage> memberIndex = new HashMap<String, PresenceMessage>();
            PaginatedResult<PresenceMessage> members = testChannel.restChannel.presence.get(new Param[] { new Param("limit", "10") });
            assertNotNull("Expected non-null messages", members);
            assertEquals("Expected 10 messages", members.items().length, 10);

            /* index received messages */
            for(int i = 0; i < 10; i++) {
                PresenceMessage member = members.items()[i];
                memberIndex.put(member.clientId, member);
            }

            /* get next page */
            members = members.next();
            assertNotNull("Expected non-null messages", members);
            assertEquals("Expected 10 messages", members.items().length, 10);

            /* index received messages */
            for(int i = 0; i < 10; i++) {
                PresenceMessage member = members.items()[i];
                memberIndex.put(member.clientId, member);
            }

            /* get next page */
            members = members.next();
            assertNotNull("Expected non-null messages", members);
            assertEquals("Expected 10 messages", members.items().length, 10);

            /* index received messages */
            for(int i = 0; i < 10; i++) {
                PresenceMessage member = members.items()[i];
                memberIndex.put(member.clientId, member);
            }

            /* verify there is no next page */
            assertFalse("Expected null next page", members.hasNext());

            /* verify that all clientIds were received */
            assertEquals("Expected " + clientCount + " members", memberIndex.size(), clientCount);
            for(int i = 0; i < clientCount; i++) {
                String clientId = "client" + i;
                assertTrue("Expected client with id " + clientId, memberIndex.containsKey(clientId));
            }
        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Attach to channel, enter presence channel, disconnect and await leave event
     */
    @Test
    public void disconnect_leave() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        boolean requiresClose = false;
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);
            requiresClose = true;

            /* get channel */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

            /* let client1 enter the channel and wait for the entered event to be delivered */
            CompletionWaiter enterComplete = new CompletionWaiter();
            String enterString = "Test data (disconnect_leave)";
            client1Channel.presence.enter(enterString, enterComplete);
            presenceWaiter.waitFor(testClientId1, Action.enter);
            PresenceMessage expectedPresent = presenceWaiter.contains(testClientId1, Action.enter);
            assertNotNull(expectedPresent);
            assertEquals(expectedPresent.data, enterString);

            /* verify enter callback called on completion */
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);

            /* close client1 connection and wait for the leave event to be delivered */
            clientAbly1.close();
            requiresClose = false;
            presenceWaiter.waitFor(testClientId1, Action.leave);
            PresenceMessage expectedLeft = presenceWaiter.contains(testClientId1, Action.leave);
            assertNotNull(expectedLeft);
            /* verify leave message contains data that was published with enter */
            assertEquals(expectedLeft.data, enterString);

        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(requiresClose)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * <p>
     * Validates channel removes all subscribers,
     * when {@code Channel#unsubscribe()} with no argument gets called.
     * </p>
     *
     * Tests RTP7a
     *
     * @throws AblyException
     */
    @Test
    public void realtime_presence_unsubscribe_all() throws AblyException {
        /* Ably instance that will emit presence events */
        AblyRealtime ably1 = null;
        /* Ably instance that will receive presence events */
        AblyRealtime ably2 = null;

        String channelName = "test.presence.unsubscribe.all" + System.currentTimeMillis();

        try {
            ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
            option1.clientId = "emitter client";
            ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
            option2.clientId = "receiver client";

            ably1 = new AblyRealtime(option1);
            ably2 = new AblyRealtime(option2);

            Channel channel1 = ably1.channels.get(channelName);
            channel1.attach();
            (new ChannelWaiter(channel1)).waitFor(ChannelState.attached);

            Channel channel2 = ably2.channels.get(channelName);
            channel2.attach();
            (new ChannelWaiter(channel2)).waitFor(ChannelState.attached);

            ArrayList<PresenceMessage> receivedMessageStack = new ArrayList<>();
            Presence.PresenceListener listener = new Presence.PresenceListener() {
                List<PresenceMessage> messageStack;

                @Override
                public void onPresenceMessage(PresenceMessage message) {
                    messageStack.add(message);
                }

                public Presence.PresenceListener setMessageStack(List<PresenceMessage> messageStack) {
                    this.messageStack = messageStack;
                    return this;
                }
            }.setMessageStack(receivedMessageStack);

            /* Subscribe using various alternatives of {@code Presence#subscribe()} */
            channel2.presence.subscribe(listener);
            channel2.presence.subscribe(Action.present, listener);
            channel2.presence.subscribe(EnumSet.of(Action.update, Action.leave), listener);

            /* Unsubscribe */
            channel2.presence.unsubscribe();

            /* Start emitting channel with ably client 1 (emitter) */
            channel1.presence.enter("Hello, #2!", null);
            channel1.presence.update("Lorem ipsum", null);
            channel1.presence.update("Dolor sit!", null);
            channel1.presence.leave(null);

            /* Wait until receiver client (ably2) observes {@code Action.leave}
             * is emitted from emitter client (ably1)
             */
            Helpers.PresenceWaiter leavePresenceWaiter = new Helpers.PresenceWaiter(channel2);
            leavePresenceWaiter.waitFor(ably1.options.clientId, Action.leave);

            /* Validate that we didn't received anything
             */
            assertThat(receivedMessageStack, is(emptyCollectionOf(PresenceMessage.class)));
        } finally {
            if (ably1 != null) ably1.close();
            if (ably2 != null) ably2.close();
        }
    }

    /**
     * <p>
     * Validates channel removes a subscriber,
     * when {@code Channel#unsubscribe()} gets called with a listener.
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void realtime_presence_unsubscribe_single() throws AblyException {
        /* Ably instance that will emit presence events */
        AblyRealtime ably1 = null;
        /* Ably instance that will receive presence events */
        AblyRealtime ably2 = null;

        String channelName = "test.presence.unsubscribe.single" + System.currentTimeMillis();

        try {
            ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
            option1.clientId = "emitter client";
            ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
            option2.clientId = "receiver client";

            ably1 = new AblyRealtime(option1);
            ably2 = new AblyRealtime(option2);

            Channel channel1 = ably1.channels.get(channelName);
            channel1.attach();
            (new ChannelWaiter(channel1)).waitFor(ChannelState.attached);

            Channel channel2 = ably2.channels.get(channelName);
            channel2.attach();
            (new ChannelWaiter(channel2)).waitFor(ChannelState.attached);

            ArrayList<PresenceMessage> receivedMessageStack = new ArrayList<>();
            Presence.PresenceListener listener = new Presence.PresenceListener() {
                List<PresenceMessage> messageStack;

                @Override
                public void onPresenceMessage(PresenceMessage message) {
                    messageStack.add(message);
                }

                public Presence.PresenceListener setMessageStack(List<PresenceMessage> messageStack) {
                    this.messageStack = messageStack;
                    return this;
                }
            }.setMessageStack(receivedMessageStack);

            /* Subscribe using various alternatives of {@code Presence#subscribe()} */
            channel2.presence.subscribe(listener);
            channel2.presence.subscribe(Action.present, listener);
            channel2.presence.subscribe(EnumSet.of(Action.update, Action.leave), listener);

            /* Unsubscribe */
            channel2.presence.unsubscribe(listener);

            /* Start emitting channel with ably client 1 (emitter) */
            channel1.presence.enter("Hello, #2!", null);
            channel1.presence.update("Lorem ipsum", null);
            channel1.presence.update("Dolor sit!", null);
            channel1.presence.leave(null);

            /* Wait until receiver client (ably2) observes {@code Action.leave}
             * is emitted from emitter client (ably1)
             */
            Helpers.PresenceWaiter leavePresenceWaiter = new Helpers.PresenceWaiter(channel2);
            leavePresenceWaiter.waitFor(ably1.options.clientId, Action.leave);

            /* Validate that we didn't received anything
             */
            assertThat(receivedMessageStack, is(emptyCollectionOf(PresenceMessage.class)));
        } finally {
            if (ably1 != null) ably1.close();
            if (ably2 != null) ably2.close();
        }
    }

    /**
     * <p>
     * Validates a client can observe presence messages of other client,
     * when they entered to the same channel and observing client subscribed
     * to multiple actions.
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void realtime_presence_subscribe_all() throws AblyException {
        /* Ably instance that will emit presence events */
        AblyRealtime ably1 = null;
        /* Ably instance that will receive presence events */
        AblyRealtime ably2 = null;

        String channelName = "test.presence.subscribe.all" + System.currentTimeMillis();

        try {
            ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
            option1.clientId = "emitter client";
            ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
            option2.clientId = "receiver client";

            ably1 = new AblyRealtime(option1);
            ably2 = new AblyRealtime(option2);

            Channel channel1 = ably1.channels.get(channelName);
            channel1.attach();
            (new ChannelWaiter(channel1)).waitFor(ChannelState.attached);

            Channel channel2 = ably2.channels.get(channelName);
            channel2.attach();
            (new ChannelWaiter(channel2)).waitFor(ChannelState.attached);

            ArrayList<PresenceMessage> receivedMessageStack = new ArrayList<>();
            channel2.presence.subscribe(new Presence.PresenceListener() {
                List<PresenceMessage> messageStack;

                @Override
                public void onPresenceMessage(PresenceMessage message) {
                    messageStack.add(message);
                }

                public Presence.PresenceListener setMessageStack(List<PresenceMessage> messageStack) {
                    this.messageStack = messageStack;
                    return this;
                }
            }.setMessageStack(receivedMessageStack));

            /* Start emitting channel with ably client 1 (emitter) */
            channel1.presence.enter("Hello, #2!", null);
            channel1.presence.update("Lorem ipsum", null);
            channel1.presence.update("Dolor sit!", null);
            channel1.presence.leave(null);

            /* Wait until receiver client (ably2) observes {@code Action.leave}
             * is emitted from emitter client (ably1)
             */
            Helpers.PresenceWaiter leavePresenceWaiter = new Helpers.PresenceWaiter(channel2);
            leavePresenceWaiter.waitFor(ably1.options.clientId, Action.leave);

            /* Validate that,
             *- we received all actions
             */
            assertThat(receivedMessageStack.size(), is(equalTo(4)));
            for (PresenceMessage message : receivedMessageStack) {
                assertThat(message.action, isOneOf(Action.enter, Action.update, Action.leave));
            }
        } finally {
            if (ably1 != null) ably1.close();
            if (ably2 != null) ably2.close();
        }
    }

    /**
     * <p>
     * Validates a client can observe presence messages of other client,
     * when they entered to the same channel and observing client subscribed
     * to multiple actions.
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void realtime_presence_subscribe_multiple() throws AblyException {
        /* Ably instance that will emit presence events */
        AblyRealtime ably1 = null;
        /* Ably instance that will receive presence events */
        AblyRealtime ably2 = null;

        String channelName = "test.presence.subscribe.multiple" + System.currentTimeMillis();
        EnumSet<PresenceMessage.Action> actions = EnumSet.of(Action.update, Action.leave);

        try {
            ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
            option1.clientId = "emitter client";
            ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
            option2.clientId = "receiver client";

            ably1 = new AblyRealtime(option1);
            ably2 = new AblyRealtime(option2);

            Channel channel1 = ably1.channels.get(channelName);
            channel1.attach();
            (new ChannelWaiter(channel1)).waitFor(ChannelState.attached);

            Channel channel2 = ably2.channels.get(channelName);
            channel2.attach();
            (new ChannelWaiter(channel2)).waitFor(ChannelState.attached);

            final ArrayList<PresenceMessage> receivedMessageStack = new ArrayList<>();
            channel2.presence.subscribe(actions, new Presence.PresenceListener() {
                @Override
                public void onPresenceMessage(PresenceMessage message) {
                    synchronized (receivedMessageStack) {
                        receivedMessageStack.add(message);
                        receivedMessageStack.notify();
                    }
                }
            });

            /* Start emitting channel with ably client 1 (emitter) */
            channel1.presence.enter("Hello, #2!", null);
            channel1.presence.update("Lorem ipsum", null);
            channel1.presence.update("Dolor sit!", null);
            channel1.presence.leave(null);

            /* Wait until receiver client (ably2) observes {@code Action.leave}
             * is emitted from emitter client (ably1)
             */
            try {
                synchronized (receivedMessageStack) {
                    while (receivedMessageStack.size() == 0 ||
                            !receivedMessageStack.get(receivedMessageStack.size()-1).clientId.equals(ably1.options.clientId) ||
                            receivedMessageStack.get(receivedMessageStack.size()-1).action != Action.leave)
                        receivedMessageStack.wait();
                }
            } catch(InterruptedException e) {}

            /* Validate that,
             *- we received specific actions
             */
            assertThat(receivedMessageStack.size(), is(equalTo(3)));
            for (PresenceMessage message : receivedMessageStack) {
                assertTrue(actions.contains(message.action));
            }
        } finally {
            if (ably1 != null) ably1.close();
            if (ably2 != null) ably2.close();
        }
    }

    /**
     * <p>
     * Validates a client can subscribe to presence without implicit channel attach
     * Refer Spec TB4, RTP6d, RTP6e
     * </p>
     * @throws AblyException
     */
    @Test
    public void presence_subscribe_without_implicit_attach() {
        String ablyChannel = "subscribe_" + testParams.name;
        AblyRealtime ably = null;
        try {
            ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
            option1.clientId = "client1";
            ably = new AblyRealtime(option1);

            /* create a channel and set attachOnSubscribe to false */
            final Channel channel = ably.channels.get(ablyChannel);
            ChannelOptions chOpts = new ChannelOptions();
            chOpts.attachOnSubscribe = false;
            channel.setOptions(chOpts);

            List<Boolean> receivedPresenceMsg = Collections.synchronizedList(new ArrayList<>());

            /* Check for all subscriptions without ATTACHING state */
            channel.presence.subscribe(m -> receivedPresenceMsg.add(true));
            assertEquals(ChannelState.initialized, channel.state);

            channel.presence.subscribe(Action.enter, m -> receivedPresenceMsg.add(true));
            assertEquals(ChannelState.initialized, channel.state);

            channel.presence.subscribe(EnumSet.of(Action.enter, Action.leave),m -> receivedPresenceMsg.add(true));
            assertEquals(ChannelState.initialized, channel.state);

            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);

            channel.presence.enter("enter client1", null);
            // Expecting 3 msg: one from the wildcard subscription and two from specific event subscription
            Exception conditionError = new Helpers.ConditionalWaiter().
                wait(() -> receivedPresenceMsg.size() == 3, 5000);
            assertNull(conditionError);

            receivedPresenceMsg.clear();
            channel.presence.leave(null);
            // Expecting 2 msg: one from the wildcard subscription and one from specific event subscription
            conditionError = new Helpers.ConditionalWaiter().
                wait(() -> receivedPresenceMsg.size() == 2, 5000);
            assertNull(conditionError);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("presence_subscribe_without_implicit_attach: Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * <p>
     * Validates a client can subscribe to presence without implicit channel attach
     * Refer Spec TB4, RTP6d, RTP6e
     * </p>
     * @throws AblyException
     */
    @Test
    public void presence_subscribe_without_implicit_attach_and_completion_listener_throws_exception() throws AblyException {
        String ablyChannel = "subscribe_" + testParams.name;
        ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
        option1.clientId = "client1";
        try (AblyRealtime ably = new AblyRealtime(option1)) {
            /* create a channel and set attachOnSubscribe to false */
            final Channel channel = ably.channels.get(ablyChannel);
            ChannelOptions chOpts = new ChannelOptions();
            chOpts.attachOnSubscribe = false;
            channel.setOptions(chOpts);

            // When completionWaiter passed with attachOnSubscribe=false, throws exception.
            CompletionWaiter completionWaiter = new CompletionWaiter();
            try {
                channel.presence.subscribe(m -> {}, completionWaiter);
            } catch (AblyException e) {
                assertEquals(400, e.errorInfo.statusCode);
                assertEquals(40000, e.errorInfo.code);
                assertThat(e.errorInfo.message, containsString("attachOnSubscribe=false doesn't expect attach completion callback"));
            }
            assertEquals(ChannelState.initialized, channel.state);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("presence_subscribe_without_implicit_attach: Unexpected exception");
        }
    }

    /**
     * <p>
     * Validates a client sending multiple presence updates when the channel is in the attaching
     * state will have all messages sent once the channel attaches, and all listeners will be called.
     * </p>
     *
     */
    @Test
    public void realtime_presence_update_multiple_queued_messages() throws AblyException {
        /* Ably instance that will emit presence events */
        AblyRealtime ably1 = null;
        /* Ably instance that will receive presence events */
        AblyRealtime ably2 = null;

        String channelName = "test.presence.subscribe.update_multiple_queued_messages" + System.currentTimeMillis();
        EnumSet<PresenceMessage.Action> actions = EnumSet.of(Action.update, Action.enter);

        try {
            ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
            option1.clientId = "emitter client";
            ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
            option2.clientId = "receiver client";

            ably1 = new AblyRealtime(option1);
            ably2 = new AblyRealtime(option2);

            Channel channel1 = ably1.channels.get(channelName);

            Channel channel2 = ably2.channels.get(channelName);
            channel2.attach();
            (new ChannelWaiter(channel2)).waitFor(ChannelState.attached);

            CompletionWaiter messageCompletionListener = new CompletionWaiter();

            final ArrayList<PresenceMessage> receivedMessageStack = new ArrayList<>();
            channel2.presence.subscribe(actions, new Presence.PresenceListener() {
                @Override
                public void onPresenceMessage(PresenceMessage message) {
                    synchronized (receivedMessageStack) {
                        receivedMessageStack.add(message);
                        receivedMessageStack.notify();
                    }
                }
            });

            /*
                Start emitting channel with ably client 1 (emitter)

                This is synchronized against the channel so that channel.setState cant mark
                the channel as attached until we're done queueing up events.
            */
            synchronized (channel1) {
                channel1.presence.enter("Hello, #2!", messageCompletionListener);
                channel1.presence.update("Lorem ipsum", messageCompletionListener);
                channel1.presence.update("Dolor sit!", messageCompletionListener);
            }

            /* Wait until receiver client (ably2) observes {@code Action.leave}
             * is emitted from emitter client (ably1)
             */
            try {
                synchronized (receivedMessageStack) {
                    while (receivedMessageStack.size() == 0 ||
                            !receivedMessageStack.get(receivedMessageStack.size()-1).clientId.equals(ably1.options.clientId) ||
                            !receivedMessageStack.get(receivedMessageStack.size()-1).data.equals("Dolor sit!"))
                                receivedMessageStack.wait();
                }
            } catch(InterruptedException ignored) {}

            /* Validate that,
             *- we received specific actions
             */
            assertThat(receivedMessageStack.size(), is(equalTo(3)));
            for (PresenceMessage message : receivedMessageStack) {
                assertTrue(actions.contains(message.action));
            }

            /*
             * Validate that
             * - our listeners are called within 10 seconds
             */
            messageCompletionListener.waitFor(3, 10000);
            assertTrue(messageCompletionListener.success);

        } finally {
            if (ably1 != null) ably1.close();
            if (ably2 != null) ably2.close();
        }
    }

    /**
     * <p>
     * Validates a client can observe presence messages of other client,
     * when they entered to the same channel and observing client subscribed
     * to a single action.
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void realtime_presence_subscribe_single() throws AblyException {
        /* Ably instance that will emit presence events */
        AblyRealtime ably1 = null;
        /* Ably instance that will receive presence events */
        AblyRealtime ably2 = null;

        String channelName = "test.presence.subscribe.single." + System.currentTimeMillis();
        PresenceMessage.Action action = Action.enter;

        try {
            ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
            option1.clientId = "emitter client";
            ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
            option2.clientId = "receiver client";

            ably1 = new AblyRealtime(option1);
            ably2 = new AblyRealtime(option2);

            Channel channel1 = ably1.channels.get(channelName);
            channel1.attach();
            (new ChannelWaiter(channel1)).waitFor(ChannelState.attached);

            Channel channel2 = ably2.channels.get(channelName);
            channel2.attach();
            (new ChannelWaiter(channel2)).waitFor(ChannelState.attached);

            ArrayList<PresenceMessage> receivedMessageStack = new ArrayList<>();
            channel2.presence.subscribe(action, new Presence.PresenceListener() {
                List<PresenceMessage> messageStack;

                @Override
                public void onPresenceMessage(PresenceMessage message) {
                    messageStack.add(message);
                }

                public Presence.PresenceListener setMessageStack(List<PresenceMessage> messageStack) {
                    this.messageStack = messageStack;
                    return this;
                }
            }.setMessageStack(receivedMessageStack));

            Helpers.PresenceWaiter waiter = new Helpers.PresenceWaiter(channel2);

            /* Start emitting presence with ably client 1 (emitter) */
            channel1.presence.enter("Hello, #2!", null);
            channel1.presence.updatePresence(new PresenceMessage(Action.update, ably1.options.clientId), null);
            channel1.presence.update("Lorem Ipsum", null);
            channel1.presence.leave(null);

            /* Wait until receiver client (ably2) observes {@code Action.leave}
             * is emitted from emitter client (ably1)
             */
            waiter.waitFor(ably1.options.clientId, Action.leave);

            /* Validate that,
             *- we received specific actions
             */
            assertThat(receivedMessageStack, is(not(empty())));
            for (PresenceMessage message : receivedMessageStack) {
                assertThat(message.action, is(equalTo(action)));
            }
        } finally {
            if (ably1 != null) ably1.close();
            if (ably2 != null) ably2.close();
        }
    }

    /**
     * <p>
     * Validate {@code Presence#subscribe(...)} will result in the listener not being
     * registered and an error being indicated, when the channel moves to the FAILED
     * state before the operation succeeds
     * </p>
     * <p>
     * Spec: RTP6c
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void realtime_presence_attach_implicit_subscribe_fail() throws AblyException {
        AblyRealtime ably = null;
        try {
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            final AblyRest ablyForToken = new AblyRest(optsForToken);
            final String channelName = "realtime_presence_attach_implicit_subscribe_fail" + testParams.name;

            /* get first token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            Capability capability = new Capability();
            capability.addResource("otherchannel", "publish");
            tokenParams.capability = capability.toString();
            tokenParams.clientId = testClientId1;

            Auth.TokenDetails token = ablyForToken.auth.requestToken(tokenParams, null);

            /* get second token */
            Auth.TokenParams tokenParams2 = new Auth.TokenParams();
            Capability capability2 = new Capability();
            capability2.addResource(channelName, "publish");
            capability2.addOperation(channelName, "presence");
            capability2.addOperation(channelName, "subscribe");
            tokenParams2.capability = capability2.toString();
            tokenParams2.clientId = testClientId1;

            final Auth.TokenDetails token2 = ablyForToken.auth.requestToken(tokenParams2, null);
            assertNotNull("Expected token value", token2.token);

            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.autoConnect = false;
            opts.tokenDetails = token;
            opts.clientId = testClientId1;
            ably = new AblyRealtime(opts);

            final ArrayList<PresenceMessage> presenceMessages = new ArrayList<>();
            Presence.PresenceListener listener = new Presence.PresenceListener() {
                @Override
                public void onPresenceMessage(PresenceMessage message) {
                    synchronized (presenceMessages) {
                        presenceMessages.add(message);
                        presenceMessages.notify();
                    }
                }
            };

            /* create a channel and subscribe, implicitly initiate attach */
            CompletionWaiter completionWaiter = new CompletionWaiter();
            final Channel channel = ably.channels.get(channelName);
            channel.presence.subscribe(listener, completionWaiter);

            ably.connection.connect();

            completionWaiter.waitFor(1);
            assertFalse("Verify subscribe failed", completionWaiter.success);
            assertEquals("Verify subscribe failure error status", completionWaiter.error.statusCode, 401);
            assertEquals("Verify failed state reached", channel.state, ChannelState.failed);

            try {
                channel.presence.subscribe(new PresenceWaiter(channel));
                fail("Presence.subscribe() shouldn't succeed");
            } catch (AblyException e) {
                assertEquals("Verify failure error code", e.errorInfo.code, 90001);
            }

            /* Change token to allow channel subscription so we can enter client and verify listener was set despite the failure */
            final boolean[] authUpdated = new boolean[]{false};
            ably.connection.on(ConnectionEvent.update, new ConnectionStateListener() {
                        @Override
                        public void onConnectionStateChanged(ConnectionStateChange state) {
                            synchronized (authUpdated) {
                                authUpdated[0] = true;
                                authUpdated.notify();
                            }
                        }
                    });


            ably.auth.authorize(null, new Auth.AuthOptions() {{
                tokenDetails = token2;
            }});

            try {
                synchronized (authUpdated) {
                    while (!authUpdated[0])
                        authUpdated.wait();
                }
            } catch (InterruptedException e) {}

            channel.attach();
            new ChannelWaiter(channel).waitFor(ChannelState.attached);

            /* Now to ensure listener was set despite the error we enter a client */
            channel.presence.enter(null, null);
            try {
                synchronized (presenceMessages) {
                    while (presenceMessages.size() == 0)
                        presenceMessages.wait();
                }
            } catch (InterruptedException e) {}

            assertTrue("Verify listener was set despite channel attach failure",
                    presenceMessages.size() == 1 &&
                    presenceMessages.get(0).action == Action.enter && presenceMessages.get(0).clientId.equals(testClientId1));

        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * <p>
     * Validate {@code Presence#enter(...)} will result in the listener not being
     * registered and an error being indicated, when the channel moves to the
     * FAILED state before the operation succeeds
     * </p>
     * <p>
     * Spec: RTP8d
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void realtime_presence_attach_implicit_enter_fail() throws AblyException {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[1].keyStr);
            opts.clientId = "theClient";
            ably = new AblyRealtime(opts);

            /* wait until connected */
            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and subscribe */
            final Channel channel = ably.channels.get("enter_fail_" + testParams.name);
            CompletionWaiter completionWaiter = new CompletionWaiter();
            channel.presence.enter("Lorem Ipsum", completionWaiter);
            assertEquals("Verify attaching state reached", channel.state, ChannelState.attaching);

            ErrorInfo errorInfo = completionWaiter.waitFor();

            new ChannelWaiter(channel).waitFor(ChannelState.failed);
            assertEquals("Verify failed state reached", channel.state, ChannelState.failed);
            assertEquals("Verify reason code gives correct failure reason", errorInfo.statusCode, 401);
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * <p>
     * Validate {@code Presence#get(...)} will result in an error, when the channel
     * moves to the FAILED state before the operation succeeds
     * </p>
     * <p>
     * Spec: RTP11b
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void realtime_presence_attach_implicit_get_fail() throws AblyException {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[1].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and subscribe */
            final Channel channel = ably.channels.get("get_fail");
            channel.presence.get(false);
            assertEquals("Verify attaching state reached", channel.state, ChannelState.attaching);

            ErrorInfo fail = new ChannelWaiter(channel).waitFor(ChannelState.failed);
            assertEquals("Verify failed state reached", channel.state, ChannelState.failed);
            assertEquals("Verify reason code gives correct failure reason", fail.statusCode, 401);
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * <p>
     * Validate {@code Presence#enterClient(...)} will result in the listener not being
     * registered and an error being indicated, when the channel moves to the FAILED
     * state before the operation succeeds
     * </p>
     * <p>
     * Spec: RTP15e
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void realtime_presence_attach_implicit_enterclient_fail() throws AblyException {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[1].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and subscribe */
            final Channel channel = ably.channels.get("enterclient_fail_" + testParams.name);
            CompletionWaiter completionWaiter = new CompletionWaiter();
            channel.presence.enterClient("theClient", "Lorem Ipsum", completionWaiter);
            assertEquals("Verify attaching state reached", channel.state, ChannelState.attaching);

            ErrorInfo errorInfo = completionWaiter.waitFor();

            new ChannelWaiter(channel).waitFor(ChannelState.failed);
            assertEquals("Verify failed state reached", channel.state, ChannelState.failed);
            assertEquals("Verify reason code gives correct failure reason", errorInfo.statusCode, 401);
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * <p>
     * Validate {@code Presence#updateClient(...)} will result in the listener not being
     * registered and an error being indicated, when the channel is in or moves to the
     * FAILED state before the operation succeeds
     * </p>
     * <p>
     * Spec: RTP15e
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void realtime_presence_attach_implicit_updateclient_fail() throws AblyException {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[1].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and subscribe */
            final Channel channel = ably.channels.get("updateclient_fail_" + testParams.name);
            CompletionWaiter completionWaiter = new CompletionWaiter();
            channel.presence.updateClient("theClient", "Lorem Ipsum", completionWaiter);
            assertEquals("Verify attaching state reached", channel.state, ChannelState.attaching);

            ErrorInfo errorInfo = completionWaiter.waitFor();

            new ChannelWaiter(channel).waitFor(ChannelState.failed);
            assertEquals("Verify failed state reached", channel.state, ChannelState.failed);
            assertEquals("Verify reason code gives correct failure reason", errorInfo.statusCode, 401);
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * <p>
     * Validate {@code Presence#leaveClient(...)} will result in the listener not being
     * registered and an error being indicated, when the channel is in or moves to the
     * FAILED state before the operation succeeds
     * </p>
     * <p>
     * Spec: RTP15e
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void realtime_presence_attach_implicit_leaveclient_fail() throws AblyException {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[1].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and subscribe */
            final Channel channel = ably.channels.get("leaveclient_fail+" + testParams.name);
            CompletionWaiter completionWaiter = new CompletionWaiter();
            channel.presence.leaveClient("theClient", "Lorem Ipsum", completionWaiter);
            assertEquals("Verify attaching state reached", channel.state, ChannelState.attaching);
            completionWaiter.waitFor();

            ErrorInfo errorInfo = completionWaiter.waitFor();

            new ChannelWaiter(channel).waitFor(ChannelState.failed);
            assertEquals("Verify failed state reached", channel.state, ChannelState.failed);
            assertEquals("Verify reason code gives correct failure reason", errorInfo.statusCode, 401);
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * <p>
     * Validate {@code Presence#get(...)} throws an exception, when the channel
     * is in the FAILED state
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void realtime_presence_get_throws_when_channel_failed() throws AblyException {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[1].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and subscribe */
            final Channel channel = ably.channels.get("get_fail");
            channel.attach();
            new ChannelWaiter(channel).waitFor(ChannelState.failed);

            try {
                channel.presence.get(false);
                fail("Presence#get(...) should throw an exception when channel is in failed state");
            } catch(AblyException e) {
                assertThat(e.errorInfo.code, is(equalTo(90001)));
                assertThat(e.errorInfo.message, is(equalTo("channel operation failed (invalid channel state)")));
            }
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Test if after reattach when returning from suspended mode client re-enters the channel with the same data
     * @throws AblyException
     *
     * Tests RTP17, RTP19, RTP19a, RTP5f, RTP6b
     */
    @Test
    public void realtime_presence_suspended_reenter() throws Exception {
        MockWebsocketFactory mockTransport = new MockWebsocketFactory();
        DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
        fillInOptions(opts);
        opts.transportFactory = mockTransport;
        final String channelName = "presence_suspended_reenter" + testParams.name;
        mockTransport.allowSend();

        try (AblyRealtime ably = new AblyRealtime(opts)) {

            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);

            final Channel channel = ably.channels.get(channelName);
            channel.attach();
            ChannelWaiter channelWaiter = new ChannelWaiter(channel);

            channelWaiter.waitFor(ChannelState.attached);

            final String presenceData = "PRESENCE_DATA";

            final ArrayList<PresenceMessage> leaveMessages = new ArrayList<>();
            /* Subscribe for message type, test RTP6b */
            channel.presence.subscribe(Action.leave, new Presence.PresenceListener() {
                @Override
                public void onPresenceMessage(PresenceMessage message) {
                    leaveMessages.add(message);
                }
            });

            channel.presence.enterClient(testClientId1, presenceData);

            /*
             * We put testClientId2 presence data into the client library presence map but we
             * don't send it to the server
             */
            ProtocolMessage msg = new ProtocolMessage();
            msg.connectionId = "randomConnectionId";
            msg.action = ProtocolMessage.Action.sync;
            msg.channel = channelName;
            msg.presence = new PresenceMessage[]{
                new PresenceMessage() {{
                    action = Action.present;
                    id = String.format("%s:0:0", "randomConnectionId");
                    timestamp = System.currentTimeMillis();
                    clientId = testClientId2;
                    connectionId = "randomConnectionId";
                    data = presenceData;
                }}
            };
            ably.connection.connectionManager.onMessage(null, msg);

            ably.connection.connectionManager.requestState(ConnectionState.suspended);
            channelWaiter.waitFor(ChannelState.suspended);

            /*
             * When restoring from suspended state server will send sync message erasing
             * testClientId2 record from the presence map.
             */
            ably.connection.connectionManager.requestState(ConnectionState.connected);
            channelWaiter.waitFor(ChannelState.attached);
            long reconnectTimestamp = System.currentTimeMillis();

            PaginatedResult<PresenceMessage> presentMembers;

            try (AblyRest restClient = new AblyRest(opts)) {
                long timeout = 10_000;
                presentMembers = restClient.channels.get(channelName).presence.get(null);
                while (presentMembers.items().length != 1 && System.currentTimeMillis() - reconnectTimestamp < timeout) {
                    Thread.sleep(250);
                    presentMembers = restClient.channels.get(channelName).presence.get(null);
                }
            }

            assertEquals("Verify presence data is received by the server",
                1, presentMembers.items().length);

            assertEquals(testClientId1, presentMembers.items()[0].clientId);
            assertEquals(presenceData, presentMembers.items()[0].data);

            assertEquals("Verify exactly one LEAVE message was generated", leaveMessages.size(), 1);

            PresenceMessage leaveMessage = leaveMessages.get(0);
            assertEquals("Verify LEAVE message follows specs",leaveMessage.action, Action.leave);
            assertEquals("Verify LEAVE message follows specs",leaveMessage.clientId, testClientId2);
            assertEquals("Verify LEAVE message follows specs",leaveMessage.data, presenceData);
            assertTrue("Verify LEAVE message follows specs", Math.abs(leaveMessage.timestamp-reconnectTimestamp) < 2000);
        }
    }

    /**
     * Test presence message map behaviour (RTP2 features)
     * Tests RTP2a, RTP2b1, RTP2b2, RTP2c, RTP2d, RTP2g, RTP18c, RTP6a features
     */
    @Test
    public void realtime_presence_map_test() {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);
            final String channelName = "newness_comparison_" + testParams.name;
            Channel channel = ably.channels.get(channelName);
            channel.attach();
            ChannelWaiter channelWaiter = new ChannelWaiter(channel);
            channelWaiter.waitFor(ChannelState.attached);

            final String wontPass = "Won't pass newness test";

            Presence presence = channel.presence;
            final ArrayList<PresenceMessage> presenceMessages = new ArrayList<>();
            /* Subscribe for all the message types, test RTP6a */
            presence.subscribe(new Presence.PresenceListener() {
                @Override
                public void onPresenceMessage(PresenceMessage message) {
                    synchronized (presenceMessages) {
                        assertNotEquals("Verify wrong message didn't pass the newness test",
                                message.data, wontPass);
                        // To exclude leave messages that sometimes sneak in let's collect only enter and update messages
                        if (message.action == Action.enter || message.action == Action.update) {
                            presenceMessages.add(message);
                        }
                    }
                }
            });

            /* Test message newness criteria as described in RTP2b */
            final PresenceMessage[] testData = new PresenceMessage[] {
                    new PresenceMessage() {{
                        clientId = "1";
                        action = Action.enter;
                        connectionId = "1";
                        id = "1:0";
                    }},
                    new PresenceMessage() {{
                        clientId = "2";
                        action = Action.enter;
                        connectionId = "2";
                        id = "2:1:0";
                    }},
                    /* Should be newer than previous one */
                    new PresenceMessage() {{
                        clientId = "2";
                        action = Action.update;
                        connectionId = "2";
                        id = "2:2:1";
                        timestamp = 1;
                    }},
                    /* Shouldn't pass newness test because of message serial, timestamp doesn't matter in this case */
                    new PresenceMessage() {{
                        clientId = "2";
                        action = Action.update;
                        connectionId = "2";
                        id = "2:1:1";
                        timestamp = 2;
                        data = wontPass;
                    }},
                    /* Shouldn't pass because of message index */
                    new PresenceMessage() {{
                        clientId = "2";
                        action = Action.update;
                        connectionId = "2";
                        id = "2:2:0";
                        data = wontPass;
                    }},
                    /* Should pass because id is not in form connId:clientId:index and timestamp is greater */
                    new PresenceMessage() {{
                        clientId = "2";
                        action = Action.update;
                        connectionId = "2";
                        id = "weird_id";
                        timestamp = 1000;
                    }},
                    /* Shouldn't pass because of timestamp */
                    new PresenceMessage() {{
                        clientId = "2";
                        action = Action.update;
                        connectionId = "2";
                        id = "2:3:1";
                        timestamp = 500;
                        data = wontPass;
                    }}
            };

            for (final PresenceMessage msg: testData) {
                ProtocolMessage protocolMessage = new ProtocolMessage() {{
                        channel = channelName;
                        action = Action.presence;
                        presence = new PresenceMessage[]{msg};
                    }};

                ably.connection.connectionManager.onMessage(null, protocolMessage);
            }

            int n = 0;
            for (PresenceMessage testMsg: testData) {
                if (testMsg.data != wontPass) {
                    PresenceMessage factualMsg = n < presenceMessages.size() ? presenceMessages.get(n++) : null;
                    assertTrue("Verify message passed newness test",
                            factualMsg != null && factualMsg.id.equals(testMsg.id));
                    assertEquals("Verify message was emitted on the presence object with original action",
                            factualMsg.action, testMsg.action);
                    assertEquals("Verify message was added to the presence map and stored with PRESENT action",
                            presence.get(testMsg.clientId, false)[0].action, Action.present);
                }
            }
            assertEquals("Verify nothing else passed the newness test", n, presenceMessages.size());

            /* Repeat the process now as a part of SYNC and verify everything is exactly the same */
            final String channel2Name = "sync_newness_comparison_" + testParams.name;
            Channel channel2 = ably.channels.get(channel2Name);
            channel2.attach();
            new ChannelWaiter(channel2).waitFor(ChannelState.attached);

            /* Send all the presence data in one SYNC message without channelSerial (RTP18c) */
            ProtocolMessage syncMessage = new ProtocolMessage() {{
                channel = channel2Name;
                action = Action.sync;
                presence = testData.clone();
            }};
            final ArrayList<PresenceMessage> syncPresenceMessages = new ArrayList<>();
            channel2.presence.subscribe(new Presence.PresenceListener() {
                @Override
                public void onPresenceMessage(PresenceMessage message) {
                    syncPresenceMessages.add(message);
                }
            });
            ably.connection.connectionManager.onMessage(null, syncMessage);

            assertEquals("Verify result is the same in case of SYNC", syncPresenceMessages.size(), presenceMessages.size());
            for (int i=0; i<syncPresenceMessages.size(); i++)
                assertTrue("Verify result is the same in case of SYNC",
                        syncPresenceMessages.get(i).id.equals(presenceMessages.get(i).id) &&
                        syncPresenceMessages.get(i).action.equals(presenceMessages.get(i).action));
        }
        catch (AblyException e) {
            System.out.println("Ably exception thrown in realtime_presence_map_test " + e);
            fail("Ably exception thrown in realtime_presence_map_test " + e);
        }
        finally {
            if (ably != null)
                ably.close();
        }
    }

    /**
     * Enter large (>100) number of clients so there are several sync messages, disconnect transport
     * in the middle and verify channel is re-syncing presence messages after transport reconnect
     *
     * Tests RTP3
     */
    @Test
    public void reattach_resume_broken_sync() {
        AblyRealtime clientAbly1 = null;
        AblyRealtime clientAbly2 = null;
        TestChannel testChannel = new TestChannel();
        int clientCount = 150; /* Should be greater than 100 to break sync into several messages */
        try {
            /* subscribe for presence events in the anonymous connection */
            new PresenceWaiter(testChannel.realtimeChannel);

            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions(testVars.keys[0].keyStr);
            fillInOptions(client1Opts);
            client1Opts.tokenDetails = wildcardToken;
            clientAbly1 = new AblyRealtime(client1Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            client1Channel.attach();
            (new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

            /* let client1 enter the channel for multiple clients and wait for the success callback */
            CompletionSet enterComplete = new CompletionSet();
            for(int i = 0; i < clientCount; i++) {
                client1Channel.presence.enterClient("client" + i, "Test data (attach_enter_multiple) " + i, enterComplete.add());
            }
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.pending.isEmpty());
            assertTrue("Verify no enter errors", enterComplete.errors.isEmpty());

            /* set up a second connection with different clientId */
            final MockWebsocketFactory mockTransport20 = new MockWebsocketFactory();
            DebugOptions client2Opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(client2Opts);
            client2Opts.transportFactory = mockTransport20;
            client2Opts.tokenDetails = token2;
            client2Opts.clientId = testClientId2;
            client2Opts.autoConnect = false;

            mockTransport20.allowSend();
            clientAbly2 = new AblyRealtime(client2Opts);

            /* wait until connected */
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(clientAbly2.connection);
            clientAbly2.connection.connect();
            connectionWaiter.waitFor(ConnectionState.connected);

            /* get channel */
            final Channel client2Channel = clientAbly2.channels.get(testChannel.channelName);
            final ConnectionManager connectionManager = clientAbly2.connection.connectionManager;
            final boolean[] disconnectedTransport = new boolean[]{false};
            final int[] presenceCount = new int[]{0};
            client2Channel.attach(new CompletionListener() {
                @Override
                public void onSuccess() {
                    try {
                        client2Channel.presence.subscribe(new Presence.PresenceListener() {
                            @Override
                            public void onPresenceMessage(PresenceMessage message) {
                                if (!disconnectedTransport[0]) {
                                    mockTransport20.lastCreatedTransport.close();
                                    connectionManager.onTransportUnavailable(mockTransport20.lastCreatedTransport, new ErrorInfo("Mock", 50000));

                                }
                                disconnectedTransport[0] = true;
                                presenceCount[0]++;
                            }
                        });
                    }
                    catch (AblyException e) {
                    }
                }

                @Override
                public void onError(ErrorInfo reason) {
                }
            });

            ChannelWaiter channelWaiter = new ChannelWaiter(client2Channel);
            channelWaiter.waitFor(ChannelState.attached);

            /* Wait for reconnect */
            connectionWaiter.waitFor(ConnectionState.connected, 2);

            client2Channel.presence.unsubscribe();

            /* Verify that channel received sync and all 150 presence messages are received */
            try {
                Thread.sleep(500);
                assertEquals("Verify number of received presence messages", client2Channel.presence.get(true).length, clientCount);
            } catch (InterruptedException e) {}

        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(clientAbly2 != null)
                clientAbly2.close();
            testChannel.dispose();
        }
    }

    /**
     * Test if presence sync works as it should
     * Tests RTP18a, RTP18b, RTP2f
     */
    @Test
    public void presence_sync() {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            final String channelName = "presence_sync_test" + testParams.name;

            final Channel channel = ably.channels.get(channelName);
            channel.attach();
            ChannelWaiter channelWaiter = new ChannelWaiter(channel);
            channelWaiter.waitFor(ChannelState.attached);

            final ArrayList<PresenceMessage> presenceHistory = new ArrayList<>();
            channel.presence.subscribe(new Presence.PresenceListener() {
                @Override
                public void onPresenceMessage(PresenceMessage message) {
                    presenceHistory.add(message);
                }
            });

            final PresenceMessage[] testPresence1 = new PresenceMessage[] {
                    /* Will be discarded because we'll start new sync with different channelSerial */
                    new PresenceMessage() {{
                        clientId = "1";
                        action = Action.enter;
                        connectionId = "1";
                        id = "1:0";
                    }}
            };

            final PresenceMessage[] testPresence2 = new PresenceMessage[] {
                    new PresenceMessage() {{
                        clientId = "2";
                        action = Action.enter;
                        connectionId = "2";
                        id = "2:1:0";
                    }},
                    /* Enter presence message here is newer than leave in the subsequent message */
                    new PresenceMessage() {{
                        clientId = "3";
                        action = Action.enter;
                        connectionId = "3";
                        id = "3:1:0";
                    }}
            };

            final PresenceMessage[] testPresence3 = new PresenceMessage[] {
                    new PresenceMessage() {{
                        clientId = "3";
                        action = Action.leave;
                        connectionId = "3";
                        id = "3:0:0";
                    }},
                    new PresenceMessage() {{
                        clientId = "4";
                        action = Action.enter;
                        connectionId = "4";
                        id = "4:1:1";
                    }},
                    new PresenceMessage() {{
                        clientId = "4";
                        action = Action.leave;
                        connectionId = "4";
                        id = "4:2:2";
                    }}
            };

            final boolean[] seenLeaveMessageAsAbsentForClient4 = new boolean[] {false};
            channel.presence.subscribe(Action.leave, new Presence.PresenceListener() {
                @Override
                public void onPresenceMessage(PresenceMessage message) {
                    try {
                        /*
                         * Do not call it in states other than ATTACHED because of presence.get() side
                         * effect of attaching channel
                         */
                        if (message.clientId.equals("4") && message.action == Action.leave && channel.state == ChannelState.attached) {
                            /*
                             * Client library won't return a presence message if it is stored as ABSENT
                             * so the result of the presence.get() call should be empty. This is the
                             * only case when get() called from PresenceListener.onPresenceMessage results
                             * in an empty answer.
                             */
                            seenLeaveMessageAsAbsentForClient4[0] = channel.presence.get("4", false).length == 0;
                        }
                    } catch (AblyException e) {}
                }
            });

            ably.connection.connectionManager.onMessage(null, new ProtocolMessage() {{
                action = Action.sync;
                channel = channelName;
                channelSerial = "1:1";
                presence = testPresence1;
            }});
            ably.connection.connectionManager.onMessage(null, new ProtocolMessage() {{
                action = Action.sync;
                channel = channelName;
                channelSerial = "2:1";
                presence = testPresence2;
            }});
            ably.connection.connectionManager.onMessage(null, new ProtocolMessage() {{
                action = Action.sync;
                channel = channelName;
                channelSerial = "2:";
                presence = testPresence3;
            }});

            assertEquals("Verify incomplete sync was discarded", channel.presence.get("1", false).length, 0);
            assertEquals("Verify client with id==2 is in presence map", channel.presence.get("2", false).length, 1);
            assertEquals("Verify client with id==3 is in presence map", channel.presence.get("3", false).length, 1);
            assertEquals("Verify nothing else is in presence map", channel.presence.get(false).length, 2);

            assertTrue("Verify LEAVE message for client with id==4 was stored as ABSENT", seenLeaveMessageAsAbsentForClient4[0]);

            PresenceMessage[] correctPresenceHistory = new PresenceMessage[] {
                    /* client 1 enters (will later be discarded) */
                    new PresenceMessage(Action.enter, "1"),
                    /* client 2 enters */
                    new PresenceMessage(Action.enter, "2"),
                    /* client 3 enters and never leaves because of newness comparison for LEAVE fails */
                    new PresenceMessage(Action.enter, "3"),
                    /* client 4 enters and leaves */
                    new PresenceMessage(Action.enter, "4"),
                    new PresenceMessage(Action.leave, "4"),
                    /* client 1 is eliminated from the presence map because the first portion of SYNC is discarded */
                    new PresenceMessage(Action.leave, "1")
            };

            assertEquals("Verify number of presence messages", presenceHistory.size(), correctPresenceHistory.length);
            for (int i=0; i<correctPresenceHistory.length; i++) {
                PresenceMessage factualMsg = presenceHistory.get(i);
                PresenceMessage correctMsg = correctPresenceHistory[i];
                assertTrue("Verify presence message correctness",
                        factualMsg.clientId.equals(correctMsg.clientId) && factualMsg.action == correctMsg.action);
            }

        } catch (AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if (ably != null)
                ably.close();
        }
    }

    /*
     * Test channel state change effect on presence
     * Tests RTP5a, RTP5b, RTP5c3, RTP16b
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void presence_state_change () {
        AblyRealtime ably = null;
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            opts.autoConnect = false;  /* to queue presence messages */

            final MockWebsocketFactory mockTransport = new MockWebsocketFactory();
            opts.transportFactory = mockTransport;
            ably = new AblyRealtime(opts);

            final String channelName = "presence_state_change" + testParams.name;
            Channel channel = ably.channels.get(channelName);
            /*
             * This will queue the message, initiate channel attach and send it
             * when the channel becomes attached (to test RTP5c)
             *
             * Also the connection state is INITIALIZED at the moment (to test RTP16b)
             */
            channel.presence.enterClient(testClientId1);

            /* Connect */
            ably.connection.connect();

            ChannelWaiter channelWaiter = new ChannelWaiter(channel);
            channelWaiter.waitFor(ChannelState.attached);

            PresenceWaiter presenceWaiter = new PresenceWaiter(channel);
            presenceWaiter.waitFor(1);

            PresenceMessage[] presenceMessages = channel.presence.get(false);
            assertEquals(presenceMessages.length, 1);

            mockTransport.blockSend();
            /* Inject something into internal presence map */
            final String connId = ably.connection.id;
            channel.presence.enterClient(testClientId2);

            ProtocolMessage msg = new ProtocolMessage() {{
                    connectionId = connId;
                    action = ProtocolMessage.Action.sync;
                    channel = channelName;
                    presence = new PresenceMessage[] {
                            new PresenceMessage() {{
                                action = Action.present;
                                id = String.format("%s:0:0", connId);
                                timestamp = System.currentTimeMillis();
                                clientId = testClientId2;
                                connectionId = connId;
                            }}
                    };
            }};
            ably.connection.connectionManager.onMessage(null, msg);

            mockTransport.allowSend();

            channel.detach();
            channelWaiter.waitFor(ChannelState.detached);

            /* Verify that presence map is cleared on DETACH (RTP5a) */
            /* As a side effect this operation will initiate reattach of the channel but it doesn't matter here */
            assertEquals("Verify presence map is cleared on DETACH", channel.presence.get(false).length, 0);

            /*
             * Reconnect and verify that client2 is not automatically re-entered i.e.
             * internal presence map is cleared as well
             */
            channel.attach();
            Thread.sleep(500);
            assertEquals("Verify internal presence map is cleared on DETACH", channel.presence.get(false).length, 0);

            /* Test failure of presence re-enter */
            mockTransport.blockSend();
            /* Let's add something to the presence map */
            channel.presence.enterClient(testClientId2);
            ably.connection.connectionManager.onMessage(null, msg);
            mockTransport.failSend(new MockWebsocketFactory.MessageFilter() {
                @Override
                public boolean matches(ProtocolMessage message) {
                    return message.action == ProtocolMessage.Action.presence;
                }
            });

            final boolean[] reenterFailureReceived = new boolean[] {false};
            channel.on(ChannelEvent.update, new ChannelStateListener() {
                @Override
                public void onChannelStateChanged(ChannelStateChange stateChange) {
                    if (stateChange.reason.code == 91004)
                        reenterFailureReceived[0] = true;
                }
            });

            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            ably.connection.connectionManager.requestState(ConnectionState.suspended);
            connectionWaiter.waitFor(ConnectionState.suspended);
            ably.connection.connectionManager.requestState(ConnectionState.connected);
            connectionWaiter.waitFor(ConnectionState.connected);
            Thread.sleep(500);

            assertTrue("Verify re-enter presence message failed", reenterFailureReceived[0]);
        } catch (AblyException|InterruptedException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if (ably != null)
                ably.close();
        }
    }

    /**
     * Enter channel without subscribe permission, expect presence message from this connection
     * Test RTP17a
     *
     * Not functional yet
     */
    @Test
    public void presence_without_subscribe_capability() throws AblyException {
        String channelName = "presence_without_subscribe" + testParams.name;
        AblyRealtime ably = null;
        String presenceData = "presence_test_data";

        try {
            /* init ably for token */
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            final AblyRest ablyForToken = new AblyRest(optsForToken);

            /* get first token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            Capability capability = new Capability();
            capability.addResource(channelName, "publish");
            capability.addOperation(channelName, "presence");
            //capability.addOperation(channelName, "subscribe");
            tokenParams.capability = capability.toString();
            tokenParams.clientId = testClientId1;

            Auth.TokenDetails token = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", token.token);

            ClientOptions opts = createOptions();
            opts.clientId = testClientId1;
            opts.tokenDetails = token;
            ably = new AblyRealtime(opts);

            Channel channel = ably.channels.get(channelName);
            channel.attach();

            ChannelWaiter channelWaiter = new ChannelWaiter(channel);
            channelWaiter.waitFor(ChannelState.attached);

            channel.presence.enterClient(testClientId1, presenceData, new CompletionListener() {
                @Override
                public void onSuccess() {
                    System.out.println("Success");
                }

                @Override
                public void onError(ErrorInfo reason) {
                    System.out.println("failure");
                }
            });
            PresenceWaiter presenceWaiter = new PresenceWaiter(Action.enter, channel);
            presenceWaiter.waitFor(1);

            PresenceMessage[] presenceMessages = channel.presence.get(testClientId1, false);
            assertEquals("Verify total number of received presence messages", presenceMessages.length, 1);
            assertEquals("Verify present message is valid", presenceMessages[0].data, presenceData);
        } finally {
            if (ably != null)
                ably.close();
        }
    }

    /**
     * Test if Presence.syncComplete() works. Enter client on one ably connection and
     * wait for initial SYNC on another connection.
     *
     * Tests RTP13
     */
    @Test
    public void sync_complete() {
        AblyRealtime ably1 = null, ably2 = null;
        final String channelName = "sync_complete" + testParams.name;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably1 = new AblyRealtime(opts);
            ably2 = new AblyRealtime(opts);

            Channel channel1 = ably1.channels.get(channelName);
            channel1.presence.enterClient(testClientId1);
            new PresenceWaiter(Action.enter, channel1).waitFor(1);

            Channel channel2 = ably2.channels.get(channelName);
            assertFalse("Verify SYNC is not complete yet", channel2.presence.syncComplete);
            channel2.attach();
            /* Wait for the SYNC to complete */
            new PresenceWaiter(Action.present, channel2).waitFor(1);
            channel2.presence.get(true);
            /* Initial SYNC should be complete at this point */
            assertTrue("Verify SYNC is complete", channel2.presence.syncComplete);
        } catch (AblyException e) {
            fail("Unexpected exception");
        } finally {
            if (ably1 != null)
                ably1.close();
            if (ably2 != null)
                ably2.close();
        }
    }

    /**
     * Enter client without permission to do so, check exception
     * Tests RTP8h
     */
    @Test
    public void presence_enter_without_permission() throws AblyException {
        String channelName = "presence_enter_without_permission" + testParams.name;
        AblyRealtime ably = null;

        try {
            /* init ably for token */
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            final AblyRest ablyForToken = new AblyRest(optsForToken);

            /* get first token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            Capability capability = new Capability();
            capability.addResource(channelName, "publish"); /* no presence permission! */
            tokenParams.capability = capability.toString();
            tokenParams.clientId = testClientId1;

            Auth.TokenDetails token = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", token.token);

            ClientOptions opts = createOptions();
            opts.clientId = testClientId1;
            opts.tokenDetails = token;
            ably = new AblyRealtime(opts);

            Channel channel = ably.channels.get(channelName);
            ChannelWaiter channelWaiter = new ChannelWaiter(channel);
            channel.attach();
            channelWaiter.waitFor(ChannelState.attached);

            CompletionWaiter enterWaiter = new CompletionWaiter();
            channel.presence.enterClient(testClientId1, null, enterWaiter);
            ErrorInfo enterError = enterWaiter.waitFor();
            assertNotNull("Verify enter client failed", enterError);

        } finally {
            if (ably != null)
                ably.close();
        }
    }

    /**
     * Enter wrong client (mismatching one set in the token), check exception
     */
    @Test
    public void presence_enter_mismatched_clientid() throws AblyException {
        String channelName = "presence_enter_mismatched_clientid" + testParams.name;
        AblyRealtime ably = null;

        try {
            /* init ably for token */
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            final AblyRest ablyForToken = new AblyRest(optsForToken);

            /* get first token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            Capability capability = new Capability();
            capability.addResource(channelName, "publish");
            capability.addOperation(channelName, "presence");
            tokenParams.capability = capability.toString();
            tokenParams.clientId = testClientId1;

            Auth.TokenDetails token = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", token.token);

            ClientOptions opts = createOptions();
            opts.clientId = testClientId1;
            opts.tokenDetails = token;
            ably = new AblyRealtime(opts);

            Channel channel = ably.channels.get(channelName);
            channel.attach();

            ChannelWaiter channelWaiter = new ChannelWaiter(channel);
            channelWaiter.waitFor(ChannelState.attached);

            /* should succeed with testClientId1 */
            CompletionWaiter enterWaiter = new CompletionWaiter();
            channel.presence.enterClient(testClientId1, null, enterWaiter);
            ErrorInfo enterErr = enterWaiter.waitFor();

            assertNull("Verify enter client succeeded", enterErr);

            /* should fail with testClientId2 */
            enterWaiter.reset();
            channel.presence.enterClient(testClientId2, null, enterWaiter);
            enterErr = enterWaiter.waitFor();

            assertNotNull("Verify enter client failed", enterErr);

        } finally {
            if (ably != null)
                ably.close();
        }
    }

    /**
     * Attempt to enterClient() etc with a null clientId; check exception
     */
    @Test
    public void presence_enterclient_null_clientid() throws AblyException {
        String channelName = "presence_enterclient_null_clientid_" + testParams.name;
        AblyRealtime ably = null;

        try {
            /* init ably for token */
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            final AblyRest ablyForToken = new AblyRest(optsForToken);

            /* get first token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            tokenParams.clientId = testClientId1;

            Auth.TokenDetails token = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", token.token);

            ClientOptions opts = createOptions(token.token);
            opts.clientId = testClientId1;
            ably = new AblyRealtime(opts);

            Channel channel = ably.channels.get(channelName);
            channel.attach();

            ChannelWaiter channelWaiter = new ChannelWaiter(channel);
            channelWaiter.waitFor(ChannelState.attached);
            CompletionWaiter presenceWaiter = new CompletionWaiter();

            /* should fail with null clientId */
            channel.presence.enterClient(null, null, presenceWaiter);
            ErrorInfo enterErr = presenceWaiter.waitFor();
            assertNotNull("Verify error result", enterErr);
            assertEquals("Verify error result with expected error code", enterErr.code, 40000);

            /* should fail with null clientId */
            presenceWaiter.reset();
            channel.presence.leaveClient(null, null, presenceWaiter);
            ErrorInfo leaveErr = presenceWaiter.waitFor();
            assertNotNull("Verify error result", leaveErr);
            assertEquals("Verify error result with expected error code", leaveErr.code, 40000);

            /* should fail with null clientId */
            presenceWaiter.reset();
            channel.presence.updateClient(null, null, presenceWaiter);
            ErrorInfo updateErr = presenceWaiter.waitFor();
            assertNotNull("Verify error result", updateErr);
            assertEquals("Verify error result with expected error code", updateErr.code, 40000);
        } finally {
            if (ably != null)
                ably.close();
        }
    }

    /**
     * Verify protocol messages sent on Presence.enter() follow specs if sent from correct state and
     * the call fails if sent from DETACHED state
     *
     * Tests RTP8c, RTP8g
     */
    @Test
    public void protocol_enter_message_format() throws AblyException, InterruptedException {
        AblyRealtime ably = null;

        try {
            final ArrayList<PresenceMessage> sentPresence = new ArrayList<>();

            /* Allow send but record all the presence messages for later analysis */
            final MockWebsocketFactory mockTransport = new MockWebsocketFactory();
            mockTransport.allowSend(new MockWebsocketFactory.MessageFilter() {
                @Override
                public boolean matches(ProtocolMessage message) {
                    if (message.action == ProtocolMessage.Action.presence && message.presence != null) {
                        synchronized (sentPresence) {
                            Collections.addAll(sentPresence, message.presence);
                            sentPresence.notify();
                        }
                    }
                    return true;
                }
            });

            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            opts.clientId = testClientId1;
            opts.transportFactory = mockTransport;
            ably = new AblyRealtime(opts);

            Channel channel = ably.channels.get("protocol_enter_message_format_" + testParams.name);
            /* using testClientId1 */
            channel.presence.enter(null, null);

            synchronized (sentPresence) {
                while (sentPresence.size() < 1)
                    sentPresence.wait();
            }

            assertEquals("Verify number of presence messages sent", sentPresence.size(), 1);
            assertTrue("Verify presence messages follows spec",
                    sentPresence.get(0).action == Action.enter &&
                            sentPresence.get(0).clientId == null
            );

            channel.detach();
            new ChannelWaiter(channel).waitFor(ChannelState.detached);

            try {
                channel.presence.enter(null, null);
                fail("Presence.enter() shouldn't succeed in detached state");
            } catch (AblyException e) {
                assertEquals("Verify exception error code", e.errorInfo.code, 91001 /* unable to enter presence channel (invalid channel state) */);
            }

        } finally {
            if (ably != null)
                ably.close();
        }
    }

    /**
     * Verify protocol messages sent on Presence.enter() follow specs if sent from correct state and
     * the call fails if sent from DETACHED state
     *
     * Tests RTP8c, RTP8g
     */
    @Test
    public void protocol_enterclient_message_format() throws AblyException, InterruptedException {
        AblyRealtime ably = null;

        try {
            final ArrayList<PresenceMessage> sentPresence = new ArrayList<>();

            /* Allow send but record all the presence messages for later analysis */
            final MockWebsocketFactory mockTransport = new MockWebsocketFactory();
            mockTransport.allowSend(new MockWebsocketFactory.MessageFilter() {
                @Override
                public boolean matches(ProtocolMessage message) {
                    if (message.action == ProtocolMessage.Action.presence && message.presence != null) {
                        synchronized (sentPresence) {
                            Collections.addAll(sentPresence, message.presence);
                            sentPresence.notify();
                        }
                    }
                    return true;
                }
            });

            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            opts.transportFactory = mockTransport;
            ably = new AblyRealtime(opts);

            Channel channel = ably.channels.get("protocol_enterclient_message_format_" + testParams.name);
            /* using testClientId2 */
            channel.presence.enterClient(testClientId2);

            synchronized (sentPresence) {
                while (sentPresence.size() < 1)
                    sentPresence.wait();
            }

            assertEquals("Verify number of presence messages sent", sentPresence.size(), 1);
            assertTrue("Verify presence messages follows spec",
                    sentPresence.get(0).action == Action.enter &&
                            sentPresence.get(0).clientId.equals(testClientId2)
            );

            channel.detach();
            new ChannelWaiter(channel).waitFor(ChannelState.detached);

            try {
                channel.presence.enterClient("testClient3");
                fail("Presence.enterClient() shouldn't succeed in detached state");
            } catch (AblyException e) {
                assertEquals("Verify exception error code", e.errorInfo.code, 91001 /* unable to enter presence channel (invalid channel state) */);
            }

        } finally {
            if (ably != null)
                ably.close();
        }
    }

    /*
     * Verify presence data is received and encoded/decoded correctly
     * Tests RTP8e, RTP6a
     */
    @Test
    public void presence_encoding() throws AblyException, InterruptedException {
        AblyRealtime ably1 = null, ably2 = null;
        try {
            /* Set up two connections: one for entering, one for listening */
            final String channelName = "presence_encoding" + testParams.name;
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably1 = new AblyRealtime(opts);
            ably2 = new AblyRealtime(opts);

            Channel channel1 = ably1.channels.get(channelName);
            Channel channel2 = ably2.channels.get(channelName);

            channel2.attach();
            new ChannelWaiter(channel2).waitFor(ChannelState.attached);
            final ArrayList<Object> receivedPresenceData = new ArrayList<>();
            channel2.presence.subscribe(new Presence.PresenceListener() {
                @Override
                public void onPresenceMessage(PresenceMessage message) {
                    synchronized (receivedPresenceData) {
                        receivedPresenceData.add(message.data);
                        receivedPresenceData.notify();
                    }
                }
            });

            String testStringData = "123";
            byte[] testByteData = new byte[] {1, 2, 3};
            JsonElement testJsonData = new JsonParser().parse("{\"var1\":\"val1\", \"var2\": \"val2\"}");

            channel1.presence.enterClient("1", testStringData);
            channel1.presence.enterClient("2", testByteData);
            channel1.presence.enterClient("3", testJsonData);
            synchronized (receivedPresenceData) {
                while (receivedPresenceData.size() < 3)
                    receivedPresenceData.wait();
            }

            assertEquals("Verify number of received presence messages", receivedPresenceData.size(), 3);
            assertEquals("Verify string data", receivedPresenceData.get(0), testStringData);
            assertTrue("Verify byte[] data",
                    receivedPresenceData.get(1) instanceof byte[] &&
                    Arrays.equals((byte[])receivedPresenceData.get(1), testByteData));
            assertEquals("Verify JSON data", receivedPresenceData.get(2), testJsonData);

            /* use data from ENTER message */
            channel1.presence.leaveClient("1");
            /* use different data */
            channel1.presence.leaveClient("2", "leave");

            synchronized (receivedPresenceData) {
                while (receivedPresenceData.size() < 5)
                    receivedPresenceData.wait();
            }

            assertEquals("Verify string data for enter message is used in leave message", receivedPresenceData.get(3), testStringData);
            assertEquals("Verify overridden leave data", receivedPresenceData.get(4), "leave");

        } finally {
            if (ably1 != null)
                ably1.close();
            if (ably2 != null)
                ably2.close();
        }
    }

    /*
     * Test Presence.get() filtering and syncToWait flag
     * Tests RTP11b, RTP11c, RTP11d
     */
    @Test
    public void presence_get() throws AblyException, InterruptedException {
        AblyRealtime ably1 = null, ably2 = null;
        try {
            /* Set up two connections: one for entering, one for listening */
            final String channelName = "presence_get" + testParams.name;
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably1 = new AblyRealtime(opts);

            Channel channel1 = ably1.channels.get(channelName);
            CompletionWaiter completionWaiter = new CompletionWaiter();
            channel1.presence.enterClient("1", null, completionWaiter);
            channel1.presence.enterClient("2", null, completionWaiter);
            completionWaiter.waitFor(2);

            opts.autoConnect = false;
            ably2 = new AblyRealtime(opts);
            Channel channel2 = ably2.channels.get(channelName);
            PresenceWaiter waiter2 = new PresenceWaiter(channel2);

            /*
             * Wait with waitForSync set to false, should result in 0 members because autoConnect is set to false
             * This also tests implicit attach()
             */
            PresenceMessage[] presenceMessages1 = channel2.presence.get(false);
            assertEquals("Verify number of presence members before SYNC", presenceMessages1.length, 0);

            ably2.connection.connect();

            /* now that waitForSync is true it should get all the members entered on first connection */
            PresenceMessage[] presenceMessages2 = channel2.presence.get(true);
            assertEquals("Verify number of presence members after SYNC", presenceMessages2.length, 2);

            /* enter third member from second connection */
            channel2.presence.enterClient("3", null, completionWaiter);
            completionWaiter.waitFor(3);
            waiter2.waitFor(3);

            /* filter by clientId */
            PresenceMessage[] presenceMessages3 = channel2.presence.get(new Param(Presence.GET_CLIENTID, "1"));
            assertTrue("Verify clientId filter works",
                    presenceMessages3.length == 1 && presenceMessages3[0].clientId.equals("1"));

            /* filter by connectionId */
            PresenceMessage[] presenceMessages4 = channel2.presence.get(new Param(Presence.GET_CONNECTIONID, ably2.connection.id));
            assertTrue("Verify connectionId filter works",
                    presenceMessages4.length == 1 && presenceMessages4[0].clientId.equals("3"));

            /* filter by both clientId and connectionId */
            PresenceMessage[] presenceMessages5 = channel2.presence.get(
                    new Param(Presence.GET_CONNECTIONID, ably1.connection.id),
                    new Param(Presence.GET_CLIENTID, "2")
            );
            PresenceMessage[] presenceMessages6 = channel2.presence.get(
                    new Param(Presence.GET_CONNECTIONID, ably2.connection.id),
                    new Param(Presence.GET_CLIENTID, "2")
            );
            assertTrue("Verify clientId+connectionId filter works",
                    presenceMessages5.length == 1 && presenceMessages5[0].clientId.equals("2") && presenceMessages6.length == 0);

            /* go into suspended mode */
            ably2.connection.connectionManager.requestState(ConnectionState.suspended);
            new ConnectionWaiter(ably2.connection).waitFor(ConnectionState.suspended);

            /* try with wait set to false, should get all the three members */
            PresenceMessage[] presenceMessages7 = channel2.presence.get(false);
            assertEquals("Verify Presence.get() with waitForSync set to false works in SUSPENDED state", presenceMessages7.length, 3);

            /* try with wait set to true, should get exception */
            try {
                channel2.presence.get(true);
                fail("Presence.get() with waitForSync=true shouldn't succeed in SUSPENDED state");
            } catch (AblyException e) {
                assertEquals("Verify correct error code for Presence.get() with waitForSync=true in SUSPENDED state", e.errorInfo.code, 91005);
            }
        } finally {
            if (ably1 != null)
                ably1.close();
            if (ably2 != null)
                ably2.close();
        }
    }

    /**
     * Test Presence.get()
     * check if parent channel is able to detect presence
     * during intermittent detach cycles
     */

    public void checkMembersWithChannelPresence(Channel testChannel) throws AblyException {
        PresenceMessage[] presenceMessages = testChannel.presence.get(true);
        testChannel.detach();
        assertEquals("Members count with channel presence should be " + presenceMessages.length, presenceMessages.length, 1);
    }

    @Test
    public void test_consistent_presence_for_members() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            client1Channel.attach();
            (new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

            String enterString = "Entering presence from child channel";

            CompletionWaiter enterComplete = new CompletionWaiter();
            client1Channel.presence.enter(enterString, enterComplete);
            enterComplete.waitFor();

            presenceWaiter.waitFor(testClientId1, Action.enter);
            assertNotNull(presenceWaiter.contains(testClientId1, Action.enter));
            assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);

            int parent_detach_cycle = 6;
            for (int cycle = 0; cycle < parent_detach_cycle ; cycle++) {
                Thread.sleep(1000);
                checkMembersWithChannelPresence(testChannel.realtimeChannel);
            }

        } catch(AblyException | InterruptedException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Authenticate using wildcard token, initialize AblyRealtime so clientId is not known a priori,
     * call enter() without attaching first, start connection
     *
     * Expect NACK from the server because client is unidentified
     *
     * Tests RTP8i, RTP8f, partial tests for RTP9e, RTP10e
     */
    @Test
    public void enter_before_clientid_is_known() throws AblyException {
        AblyRealtime ably = null;
        try {
            ClientOptions restOpts = createOptions(testVars.keys[0].keyStr);
            AblyRest ablyForToken = new AblyRest(restOpts);

            /* Initialize connection so clientId is not known before actual connection */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            Capability capability = new Capability();
            tokenParams.capability = capability.toString();
            tokenParams.clientId = "*";

            Auth.TokenDetails token = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", token.token);

            ClientOptions opts = createOptions();
            opts.defaultTokenParams.clientId = "*";
            opts.token = token.token;
            opts.autoConnect = false;
            ably = new AblyRealtime(opts);

            /* enter without attaching first */
            Channel channel = ably.channels.get("enter_before_clientid_is_known"+testParams.name);
            CompletionWaiter completionWaiter = new CompletionWaiter();
            channel.presence.enter(null, completionWaiter);

            ably.connection.connect();

            completionWaiter.waitFor(1);
            assertFalse("Verify enter() failed", completionWaiter.success);
            assertEquals("Verify error code", completionWaiter.error.code, 40012);

            /* Now clientId is known to be "*" and subsequent enter() should fail immediately */
            completionWaiter.reset();
            channel.presence.enter(null, completionWaiter);
            completionWaiter.waitFor(1);
            assertFalse("Verify enter() failed", completionWaiter.success);
            assertEquals("Verify error code", completionWaiter.error.code, 91000);

            /* and so should update() and leave() */
            completionWaiter.reset();
            channel.presence.update(null, completionWaiter);
            completionWaiter.waitFor(1);
            assertFalse("Verify update() failed", completionWaiter.success);
            assertEquals("Verify error code", completionWaiter.error.code, 91000);

            completionWaiter.reset();
            channel.presence.leave(null, completionWaiter);
            completionWaiter.waitFor(1);
            assertFalse("Verify update() failed", completionWaiter.success);
            assertEquals("Verify error code", completionWaiter.error.code, 91000);

        } finally {
            if (ably != null)
                ably.close();
        }
    }

    /**
     * To Test PresenceMessage.fromEncoded(JsonObject, ChannelOptions) and PresenceMessage.fromEncoded(String, ChannelOptions)
     * Refer Spec TP4
     * @throws AblyException
     */
    @Test
    public void message_from_encoded_json_object() throws AblyException {
        ChannelOptions options = null;
        byte[] data = "0123456789".getBytes();
        PresenceMessage encoded = new PresenceMessage(Action.present, "client-123");
        encoded.data = data;
        encoded.encode(options);

        PresenceMessage decoded = PresenceMessage.fromEncoded(Serialisation.gson.toJson(encoded), options);
        assertEquals(encoded.clientId, decoded.clientId);
        assertArrayEquals(data, (byte[]) decoded.data);

        /*Test JSON Data decoding in PresenceMessage.fromEncoded(JsonObject)*/
        JsonObject person = new JsonObject();
        person.addProperty("name", "Amit");
        person.addProperty("country", "Interlaken Ost");

        PresenceMessage userDetails = new PresenceMessage(Action.absent, "client-123", person);
        userDetails.encode(options);

        PresenceMessage decodedMessage1 = PresenceMessage.fromEncoded(Serialisation.gson.toJsonTree(userDetails).getAsJsonObject(), null);
        assertEquals(person, decodedMessage1.data);

        /*Test PresenceMessage.fromEncoded(String)*/
        PresenceMessage decodedMessage2 = PresenceMessage.fromEncoded(Serialisation.gson.toJson(userDetails), options);
        assertEquals(person, decodedMessage2.data);

        /*Test invalid case.*/
        try {
            //We pass invalid PresenceMessage object
            PresenceMessage.fromEncoded(person, options);
            fail();
        } catch(Exception e) {/*ignore as we are expecting it to fail.*/}
    }

    /**
     * To test PresenceMessage.fromEncodedArray(JsonArray, ChannelOptions) and PresenceMessage.fromEncodedArray(String, ChannelOptions)
     * Refer Spec. TP4
     * @throws AblyException
     */
    @Test
    public void messages_from_encoded_json_array() throws AblyException {
        JsonArray fixtures = null;
        MessagesData testMessages = null;
        testMessages = AblyCommonsReader.read(testMessagesEncodingFile, MessagesData.class);
        JsonObject jsonObject = AblyCommonsReader.readAsJsonObject(testMessagesEncodingFile);
        //We use this as-is for decoding purposes.
        fixtures = jsonObject.getAsJsonArray("messages");

        PresenceMessage[] decodedMessages = PresenceMessage.fromEncodedArray(fixtures, null);
        for(int index = 0; index < decodedMessages.length; index++) {
            PresenceMessage testInputMsg = testMessages.messages[index];
            testInputMsg.decode(null);
            if(testInputMsg.data instanceof byte[]) {
                assertArrayEquals((byte[]) testInputMsg.data, (byte[]) decodedMessages[index].data);
            } else {
                assertEquals(testInputMsg.data, decodedMessages[index].data);
            }
        }
        /*Test PresenceMessage.fromEncodedArray(String)*/
        String fixturesArray = Serialisation.gson.toJson(fixtures);
        PresenceMessage[] decodedMessages2 = PresenceMessage.fromEncodedArray(fixturesArray, null);
        for(int index = 0; index < decodedMessages2.length; index++) {
            PresenceMessage testInputMsg = testMessages.messages[index];
            if(testInputMsg.data instanceof byte[]) {
                assertArrayEquals((byte[]) testInputMsg.data, (byte[]) decodedMessages2[index].data);
            } else {
                assertEquals(testInputMsg.data, decodedMessages2[index].data);
            }
        }
    }

    /**
     * Enter presence with extras field and verify it comes back on the other side
     * Test TP3i
     */
    @Test
    public void presence_enter_with_extras() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            client1Channel.attach();
            (new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

            /* create extras with headers.foo */
            JsonObject extrasJson = new JsonObject();
            JsonObject headers = new JsonObject();
            headers.addProperty("foo", "bar");
            extrasJson.add("headers", headers);
            io.ably.lib.types.MessageExtras extras = new io.ably.lib.types.MessageExtras(extrasJson);

            /* create presence message with extras */
            String enterString = "Test data (presence_enter_with_extras)";
            PresenceMessage presenceMsg = new PresenceMessage(PresenceMessage.Action.enter, null, enterString);
            presenceMsg.extras = extras;

            /* let client1 enter the channel with extras and wait for the entered event to be delivered */
            CompletionWaiter enterComplete = new CompletionWaiter();
            client1Channel.presence.updatePresence(presenceMsg, enterComplete);
            presenceWaiter.waitFor(testClientId1, Action.enter);
            PresenceMessage receivedMessage = presenceWaiter.contains(testClientId1, Action.enter);
            assertNotNull("Verify presence message received", receivedMessage);
            assertEquals("Verify data matches", enterString, receivedMessage.data);

            /* verify extras field is present and correct */
            assertNotNull("Verify extras is not null", receivedMessage.extras);
            JsonObject receivedExtrasJson = receivedMessage.extras.asJsonObject();
            assertNotNull("Verify extras JSON is not null", receivedExtrasJson);
            assertTrue("Verify headers exists in extras", receivedExtrasJson.has("headers"));
            JsonObject receivedHeaders = receivedExtrasJson.getAsJsonObject("headers");
            assertNotNull("Verify headers object is not null", receivedHeaders);
            assertTrue("Verify foo exists in headers", receivedHeaders.has("foo"));
            assertEquals("Verify foo value matches", "bar", receivedHeaders.get("foo").getAsString());

            /* verify enter callback called on completion */
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);
        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Enter presence using the convenience enter(data, extras, listener) method
     * and verify extras come back on the subscriber side.
     */
    @Test
    public void presence_enter_with_extras_convenience() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            client1Channel.attach();
            (new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

            /* create extras with headers */
            JsonObject extrasJson = new JsonObject();
            JsonObject headers = new JsonObject();
            headers.addProperty("foo", "bar");
            extrasJson.add("headers", headers);
            io.ably.lib.types.MessageExtras extras = new io.ably.lib.types.MessageExtras(extrasJson);

            /* enter using the convenience method with extras */
            String enterString = "Test data (presence_enter_with_extras_convenience)";
            CompletionWaiter enterComplete = new CompletionWaiter();
            client1Channel.presence.enter(enterString, extras, enterComplete);
            presenceWaiter.waitFor(testClientId1, Action.enter);
            PresenceMessage receivedMessage = presenceWaiter.contains(testClientId1, Action.enter);
            assertNotNull("Verify presence message received", receivedMessage);
            assertEquals("Verify data matches", enterString, receivedMessage.data);

            /* verify extras field is present and correct */
            assertNotNull("Verify extras is not null", receivedMessage.extras);
            JsonObject receivedExtrasJson = receivedMessage.extras.asJsonObject();
            assertTrue("Verify headers exists in extras", receivedExtrasJson.has("headers"));
            JsonObject receivedHeaders = receivedExtrasJson.getAsJsonObject("headers");
            assertEquals("Verify foo value matches", "bar", receivedHeaders.get("foo").getAsString());

            /* verify enter callback called on completion */
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);
        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Update presence using the convenience update(data, extras, listener) method
     * and verify extras come back on the subscriber side.
     */
    @Test
    public void presence_update_with_extras() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            client1Channel.attach();
            (new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

            /* enter first (no extras) */
            CompletionWaiter enterComplete = new CompletionWaiter();
            client1Channel.presence.enter("initial data", enterComplete);
            enterComplete.waitFor();
            presenceWaiter.waitFor(testClientId1, Action.enter);

            /* create extras with headers */
            JsonObject extrasJson = new JsonObject();
            JsonObject headers = new JsonObject();
            headers.addProperty("key", "value");
            extrasJson.add("headers", headers);
            io.ably.lib.types.MessageExtras extras = new io.ably.lib.types.MessageExtras(extrasJson);

            /* update using the convenience method with extras */
            String updateString = "Test data (presence_update_with_extras)";
            CompletionWaiter updateComplete = new CompletionWaiter();
            client1Channel.presence.update(updateString, extras, updateComplete);
            presenceWaiter.waitFor(testClientId1, Action.update);
            PresenceMessage receivedMessage = presenceWaiter.contains(testClientId1, Action.update);
            assertNotNull("Verify presence message received", receivedMessage);
            assertEquals("Verify data matches", updateString, receivedMessage.data);

            /* verify extras field is present and correct */
            assertNotNull("Verify extras is not null", receivedMessage.extras);
            JsonObject receivedExtrasJson = receivedMessage.extras.asJsonObject();
            assertTrue("Verify headers exists in extras", receivedExtrasJson.has("headers"));
            JsonObject receivedHeaders = receivedExtrasJson.getAsJsonObject("headers");
            assertEquals("Verify key value matches", "value", receivedHeaders.get("key").getAsString());

            /* verify update callback called on completion */
            updateComplete.waitFor();
            assertTrue("Verify update callback called on completion", updateComplete.success);
        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Leave presence using the convenience leave(data, extras, listener) method
     * and verify extras come back on the subscriber side.
     */
    @Test
    public void presence_leave_with_extras() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            client1Channel.attach();
            (new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

            /* enter first (no extras) */
            CompletionWaiter enterComplete = new CompletionWaiter();
            client1Channel.presence.enter("initial data", enterComplete);
            enterComplete.waitFor();
            presenceWaiter.waitFor(testClientId1, Action.enter);

            /* create extras with headers */
            JsonObject extrasJson = new JsonObject();
            JsonObject headers = new JsonObject();
            headers.addProperty("reason", "goodbye");
            extrasJson.add("headers", headers);
            io.ably.lib.types.MessageExtras extras = new io.ably.lib.types.MessageExtras(extrasJson);

            /* leave using the convenience method with extras */
            String leaveString = "Test data (presence_leave_with_extras)";
            CompletionWaiter leaveComplete = new CompletionWaiter();
            client1Channel.presence.leave(leaveString, extras, leaveComplete);
            presenceWaiter.waitFor(testClientId1, Action.leave);
            PresenceMessage receivedMessage = presenceWaiter.contains(testClientId1, Action.leave);
            assertNotNull("Verify presence message received", receivedMessage);
            assertEquals("Verify data matches", leaveString, receivedMessage.data);

            /* verify extras field is present and correct */
            assertNotNull("Verify extras is not null", receivedMessage.extras);
            JsonObject receivedExtrasJson = receivedMessage.extras.asJsonObject();
            assertTrue("Verify headers exists in extras", receivedExtrasJson.has("headers"));
            JsonObject receivedHeaders = receivedExtrasJson.getAsJsonObject("headers");
            assertEquals("Verify reason value matches", "goodbye", receivedHeaders.get("reason").getAsString());

            /* verify leave callback called on completion */
            leaveComplete.waitFor();
            assertTrue("Verify leave callback called on completion", leaveComplete.success);
        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Enter presence for a specific clientId using enterClient(clientId, data, extras, listener)
     * and verify extras come back on the subscriber side.
     */
    @Test
    public void presence_enterClient_with_extras() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with wildcard clientId capability */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = wildcardToken;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            client1Channel.attach();
            (new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

            /* create extras with headers */
            JsonObject extrasJson = new JsonObject();
            JsonObject headers = new JsonObject();
            headers.addProperty("role", "admin");
            extrasJson.add("headers", headers);
            io.ably.lib.types.MessageExtras extras = new io.ably.lib.types.MessageExtras(extrasJson);

            /* enter using enterClient with extras */
            String enterString = "Test data (presence_enterClient_with_extras)";
            CompletionWaiter enterComplete = new CompletionWaiter();
            client1Channel.presence.enterClient(testClientId1, enterString, extras, enterComplete);
            presenceWaiter.waitFor(testClientId1, Action.enter);
            PresenceMessage receivedMessage = presenceWaiter.contains(testClientId1, Action.enter);
            assertNotNull("Verify presence message received", receivedMessage);
            assertEquals("Verify data matches", enterString, receivedMessage.data);

            /* verify extras field is present and correct */
            assertNotNull("Verify extras is not null", receivedMessage.extras);
            JsonObject receivedExtrasJson = receivedMessage.extras.asJsonObject();
            assertTrue("Verify headers exists in extras", receivedExtrasJson.has("headers"));
            JsonObject receivedHeaders = receivedExtrasJson.getAsJsonObject("headers");
            assertEquals("Verify role value matches", "admin", receivedHeaders.get("role").getAsString());

            /* verify enter callback called on completion */
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);
        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Update presence for a specific clientId using updateClient(clientId, data, extras, listener)
     * and verify extras come back on the subscriber side.
     */
    @Test
    public void presence_updateClient_with_extras() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with wildcard clientId capability */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = wildcardToken;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            client1Channel.attach();
            (new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

            /* enter first so we can update */
            CompletionWaiter enterComplete = new CompletionWaiter();
            client1Channel.presence.enterClient(testClientId1, "initial data", enterComplete);
            presenceWaiter.waitFor(testClientId1, Action.enter);
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);

            /* create extras with headers */
            JsonObject extrasJson = new JsonObject();
            JsonObject headers = new JsonObject();
            headers.addProperty("role", "editor");
            extrasJson.add("headers", headers);
            io.ably.lib.types.MessageExtras extras = new io.ably.lib.types.MessageExtras(extrasJson);

            /* update using updateClient with extras */
            String updateString = "Test data (presence_updateClient_with_extras)";
            CompletionWaiter updateComplete = new CompletionWaiter();
            client1Channel.presence.updateClient(testClientId1, updateString, extras, updateComplete);
            presenceWaiter.waitFor(testClientId1, Action.update);
            PresenceMessage receivedMessage = presenceWaiter.contains(testClientId1, Action.update);
            assertNotNull("Verify presence message received", receivedMessage);
            assertEquals("Verify data matches", updateString, receivedMessage.data);

            /* verify extras field is present and correct */
            assertNotNull("Verify extras is not null", receivedMessage.extras);
            JsonObject receivedExtrasJson = receivedMessage.extras.asJsonObject();
            assertTrue("Verify headers exists in extras", receivedExtrasJson.has("headers"));
            JsonObject receivedHeaders = receivedExtrasJson.getAsJsonObject("headers");
            assertEquals("Verify role value matches", "editor", receivedHeaders.get("role").getAsString());

            /* verify update callback called on completion */
            updateComplete.waitFor();
            assertTrue("Verify update callback called on completion", updateComplete.success);
        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Leave presence for a specific clientId using leaveClient(clientId, data, extras, listener)
     * and verify extras come back on the subscriber side.
     */
    @Test
    public void presence_leaveClient_with_extras() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with wildcard clientId capability */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = wildcardToken;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            client1Channel.attach();
            (new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

            /* enter first so we can leave */
            CompletionWaiter enterComplete = new CompletionWaiter();
            client1Channel.presence.enterClient(testClientId1, "initial data", enterComplete);
            presenceWaiter.waitFor(testClientId1, Action.enter);
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);

            /* create extras with headers */
            JsonObject extrasJson = new JsonObject();
            JsonObject headers = new JsonObject();
            headers.addProperty("role", "departing");
            extrasJson.add("headers", headers);
            io.ably.lib.types.MessageExtras extras = new io.ably.lib.types.MessageExtras(extrasJson);

            /* leave using leaveClient with extras */
            String leaveString = "Test data (presence_leaveClient_with_extras)";
            CompletionWaiter leaveComplete = new CompletionWaiter();
            client1Channel.presence.leaveClient(testClientId1, leaveString, extras, leaveComplete);
            presenceWaiter.waitFor(testClientId1, Action.leave);
            PresenceMessage receivedMessage = presenceWaiter.contains(testClientId1, Action.leave);
            assertNotNull("Verify presence message received", receivedMessage);
            assertEquals("Verify data matches", leaveString, receivedMessage.data);

            /* verify extras field is present and correct */
            assertNotNull("Verify extras is not null", receivedMessage.extras);
            JsonObject receivedExtrasJson = receivedMessage.extras.asJsonObject();
            assertTrue("Verify headers exists in extras", receivedExtrasJson.has("headers"));
            JsonObject receivedHeaders = receivedExtrasJson.getAsJsonObject("headers");
            assertEquals("Verify role value matches", "departing", receivedHeaders.get("role").getAsString());

            /* verify leave callback called on completion */
            leaveComplete.waitFor();
            assertTrue("Verify leave callback called on completion", leaveComplete.success);
        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Enter presence with extras and verify that channel.presence.get()
     * returns the extras on the PresenceMessage from the local presence map.
     */
    @Test
    public void presence_extras_in_presence_get() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with wildcard clientId capability */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = wildcardToken;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            client1Channel.attach();
            (new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

            /* create extras with headers */
            JsonObject extrasJson = new JsonObject();
            JsonObject headers = new JsonObject();
            headers.addProperty("status", "active");
            extrasJson.add("headers", headers);
            io.ably.lib.types.MessageExtras extras = new io.ably.lib.types.MessageExtras(extrasJson);

            /* enter using enterClient with extras */
            String enterString = "Test data (presence_extras_in_presence_get)";
            CompletionWaiter enterComplete = new CompletionWaiter();
            client1Channel.presence.enterClient(testClientId1, enterString, extras, enterComplete);
            presenceWaiter.waitFor(testClientId1, Action.enter);
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);

            /* call presence.get() and verify extras on the returned message */
            PresenceMessage[] members = testChannel.realtimeChannel.presence.get(false);
            assertNotNull("Verify presence members returned", members);
            PresenceMessage member = contains(members, testClientId1);
            assertNotNull("Verify member found in presence set", member);
            assertEquals("Verify data matches", enterString, member.data);

            /* verify extras field is present and correct */
            assertNotNull("Verify extras is not null on get() result", member.extras);
            JsonObject memberExtrasJson = member.extras.asJsonObject();
            assertTrue("Verify headers exists in extras", memberExtrasJson.has("headers"));
            JsonObject memberHeaders = memberExtrasJson.getAsJsonObject("headers");
            assertEquals("Verify status value matches", "active", memberHeaders.get("status").getAsString());
        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Enter presence with extras and verify that the extras survive a round-trip
     * through the REST presence history API.
     */
    @Test
    public void presence_extras_in_rest_history() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with wildcard clientId capability */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = wildcardToken;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            client1Channel.attach();
            (new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

            /* create extras with headers */
            JsonObject extrasJson = new JsonObject();
            JsonObject headers = new JsonObject();
            headers.addProperty("source", "test");
            extrasJson.add("headers", headers);
            io.ably.lib.types.MessageExtras extras = new io.ably.lib.types.MessageExtras(extrasJson);

            /* enter using enterClient with extras */
            String enterString = "Test data (presence_extras_in_rest_history)";
            CompletionWaiter enterComplete = new CompletionWaiter();
            client1Channel.presence.enterClient(testClientId1, enterString, extras, enterComplete);
            presenceWaiter.waitFor(testClientId1, Action.enter);
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);

            /* query REST presence history and verify extras */
            PaginatedResult<PresenceMessage> historyPage = testChannel.restChannel.presence.history(
                new Param[]{ new Param("direction", "forwards") }
            );
            assertNotNull("Verify history returned", historyPage);
            PresenceMessage[] historyMessages = historyPage.items();
            assertTrue("Verify at least one history message", historyMessages.length > 0);

            /* find the enter message in history */
            PresenceMessage historyMsg = null;
            for (PresenceMessage msg : historyMessages) {
                if (testClientId1.equals(msg.clientId) && msg.action == Action.enter) {
                    historyMsg = msg;
                    break;
                }
            }
            assertNotNull("Verify enter message found in history", historyMsg);
            assertEquals("Verify data matches", enterString, historyMsg.data);

            /* verify extras field is present and correct */
            assertNotNull("Verify extras is not null in history", historyMsg.extras);
            JsonObject historyExtrasJson = historyMsg.extras.asJsonObject();
            assertTrue("Verify headers exists in extras", historyExtrasJson.has("headers"));
            JsonObject historyHeaders = historyExtrasJson.getAsJsonObject("headers");
            assertEquals("Verify source value matches", "test", historyHeaders.get("source").getAsString());
        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    /**
     * Verify that the existing two-arg overloads (e.g., enter(data, listener)) still work
     * correctly and produce a PresenceMessage with null extras, ensuring no regression.
     */
    @Test
    public void presence_null_extras_backward_compatibility() {
        AblyRealtime clientAbly1 = null;
        TestChannel testChannel = new TestChannel();
        try {
            /* subscribe for presence events in the anonymous connection */
            PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
            /* set up a connection with specific clientId */
            ClientOptions client1Opts = new ClientOptions() {{
                tokenDetails = token1;
                clientId = testClientId1;
            }};
            fillInOptions(client1Opts);
            clientAbly1 = new AblyRealtime(client1Opts);

            /* wait until connected */
            (new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

            /* get channel and attach */
            Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
            client1Channel.attach();
            (new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

            /* enter using the original two-arg overload: enter(data, listener) */
            String enterString = "Test data (presence_null_extras_backward_compatibility)";
            CompletionWaiter enterComplete = new CompletionWaiter();
            client1Channel.presence.enter(enterString, enterComplete);
            presenceWaiter.waitFor(testClientId1, Action.enter);
            PresenceMessage enterMsg = presenceWaiter.contains(testClientId1, Action.enter);
            assertNotNull("Verify enter presence message received", enterMsg);
            assertEquals("Verify enter data matches", enterString, enterMsg.data);
            assertNull("Verify extras is null for enter without extras", enterMsg.extras);
            enterComplete.waitFor();
            assertTrue("Verify enter callback called on completion", enterComplete.success);

            /* update using the original two-arg overload: update(data, listener) */
            String updateString = "Updated data (presence_null_extras_backward_compatibility)";
            CompletionWaiter updateComplete = new CompletionWaiter();
            client1Channel.presence.update(updateString, updateComplete);
            presenceWaiter.waitFor(testClientId1, Action.update);
            PresenceMessage updateMsg = presenceWaiter.contains(testClientId1, Action.update);
            assertNotNull("Verify update presence message received", updateMsg);
            assertEquals("Verify update data matches", updateString, updateMsg.data);
            assertNull("Verify extras is null for update without extras", updateMsg.extras);
            updateComplete.waitFor();
            assertTrue("Verify update callback called on completion", updateComplete.success);

            /* leave using the original two-arg overload: leave(data, listener) */
            String leaveString = "Leave data (presence_null_extras_backward_compatibility)";
            CompletionWaiter leaveComplete = new CompletionWaiter();
            client1Channel.presence.leave(leaveString, leaveComplete);
            presenceWaiter.waitFor(testClientId1, Action.leave);
            PresenceMessage leaveMsg = presenceWaiter.contains(testClientId1, Action.leave);
            assertNotNull("Verify leave presence message received", leaveMsg);
            assertEquals("Verify leave data matches", leaveString, leaveMsg.data);
            assertNull("Verify extras is null for leave without extras", leaveMsg.extras);
            leaveComplete.waitFor();
            assertTrue("Verify leave callback called on completion", leaveComplete.success);
        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception running test: " + e.getMessage());
        } finally {
            if(clientAbly1 != null)
                clientAbly1.close();
            if(testChannel != null)
                testChannel.dispose();
        }
    }

    static class MessagesData {
        public PresenceMessage[] messages;
    }
}
