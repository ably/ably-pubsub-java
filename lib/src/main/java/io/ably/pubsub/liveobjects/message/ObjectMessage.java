package io.ably.pubsub.liveobjects.message;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The user-facing representation of an inbound object message that carried an operation.
 * It is delivered to subscription listeners (see
 * {@link io.ably.pubsub.liveobjects.path.PathObjectSubscriptionEvent} and
 * {@link io.ably.pubsub.liveobjects.instance.InstanceSubscriptionEvent}) so that user code can
 * inspect the metadata of the message that triggered an object change.
 *
 * <p>An {@code ObjectMessage} always carries an {@link #getOperation() operation}; object
 * messages without an operation (e.g. sync state messages) are never surfaced to users.
 *
 * <p>This type is the entry point of the {@code io.ably.pubsub.object.message} package;
 * all sibling types are reached by walking its properties:
 *
 * <pre>{@code
 * ObjectMessage
 * └── getOperation()  → ObjectOperation
 *     ├── getAction()        → ObjectOperationAction (enum)
 *     ├── getMapCreate()     → MapCreate → ObjectsMapSemantics, Map<String, ObjectsMapEntry> → ObjectData
 *     ├── getMapSet()        → MapSet → ObjectData
 *     ├── getMapRemove()     → MapRemove
 *     ├── getCounterCreate() → CounterCreate
 *     ├── getCounterInc()    → CounterInc
 *     ├── getObjectDelete()  → ObjectDelete (empty)
 *     └── getMapClear()      → MapClear (empty)
 * }</pre>
 *
 * <p>Spec: PAOM1, PAOM2
 */
public interface ObjectMessage {

    /**
     * Returns the unique id of the source object message.
     *
     * <p>Spec: PAOM2a / OM2a
     *
     * @return the message id, or {@code null} if unavailable
     */
    @Nullable String getId();

    /**
     * Returns the client id of the client that published the source object message.
     *
     * <p>Spec: PAOM2b / OM2b
     *
     * @return the client id, or {@code null} if unavailable
     */
    @Nullable String getClientId();

    /**
     * Returns the connection id of the connection from which the source object message
     * was published.
     *
     * <p>Spec: PAOM2c / OM2c
     *
     * @return the connection id, or {@code null} if unavailable
     */
    @Nullable String getConnectionId();

    /**
     * Returns the timestamp of the source object message, as milliseconds since the
     * epoch.
     *
     * <p>Spec: PAOM2d / OM2e
     *
     * @return the timestamp in milliseconds since the epoch, or {@code null} if
     *         unavailable
     */
    @Nullable Long getTimestamp();

    /**
     * Returns the name of the channel on which the source object message was received.
     *
     * <p>Spec: PAOM2e
     *
     * @return the channel name
     */
    @NotNull String getChannel();

    /**
     * Returns the operation carried by the source object message.
     *
     * <p>Spec: PAOM2f
     *
     * @return the operation that was applied
     */
    @NotNull ObjectOperation getOperation();

    /**
     * Returns the serial of the source object message - an opaque string that uniquely
     * identifies the operation.
     *
     * <p>Spec: PAOM2g / OM2h
     *
     * @return the serial, or {@code null} if unavailable
     */
    @Nullable String getSerial();

    /**
     * Returns the timestamp derived from the {@link #getSerial() serial} of the source
     * object message, as milliseconds since the epoch.
     *
     * <p>Spec: PAOM2h / OM2j
     *
     * @return the serial timestamp in milliseconds since the epoch, or {@code null} if
     *         unavailable
     */
    @Nullable Long getSerialTimestamp();

    /**
     * Returns the site code of the source object message - an opaque string used as a
     * key to update the map of serial values on an object.
     *
     * <p>Spec: PAOM2i / OM2i
     *
     * @return the site code, or {@code null} if unavailable
     */
    @Nullable String getSiteCode();

    /**
     * Returns the extras of the source object message - a JSON-encodable object
     * containing arbitrary message metadata and/or ancillary payloads. The client
     * library treats this field opaquely.
     *
     * <p>Spec: PAOM2j / OM2d
     *
     * @return the extras, or {@code null} if unavailable
     */
    @Nullable JsonObject getExtras();
}
