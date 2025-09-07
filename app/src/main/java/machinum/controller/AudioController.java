package machinum.controller;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.model.AudioFile;
import machinum.service.AudioService;
import machinum.service.MinioService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static machinum.processor.core.HashSupport.hashStringWithCRC32;

/**
 * Controller class for handling HTTP requests related to Audio.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class AudioController {

    private final AudioService service;
    private final MinioService minioService;

    /**
     * Retrieves all audio files for given chapter's id.
     *
     * @return a list of audio files
     */
    @GetMapping("/chapters/{id}/audio")
    public List<AudioFile> getChapterAudio(@PathVariable("id") String chapterId) {
        log.info("Received request to get all audio files for chapter: {}", chapterId);
        return service.getAllByChapterId(chapterId);
    }

    @SneakyThrows
    @GetMapping("/audio/{id}/content")
    public ResponseEntity<byte[]> getAudioContent(@PathVariable("id") String id) {
        log.info("Received request to get audio content by id: {}", id);
        var audioFile = service.getById(id);
        var preSignedUrl = minioService.getPreSignedUrl(audioFile.getMinioKey());
        var content = minioService.downloadContent(preSignedUrl);

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
        headers.setContentLength(content.length);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s.mp3\"".formatted(hashStringWithCRC32(preSignedUrl)));

        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

}