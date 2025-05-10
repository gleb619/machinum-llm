package machinum.service;

import com.github.difflib.DiffUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.converter.ChapterHistoryMapper;
import machinum.model.ChapterHistory;
import machinum.repository.ChapterHistoryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static machinum.listener.ChapterEntityListener.TRACKED_FIELDS;
import static org.springframework.data.domain.Sort.Order.asc;
import static org.springframework.data.domain.Sort.Order.desc;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterHistoryService {

    private final ChapterHistoryRepository chapterHistoryRepository;
    private final ChapterHistoryMapper chapterHistoryMapper;


    @Transactional
    public void save(ChapterHistory history) {
        log.debug("Prepare to save history for chapter: {}", history);
        chapterHistoryRepository.save(chapterHistoryMapper.toEntity(history));
    }

    /**
     * Retrieves all patches for all fields
     */
    @Transactional(readOnly = true)
    public List<ChapterHistory> getPatches(String chapterId) {
        log.debug("Prepare to load all changes for chapter: {}", chapterId);
        var list = chapterHistoryRepository.findByChapterInfoIdAndFieldNameIn(chapterId, TRACKED_FIELDS, Sort.by(desc("fieldName"), asc("number")));
        return chapterHistoryMapper.toDto(list);
    }

    /**
     * Retrieves all patches for a specific chapter and field
     */
    @Transactional(readOnly = true)
    public List<ChapterHistory> getPatches(String chapterId, String fieldName) {
        log.debug("Prepare to load history for chapter's field: {}, field={}", chapterId, fieldName);
        var list = chapterHistoryRepository.findByChapterInfoIdAndFieldName(chapterId, fieldName, Sort.by(desc("fieldName"), asc("number")));
        return chapterHistoryMapper.toDto(list);
    }

    /**
     * Rebuilds the content at a specific point in time by applying patches
     */
    @SneakyThrows
    @Transactional(readOnly = true)
    public String rebuildContentAtPoint(String chapterId, String fieldName, Integer point) {
        log.debug("Restoring content for chapter's field: {}, field={}", chapterId, fieldName);
        var list = chapterHistoryRepository.findByChapterInfoIdAndFieldNameAndNumberLessThanEqual(chapterId, fieldName,
                point, Sort.by(asc("number"), asc("createdAt")));
        var infoHistory = chapterHistoryMapper.toDto(list);

        // Apply patches in order
        var contentLines = List.<String>of();

        for (var chapterInfoHistory : infoHistory) {
            // Parse the patch string (simplified - in real implementation you'd need a proper parser)
            var patch = chapterInfoHistory.getPatch();
            contentLines = DiffUtils.patch(contentLines, patch);
        }

        // Join lines back to content
        return String.join("\n", contentLines);
    }

}
