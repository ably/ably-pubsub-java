package io.ably.pubsub.liveobjects.message;

import org.jetbrains.annotations.NotNull;

/**
 * Payload of a {@link ObjectOperationAction#MAP_SET} operation, describing a key being
 * set on a {@code LiveMap} object.
 *
 * <p>Spec: MST*
 */
public interface MapSet {

    /**
     * Returns the key being set.
     *
     * <p>Spec: MST2a
     *
     * @return the map key
     */
    @NotNull String getKey();

    /**
     * Returns the value the key is being set to.
     *
     * <p>Spec: MST2b
     *
     * @return the value being set
     */
    @NotNull ObjectData getValue();
}
