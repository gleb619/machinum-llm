package machinum.service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.controller.LineController;
import machinum.controller.LineController.FindSimilarRequest;
import machinum.controller.LineController.RemoveLineRequest;
import machinum.converter.LineMapper;
import machinum.entity.LineView;
import machinum.exception.AppIllegalStateException;
import machinum.model.Chapter;
import machinum.model.Line;
import machinum.repository.LineDao;
import machinum.repository.LineRepository;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.async.AsyncHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.db.DbHelper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static machinum.listener.ChapterEntityListener.ChapterInfoConstants.CLEAN_TEXT;
import static machinum.listener.ChapterEntityListener.ChapterInfoConstants.TRANSLATED_TEXT;
import static machinum.util.JavaUtil.md5;

@Slf4j
@Service
@RequiredArgsConstructor
public class LineService {

    public static final List<String> SUPPORTED_FIELDS = Arrays.asList(CLEAN_TEXT, TRANSLATED_TEXT);

    private final LineRepository repository;
    private final LineMapper mapper;
    private final LineDao lineDao;
    private final LinesInfoDao linesInfoDao;
    private final ChapterService chapterService;
    private final AsyncHelper asyncHelper;
    private final DbHelper dbHelper;


    @Transactional(readOnly = true)
    public Page<Line> getLinesByBookId(@NonNull String bookId, PageRequest pageRequest) {
        return repository.findByBookId(bookId, pageRequest).map(mapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<Line> findSimilarForBook(String bookId, FindSimilarRequest request, PageRequest pageRequest) {
        for (String field : request.fields()) {
            if (!SUPPORTED_FIELDS.contains(field)) {
                throw new AppIllegalStateException("Unknown field: " + field);
            }
        }

        var ids = new HashSet<String>();
        long textTotal = 0;
        long translatedTextTotal = 0;

        if (request.isStrictSearch()) {
            String searchPattern = request.useRegex() != null && request.useRegex() ? request.line() : null;

            for (String field : request.fields()) {
                if (CLEAN_TEXT.equals(field)) {
                    Page<String> page = repository.findSimilarOriginalLines(bookId,
                            request.line(),
                            searchPattern,
                            request.matchCase(),
                            request.matchWholeWord(),
                            request.useRegex(),
                            pageRequest
                    );
                    textTotal = page.getTotalElements();
                    ids.addAll(page.getContent());
                } else if (TRANSLATED_TEXT.equals(field)) {
                    Page<String> page = repository.findSimilarTranslatedLines(bookId,
                            request.line(),
                            searchPattern,
                            request.matchCase(),
                            request.matchWholeWord(),
                            request.useRegex(),
                            pageRequest
                    );
                    translatedTextTotal = page.getTotalElements();
                    ids.addAll(page.getContent());
                }
            }
        } else {
            for (String field : request.fields()) {
                if (CLEAN_TEXT.equals(field)) {
                    ids.addAll(lineDao.findOriginalSimilarLineForBook(bookId, request.line()));
                } else if (TRANSLATED_TEXT.equals(field)) {
                    ids.addAll(lineDao.findTranslatedSimilarLineForBook(bookId, request.line()));
                }
            }
        }

        long totalItems = Math.max(textTotal, translatedTextTotal);
        return new PageImpl<>(mapper.toDto(repository.findAllById(ids)), pageRequest, totalItems);
    }

    @Transactional(readOnly = true)
    public Page<Line> findSimilarForChapter(String chapterId, FindSimilarRequest request, PageRequest pageRequest) {
        for (String field : request.fields()) {
            if (!SUPPORTED_FIELDS.contains(field)) {
                throw new AppIllegalStateException("Unknown field: " + field);
            }
        }

        var ids = new HashSet<String>();
        long textTotal = 0;
        long translatedTextTotal = 0;

        if (request.isStrictSearch()) {
            String searchPattern = request.useRegex() != null && request.useRegex() ? request.line() : null;
            String bookId = lineDao.findBookIdByChapter(chapterId);

            for (String field : request.fields()) {
                if (CLEAN_TEXT.equals(field)) {
                    Page<String> page = repository.findSimilarOriginalLines(bookId,
                            request.line(),
                            searchPattern,
                            request.matchCase(),
                            request.matchWholeWord(),
                            request.useRegex(),
                            pageRequest
                    );
                    textTotal = page.getTotalElements();
                    ids.addAll(page.getContent());
                } else if (TRANSLATED_TEXT.equals(field)) {
                    Page<String> page = repository.findSimilarTranslatedLines(bookId,
                            request.line(),
                            searchPattern,
                            request.matchCase(),
                            request.matchWholeWord(),
                            request.useRegex(),
                            pageRequest
                    );
                    translatedTextTotal = page.getTotalElements();
                    ids.addAll(page.getContent());
                }
            }
        } else {
            for (String field : request.fields()) {
                if (CLEAN_TEXT.equals(field)) {
                    ids.addAll(lineDao.findOriginalSimilarLineForChapter(chapterId, request.line()));
                } else if (TRANSLATED_TEXT.equals(field)) {
                    ids.addAll(lineDao.findTranslatedSimilarLineForChapter(chapterId, request.line()));
                }
            }
        }

        long totalItems = Math.max(textTotal, translatedTextTotal);
        return new PageImpl<>(mapper.toDto(repository.findAllById(ids)), pageRequest, totalItems);
    }

    @Transactional(readOnly = true)
    public List<Line> getLinesByChapterId(@NonNull String chapterId) {
        return mapper.toDto(repository.findByChapterId(chapterId));
    }

    /**
     * @param request
     * @deprecated use {@link #removeAllLines(RemoveLineRequest)}
     */
    @Transactional
    @Deprecated(forRemoval = true)
    public void deleteLine(RemoveLineRequest request) {
        eraseAllLines(request.ids(), request.fields());
    }

    @Transactional
    public void removeAllLines(RemoveLineRequest request) {
        eraseAllLines(request.ids(), request.fields());
    }

    @Transactional(readOnly = true)
    public Page<String> getEnglishInTranslatedChapterIds(@NonNull String bookId, PageRequest request) {
        return repository.findEnglishInTranslated(bookId, request);
    }

    @Transactional(readOnly = true)
    public Page<String> getSuspiciousInOriginalChapterIds(@NonNull String bookId, PageRequest request) {
        return repository.findSuspiciousInOriginal(bookId, request);
    }

    @Transactional(readOnly = true)
    public Page<String> getSuspiciousInTranslatedChapterIds(@NonNull String bookId, PageRequest request) {
        return repository.findSuspiciousInTranslated(bookId, request);
    }

    @Transactional
    public void updateChapterLine(@NonNull Line updatedLine) {
        doUpdateChapterLine(updatedLine);
    }

    @SneakyThrows
    public void replaceLineContent(LineController.ReplaceLineRequest request) {
        if (request.ids() == null || request.ids().isEmpty()) {
            return;
        }

        var hasChanges = new AtomicBoolean(false);
        var lines = new AtomicReference<List<Line>>();

        dbHelper.doInNewTransaction(() -> {
            // Get all lines to process
            lines.set(repository.findAllById(request.ids())
                    .stream()
                    .map(mapper::toDto)
                    .toList());

            if (lines.get().isEmpty()) {
                return;
            }

            // Group lines by chapter ID to minimize chapter updates
            var linesByChapter = lines.get().stream()
                    .sorted(Comparator.comparing(Line::getNumber))
                    .collect(Collectors.groupingBy(Line::getChapterId, LinkedHashMap::new, Collectors.toList()));


            for (var entry : linesByChapter.entrySet()) {
                var chapterId = entry.getKey();
                var chapterLines = entry.getValue();
                // Sort lines within each chapter by lineIndex
                chapterLines.sort(Comparator.comparing(Line::getLineIndex));

                // Process each line in the chapter
                hasChanges.set(hasChanges.get() | updateChapterLines(chapterId, chapterLines,
                        (line, originalLines, translatedLines, lineIndex) -> {
                            // Get current line content
                            var currentOriginalLine = originalLines.get(lineIndex);
                            var currentTranslatedLine = translatedLines.get(lineIndex);

                            // Replace original line if needed
                            if (request.find() != null && currentOriginalLine.contains(request.find())) {
                                originalLines.set(lineIndex, currentOriginalLine.replace(request.find(), request.replace()));
                            }

                            // Replace translated line if needed
                            if (line.getTranslatedLine() != null &&
                                    request.find() != null &&
                                    currentTranslatedLine.contains(request.find())) {
                                translatedLines.set(lineIndex, currentTranslatedLine.replace(request.find(), request.replace()));
                            }
                        }));
            }
        });

        // Refresh materialized view if changes were made
        if (hasChanges.get()) {
            log.debug("Perform material view refresh");
            asyncHelper.inNewTransaction(() -> lines.get().stream()
                                    .collect(Collectors.groupingBy(Line::getBookId)).entrySet().stream()
                                    .allMatch(entry -> linesInfoDao.refreshView(entry.getKey(), entry.getValue().stream()
                                            .map(Line::getNumber)
                                            .collect(Collectors.toList()))),
                            (r, throwable) -> log.debug("MatView update status: {}", r ? "success" : "failed"))
                    .get(1, TimeUnit.HOURS);
        }
    }

    /* ============= */

    /**
     * Erases multiple lines from chapter_info by setting both text and translated_text to empty
     * for the given list of line ids from lines_info materialized view
     *
     * @param lineIds list of IDs of the lines from lines_info view to erase
     * @param fields  fields to erase
     * @return map containing the results of the operation for each lineId
     */
    @SneakyThrows
    private void eraseAllLines(List<String> lineIds, List<String> fields) {
        if (lineIds == null || lineIds.isEmpty() || fields == null || fields.isEmpty()) {
            log.warn("No data provided for erasure");
            return;
        }
        for (String field : fields) {
            if (!SUPPORTED_FIELDS.contains(field)) {
                throw new AppIllegalStateException("Unknown field: " + field);
            }
        }

        // Query for all lines at once
        List<LineView> linesList = repository.findAllById(lineIds);

        // Group line IDs by chapter ID to optimize database operations
        var linesByChapter = linesList.stream()
                .map(mapper::toDto)
                .sorted(Comparator.comparing(Line::getNumber))
                .collect(Collectors.groupingBy(Line::getChapterId, LinkedHashMap::new, Collectors.toList()));

        var results = new ArrayList<Boolean>();

        // Process each chapter's lines
        for (var entry : linesByChapter.entrySet()) {
            String chapterId = entry.getKey();
            List<Line> lines = entry.getValue();
            // Sort lines within each chapter by lineIndex
            lines.sort(Comparator.comparing(Line::getLineIndex));

            results.add(updateChapterLines(chapterId, lines, (line, originalLines, translatedLines, lineIndex) -> {
                // Verify hash before modifying
                String currentOriginalLine = originalLines.get(lineIndex);
                String currentTranslatedLine = translatedLines.get(lineIndex);

                if (fields.contains(CLEAN_TEXT)) {
                    checkHash(line.getOriginalLine(), currentOriginalLine);
                    originalLines.set(lineIndex, "");
                }

                if (fields.contains(TRANSLATED_TEXT)) {
                    checkHash(line.getTranslatedLine(), currentTranslatedLine);
                    translatedLines.set(lineIndex, "");
                }
            }));
        }

        if(results.stream().anyMatch(b -> b)) {
            asyncHelper.inNewTransaction(() -> linesList.stream()
                                    .collect(Collectors.groupingBy(LineView::getBookId)).entrySet().stream()
                                    .allMatch(entry -> linesInfoDao.refreshView(entry.getKey(), entry.getValue().stream()
                                            .map(LineView::getNumber)
                                            .collect(Collectors.toList()))),
                            (r, throwable) -> log.debug("MatView update status: {}", r ? "success" : "failed"))
                    .get(1, TimeUnit.HOURS);
        }
    }

    @SneakyThrows
    private void doUpdateChapterLine(Line updatedLine) {
        log.debug("Updating line with ID: {}", updatedLine.getId());

        // Get current line from materialized view
        Line currentLine = repository.findById(updatedLine.getId())
                .map(mapper::toDto)
                .orElseThrow(() -> new RuntimeException("Line not found with id: " + updatedLine.getId()));

        boolean result = false;
        try {
            result = updateChapterLines(currentLine.getChapterId(), Collections.singletonList(currentLine),
                    (line, originalLines, translatedLines, lineIndex) -> {
                        // Verify hash before modifying
                        String currentOriginalLine = originalLines.get(lineIndex);
                        String currentTranslatedLine = translatedLines.get(lineIndex);

                        if (updatedLine.getOriginalLine() != null && line.getOriginalLine() != null) {
                            checkHash(line.getOriginalLine(), currentOriginalLine);
                            originalLines.set(lineIndex, updatedLine.getOriginalLine());
                        }

                        if (updatedLine.getTranslatedLine() != null && line.getTranslatedLine() != null) {
                            checkHash(line.getTranslatedLine(), currentTranslatedLine);
                            translatedLines.set(lineIndex, updatedLine.getTranslatedLine());
                        }
                    });
        } catch (LineHashMismatchException e) {
            asyncHelper.inNewTransaction(() ->
                            linesInfoDao.refreshView(currentLine.getBookId(), currentLine.getNumber()),
                    (r, throwable) -> log.debug("MatView update status: {}", r ? "success" : "failed"));
            ExceptionUtils.rethrow(e);
        }

        if(result) {
            asyncHelper.inNewTransaction(() ->
                            linesInfoDao.refreshView(currentLine.getBookId(), currentLine.getNumber()),
                    (r, throwable) -> log.debug("MatView update status: {}", r ? "success" : "failed"));
        }
    }

    /**
     * Common method to update chapter lines with a provided operation
     *
     * @param chapterId     the chapter ID
     * @param lines         list of lines to process
     * @param lineOperation operation to perform on each line
     * @return
     */
    private boolean updateChapterLines(String chapterId, List<Line> lines, LineOperation lineOperation) {
        // Fetch the chapter entity
        Chapter chapter = chapterService.getById(chapterId);

        // Get current content
        String currentOriginalText = chapter.getText();
        String currentTranslatedText = chapter.getTranslatedText();

        // Split the text into lines
        List<String> originalLines = splitTextIntoLines(currentOriginalText);
        List<String> translatedLines = splitTextIntoLines(currentTranslatedText);

        boolean hasChanges = false;

        // Process each line
        for (Line line : lines) {
            int lineIndex = line.getLineIndex() - 1; // Convert to 0-based index

            // Ensure the lists have enough elements for the line index
            ensureCapacity(originalLines, line.getLineIndex());
            ensureCapacity(translatedLines, line.getLineIndex());

            if (lineIndex < 0 || lineIndex >= Math.max(originalLines.size(), translatedLines.size())) {
                log.warn("Line index {} is out of bounds for chapter {}", lineIndex + 1, chapterId);
                continue;
            }

            try {
                lineOperation.apply(line, originalLines, translatedLines, lineIndex);
                hasChanges = true;
            } catch (LineHashMismatchException e) {
                log.error("Hash mismatch for line {}", line.getId(), e);
                throw e;
            }
        }

        // Save changes if any
        if (hasChanges) {
            String updatedOriginalText = String.join("\n", originalLines);
            String updatedTranslatedText = String.join("\n", translatedLines);

            chapter = chapter.toBuilder()
                    .text(updatedOriginalText)
                    .translatedText(updatedTranslatedText)
                    .build();

            chapterService.save(chapter);
        }

        return hasChanges;
    }

    private List<String> splitTextIntoLines(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(text.split("\n")));
    }

    private void ensureCapacity(List<String> lines, int lineIndex) {
        // Ensure the list has enough capacity
        while (lines.size() < lineIndex) {
            lines.add("");
        }
    }

    private void checkHash(String newLine, String expectedLine) {
        String newHash = md5(newLine);
        String expectedHash = md5(expectedLine);

        if (!newHash.equals(expectedHash)) {
            throw new LineHashMismatchException(
                    "Line content has changed. Expected hash: %s, calculated hash: %s".formatted(expectedHash, newHash));
        }
    }

    @FunctionalInterface
    private interface LineOperation {

        void apply(Line line, List<String> originalLines, List<String> translatedLines, int lineIndex);

    }

    @ResponseStatus(value = HttpStatus.CONFLICT, reason = "Line content has changed since you fetched it. Please refresh and try again.")
    public static class LineHashMismatchException extends RuntimeException {

        public LineHashMismatchException(String message) {
            super(message);
        }

    }

}
