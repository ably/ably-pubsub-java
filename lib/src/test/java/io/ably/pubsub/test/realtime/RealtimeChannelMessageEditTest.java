package io.ably.pubsub.test.realtime;

import io.ably.pubsub.realtime.RealtimeClient;
import io.ably.pubsub.realtime.Channel;
import io.ably.pubsub.test.common.Helpers;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ChannelOptions;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.Message;
import io.ably.pubsub.types.MessageAction;
import io.ably.pubsub.types.MessageOperation;
import io.ably.pubsub.types.PaginatedResult;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.types.PublishResult;
import io.ably.pubsub.types.UpdateDeleteResult;
import io.ably.pubsub.util.Crypto;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests for realtime channel message edit and delete operations
 */
public class RealtimeChannelMessageEditTest extends ParameterizedTest {

    @Rule
    public Timeout testTimeout = Timeout.seconds(300);
    private RealtimeClient ably;

    @Before
    public void setUpBefore() throws Exception {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        ably = new RealtimeClient(opts);
    }

    @After
    public void tearDown() {
        ably.close();
    }

    /**
     * Test getMessage: Publish a message and retrieve it by serial
     */
    @Test
    public void getMessage_retrieveBySerial() throws Exception {
        String channelName = "mutable:get_message_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        Helpers.AsyncWaiter<PublishResult> publishResultAsyncWaiter = new Helpers.AsyncWaiter<>();

        // Publish a message
        channel.publish("test_event", "Test message data", publishResultAsyncWaiter);

        publishResultAsyncWaiter.waitFor();
        PublishResult publishResult = publishResultAsyncWaiter.result;
        assertEquals("Expected to have one serial", 1, publishResult.serials.length);

        // Retrieve the message by serial
        Message retrievedMessage = waitForUpdatedMessageAppear(channel, publishResult.serials[0]);

        // Verify the retrieved message
        assertNotNull("Expected non-null retrieved message", retrievedMessage);
        assertEquals("Expected same message name", "test_event", retrievedMessage.name);
        assertEquals("Expected same message data", "Test message data", retrievedMessage.data);
        assertEquals("Expected same serial", publishResult.serials[0], retrievedMessage.serial);
    }

    /**
     * Test updateMessage: Update a message's data
     */
    @Test
    public void updateMessage_updateData() throws Exception {
        String channelName = "mutable:update_message_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        Helpers.AsyncWaiter<PublishResult> publishResultAsyncWaiter = new Helpers.AsyncWaiter<>();

        // Publish a message
        channel.publish("test_event", "Original message data", publishResultAsyncWaiter);

        // Get the message from history to obtain its serial
        publishResultAsyncWaiter.waitFor();
        PublishResult publishResult = publishResultAsyncWaiter.result;
        assertEquals("Expected to have one serial", 1, publishResult.serials.length);

        // Update the message
        Message updateMessage = new Message();
        updateMessage.serial = publishResult.serials[0];
        updateMessage.data = "Updated message data";
        updateMessage.name = "updated_event";

        Helpers.AsyncWaiter<UpdateDeleteResult> waiter = new Helpers.AsyncWaiter<>();

        channel.updateMessage(updateMessage, waiter);

        waiter.waitFor();

        // Retrieve the updated message
        Message updatedMessage = waitForUpdatedMessageAppear(channel, publishResult.serials[0]);

        // Verify the message was updated
        assertNotNull("Expected non-null updated message", updatedMessage);
        assertEquals("Expected updated message data", "Updated message data", updatedMessage.data);
        assertEquals("Expected updated message name", "updated_event", updatedMessage.name);
        assertEquals("Expected action to be MESSAGE_UPDATE", MessageAction.MESSAGE_UPDATE, updatedMessage.action);
        assertEquals("Expected same serial", updatedMessage.version.serial, waiter.result.versionSerial);
    }

    /**
     * Test updateMessage: Update a message's data
     */
    @Test
    public void updateMessage_updateEncodedData() throws Exception {
        String channelName = "mutable:update_encodedmessage_" + UUID.randomUUID() + "_" + testParams.name;
        ChannelOptions channelOptions = ChannelOptions.withCipherKey(Crypto.generateRandomKey());
        Channel channel = ably.channels.get(channelName, channelOptions);

        Helpers.AsyncWaiter<PublishResult> publishResultAsyncWaiter = new Helpers.AsyncWaiter<>();

        // Publish a message
        channel.publish("test_event", "Original message data", publishResultAsyncWaiter);

        // Get the message from history to obtain its serial
        publishResultAsyncWaiter.waitFor();
        PublishResult publishResult = publishResultAsyncWaiter.result;
        assertEquals("Expected to have one serial", 1, publishResult.serials.length);

        // Update the message
        Message updateMessage = new Message();
        updateMessage.serial = publishResult.serials[0];
        updateMessage.data = "Updated message data";
        updateMessage.name = "updated_event";

        channel.updateMessage(updateMessage);

        // Retrieve the updated message
        Message updatedMessage = waitForUpdatedMessageAppear(channel, publishResult.serials[0]);

        // Verify the message was updated
        assertNotNull("Expected non-null updated message", updatedMessage);
        assertEquals("Expected updated message data", "Updated message data", updatedMessage.data);
        assertEquals("Expected updated message name", "updated_event", updatedMessage.name);
        assertEquals("Expected action to be MESSAGE_UPDATE", MessageAction.MESSAGE_UPDATE, updatedMessage.action);
    }

    /**
     * Test deleteMessage: Soft delete a message
     */
    @Test
    public void deleteMessage_softDelete() throws Exception {
        String channelName = "mutable:delete_message_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        Helpers.AsyncWaiter<PublishResult> publishResultAsyncWaiter = new Helpers.AsyncWaiter<>();

        // Publish a message
        channel.publish("test_event", "Message to be deleted", publishResultAsyncWaiter);

        // Get the message from history
        publishResultAsyncWaiter.waitFor();
        PublishResult publishResult = publishResultAsyncWaiter.result;
        assertEquals("Expected to have one serial", 1, publishResult.serials.length);

        // Delete the message
        Message deleteMessage = new Message();
        deleteMessage.serial = publishResult.serials[0];
        deleteMessage.data = "Message deleted";

        channel.deleteMessage(deleteMessage);

        // Retrieve the deleted message
        Message deletedMessage = waitForDeletedMessageAppear(channel, publishResult.serials[0]);

        // Verify the message was soft deleted
        assertNotNull("Expected non-null deleted message", deletedMessage);
        assertEquals("Expected action to be MESSAGE_DELETE", MessageAction.MESSAGE_DELETE, deletedMessage.action);
    }

    /**
     * Test getMessageVersions: Retrieve version history of a message
     */
    @Test
    public void getMessageVersions_retrieveHistory() throws Exception {
        String channelName = "mutable:message_versions_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        Helpers.AsyncWaiter<PublishResult> publishResultAsyncWaiter = new Helpers.AsyncWaiter<>();

        // Publish a message
        channel.publish("test_event", "Original data", publishResultAsyncWaiter);

        // Get the message from history
        publishResultAsyncWaiter.waitFor();
        PublishResult publishResult = publishResultAsyncWaiter.result;
        assertEquals("Expected to have one serial", 1, publishResult.serials.length);

        // Update the message to create version history
        Message updateMessage1 = new Message();
        updateMessage1.serial = publishResult.serials[0];
        updateMessage1.data = "First update";
        channel.updateMessage(updateMessage1);

        Message updateMessage2 = new Message();
        updateMessage2.serial = publishResult.serials[0];
        updateMessage2.data = "Second update";
        MessageOperation messageOperation = new MessageOperation();
        messageOperation.description = "description";
        messageOperation.metadata = new HashMap<>();
        messageOperation.metadata.put("key", "value");
        channel.updateMessage(updateMessage2, messageOperation);

        // Retrieve version history
        PaginatedResult<Message> versions = waitForMessageAppearInVersionHistory(channel, publishResult.serials[0], null, msgs ->
            msgs.length >= 3
        );

        // Verify version history
        assertNotNull("Expected non-null versions", versions);
        assertTrue("Expected at least 3 versions (original + 2 updates)", versions.items().length >= 3);

        Message latestVersion = versions.items()[versions.items().length - 1];
        assertEquals("Expected latest version to have second update data", "Second update", latestVersion.data);
        assertEquals("description", latestVersion.version.description);
        assertEquals("value", latestVersion.version.metadata.get("key"));
    }

    /**
     * Test error handling: getMessage with invalid serial
     */
    @Test
    public void getMessage_invalidSerial() {
        String channelName = "mutable:get_message_invalid_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        AblyException exception = assertThrows(AblyException.class, () -> {
            channel.getMessage("invalid_serial_12345");
        });

        assertNotNull("Expected error info", exception.errorInfo);
    }

    /**
     * Test error handling: updateMessage with null serial
     */
    @Test
    public void updateMessage_nullSerial() {
        String channelName = "mutable:update_message_null_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        AblyException exception = assertThrows(AblyException.class, () -> {
            Message updateMessage = new Message();
            updateMessage.serial = null;
            updateMessage.data = "Update data";

            channel.updateMessage(updateMessage);
        });

        assertNotNull("Expected error info", exception.errorInfo);
        assertTrue("Expected error message about serial",
            exception.errorInfo.message.toLowerCase().contains("serial"));
    }

    /**
     * Test error handling: deleteMessage with empty serial
     */
    @Test
    public void deleteMessage_emptySerial() {
        String channelName = "mutable:delete_message_empty_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        AblyException exception = assertThrows(AblyException.class, () -> {
            Message deleteMessage = new Message();
            deleteMessage.serial = "";
            deleteMessage.data = "Delete data";

            channel.deleteMessage(deleteMessage);
        });

        assertNotNull("Expected error info", exception.errorInfo);
        assertTrue("Expected error message about serial",
            exception.errorInfo.message.toLowerCase().contains("serial"));
    }

    /**
     * Test complete workflow: publish, update, get versions, delete
     */
    @Test
    public void completeWorkflow_publishUpdateVersionsDelete() throws Exception {
        String channelName = "mutable:complete_workflow_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        Helpers.AsyncWaiter<PublishResult> publishResultAsyncWaiter = new Helpers.AsyncWaiter<>();

        // 1. Publish a message
        channel.publish("workflow_event", "Initial data", publishResultAsyncWaiter);

        // Get the published message
        publishResultAsyncWaiter.waitFor();
        PublishResult publishResult = publishResultAsyncWaiter.result;
        assertEquals("Expected to have one serial", 1, publishResult.serials.length);
        String serial = publishResult.serials[0];

        // 2. Update the message
        Message updateMessage = new Message();
        updateMessage.serial = serial;
        updateMessage.data = "Updated data";
        updateMessage.name = "workflow_event_updated";

        Helpers.AsyncWaiter<UpdateDeleteResult> updateWaiter = new Helpers.AsyncWaiter<>();

        channel.updateMessage(updateMessage, updateWaiter);
        updateWaiter.waitFor();

        // 3. Verify update
        Message retrieved = waitForUpdatedMessageAppear(channel, serial);
        assertEquals("Expected updated data", "Updated data", retrieved.data);
        assertEquals("Expected MESSAGE_UPDATE action", MessageAction.MESSAGE_UPDATE, retrieved.action);

        // 4. Delete the message
        Message deleteMessage = new Message();
        deleteMessage.serial = serial;
        deleteMessage.data = "Deleted";

        Helpers.AsyncWaiter<UpdateDeleteResult> deleteWaiter = new Helpers.AsyncWaiter<>();
        channel.deleteMessage(deleteMessage, deleteWaiter);
        deleteWaiter.waitFor();

        // 5. Verify deletion
        Message deleted = waitForDeletedMessageAppear(channel, serial);
        assertEquals("Expected MESSAGE_DELETE action", MessageAction.MESSAGE_DELETE, deleted.action);

        // 6. Verify delete appears in versions
        PaginatedResult<Message> finalVersions = waitForMessageAppearInVersionHistory(channel, serial, null, msgs ->
            msgs.length > 0 && msgs[msgs.length - 1].action == MessageAction.MESSAGE_DELETE
        );
        assertTrue("Expected at least 3 versions (create, update, delete)", finalVersions.items().length >= 3);
        assertNull(finalVersions.items()[0].version);
        assertEquals(updateWaiter.result.versionSerial, finalVersions.items()[1].version.serial);
        assertEquals(deleteWaiter.result.versionSerial, finalVersions.items()[2].version.serial);
    }

    @Test
    public void appendMessage_checkUpdatedData() throws Exception {
        String channelName = "mutable:message_append_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        Helpers.AsyncWaiter<PublishResult> publishResultAsyncWaiter = new Helpers.AsyncWaiter<>();

        // 1. Publish a message
        channel.publish("append_event", "Initial data", publishResultAsyncWaiter);

        // Get the published message
        publishResultAsyncWaiter.waitFor();
        PublishResult publishResult = publishResultAsyncWaiter.result;
        assertEquals("Expected to have one serial", 1, publishResult.serials.length);

        String serial = publishResult.serials[0];

        Helpers.AsyncWaiter<Message> appendWaiter = new Helpers.AsyncWaiter<>();

        channel.subscribe(msg -> {
            if (msg.serial.equals(serial)) {
                appendWaiter.onSuccess(msg);
            } else {
                appendWaiter.onError(null);
            }
        });

        // 2. Update the message
        Message messageAppend = new Message();
        messageAppend.serial = serial;
        messageAppend.data = "Append data";
        channel.appendMessage(messageAppend);

        appendWaiter.waitFor();

        // 3. Verify update
        Message retrieved = waitForUpdatedMessageAppear(channel, serial);
        assertEquals("Expected updated data in history", "Initial dataAppend data", retrieved.data);
        assertEquals("Expected MESSAGE_UPDATE action in history", MessageAction.MESSAGE_UPDATE, retrieved.action);
        assertEquals("Expected MESSAGE_UPDATE only for the first time from realtime", MessageAction.MESSAGE_UPDATE, appendWaiter.result.action);
        assertEquals("Expected full message data through realtime", "Initial dataAppend data", appendWaiter.result.data);

        appendWaiter.reset();

        channel.appendMessage(messageAppend);

        appendWaiter.waitFor();

        assertEquals("Expected MESSAGE_APPEND action through realtime", MessageAction.MESSAGE_APPEND, appendWaiter.result.action);
        assertEquals("Expected only delta through realtime", "Append data", appendWaiter.result.data);
    }

    private PaginatedResult<Message> waitForMessageAppearInVersionHistory(Channel channel, String serial, Param[] params, Predicate<Message[]> predicate) throws Exception {
        long timeout = System.currentTimeMillis() + 5_000;
        while (true) {
            PaginatedResult<Message> history = channel.getMessageVersions(serial, params);
            if ((history.items().length > 0 && predicate.test(history.items())) || System.currentTimeMillis() > timeout)
                return history;
            Thread.sleep(200);
        }
    }

    private Message waitForUpdatedMessageAppear(Channel channel, String serial) throws Exception {
        long timeout = System.currentTimeMillis() + 5_000;
        while (true) {
            try {
                Message message = channel.getMessage(serial);
                if ((message != null && message.action == MessageAction.MESSAGE_UPDATE) || System.currentTimeMillis() > timeout)
                    return message;
                Thread.sleep(200);
            } catch (AblyException e) {
                // skip not found errors
                if (e.errorInfo.statusCode != 404) throw e;
            }
        }
    }

    private Message waitForDeletedMessageAppear(Channel channel, String serial) throws Exception {
        long timeout = System.currentTimeMillis() + 5_000;
        while (true) {
            try {
                Message message = channel.getMessage(serial);
                if ((message != null && message.action == MessageAction.MESSAGE_DELETE) || System.currentTimeMillis() > timeout)
                    return message;
                Thread.sleep(200);
            } catch (AblyException e) {
                // skip not found errors
                if (e.errorInfo.statusCode != 404) throw e;
            }
        }
    }
}
