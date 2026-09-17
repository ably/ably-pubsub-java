package io.ably.pubsub.liveobjects.instance.types;

import com.google.gson.JsonArray;
import io.ably.pubsub.liveobjects.instance.Instance;
import org.jetbrains.annotations.NotNull;

/**
 * A read-only {@link Instance} bound to a {@link JsonArray} primitive value.
 * Primitive instances are anonymous (no object id) and deliberately do not expose
 * {@code subscribe}, {@code set}, {@code remove} or any other id/iteration/write
 * methods - only {@code value()} - per RTTS10c.
 *
 * <p>Spec: RTTS10c
 */
public interface JsonArrayInstance extends Instance {

    /**
     * Returns the wrapped JSON array.
     *
     * <p>Spec: RTINS4 / RTTS10c
     *
     * @return the wrapped JsonArray value
     */
    @NotNull
    JsonArray value();

    /**
     * Returns the compacted JSON snapshot of the wrapped value, narrowed to a
     * {@link JsonArray}: a {@code JsonArrayInstance} always compacts to a JSON array.
     *
     * <p>Spec: RTTS7a
     *
     * @return the compacted JSON array
     */
    @Override
    @NotNull JsonArray compactJson();
}
