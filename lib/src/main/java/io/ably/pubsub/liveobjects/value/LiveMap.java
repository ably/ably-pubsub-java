package io.ably.pubsub.liveobjects.value;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Map;

/**
 * An immutable value type representing the intent to create a new
 * {@code LiveMap} object with specific initial entries. Passed to mutation
 * methods (such as {@code LiveMapInstance#set} or {@code LiveMapPathObject#set},
 * wrapped via {@link LiveMapValue#of(LiveMap)}) to assign a new {@code LiveMap}
 * to the objects graph. Entries may themselves contain nested {@code LiveMap} /
 * {@code LiveCounter} value types, enabling composable object structures.
 *
 * <p>This type is a holder for the initial value only - it is not a live,
 * subscribable view of channel state. The {@code MAP_CREATE} operation it gives
 * rise to is published when the enclosing mutation is applied.
 *
 * <p>Instances are obtained via the static {@link #create(Map)} factory and
 * are immutable after creation. The initial entries are held internally by the
 * implementation; they have no public accessor.
 *
 * <p>Spec: RTLMV1, RTLMV2, RTLMV3
 */
public abstract class LiveMap {

    private static final String IMPLEMENTATION_CLASS = "io.ably.pubsub.liveobjects.value.livemap.DefaultLiveMap";

    /**
     * Extended by the LiveObjects implementation; not intended for
     * application subclassing. Avoids implicit empty public constructor.
     */
    protected LiveMap() {
    }

    /**
     * Creates a new {@code LiveMap} value type with no initial entries.
     *
     * <p>Spec: RTLMV3, RTLMV3a1, RTLMV3b
     *
     * @return an immutable {@code LiveMap} value type
     * @throws IllegalStateException if the LiveObjects plugin is not on the classpath
     */
    @NotNull
    public static LiveMap create() {
        return create(Collections.<String, LiveMapValue>emptyMap());
    }

    /**
     * Creates a new {@code LiveMap} value type with the given initial entries.
     * No input validation is performed at creation time; validation is deferred
     * to when the value is evaluated by a mutation method.
     *
     * <p>Spec: RTLMV3, RTLMV3b, RTLMV3c, RTLMV3d
     *
     * @param entries the initial entries for the new {@code LiveMap} object
     * @return an immutable {@code LiveMap} value type
     * @throws IllegalStateException if the LiveObjects plugin is not on the classpath
     */
    @NotNull
    public static LiveMap create(@NotNull Map<String, LiveMapValue> entries) {
        try {
            Class<?> implementation = Class.forName(IMPLEMENTATION_CLASS);
            return (LiveMap) implementation
                .getDeclaredConstructor(Map.class)
                .newInstance(entries);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException e) {
            throw new IllegalStateException(
                "LiveObjects plugin not found in classpath. LiveObjects functionality will not be available.", e);
        }
    }
}
