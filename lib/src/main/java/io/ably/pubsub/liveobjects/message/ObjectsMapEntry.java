package io.ably.pubsub.liveobjects.message;

import org.jetbrains.annotations.Nullable;

/**
 * Represents the value at a given key in a {@code LiveMap} object.
 *
 * <p>Spec: OME1
 */
public interface ObjectsMapEntry {

    /**
     * Indicates whether the map entry has been removed.
     *
     * <p>Spec: OME2a
     *
     * @return {@code true} if the entry is tombstoned, or {@code null} if unavailable
     */
    @Nullable Boolean getTombstone();

    /**
     * Returns the serial value of the latest operation that was applied to the map
     * entry.
     *
     * <p>Spec: OME2b
     *
     * @return the entry timeserial, or {@code null} if unavailable
     */
    @Nullable String getTimeserial();

    /**
     * Returns the timestamp derived from the {@link #getTimeserial() timeserial} of
     * this entry, as milliseconds since the epoch. Only present if
     * {@link #getTombstone()} is {@code true}.
     *
     * <p>Spec: OME2d
     *
     * @return the serial timestamp in milliseconds since the epoch, or {@code null} if
     *         unavailable
     */
    @Nullable Long getSerialTimestamp();

    /**
     * Returns the data that represents the value of the map entry.
     *
     * <p>Spec: OME2c
     *
     * @return the entry value, or {@code null} if unavailable
     */
    @Nullable ObjectData getData();
}
