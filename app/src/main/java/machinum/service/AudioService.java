package machinum.service;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.errors.MinioException;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Holder;
import machinum.converter.AudioFileMapper;
import machinum.entity.AudioFileEntity;
import machinum.exception.AppIllegalStateException;
import machinum.model.AudioFile;
import machinum.model.AudioFile.AudioFileType;
import machinum.processor.core.HashSupport;
import machinum.repository.AudioFileRepository;
import machinum.service.TTSRestClient.Metadata;
import machinum.service.TTSRestClient.TTSRequest;
import machinum.util.TextUtil;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheHelper;
import org.springframework.cache.InMemoryCache;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static machinum.model.AudioFile.AudioFileType.SPEECH;
import static machinum.model.CheckedFunction.checked;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioService {

    private final TTSRestClient ttsRestClient;

    private final MinioService minioService;

    private final AudioFileRepository ttsFileRepository;

    private final AudioFileMapper audioFileMapper;

    private final CacheHelper cacheHelper;

    @Qualifier("ttsObjectMapper")
    private final Holder<ObjectMapper> objectMapper;

    private final InMemoryCache<String, Object> inMemoryCache;


    @Transactional(readOnly = true)
    public List<AudioFile> getAllByBookId(String bookId) {
        return audioFileMapper.toDto(ttsFileRepository.findAllByBookId(bookId, Sort.by("createdAt")));
    }

    @Transactional(readOnly = true)
    public List<AudioFile> getByChapterIds(List<String> chapterIds) {
        return audioFileMapper.toDto(ttsFileRepository.findByChapterIdIn(chapterIds));
    }

    @Transactional(readOnly = true)
    public List<AudioFile> getAllByChapterId(String chapterId) {
        return audioFileMapper.toDto(ttsFileRepository.findByChapterId(chapterId));
    }

    @Transactional(readOnly = true)
    public AudioFile getByChapterId(String chapterId, AudioFileType type) {
        return ttsFileRepository.findOneByChapterIdAndType(chapterId, type.name())
                .map(audioFileMapper::toDto)
                .orElseThrow(() -> new AppIllegalStateException("Audio not found for chapterId: %s", chapterId));
    }

    @Transactional(readOnly = true)
    public Optional<AudioFile> findById(String id) {
        return ttsFileRepository.findById(id)
                .map(audioFileMapper::toDto);
    }

    @Transactional(readOnly = true)
    public AudioFile getById(String id) {
        return findById(id)
                .orElseThrow(() -> new AppIllegalStateException("Audio not found for id: %s", id));
    }

    @SneakyThrows
    @Transactional
    public AudioFile generate(TTSRequest request) {
        return inMemoryCache.getRaw(request.toKey(), checked(s -> {
            if (request.getText() == null || request.getText().trim().isEmpty()) {
                log.error("Text is required for TTSRequest: {}", request);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Text is required");
            }

            //TODO add metadata check for minio file

            var fileId = UUID.randomUUID().toString();
            var chapterId = request.getChapterId();
            var minioKey = String.format("tts-%s.mp3", chapterId);

            var data = generateAudio(request);
            AudioFileEntity ttsFile = null;
            try {
                var fileMetadata = uploadToMinio(request, data, minioKey, fileId, chapterId);
                ttsFile = persistToDb(request, fileId, chapterId, minioKey, fileMetadata);
            } catch (Exception e) {
                minioService.removeFromToMinio(minioKey);
                ExceptionUtils.rethrow(e);
            }

            var audioFile = audioFileMapper.toDto(ttsFile);
            log.info("Generated AudioFile: {}", audioFile);

            return audioFile;
        }));
    }

    @SneakyThrows
    public byte[] joinAudioFiles(JoinRequest joinRequest) {
        return inMemoryCache.getRaw(joinRequest.toKey(), checked(s -> {
            log.debug("Joining {} audio files", joinRequest.getAudioFiles().size());

            if (joinRequest.getAudioFiles().size() < 2) {
                throw new IllegalArgumentException("At least two audio files are required for joining");
            }

            // Download all files from MinIO
            var downloadedFiles = new HashMap<String, byte[]>();
            var audioFiles = joinRequest.getAudioFiles();
            for (int i = 0; i < audioFiles.size(); i++) {
                var audioFile = audioFiles.get(i);
                try {
                    var preSignedUrl = minioService.getPreSignedUrl(audioFile.getMinioKey());
                    var content = minioService.downloadContent(preSignedUrl);
                    downloadedFiles.put("%s_%s".formatted("%04d".formatted(i), audioFile.getMinioKey()), content);
                    log.debug("Downloaded file: {}, size={}mb", audioFile.getMinioKey(), content.length / 1024 / 1024);
                } catch (Exception e) {
                    log.error("Failed to download file with key: {}", audioFile.getMinioKey(), e);
                    throw new IOException("Failed to download file: " + audioFile.getMinioKey(), e);
                }
            }

            // Create ZIP file
            byte[] zipContent = createZipFile(downloadedFiles);
            log.debug("ZIP file with chapter created: size={}mb", zipContent.length / 1024 / 1024);

            // Send to TTS service for joining
            byte[] bytes = ttsRestClient.joinMp3Files(zipContent, joinRequest.getOutputName(), joinRequest.isEnhance(),
                    joinRequest.getCoverArt(), joinRequest.getMetadata());
            log.debug("Successfully joined {} chapters into one file, size={}mb", joinRequest.getAudioFiles().size(),
                    bytes.length / 1024 / 1024);

            return bytes;
        }));
    }

    @SneakyThrows
    public Map<String, byte[]> getAudioContent(List<AudioFile> audioFiles) {
        var downloadedFiles = new ConcurrentHashMap<String, byte[]>();
        var executor = Executors.newFixedThreadPool(Math.min(audioFiles.size(), 10)); // adjust pool size as needed
        var futures = new ArrayList<Future<?>>();

        for (AudioFile audioFile : audioFiles) {
            futures.add(executor.submit(() -> {
                try {
                    var preSignedUrl = minioService.getPreSignedUrl(audioFile.getMinioKey());
                    var content = minioService.downloadContent(preSignedUrl);
                    downloadedFiles.put(audioFile.getId(), content);
                    log.debug("Downloaded file: {}, size={}mb", audioFile.getMinioKey(), content.length / 1024 / 1024);
                } catch (Exception e) {
                    log.error("Failed to download file with key: {}", audioFile.getMinioKey(), e);
                    throw new RuntimeException("Failed to download file: " + audioFile.getMinioKey(), e);
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get(); // wait for all tasks to complete
        }

        executor.shutdown();
        return downloadedFiles;
    }

    /* ============= */

    private byte[] createZipFile(Map<String, byte[]> files) throws IOException {
        var baos = new ByteArrayOutputStream();
        try (var zos = new ZipOutputStream(baos)) {
            for (var entry : files.entrySet()) {
                var zipEntry = new ZipEntry(entry.getKey());
                zos.putNextEntry(zipEntry);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }

        return baos.toByteArray();
    }

    private AudioFileEntity persistToDb(TTSRequest request, String fileId, String chapterId, String minioKey, FileMetadata fileMetadata) {
        var ttsFile = ttsFileRepository.findOneByMinioKey(minioKey)
                .map(entity -> {
                    entity.setUpdatedAt(Instant.now());
                    entity.setMetadata(fileMetadata);

                    return entity;
                })
                .orElseGet(() -> {
                    var audioFile = new AudioFileEntity();
                    audioFile.setId(fileId);
                    audioFile.setChapterId(chapterId);
                    audioFile.setName(TextUtil.toSnakeCase(request.getChapterTitle()));
                    audioFile.setType(SPEECH.name());
                    audioFile.setMinioKey(minioKey);
                    audioFile.setCreatedAt(Instant.now());
                    audioFile.setUpdatedAt(Instant.now());
                    audioFile.setMetadata(fileMetadata);

                    return audioFile;
                });


        log.info("Saving AudioFileEntity: {}", ttsFile);
        ttsFileRepository.save(ttsFile);

        return ttsFile;
    }

    private FileMetadata uploadToMinio(TTSRequest request, byte[] data, String minioKey, String fileId, String chapterId) throws IOException, MinioException, NoSuchAlgorithmException, InvalidKeyException {
        if (request.getReturnZip()) {
            return processZip(data, minioKey, fileId, chapterId, request.getMetadata());
        } else {
            log.info("Uploading MP3 data to MinIO with key: {}", minioKey);
            var fileMetadata = FileMetadata.builder()
                    .filename(minioKey)
                    .fileSizeBytes(data.length)
                    .durationSeconds(0)
                    .bitrateBps(0)
                    .sampleRateHz(0)
                    .channels(0)
                    .format("mp3")
                    .metadata(request.getMetadata())
                    .build();

            minioService.uploadToMinio(data, minioKey, fileId, chapterId, fileMetadata);
            return fileMetadata;
        }
    }

    @SneakyThrows
    private byte[] generateAudio(TTSRequest request) {
        if (minioService.isMinioEnabled()) {
            log.info("Generating MP3 data for request: {}", request);
            return ttsRestClient.generate(request);
        } else {
            throw new AppIllegalStateException("Minio is not available, please connect service to proceed.");
        }
    }

    private FileMetadata processZip(byte[] data, String minioKey, String fileId,
                                    String chapterId, Metadata metadata) throws IOException, MinioException, NoSuchAlgorithmException, InvalidKeyException {
        FileMetadata output = null;
        byte[] mp3Data = new byte[0];
        try (var zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                var name = entry.getName();
                log.debug("Found {} file in zip", name);
                if (name.endsWith(".mp3")) {
                    log.info("Uploading MP3 data to MinIO with key: {}", minioKey);
                    mp3Data = zis.readAllBytes();
                } else if (name.endsWith(".json")) {
                    byte[] jsonData = zis.readAllBytes();
                    log.debug("Extracted JSON metadata from zip entry: {}", new String(jsonData, StandardCharsets.UTF_8));
                    output = objectMapper.execute(mapper -> mapper.readValue(jsonData, FileMetadata.class));
                    output.setMetadata(metadata);
                } else {
                    log.warn("Found different file: {}", name);
                }
            }
        }

        if (Objects.nonNull(mp3Data) && mp3Data.length > 0) {
            minioService.uploadToMinio(mp3Data, minioKey, fileId, chapterId, output);
        } else {
            throw new AppIllegalStateException("MP3 file was not found in result zip");
        }

        return output;
    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @ToString(onlyExplicitlyIncluded = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class FileMetadata {

        @ToString.Include
        private String filename;
        private long fileSizeBytes;
        private double durationSeconds;
        private int bitrateBps;
        private int sampleRateHz;
        private int channels;
        private String format;
        @JsonAlias("id3Tags")
        @ToString.Include
        private Metadata metadata;

        public static FileMetadata createNew(Function<FileMetadataBuilder, FileMetadataBuilder> builderFn) {
            return builderFn.apply(builder()).build();
        }

        public Map<String, String> toMap() {
            Map<String, String> map = new HashMap<>();
            if (Objects.nonNull(metadata)) {
                map.putAll(metadata.toMap());
            }
            map.put("filename", getFilename());
            map.put("fileSizeBytes", String.valueOf(getFileSizeBytes()));
            map.put("durationSeconds", String.valueOf(getDurationSeconds()));
            map.put("bitrateBps", String.valueOf(getBitrateBps()));
            map.put("sampleRateHz", String.valueOf(getSampleRateHz()));
            map.put("channels", String.valueOf(getChannels()));
            map.put("format", getFormat());
            return map;
        }

    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @ToString(onlyExplicitlyIncluded = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class JoinRequest {

        @Builder.Default
        private List<AudioFile> audioFiles = new ArrayList<>();
        @ToString.Include
        private String outputName;
        private boolean enhance;
        private byte[] coverArt;
        private Metadata metadata;

        public String toKey() {
            return HashSupport.hashStringWithCRC32(audioFiles.stream()
                    .map(AudioFile::getId)
                    .collect(Collectors.joining("-")));
        }

    }

}
