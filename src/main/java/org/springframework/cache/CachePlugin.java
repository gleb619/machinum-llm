package org.springframework.cache;

import java.util.Optional;

/**
 * Interface for plugins that provide key-value storage functionality.
 */
public interface CachePlugin {

    /**
     * Checks if this plugin has a value for the given key.
     *
     * @param key The key to check
     * @return true if a value exists for the key
     */
    boolean hasKey(String key);

    /**
     * Gets a value for the given key if it exists.
     *
     * @param <T> The expected type of the value
     * @param key The key to look up
     * @return Optional containing the value if found, empty Optional otherwise
     */
    <T> Optional<T> get(String key);

    /**
     * Saves a value with the given key.
     *
     * @param <T>   The type of the value
     * @param key   The key to save the value under
     * @param value The value to save
     */
    <T> void save(String key, T value);

    default void remove(String key) {
        throw new IllegalStateException("Not implemented!");
    }

}
