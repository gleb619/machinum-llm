package org.springframework.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Helper class that provides a flexible key-value store abstraction.
 * Values can be retrieved by key from configured plugins, or generated
 * and stored if they don't exist.
 */
@Slf4j
@RequiredArgsConstructor
public class CacheHelper {

    private final CacheManager cacheManager;
    private final List<CachePlugin> plugins;

    @Cacheable(value = "store", key = "#key", unless = "#result == null")
    public <T> Optional<T> getValue(String key) {
        T value = null;
        return Optional.ofNullable(value); // This will be overridden by the cached value
    }

    @CachePut(value = "store", key = "#key", unless = "#result == null")
    public <T> Optional<T> setValue(String key, T value) {
        return Optional.ofNullable(value);
    }

    @CacheEvict(value = "store", key = "#key")
    public void evictValue(String key) {
        log.info("Cache evicted for key: {}", key);

        for (CachePlugin plugin : plugins) {
            plugin.remove(key);
        }
    }

    public boolean cacheContainsKey(String key) {
        return cacheManager.getCache("store").get(key) != null;
    }

    /**
     * Gets a value for a key, or creates and stores it if not found.
     *
     * @param <T>      Type of value to get or create
     * @param key      The key to look up
     * @param supplier The supplier to create the value if not found
     * @return The retrieved or newly created value
     */
    public <T> T getOrCreate(String key, Supplier<T> supplier) {
        // First try to get from any plugin
        for (CachePlugin plugin : plugins) {
            if (plugin.hasKey(key)) {
                Optional<T> value = plugin.get(key);
                if (value.isPresent()) {
                    log.warn("Return value from cache: {}", key);
                    return value.get();
                }
            }
        }

        // Create new value if not found
        T value = supplier.get();

        // Store in all plugins
        for (CachePlugin plugin : plugins) {
            plugin.save(key, value);
        }

        return value;
    }

}
