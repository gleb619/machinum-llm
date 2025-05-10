package org.springframework.cache.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Holder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CachePlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Optional;


/**
 * KeyValuePlugin implementation that stores values in local files.
 * Each key-value pair is stored in a separate file in a configured directory.
 */
//@Component
//@Order(100)
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mode", havingValue = "production")
public class LocalFilePlugin implements CachePlugin {

    @Value("${app.cache.folder}")
    private final String storageDirectory;
    private final Holder<ObjectMapper> objectMapper;


    @PostConstruct
    public void init() {
        // Ensure storage directory exists
        File directory = new File(storageDirectory);
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                log.warn("Failed to create storage directory: {}", storageDirectory);
            }
        }
    }

    @Override
    public boolean hasKey(String key) {
        File file = getFileForKey(key, null);
        return file.exists() && file.isFile() && file.canRead();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        File file = getFileForKey(key, null);

        if (!file.exists()) {
            return Optional.empty();
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            T value = (T) ois.readObject();
            return Optional.ofNullable(value);
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            log.error("Error reading value for key: {}", key, e);
            return Optional.empty();
        }
    }

    @Override
    public <T> void save(String key, T value) {
        File file = getFileForKey(key, value.getClass().getName());

        try {
            objectMapper.execute(mapper -> {
                mapper.writeValue(file, value);

                return null;
            });
        } catch (Exception e) {
            log.error("Error saving value for key: {}", key, e);
        }
    }

    private File getFileForKey(String key, String valueType) {
        String sanitizedKey = key.replaceAll("[^a-zA-Z0-9.-]", "_");
        return new File(storageDirectory, "%s-%s.json".formatted(sanitizedKey, valueType));
    }

}
