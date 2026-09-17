package io.ably.pubsub.liveobjects.message;

/**
 * The conflict-resolution semantics used by a {@code LiveMap} object.
 *
 * <p>Spec: OMP2
 */
public enum ObjectsMapSemantics {

    /** Last-write-wins conflict resolution. Spec: OMP2a */
    LWW,

    /**
     * Future-compatibility fallback for semantics not known to this version of the
     * client library.
     */
    UNKNOWN,
}
