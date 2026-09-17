package io.ably.pubsub.test.realtime;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import javax.crypto.KeyGenerator;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import io.ably.pubsub.realtime.RealtimeClient;
import io.ably.pubsub.realtime.RealtimeClientFactory;
import io.ably.pubsub.realtime.Channel;
import io.ably.pubsub.realtime.ChannelState;
import io.ably.pubsub.test.common.Helpers.ChannelWaiter;
import io.ably.pubsub.test.common.Helpers.CompletionSet;
import io.ably.pubsub.test.common.Helpers.CompletionWaiter;
import io.ably.pubsub.test.common.Helpers.MessageWaiter;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ChannelOptions;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.ErrorInfo;
import io.ably.pubsub.util.Crypto;
import io.ably.pubsub.util.Crypto.EncryptingChannelCipher;
import io.ably.pubsub.util.Crypto.DecryptingChannelCipher;
import io.ably.pubsub.util.Crypto.CipherParams;

public class RealtimeCryptoTest extends ParameterizedTest {

    @Rule
    public Timeout testTimeout = Timeout.seconds(30);

    /**
     * Connect to the service
     * and publish an encrypted message on that channel using
     * the default cipher params
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void single_send() {
        String channelName = "single_send_" + testParams.name;
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* create a channel */
            ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
            final Channel channel = ably.channels.get(channelName, channelOpts);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Channel is not attached", channel.state, ChannelState.attached);

            /* subscribe */
            MessageWaiter messageWaiter =  new MessageWaiter(channel);

            /* publish to the channel */
            String messageText = "Test message (subscribe_send_binary)";
            CompletionWaiter msgComplete = new CompletionWaiter();
            channel.publish("test_event", messageText, msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Success callback was not called", msgComplete.success);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(1);
            assertEquals(
                "Unexpected number of received messages",
                messageWaiter.receivedMessages.size(), 1
            );

            /* check the correct plaintext recovered from the message */
            assertTrue(
                "Unexpected message received",
                messageText.equals(messageWaiter.receivedMessages.get(0).data)
            );

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null) {
                ably.close();
            }
        }
    }

    /**
     * Connect to the service
     * and publish an encrypted message on that channel using
     * a 256-bit key
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void single_send_256() {
        String channelName = "single_send_256_" + testParams.name;
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* create a key */
            KeyGenerator keygen = KeyGenerator.getInstance("AES");
            keygen.init(256);
            byte[] key = keygen.generateKey().getEncoded();
            final CipherParams params = Crypto.getDefaultParams(key);

            /* create a channel */
            ChannelOptions channelOpts = new ChannelOptions();
            channelOpts.encrypted = true;
            channelOpts.cipherParams = params;
            final Channel channel = ably.channels.get(channelName, channelOpts);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals(
                "Channel is not attached",
                channel.state, ChannelState.attached
            );

            /* subscribe */
            MessageWaiter messageWaiter =  new MessageWaiter(channel);

            /* publish to the channel */
            String messageText = "Test message (subscribe_send_binary)";
            CompletionWaiter msgComplete = new CompletionWaiter();
            channel.publish("test_event", messageText, msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Success callback was not called", msgComplete.success);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(1);
            assertEquals(
                "Unexpected number of received messages",
                messageWaiter.receivedMessages.size(), 1
            );

            /* check the correct plaintext recovered from the message */
            assertTrue(
                "Unexpected message received",
                messageText.equals(messageWaiter.receivedMessages.get(0).data)
            );

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception generating key");
        } finally {
            if(ably != null) {
                ably.close();
            }
        }
    }

    /**
     * Connect to the service using the default (binary) protocol
     * and attach, subscribe to an event, and publish multiple
     * messages on that channel
     */
    private void _multiple_send(String channelName, int messageCount, long delay) {
        RealtimeClient ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = RealtimeClientFactory.create(opts);

            /* generate and remember message texts */
            String[] messageTexts = new String[messageCount];
            for(int i = 0; i < messageCount; i++) {
                messageTexts[i] = "Test message (_multiple_send) " + i;
            }
            /* create a channel */
            ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
            final Channel channel = ably.channels.get(channelName, channelOpts);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals(
                "Channel is not attached",
                channel.state, ChannelState.attached
            );

            /* subscribe */
            MessageWaiter messageWaiter =  new MessageWaiter(channel);

            /* publish to the channel */
            CompletionSet msgComplete = new CompletionSet();
            for(int i = 0; i < messageCount; i++) {
                channel.publish("test_event", messageTexts[i], msgComplete.add());
                try { Thread.sleep(delay); } catch(InterruptedException e){}
            }

            /* wait for the publish callback to be called */
            ErrorInfo[] errors = msgComplete.waitFor();
            assertTrue("Errors when sending messages", errors.length == 0);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(messageCount);
            assertEquals(
                "Unexpected number of messages received",
                messageWaiter.receivedMessages.size(), messageCount
            );

            /* check the correct plaintext recovered from the message */
            for(int i = 0; i < messageCount; i++) {
                assertTrue(
                    "Unexpected message received",
                    messageTexts[i].equals(
                        messageWaiter.receivedMessages.get(i).data
                    )
                );
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null) {
                ably.close();
            }
        }
    }

    @Ignore("FIXME: fix exception")
    @Test
    public void multiple_send_2_200() {
        int messageCount = 2;
        long delay = 200L;
        _multiple_send("multiple_send_binary_2_200_" + testParams.name, messageCount, delay);
    }

    @Ignore("FIXME: fix exception")
    @Test
    public void multiple_send_20_100() {
        int messageCount = 20;
        long delay = 100L;
        _multiple_send("multiple_send_binary_20_100_" + testParams.name, messageCount, delay);
    }

    /**
     * Connect twice to the service, using the default (binary) protocol
     * and the text protocol. Publish an encrypted message on that channel using
     * the default cipher params and verify correct receipt.
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void single_send_binary_text() {
        String channelName = "single_send_binary_text_" + testParams.name;
        RealtimeClient sender = null;
        RealtimeClient receiver = null;
        try {
            ClientOptions senderOpts = createOptions(testVars.keys[0].keyStr);
            sender = RealtimeClientFactory.create(senderOpts);
            ClientOptions receiverOpts = createOptions(testVars.keys[0].keyStr);
            receiverOpts.useBinaryProtocol = !testParams.useBinaryProtocol;
            receiver = RealtimeClientFactory.create(receiverOpts);

            /* create a key */
            final CipherParams params = Crypto.getDefaultParams();

            /* create a channel */
            final ChannelOptions senderChannelOpts = new ChannelOptions();
            senderChannelOpts.encrypted = true;
            senderChannelOpts.cipherParams = params;

            final Channel senderChannel = sender.channels.get(channelName, senderChannelOpts);

            final ChannelOptions receiverChannelOpts = new ChannelOptions();
            receiverChannelOpts.encrypted = true;
            receiverChannelOpts.cipherParams = params;

            final Channel receiverChannel = receiver.channels.get(channelName, receiverChannelOpts);

            /* attach */
            senderChannel.attach();
            receiverChannel.attach();
            (new ChannelWaiter(senderChannel)).waitFor(ChannelState.attached);
            (new ChannelWaiter(receiverChannel)).waitFor(ChannelState.attached);
            assertEquals(
                "Sender channel is not attached",
                senderChannel.state, ChannelState.attached
            );
            assertEquals(
                "Receiver channel is not attached",
                receiverChannel.state, ChannelState.attached
            );

            /* subscribe */
            MessageWaiter messageWaiter =  new MessageWaiter(receiverChannel);

            /* publish to the channel */
            String messageText = "Test message (single_send_binary_text)";
            CompletionWaiter msgComplete = new CompletionWaiter();
            senderChannel.publish("test_event", messageText, msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Success callback was not called", msgComplete.success);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(1);
            assertEquals(
                "Unexpected number of received messages",
                messageWaiter.receivedMessages.size(), 1
            );
            /* check the correct plaintext recovered from the message */
            assertTrue(
                "Unexpected message received",
                messageText.equals(messageWaiter.receivedMessages.get(0).data)
            );
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
     * Connect twice to the service, using different cipher keys.
     * Publish an encrypted message on that channel using
     * the default cipher params and verify that the decrypt failure
     * is noticed as bad recovered plaintext.
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void single_send_key_mismatch() {
        RealtimeClient sender = null;
        RealtimeClient receiver = null;
        try {
            ClientOptions senderOpts = createOptions(testVars.keys[0].keyStr);
            sender = RealtimeClientFactory.create(senderOpts);
            ClientOptions receiverOpts = createOptions(testVars.keys[0].keyStr);
            receiver = RealtimeClientFactory.create(receiverOpts);

            /* create a channel */
            final ChannelOptions senderChannelOpts = new ChannelOptions() {{ encrypted = true; }};
            final Channel senderChannel = sender.channels.get("single_send_binary_text", senderChannelOpts);
            final ChannelOptions receiverChannelOpts = new ChannelOptions() {{ encrypted = true; }};
            final Channel receiverChannel = receiver.channels.get("single_send_binary_text", receiverChannelOpts);

            /* attach */
            senderChannel.attach();
            receiverChannel.attach();
            (new ChannelWaiter(senderChannel)).waitFor(ChannelState.attached);
            (new ChannelWaiter(receiverChannel)).waitFor(ChannelState.attached);
            assertEquals(
                "Sender channel is not attached",
                senderChannel.state, ChannelState.attached
            );
            assertEquals(
                "Receiver channel is not attached",
                receiverChannel.state, ChannelState.attached
            );

            /* subscribe */
            MessageWaiter messageWaiter =  new MessageWaiter(receiverChannel);

            /* publish to the channel */
            String messageText = "Test message (single_send_key_mismatch)";
            CompletionWaiter msgComplete = new CompletionWaiter();
            senderChannel.publish("test_event", messageText, msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Success callback was not called", msgComplete.success);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(1);
            assertEquals(
                "Unexpected number of received messages",
                messageWaiter.receivedMessages.size(), 1
            );

            /* check the correct plaintext recovered from the message */
            assertFalse(
                "Unexpected message received",
                messageText.equals(messageWaiter.receivedMessages.get(0).data)
            );
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
     * Connect twice to the service, one with and one without encryption.
     * Publish an unencrypted message and verify that the receiving connection
     * does not attempt to decrypt it.
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void single_send_unencrypted() {
        RealtimeClient sender = null;
        RealtimeClient receiver = null;
        try {
            ClientOptions senderOpts = createOptions(testVars.keys[0].keyStr);
            sender = RealtimeClientFactory.create(senderOpts);
            ClientOptions receiverOpts = createOptions(testVars.keys[0].keyStr);
            receiver = RealtimeClientFactory.create(receiverOpts);

            /* create a channel */
            final Channel senderChannel = sender.channels.get("single_send_unencrypted");
            ChannelOptions receiverChannelOpts = new ChannelOptions() {{ encrypted = true; }};
            final Channel receiverChannel = receiver.channels.get("single_send_unencrypted", receiverChannelOpts);

            /* attach */
            senderChannel.attach();
            receiverChannel.attach();
            (new ChannelWaiter(senderChannel)).waitFor(ChannelState.attached);
            (new ChannelWaiter(receiverChannel)).waitFor(ChannelState.attached);
            assertEquals(
                "Sender channel is not attached",
                senderChannel.state, ChannelState.attached
            );
            assertEquals(
                "Receiver channel is not attached",
                receiverChannel.state, ChannelState.attached
            );

            /* subscribe */
            MessageWaiter messageWaiter =  new MessageWaiter(receiverChannel);

            /* publish to the channel */
            String messageText = "Test message (single_send_unencrypted)";
            CompletionWaiter msgComplete = new CompletionWaiter();
            senderChannel.publish("test_event", messageText, msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Success callback was not called", msgComplete.success);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(1);
            assertEquals(
                "Unexpected number of received messages",
                messageWaiter.receivedMessages.size(), 1
            );

            /* check the correct text recovered from the message */
            assertTrue(
                "Received message is not correct",
                messageText.equals(messageWaiter.receivedMessages.get(0).data)
            );
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
     * Connect twice to the service, one with and one without encryption.
     * Publish an unencrypted message and verify that the receiving connection
     * does not attempt to decrypt it.
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void single_send_encrypted_unhandled() {
        RealtimeClient sender = null;
        RealtimeClient receiver = null;
        try {
            ClientOptions senderOpts = createOptions(testVars.keys[0].keyStr);
            sender = RealtimeClientFactory.create(senderOpts);
            ClientOptions receiverOpts = createOptions(testVars.keys[0].keyStr);
            receiver = RealtimeClientFactory.create(receiverOpts);

            /* create a channel */
            ChannelOptions senderChannelOpts = new ChannelOptions() {{ encrypted = true; }};
            final Channel senderChannel = sender.channels.get("single_send_encrypted_unhandled", senderChannelOpts);
            final Channel receiverChannel = receiver.channels.get("single_send_encrypted_unhandled");

            /* attach */
            senderChannel.attach();
            receiverChannel.attach();
            (new ChannelWaiter(senderChannel)).waitFor(ChannelState.attached);
            (new ChannelWaiter(receiverChannel)).waitFor(ChannelState.attached);
            assertEquals(
                "Sender channel is not attached",
                senderChannel.state, ChannelState.attached
            );
            assertEquals(
                "Receiver channel is not attached",
                receiverChannel.state, ChannelState.attached
            );

            /* subscribe */
            MessageWaiter messageWaiter =  new MessageWaiter(receiverChannel);

            /* publish to the channel */
            String messageText = "Test message (single_send_encrypted_unhandled)";
            CompletionWaiter msgComplete = new CompletionWaiter();
            senderChannel.publish("test_event", messageText, msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Success callback was not called", msgComplete.success);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(1);
            assertEquals(
                "Unexpected number of messages received",
                messageWaiter.receivedMessages.size(), 1
            );

            /* check the the message payload is indicated as encrypted */
//          assertTrue("Verify correct message text received", messageWaiter.receivedMessages.get(0).data instanceof CipherData);

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
     * Check Channel.setOptions updates CipherParams correctly:
     * - publish a message using a key, verifying correct receipt;
     * - publish with an updated key on the tx connection and verify that it is not decrypted by the rx connection;
     * - publish with an updated key on the rx connection and verify connect receipt
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void set_cipher_params() {
        RealtimeClient sender = null;
        RealtimeClient receiver = null;
        try {
            ClientOptions senderOpts = createOptions(testVars.keys[0].keyStr);
            sender = RealtimeClientFactory.create(senderOpts);
            ClientOptions receiverOpts = createOptions(testVars.keys[0].keyStr);
            receiverOpts.useBinaryProtocol = !testParams.useBinaryProtocol;
            receiver = RealtimeClientFactory.create(receiverOpts);

            /* create a key */
            final CipherParams params1 = Crypto.getDefaultParams();

            /* create a channel */
            ChannelOptions senderChannelOpts = new ChannelOptions();
            senderChannelOpts.encrypted = true;
            senderChannelOpts.cipherParams = params1;
            final Channel senderChannel = sender.channels.get("set_cipher_params", senderChannelOpts);

            ChannelOptions receiverChannelOpts = new ChannelOptions();
            receiverChannelOpts.encrypted = true;
            receiverChannelOpts.cipherParams = params1;
            final Channel receiverChannel = receiver.channels.get("set_cipher_params", receiverChannelOpts);

            /* attach */
            senderChannel.attach();
            receiverChannel.attach();
            (new ChannelWaiter(senderChannel)).waitFor(ChannelState.attached);
            (new ChannelWaiter(receiverChannel)).waitFor(ChannelState.attached);

            assertEquals(
                "Sender channel is not attached",
                senderChannel.state, ChannelState.attached
            );
            assertEquals(
                "Receiver channel is not attached",
                receiverChannel.state, ChannelState.attached
            );

            /* subscribe */
            MessageWaiter messageWaiter =  new MessageWaiter(receiverChannel);

            /* publish to the channel */
            String messageText = "Test message (set_cipher_params)";
            CompletionWaiter msgComplete = new CompletionWaiter();
            senderChannel.publish("test_event", messageText, msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Success callback was not called", msgComplete.success);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(1);
            assertEquals(
                "Unexpected number of received messages",
                messageWaiter.receivedMessages.size(), 1
            );

            /* check the correct plaintext recovered from the message */
            assertTrue(
                "Received message is not correct",
                messageText.equals(messageWaiter.receivedMessages.get(0).data)
            );

            /* create a second key and set sender channel opts */
            final CipherParams params2 = Crypto.getDefaultParams();
            senderChannelOpts = new ChannelOptions() {{ encrypted = true; cipherParams = params2; }};
            senderChannel.setOptions(senderChannelOpts);

            /* publish to the channel, wait, check message bad */
            messageWaiter.reset();
            senderChannel.publish("test_event", messageText, msgComplete);
            messageWaiter.waitFor(1);
            assertFalse(
                "Received message is not correct",
                messageText.equals(messageWaiter.receivedMessages.get(0).data)
            );

            /* See issue https://github.com/ably/ably-java/issues/202
             * This final part of the test fails intermittently. For now just try
             * it multiple times. */
            for (int count = 4;; --count) {
                assertTrue("Verify correct plaintext received", count != 0);

                /* set rx channel opts */
                receiverChannel.setOptions(senderChannelOpts);

                /* publish to the channel, wait, check message bad */
                messageWaiter.reset();
                senderChannel.publish("test_event", messageText, msgComplete);
                messageWaiter.waitFor(1);
                if (messageText.equals(messageWaiter.receivedMessages.get(0).data)) {
                    break;
                }
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
     * Test channel options creation with the cipher key.
     * @see <a href="https://docs.ably.com/client-lib-development-guide/features/#TB3>TB3</a>
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void channel_options_with_cipher_key() {
        String channelName = "cipher_params_test_" + testParams.name;
        RealtimeClient sender = null;
        RealtimeClient receiver = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            sender = RealtimeClientFactory.create(opts);
            receiver = RealtimeClientFactory.create(opts);

            /* 128-bit key */
            byte[] key = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
            /* Same key but encoded with Base64 */
            String base64key = "AQIDBAUGBwgJCgsMDQ4PEA==";

            /* create a sending channel using byte[] array */
            final Channel channelSend = sender.channels.get(channelName, ChannelOptions.withCipherKey(key));
            /* create a receiving channel using (the same) key encoded with base64 */
            final Channel channelReceive = receiver.channels.get(channelName, ChannelOptions.withCipherKey(base64key));

            /* attach */
            channelSend.attach();
            channelReceive.attach();
            new ChannelWaiter(channelSend).waitFor(ChannelState.attached);
            new ChannelWaiter(channelReceive).waitFor(ChannelState.attached);

            /* subscribe */
            MessageWaiter messageWaiter =  new MessageWaiter(channelReceive);

            /* publish to the channel */
            String messageText = "Test message";
            CompletionWaiter msgComplete = new CompletionWaiter();
            channelSend.publish("test_event", messageText, msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Success callback was not called", msgComplete.success);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(1);
            assertEquals(
                "Unexpected number of received messages",
                messageWaiter.receivedMessages.size(), 1
            );

            /* check the correct plaintext recovered from the message */
            assertTrue(
                "Received message is not correct",
                messageText.equals(messageWaiter.receivedMessages.get(0).data)
            );

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

    @Test
    public void encodeDecodeVariableSizesWithAES256CBC() throws NoSuchAlgorithmException, AblyException {
        final CipherParams params = Crypto.getParams("aes", generateNonce(32), generateNonce(16));
        final Crypto.EncryptingChannelCipher encipher = Crypto.createChannelEncipher(params);
        final Crypto.DecryptingChannelCipher decipher = Crypto.createChannelDecipher(params);
        for (int i=1; i<1000; i++) {
            final int size = RANDOM.nextInt(2000) + 1;
            final byte[] message = generateNonce(size);
            final byte[] encrypted = encipher.encrypt(message);
            final byte[] decrypted = decipher.decrypt(encrypted);
            try {
                assertArrayEquals(message, decrypted);
            } catch (final AssertionError e) {
                throw new AssertionError("Failed at #" + i + " for size " + size, e);
            }
        }
    }

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private static String byteArrayToHexString(final byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    @Ignore("FIXME: fix BadPaddingException")
    @Test
    public void decodeAppleLibrarySequences() throws NoSuchAlgorithmException, AblyException {
        final Map<String, String> apple = new LinkedHashMap<>();
        final String appleKey = "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F20";
        final String appleIv = "100F0E0D0C0B0A090807060504030201";
        apple.put(
            "01",
            "100F0E0D0C0B0A090807060504030201C18B3B262A725C728E2089A9BB04E0C9");
        apple.put(
            "0102",
            "100F0E0D0C0B0A09080706050403020107FEEFB5103001C131166F6D3DB66143");
        apple.put(
            "010203",
            "100F0E0D0C0B0A0908070605040302018C9E6A8CBACA88F4AFC78132D0F194E3");
        apple.put(
            "01020304",
            "100F0E0D0C0B0A090807060504030201AB8C3A090FCB8CED353A621F76ABDB8A");
        apple.put(
            "0102030405",
            "100F0E0D0C0B0A09080706050403020199BF04E9DDF21E591FA4BB45E734F6BD");
        apple.put(
            "010203040506",
            "100F0E0D0C0B0A09080706050403020149C87F17C0DDAD95ED6BB5E985E628AD");
        apple.put(
            "01020304050607",
            "100F0E0D0C0B0A090807060504030201C9CBB2F122CA14A95AE8AE01FC817E84");
        apple.put(
            "0102030405060708",
            "100F0E0D0C0B0A090807060504030201C85B0A14C1C21512D82DA3AECCB3201A");
        apple.put(
            "010203040506070809",
            "100F0E0D0C0B0A09080706050403020155311B93A81FD9642034DE137E2CE98D");
        apple.put(
            "0102030405060708090A",
            "100F0E0D0C0B0A0908070605040302012D9DCACDE38301B77E2C51B72FE9F31B");
        apple.put(
            "0102030405060708090A0B",
            "100F0E0D0C0B0A090807060504030201265782DBDF11A2AD0DEB9F71231CA9BA");
        apple.put(
            "0102030405060708090A0B0C",
            "100F0E0D0C0B0A0908070605040302019C196DB8E04A3067939931351D015CAE");
        apple.put(
            "0102030405060708090A0B0C0D",
            "100F0E0D0C0B0A0908070605040302011BB9EFC492B650703761DAEFF97A1FC1");
        apple.put(
            "0102030405060708090A0B0C0D0E",
            "100F0E0D0C0B0A09080706050403020157CC4C876775E5FC7B57C8876CDC0CEA");
        apple.put(
            "0102030405060708090A0B0C0D0E0F",
            "100F0E0D0C0B0A09080706050403020103F86465C2295B868CBB3F98A5DE0DF0");
        apple.put(
            "0102030405060708090A0B0C0D0E0F10",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389197EF97609BBE4B7D292AFE6511E9F21");
        apple.put(
            "0102030405060708090A0B0C0D0E0F1011",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389CE964C8964215B27A7AE48DC056732F0");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C3896D936723C7A5816CC024E08603527959");
        apple.put(
            "0102030405060708090A0B0C0D0E0F10111213",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C3899CA4B3CC6E8D8C6D8FFD0AD70BA7BC65");
        apple.put(
            "0102030405060708090A0B0C0D0E0F1011121314",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389BEA4DE4B76525611799D65582BE3CE5B");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389DBC26994C71DCE7B068AF1A5B7202550");
        apple.put(
            "0102030405060708090A0B0C0D0E0F10111213141516",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C3891F8B3B76E9DDA4982B36456BDD40EF0E");
        apple.put(
            "0102030405060708090A0B0C0D0E0F1011121314151617",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C3892BC4022B166896A7CA9763E61EF458F1");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389D0D4F09B27A411EF48EA46185C9D6074");
        apple.put(
            "0102030405060708090A0B0C0D0E0F10111213141516171819",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C3897D1174ADCAA121457AC96C6C829962C8");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389E83AF24AC021FC94B4DA9606DB19D0D9");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C3898FEFA4682FFABAFB448EFE75DB321BB6");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C3899E3FBE0B68D8E19D40FBD9F081066C5E");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389F2D251D999ED1CABD6C76D74F8DE20BF");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389745AE302AD445421E2020E4C64698E41");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C3892189CE8E1F0F2B7D3343510EEBB885A0");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F20",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC522FBEF5D7912F605D4FC2D1F6FD0D2637");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F2021",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5285714982C410B3BF37C3489B56FAE2FB");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5226BDBA1215FF3C3B4B34CA9A85BDCFD0");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F20212223",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5208978E6B0FC4444DED918DB73764CFED");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F2021222324",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC52FFAC17521AA380BB9E7BAB2462C36610");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5218639B13B3DBAB9F5E92F2F6CC4F7481");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F20212223242526",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC522C4052C742B73009972E6BE6AE6753BD");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F2021222324252627",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC52F4932F9E79A2EFB14F94A3343EC91F12");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC52A96F28E8526F4C544431407083DA3E0C");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F20212223242526272829",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5232DF3FD5AD555A870610EFBE89793E93");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC520217EB61E2F102A27423DCF79B93B2B2");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC52A5DD3DA740A00F9083D1BEDADB572A2E");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5226A9DD99883A6A14CB758CEBD8C84E17");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC522E33DADC6A8B7D1BA2E8BE4BB6D683CF");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC527B1B18076B27418D6481AF3D8C0184BB");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC52C8CD727FBC61D7118E6994CD38FA207C");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F30",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D4FEADD56E96F84810035B7C7E47DEAAD8");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F3031",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D468AFE6CBF19B74BDE904BEC71C389E48");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F303132",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D482C21C40198174A4F284924DFED97B17");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F30313233",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D4781E4D3416BB1E63A1133FFD286BB908");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F3031323334",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D4923EE3CE4AFCA7DDCAF8D2C450067BFE");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F303132333435",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D4631E7238AF8181C5CE14EA55BCA51C72");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F30313233343536",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D4D17E8CD90B71309F8479DDD6053214DC");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F3031323334353637",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D468916A7D602693C416C2AB97DB3F6EDB");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F303132333435363738",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D41EA7274945614C3D4EB7850B495D3C61");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F30313233343536373839",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D4ED2DA843786063244F338B5C247A5FE2");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F303132333435363738393A",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D45A36064BA7C95C958F8437FA411FD34B");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F303132333435363738393A3B",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D43CA3FA4F14CAE59248D7E95AF4C96AF3");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F303132333435363738393A3B3C",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D4194011ACA9B17F0FBA3BAEB426A96B2C");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F303132333435363738393A3B3C3D",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D4637E86EA90E9027CE52AB54F95CA4DB2");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F303132333435363738393A3B3C3D3E",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D45DA5A687079A7374893816F9C83174D9");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F303132333435363738393A3B3C3D3E3F",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D429B7AF5929FF893290B55FAAF74F8C7D");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F303132333435363738393A3B3C3D3E3F40",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D4FECF4BE2FF20FFA4685CF25AA46C073DE5D5838F20613E79680D6E4380FE0A29");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F303132333435363738393A3B3C3D3E3F4041",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D4FECF4BE2FF20FFA4685CF25AA46C073DBF82C25ABBEE49629EE392B19F2B7EAB");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F303132333435363738393A3B3C3D3E3F404142",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D4FECF4BE2FF20FFA4685CF25AA46C073D020F3877B543C7A88489B985B1BA025D");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F303132333435363738393A3B3C3D3E3F40414243",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D4FECF4BE2FF20FFA4685CF25AA46C073D4309EF51D3C757D9FFAD71B00CD4BA8B");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F303132333435363738393A3B3C3D3E3F4041424344",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D4FECF4BE2FF20FFA4685CF25AA46C073D0AAE386BD16405B7EC84DD0A484A140E");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F303132333435363738393A3B3C3D3E3F404142434445",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D4FECF4BE2FF20FFA4685CF25AA46C073D2A085661E6EECDAD932CA3C709C4DD58");
        apple.put(
            "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2C2D2E2F303132333435363738393A3B3C3D3E3F40414243444546",
            "100F0E0D0C0B0A090807060504030201DD85C6780D7CBDFDAF8F5CE92EC1C389AD84FAD57E8FAA692A75313E76FDEC5298511F43949DD4C7C6D4E4C767BE26D4FECF4BE2FF20FFA4685CF25AA46C073D341FFEFA178945C75BA2144F828F482B");

        final byte[] key = hexStringToByteArray(appleKey);
        final byte[] iv = hexStringToByteArray(appleIv);

        final CipherParams params = Crypto.getParams("aes", key, iv);
        boolean failed = false;
        for (final Entry<String, String> entry : apple.entrySet()) {
            // We have to create a new ChannelCipher for each message we encode because
            // cipher instances only use the IV we've supplied via CipherParams for the
            // encryption of the very first message.
            final EncryptingChannelCipher encipher = Crypto.createChannelEncipher(params);
            final DecryptingChannelCipher decipher = Crypto.createChannelDecipher(params);

            final byte[] appleMessage = hexStringToByteArray(entry.getKey());
            final byte[] appleEncrypted = hexStringToByteArray(entry.getValue());
            final byte[] encrypted = encipher.encrypt(appleMessage);
            final byte[] decrypted = decipher.decrypt(appleEncrypted);

            try {
                assertArrayEquals(appleMessage, decrypted);
                System.out.println("Decryption Success for length " + appleMessage.length + ".");
            } catch (final AssertionError e) {
                failed = true;
                System.out.println("Decryption BAD for length " + appleMessage.length + ":"
                    + "\n\texpected: " + byteArrayToHexString(appleMessage)
                    + "\n\tproduced: " + byteArrayToHexString(decrypted));
            }

            try {
                assertArrayEquals(appleEncrypted, encrypted);
                System.out.println("Encryption Success for length " + appleMessage.length + ".");
            } catch (final AssertionError e) {
                failed = true;
                System.out.println("Encryption BAD for length " + appleMessage.length + ":"
                    + "\n\texpected: " + byteArrayToHexString(appleEncrypted)
                    + "\n\tproduced: " + byteArrayToHexString(encrypted));
            }

            System.out.println();
        }

        assertFalse("At least one decryption or encryption operation failed. See output for details.", failed);
    }

    private static final Random RANDOM = new Random();

    private static byte[] generateNonce(final int size) {
        final byte[] nonce = new byte[size];
        RANDOM.nextBytes(nonce);
        return nonce;
    }

    /**
     * Test Crypto.generateRandomKey.
     * @see <a href="https://docs.ably.com/client-lib-development-guide/features/#RSE2">RSE2</a>
     */
    @Test
    public void generate_random_key() {
        final int numberOfRandomKeys = 50;
        final int randomKeyBits = 256;
        byte[][] randomKeys = new byte[numberOfRandomKeys][];

        for (int i=0; i<numberOfRandomKeys; i++) {
            randomKeys[i] = Crypto.generateRandomKey(randomKeyBits);
            assertEquals(
                "Random key doesn't have the correct length",
                randomKeys[i].length, (randomKeyBits + 7) / 8
            );
            for (int j=0; j<i; j++) {
                assertFalse(
                    "Found duplicates among randomly generated keys",
                    Arrays.equals(randomKeys[i], randomKeys[j])
                );
            }
        }
    }
}
