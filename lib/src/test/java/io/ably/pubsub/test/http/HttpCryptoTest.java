package io.ably.pubsub.test.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import javax.crypto.KeyGenerator;

import io.ably.pubsub.test.common.Helpers;
import org.junit.Before;
import org.junit.Test;

import io.ably.pubsub.http.HttpClientFactory;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.http.Channel;
import io.ably.pubsub.test.common.ParameterizedTest;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ChannelOptions;
import io.ably.pubsub.types.ClientOptions;
import io.ably.pubsub.types.Message;
import io.ably.pubsub.types.PaginatedResult;
import io.ably.pubsub.types.Param;
import io.ably.pubsub.util.Crypto;
import io.ably.pubsub.util.Crypto.CipherParams;

public class HttpCryptoTest extends ParameterizedTest {

    private static final String TAG = HttpCryptoTest.class.getName();
    private PubSubHttpClient ably;
    private PubSubHttpClient ably_alt;

    @Before
    public void setUpBefore() throws Exception {
        final ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        ably = HttpClientFactory.create(opts);
        final ClientOptions opts_alt = createOptions(testVars.keys[0].keyStr);
        opts_alt.useBinaryProtocol = testParams.useBinaryProtocol;
        ably_alt = HttpClientFactory.create(opts_alt);
    }

    /**
     * Publish events with data of various datatypes using text protocol
     */
    @Test
    public void crypto_publish() throws AblyException {
        /* first, publish some messages */
        final ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
        final Channel publish0 = ably.channels.get("persisted:crypto_publish_" + testParams.name, channelOpts);

        final Helpers.CompletionWaiter publishWaiter = new Helpers.CompletionWaiter();
        publish0.publishAsync("publish0", "This is a string message payload", publishWaiter);
        publish0.publishAsync("publish1", "This is a byte[] message payload".getBytes(), publishWaiter);
        assertNull(publishWaiter.waitFor(2));

        // TODO find a way to know that the history call below will have data available already
        // (i.e. that data has made it to the REST endpoint ... we know that we've waitied for our publish requests
        // to succeed, but that doesn't necessarily mean the data is yet available to all clients)

        /* get the history for this channel */
        final PaginatedResult<Message> messages = publish0.history(null);
        assertNotNull(messages);
        assertEquals(2, messages.items().length);
        final HashMap<String, Object> messageContents = new HashMap<String, Object>();
        /* verify message contents */
        for (final Message message : messages.items())
            messageContents.put(message.name, message.data);
        final Object payload0 = messageContents.get("publish0");
        final Object payload1 = messageContents.get("publish1");
        assertTrue("Unexpected " + payload0.getClass(), payload0 instanceof String);
        assertTrue("Unexpected " + payload1.getClass(), payload1 instanceof byte[]);
        assertEquals("This is a string message payload", payload0);
        assertEquals("This is a byte[] message payload", new String((byte[])payload1));
    }

    /**
     * Publish events with data of various datatypes using text protocol with a 256-bit key
     */
    @Test
    public void crypto_publish_256() throws NoSuchAlgorithmException, AblyException {
        /* first, publish some messages */
        /* create a key */
        final KeyGenerator keygen = KeyGenerator.getInstance("AES");
        keygen.init(256);
        byte[] key = keygen.generateKey().getEncoded();
        final CipherParams params = Crypto.getDefaultParams(key);

        /* create a channel */
        final ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; this.cipherParams = params; }};
        final Channel publish0 = ably.channels.get("persisted:crypto_publish_256_" + testParams.name, channelOpts);

        final Helpers.CompletionWaiter publishWaiter = new Helpers.CompletionWaiter();
        publish0.publishAsync("publish0", "This is a string message payload", publishWaiter);
        publish0.publishAsync("publish1", "This is a byte[] message payload".getBytes(), publishWaiter);
        assertNull(publishWaiter.waitFor(2));

        // TODO find a way to know that the history call below will have data available already
        // (i.e. that data has made it to the REST endpoint ... we know that we've waitied for our publish requests
        // to succeed, but that doesn't necessarily mean the data is yet available to all clients)

        /* get the history for this channel */
        final PaginatedResult<Message> messages = publish0.history(null);
        assertNotNull(messages);
        assertEquals(2, messages.items().length);
        final HashMap<String, Object> messageContents = new HashMap<String, Object>();
        /* verify message contents */
        for (final Message message : messages.items())
            messageContents.put(message.name, message.data);
        assertEquals("This is a string message payload", messageContents.get("publish0"));
        assertEquals("This is a byte[] message payload", new String((byte[])messageContents.get("publish1")));
    }

    /**
     * Connect twice to the service, using the default (binary) protocol
     * and the text protocol. Publish an encrypted message on that channel using
     * the default cipher params and verify correct receipt.
     */
    @Test
    public void crypto_publish_alt() throws AblyException {
        /* first, publish some messages */
        final String channelName = "persisted:crypto_publish_alt_" + testParams.name;

        /* create a key */
        final CipherParams params = Crypto.getDefaultParams();

        /* create a channel */
        final ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; cipherParams = params; }};
        final Channel tx_publish = ably.channels.get(channelName, channelOpts);

        final Helpers.CompletionWaiter publishWaiter = new Helpers.CompletionWaiter();
        tx_publish.publishAsync("publish0", "This is a string message payload", publishWaiter);
        tx_publish.publishAsync("publish1", "This is a byte[] message payload".getBytes(), publishWaiter);
        assertNull(publishWaiter.waitFor(2));

        // TODO find a way to know that the history call below will have data available already
        // (i.e. that data has made it to the REST endpoint ... we know that we've waitied for our publish requests
        // to succeed, but that doesn't necessarily mean the data is yet available to all clients)

        /* get the history for this channel */
        final Channel rx_publish = ably_alt.channels.get(channelName, channelOpts);
        final PaginatedResult<Message> messages = rx_publish.history(null);
        assertNotNull(messages);
        assertEquals(2, messages.items().length);
        final HashMap<String, Object> messageContents = new HashMap<String, Object>();
        /* verify message contents */
        for (final Message message : messages.items())
            messageContents.put(message.name, message.data);
        assertEquals("This is a string message payload", messageContents.get("publish0"));
        assertEquals("This is a byte[] message payload", new String((byte[])messageContents.get("publish1")));
    }

    /**
     * Connect twice to the service, using different cipher keys.
     * Publish an encrypted message on that channel using
     * the default cipher params and verify that the decrypt failure
     * is noticed as bad recovered plaintext.
     */
    @Test
    public void crypto_publish_key_mismatch() throws AblyException {
        /* first, publish some messages */
        final String channelName = "persisted:crypto_publish_key_mismatch_" + testParams.name;

        /* create a channel */
        final ChannelOptions tx_channelOpts = new ChannelOptions() {{ encrypted = true; }};
        final Channel tx_publish = ably.channels.get(channelName, tx_channelOpts);

        final Helpers.CompletionWaiter publishWaiter = new Helpers.CompletionWaiter();
        tx_publish.publishAsync("publish0", "This is a string message payload", publishWaiter);
        tx_publish.publishAsync("publish1", "This is a byte[] message payload".getBytes(), publishWaiter);
        assertNull(publishWaiter.waitFor(2));

        // TODO find a way to know that the history call below will have data available already
        // (i.e. that data has made it to the REST endpoint ... we know that we've waitied for our publish requests
        // to succeed, but that doesn't necessarily mean the data is yet available to all clients)

        /* get the history for this channel */
        final ChannelOptions rx_channelOpts = new ChannelOptions() {{ encrypted = true; }};
        final Channel rx_publish = ably.channels.get(channelName, rx_channelOpts);

        final PaginatedResult<Message> messages = rx_publish.history(new Param[] { new Param("direction", "backwards"), new Param("limit", "2") });
        for (final Message failedMessage: messages.items())
            assertTrue(failedMessage.encoding.contains("cipher"));
    }

    /**
     * Connect twice to the service, one with and one without encryption.
     * Publish an unencrypted message and verify that the receiving connection
     * does not attempt to decrypt it.
     */
    @Test
    public void crypto_send_unencrypted() throws AblyException {
        final String channelName = "persisted:crypto_send_unencrypted_" + testParams.name;
        /* first, publish some messages */

        /* create a channel */
        final Channel tx_publish = ably.channels.get(channelName);

        final Helpers.CompletionWaiter publishWaiter = new Helpers.CompletionWaiter();
        tx_publish.publishAsync("publish0", "This is a string message payload", publishWaiter);
        tx_publish.publishAsync("publish1", "This is a byte[] message payload".getBytes(), publishWaiter);
        assertNull(publishWaiter.waitFor(2));

        // TODO find a way to know that the history call below will have data available already
        // (i.e. that data has made it to the REST endpoint ... we know that we've waitied for our publish requests
        // to succeed, but that doesn't necessarily mean the data is yet available to all clients)

        /* get the history for this channel */
        final ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
        final Channel rx_publish = ably.channels.get(channelName, channelOpts);
        final PaginatedResult<Message> messages = rx_publish.history(null);
        assertNotNull(messages);
        assertEquals(2, messages.items().length);
        final HashMap<String, Object> messageContents = new HashMap<String, Object>();

        /* verify message contents */
        for (final Message message : messages.items())
            messageContents.put(message.name, message.data);
        assertEquals("This is a string message payload", messageContents.get("publish0"));
        assertEquals("This is a byte[] message payload", new String((byte[])messageContents.get("publish1")));
    }

    /**
     * Connect twice to the service, one with and one without encryption.
     * Publish an encrypted message and verify that the receiving connection
     * is unable to decrypt it and leaves it as encoded cipher data
     */
    @Test
    public void crypto_send_encrypted_unhandled() throws AblyException {
        final String channelName = "persisted:crypto_send_encrypted_unhandled_" + testParams.name;

        /* first, publish some messages */

        /* create a channel */
        final ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
        final Channel tx_publish = ably.channels.get(channelName, channelOpts);

        final Helpers.CompletionWaiter publishWaiter = new Helpers.CompletionWaiter();
        tx_publish.publishAsync("publish0", "This is a string message payload", publishWaiter);
        tx_publish.publishAsync("publish1", "This is a byte[] message payload".getBytes(), publishWaiter);
        assertNull(publishWaiter.waitFor(2));

        // TODO find a way to know that the history call below will have data available already
        // (i.e. that data has made it to the REST endpoint ... we know that we've waitied for our publish requests
        // to succeed, but that doesn't necessarily mean the data is yet available to all clients)

        /* get the history for this channel */
        final Channel rx_publish = ably_alt.channels.get(channelName);
        final PaginatedResult<Message> messages = rx_publish.history(null);
        assertNotNull(messages);
        assertEquals(2, messages.items().length);
        final HashMap<String, Message> messageContents = new HashMap<String, Message>();
        /* verify message contents */
        for (final Message message : messages.items())
            messageContents.put(message.name, message);
        assertTrue(messageContents.get("publish0").encoding.contains("cipher"));
        assertTrue(messageContents.get("publish1").encoding.contains("cipher"));
    }
}
