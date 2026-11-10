package io.ably.pubsub.liveobjects.http;

import org.jetbrains.annotations.NotNull;

/**
 * The result of {@link io.ably.pubsub.liveobjects.HttpObject#generateObjectId}. Pass it to
 * {@link HttpObjectOperation#mapCreateWithObjectId(HttpObjectGenerateIdResult)} or
 * {@link HttpObjectOperation#counterCreateWithObjectId(HttpObjectGenerateIdResult)} to create the
 * object; all three values must be sent unchanged.
 *
 * <p>Spec: RSO11
 */
public interface HttpObjectGenerateIdResult {

    /** @return the generated object ID (RSO11a). */
    @NotNull String getObjectId();

    /** @return the nonce the object ID was derived from (RSO11b). */
    @NotNull String getNonce();

    /** @return the JSON-encoded initial value the object ID was derived from (RSO11c). */
    @NotNull String getInitialValue();
}
