package io.ably.pubsub.liveobjects.http;

import io.ably.pubsub.types.Param;
import org.jetbrains.annotations.NotNull;

/**
 * Builds the {@code Param[]} request parameters for
 * {@link io.ably.pubsub.liveobjects.HttpObject#getCompact(Param[])} and
 * {@link io.ably.pubsub.liveobjects.HttpObject#get(Param[])}.
 *
 * <p>The params are ordinary {@link Param}s, so they can also be built by hand with the
 * {@link #OBJECT_ID} and {@link #PATH} keys. {@code compact} (RSO2a1c) is not a parameter: the
 * format is chosen by calling {@code getCompact} (compact) or {@code get} (full), so each returns its
 * own type.
 *
 * <pre>{@code
 * channel.object.get(HttpObjectGetParams.ofPath("players.alice"));
 * }</pre>
 *
 * <p>Spec: RSO2a1
 */
public final class HttpObjectGetParams {

    /**
     * The key of the ID of the object to fetch. It's sent as a path segment, not in the query
     * string. If it's absent, the channel object is the target.
     * <p>Spec: RSO2a1a
     */
    public static final String OBJECT_ID = "objectId";

    /**
     * The key of a path within the target, evaluated by the server relative to the channel object
     * or the {@link #OBJECT_ID object}. The path is sent as given.
     * <p>Spec: RSO2a1b, RSO6
     */
    public static final String PATH = "path";

    private HttpObjectGetParams() {}

    /**
     * Targets an object by ID.
     *
     * @param objectId the ID of the object to fetch.
     * @return the params.
     */
    public static @NotNull Param @NotNull [] ofObjectId(@NotNull String objectId) {
        return new Param[] { new Param(OBJECT_ID, objectId) };
    }

    /**
     * Targets a path within the channel object.
     *
     * @param path the path, relative to the channel object.
     * @return the params.
     */
    public static @NotNull Param @NotNull [] ofPath(@NotNull String path) {
        return new Param[] { new Param(PATH, path) };
    }

    /**
     * Targets a path within an object.
     *
     * @param objectId the ID of the object the path is evaluated from.
     * @param path the path, relative to that object.
     * @return the params.
     */
    public static @NotNull Param @NotNull [] of(@NotNull String objectId, @NotNull String path) {
        return new Param[] { new Param(OBJECT_ID, objectId), new Param(PATH, path) };
    }
}
