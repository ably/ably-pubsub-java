package io.ably.pubsub.liveobjects.message;

import org.jetbrains.annotations.NotNull;

/**
 * Payload of a {@link ObjectOperationAction#MAP_REMOVE} operation, describing a key
 * being removed from a {@code LiveMap} object.
 *
 * <p>Spec: MRM*
 */
public interface MapRemove {

    /**
     * Returns the key being removed.
     *
     * <p>Spec: MRM2a
     *
     * @return the map key
     */
    @NotNull String getKey();
}
