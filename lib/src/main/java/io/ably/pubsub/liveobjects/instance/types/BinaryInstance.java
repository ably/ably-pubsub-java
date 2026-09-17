package io.ably.pubsub.liveobjects.instance.types;

import com.google.gson.JsonPrimitive;
import io.ably.pubsub.liveobjects.instance.Instance;
import org.jetbrains.annotations.NotNull;

/**
 * A read-only {@link Instance} bound to a binary primitive value
 * (a {@code byte[]}).
 * Primitive instances are anonymous (no object id) and deliberately do not expose
 * {@code subscribe}, {@code set}, {@code remove} or any other id/iteration/write
 * methods - only {@code value()} - per RTTS10c.
 *
 * <p>Spec: RTTS10c
 */
public interface BinaryInstance extends Instance {

    /**
     * Returns the wrapped binary value.
     *
     * <p>Spec: RTINS4 / RTTS10c
     *
     * @return the wrapped bytes
     */
    byte @NotNull [] value();

    /**
     * Returns the compacted JSON snapshot of the wrapped value, narrowed to a
     * {@link JsonPrimitive}: binary compacts to a base64-encoded JSON string.
     *
     * <p>Spec: RTTS7a
     *
     * @return the compacted JSON primitive
     */
    @Override
    @NotNull JsonPrimitive compactJson();
}
