package io.ably.pubsub.liveobjects.message;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The user-facing representation of an operation applied to an object on a channel. It
 * is exposed as the {@link ObjectMessage#getOperation() operation} attribute of an
 * {@link ObjectMessage}.
 *
 * <p>Exactly one of the payload accessors ({@link #getMapCreate()},
 * {@link #getMapSet()}, {@link #getMapRemove()}, {@link #getCounterCreate()},
 * {@link #getCounterInc()}, {@link #getObjectDelete()}, {@link #getMapClear()}) returns
 * a non-null value, corresponding to the {@link #getAction() action} of the operation.
 *
 * <p>Note that, unlike the wire-level operation representation, this type does not carry
 * the outbound-only {@code mapCreateWithObjectId} / {@code counterCreateWithObjectId}
 * variants: those are resolved back to their derived {@link MapCreate} /
 * {@link CounterCreate} forms before being surfaced to users.
 *
 * <p>Spec: PAOOP1, PAOOP2
 */
public interface ObjectOperation {

    /**
     * Returns the action of this operation, defining what was applied to the object.
     *
     * <p>Spec: PAOOP2a / OOP3a
     *
     * @return the operation action
     */
    @NotNull ObjectOperationAction getAction();

    /**
     * Returns the object id of the object on the channel to which this operation was
     * applied.
     *
     * <p>Spec: PAOOP2b / OOP3b
     *
     * @return the target object id
     */
    @NotNull String getObjectId();

    /**
     * Returns the payload of a {@link ObjectOperationAction#MAP_CREATE} operation.
     *
     * <p>Spec: PAOOP2c / OOP3j
     *
     * @return the map-create payload, or {@code null} if not applicable
     */
    @Nullable MapCreate getMapCreate();

    /**
     * Returns the payload of a {@link ObjectOperationAction#MAP_SET} operation.
     *
     * <p>Spec: PAOOP2d / OOP3k
     *
     * @return the map-set payload, or {@code null} if not applicable
     */
    @Nullable MapSet getMapSet();

    /**
     * Returns the payload of a {@link ObjectOperationAction#MAP_REMOVE} operation.
     *
     * <p>Spec: PAOOP2e / OOP3l
     *
     * @return the map-remove payload, or {@code null} if not applicable
     */
    @Nullable MapRemove getMapRemove();

    /**
     * Returns the payload of a {@link ObjectOperationAction#COUNTER_CREATE} operation.
     *
     * <p>Spec: PAOOP2f / OOP3m
     *
     * @return the counter-create payload, or {@code null} if not applicable
     */
    @Nullable CounterCreate getCounterCreate();

    /**
     * Returns the payload of a {@link ObjectOperationAction#COUNTER_INC} operation.
     *
     * <p>Spec: PAOOP2g / OOP3n
     *
     * @return the counter-increment payload, or {@code null} if not applicable
     */
    @Nullable CounterInc getCounterInc();

    /**
     * Returns the payload of an {@link ObjectOperationAction#OBJECT_DELETE} operation.
     *
     * <p>Spec: PAOOP2h / OOP3o
     *
     * @return the object-delete payload, or {@code null} if not applicable
     */
    @Nullable ObjectDelete getObjectDelete();

    /**
     * Returns the payload of a {@link ObjectOperationAction#MAP_CLEAR} operation.
     *
     * <p>Spec: PAOOP2i / OOP3r
     *
     * @return the map-clear payload, or {@code null} if not applicable
     */
    @Nullable MapClear getMapClear();
}
