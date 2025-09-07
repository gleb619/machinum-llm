package org.springframework.cache.plugin;

import org.springframework.cache.CachePlugin;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * No-operation implementation of KeyValuePlugin that doesn't actually store anything.
 * Useful for testing or when storage is not needed.
 */
@Order
@Component
public class NoopPlugin implements CachePlugin {

    @Override
    public boolean hasKey(String key) {
        return false;
    }

    @Override
    public <T> Optional<T> get(String key) {
        return Optional.empty();
    }

    @Override
    public <T> void save(String key, T value) {
        // Do nothing - this is a no-op implementation
    }

    @Override
    public void remove(String key) {
        //ignore
    }
}
