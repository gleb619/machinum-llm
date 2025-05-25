package machinum.repository;

import machinum.entity.ChapterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChapterReportRepository extends JpaRepository<ChapterEntity, String> {

    // Basic counts
    @Query("SELECT COUNT(c) FROM ChapterEntity c WHERE c.bookId = :bookId")
    Long countByBookId(@Param("bookId") String bookId);

    // Empty/null field counts
    @Query("SELECT COUNT(c) FROM ChapterEntity c WHERE c.bookId = :bookId AND (c.title IS NULL OR c.title = '')")
    Long countEmptyTitlesByBookId(@Param("bookId") String bookId);

    @Query("SELECT COUNT(c) FROM ChapterEntity c WHERE c.bookId = :bookId AND (c.translatedTitle IS NULL OR c.translatedTitle = '')")
    Long countEmptyTranslatedTitlesByBookId(@Param("bookId") String bookId);

    @Query("SELECT COUNT(c) FROM ChapterEntity c WHERE c.bookId = :bookId AND (c.text IS NULL OR c.text = '')")
    Long countEmptyTextsByBookId(@Param("bookId") String bookId);

    @Query("SELECT COUNT(c) FROM ChapterEntity c WHERE c.bookId = :bookId AND (c.translatedText IS NULL OR c.translatedText = '')")
    Long countEmptyTranslatedTextsByBookId(@Param("bookId") String bookId);

    @Query("SELECT COUNT(c) FROM ChapterEntity c WHERE c.bookId = :bookId AND (c.summary IS NULL OR c.summary = '')")
    Long countEmptySummariesByBookId(@Param("bookId") String bookId);

    @Query(value = "SELECT COUNT(ci0.id) FROM chapter_info ci0 WHERE ci0.book_id = :bookId AND (ci0.names IS NULL OR json_array_length(ci0.names) = 0)", nativeQuery = true)
    Long countEmptyNamesByBookId(@Param("bookId") String bookId);

    @Query(value = "SELECT COUNT(ci0.id) FROM chapter_info ci0 WHERE ci0.book_id = :bookId AND (ci0.warnings IS NULL OR jsonb_array_length(ci0.warnings) = 0)", nativeQuery = true)
    Long countEmptyWarningsByBookId(@Param("bookId") String bookId);

    // Completion percentage queries
    @Query("SELECT COUNT(c) FROM ChapterEntity c WHERE c.bookId = :bookId AND c.title IS NOT NULL AND c.title != ''")
    Long countCompleteTitlesByBookId(@Param("bookId") String bookId);

    @Query("SELECT COUNT(c) FROM ChapterEntity c WHERE c.bookId = :bookId AND c.translatedTitle IS NOT NULL AND c.translatedTitle != ''")
    Long countCompleteTranslatedTitlesByBookId(@Param("bookId") String bookId);


    @Query(value = """
            SELECT 
             ci0.id, 
             ci0.number, 
             ci0.title, 
             ci0.translated_title as translatedTitle, 
             ci0.text, 
             ci0.translated_text as translatedText, 
             ci0.summary, 
             json_array_length(ci0.names) as nameCount, 
             jsonb_array_length(ci0.warnings) as warningCount,
             ci0.warnings #>> '{}' as warningsRaw 
            FROM chapter_info ci0 
            WHERE ci0.book_id = :bookId 
            ORDER BY ci0.number""", nativeQuery = true)
    List<ChapterReadinessItemProjection> getChapterReadinessData(@Param("bookId") String bookId);

    /* ============= */

    interface ChapterReadinessItemProjection {

        String getId();

        Integer getNumber();

        String getTitle();

        String getTranslatedTitle();

        String getText();

        String getTranslatedText();

        String getSummary();

        Long getNameCount();

        Long getWarningCount();

        String getWarningsRaw();

    }

}
