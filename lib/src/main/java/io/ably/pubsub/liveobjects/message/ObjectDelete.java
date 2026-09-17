package io.ably.pubsub.liveobjects.message;

/**
 * Payload of an {@link ObjectOperationAction#OBJECT_DELETE} operation. This type
 * deliberately has no attributes (ODE2) - the
 * {@link ObjectOperation#getAction() action} and
 * {@link ObjectOperation#getObjectId() objectId} are sufficient to describe the
 * deletion.
 *
 * <p>Spec: ODE1, ODE2
 */
public interface ObjectDelete {
}
