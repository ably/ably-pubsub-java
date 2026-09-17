package io.ably.pubsub.liveobjects.message;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;

/**
 * Payload of a {@link ObjectOperationAction#MAP_CREATE} operation, describing the
 * initial state of the created {@code LiveMap} object.
 *
 * <p>Spec: MCR*
 */
public interface MapCreate {

    /**
     * Returns the conflict-resolution semantics used by the created map object.
     *
     * <p>Spec: MCR2a
     *
     * @return the map semantics
     */
    @NotNull ObjectsMapSemantics getSemantics();

    /**
     * Returns the initial entries of the created map object, indexed by key.
     *
     * <p>Spec: MCR2b
     *
     * @return an unmodifiable map of initial entries
     */
    @NotNull @Unmodifiable Map<String, ObjectsMapEntry> getEntries();
}
