package machinum.service;

import io.minio.*;
import io.minio.errors.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Holder;
import machinum.exception.AppIllegalStateException;
import machinum.service.AudioService.FileMetadata;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    @Value("${app.minio.bucketName}")
    private final String minioBucketName;

    @Value("${app.minio.check-enabled}")
    private final Boolean checkEnabled;

    private final MinioClient minioClient;

    @Qualifier("minioHttpClient")
    private final Holder<HttpClient> httpClient;

    @SneakyThrows
    @PostConstruct
    public void init() {
        if (!checkEnabled) return;

        if (!isMinioEnabled()) {
            throw new AppIllegalStateException("Minio is not available right now");
        }

        createBucket();
    }

    public boolean isMinioEnabled() {
        try {
            minioClient.listBuckets();
            return true;
        } catch (Exception e) {
            log.warn("Minio is not accessible: {}", e.getMessage());
            return false;
        }
    }

    private void createBucket() throws ErrorResponseException, InsufficientDataException, InternalException, InvalidKeyException, InvalidResponseException, IOException, NoSuchAlgorithmException, ServerException, XmlParserException {
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioBucketName).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioBucketName).build());
        }
    }

    public void uploadToMinio(byte[] mp3Data, String objectKey, String fileId, String chapterId, FileMetadata output) throws MinioException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        if (!checkEnabled) {
            createBucket();
        }

        try {
            var metadata = Objects.nonNull(output) ? output.toMap() : Collections.<String, String>emptyMap();
            metadata.put("chapterId", chapterId);
            metadata.put("fileId", fileId);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioBucketName)
                            .object(objectKey)
                            .stream(new ByteArrayInputStream(mp3Data), mp3Data.length, -1)
                            .contentType("audio/mpeg")
                            .tags(Map.of(
                                    "fileId", fileId,
                                    "chapterId", chapterId
                            ))
                            .userMetadata(metadata)
                            .build()
            );
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidResponseException |
                 XmlParserException | ServerException e) {
            throw new MinioException("MinIO upload failed: " + e.getMessage());
        }
    }

    public boolean removeFromToMinio(String objectKey) throws MinioException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        if (!checkEnabled) {
            createBucket();
        }

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioBucketName)
                            .object(objectKey)
                            .build()
            );
            return true;
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidResponseException |
                 XmlParserException | ServerException e) {
            log.warn("MinIO upload failed: %s".formatted(e.getMessage()), e);
            return false;
        }
    }

    public String getPreSignedUrl(String objectKey) throws MinioException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        return getPreSignedUrl(objectKey, minioBucketName);
    }

    public String getPreSignedUrl(String objectKey, String bucketName) throws InvalidKeyException, IOException, NoSuchAlgorithmException, MinioException {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(io.minio.http.Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(1, TimeUnit.HOURS)
                            .build()
            );
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidResponseException |
                 XmlParserException | ServerException e) {
            throw new MinioException("MinIO pre-signed URL generation failed: " + e.getMessage());
        }
    }

    public byte[] downloadContent(String preSignedUrl) throws IOException {
        log.debug("Prepare to download content from minio: {}", preSignedUrl);

        var request = HttpRequest.newBuilder()
                .uri(URI.create(preSignedUrl))
                .GET()
                .build();

        log.debug("Sending GET request to: {}", preSignedUrl);

        var response = httpClient.execute(client -> client.send(request, HttpResponse.BodyHandlers.ofByteArray()));

        if (response.statusCode() != HttpStatus.OK.value()) {
            String errorMessage = "Failed to fetch MP3 from MinIO pre-signed URL. Status: " + response.statusCode();
            log.error(errorMessage);
            throw new IOException(errorMessage);
        }

        log.debug("Successfully downloaded content from: {}", preSignedUrl);

        return response.body();
    }

}
