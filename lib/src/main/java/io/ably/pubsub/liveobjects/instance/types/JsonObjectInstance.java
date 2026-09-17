package io.ably.pubsub.liveobjects.instance.types;

import com.google.gson.JsonObject;
import io.ably.pubsub.liveobjects.instance.Instance;
import org.jetbrains.annotations.NotNull;

/**
 * A read-only {@link Instance} bound to a {@link JsonObject} primitive value.
 * Primitive instances are anonymous (no object id) and deliberately do not expose
 * {@code subscribe}, {@code set}, {@code remove} or any other id/iteration/write
 * methods - only {@code value()} - per RTTS10c.
 *
 * <p>Spec: RTTS10c
 */
public interface JsonObjectInstance extends Instance {

    /**
     * Returns the wrapped JSON object.
     *
     * <p>Spec: RTINS4 / RTTS10c
     *
     * @return the wrapped JsonObject value
     */
    @NotNull
    JsonObject value();

    /**
     * Returns the compacted JSON snapshot of the wrapped value, narrowed to a
     * {@link JsonObject}: a {@code JsonObjectInstance} always compacts to a JSON object.
     *
     * <p>Spec: RTTS7a
     *
     * @return the compacted JSON object
     */
    @Override
    @NotNull JsonObject compactJson();
}
