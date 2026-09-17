package io.ably.pubsub.types;

import java.util.Map;

/**
 * Exposes a subset of the Map interface, providing read only access only, removing mutating interfaces.
 * Used in the Ably API where we have previously overexposed the full Map interface, providing a
 * level of acceptable support for users who had relied upon this overexposure for valid reasons
 * including testing and runtime inspection (e.g. iterating channels, counting channels, etc..).
 * 
 * Developers should not rely upon any methods in this interface as they will eventually
 * be remove from the Ably API, after a period of deprecation.
 */
public interface ReadOnlyMap<K, V> {
    boolean containsKey(Object key);
    boolean containsValue(Object value);
    Iterable<Map.Entry<K, V>> entrySet();
    V get(Object key);
    boolean isEmpty();
    Iterable<K> keySet();
    int size();
    Iterable<V> values();
}
