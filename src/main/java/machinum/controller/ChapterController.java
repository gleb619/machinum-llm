package machinum.controller;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.controller.core.ControllerTrait;
import machinum.converter.ChapterMapper;
import machinum.model.Chapter;
import machinum.model.ChapterDataSummary;
import machinum.model.ChapterDataSummary.ChapterHeatmapData;
import machinum.model.ChapterGlossary;
import machinum.service.ChapterAnalysisService;
import machinum.service.ChapterFacade;
import machinum.service.ChapterGlossaryService;
import machinum.service.ChapterService;
import machinum.util.TextUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChapterController implements ControllerTrait {

    private static final int MISSING_MODE = 0;
    private static final int TRANSLATION_MODE = 1;

    private final ChapterService chapterService;
    private final ChapterAnalysisService chapterAnalysisService;
    private final ChapterFacade chapterFacade;
    private final ChapterMapper chapterMapper;
    private final ChapterGlossaryService chapterGlossaryService;

    @GetMapping("/api/chapters")
    public ResponseEntity<List<Chapter>> getAllChapters(ChapterSearchRequest request) {
        var result = doSearch(request)
                .map(chapterMapper::checkForWarnings);

        return pageResponse(result);
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
        return withCacheControl(chapterAnalysisService.getChapterDataSummary(bookId));
    }

    @GetMapping("/api/books/{bookId}/chapters-heatmap")
    public ResponseEntity<ChapterHeatmapData> getChapterHeatmap(@PathVariable("bookId") String bookId) {
        log.info("Received request for chapter heatmap, bookId: {}", bookId);

        return withCacheControl(chapterAnalysisService.getChapterHeatmapData(bookId));
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

        return pageResponse(result);
    }

    @PatchMapping("/api/chapters/{id}/title")
    public ResponseEntity<Chapter> updateTitleFields(
            @PathVariable("id") String id,
            @RequestBody Chapter updatedChapter) {

        chapterService.updateTitleFields(id, updatedChapter.getTitle(), updatedChapter.getTranslatedTitle());

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/books/{bookId}/glossary")
    public ResponseEntity<List<ChapterGlossary>> getBookGlossary(
            @PathVariable("bookId") String bookId,
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestParam(value = "translationMode", defaultValue = "-1") int translationMode) {
        log.debug("Fetching chapters names for bookId: {}", bookId);

        Page<ChapterGlossary> result;
        if (translationMode == MISSING_MODE) {
            result = chapterGlossaryService.findBookTranslatedGlossary(bookId, false, PageRequest.of(page, size));
        } else if (translationMode == TRANSLATION_MODE) {
            result = chapterGlossaryService.findBookTranslatedGlossary(bookId, true, PageRequest.of(page, size));
        } else {
            result = chapterGlossaryService.findBookGlossary(bookId, PageRequest.of(page, size));
        }

        return pageResponse(result);
    }

    @PatchMapping("/api/chapters/{id}/glossary")
    public ResponseEntity<Chapter> updateGlossary(
            @PathVariable("id") String id,
            @RequestBody ChapterGlossary updatedChapterGlossary) {

        chapterFacade.updateGlossary(id, updatedChapterGlossary);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/tokens")
    public ResponseEntity<Integer> tokens(@RequestBody String text) {
        return ResponseEntity.ok(TextUtil.countTokens(text));
    }

    /* ============= */

    private Page<Chapter> doSearch(ChapterSearchRequest request) {
        if (Objects.nonNull(request.getChapterId())) {
            return chapterService.findById(request.getChapterId())
                    .map(chapter -> (Page) new PageImpl<>(List.of(chapter)))
                    .orElseGet(() -> Page.empty());
        }

        if (Objects.nonNull(request.getBookId())) {
            if (TextUtil.isNotEmpty(request.getQuery()) && TextUtil.isNotEmpty(request.getQueryNames())) {
                return chapterService.findByCombinedCriteria(request.getBookId(), request.getQuery(),
                        request.getQueryNames(), request.getPageRequest());
            } else if (TextUtil.isNotEmpty(request.getQuery())) {
                return chapterService.findByChapterInfoFields(request.getBookId(), request.getQuery(), request.getPageRequest());
            } else if (TextUtil.isNotEmpty(request.getQueryNames())) {
                return chapterService.findByChapterInfoNames(request.getBookId(), request.getQueryNames(), request.getPageRequest());
            } else if (Objects.nonNull(request.getChapterNumber())) {
                return chapterService.findByNumber(request.getBookId(), request.getChapterNumber())
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
        private String chapterId;
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