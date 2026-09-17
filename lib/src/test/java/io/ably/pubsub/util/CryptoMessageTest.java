package io.ably.pubsub.util;

import static io.ably.pubsub.test.common.Helpers.assertMessagesEqual;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import io.ably.pubsub.test.util.AblyCommonsReader;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ChannelOptions;
import io.ably.pubsub.types.Message;
import io.ably.pubsub.util.Crypto.CipherParams;

@Ignore("FIXME: Initialization is failing")
@RunWith(Parameterized.class)
public class CryptoMessageTest {
    public enum FixtureSet {
        AES128(16),
        AES256(32);

        public final byte[] key;
        public final byte[] iv;
        private final String fileName;
        public final String cipherName;

        FixtureSet(final int keySize) {
            if (keySize < 1) {
                throw new IllegalArgumentException("keySize");
            }

            final int keyLength = keySize * 8; // bytes to bits
            fileName = "crypto-data-" + keyLength;
            cipherName = "cipher+aes-" + keyLength + "-cbc";

            CryptoTestData testData;
            try {
                testData = loadTestData();
            } catch (IOException e) {
                throw new Error(e); // caught to uncaught
            }
            key = Base64Coder.decode(testData.key);
            iv = Base64Coder.decode(testData.iv);

            if (keySize != this.key.length) {
                throw new IllegalArgumentException("key");
            }
            if (16 != this.iv.length) {
                throw new IllegalArgumentException("iv");
            }
        }

        private CryptoTestData loadTestData() throws IOException {
            return (CryptoTestData) AblyCommonsReader.read(
                "test-resources/" + fileName + ".json",
                CryptoTestData.class);
        }
    }

    @Parameters(name= "{0}")
    public static Object[][] data() {
        return new Object[][] {
            { FixtureSet.AES128 },
            { FixtureSet.AES256 },
        };
    }

    private final FixtureSet fixtureSet;

    public CryptoMessageTest(final FixtureSet fixtureSet) {
        this.fixtureSet = fixtureSet;
    }

    @Test
    public void testDecrypt() throws NoSuchAlgorithmException, CloneNotSupportedException, AblyException, IOException {
        final CryptoTestData testData = fixtureSet.loadTestData();
        final String algorithm = testData.algorithm;

        final CipherParams params = Crypto.getParams(algorithm, fixtureSet.key, fixtureSet.iv);
        final ChannelOptions options = new ChannelOptions();
        options.encrypted = true;
        options.cipherParams = params;

        for(final CryptoTestItem item : testData.items) {
            final Message plain = item.encoded;
            final Message encrypted = item.encrypted;
            assertThat(encrypted.encoding, endsWith(fixtureSet.cipherName + "/base64"));

            // if necessary, remove base64 encoding from plain-'text' message
            plain.decode(null);
            assertEquals(null, plain.encoding);

            // perform the decryption (via decode) which is the thing we need to test
            encrypted.decode(options);
            assertEquals(null, encrypted.encoding);

            // compare the expected plain-'text' bytes with those decrypted above
            assertMessagesEqual(plain, encrypted);
        }
    }

    @Test
    public void testEncrypt() throws NoSuchAlgorithmException, CloneNotSupportedException, AblyException, IOException {
        final CryptoTestData testData = fixtureSet.loadTestData();
        final String algorithm = testData.algorithm;

        final CipherParams params = Crypto.getParams(algorithm, fixtureSet.key, fixtureSet.iv);

        for(final CryptoTestItem item : testData.items) {
            final ChannelOptions options = new ChannelOptions();
            options.encrypted = true;
            options.cipherParams = params;

            final Message plain = item.encoded;
            final Message encrypted = item.encrypted;
            assertThat(encrypted.encoding, endsWith(fixtureSet.cipherName + "/base64"));

            // if necessary, remove base64 encoding from plain-'text' message
            plain.decode(null);
            assertEquals(null, plain.encoding);

            // perform the encryption (via encode) which is the thing we need to test
            plain.encode(options);
            assertThat(plain.encoding, endsWith(fixtureSet.cipherName));

            // our test fixture always provides a string for the encrypted data, which means
            // that it's base64 encoded - so we need to base64 decode it to get the encrypted bytes
            final byte[] expected = Base64Coder.decode((String)encrypted.data);

            // compare the expected encrypted bytes with those encrypted above
            final byte[] actual = (byte[])plain.data;
            assertArrayEquals(expected, actual);
        }
    }

    static class CryptoTestData {
        public String algorithm;
        public String mode;
        public int keylength;
        public String key;
        public String iv;
        public CryptoTestItem[] items;
    }

    static class CryptoTestItem {
        public Message encoded;
        public Message encrypted;
    }
}
