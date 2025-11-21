package machinum.repository;

import machinum.entity.ChapterGlossaryView;
import machinum.model.ChapterGlossary.ChapterGlossaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

import static machinum.repository.ChapterGlossaryRepository.Queries.*;

@Repository
public interface ChapterGlossaryRepository extends JpaRepository<ChapterGlossaryView, String> {

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
                select 
                    c2.name,
                    count(1) as count
                from 
                    chapter_glossary c2 
                where 
                    c2.book_id = :bookId 
                    and c2.number <= :chapterNumber
                    and c2.name in :names 
                group by c2.name
            """, nativeQuery = true)
    List<CountResult> countGlossaryInPreviousChapters(@Param("names") List<String> names,
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

    @Query(value =
            BOOK_GLOSSARY + """
                    SELECT 
                        cg1.id,
                        cg1.chapterId,
                        cg1.chapterNumber,
                        cg1.rawJson 
                    FROM data cg1 
                    WHERE cg1.row_num = 1 
                    ORDER BY cg1.chapterNumber, cg1.category, cg1.name""",
            countQuery = BOOK_GLOSSARY + "SELECT count(cg1.id) FROM data cg1 WHERE cg1.row_num = 1", nativeQuery = true)
    Page<ChapterGlossaryProjection> findGlossary(@Param("bookId") String bookId, PageRequest pageRequest);

    @Query(value =
            BOOK_GLOSSARY + """
                    SELECT 
                        cg1.id,
                        cg1.chapterId,
                        cg1.chapterNumber,
                        cg1.rawJson 
                    FROM data cg1 
                    WHERE cg1.row_num = 1 
                    AND cg1.translated = :translated  
                    ORDER BY cg1.chapterNumber, cg1.category, cg1.name""",
            countQuery = BOOK_GLOSSARY + """
                    SELECT count(cg1.id) 
                    FROM data cg1 
                    WHERE cg1.row_num = 1 
                    AND cg1.translated = :translated  """, nativeQuery = true)
    Page<ChapterGlossaryProjection> findTranslatedGlossary(@Param("bookId") String bookId,
                                                           @Param("translated") Boolean translated,
                                                           PageRequest pageRequest);

    @Query(value =
            BOOK_GLOSSARY + """
                    SELECT 
                        cg1.id,
                        cg1.chapterId,
                        cg1.chapterNumber,
                        cg1.rawJson 
                    FROM data cg1 
                    WHERE cg1.row_num = 1 
                    AND cg1.chapterNumber BETWEEN :fromChapter AND :toChapter
                    ORDER BY cg1.chapterNumber, cg1.category, cg1.name""",
            countQuery = BOOK_GLOSSARY + """
                    SELECT count(cg1.id) 
                    FROM data cg1 
                    WHERE cg1.row_num = 1 
                    AND cg1.chapterNumber BETWEEN :fromChapter AND :toChapter""", nativeQuery = true)
    Page<ChapterGlossaryProjection> findTranslatedGlossary(@Param("bookId") String bookId,
                                                           @Param("fromChapter") Integer fromChapter,
                                                           @Param("toChapter") Integer toChapter,
                                                           PageRequest pageRequest);

    @Query(value = Queries.GLOSSARY_SEARCH + """
            SELECT
                cg1.id,
                cg1.chapterId,
                cg1.chapterNumber,
                cg1.rawJson
            FROM data cg1
            """, countQuery = GLOSSARY_SEARCH + """
            SELECT count(cg1.id)
            FROM data cg1
            """, nativeQuery = true)
    List<ChapterGlossaryProjection> searchGlossary(
            @Param("bookId") String bookId,
            @Param("searchText") String searchText,
            @Param("chapterStart") Integer chapterStart,
            @Param("chapterEnd") Integer chapterEnd,
            @Param("topK") Integer topK,
            @Param("minScore") Float minScore);

    @Query(value = GLOSSARY_FUZZY_SEARCH + """
            SELECT
                cg1.id,
                cg1.chapterId,
                cg1.chapterNumber,
                cg1.rawJson
            FROM data cg1
            """, countQuery = GLOSSARY_FUZZY_SEARCH + """
            SELECT count(cg1.id)
            FROM data cg1
            """, nativeQuery = true)
    List<ChapterGlossaryProjection> searchGlossaryFuzzy(
            @Param("bookId") String bookId,
            @Param("fuzzyText") String fuzzyText,
            @Param("chapterStart") Integer chapterStart,
            @Param("chapterEnd") Integer chapterEnd,
            @Param("topK") Integer topK,
            @Param("minScore") Float minScore);

    @Modifying
    @Query(value = "SELECT mt_replace_text(:bookId, :search, :replacement)", nativeQuery = true)
//    @Query(value = "SELECT mt_replace_text(:bookId, cast(:search as text), cast(:replacement as text))", nativeQuery = true)
    List replaceText(@Param("bookId") String bookId,
                     @Param("search") String search,
                     @Param("replacement") String replacement);

    @Modifying
    @Query(value = "SELECT mt_replace_text_by_id(:chapterId, cast(:search as text), cast(:replacement as text))", nativeQuery = true)
    List replaceTextById(@Param("chapterId") String chapterId,
                         @Param("search") String search,
                         @Param("replacement") String replacement);

    @Modifying
    @Query(value = "SELECT mt_replace_text_for_column(:chapterId, :columnName, cast(:search as text), cast(:replacement as text))", nativeQuery = true)
    List replaceTextForColumn(@Param("chapterId") String chapterId,
                              @Param("columnName") String columnName,
                              @Param("search") String search,
                              @Param("replacement") String replacement);

    @Modifying
    @Query(value = "SELECT mt_replace_summary(:bookId, cast(:search as text), cast(:replacement as text))", nativeQuery = true)
    List replaceSummary(@Param("bookId") String bookId,
                        @Param("search") String search,
                        @Param("replacement") String replacement);

    @Query(value = "SELECT mt_update_glossary_runame(:bookId, :oldRuName, :newRuName, :returnIds, :nameFilter)",
            nativeQuery = true)
    String updateGlossaryRuName(@Param("bookId") String bookId,
                              @Param("oldRuName") String oldRuName,
                              @Param("newRuName") String newRuName,
                                @Param("returnIds") Boolean returnIds,
                                @Param("nameFilter") String nameFilter);

    interface GlossaryByQueryResult {

        String getName();

        String getRawJson();

    }

    interface CountResult {

        String getName();

        Long getCount();

    }

    class Queries {

        public static final String BOOK_GLOSSARY = //language=sql
                """
                        WITH data AS (
                          SELECT
                              cg0.id,
                              cg0.chapter_id as chapterId,
                              cg0."number" as chapterNumber,
                              cg0."name",
                              cg0.category,
                              cg0.description,
                              cg0.translated,
                              cg0.raw_json as rawJson,
                              row_number() OVER (PARTITION BY cg0.category, cg0.name ORDER by cg0.number) row_num
                          FROM
                              chapter_glossary cg0
                              WHERE cg0.book_id = :bookId
                        )
                        """;

        public static final String GLOSSARY_SEARCH = //language=sql
                """
                        WITH data AS (
                          SELECT
                              cg0.id,
                              cg0.chapter_id as chapterId,
                              cg0."number" as chapterNumber,
                              cg0."name",
                              cg0.category,
                              cg0.description,
                              cg0.translated,
                              cg0.raw_json as rawJson
                          FROM search_glossary(:bookId, :searchText, :chapterStart, :chapterEnd, :topK, :minScore) sg1
                          LEFT JOIN chapter_glossary cg0 ON cg0.id = sg1.glossary_id
                          ORDER BY sg1.score DESC
                        )
                        """;

        public static final String GLOSSARY_FUZZY_SEARCH = //language=sql
                """
                        WITH data AS (
                          SELECT
                              cg0.id,
                              cg0.chapter_id as chapterId,
                              cg0."number" as chapterNumber,
                              cg0."name",
                              cg0.category,
                              cg0.description,
                              cg0.translated,
                              cg0.raw_json as rawJson
                          FROM fuzzy_search_glossary(:bookId, :fuzzyText, :chapterStart, :chapterEnd, :topK, :minScore) sg1
                          LEFT JOIN chapter_glossary cg0 ON cg0.id = sg1.glossary_id
                          ORDER BY sg1.score DESC
                        )
                        """;

    }


}
