package machinum.repository;

import lombok.RequiredArgsConstructor;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static machinum.repository.LineDao.Query.SELECT_SIMILAR;

@Service
@RequiredArgsConstructor
public class LineDao {

    private static final String ENGLISH_NORMALIZED_LEXEMES = "'english'";
    private static final String RUSSIAN_NORMALIZED_LEXEMES = "'russian'";

    private static final String ENGLISH_LINE = "original_line";
    private static final String TRANSLATED_LINE = "translated_line";


    private final JdbcTemplate jdbcTemplate;

    @Deprecated(forRemoval = true)
    public void refreshMaterializedView() {
        //TODO: Fix next line
//        jdbcTemplate.execute(Query.REFRESH_SQL);
    }

    @Deprecated(forRemoval = true)
    public void vacuumMaterializedView() {
        //TODO: Fix next line
//        jdbcTemplate.execute(Query.VACUUM_SQL);
    }

    public String findBookIdByChapter(String chapterId) {
        return jdbcTemplate.queryForObject("SELECT book_id FROM lines_info WHERE chapter_id = ?", String.class, chapterId);
    }

    public List<String> findTranslatedSimilarLineForChapter(String chapterId, String line) {
        String query = StringSubstitutor.replace(SELECT_SIMILAR, Map.of(
                "query", Query.SELECT_TEXT_FOR_CHAPTER,
                "lexemes", RUSSIAN_NORMALIZED_LEXEMES,
                "line", TRANSLATED_LINE
        ));

        return jdbcTemplate
                .queryForList(query, line, chapterId, 100).stream()
                .map(map -> map.get("target_id"))
                .map(Object::toString)
                .toList();
    }

    public List<String> findOriginalSimilarLineForChapter(String chapterId, String line) {
        String query = StringSubstitutor.replace(SELECT_SIMILAR, Map.of(
                "query", Query.SELECT_TEXT_FOR_CHAPTER,
                "lexemes", ENGLISH_NORMALIZED_LEXEMES,
                "line", ENGLISH_LINE
        ));

        return jdbcTemplate
                .queryForList(query, line, chapterId, 100).stream()
                .map(map -> map.get("target_id"))
                .map(Object::toString)
                .toList();
    }

    public List<String> findTranslatedSimilarLineForBook(String bookId, String line) {
        String query = StringSubstitutor.replace(SELECT_SIMILAR, Map.of(
                "query", Query.SELECT_TEXT_FOR_BOOK,
                "lexemes", RUSSIAN_NORMALIZED_LEXEMES,
                "line", TRANSLATED_LINE
        ));

        return jdbcTemplate
                .queryForList(query, line, bookId, 100).stream()
                .map(map -> map.get("target_id"))
                .map(Object::toString)
                .toList();
    }

    public List<String> findOriginalSimilarLineForBook(String bookId, String line) {
        String query = StringSubstitutor.replace(SELECT_SIMILAR, Map.of(
                "query", Query.SELECT_TEXT_FOR_BOOK,
                "lexemes", ENGLISH_NORMALIZED_LEXEMES,
                "line", ENGLISH_LINE
        ));

        return jdbcTemplate
                .queryForList(query, line, bookId, 100).stream()
                .map(map -> map.get("target_id"))
                .map(Object::toString)
                .toList();
    }

    /* ============= */

    static class Query {

        public static final String SELECT_TEXT_FOR_BOOK = //language=sql
                """
                        WITH input_params AS (
                            SELECT ? AS search_text, ? as book_id
                        )\
                        """;

        public static final String SELECT_TEXT_FOR_CHAPTER = //language=sql
                """
                        WITH input_params AS (
                            SELECT ? AS search_text, book_id FROM lines_info WHERE chapter_id = ?
                            LIMIT 1
                        )\
                        """;

        public static final String SELECT_FOR_LINE = //language=sql
                """
                        WITH input_params AS (
                            SELECT original_line AS search_text, book_id FROM lines_info WHERE id = ?
                        )\
                        """;


        public static final String SELECT_SIMILAR = //language=sql
                """
                        ${query}
                                        
                        ,exact_matches_original AS (
                            SELECT 
                                l.id AS target_id,
                                l.${line} AS target_text,
                                'exact_match' AS match_type,
                                1.0 AS match_weight
                                ,l.number
                            FROM lines_info l, input_params ip
                            WHERE l.book_id = ip.book_id 
                            AND trim(l.${line}) <> ''
                            AND l.${line} = ip.search_text
                        )
                                    
                        ,like_matches_original AS (
                            SELECT
                                l.id AS target_id,
                                l.${line} AS target_text,
                                'like_match' AS match_type,
                                0.9 AS match_weight
                                ,l.number
                            FROM lines_info l, input_params ip
                            WHERE l.book_id = ip.book_id
                            AND trim(l.${line}) <> ''
                            AND l.${line} ilike format('%%%s%%', ip.search_text)
                        )
                                        
                        ,websearch_matches_original AS (
                            SELECT 
                                l.id AS target_id,
                                l.${line} AS target_text,
                                'websearch' AS match_type,
                                ts_rank_cd(to_tsvector(${lexemes}, l.${line}), 
                                          websearch_to_tsquery(${lexemes}, ip.search_text)) AS match_weight
                                ,l.number
                            FROM lines_info l, input_params ip
                            WHERE l.book_id = ip.book_id 
                            AND trim(l.${line}) <> ''
                            AND l.${line} != ip.search_text
                            AND to_tsvector(${lexemes}, l.${line}) @@ websearch_to_tsquery(${lexemes}, ip.search_text) 
                        )
                                        
                        ,trigram_matches_original AS (
                            SELECT 
                                l.id AS target_id,
                                l.${line} AS target_text,
                                'trigram' AS match_type,
                                similarity(ip.search_text, l.${line}) AS match_weight
                                ,l.number
                            FROM lines_info l, input_params ip
                            WHERE l.book_id = ip.book_id
                            AND trim(l.${line}) <> ''
                            AND l.${line} != ip.search_text
                            AND ip.search_text % l.${line}
                        )
                                        
                        ,levenshtein_matches_original AS (
                            SELECT 
                                l.id AS target_id,
                                l.${line} AS target_text,
                                'levenshtein' AS match_type,
                                1.0 - (CAST(levenshtein(left(ip.search_text, 255), left(l.original_line, 255)) AS float) / 
                                      GREATEST(length(ip.search_text), length(l.${line}))) AS match_weight
                                ,l.number
                            FROM lines_info l, input_params ip
                            WHERE levenshtein(left(ip.search_text, 255), left(l.${line}, 255)) <= 10
                            AND l.book_id = ip.book_id
                            AND l.${line} != ip.search_text
                            AND length(l.${line}) > 10
                        )
                                    
                        ,combined_matches as (
                            SELECT * FROM (
                                SELECT * FROM exact_matches_original
                                UNION ALL
                                SELECT * FROM like_matches_original
                                UNION ALL
                                SELECT * FROM levenshtein_matches_original
                                UNION ALL
                                SELECT * FROM trigram_matches_original
                                UNION ALL
                                SELECT * FROM websearch_matches_original
                            ) WHERE match_weight > 0.3
                            ORDER BY number ASC
                        )
                                        
                        SELECT
                             target_id
                            ,target_text
                            ,match_type
                            ,match_weight
                            ,weighted_score
                        FROM (
                            SELECT
                                *
                                ,CASE match_type
                                    WHEN 'exact_match' THEN 5.0
                                    WHEN 'like_match' THEN 4.0
                                    WHEN 'levenshtein' THEN 3.0
                                    WHEN 'trigram' THEN 2.0
                                    WHEN 'websearch' THEN 1.0
                                 END * match_weight AS weighted_score
                                ,row_number() OVER (PARTITION BY target_id ORDER BY match_weight) as row_num
                            FROM combined_matches
                        ) data
                        WHERE row_num <= 1
                        ORDER BY number, weighted_score DESC
                        LIMIT ?
                        """;

    }

}
