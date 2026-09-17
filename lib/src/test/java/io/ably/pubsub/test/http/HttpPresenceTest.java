package io.ably.pubsub.test.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;

import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.http.Channel;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.PaginatedResult;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.types.PresenceMessage;

public class HttpPresenceTest extends ParameterizedTest {

    private static final String[] clientIds = new String[] {
        "client_string_0",
        "client_string_1",
        "client_string_2",
        "client_string_3"
    };

    private PubSubHttpClient ably_text;

    @Before
    public void setUpBefore() throws Exception {
        ClientOptions opts_text = createOptions(testVars.keys[0].keyStr);
        ably_text = new PubSubHttpClient(opts_text);
    }

    /**
     * Get member data of various datatypes
     */
    @Test
    public void rest_getpresence() {
        String channelName = "restpresence_notpersisted";
        /* get channel */
        Channel channel = ably_text.channels.get(channelName);
        try {
            PresenceMessage[] members = channel.presence.get(null).items();
            assertNotNull("Expected non-null messages", members);
            assertEquals("Expected 1 message", members.length, 1);

            /* verify presence contents */
            assertEquals("Expect data to be expected String", members[0].data, "This is a string data payload");
        } catch(AblyException e) {
            e.printStackTrace();
            fail("rest_getpresence: Unexpected exception");
            return;
        }
    }

    /**
     * Get presence history data of various datatypes
     */
    @Test
    public void rest_presencehistory_simple() {
        String channelName = "persisted:restpresence_persisted";
        /* get channel */
        Channel channel = ably_text.channels.get(channelName);
        try {
            /* get the history for this channel */
            PaginatedResult<PresenceMessage> members = channel.presence.history(new Param[]{ new Param("direction", "forwards") });
            assertNotNull("Expected non-null messages", members);
            assertEquals("Expected 4 messages", members.items().length, 4);

            /* verify presence contents */
            HashMap<String, Object> memberData = new HashMap<String, Object>();
            for(PresenceMessage member : members.items())
                memberData.put(member.clientId, member.data);
            assertEquals("Expect client_string_0 to be expected String", memberData.get("client_string_0"), "This is a string data payload");
        } catch(AblyException e) {
            e.printStackTrace();
            fail("rest_presencehistory_simple: Unexpected exception");
            return;
        }
    }

    /**
     * Get presence history data in the forward direction and check order
     * DISABLED: See issue https://github.com/ably/ably-java/issues/159
     */
    @Test
    public void rest_presencehistory_order_f() {
        String channelName = "persisted:restpresence_persisted";
        /* get channel */
        Channel channel = ably_text.channels.get(channelName);
        try {
            /* get the history for this channel */
            PaginatedResult<PresenceMessage> members = channel.presence.history(new Param[]{ new Param("direction", "forwards") });
            assertNotNull("Expected non-null messages", members);
            assertEquals("Expected 4 messages", members.items().length, 4);

            /* verify message order */
            for(int i = 0; i < members.items().length; i++) {
                PresenceMessage member = members.items()[i];
                assertEquals("Verify expected member (" + i + ')', clientIds[i], member.clientId);
            }
        } catch(AblyException e) {
            e.printStackTrace();
            fail("rest_presencehistory_order_f: Unexpected exception");
            return;
        }
    }

    /**
     * Get presence history data in the backwards direction using text protocol and check order
     * DISABLED: See issue https://github.com/ably/ably-java/issues/159
     */
    @Test
    public void rest_presencehistory_order_b() {
        String channelName = "persisted:restpresence_persisted";
        /* get channel */
        Channel channel = ably_text.channels.get(channelName);
        try {
            /* get the history for this channel */
            PaginatedResult<PresenceMessage> members = channel.presence.history(new Param[]{ new Param("direction", "backwards") });
            assertNotNull("Expected non-null messages", members);
            assertEquals("Expected 4 messages", members.items().length, 4);

            /* verify message order */
            for(int i = 0; i < members.items().length; i++) {
                PresenceMessage member = members.items()[i];
                assertEquals("Verify expected member (" + i + ')', clientIds[3 - i], member.clientId);
            }
        } catch(AblyException e) {
            e.printStackTrace();
            fail("rest_presencehistory_order_b: Unexpected exception");
            return;
        }
    }

    /**
     * Get limited presence history data in the forward direction using text protocol and check order
     * DISABLED: See issue https://github.com/ably/ably-java/issues/159
     */
    @Test
    public void rest_presencehistory_limit_f() {
        String channelName = "persisted:restpresence_persisted";
        /* get channel */
        Channel channel = ably_text.channels.get(channelName);
        try {
            /* get the history for this channel */
            PaginatedResult<PresenceMessage> members = channel.presence.history(new Param[]{ new Param("direction", "forwards"), new Param("limit", "2") });
            assertNotNull("Expected non-null messages", members);
            assertEquals("Expected 2 messages", members.items().length, 2);

            /* verify message order */
            for(int i = 0; i < members.items().length; i++) {
                PresenceMessage member = members.items()[i];
                assertEquals("Verify expected member (" + i + ')', clientIds[i], member.clientId);
            }
        } catch(AblyException e) {
            e.printStackTrace();
            fail("rest_presencehistory_limit_text_f: Unexpected exception");
            return;
        }
    }

    /**
     * Get limited presence history data in the backwards direction using text protocol and check order
     * DISABLED: See issue https://github.com/ably/ably-java/issues/159
     */
    @Test
    public void rest_presencehistory_limit_b() {
        String channelName = "persisted:restpresence_persisted";
        /* get channel */
        Channel channel = ably_text.channels.get(channelName);
        try {
            /* get the history for this channel */
            PaginatedResult<PresenceMessage> members = channel.presence.history(new Param[]{ new Param("direction", "backwards"), new Param("limit", "2") });
            assertNotNull("Expected non-null messages", members);
            assertEquals("Expected 2 messages", members.items().length, 2);

            /* verify message order */
            for(int i = 0; i < members.items().length; i++) {
                PresenceMessage member = members.items()[i];
                assertEquals("Verify expected member (" + i + ')', clientIds[3 - i], member.clientId);
            }
        } catch(AblyException e) {
            e.printStackTrace();
            fail("rest_presencehistory_limit_b: Unexpected exception");
            return;
        }
    }

    /**
     * Get paginated presence history data in the forward direction using text protocol
     * DISABLED: See issue https://github.com/ably/ably-java/issues/159
     */
    @Test
    public void rest_presencehistory_paginate_f() {
        /* get channel */
        String channelName = "persisted:restpresence_persisted";
        Channel channel = ably_text.channels.get(channelName);
        try {
            /* get the history for this channel */
            PaginatedResult<PresenceMessage> members = channel.presence.history(new Param[]{ new Param("direction", "forwards"), new Param("limit", "1") });
            assertNotNull("Expected non-null messages", members);
            assertEquals("Expected 1 message", members.items().length, 1);

            /* verify message order */
            for(int i = 0; i < members.items().length; i++) {
                PresenceMessage member = members.items()[i];
                assertEquals("Verify expected member (" + i + ')', clientIds[i], member.clientId);
            }

            /* get next page */
            members = members.next();
            assertNotNull("Expected non-null messages", members);
            assertEquals("Expected 1 message", members.items().length, 1);

            /* verify message order */
            for(int i = 0; i < members.items().length; i++) {
                PresenceMessage member = members.items()[i];
                assertEquals("Verify expected member (" + i + ')', clientIds[1 + i], member.clientId);
            }

            /* get next page */
            members = members.next();
            assertNotNull("Expected non-null messages", members);
            assertEquals("Expected 1 message", members.items().length, 1);

            /* verify message order */
            for(int i = 0; i < members.items().length; i++) {
                PresenceMessage member = members.items()[i];
                assertEquals("Verify expected member (" + i + ')', clientIds[2 + i], member.clientId);
            }

            /* get next page */
            members = members.next();
            assertNotNull("Expected non-null messages", members);
            assertEquals("Expected 1 message", members.items().length, 1);

            /* verify message order */
            for(int i = 0; i < members.items().length; i++) {
                PresenceMessage member = members.items()[i];
                assertEquals("Verify expected member (" + i + ')', clientIds[3 + i], member.clientId);
            }

            /* verify there are no further results */
            if(members.hasNext()) {
                members = members.next();
                if(members != null)
                    assertEquals("Expected no further members", members.items().length, 0);
            }

        } catch(AblyException e) {
            e.printStackTrace();
            fail("rest_presencehistory_paginate_f: Unexpected exception");
            return;
        }
    }

    /**
     * Get paginated presence history data in the backwards direction using text protocol
     * DISABLED: See issue https://github.com/ably/ably-java/issues/159
     */
    @Test
    public void rest_presencehistory_paginate_text_b() {
        /* get channel */
        String channelName = "persisted:restpresence_persisted";
        Channel channel = ably_text.channels.get(channelName);
        try {
            /* get the history for this channel */
            PaginatedResult<PresenceMessage> members = channel.presence.history(new Param[]{ new Param("direction", "backwards"), new Param("limit", "1") });
            assertNotNull("Expected non-null messages", members);
            assertEquals("Expected 1 message", members.items().length, 1);

            /* verify message order */
            for(int i = 0; i < members.items().length; i++) {
                PresenceMessage member = members.items()[i];
                assertEquals("Verify expected member (" + i + ')', clientIds[3 - i], member.clientId);
            }

            /* get next page */
            members = members.next();
            assertNotNull("Expected non-null messages", members);
            assertEquals("Expected 1 messages", members.items().length, 1);

            /* verify message order */
            for(int i = 0; i < members.items().length; i++) {
                PresenceMessage member = members.items()[i];
                assertEquals("Verify expected member (" + i + ')', clientIds[2 - i], member.clientId);
            }

            /* get next page */
            members = members.next();
            assertNotNull("Expected non-null messages", members);
            assertEquals("Expected 1 message", members.items().length, 1);

            /* verify message order */
            for(int i = 0; i < members.items().length; i++) {
                PresenceMessage member = members.items()[i];
                assertEquals("Verify expected member (" + i + ')', clientIds[1 - i], member.clientId);
            }

            /* get next page */
            members = members.next();
            assertNotNull("Expected non-null messages", members);
            assertEquals("Expected 1 message", members.items().length, 1);

            /* verify message order */
            for(int i = 0; i < members.items().length; i++) {
                PresenceMessage member = members.items()[i];
                assertEquals("Verify expected member (" + i + ')', clientIds[0 - i], member.clientId);
            }

            /* verify there are no further results */
            if(members.hasNext()) {
                members = members.next();
                if(members != null)
                    assertEquals("Expected no further members", members.items().length, 0);
            }

        } catch(AblyException e) {
            e.printStackTrace();
            fail("rest_presencehistory_paginate_text_f: Unexpected exception");
            return;
        }
    }
}
