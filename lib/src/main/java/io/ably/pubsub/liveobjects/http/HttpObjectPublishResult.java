package io.ably.pubsub.liveobjects.http;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The result of {@link io.ably.pubsub.liveobjects.HttpObject#publish}.
 *
 * <p>Spec: RSO10
 */
public interface HttpObjectPublishResult {

    /** @return the ID of the message the operations were published in (RSO10a). */
    @NotNull String getMessageId();

    /** @return the channel the operations were published on (RSO10b). */
    @NotNull String getChannel();

    /**
     * @return the IDs of the objects the operations affected, including objects the server created
     * for {@code mapCreate} and {@code counterCreate} operations (RSO10c).
     */
    @NotNull List<String> getObjectIds();
}
