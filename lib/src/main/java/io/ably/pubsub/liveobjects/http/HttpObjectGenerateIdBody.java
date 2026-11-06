package io.ably.pubsub.liveobjects.http;

import io.ably.pubsub.liveobjects.message.ObjectsMapSemantics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The create body passed to {@link io.ably.pubsub.liveobjects.HttpObject#generateObjectId}: either
 * a map create or a counter create. Create instances with the static factories, which set exactly one.
 *
 * <p>Spec: RSO12
 */
public final class HttpObjectGenerateIdBody {

    private final @Nullable HttpObjectOperation.MapCreate mapCreate;
    private final @Nullable HttpObjectOperation.CounterCreate counterCreate;

    private HttpObjectGenerateIdBody(@Nullable HttpObjectOperation.MapCreate mapCreate,
                                     @Nullable HttpObjectOperation.CounterCreate counterCreate) {
        this.mapCreate = mapCreate;
        this.counterCreate = counterCreate;
    }

    /** Spec: RSO12a */
    public static @NotNull HttpObjectGenerateIdBody mapCreate(@NotNull HttpObjectOperation.MapCreate body) {
        return new HttpObjectGenerateIdBody(body, null);
    }

    /** Spec: RSO12a */
    public static @NotNull HttpObjectGenerateIdBody mapCreate(@NotNull ObjectsMapSemantics semantics,
                                                              @NotNull Map<String, PublishObjectData> entries) {
        return mapCreate(new HttpObjectOperation.MapCreate(semantics, entries));
    }

    /** Spec: RSO12a, with {@link ObjectsMapSemantics#LWW} semantics. */
    public static @NotNull HttpObjectGenerateIdBody mapCreate(@NotNull Map<String, PublishObjectData> entries) {
        return mapCreate(ObjectsMapSemantics.LWW, entries);
    }

    /** Spec: RSO12b */
    public static @NotNull HttpObjectGenerateIdBody counterCreate(@NotNull HttpObjectOperation.CounterCreate body) {
        return new HttpObjectGenerateIdBody(null, body);
    }

    /** Spec: RSO12b */
    public static @NotNull HttpObjectGenerateIdBody counterCreate(@NotNull Number count) {
        return counterCreate(new HttpObjectOperation.CounterCreate(count));
    }

    /** @return the map create body, or {@code null} if this is a counter create body. */
    public @Nullable HttpObjectOperation.MapCreate getMapCreate() {
        return mapCreate;
    }

    /** @return the counter create body, or {@code null} if this is a map create body. */
    public @Nullable HttpObjectOperation.CounterCreate getCounterCreate() {
        return counterCreate;
    }
}
