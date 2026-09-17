package io.ably.pubsub.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.junit.Ignore;
import org.junit.Test;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;

import com.google.gson.stream.JsonWriter;

import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.util.Crypto.CipherParams;
import io.ably.pubsub.util.Crypto.EncryptingChannelCipher;
import io.ably.pubsub.util.CryptoMessageTest.FixtureSet;

public class CryptoTest {
    /**
     * Test Crypto.getDefaultParams.
     * @see <a href="https://docs.ably.com/client-lib-development-guide/features/#RSE1">RSE1</a>
     */
    @Test
    public void cipher_params() throws AblyException, NoSuchAlgorithmException {
        /* 128-bit key */
        /* {0xFF, 0xFE, 0xFD, 0xFC, 0xFB, 0xFA, 0xF9, 0xF8, 0xF7, 0xF6, 0xF5, 0xF4, 0xF3, 0xF2, 0xF1, 0xF0}; */
        byte[] key = {-1, -2, -3, -4, -5, -6, -7, -8, -9, -10, -11, -12, -13, -14, -15, -16};
        /* Same key but encoded with Base64 */
        String base64key = "//79/Pv6+fj39vX08/Lx8A==";
        /* Same key but encoded in URL style (RFC 4648 s.5) */
        String base64key2 = "__79_Pv6-fj39vX08_Lx8A==";

        /* IV */
        byte[] iv = {16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};

        final CipherParams params1 = Crypto.getDefaultParams(key, iv);
        final CipherParams params2 = Crypto.getDefaultParams(base64key, iv);
        final CipherParams params3 = new CipherParams(null, key, iv);
        final CipherParams params4 = Crypto.getDefaultParams(base64key2, iv);

        assertEquals("aes", params1.getAlgorithm());

        assertTrue(
            "Key length is incorrect",
            params1.getKeyLength() == key.length*8 &&
            params2.getKeyLength() == key.length*8 &&
            params3.getKeyLength() == key.length*8 &&
            params4.getKeyLength() == key.length*8
        );

        byte[] plaintext = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        EncryptingChannelCipher channelCipher1 = Crypto.createChannelEncipher(params1);
        EncryptingChannelCipher channelCipher2 = Crypto.createChannelEncipher(params2);
        EncryptingChannelCipher channelCipher3 = Crypto.createChannelEncipher(params3);
        EncryptingChannelCipher channelCipher4 = Crypto.createChannelEncipher(params4);

        byte[] ciphertext1 = channelCipher1.encrypt(plaintext);
        byte[] ciphertext2 = channelCipher2.encrypt(plaintext);
        byte[] ciphertext3 = channelCipher3.encrypt(plaintext);
        byte[] ciphertext4 = channelCipher4.encrypt(plaintext);

        assertTrue(
            "All ciphertexts should be the same.",
            Arrays.equals(ciphertext1, ciphertext2) &&
            Arrays.equals(ciphertext1, ciphertext3) &&
            Arrays.equals(ciphertext1, ciphertext4)
        );
    }

    /**
     * Test encryption using a 256 bit key and varying lengths of data.
     *
     * The key, IV and message data are the same for every test run so that the
     * encrypted data may be exported from the console output for consumption by tests
     * run on other platforms. This output is manually merged into the file
     * test-resources/crypto-data-256.json in ably-common.
     *
     * Equivalent to the following in ably-cocoa:
     * testEncryptAndDecrypt in Spec/CryptoTest.m
     * @throws IOException
     */
    @Ignore("FIXME: NullPointerException should be fixed")
    @Test
    public void encryptAndDecrypt() throws NoSuchAlgorithmException, AblyException, IOException {
        final FixtureSet fixtureSet = FixtureSet.AES256;
        final CipherParams params = Crypto.getDefaultParams(fixtureSet.key, fixtureSet.iv);

        // Prepare message data.
        final int maxLength = 70;
        final byte[] message = new byte[maxLength];
        for (byte value=1; value<=maxLength; value++) {
            message[value - 1] = value;
        }

        final StringWriter target = new StringWriter();
        final JsonWriter writer = new JsonWriter(target);
        writer.setIndent("  ");
        writer.beginObject();

        writer.name("algorithm");
        writer.value("aes");

        writer.name("mode");
        writer.value("cbc");

        writer.name("keyLength");
        writer.value(256);

        writer.name("key");
        writer.value(Base64Coder.encodeToString(fixtureSet.key));

        writer.name("iv");
        writer.value(Base64Coder.encodeToString(fixtureSet.iv));

        // Perform encrypt and decrypt on message data trimmed at all lengths up
        // to and including maxLength.
        writer.name("items");
        writer.beginArray();
        for (int i=1; i<=maxLength; i++) {
            // We need to create a new ChannelCipher for each message we encode,
            // so that our IV gets used (being start of CBC chain).
            final EncryptingChannelCipher encipher = Crypto.createChannelEncipher(params);
            final Crypto.DecryptingChannelCipher decipher = Crypto.createChannelDecipher(params);

            // Encrypt i bytes from the start of the message data.
            final byte[] encoded = Arrays.copyOfRange(message, 0, i);
            final byte[] encrypted = encipher.encrypt(encoded);

            // Add encryption result to results in format ready for fixture.
            writeResult(writer, "byte 1 to " + i, encoded, encrypted, fixtureSet.cipherName);

            // Decrypt the encrypted data and verify the result is the same as what
            // we submitted for encryption.
            final byte[] verify = decipher.decrypt(encrypted);
            assertArrayEquals(verify, encoded);
        }
        writer.endArray();

        writer.endObject();

        System.out.println("Fixture JSON for test-resources:\n" + target.toString());
    }

    private static void writeResult(final JsonWriter writer, final String name, final byte[] encoded, final byte[] encrypted, final String cipherName) throws IOException {
        writer.beginObject();

        writer.name("encoded");
        writeData(writer, name, encoded, null);

        writer.name("encrypted");
        writeData(writer, name, encrypted, cipherName);

        writer.name("msgpack");
        writer.value(Base64Coder.encodeToString(msgPacked(name, encrypted, cipherName)));

        writer.endObject();
    }

    private static final String BASE64 = "base64";
    private static void writeData(final JsonWriter writer, final String name, final byte[] data, final String encoding) throws IOException {
        writer.beginObject();

        writer.name("name");
        writer.value(name);

        writer.name("data");
        writer.value(Base64Coder.encodeToString(data));

        writer.name("encoding");
        writer.value(null == encoding ? BASE64 : encoding + "/" + BASE64);

        writer.endObject();
    }

    private static byte[] msgPacked(final String name, final byte[] data, final String encoding) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final MessagePacker packer = MessagePack.DEFAULT_PACKER_CONFIG.newPacker(out);

        packer.packMapHeader(3);

        packer.packString("name");
        packer.packString(name);

        packer.packString("data");
        packer.packBinaryHeader(data.length);
        packer.writePayload(data);

        packer.packString("encoding");
        packer.packString(encoding);

        packer.close();

        return out.toByteArray();
    }

    @Test
    public void getRandomId() {
        String randomId = Crypto.getRandomId();
        assertEquals(12, randomId.length());
    }
}
