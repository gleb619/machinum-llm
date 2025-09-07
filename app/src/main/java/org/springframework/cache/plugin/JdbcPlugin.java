package org.springframework.cache.plugin;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Holder;
import org.springframework.async.AsyncHelper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CachePlugin;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * KeyValuePlugin implementation that stores values in a database using JdbcTemplate.
 * Values are serialized to JSON for storage.
 */
@Slf4j
@Component
@Order(200)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mode", havingValue = "production")
public class JdbcPlugin implements CachePlugin {

    private final JdbcTemplate jdbcTemplate;
    @Qualifier("objectMapperHolder")
    private final Holder<ObjectMapper> objectMapper;
    private final AsyncHelper asyncHelper;


    @Override
    public boolean hasKey(String key) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cache_store WHERE key_name = ?",
                Integer.class,
                key);
        return count != null && count > 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        try {
            String json = jdbcTemplate.queryForObject(
                    "SELECT value_data FROM cache_store WHERE key_name = ?",
                    String.class,
                    key);

            if (json == null) {
                return Optional.empty();
            }

            String className = jdbcTemplate.queryForObject(
                    "SELECT value_type FROM cache_store WHERE key_name = ?",
                    String.class,
                    key);

            Class<?> valueClass = Class.forName(className);
            T value = (T) objectMapper.execute(mapper -> {
                try {
                    return mapper.readValue(json, valueClass);
                } catch (Exception e) {
                    var tree = mapper.readTree(json);
                    return mapper.convertValue(tree, valueClass);
                }
            });

            return Optional.of(value);
        } catch (Exception e) {
            log.error("Error retrieving value for key: {}", key, e);
            return Optional.empty();
        }
    }

    @Override
    public <T> void save(String key, T value) {
        if (value == null) {
            return;
        }

        asyncHelper.runAsync(() -> {
            try {
                doSave(key, value);
            } catch (Exception e) {
                log.error("Error serializing value for key: {}", key, e);
            }
        });
    }

    @Override
    public void remove(String key) {
        jdbcTemplate.update(
                "DELETE FROM cache_store WHERE key_name = ?", key);
    }

    private <T> void doSave(String key, T value) {
        String valueType = value.getClass().getName();
        String json = objectMapper.execute(mapper -> mapper.writeValueAsString(value));

        int updated = jdbcTemplate.update(
                "UPDATE cache_store SET value_data = ?, value_type = ? WHERE key_name = ?",
                json, valueType, key);

        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO cache_store (key_name, value_data, value_type) VALUES (?, ?, ?)",
                    key, json, valueType);
        }
    }

    @Slf4j
    @Component
    @RequiredArgsConstructor
    @ConditionalOnProperty(name = "app.mode", havingValue = "production")
    public static class Job implements CommandLineRunner {

        private final JdbcTemplate jdbcTemplate;

        @Value("${app.cache.ttl}")
        private final Duration duration;

        @Override
        public void run(String... args) {
            try {
                cleanupOldCacheEntries();
            } catch (Exception e) {
                log.error("ERROR: Can't clean cache table: ", e);
            }
        }

        private void cleanupOldCacheEntries() {
            var oneWeekAgo = LocalDateTime.now().minusSeconds(duration.toSeconds());
            var deletedRows = jdbcTemplate.update("DELETE FROM cache_store WHERE created_at < ?", oneWeekAgo);

            log.info("Cleaned up {} old cache entries", deletedRows);
        }

    }

}
