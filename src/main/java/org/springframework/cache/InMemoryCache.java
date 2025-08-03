package org.springframework.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class InMemoryCache<K, V> {

    private final Cache<K, V> cache;

    private InMemoryCache(Cache<K, V> cache) {
        this.cache = cache;
    }

    public static <K, V> InMemoryCache<K, V> create() {
        return new InMemoryCache<>(Caffeine.newBuilder().build());
    }

    public static <K, V> InMemoryCache<K, V> create(Duration expireAfterWrite) {
        return new InMemoryCache<>(
                Caffeine.newBuilder()
                        .expireAfterWrite(expireAfterWrite)
                        .build()
        );
    }

    public static <K, V> InMemoryCache<K, V> create(Duration expireAfterWrite, long maximumSize) {
        return new InMemoryCache<>(
                Caffeine.newBuilder()
                        .expireAfterWrite(expireAfterWrite)
                        .maximumSize(maximumSize)
                        .build()
        );
    }

    public void put(K key, V value) {
        cache.put(key, value);
    }

    public Optional<V> get(K key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    public <T> T getRaw(K key, Function<K, T> loader) {
        return (T) get(key, k -> (V) loader.apply(k));
    }

    public V get(K key, Function<K, V> loader) {
        return cache.get(key, loader);
    }

    public CompletableFuture<V> getAsync(K key, Function<K, CompletableFuture<V>> asyncLoader) {
        V cached = cache.getIfPresent(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return asyncLoader.apply(key).thenApply(value -> {
            cache.put(key, value);
            return value;
        });
    }

    public void invalidate(K key) {
        cache.invalidate(key);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    public long size() {
        return cache.estimatedSize();
    }

    public void cleanUp() {
        cache.cleanUp();
    }

}