package machinum.service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.controller.LineController.FindSimilarRequest;
import machinum.controller.LineController.RemoveLineRequest;
import machinum.converter.LineMapper;
import machinum.entity.LineView;
import machinum.model.Chapter;
import machinum.model.Line;
import machinum.repository.LineDao;
import machinum.repository.LineRepository;
import org.springframework.async.AsyncHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.db.DbHelper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static machinum.listener.ChapterEntityListener.ChapterInfoConstants.CLEAN_TEXT;
import static machinum.listener.ChapterEntityListener.ChapterInfoConstants.TRANSLATED_TEXT;

@Slf4j
@Service
@RequiredArgsConstructor
public class LineService {

    public static final List<String> SUPPORTED_FIELDS = Arrays.asList(CLEAN_TEXT, TRANSLATED_TEXT);

    private final LineRepository repository;
    private final LineMapper mapper;
    private final LineDao lineDao;
    private final ChapterService chapterService;
    private final AsyncHelper asyncHelper;
    private final DbHelper dbHelper;

    @Transactional(readOnly = true)
    public List<Line> getAllLines() {
        return mapper.toDto(repository.findAll());
    }

    @Transactional(readOnly = true)
    public List<Line> getLinesByBookId(@NonNull String bookId) {
        return mapper.toDto(repository.findByBookId(bookId));
    }

    @Transactional(readOnly = true)
    public List<Line> findSimilarForBook(String bookId, FindSimilarRequest request) {
        for (String field : request.fields()) {
            if (!SUPPORTED_FIELDS.contains(field)) {
                throw new IllegalStateException("Unknown field: " + field);
            }
        }

        var ids = new HashSet<String>();
        for (String field : request.fields()) {
            if (CLEAN_TEXT.equals(field)) {
                ids.addAll(lineDao.findOriginalSimilarLineForBook(bookId, request.line()));
            } else if (TRANSLATED_TEXT.equals(field)) {
                ids.addAll(lineDao.findTranslatedSimilarLineForBook(bookId, request.line()));
            }
        }

        return mapper.toDto(repository.findAllById(ids));
    }

    @Transactional(readOnly = true)
    public List<Line> findSimilarForChapter(String chapterId, FindSimilarRequest request) {
        for (String field : request.fields()) {
            if (!SUPPORTED_FIELDS.contains(field)) {
                throw new IllegalStateException("Unknown field: " + field);
            }
        }

        var ids = new HashSet<String>();
        for (String field : request.fields()) {
            if (CLEAN_TEXT.equals(field)) {
                ids.addAll(lineDao.findOriginalSimilarLineForChapter(chapterId, request.line()));
            } else if (TRANSLATED_TEXT.equals(field)) {
                ids.addAll(lineDao.findTranslatedSimilarLineForChapter(chapterId, request.line()));
            }
        }

        return mapper.toDto(repository.findAllById(ids));
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

    /* ============= */

    /**
     * Erases multiple lines from chapter_info by setting both text and translated_text to empty
     * for the given list of line ids from lines_info materialized view
     *
     * @param lineIds list of IDs of the lines from lines_info view to erase
     * @param fields  fields to erase
     * @return map containing the results of the operation for each lineId
     */
    private void eraseAllLines(List<String> lineIds, List<String> fields) {
        if (lineIds == null || lineIds.isEmpty() || fields == null || fields.isEmpty()) {
            log.warn("No data provided for erasure");
            return;
        }
        for (String field : fields) {
            if (!SUPPORTED_FIELDS.contains(field)) {
                throw new IllegalStateException("Unknown field: " + field);
            }
        }

        // Query for all lines at once
        List<LineView> linesList = repository.findAllById(lineIds);

        // Group line IDs by chapter ID to optimize database operations
        Map<String, List<Line>> chapterLineMap = new HashMap<>();
        linesList.forEach(lineView -> {
            Line line = mapper.toDto(lineView);
            chapterLineMap.computeIfAbsent(line.getChapterId(), k -> new ArrayList<>()).add(line);
        });

        // Process each chapter's lines
        for (var entry : chapterLineMap.entrySet()) {
            String chapterId = entry.getKey();
            List<Line> lines = entry.getValue();

            updateChapterLines(chapterId, lines, (line, originalLines, translatedLines, lineIndex) -> {
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
            });
        }
    }

    private void doUpdateChapterLine(Line updatedLine) {
        log.debug("Updating line with ID: {}", updatedLine.getId());

        // Get current line from materialized view
        Line currentLine = repository.findById(updatedLine.getId())
                .map(mapper::toDto)
                .orElseThrow(() -> new RuntimeException("Line not found with id: " + updatedLine.getId()));

        updateChapterLines(currentLine.getChapterId(), Collections.singletonList(currentLine),
                (line, originalLines, translatedLines, lineIndex) -> {
                    // Verify hash before modifying
                    String currentOriginalLine = originalLines.get(lineIndex);
                    String currentTranslatedLine = translatedLines.get(lineIndex);

                    if (updatedLine.getOriginalLine() != null) {
                        checkHash(line.getOriginalLine(), currentOriginalLine);
                        originalLines.set(lineIndex, updatedLine.getOriginalLine());
                    }

                    if (updatedLine.getTranslatedLine() != null) {
                        checkHash(line.getTranslatedLine(), currentTranslatedLine);
                        translatedLines.set(lineIndex, updatedLine.getTranslatedLine());
                    }
                });
    }

    /**
     * Common method to update chapter lines with a provided operation
     *
     * @param chapterId     the chapter ID
     * @param lines         list of lines to process
     * @param lineOperation operation to perform on each line
     */
    private void updateChapterLines(String chapterId, List<Line> lines, LineOperation lineOperation) {
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
            asyncHelper.runAsync(() -> dbHelper.doInNewTransaction(lineDao::refreshMaterializedView));
        }
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

    /**
     * Calculate MD5 hash for line content, matching the logic used in materialized view
     */
    private String calculateHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to calculate hash", e);
        }
    }

    private void checkHash(String newLine, String expectedLine) {
        String newHash = calculateHash(newLine);
        String expectedHash = calculateHash(expectedLine);

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
