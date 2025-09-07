package machinum.listener;

import machinum.entity.ChapterHistoryEntity;
import jakarta.persistence.PrePersist;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChapterHistoryEntityListener {

    private final JdbcTemplate jdbcTemplate;

    @PrePersist
    public void preUpdate(ChapterHistoryEntity entity) {
        try {
            entity.setNumber(getNextHistoryNumber(entity.getChapterInfoId(), entity.getFieldName()));
        } catch (Exception e) {
            log.error("ERROR: ", e);
        }
    }

    private Integer getNextHistoryNumber(String chapterInfoId, String fieldName) {
        String sql = "SELECT COALESCE(COUNT(*), 0) FROM chapter_info_history WHERE chapter_info_id = ? and field_name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, chapterInfoId, fieldName);
        return count + 1;
    }

}
