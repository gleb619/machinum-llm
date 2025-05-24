package machinum.controller;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.model.Chapter;
import machinum.model.ChapterDataSummary;
import machinum.model.ChapterDataSummary.ChapterHeatmapData;
import machinum.service.ChapterAnalysisService;
import machinum.service.ChapterFacade;
import machinum.service.ChapterService;
import machinum.util.TextUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChapterController {

    private final ChapterService chapterService;
    private final ChapterAnalysisService chapterAnalysisService;
    private final ChapterFacade chapterFacade;

    @GetMapping("/api/chapters")
    public ResponseEntity<List<Chapter>> getAllChapters(ChapterSearchRequest request) {
        var result = doSearch(request);

        // Extract pagination metadata
        int totalPages = result.getTotalPages();
        long totalElements = result.getTotalElements();

        // Build headers with pagination info
        var headers = new HttpHeaders();
        headers.add("X-Total-Pages", String.valueOf(totalPages));
        headers.add("X-Total-Elements", String.valueOf(totalElements));
        headers.add("X-Current-Page", String.valueOf(request.getPage()));
        headers.add("X-Page-Size", String.valueOf(request.getSize()));

        return ResponseEntity.ok()
                .headers(headers)
                .body(result.getContent());
    }

    @GetMapping("/api/chapters/{id}")
    public ResponseEntity<Chapter> getChapterById(@PathVariable("id") String id) {
        return chapterService.getChapterById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/api/chapters")
    public ResponseEntity<Chapter> createChapter(@RequestBody Chapter chapter) {
        return ResponseEntity.ok(chapterService.createChapter(chapter));
    }

    @PutMapping("/api/chapters/{id}")
    public ResponseEntity<Chapter> updateChapter(@PathVariable("id") String id, @RequestBody Chapter updatedChapter) {
        if (!Objects.equals(id, updatedChapter.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .build();
        }

        return ResponseEntity.ok(chapterFacade.updateChapter(updatedChapter));
    }

    @DeleteMapping("/api/chapters/{id}")
    public ResponseEntity<Void> deleteChapter(@PathVariable("id") String id) {
        chapterService.deleteChapter(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/books/{bookId}/chapters-summary")
    public ResponseEntity<ChapterDataSummary> getChapterSummary(@PathVariable("bookId") String bookId) {
        log.info("Received request for chapter summary, bookId: {}", bookId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS))
                .body(chapterAnalysisService.getChapterDataSummary(bookId));
    }

    @GetMapping("/api/books/{bookId}/chapters-heatmap")
    public ResponseEntity<ChapterHeatmapData> getChapterHeatmap(@PathVariable("bookId") String bookId) {
        log.info("Received request for chapter heatmap, bookId: {}", bookId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS))
                .body(chapterAnalysisService.getChapterHeatmapData(bookId));
    }

    @GetMapping("/api/books/{bookId}/chapters-titles")
    public ResponseEntity<List<Chapter>> getChaptersTitles(
            @PathVariable("bookId") String bookId,
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestParam(value = "missingTranslation", defaultValue = "false") boolean missingTranslation,
            @RequestParam(value = "aberrationTranslation", defaultValue = "false") boolean aberrationTranslation) {
        log.debug("Fetching chapters titles for bookId: {}", bookId);
        var result = doSearchChaptersTitles(bookId, page, size, missingTranslation, aberrationTranslation);

        // Extract pagination metadata
        int totalPages = result.getTotalPages();
        long totalElements = result.getTotalElements();

        // Build headers with pagination info
        var headers = new HttpHeaders();
        headers.add("X-Total-Pages", String.valueOf(totalPages));
        headers.add("X-Total-Elements", String.valueOf(totalElements));
        headers.add("X-Current-Page", String.valueOf(page));
        headers.add("X-Page-Size", String.valueOf(size));

        return ResponseEntity.ok()
                .headers(headers)
                .body(result.getContent());
    }

    @PatchMapping("/api/chapters/{id}/title")
    public ResponseEntity<Chapter> updateTitleFields(
            @PathVariable("id") String id,
            @RequestBody Chapter updatedChapter) {

        chapterService.updateTitleFields(id, updatedChapter.getTitle(), updatedChapter.getTranslatedTitle());

        return ResponseEntity.noContent().build();
    }

    /* ============= */

    private Page<Chapter> doSearch(ChapterSearchRequest request) {
        if (Objects.nonNull(request.getBookId())) {
            if (TextUtil.isNotEmpty(request.getQuery()) && TextUtil.isNotEmpty(request.getQueryNames())) {
                return chapterService.findByCombinedCriteria(request.getBookId(), request.getQuery(),
                        request.getQueryNames(), request.getPageRequest());
            } else if (TextUtil.isNotEmpty(request.getQuery())) {
                return chapterService.findByChapterInfoFields(request.getBookId(), request.getQuery(), request.getPageRequest());
            } else if (TextUtil.isNotEmpty(request.getQueryNames())) {
                return chapterService.findByChapterInfoNames(request.getBookId(), request.getQueryNames(), request.getPageRequest());
            } else if (Objects.nonNull(request.getChapterNumber())) {
                return chapterService.loadBookChaptersForNumber(request.getBookId(), request.getChapterNumber())
                        .map(chapter -> List.of(chapter))
                        .map(list -> (Page<Chapter>) new PageImpl(list))
                        .orElse(Page.empty());
            } else if (request.isEnglishText() || request.isSuspiciousOriginalWords() || request.isSuspiciousTranslatedWords() || request.isWarnings()) {
                return chapterFacade.getSuspiciousChapters(request.getBookId(), request.isEnglishText(),
                        request.isSuspiciousOriginalWords(), request.isSuspiciousTranslatedWords(), request.getPageRequest(), request.isWarnings());
            } else {
                return chapterService.loadBookChapters(request.getBookId(), request.getPageRequest());
            }
        } else {
            return chapterService.getAllChapters(request.getPageRequest());
        }
    }

    private Page<Chapter> doSearchChaptersTitles(String bookId, int page, int size,
                                                 boolean missingTranslation, boolean aberrationTranslation) {
        PageRequest withSort = PageRequest.of(page, size).withSort(Sort.by("number"));
        if (missingTranslation || aberrationTranslation) {
            if (missingTranslation) {
                return chapterService.findMissingTitles(bookId, withSort);
            } else {
                return chapterService.findAberrationTitles(bookId, PageRequest.of(page, size));
            }
        } else {
            return chapterService.findTitles(bookId, withSort);
        }
    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class ChapterSearchRequest {

        private String query;
        private String queryNames;
        private String bookId;
        private Integer chapterNumber;
        private boolean englishText;
        private boolean suspiciousOriginalWords;
        private boolean suspiciousTranslatedWords;
        private boolean warnings;
        private List<String> userFilters;
        private int page = 0;
        private int size = 10;

        public PageRequest getPageRequest() {
            return PageRequest.of(page, size);
        }

    }

}