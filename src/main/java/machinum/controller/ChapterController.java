package machinum.controller;

import machinum.model.Chapter;
import machinum.service.ChapterFacade;
import machinum.service.ChapterService;
import machinum.util.TextUtil;
import lombok.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/chapters")
@RequiredArgsConstructor
public class ChapterController {

    private final ChapterService chapterService;
    private final ChapterFacade chapterFacade;

    @GetMapping
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

    @GetMapping("/{id}")
    public ResponseEntity<Chapter> getChapterById(@PathVariable String id) {
        return chapterService.getChapterById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Chapter> createChapter(@RequestBody Chapter chapter) {
        return ResponseEntity.ok(chapterService.createChapter(chapter));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Chapter> updateChapter(@PathVariable String id, @RequestBody Chapter updatedChapter) {
        if (!Objects.equals(id, updatedChapter.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .build();
        }

        return chapterService.updateChapter(id, updatedChapter)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChapter(@PathVariable String id) {
        chapterService.deleteChapter(id);
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
            } else if (request.isEnglishText() || request.isSuspiciousOriginalWords() || request.isSuspiciousTranslatedWords()) {
                return chapterFacade.getSuspiciousChapters(request.getBookId(), request.isEnglishText(),
                        request.isSuspiciousOriginalWords(), request.isSuspiciousTranslatedWords(), request.getPageRequest());
            } else {
                return chapterService.loadBookChapters(request.getBookId(), request.getPageRequest());
            }
        } else {
            return chapterService.getAllChapters(request.getPageRequest());
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
        private List<String> userFilters;
        private int page = 0;
        private int size = 10;

        public PageRequest getPageRequest() {
            return PageRequest.of(page, size);
        }

    }

}