package machinum.listener;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import machinum.entity.ChapterEntity;
import machinum.model.Chapter;
import machinum.model.ChapterHistory;
import machinum.service.ChapterHistoryService;
import machinum.util.TextUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.async.AsyncHelper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.db.DbHelper;

import jakarta.persistence.PreUpdate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static machinum.listener.ChapterEntityListener.ChapterInfoConstants.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChapterEntityListener {

    public static final String FETCH_SQL = "SELECT * FROM chapter_info WHERE id = ?";

    public static final String FETCH_CHAPTER_ID_SQL = "SELECT id FROM chapter_info WHERE source_key = ? AND book_id = ?";

    public static final String REMOVE_HISTORY_FOR_CHAPTER_SQL = "DELETE FROM chapter_info_history WHERE chapter_info_id = ?";

    public static final String REMOVE_HISTORY_FOR_CHAPTER_BY_SOURCE_KEY_SQL = """
            DELETE FROM chapter_info_history 
            WHERE chapter_info_id IN (
                SELECT id FROM chapter_info WHERE source_key = ? AND book_id = ?
            )""";

    // Fields to track
    public static final List<String> TRACKED_FIELDS = Arrays.asList(CLEAN_TEXT, TRANSLATED_TEXT);

    private final JdbcTemplate jdbcTemplate;
    private final DbHelper dbHelper;
    private final AsyncHelper asyncHelper;
    private final ObjectProvider<ChapterHistoryService> chapterInfoHistoryService;


    public void preUpdate(List<ChapterEntity> entities) {
        entities.stream()
                .reduce(CompletableFuture.<Void>completedFuture(null),
                        (future, entity) -> future.thenCompose(v -> execute(entity, ChapterEntity.builder().build())),
                        (f1, f2) -> f1.thenCompose(v -> f2));
    }

    @PreUpdate
    public void preUpdate(ChapterEntity entity) {
        var originalEntity = fetchOriginEntity(entity);

        if (Objects.nonNull(originalEntity)) {
            execute(entity, originalEntity);
        }
    }

    public CompletableFuture<Void> execute(ChapterEntity entity, ChapterEntity originalEntity) {
        return asyncHelper.runAsync(() -> dbHelper.doInNewTransaction(() -> {
            try {
                doPreUpdate(entity, originalEntity);
            } catch (Exception e) {
                // Log the error but don't stop the update
                log.error("ERROR: ", e);
            }
        }));
    }

    @SneakyThrows
    public void trackChange(ChapterEntity entity, String fieldName) {
        var originalEntity = fetchOriginEntity(entity);

        if (Objects.nonNull(originalEntity)) {
            asyncHelper.runAsync(() -> dbHelper.doInNewTransaction(() -> {
                try {
                    trackChange(entity, originalEntity, fieldName);
                } catch (Exception e) {
                    // Log the error but don't stop the update
                    log.error("ERROR: ", e);
                }
            }));
        }
    }

    public void overrideChange(ChapterEntity entity, String fieldName) {
        var originalEntity = ChapterEntity.builder().build();

        asyncHelper.runAsync(() -> dbHelper.doInNewTransaction(() -> {
            try {
                removeHistory(entity);
                trackChange(entity, originalEntity, fieldName);
            } catch (Exception e) {
                // Log the error but don't stop the update
                log.error("ERROR: ", e);
            }
        }));
    }

    private void removeHistory(ChapterEntity entity) {
        String id = entity.getId();
        if (TextUtil.isNotEmpty(id)) {
            jdbcTemplate.update(REMOVE_HISTORY_FOR_CHAPTER_SQL, id);
        } else if (TextUtil.isNotEmpty(entity.getSourceKey()) && TextUtil.isNotEmpty(entity.getBookId())) {
            jdbcTemplate.update(REMOVE_HISTORY_FOR_CHAPTER_BY_SOURCE_KEY_SQL, entity.getSourceKey(), entity.getBookId());
        } else {
            throw new IllegalStateException("Can't remove history, unknown chapter: " + entity);
        }
    }


    /* ============= */

    private boolean doPreUpdate(ChapterEntity entity, ChapterEntity originalEntity) {
        // Check for changes in tracked fields
        for (String fieldName : TRACKED_FIELDS) {
            trackChange(entity, originalEntity, fieldName);
        }

        return false;
    }

    private ChapterEntity fetchOriginEntity(ChapterEntity entity) {
        // Fetch the original entity from the database
        return jdbcTemplate.queryForObject(
                FETCH_SQL,
                (rs, rowNum) -> {
                    ChapterEntity original = new ChapterEntity();
                    original.setId(rs.getString("id"));
                    original.setText(rs.getString("text"));
                    original.setTranslatedText(rs.getString("translated_text"));

                    return original;
                },
                entity.getId()
        );
    }

    @SneakyThrows
    private void trackChange(ChapterEntity entity, ChapterEntity originalEntity, String fieldName) {
        var patch = createPatch(entity, originalEntity, fieldName);
        if (patch == null) {
            return;
        }

        if (!patch.getDeltas().isEmpty()) {
            var entityId = parseEntityId(entity);

            chapterInfoHistoryService.getIfAvailable().save(ChapterHistory.builder()
                    .chapterInfoId(entityId)
                    .fieldName(fieldName)
                    .patch(patch)
                    .createdAt(LocalDateTime.now())
                    .build());
        }
    }

    private Patch<String> createInitialPatch(ChapterEntity originalEntity, String fieldName) {
        var value = ChangeUtil.resolveField(originalEntity, fieldName);
        return DiffUtils.diff(List.of(), List.of(value.split("\n")));
    }

    private Patch<String> createPatch(ChapterEntity entity, ChapterEntity originalEntity, String fieldName) {
        String originalValue = ChangeUtil.resolveField(originalEntity, fieldName);
        String newValue = ChangeUtil.resolveField(entity, fieldName);

        // Skip if values are the same or both null
        if ((originalValue == null && newValue == null) ||
                (originalValue != null && originalValue.equals(newValue))) {
            return null;
        }

        // Create diff patch
        var originalLines = originalValue != null
                ? Arrays.asList(originalValue.split("\n"))
                : List.<String>of();
        var newLines = newValue != null
                ? Arrays.asList(newValue.split("\n"))
                : List.<String>of();

        return DiffUtils.diff(originalLines, newLines);
    }

    private String parseEntityId(ChapterEntity entity) {
        if (TextUtil.isEmpty(entity.getId())) {
            if (TextUtil.isNotEmpty(entity.getSourceKey()) && TextUtil.isNotEmpty(entity.getBookId())) {
                log.debug("Prepare to load chapter's id by: sourceKey={}, bookId={}", entity.getSourceKey(), entity.getBookId());
                return jdbcTemplate.queryForObject(FETCH_CHAPTER_ID_SQL, String.class,
                        entity.getSourceKey(), entity.getBookId());
            } else {
                throw new IllegalStateException("Unknown chapter: " + entity);
            }
        } else {
            return entity.getId();
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ChangeUtil {

        public static String resolveField(Chapter chapter, String fieldName) {
            if (Objects.isNull(chapter)) {
                return null;
            }

            if (CLEAN_TEXT.equals(fieldName)) {
                return chapter.getText();
            } else if (TRANSLATED_TEXT.equals(fieldName)) {
                return chapter.getTranslatedText();
            }

            throw new IllegalArgumentException("Unknown field: " + fieldName);
        }

        public static String resolveField(ChapterEntity chapterInfo, String fieldName) {
            if (Objects.isNull(chapterInfo)) {
                return null;
            }

            if (CLEAN_TEXT.equals(fieldName)) {
                return chapterInfo.getText();
            } else if (TRANSLATED_TEXT.equals(fieldName)) {
                return chapterInfo.getTranslatedText();
            }

            throw new IllegalArgumentException("Unknown field: " + fieldName);
        }

    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ChapterInfoConstants {

        public static final String CLEAN_TEXT = "text";

        public static final String PROOFREAD_TEXT = "proofreadText";

        public static final String TRANSLATED_TEXT = "translatedText";

    }

}
