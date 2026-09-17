package io.ably.pubsub.liveobjects.value;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;

/**
 * An immutable value type representing the intent to create a new
 * {@code LiveCounter} object with a specific initial count. Passed to mutation
 * methods (such as {@code LiveMapInstance#set} or {@code LiveMapPathObject#set},
 * wrapped via {@link LiveMapValue#of(LiveCounter)}) to assign a new
 * {@code LiveCounter} to the objects graph.
 *
 * <p>This type is a holder for the initial value only - it is not a live,
 * subscribable view of channel state. The {@code COUNTER_CREATE} operation it
 * gives rise to is published when the enclosing mutation is applied.
 *
 * <p>Instances are obtained via the static {@link #create(Number)} factory and
 * are immutable after creation. The initial count is held internally by the
 * implementation; it has no public accessor.
 *
 * <p>Spec: RTLCV1, RTLCV2, RTLCV3
 */
public abstract class LiveCounter {

    private static final String IMPLEMENTATION_CLASS = "io.ably.pubsub.liveobjects.value.livecounter.DefaultLiveCounter";

    /**
     * Extended by the LiveObjects implementation; not intended for
     * application subclassing. Avoids implicit empty public constructor.
     */
    protected LiveCounter() {
    }

    /**
     * Creates a new {@code LiveCounter} value type with an initial count of 0.
     *
     * <p>Spec: RTLCV3, RTLCV3a1, RTLCV3b
     *
     * @return an immutable {@code LiveCounter} value type
     * @throws IllegalStateException if the LiveObjects plugin is not on the classpath
     */
    @NotNull
    public static LiveCounter create() {
        return create(0);
    }

    /**
     * Creates a new {@code LiveCounter} value type with the given initial count.
     * No input validation is performed at creation time; validation is deferred
     * to when the value is evaluated by a mutation method.
     *
     * <p>Spec: RTLCV3, RTLCV3b, RTLCV3c, RTLCV3d
     *
     * @param initialCount the initial count for the new {@code LiveCounter} object
     * @return an immutable {@code LiveCounter} value type
     * @throws IllegalStateException if the LiveObjects plugin is not on the classpath
     */
    @NotNull
    public static LiveCounter create(@NotNull Number initialCount) {
        try {
            Class<?> implementation = Class.forName(IMPLEMENTATION_CLASS);
            return (LiveCounter) implementation
                .getDeclaredConstructor(Number.class)
                .newInstance(initialCount);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException e) {
            throw new IllegalStateException(
                "LiveObjects plugin not found in classpath. LiveObjects functionality will not be available.", e);
        }
    }
}
