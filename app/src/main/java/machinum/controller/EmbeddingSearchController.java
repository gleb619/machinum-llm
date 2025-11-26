package machinum.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.model.ChapterSimilarityResult;
import machinum.model.NameSimilarityResult;
import machinum.service.EmbeddingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * REST controller for embedding-based semantic search functionality.
 * Provides endpoints for searching chapters and glossary names using similarity search.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class EmbeddingSearchController {

    private final EmbeddingService embeddingService;

    /**
     * Search for chapters similar to the given text within a specific field.
     */
    @GetMapping("/api/books/{bookId}/search/chapters")
    public ResponseEntity<List<ChapterSimilarityResult>> searchChaptersByField(
            @PathVariable String bookId,
            @RequestParam String query,
            @RequestParam(defaultValue = "") String field,
            @RequestParam(defaultValue = "0.8") double threshold,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("Received chapter field search request: query='{}', field='{}', threshold={}, limit={}",
                query, field, threshold, limit);

        try {
            List<ChapterSimilarityResult> results;
            if (Objects.isNull(field) || field.isBlank()) {
                results = embeddingService.findSimilarChaptersAcrossFields(bookId, query, threshold, limit);
            } else {
                results = embeddingService.findSimilarChaptersByField(bookId, query, field, threshold, limit);
            }
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Failed to search chapters by field: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Search for names similar to the given name text.
     */
    @GetMapping("/api/books/{bookId}/search/names")
    public ResponseEntity<List<NameSimilarityResult>> searchNames(
            @PathVariable String bookId,
            @RequestParam String query,
            @RequestParam(defaultValue = "0.8") double threshold,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("Received name search request: query='{}', threshold={}, limit={}",
                query, threshold, limit);

        try {
            var results = embeddingService.findByEmbeddingSimilarNames(bookId, query, threshold, limit);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Failed to search names: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

}
