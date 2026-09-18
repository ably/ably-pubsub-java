package io.ably.pubsub.chat;

import com.google.gson.JsonObject;
import io.ably.pubsub.realtime.RealtimeClient;
import io.ably.pubsub.realtime.RealtimeClientFactory;
import io.ably.pubsub.realtime.Channel;
import io.ably.pubsub.realtime.ChannelState;
import io.ably.pubsub.test.common.Helpers;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.Message;
import io.ably.pubsub.types.MessageAction;
import io.ably.pubsub.types.MessageVersion;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatMessagesTest extends ParameterizedTest {
    /**
     * Test that a message sent via rest API is sent to a messages channel.
     * It should be received by the client that is subscribed to the messages channel.
     */
    @Test
    public void test_room_message_is_published() {
        String roomId = "1234";
        String channelName = roomId + "::$chat::$chatMessages";
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[7].keyStr);
            opts.clientId = "sandbox-client";
            ably = RealtimeClientFactory.create(opts);
            ChatRoom room = new ChatRoom(roomId, ably);

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName);
            channel.attach();
            (new Helpers.ChannelWaiter(channel)).waitFor(ChannelState.attached);

            /* subscribe to messages */
            List<Message> receivedMsg = new ArrayList<>();
            channel.subscribe(receivedMsg::add);

            // send message to room
            ChatRoom.SendMessageParams params = new ChatRoom.SendMessageParams();
            params.text = "hello there";
            params.metadata = new JsonObject();
            JsonObject foo = new JsonObject();
            foo.addProperty("bar", 1);
            params.metadata.add("foo", foo);
            Map<String, String> headers = new HashMap<>();
            headers.put("header1", "value1");
            headers.put("baz", "qux");
            params.headers = headers;

            JsonObject sendMessageResult = (JsonObject) room.sendMessage(params);
            // check sendMessageResult has 2 fields and are not null
            Assert.assertEquals(2, sendMessageResult.entrySet().size());
            String resultSerial = sendMessageResult.get("serial").getAsString();
            Assert.assertFalse(resultSerial.isEmpty());
            String resultCreatedAt = sendMessageResult.get("createdAt").getAsString();
            Assert.assertFalse(resultCreatedAt.isEmpty());

            Exception err = new Helpers.ConditionalWaiter().wait(() -> !receivedMsg.isEmpty(), 10_000);
            Assert.assertNull(err);

            Assert.assertEquals(1, receivedMsg.size());
            Message message = receivedMsg.get(0);

            Assert.assertFalse("Message ID should not be empty", message.id.isEmpty());
            Assert.assertEquals("chat.message", message.name);
            Assert.assertEquals("sandbox-client", message.clientId);

            JsonObject data = (JsonObject) message.data;
            // has two fields "text" and "metadata"
            Assert.assertEquals(2, data.entrySet().size());
            // Assert for received text
            Assert.assertEquals("hello there", data.get("text").getAsString());
            // Assert on received metadata
            JsonObject metadata = data.getAsJsonObject("metadata");
            Assert.assertTrue(metadata.has("foo"));
            Assert.assertTrue(metadata.get("foo").isJsonObject());
            Assert.assertEquals(1, metadata.getAsJsonObject("foo").get("bar").getAsInt());

            // Assert sent headers as a part of message.extras.headers
            JsonObject extrasJson = message.extras.asJsonObject();
            Assert.assertTrue(extrasJson.has("headers"));
            JsonObject headersJson = extrasJson.getAsJsonObject("headers");
            Assert.assertEquals(2, headersJson.entrySet().size());
            Assert.assertEquals("value1", headersJson.get("header1").getAsString());
            Assert.assertEquals("qux", headersJson.get("baz").getAsString());

            Assert.assertEquals(resultCreatedAt, String.valueOf(message.timestamp));

            Assert.assertEquals(resultSerial, message.serial);
            Assert.assertEquals(resultSerial, message.version.serial);

            Assert.assertEquals(MessageAction.MESSAGE_CREATE, message.action);
            Assert.assertEquals(resultCreatedAt, String.valueOf(message.version.timestamp));

        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Test that a message updated via rest API is sent to a messages channel.
     * It should be received by another client that is subscribed to the same messages channel.
     * Make sure to use two clientIds: clientId1 and clientId2
     */
    @Test
    public void test_room_message_is_updated() {
        String roomId = "1234";
        String channelName = roomId + "::$chat::$chatMessages";
        RealtimeClient ablyClient1 = null;
        RealtimeClient ablyClient2 = null;
        try {
            ClientOptions opts1 = createOptions(testVars.keys[7].keyStr);
            opts1.clientId = "clientId1";
            ablyClient1 = RealtimeClientFactory.create(opts1);

            ClientOptions opts2 = createOptions(testVars.keys[7].keyStr);
            opts2.clientId = "clientId2";
            ablyClient2 = RealtimeClientFactory.create(opts2);

            ChatRoom room = new ChatRoom(roomId, ablyClient1);

            // Create a channel and attach with client1
            final Channel channel1 = ablyClient1.channels.get(channelName);
            channel1.attach();
            (new Helpers.ChannelWaiter(channel1)).waitFor(ChannelState.attached);

            // Subscribe to messages with client2
            final Channel channel2 = ablyClient2.channels.get(channelName);
            channel2.attach();
            (new Helpers.ChannelWaiter(channel2)).waitFor(ChannelState.attached);

            List<Message> receivedMsg = new ArrayList<>();
            channel2.subscribe(receivedMsg::add);

            // Send message to room
            ChatRoom.SendMessageParams params = new ChatRoom.SendMessageParams();
            params.text = "hello there";
            JsonObject sendMessageResult = (JsonObject) room.sendMessage(params);
            String originalSerial = sendMessageResult.get("serial").getAsString();
            String originalCreatedAt = sendMessageResult.get("createdAt").getAsString();

            // Wait for the message to be received
            Exception err = new Helpers.ConditionalWaiter().wait(() -> !receivedMsg.isEmpty(), 10_000);
            Assert.assertNull(err);

            // Update the message
            ChatRoom.UpdateMessageParams updateParams = new ChatRoom.UpdateMessageParams();
            // Update message context
            updateParams.message = new ChatRoom.SendMessageParams();
            updateParams.message.text = "updated text";
            JsonObject metaData = new JsonObject();
            JsonObject foo = new JsonObject();
            foo.addProperty("bar", 1);
            metaData.add("foo", foo);
            updateParams.message.metadata = metaData;
            // Update description
            updateParams.description = "message updated by clientId1";

            // Update metadata, add few random fields
            Map<String, String> operationMetadata = new HashMap<>();
            operationMetadata.put("foo", "bar");
            operationMetadata.put("naruto", "hero");
            updateParams.metadata = operationMetadata;

            JsonObject updateMessageResult = (JsonObject) room.updateMessage(originalSerial, updateParams);
            String updateResultVersion = updateMessageResult.get("version").getAsString();
            String updateResultTimestamp = updateMessageResult.get("timestamp").getAsString();

            // Wait for the updated message to be received
            err = new Helpers.ConditionalWaiter().wait(() -> receivedMsg.size() == 2, 10_000);
            Assert.assertNull(err);

            // Verify the updated message
            Message updatedMessage = receivedMsg.get(1);

            Assert.assertEquals(MessageAction.MESSAGE_UPDATE, updatedMessage.action);

            Assert.assertFalse("Message ID should not be empty", updatedMessage.id.isEmpty());
            Assert.assertEquals("chat.message", updatedMessage.name);
            Assert.assertEquals("clientId1", updatedMessage.clientId);

            JsonObject data = (JsonObject) updatedMessage.data;
            Assert.assertEquals(2, data.entrySet().size());
            Assert.assertEquals("updated text", data.get("text").getAsString());
            JsonObject metadata = data.getAsJsonObject("metadata");
            Assert.assertTrue(metadata.has("foo"));
            Assert.assertTrue(metadata.get("foo").isJsonObject());
            Assert.assertEquals(1, metadata.getAsJsonObject("foo").get("bar").getAsInt());

            Assert.assertEquals(originalSerial, updatedMessage.serial);
            Assert.assertEquals(originalCreatedAt, String.valueOf(updatedMessage.timestamp));

            Assert.assertEquals(updateResultVersion, updatedMessage.version.serial);
            Assert.assertEquals(updateResultTimestamp, String.valueOf(updatedMessage.version.timestamp));

            // updatedMessage contains `operation` with fields as clientId, description, metadata, assert for these fields
            MessageVersion operation = updatedMessage.version;
            Assert.assertEquals("clientId1", operation.clientId);
            Assert.assertEquals("message updated by clientId1", operation.description);
            Assert.assertEquals(2, operation.metadata.size());
            Assert.assertEquals("bar", operation.metadata.get("foo"));
            Assert.assertEquals("hero", operation.metadata.get("naruto"));

        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception instantiating library");
        } finally {
            if (ablyClient1 != null) ablyClient1.close();
            if (ablyClient2 != null) ablyClient2.close();
        }
    }

    /**
     * Test that a message deleted via rest API is sent to a messages channel.
     * It should be received by another client that is subscribed to the same messages channel.
     * Make sure to use two clientIds: clientId1 and clientId2
     */
    @Test
    public void test_room_message_is_deleted() {
        String roomId = "1234";
        String channelName = roomId + "::$chat::$chatMessages";
        RealtimeClient ablyClient1 = null;
        RealtimeClient ablyClient2 = null;
        try {
            ClientOptions opts1 = createOptions(testVars.keys[7].keyStr);
            opts1.clientId = "clientId1";
            ablyClient1 = RealtimeClientFactory.create(opts1);

            ClientOptions opts2 = createOptions(testVars.keys[7].keyStr);
            opts2.clientId = "clientId2";
            ablyClient2 = RealtimeClientFactory.create(opts2);

            ChatRoom room = new ChatRoom(roomId, ablyClient1);

            // Create a channel and attach with client1
            final Channel channel1 = ablyClient1.channels.get(channelName);
            channel1.attach();
            (new Helpers.ChannelWaiter(channel1)).waitFor(ChannelState.attached);

            // Subscribe to messages with client2
            final Channel channel2 = ablyClient2.channels.get(channelName);
            channel2.attach();
            (new Helpers.ChannelWaiter(channel2)).waitFor(ChannelState.attached);

            List<Message> receivedMsg = new ArrayList<>();
            channel2.subscribe(receivedMsg::add);

            // Send message to room
            ChatRoom.SendMessageParams params = new ChatRoom.SendMessageParams();
            params.text = "hello there";
            JsonObject sendMessageResult = (JsonObject) room.sendMessage(params);
            String originalSerial = sendMessageResult.get("serial").getAsString();
            String originalCreatedAt = sendMessageResult.get("createdAt").getAsString();

            // Wait for the message to be received
            Exception err = new Helpers.ConditionalWaiter().wait(() -> !receivedMsg.isEmpty(), 10_000);
            Assert.assertNull(err);

            // Delete the message
            ChatRoom.DeleteMessageParams deleteParams = new ChatRoom.DeleteMessageParams();
            deleteParams.description = "message deleted by clientId1";
            Map<String, String> deleteMetadata = new HashMap<>();
            deleteMetadata.put("foo", "bar");
            deleteMetadata.put("naruto", "hero");
            deleteParams.metadata = deleteMetadata;

            JsonObject deleteMessageResult = (JsonObject) room.deleteMessage(originalSerial, deleteParams);
            String deleteResultVersion = deleteMessageResult.get("version").getAsString();
            String deleteResultTimestamp = deleteMessageResult.get("timestamp").getAsString();

            // Wait for the deleted message to be received
            err = new Helpers.ConditionalWaiter().wait(() -> receivedMsg.size() == 2, 10_000);
            Assert.assertNull(err);

            // Verify the deleted message
            Message deletedMessage = receivedMsg.get(1);

            Assert.assertEquals(MessageAction.MESSAGE_DELETE, deletedMessage.action);

            Assert.assertFalse("Message ID should not be empty", deletedMessage.id.isEmpty());
            Assert.assertEquals("chat.message", deletedMessage.name);
            Assert.assertEquals("clientId1", deletedMessage.clientId);

            Assert.assertEquals(originalSerial, deletedMessage.serial);
            Assert.assertEquals(originalCreatedAt, String.valueOf(deletedMessage.timestamp));

            Assert.assertEquals(deleteResultVersion, deletedMessage.version.serial);
            Assert.assertEquals(deleteResultTimestamp, String.valueOf(deletedMessage.version.timestamp));

            // deletedMessage contains `operation` with fields as clientId, reason
            MessageVersion version = deletedMessage.version;
            Assert.assertEquals("clientId1", version.clientId);
            Assert.assertEquals("message deleted by clientId1", version.description);
            // assert on metadata
            Assert.assertEquals(2, version.metadata.size());
            Assert.assertEquals("bar", version.metadata.get("foo"));
            Assert.assertEquals("hero", version.metadata.get("naruto"));

        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception instantiating library");
        } finally {
            if (ablyClient1 != null) ablyClient1.close();
            if (ablyClient2 != null) ablyClient2.close();
        }
    }

    /**
     * Test that message is created, updated and then deleted serially
     */
    @Test
    public void test_room_message_create_update_delete() {
        String roomId = "1234";
        String channelName = roomId + "::$chat::$chatMessages";
        RealtimeClient ablyClient1 = null;
        RealtimeClient ablyClient2 = null;
        try {
            ClientOptions opts1 = createOptions(testVars.keys[7].keyStr);
            opts1.clientId = "clientId1";
            ablyClient1 = RealtimeClientFactory.create(opts1);

            ClientOptions opts2 = createOptions(testVars.keys[7].keyStr);
            opts2.clientId = "clientId2";
            ablyClient2 = RealtimeClientFactory.create(opts2);

            ChatRoom room = new ChatRoom(roomId, ablyClient1);

            // Create a channel and attach with client1
            final Channel channel1 = ablyClient1.channels.get(channelName);
            channel1.attach();
            (new Helpers.ChannelWaiter(channel1)).waitFor(ChannelState.attached);

            // Subscribe to messages with client2
            final Channel channel2 = ablyClient2.channels.get(channelName);
            channel2.attach();
            (new Helpers.ChannelWaiter(channel2)).waitFor(ChannelState.attached);

            List<Message> receivedMsg = new ArrayList<>();
            channel2.subscribe(receivedMsg::add);

            // Send message to room
            ChatRoom.SendMessageParams sendParams = new ChatRoom.SendMessageParams();
            sendParams.text = "hello there";

            JsonObject sendMessageResult = (JsonObject) room.sendMessage(sendParams);
            String originalSerial = sendMessageResult.get("serial").getAsString();
            String originalCreatedAt = sendMessageResult.get("createdAt").getAsString();

            // Wait for the message to be received
            Exception err = new Helpers.ConditionalWaiter().wait(() -> !receivedMsg.isEmpty(), 10_000);
            Assert.assertNull(err);

            // Update the message
            ChatRoom.UpdateMessageParams updateParams = new ChatRoom.UpdateMessageParams();
            updateParams.message = new ChatRoom.SendMessageParams();
            updateParams.message.text = "updated text";

            JsonObject updateMessageResult = (JsonObject) room.updateMessage(originalSerial, updateParams);
            String updateResultVersion = updateMessageResult.get("version").getAsString();
            String updateResultTimestamp = updateMessageResult.get("timestamp").getAsString();

            // Wait for the updated message to be received
            err = new Helpers.ConditionalWaiter().wait(() -> receivedMsg.size() == 2, 10_000);
            Assert.assertNull(err);

            // Delete the message
            ChatRoom.DeleteMessageParams deleteParams = new ChatRoom.DeleteMessageParams();
            deleteParams.description = "message deleted by clientId1";

            JsonObject deleteMessageResult = (JsonObject) room.deleteMessage(originalSerial, deleteParams);
            String deleteResultVersion = deleteMessageResult.get("version").getAsString();
            String deleteResultTimestamp = deleteMessageResult.get("timestamp").getAsString();

            // Wait for the deleted message to be received
            err = new Helpers.ConditionalWaiter().wait(() -> receivedMsg.size() == 3, 10_000);
            Assert.assertNull(err);

            // Verify the created message
            Message createdMessage = receivedMsg.get(0);
            Assert.assertEquals(MessageAction.MESSAGE_CREATE, createdMessage.action);
            Assert.assertFalse("Message ID should not be empty", createdMessage.id.isEmpty());
            Assert.assertEquals("chat.message", createdMessage.name);
            Assert.assertEquals("clientId1", createdMessage.clientId);
            JsonObject createdData = (JsonObject) createdMessage.data;
            Assert.assertEquals("hello there", createdData.get("text").getAsString());

            // Verify the updated message
            Message updatedMessage = receivedMsg.get(1);
            Assert.assertEquals(MessageAction.MESSAGE_UPDATE, updatedMessage.action);
            Assert.assertFalse("Message ID should not be empty", updatedMessage.id.isEmpty());
            Assert.assertEquals("chat.message", updatedMessage.name);
            Assert.assertEquals("clientId1", updatedMessage.clientId);
            JsonObject updatedData = (JsonObject) updatedMessage.data;
            Assert.assertEquals("updated text", updatedData.get("text").getAsString());

            Assert.assertEquals(updateResultVersion, updatedMessage.version.serial);
            Assert.assertEquals(updateResultTimestamp, String.valueOf(updatedMessage.version.timestamp));

            // Verify the deleted message
            Message deletedMessage = receivedMsg.get(2);
            Assert.assertEquals(MessageAction.MESSAGE_DELETE, deletedMessage.action);
            Assert.assertFalse("Message ID should not be empty", deletedMessage.id.isEmpty());
            Assert.assertEquals("chat.message", deletedMessage.name);
            Assert.assertEquals("clientId1", deletedMessage.clientId);

            Assert.assertEquals(deleteResultVersion, deletedMessage.version.serial);
            Assert.assertEquals(deleteResultTimestamp, String.valueOf(deletedMessage.version.timestamp));

            // Check original serials
            Assert.assertEquals(originalSerial, createdMessage.serial);
            Assert.assertEquals(originalSerial, updatedMessage.serial);
            Assert.assertEquals(originalSerial, deletedMessage.serial);

            // Check original message timestamp
            Assert.assertEquals(originalCreatedAt, String.valueOf(createdMessage.timestamp));
            Assert.assertEquals(originalCreatedAt, String.valueOf(updatedMessage.timestamp));
            Assert.assertEquals(originalCreatedAt, String.valueOf(deletedMessage.timestamp));

        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception instantiating library");
        } finally {
            if (ablyClient1 != null) ablyClient1.close();
            if (ablyClient2 != null) ablyClient2.close();
        }
    }

    /**
     * Test that update/delete operations are allowed on a deleted message.
     */
    @Test
    public void test_operations_allowed_on_deleted_message() {
        String roomId = "1234";
        String channelName = roomId + "::$chat::$chatMessages";
        RealtimeClient ablyClient1 = null;
        RealtimeClient ablyClient2 = null;
        try {
            ClientOptions opts1 = createOptions(testVars.keys[7].keyStr);
            opts1.clientId = "clientId1";
            ablyClient1 = RealtimeClientFactory.create(opts1);

            ClientOptions opts2 = createOptions(testVars.keys[7].keyStr);
            opts2.clientId = "clientId2";
            ablyClient2 = RealtimeClientFactory.create(opts2);

            ChatRoom room = new ChatRoom(roomId, ablyClient1);

            // Create a channel and attach with client1
            final Channel channel1 = ablyClient1.channels.get(channelName);
            channel1.attach();
            (new Helpers.ChannelWaiter(channel1)).waitFor(ChannelState.attached);

            // Subscribe to messages with client2
            final Channel channel2 = ablyClient2.channels.get(channelName);
            channel2.attach();
            (new Helpers.ChannelWaiter(channel2)).waitFor(ChannelState.attached);

            List<Message> receivedMsg = new ArrayList<>();
            channel2.subscribe(receivedMsg::add);

            // Send message to room
            ChatRoom.SendMessageParams sendParams = new ChatRoom.SendMessageParams();
            sendParams.text = "hello there";

            JsonObject sendMessageResult = (JsonObject) room.sendMessage(sendParams);
            String originalSerial = sendMessageResult.get("serial").getAsString();

            // Wait for the message to be received
            Exception err = new Helpers.ConditionalWaiter().wait(() -> !receivedMsg.isEmpty(), 10_000);
            Assert.assertNull(err);

            // Delete the message
            ChatRoom.DeleteMessageParams deleteParams = new ChatRoom.DeleteMessageParams();
            deleteParams.description = "message deleted by clientId1";

            room.deleteMessage(originalSerial, deleteParams);

            // Wait for the deleted message to be received
            err = new Helpers.ConditionalWaiter().wait(() -> receivedMsg.size() == 2, 10_000);
            Assert.assertNull(err);

            // Attempt to update the deleted message
            ChatRoom.UpdateMessageParams updateParams = new ChatRoom.UpdateMessageParams();
            updateParams.message = new ChatRoom.SendMessageParams();
            updateParams.message.text = "updated text";
            room.updateMessage(originalSerial, updateParams);

            // wait for updated message to be received
            err = new Helpers.ConditionalWaiter().wait(() -> receivedMsg.size() == 3, 10_000);
            Assert.assertNull(err);

            // Attempt to delete the already deleted message
            room.deleteMessage(originalSerial, deleteParams);
            // wait for delete message received
            err = new Helpers.ConditionalWaiter().wait(() -> receivedMsg.size() == 4, 10_000);
            Assert.assertNull(err);

            Assert.assertEquals(4, receivedMsg.size());
            for (Message msg : receivedMsg) {
                Assert.assertEquals("Serial should match original serial", originalSerial, msg.serial);
            }

        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception instantiating library");
        } finally {
            if (ablyClient1 != null) ablyClient1.close();
            if (ablyClient2 != null) ablyClient2.close();
        }
    }
}
