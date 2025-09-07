package machinum.repository;

import machinum.entity.ChapterHistoryEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChapterHistoryRepository extends JpaRepository<ChapterHistoryEntity, String> {

    List<ChapterHistoryEntity> findByChapterInfoIdAndFieldNameIn(String chapterInfoId, List<String> fieldNames, Sort sort);

    List<ChapterHistoryEntity> findByChapterInfoIdAndFieldName(String chapterInfoId, String fieldName, Sort sort);

    List<ChapterHistoryEntity> findByChapterInfoIdAndFieldNameAndNumberLessThanEqual(String chapterInfoId, String fieldName, Integer number, Sort sort);

}
