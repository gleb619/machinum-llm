package org.springframework.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@RequiredArgsConstructor
public class AssetsResourceController {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, String> cachedResourceMetadata = new ConcurrentHashMap<>(); // Key: "type/filename", Value: remoteUrl
    @Value("${assets.cache.folder}")
    private String cacheDirectoryPathString;
    @Value("${assets.cache.metadata-file}")
    private String metadataFilePathString;
    private Path cachePath;
    private Path metadataFilePath;

    @PostConstruct
    public void init() {
        this.cachePath = Paths.get(cacheDirectoryPathString).toAbsolutePath();
        this.metadataFilePath = Paths.get(metadataFilePathString).toAbsolutePath();
        try {
            Files.createDirectories(cachePath);
            log.debug("Dynamic cache directory created/ensured at: {}", cachePath);
            loadMetadata();
        } catch (IOException e) {
            log.error("Could not create cache directory or load metadata: {}", cacheDirectoryPathString, e);
            throw new RuntimeException("Could not initialize cache", e);
        }
    }

    @GetMapping("/assets/{type}/{filename:.+}")
    public ResponseEntity<Resource> getCachedResource(@PathVariable String type,
                                                      @PathVariable String filename,
                                                      @RequestParam String url) {
        String decodedUrl;
        try {
            // URLs from query parameters are typically already decoded by Spring,
            // but if they are double-encoded or specifically encoded, decode them.
            // For simplicity, assuming Spring handles basic decoding. If issues, explicitly decode.
            decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decode URL: {}", url, e);
            return ResponseEntity.badRequest().build();
        }

        Path typeDir = cachePath.resolve(type);
        try {
            Files.createDirectories(typeDir);
        } catch (IOException e) {
            log.error("Could not create type directory in cache: {}", typeDir, e);
            return ResponseEntity.internalServerError().build();
        }

        Path localFilePath = typeDir.resolve(filename);
        String metadataKey = type + "/" + filename;

        if (!Files.exists(localFilePath)) {
            log.debug("Cache miss for: {}. Attempting to download from: {}", localFilePath, decodedUrl);
            boolean downloaded = downloadAndCacheResource(decodedUrl, type, filename);
            if (!downloaded) {
                return ResponseEntity.notFound().build();
            }
        } else {
            log.trace("Cache hit for: {}", localFilePath);
            // Optionally, check file age here for more sophisticated refresh
        }

        // Always ensure metadata contains the URL for refresh purposes,
        // especially if it was populated by another node or manually.
        if (!decodedUrl.equals(cachedResourceMetadata.get(metadataKey))) {
            // This might happen if URL for same file changes, or if metadata was lost and rebuilt by access
            cachedResourceMetadata.put(metadataKey, decodedUrl);
            saveMetadata();
        }

        Resource resource = new FileSystemResource(localFilePath);
        String contentType = determineContentType(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    private boolean downloadAndCacheResource(String remoteUrl, String type, String filename) {
        try {
            log.debug("Downloading: {} to cache as {}/{}", remoteUrl, type, filename);
            byte[] fileContent = restTemplate.getForObject(remoteUrl, byte[].class);
            if (fileContent != null) {
                Path typePath = cachePath.resolve(type);
                Files.createDirectories(typePath);
                Path filePath = typePath.resolve(filename);
                Files.write(filePath, fileContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
                log.debug("Successfully downloaded and cached: {} to {}", remoteUrl, filePath);

                String metadataKey = type + "/" + filename;
                cachedResourceMetadata.put(metadataKey, remoteUrl);
                saveMetadata();
                return true;
            } else {
                log.warn("Failed to download {}: content was null", remoteUrl);
                return false;
            }
        } catch (Exception e) {
            String message = "Error downloading resource %s for %s/%s: %s".formatted(remoteUrl, type, filename, e.getMessage());
            if (log.isTraceEnabled()) {
                log.error(message, e);
            } else {
                log.error(message);
            }

            return false;
        }
    }

    private void loadMetadata() {
        if (Files.exists(metadataFilePath)) {
            try {
                Map<String, String> loadedMap = objectMapper.readValue(metadataFilePath.toFile(), new TypeReference<Map<String, String>>() {
                });
                cachedResourceMetadata.putAll(loadedMap);
                log.debug("Loaded {} entries from cache metadata file: {}", cachedResourceMetadata.size(), metadataFilePath);
            } catch (IOException e) {
                log.error("Failed to load cache metadata from {}: {}", metadataFilePath, e.getMessage());
            }
        } else {
            log.debug("Cache metadata file not found, starting with empty metadata: {}", metadataFilePath);
        }
    }

    @Synchronized
    private void saveMetadata() {
        try {
            Files.createDirectories(metadataFilePath.getParent()); // Ensure parent directory exists
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(metadataFilePath.toFile(), new TreeMap<>(cachedResourceMetadata));
            log.trace("Cache metadata saved to: {}", metadataFilePath);
        } catch (IOException e) {
            log.error("Failed to save cache metadata to {}: {}", metadataFilePath, e.getMessage());
        }
    }

    private String determineContentType(String filename) {
        if (filename.endsWith(".css")) {
            return "text/css";
        } else if (filename.endsWith(".js") || filename.endsWith(".mjs")) {
            return "application/javascript";
        } else if (filename.endsWith(".json")) {
            return "application/json";
        } else if (filename.endsWith(".png")) {
            return "image/png";
        } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (filename.endsWith(".gif")) {
            return "image/gif";
        }
        // Add more types as needed
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

}

