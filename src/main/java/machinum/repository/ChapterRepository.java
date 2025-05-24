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
import java.util.stream.Collectors;

@Repository
public interface ChapterRepository extends JpaRepository<ChapterEntity, String> {

    @Query(value = """ 
                select 
                    c2.name,
                    c2.raw_json
                from 
                    chapter_glossary c2 
                where 
                    c2.name in :names 
                    and c2.number <= :chapterNumber
                    and c2.book_id = :bookId 
            """, nativeQuery = true)
    List<GlossaryByQueryResult> findGlossaryByQuery(@Param("names") List<String> names,
                                                    @Param("chapterNumber") Integer chapterNumber,
                                                    @Param("bookId") String bookId);

    @Query(value = """ 
                WITH data AS (
                   SELECT c0.*
                   FROM chapter_glossary c0
                   WHERE c0.number <= :chapterNumber
                       AND c0.book_id = :bookId
                   ORDER BY c0.number DESC
                )
                , search_terms AS (
                   SELECT unnest(:terms) AS term
                       , unnest(:termsOr) AS termOr
                )
                , full_text_search AS (
                   SELECT c2.id
                       ,'2' AS search_type
                       ,MAX(GREATEST(
                            ts_rank(c2.search_string1, websearch_to_tsquery(st.term)), 
                            ts_rank(c2.search_string2, websearch_to_tsquery(st.term))
                        )) AS relevance_rank
                       ,0 AS similarity_rank
                       ,99 AS distance_rank
                   FROM data c2
                   CROSS JOIN search_terms st
                   WHERE c2.search_string2 @@ websearch_to_tsquery(st.term)
                   GROUP BY c2.id
                )
                , full_text_alternative_search AS (
                   SELECT c3.id
                       ,'3' AS data_type
                       ,MAX(GREATEST(
                            ts_rank(c3.search_string1, websearch_to_tsquery(st.termOr)), 
                            ts_rank(c3.search_string2, websearch_to_tsquery(st.termOr))
                        )) AS relevance_rank
                       ,0 AS similarity_rank
                       ,99 AS distance_rank
                   FROM chapter_glossary c3
                   CROSS JOIN search_terms st
                   WHERE c3.search_string2 @@ websearch_to_tsquery(st.termOr)
                   GROUP BY c3.id
                )
                , trigram_search AS (
                   SELECT TEMP.id
                       ,TEMP.search_type
                       ,0 AS relevance_rank
                       ,TEMP.similarity_rank
                       ,99 AS distance_rank
                   FROM (
                       SELECT c4.id
                           ,'4' AS search_type
                           ,GREATEST(
                                similarity(c4.name, st.term), 
                                similarity(c4.search_string3, st.term)
                            ) AS similarity_rank
                           ,row_number() OVER (PARTITION BY id ORDER BY name ,number DESC ) id_num
                       FROM data c4
                       CROSS JOIN search_terms st
                       WHERE c4.search_string3 % st.term
                   ) AS TEMP
                   WHERE TEMP.id_num = 1
                )
                , levenshtein_search AS (
                   SELECT TEMP.id
                       ,TEMP.search_type
                       ,0 AS relevance_rank
                       ,0 AS similarity_rank
                       ,TEMP.distance_rank
                   FROM (
                       SELECT c5.id
                           ,'5' AS search_type
                           ,levenshtein(lower(c5.name), lower(st.term)) AS distance_rank
                           ,row_number() OVER ( PARTITION BY id ORDER BY name ,number DESC ) id_num
                       FROM data c5
                       CROSS JOIN search_terms st
                       WHERE levenshtein(c5.name, lower(st.term)) <= 3
                   ) AS TEMP
                   WHERE TEMP.id_num = 1
                )
                , search_results AS (
                   SELECT *
                       ,row_number() OVER (PARTITION BY id) AS id_num
                   FROM (
                       SELECT id
                           ,search_type
                           ,COALESCE(relevance_rank, similarity_rank, 1.0 / (distance_rank + 1)) AS score
                       FROM (
                           SELECT * FROM full_text_search
                           UNION ALL
                           SELECT * FROM full_text_alternative_search
                           UNION ALL
                           SELECT * FROM trigram_search
                           UNION ALL
                           SELECT * FROM levenshtein_search
                       )
                   )
                )
                ,glossary AS (
                   SELECT row_number() OVER (PARTITION BY name ORDER BY score DESC) AS name_num
                       ,c7.*
                       ,c6.*
                   FROM search_results c7
                   INNER JOIN data c6 ON c6.id = c7.id
                   WHERE c7.id_num = 1
                )
                SELECT raw_json
                FROM glossary
                WHERE name_num = 1
            """, nativeQuery = true)
    List<String> findLatestGlossaryListByQuery(@Param("terms") List<String> terms,
                                               @Param("termsOr") List<String> termsOr,
                                               @Param("chapterNumber") Integer chapterNumber,
                                               @Param("bookId") String bookId);

    @Deprecated
    @Query(value = """ 
                with ranked_ts_data as ( 
                	select 
                		c2.*, 
                		'2' as data_type, 
                		ts_rank(c2.search_string1, websearch_to_tsquery(:queryAnd)) as rank1, 
                		ts_rank(c2.search_string2, websearch_to_tsquery(:queryAnd)) as rank2 
                	from 
                		chapter_glossary c2 
                	where 
                		c2.search_string2 @@ websearch_to_tsquery(:queryAnd) 
                		and c2.number <= :chapterNumber
                		and c2.book_id = :bookId 
                	union all 
                	select 
                		c3.*, 
                		'3' as data_type, 
                		ts_rank(c3.search_string1, websearch_to_tsquery(:queryOr)) as rank1, 
                		ts_rank(c3.search_string2, websearch_to_tsquery(:queryOr)) as rank2 
                	from 
                		chapter_glossary c3 
                	where 
                		c3.search_string2 @@ websearch_to_tsquery(:queryOr)
                		and c3.number <= :chapterNumber
                		and c3.book_id = :bookId 
                ), 
                last_data as ( 
                	select 
                	   max(c4.number) OVER (PARTITION BY data_type, name) last_number, 
                       row_number() OVER (PARTITION BY data_type, name ORDER BY number DESC) row_num, 
                 	   c4.* 
                    from 
                      ranked_ts_data c4 
                ) 
                select 
                    c5.raw_json 
                from 
                	last_data c5 
                where row_num = 1	 
                order by 
                	c5.data_type, c5.rank1 desc, c5.rank2 desc, c5.name 
                limit :rowLimit	 
            """, nativeQuery = true)
    List<String> findLatestGlossaryByQuery(@Param("queryAnd") String query,
                                           @Param("queryOr") String queryOr,
                                           @Param("chapterNumber") Integer chapterNumber,
                                           @Param("bookId") String bookId,
                                           @Param("rowLimit") Integer rowLimit);

    @Deprecated
    default List<String> findLatestGlossaryByQuery(Integer chapterNumber, List<String> names, String bookId, int rowLimit) {
        return names.stream().parallel()
                .flatMap(n -> findLatestGlossaryByQuery(n, String.join(" or ", n.split("\\s+")), chapterNumber, bookId, rowLimit).stream())
                .distinct()
                .collect(Collectors.toList());
    }

    @Query(value = """ 
                with data as (
                    SELECT 
                        cg0.* ,
                        row_number() OVER (PARTITION BY name ORDER BY number DESC) row_num
                    FROM chapter_glossary cg0
                    WHERE cg0.translated is true
                    AND cg0.book_id = :bookId  
                )
                SELECT 
                    cg1.raw_json 
                FROM 
                    data cg1 
                WHERE row_num = 1
                AND name IN :names
            """, nativeQuery = true)
    List<String> findTranslatedNames(@Param("bookId") String bookId, @Param("names") List<String> names);

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

    // 1) Search by title, translatedTitle, rawText, text, proofreadText, translatedText, fixedTranslatedText, summary, consolidatedSummary contains ignore case given string
    @Query(value = """
            SELECT c.id FROM chapter_info c WHERE 
            c.book_id = :bookId AND (
                LOWER(c.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                LOWER(c.translated_title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                LOWER(c.raw_text) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                LOWER(c.text) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                LOWER(c.proofread_text) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                LOWER(c.translated_text) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                LOWER(c.fixed_translated_text) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                LOWER(c.summary) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                LOWER(c.consolidated_summary) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
            )
            """, nativeQuery = true)
    Page<String> searchByChapterInfoFields_Native(@Param("bookId") String bookId, @Param("searchTerm") String searchTerm, PageRequest pageRequest);

    default Page<ChapterEntity> searchByChapterInfoFields(String bookId, String searchTerm, PageRequest pageRequest) {
        var withSort = pageRequest.withSort(Sort.by("number"));
        var ids = searchByChapterInfoFields_Native(bookId, searchTerm, withSort);
        return page(findAllById(ids.getContent()), withSort, ids.getTotalElements());
    }

    // 2) Search by name, category, description, ruName contains ignore case given string in ObjectName JSON field
    @Query(value = """
            SELECT c.id FROM chapter_info c WHERE 
            c.book_id = :bookId AND (
                EXISTS (SELECT 1 FROM json_array_elements(c.names::json) AS elem 
                WHERE LOWER(elem->>'name') LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                LOWER(elem->>'category') LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                LOWER(elem->>'description') LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
                LOWER(elem->>'ruName') LIKE LOWER(CONCAT('%', :searchTerm, '%')))
            )
            """, nativeQuery = true)
    Page<String> searchByObjectNameFields_Native(@Param("bookId") String bookId, @Param("searchTerm") String searchTerm, PageRequest pageRequest);

    default Page<ChapterEntity> searchByObjectNameFields(String bookId, String searchTerm, PageRequest pageRequest) {
        var withSort = pageRequest.withSort(Sort.by("number"));
        var ids = searchByObjectNameFields_Native(bookId, searchTerm, withSort);
        return page(findAllById(ids), withSort, ids.getTotalElements());
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

    interface GlossaryByQueryResult {

        String getName();

        String getRawJson();

    }

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
