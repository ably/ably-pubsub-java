package io.ably.pubsub.util;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.ably.pubsub.types.ReadOnlyMap;

/**
 * A map implemented using a {@link ConcurrentHashMap}. This class is a base class for other classes
 * which are designed to be internal to the library, specifically as regards access to the map
 * field.
 *
 * This class exposes a {@link ReadOnlyMap} which is safe to be exposed in our public API.
 *
 * @param <K> Key type.
 * @param <V> Value type.
 */
public abstract class InternalMap<K, V> implements ReadOnlyMap<K, V> {
    protected final ConcurrentMap<K, V> map = new ConcurrentHashMap<>();

    @Override
    public final boolean containsKey(final Object key) {
        return map.containsKey(key);
    }

    @Override
    public final boolean containsValue(final Object value) {
        return map.containsValue(value);
    }

    @Override
    public final Iterable<Entry<K, V>> entrySet() {
        return map.entrySet();
    }

    @Override
    public final V get(final Object key) {
        return map.get(key);
    }

    @Override
    public final boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public final Iterable<K> keySet() {
        return map.keySet();
    }

    @Override
    public final int size() {
        return map.size();
    }

    @Override
    public final Iterable<V> values() {
        return map.values();
    }
}
