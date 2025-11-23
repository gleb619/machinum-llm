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

    @Query(value = """
             SELECT COUNT(ci0.id) FROM chapter_info ci0 
             WHERE ci0.book_id = :bookId AND (ci0.names IS NOT NULL AND json_array_length(ci0.names) <> (
              SELECT COUNT(*)
              FROM json_array_elements(ci0.names) as elem
              WHERE elem->>'ruName' IS NOT NULL AND elem->>'ruName' != ''
            ))""", nativeQuery = true)
    Long countTranslatedNamesByBookId(@Param("bookId") String bookId);

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
             ci0.warnings #>> '{}' as warningsRaw ,
             CASE 
               WHEN json_array_length(ci0.names) = 0 THEN 0
               WHEN json_array_length(ci0.names) = (
                 SELECT COUNT(*)
                 FROM json_array_elements(ci0.names) as elem
                 WHERE elem->>'ruName' IS NOT NULL AND elem->>'ruName' != ''
               ) THEN 1
               ELSE 0
             END as translatedNameCount
            FROM chapter_info ci0 
            WHERE ci0.book_id = :bookId 
            ORDER BY ci0.number""", nativeQuery = true)
    List<ChapterReadinessItemProjection> getChapterReadinessData(@Param("bookId") String bookId);

    // Text fingerprint queries
    @Query(value = """
            SELECT
                ci0.number,
                CASE
                    WHEN LENGTH(ci0.text) > 50000 THEN (
                        -- Sample character count for large texts (first 10000 + middle 10000 + last 10000)
                        LENGTH(SUBSTRING(ci0.text, 1, 10000)) +
                        LENGTH(SUBSTRING(ci0.text, GREATEST(1, LENGTH(ci0.text)/2 - 5000), 10000)) +
                        LENGTH(SUBSTRING(ci0.text, GREATEST(1, LENGTH(ci0.text) - 10000), 10000))
                    ) / 3
                    ELSE LENGTH(ci0.text)
                END as sampled_chars
            FROM chapter_info ci0
            WHERE ci0.book_id = :bookId
            ORDER BY ci0.number
            """, nativeQuery = true)
    List<CharacterCountProjection> getChapterCharacterCounts(@Param("bookId") String bookId);

    @Query(value = """
            WITH chapter_names AS (
                SELECT
                    cg.chapter_id,
                    cg.number as chapter_number,
                    cg.name,
                    ROW_NUMBER() OVER (PARTITION BY cg.name ORDER BY cg.number) as name_occurrence
                FROM chapter_glossary cg
                WHERE cg.book_id = :bookId
            ),
            new_unique_names AS (
                SELECT
                    chapter_number,
                    COUNT(*) as new_names
                FROM chapter_names
                WHERE name_occurrence = 1
                GROUP BY chapter_number
            ),
            cumulative_unique AS (
                SELECT
                    chapter_number,
                    SUM(new_names) OVER (ORDER BY chapter_number) as cumulative
                FROM new_unique_names
            )
            SELECT
                c.number as chapter_number,
                COALESCE(n.new_names, 0) as new_unique_names,
                COALESCE(cu.cumulative, 0) as cumulative_unique_names
            FROM (SELECT DISTINCT number FROM chapter_info WHERE book_id = :bookId) c
            LEFT JOIN new_unique_names n ON c.number = n.chapter_number
            LEFT JOIN cumulative_unique cu ON c.number = cu.chapter_number
            ORDER BY c.number
            """, nativeQuery = true)
    List<UniqueNamesProjection> getChapterUniqueNamesProgress(@Param("bookId") String bookId);


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

        Long getTranslatedNameCount();

    }

    /* ============= */

    interface CharacterCountProjection {
        Integer getNumber();

        Integer getSampledChars();
    }

    interface UniqueNamesProjection {
        Integer getChapterNumber();

        Integer getNewUniqueNames();

        Integer getCumulativeUniqueNames();
    }

}
