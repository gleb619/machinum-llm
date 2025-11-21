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

    private static final String MISSING_MODE = "missing";
    private static final String TRANSLATION_MODE = "translated";
    private static final String CHAPTERS_MODE = "chapters";

    private final ChapterService chapterService;
    private final ChapterAnalysisService chapterAnalysisService;
    private final ChapterFacade chapterFacade;
    private final ChapterMapper chapterMapper;
    private final ChapterGlossaryService chapterGlossaryService;

    @GetMapping("/api/chapters")
    public ResponseEntity<List<Chapter>> getAllChapters(ChapterSearchRequest request) {
        log.info("Getting all chapters with search request: {}", request);
        var result = doSearch(request)
                .map(chapterMapper::checkForWarnings);

        return pageResponse(result);
    }

    @GetMapping("/api/chapters/{id}")
    public ResponseEntity<Chapter> getChapterById(@PathVariable("id") String id) {
        log.info("Getting chapter by ID: {}", id);
        return chapterService.getChapterById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/api/chapters")
    public ResponseEntity<Chapter> createChapter(@RequestBody Chapter chapter) {
        log.info("Creating new chapter: {}", chapter);
        return ResponseEntity.ok(chapterService.createChapter(chapter));
    }

    @PutMapping("/api/chapters/{id}")
    public ResponseEntity<Chapter> updateChapter(@PathVariable("id") String id, @RequestBody Chapter updatedChapter) {
        log.info("Updating chapter with ID: {} and data: {}", id, updatedChapter);
        if (!Objects.equals(id, updatedChapter.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .build();
        }

        return ResponseEntity.ok(chapterFacade.updateChapter(updatedChapter));
    }

    @DeleteMapping("/api/chapters/{id}")
    public ResponseEntity<Void> deleteChapter(@PathVariable("id") String id) {
        log.info("Deleting chapter with ID: {}", id);
        chapterService.deleteChapter(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/books/{bookId}/chapters-summary")
    public ResponseEntity<ChapterDataSummary> getChapterSummary(@PathVariable("bookId") String bookId) {
        log.info("Received request for chapter summary, bookId: {}", bookId);
        return withCacheControl(chapterAnalysisService.getChapterDataSummary(bookId));
    }

    @GetMapping("/api/books/{bookId}/chapters-heatmap")
    public ResponseEntity<ChapterHeatmapData> getChapterHeatmap(@PathVariable("bookId") String bookId, @RequestParam(value = "forceUpdate", defaultValue = "false") Boolean forceUpdate) {
        log.info("Received request for chapter heatmap, bookId: {}", bookId);

        if (forceUpdate) {
            chapterAnalysisService.clearHeatmapCache(bookId);
            chapterAnalysisService.getChapterHeatmapData(bookId);
        }

        return withCacheControl(chapterAnalysisService.getChapterHeatmapData(bookId));
    }

    @GetMapping("/api/books/{bookId}/chapters-titles")
    public ResponseEntity<List<Chapter>> getChaptersTitles(
            @PathVariable("bookId") String bookId,
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestParam(name = "missingTranslation", defaultValue = "false") boolean missingTranslation,
            @RequestParam(name = "aberrationTranslation", defaultValue = "false") boolean aberrationTranslation) {

        log.info("Fetching chapters titles for bookId: {}", bookId);
        var result = doSearchChaptersTitles(bookId, page, size, missingTranslation, aberrationTranslation);

        return pageResponse(result);
    }

    @PatchMapping("/api/chapters/{id}/title")
    public ResponseEntity<Chapter> updateTitleFields(
            @PathVariable("id") String id,
            @RequestBody Chapter updatedChapter) {

        log.info("Updating title fields for chapter with ID: {}, from title: {} to translated title: {}",
                id, updatedChapter.getTitle(), updatedChapter.getTranslatedTitle());

        chapterService.updateTitleFields(id, updatedChapter.getTitle(), updatedChapter.getTranslatedTitle());

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/books/{bookId}/glossary")
    public ResponseEntity<List<ChapterGlossary>> getBookGlossary(
            @PathVariable("bookId") String bookId,
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestParam(name = "translationMode", defaultValue = "all") String translationMode,
            @RequestParam(name = "fromChapter", required = false) Integer fromChapterNumber,
            @RequestParam(name = "toChapter", required = false) Integer toChapterNumber) {
        log.info("Fetching chapters names for bookId: {}", bookId);

        Page<ChapterGlossary> result = switch (translationMode) {
            case MISSING_MODE ->
                    chapterGlossaryService.findBookTranslatedGlossary(bookId, false, PageRequest.of(page, size));
            case TRANSLATION_MODE ->
                    chapterGlossaryService.findBookTranslatedGlossary(bookId, true, PageRequest.of(page, size));
            case CHAPTERS_MODE ->
                    chapterGlossaryService.findBookTranslatedGlossary(bookId, fromChapterNumber, toChapterNumber, PageRequest.of(page, size));
            case null, default -> chapterGlossaryService.findBookGlossary(bookId, PageRequest.of(page, size));
        };

        return pageResponse(result);
    }

    @PostMapping("/api/books/{bookId}/glossary/search")
    public ResponseEntity<List<ChapterGlossary>> searchGlossary(@PathVariable("bookId") String bookId,
                                                                @RequestBody GlossarySearchRequest request) {

        log.info("Searching glossary for bookId: {}, request: {}", bookId, request);
        var result = chapterGlossaryService.searchGlossary(bookId, request);

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/api/chapters/{chapterId}/glossary")
    public ResponseEntity<Chapter> updateGlossary(
            @PathVariable("chapterId") String chapterId,
            @RequestBody ChapterGlossary updatedChapterGlossary) {

        log.info("Updating glossary for chapter with ID: {}", chapterId);
        chapterFacade.updateGlossary(chapterId, updatedChapterGlossary);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/books/{bookId}/replace-text")
    public void replaceText(@PathVariable("bookId") String bookId, @RequestBody ReplaceTextRequest request) {
        log.info("Replacing text in bookId: {}, search: {}, replacement: {}",
                bookId, request.search(), request.replacement());
        chapterGlossaryService.replaceText(bookId, request.search(), request.replacement());
    }

    @PostMapping("/api/chapters/{chapterId}/replace-text-by-id")
    public void replaceTextById(@PathVariable("chapterId") String chapterId, @RequestBody ReplaceTextByIdRequest request) {
        log.info("Replacing text by id in chapterId: {}, search: {}, replacement: {}",
                chapterId, request.search(), request.replacement());
        chapterGlossaryService.replaceTextById(chapterId, request.search(), request.replacement());
    }

    @PostMapping("/api/chapters/{chapterId}/replace-text-for-column")
    public void replaceTextForColumn(@PathVariable("chapterId") String chapterId, @RequestBody ReplaceTextForColumnRequest request) {
        log.info("Replacing text for column in chapterId: {}, columnName: {}, search: {}, replacement: {}",
                chapterId, request.columnName(), request.search(), request.replacement());
        chapterGlossaryService.replaceTextForColumn(chapterId, request.columnName(), request.search(), request.replacement());
    }

    @PostMapping("/api/books/{bookId}/replace-summary")
    public void replaceSummary(@PathVariable("bookId") String bookId, @RequestBody ReplaceSummaryRequest request) {
        log.info("Replacing summary in bookId: {}, search: {}, replacement: {}",
                bookId, request.search(), request.replacement());
        chapterGlossaryService.replaceSummary(bookId, request.search(), request.replacement());
    }

    @PutMapping("/api/books/{bookId}/update-ru-name")
    public ResponseEntity<Void> updateGlossaryRuName(@PathVariable("bookId") String bookId, @RequestBody UpdateGlossaryRuNameRequest request) {
        log.info("Updating glossary ru name for bookId: {}, oldRuName: {}, newRuName: {}, returnIds: {}, nameFilter: {}",
                bookId, request.oldRuName(), request.newRuName(), request.returnIds(), request.nameFilter());
        chapterGlossaryService.updateGlossaryRuName(bookId, request.oldRuName(), request.newRuName(), request.returnIds(), request.nameFilter());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/books/{bookId}/preview-update-ru-name")
    public ResponseEntity<List<String>> previewUpdateGlossaryRuName(@PathVariable("bookId") String bookId, @RequestBody UpdateGlossaryRuNameRequest request) {
        log.info("Previewing glossary ru name update for bookId: {}, oldRuName: {}, newRuName: {}, nameFilter: {}",
                bookId, request.oldRuName(), request.newRuName(), request.nameFilter());
        List<String> chapterIds = chapterGlossaryService.updateGlossaryRuName(bookId, request.oldRuName(), request.newRuName(), true, request.nameFilter());
        return ResponseEntity.ok(chapterIds != null ? chapterIds : List.of());
    }

    @PostMapping("/api/tokens")
    public ResponseEntity<Integer> tokens(@RequestBody String text) {
        return ResponseEntity.ok(TextUtil.countTokens(text));
    }

    /* ============= */

    private Page<Chapter> doSearch(ChapterSearchRequest request) {
        if (Objects.nonNull(request.getChapterId())) {
            return chapterService.findById(request.getChapterId())
                    .map(chapter -> (Page<Chapter>) new PageImpl<>(List.of(chapter)))
                    .orElseGet(Page::empty);
        }

        if (Objects.nonNull(request.getBookId())) {
            if (TextUtil.isNotEmpty(request.getQuery()) && TextUtil.isNotEmpty(request.getQueryNames())) {
                return chapterService.findByCombinedCriteria(request.getBookId(), request.getQuery(),
                        request.getQueryNames(), request.getPageRequest());
            } else if (TextUtil.isNotEmpty(request.getQuery())) {
                return chapterService.findByChapterText(request);
            } else if (TextUtil.isNotEmpty(request.getQueryNames())) {
                return chapterService.findByChapterNames(request);
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
        private boolean chapterMatchCase;
        private boolean chapterWholeWord;
        private boolean chapterRegex;
        private boolean namesMatchCase;
        private boolean namesWholeWord;
        private boolean namesRegex;

        public PageRequest getPageRequest() {
            return PageRequest.of(page, size);
        }

    }

    public record ReplaceTextRequest(String search, String replacement) {
    }

    public record ReplaceTextByIdRequest(String search, String replacement) {
    }

    public record ReplaceTextForColumnRequest(String columnName, String search, String replacement) {
    }

    public record ReplaceSummaryRequest(String search, String replacement) {
    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class GlossarySearchRequest {

        private String searchText;
        private String fuzzyText;
        private Integer chapterStart = 1;
        private Integer chapterEnd = 999999;
        private Integer topK = 20;
        private Float minScore = 0.1f;

    }

    public record UpdateGlossaryRuNameRequest(String oldRuName, String newRuName, Boolean returnIds,
                                              String nameFilter) {
    }

}
