package io.ably.pubsub.push;

/**
 * Interface for an entity that supplies key value store
 */
public interface Storage {

    /**
     * Put string value in to storage
     * @param key name under which value is stored
     * @param value stored string value
     */
    void put(String key, String value);

    /**
     * Put integer value in to storage
     * @param key name after which value is stored
     * @param value stored integer value
     */
    void put(String key, int value);

    /**
     * Returns string value based on key from storage
     * @param key name under value is stored
     * @param defaultValue value which is returned if key is not found
     * @return value stored under key or default value if key is not found
     */
    String get(String key, String defaultValue);

    /**
     * Returns integer value based on key from storage
     * @param key name under value is stored
     * @param defaultValue value which is returned if key is not found
     * @return value stored under key or default value if key is not found
     */
    int get(String key, int defaultValue);

    /**
     * Removes keys from storage
     * @param keys array of keys which values should be removed from storage
     */
    void clear(String[] keys);
}
