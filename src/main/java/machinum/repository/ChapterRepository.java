package machinum.repository;

import machinum.entity.ChapterEntity;
import machinum.util.TextUtil;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChapterRepository extends JpaRepository<ChapterEntity, String> {

    Optional<ChapterEntity> findOneByBookIdAndTitleAndNumber(String bookId, String title, Integer number);

    @Modifying
    @Query("UPDATE ChapterEntity cie0 SET cie0.text = :text WHERE cie0.id = :id")
    void updateCleanText(@Param("id") String id, @Param("text") String cleanText);

    @Modifying
    @Query("UPDATE ChapterEntity cie0 SET cie0.summary = :context WHERE cie0.id = :id")
    void updateSummary(@Param("id") String id,
                       @Param("context") String context);

    @Modifying
    @Query("UPDATE ChapterEntity cie0 SET cie0.summary = :context, cie0.consolidatedSummary = :consolidatedContext WHERE cie0.id = :id")
    void updateSummary(@Param("id") String id,
                       @Param("context") String context,
                       @Param("consolidatedContext") String consolidatedContext);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE chapter_info SET names = cast(:glossary as json) WHERE id = :id", nativeQuery = true)
    void updateGlossary(@Param("id") String id, @Param("glossary") String glossary);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE chapter_info SET warnings = cast(:warningText as jsonb) WHERE id = :id", nativeQuery = true)
    void updateWarning(@Param("id") String id, @Param("warningText") String warningText);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE chapter_info SET clean_chunks = cast(:chunks as json) WHERE id = :id", nativeQuery = true)
    void updateCleanChunks(@Param("id") String id, @Param("chunks") String glossary);

    @Modifying
    @Query("UPDATE ChapterEntity cie0 SET cie0.proofreadText = :text WHERE cie0.id = :id")
    void updateProofreadText(@Param("id") String id, @Param("text") String proofreadText);

    @Modifying
    @Query("UPDATE ChapterEntity cie0 SET cie0.translatedText = :text WHERE cie0.id = :id")
    void updateTranslatedText(@Param("id") String id, @Param("text") String translatedText);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE chapter_info SET translated_chunks = cast(:chunks as json) WHERE id = :id", nativeQuery = true)
    void updateTranslatedChunks(@Param("id") String id, @Param("chunks") String glossary);

    @Modifying
    @Query("UPDATE ChapterEntity cie0 SET cie0.translatedTitle = :title WHERE cie0.id = :id")
    void updateTranslatedTitle(@Param("id") String id, @Param("title") String translatedTitle);

    @Modifying
    @Query("UPDATE ChapterEntity cie0 SET cie0.translatedTitle = :title WHERE cie0.bookId = :bookId AND cie0.sourceKey = :sourceKey")
    void updateTranslatedTitleByKey(@Param("bookId") String bookId, @Param("sourceKey") String sourceKey, @Param("title") String translatedTitle);

    @Modifying
    @Query("UPDATE ChapterEntity cie0 SET cie0.translatedText = :text WHERE cie0.bookId = :bookId AND cie0.sourceKey = :sourceKey")
    void updateTranslatedTextByKey(@Param("bookId") String bookId, @Param("sourceKey") String sourceKey, @Param("text") String translatedText);

    Page<ChapterEntity> findAllByBookId(String bookId, PageRequest pageRequest);

    Page<ChapterEntity> findAllByBookIdAndNumberBetween(String bookId,
                                                        @Param("startNumber") Integer startNumber,
                                                        @Param("endNumber") Integer endNumber,
                                                        PageRequest pageRequest);

    Optional<ChapterEntity> findOneByBookIdAndNumber(String bookId, Integer number);

    @Query("""
            SELECT cie0 
            FROM ChapterEntity cie0 
            WHERE cie0.bookId = :bookId 
            AND cie0.translatedText IS NOT NULL AND LENGTH(cie0.translatedText) > 0
            AND cie0.translatedTitle IS NOT NULL AND LENGTH(cie0.translatedTitle) > 0
            AND cie0.number >= :startNumber AND cie0.number <= :endNumber
            """)
    List<ChapterEntity> findReadyChapters(@Param("bookId") String bookId,
                                          @Param("startNumber") Integer startNumber,
                                          @Param("endNumber") Integer endNumber,
                                          Sort sort);

    @Query(value = """
            SELECT c.id FROM chapter_info c WHERE 
            c.book_id = :bookId AND (
                CASE 
                    WHEN :matchCase = true AND :wholeWord = true AND :regex = true THEN
                        (c.title ~ :searchTerm OR 
                         c.translated_title ~ :searchTerm OR 
                         c.raw_text ~ :searchTerm OR 
                         c.text ~ :searchTerm OR 
                         c.proofread_text ~ :searchTerm OR 
                         c.translated_text ~ :searchTerm OR 
                         c.fixed_translated_text ~ :searchTerm OR 
                         c.summary ~ :searchTerm OR 
                         c.consolidated_summary ~ :searchTerm)
                    WHEN :matchCase = true AND :wholeWord = true AND :regex = false THEN
                        (c.title ~ CONCAT('\\m', :searchTerm, '\\M') OR 
                         c.translated_title ~ CONCAT('\\m', :searchTerm, '\\M') OR 
                         c.raw_text ~ CONCAT('\\m', :searchTerm, '\\M') OR 
                         c.text ~ CONCAT('\\m', :searchTerm, '\\M') OR 
                         c.proofread_text ~ CONCAT('\\m', :searchTerm, '\\M') OR 
                         c.translated_text ~ CONCAT('\\m', :searchTerm, '\\M') OR 
                         c.fixed_translated_text ~ CONCAT('\\m', :searchTerm, '\\M') OR 
                         c.summary ~ CONCAT('\\m', :searchTerm, '\\M') OR 
                         c.consolidated_summary ~ CONCAT('\\m', :searchTerm, '\\M'))
                    WHEN :matchCase = true AND :wholeWord = false AND :regex = true THEN
                        (c.title ~ :searchTerm OR 
                         c.translated_title ~ :searchTerm OR 
                         c.raw_text ~ :searchTerm OR 
                         c.text ~ :searchTerm OR 
                         c.proofread_text ~ :searchTerm OR 
                         c.translated_text ~ :searchTerm OR 
                         c.fixed_translated_text ~ :searchTerm OR 
                         c.summary ~ :searchTerm OR 
                         c.consolidated_summary ~ :searchTerm)
                    WHEN :matchCase = true AND :wholeWord = false AND :regex = false THEN
                        (c.title LIKE CONCAT('%', :searchTerm, '%') OR 
                         c.translated_title LIKE CONCAT('%', :searchTerm, '%') OR 
                         c.raw_text LIKE CONCAT('%', :searchTerm, '%') OR 
                         c.text LIKE CONCAT('%', :searchTerm, '%') OR 
                         c.proofread_text LIKE CONCAT('%', :searchTerm, '%') OR 
                         c.translated_text LIKE CONCAT('%', :searchTerm, '%') OR 
                         c.fixed_translated_text LIKE CONCAT('%', :searchTerm, '%') OR 
                         c.summary LIKE CONCAT('%', :searchTerm, '%') OR 
                         c.consolidated_summary LIKE CONCAT('%', :searchTerm, '%'))
                    WHEN :matchCase = false AND :wholeWord = true AND :regex = true THEN
                        (c.title ~* :searchTerm OR 
                         c.translated_title ~* :searchTerm OR 
                         c.raw_text ~* :searchTerm OR 
                         c.text ~* :searchTerm OR 
                         c.proofread_text ~* :searchTerm OR 
                         c.translated_text ~* :searchTerm OR 
                         c.fixed_translated_text ~* :searchTerm OR 
                         c.summary ~* :searchTerm OR 
                         c.consolidated_summary ~* :searchTerm)
                    WHEN :matchCase = false AND :wholeWord = true AND :regex = false THEN
                        (c.title ~* CONCAT('\\m', :searchTerm, '\\M') OR 
                         c.translated_title ~* CONCAT('\\m', :searchTerm, '\\M') OR 
                         c.raw_text ~* CONCAT('\\m', :searchTerm, '\\M') OR 
                         c.text ~* CONCAT('\\m', :searchTerm, '\\M') OR 
                         c.proofread_text ~* CONCAT('\\m', :searchTerm, '\\M') OR 
                         c.translated_text ~* CONCAT('\\m', :searchTerm, '\\M') OR 
                         c.fixed_translated_text ~* CONCAT('\\m', :searchTerm, '\\M') OR 
                         c.summary ~* CONCAT('\\m', :searchTerm, '\\M') OR 
                         c.consolidated_summary ~* CONCAT('\\m', :searchTerm, '\\M'))
                    WHEN :matchCase = false AND :wholeWord = false AND :regex = true THEN
                        (c.title ~* :searchTerm OR 
                         c.translated_title ~* :searchTerm OR 
                         c.raw_text ~* :searchTerm OR 
                         c.text ~* :searchTerm OR 
                         c.proofread_text ~* :searchTerm OR 
                         c.translated_text ~* :searchTerm OR 
                         c.fixed_translated_text ~* :searchTerm OR 
                         c.summary ~* :searchTerm OR 
                         c.consolidated_summary ~* :searchTerm)
                    ELSE
                        (LOWER(c.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                         LOWER(c.translated_title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                         LOWER(c.raw_text) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                         LOWER(c.text) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                         LOWER(c.proofread_text) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                         LOWER(c.translated_text) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                         LOWER(c.fixed_translated_text) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                         LOWER(c.summary) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                         LOWER(c.consolidated_summary) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
                END
            )
            """, nativeQuery = true)
    Page<String> searchInText_Native(
            @Param("bookId") String bookId,
            @Param("searchTerm") String searchTerm,
            @Param("matchCase") boolean matchCase,
            @Param("wholeWord") boolean wholeWord,
            @Param("regex") boolean regex,
            PageRequest pageRequest
    );

    default Page<ChapterEntity> searchInText(String bookId, String searchTerm, boolean matchCase,
                                             boolean wholeWord, boolean regex, PageRequest pageRequest) {
        var withSort = pageRequest.withSort(Sort.by("number"));
        var ids = searchInText_Native(bookId, searchTerm, matchCase, wholeWord, regex, pageRequest);
        return page(findAllById(ids.getContent()), withSort, ids.getTotalElements());
    }

    @Query(value = """
            SELECT c.id FROM chapter_info c WHERE 
            c.book_id = :bookId AND (
                EXISTS (SELECT 1 FROM json_array_elements(c.names::json) AS elem 
                WHERE CASE 
                    WHEN :matchCase = true AND :wholeWord = true AND :regex = true THEN
                        (elem->>'name' ~ :searchTerm OR 
                         elem->>'category' ~ :searchTerm OR 
                         elem->>'description' ~ :searchTerm OR 
                         elem->>'ruName' ~ :searchTerm)
                    WHEN :matchCase = true AND :wholeWord = true AND :regex = false THEN
                        (elem->>'name' ~ CONCAT('\\m', :searchTerm, '\\M') OR 
                         elem->>'category' ~ CONCAT('\\m', :searchTerm, '\\M') OR 
                         elem->>'description' ~ CONCAT('\\m', :searchTerm, '\\M') OR 
                         elem->>'ruName' ~ CONCAT('\\m', :searchTerm, '\\M'))
                    WHEN :matchCase = true AND :wholeWord = false AND :regex = true THEN
                        (elem->>'name' ~ :searchTerm OR 
                         elem->>'category' ~ :searchTerm OR 
                         elem->>'description' ~ :searchTerm OR 
                         elem->>'ruName' ~ :searchTerm)
                    WHEN :matchCase = true AND :wholeWord = false AND :regex = false THEN
                        (elem->>'name' LIKE CONCAT('%', :searchTerm, '%') OR 
                         elem->>'category' LIKE CONCAT('%', :searchTerm, '%') OR 
                         elem->>'description' LIKE CONCAT('%', :searchTerm, '%') OR 
                         elem->>'ruName' LIKE CONCAT('%', :searchTerm, '%'))
                    WHEN :matchCase = false AND :wholeWord = true AND :regex = true THEN
                        (elem->>'name' ~* :searchTerm OR 
                         elem->>'category' ~* :searchTerm OR 
                         elem->>'description' ~* :searchTerm OR 
                         elem->>'ruName' ~* :searchTerm)
                    WHEN :matchCase = false AND :wholeWord = true AND :regex = false THEN
                        (elem->>'name' ~* CONCAT('\\m', :searchTerm, '\\M') OR 
                         elem->>'category' ~* CONCAT('\\m', :searchTerm, '\\M') OR 
                         elem->>'description' ~* CONCAT('\\m', :searchTerm, '\\M') OR 
                         elem->>'ruName' ~* CONCAT('\\m', :searchTerm, '\\M'))
                    WHEN :matchCase = false AND :wholeWord = false AND :regex = true THEN
                        (elem->>'name' ~* :searchTerm OR 
                         elem->>'category' ~* :searchTerm OR 
                         elem->>'description' ~* :searchTerm OR 
                         elem->>'ruName' ~* :searchTerm)
                    ELSE
                        (LOWER(elem->>'name') LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                         LOWER(elem->>'category') LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                         LOWER(elem->>'description') LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                         LOWER(elem->>'ruName') LIKE LOWER(CONCAT('%', :searchTerm, '%')))
                END)
            )
            """, nativeQuery = true)
    Page<String> searchInGlossary_Native(
            @Param("bookId") String bookId,
            @Param("searchTerm") String searchTerm,
            @Param("matchCase") boolean matchCase,
            @Param("wholeWord") boolean wholeWord,
            @Param("regex") boolean regex,
            PageRequest pageRequest
    );

    default Page<ChapterEntity> searchInGlossary(String bookId, String searchTerm, boolean matchCase,
                                                 boolean wholeWord, boolean regex, PageRequest pageRequest) {
        var withSort = pageRequest.withSort(Sort.by("number"));
        var ids = searchInGlossary_Native(bookId, searchTerm, matchCase, wholeWord, regex, withSort);
        return page(findAllById(ids.getContent()), withSort, ids.getTotalElements());
    }

    // 3) Combined search: both Chapter fields and ObjectName JSON fields contain ignore case given string
    @Query(value = """
            SELECT c.id FROM chapter_info c WHERE 
            c.book_id = :bookId AND (
                (LOWER(c.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                LOWER(c.translated_title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                LOWER(c.raw_text) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                LOWER(c.text) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                LOWER(c.proofread_text) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                LOWER(c.translated_text) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                LOWER(c.fixed_translated_text) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                LOWER(c.summary) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                LOWER(c.consolidated_summary) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) OR 
                EXISTS (SELECT 1 FROM json_array_elements(c.names::json) AS elem 
                WHERE LOWER(elem->>'name') LIKE LOWER(CONCAT('%', :searchNameTerm, '%')) OR 
                LOWER(elem->>'category') LIKE LOWER(CONCAT('%', :searchNameTerm, '%')) OR 
                LOWER(elem->>'description') LIKE LOWER(CONCAT('%', :searchNameTerm, '%')) OR 
                LOWER(elem->>'ruName') LIKE LOWER(CONCAT('%', :searchNameTerm, '%')))
            )
            """, nativeQuery = true)
    Page<String> searchByCombinedCriteria_Native(@Param("bookId") String bookId, @Param("searchTerm") String searchTerm,
                                                 @Param("searchNameTerm") String searchNameTerm, PageRequest pageRequest);

    default Page<ChapterEntity> searchByCombinedCriteria(String bookId, String searchTerm, String searchNameTerm, PageRequest pageRequest) {
        var withSort = pageRequest.withSort(Sort.by("number"));
        var ids = searchByCombinedCriteria_Native(bookId, searchTerm, searchNameTerm, withSort);
        return page(findAllById(ids), withSort, ids.getTotalElements());
    }

    @Query(value = """
            WITH data as (
                SELECT ci0.* 
                FROM chapter_info ci0
                WHERE ci0.id = :chapterInfoId
            )
            SELECT ci1.id 
            FROM chapter_info ci1
            JOIN data ci2 ON ci2.book_id = ci1.book_id AND ci1.number = (ci2.number - 1)
            LIMIT 1
            """, nativeQuery = true)
    String findPrevious_Native(@Param("chapterInfoId") String chapterInfoId);

    default Optional<ChapterEntity> findPrevious(String chapterInfoId) {
        var id = findPrevious_Native(chapterInfoId);
        if (TextUtil.isNotEmpty(id)) {
            return findById(id);
        } else {
            return Optional.empty();
        }
    }

    @Query(value = """
            SELECT ce0.id 
            FROM chapter_info ce0 
            WHERE ce0.book_id = :bookId 
            AND (
                jsonb_array_length(ce0.warnings) > 0
             OR length(title) < 2
             OR length(translated_title) < 2
             OR length(text) < 2
             OR length(translated_text) < 2
             OR length(summary) < 2
             OR json_array_length(names) < 1
             OR (length(translated_text) - length(replace(translated_text, '\\n', ''))) < 2
            )
            """, nativeQuery = true)
    Page<String> findChaptersWithWarningsByBookId_Native(@Param("bookId") String bookId, PageRequest pageRequest);
    
    default Page<ChapterEntity> findChaptersWithWarningsByBookId(String bookId, PageRequest pageRequest) {
        var withSort = pageRequest.withSort(Sort.by("number"));
        var ids = findChaptersWithWarningsByBookId_Native(bookId, withSort);
        return page(findAllById(ids.getContent()), withSort, ids.getTotalElements());
    }

    @Query(value = "SELECT c FROM ChapterEntity c WHERE c.bookId = :bookId")
    Page<ChapterTitleDto> findTitles(@Param("bookId") String bookId, PageRequest pageRequest);

    @Query(value = "SELECT c FROM ChapterEntity c WHERE c.bookId = :bookId AND (LENGTH(TRIM(COALESCE(title, ''))) = 0 OR LENGTH(TRIM(COALESCE(translatedTitle, ''))) = 0)")
    Page<ChapterTitleDto> findMissingTitles(@Param("bookId") String bookId, PageRequest pageRequest);

    @Query(value =
            Queries.ABERRATION_TITLES + """
                    SELECT 
                      id1 as id,
                      number1 as number,
                      title1 as title,
                      translated_title1 as translatedTitle
                    FROM translation_distances
                    ORDER by number1, number2""",
            countQuery = Queries.ABERRATION_TITLES + """
                    SELECT count(id1) FROM translation_distances""", nativeQuery = true)
    Page<ChapterTitleDto> findAberrationTitles(@Param("bookId") String bookId, PageRequest pageRequest);

    private Page<ChapterEntity> page(List<ChapterEntity> entities, Pageable pageable, long total) {
        entities.sort(Comparator.comparing(ChapterEntity::getNumber));
        
        return new PageImpl<>(entities, pageable, total);
    }

    Long countByBookId(String bookId);

    interface ChapterTitleDto {

        String getId();

        Integer getNumber();

        String getTitle();

        String getTranslatedTitle();

    }

    class Queries {

        public static final String ABERRATION_TITLES = //language=sql
                """
                        WITH book_data AS (
                            SELECT 
                                *
                            FROM chapter_info
                            WHERE book_id = :bookId 
                              AND title IS NOT NULL 
                                AND translated_title IS NOT NULL 
                            ORDER BY number
                        ),
                        similar_titles AS (
                            SELECT 
                                c1.id as id1,
                                c2.id as id2,
                                c1.title as title1,
                                c2.title as title2,
                                c1.translated_title as translated_title1,
                                c2.translated_title as translated_title2,
                                c1.number as number1,
                                c2.number as number2,
                                similarity(c1.title, c2.title) as title_similarity,
                                similarity(c1.translated_title, c2.translated_title) as translation_similarity,
                                row_number() OVER (PARTITION BY c1.id ORDER BY c1.number, c2.number) id1_num,
                                row_number() OVER (PARTITION BY c2.id ORDER BY c1.number, c2.number) id2_num
                            FROM book_data c1
                            JOIN book_data c2 ON c1.book_id = c2.book_id 
                                AND c1.id != c2.id
                                AND similarity(c1.title, c2.title) > 0.7
                        ),
                        translation_distances AS (
                            SELECT distinct on (id1)
                              *
                            FROM similar_titles
                            WHERE title_similarity > 0.7
                              AND translation_similarity < 0.5
                              AND (title_similarity - translation_similarity) > 0.3
                              AND (id1_num = 1 OR id2_num = 1)
                        )
                        """;


    }

}
