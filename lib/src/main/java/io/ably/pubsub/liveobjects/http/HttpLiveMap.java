package io.ably.pubsub.liveobjects.http;

import io.ably.pubsub.liveobjects.message.ObjectsMapSemantics;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * A map object in a {@link io.ably.pubsub.liveobjects.HttpObject#get(io.ably.pubsub.types.Param[])}
 * response. Nested objects and values in its entries are decoded recursively.
 *
 * <p>Spec: RSO14
 */
public interface HttpLiveMap extends HttpLiveObject {

    /**
     * @return the map's conflict-resolution semantics, or {@link ObjectsMapSemantics#UNKNOWN} for a
     * value this library doesn't recognise (RSO14b).
     */
    @NotNull ObjectsMapSemantics getSemantics();

    /**
     * @return an unmodifiable map of the map's entries, by key (RSO14c).
     */
    @NotNull Map<String, HttpLiveMapEntry> getEntries();
}
