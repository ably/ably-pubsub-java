package io.ably.pubsub.liveobjects.message;

/**
 * Payload of a {@link ObjectOperationAction#MAP_CLEAR} operation. This type
 * deliberately has no attributes (MCL2) - the
 * {@link ObjectOperation#getAction() action} and
 * {@link ObjectOperation#getObjectId() objectId} are sufficient to describe the clear.
 *
 * <p>Spec: MCL1, MCL2
 */
public interface MapClear {
}
