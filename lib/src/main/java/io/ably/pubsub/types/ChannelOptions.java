package io.ably.pubsub.types;

import java.util.Map;

import io.ably.pubsub.util.Base64Coder;
import io.ably.pubsub.util.Crypto;
import io.ably.pubsub.util.Crypto.CipherParams;

/**
 * Passes additional properties to a {@link io.ably.pubsub.http.Channel} or {@link io.ably.pubsub.realtime.Channel} object,
 * such as encryption, {@link ChannelMode} and channel parameters.
 */
public class ChannelOptions {
    /**
     * <a href="https://ably.com/docs/realtime/channels/channel-parameters/overview">Channel Parameters</a>
     * that configure the behavior of the channel.
     * <p>
     * Spec: TB2c
     */
    public Map<String, String> params;

    /**
     * An array of {@link ChannelMode} objects.
     * <p>
     * Spec: TB2d
     */
    public ChannelMode[] modes;

    /**
     * Requests encryption for this channel when not null,
     * and specifies encryption-related parameters (such as algorithm, chaining mode, key length and key).
     * See <a href="https://ably.com/docs/realtime/encryption#getting-started">an example</a>.
     * <p>
     * Spec: RSL5a, TB2b
     */
    public Object cipherParams;

    /**
     * Whether or not this ChannelOptions is encrypted.
     */
    public boolean encrypted;

    /**
     * <p>
     * Determines whether calling {@link io.ably.pubsub.realtime.Channel#subscribe Channel.subscribe} or
     * {@link io.ably.pubsub.realtime.Presence#subscribe Presence.subscribe} method
     * should trigger an implicit attach.
     * </p>
     * <p>Defaults to {@code true}.</p>
     * <p>Spec: TB4, RTL7g, RTL7h, RTP6d, RTP6e</p>
     */
    public boolean attachOnSubscribe = true;

    public boolean hasModes() {
        return null != modes && 0 != modes.length;
    }

    public boolean hasParams() {
        return null != params && !params.isEmpty();
    }

    public int getModeFlags() {
        int flags = 0;
        for (final ChannelMode mode : modes) {
            flags |= mode.getMask();
        }
        return flags;
    }

    /**
     * Constructor withCipherKey, that takes a key only.
     * <p>
     * Spec: TB3
     * @param key A private key used to encrypt and decrypt payloads.
     * @return A ChannelOptions object.
     * @throws AblyException If something goes wrong.
     */
    public static ChannelOptions withCipherKey(byte[] key) throws AblyException {
        ChannelOptions options = new ChannelOptions();
        options.encrypted = true;
        options.cipherParams = Crypto.getDefaultParams(key);
        return options;
    }

    /**
     * Constructor withCipherKey, that takes a key only.
     * <p>
     * Spec: TB3
     * @param base64Key A private key used to encrypt and decrypt payloads.
     * @return A ChannelOptions object.
     * @throws AblyException If something goes wrong.
     */
    public static ChannelOptions withCipherKey(String base64Key) throws AblyException {
        return withCipherKey(Base64Coder.decode(base64Key));
    }

    /**
     * Internal; returns cipher params or generate default
     */
    public synchronized CipherParams getCipherParamsOrDefault() throws AblyException {
        CipherParams params = Crypto.checkCipherParams(this.cipherParams);
        if (this.cipherParams == null) {
            this.cipherParams = params;
        }
        return params;
    }
}
