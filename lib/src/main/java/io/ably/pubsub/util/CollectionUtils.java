package io.ably.pubsub.util;

import java.util.HashMap;
import java.util.Map;

public final class CollectionUtils {
    private CollectionUtils() { }
    
    /**
     * Creates a shallow copy.
     * 
     * @param <K> Key type.
     * @param <V> Value type.
     * @param map The map to be copied.
     * @return A new map.
     */
    public static <K, V> Map<K, V> copy(final Map<K, V> map) {
        final Map<K, V> copy = new HashMap<>(map.size());
        copy.putAll(map);
        return copy;
    }
}
