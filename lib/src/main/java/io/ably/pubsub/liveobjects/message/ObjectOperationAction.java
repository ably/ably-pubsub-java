package io.ably.pubsub.liveobjects.message;

/**
 * The action of an {@link ObjectOperation}, defining the type of operation that was
 * applied to an object on a channel.
 *
 * <p>Spec: OOP2 / PAOOP2a
 */
public enum ObjectOperationAction {

    /** Creates a new {@code LiveMap} object. Spec: OOP2 */
    MAP_CREATE,

    /** Sets the value at a key of a {@code LiveMap} object. Spec: OOP2 */
    MAP_SET,

    /** Removes a key from a {@code LiveMap} object. Spec: OOP2 */
    MAP_REMOVE,

    /** Creates a new {@code LiveCounter} object. Spec: OOP2 */
    COUNTER_CREATE,

    /** Increments the value of a {@code LiveCounter} object. Spec: OOP2 */
    COUNTER_INC,

    /** Deletes (tombstones) an object. Spec: OOP2 */
    OBJECT_DELETE,

    /** Removes all entries from a {@code LiveMap} object. Spec: OOP2 */
    MAP_CLEAR,

    /**
     * Future-compatibility fallback for an action not recognized by this version of
     * the client library.
     */
    UNKNOWN,
}
