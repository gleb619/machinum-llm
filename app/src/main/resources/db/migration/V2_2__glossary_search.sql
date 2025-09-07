DROP FUNCTION IF EXISTS search_glossary;

CREATE OR REPLACE FUNCTION search_glossary(
    p_book_id VARCHAR(36),
    p_search_text TEXT,
    p_chapter_start INTEGER DEFAULT 1,
    p_chapter_end INTEGER DEFAULT 999999,
    p_top_k INTEGER DEFAULT 20,
    p_min_score FLOAT DEFAULT 0.1
)
RETURNS TABLE(
    glossary_id TEXT,
    chapter_id VARCHAR(36),
    name TEXT,
    translated_name TEXT,
    category TEXT,
    description TEXT,
    chapter_number INTEGER,
    search_type TEXT,
    score REAL,
    raw_json TEXT
) AS $$
BEGIN
    RETURN QUERY
    WITH base_data AS (
        SELECT cg.id,
               cg.chapter_id,
               cg.name,
               cg.translated_name,
               cg.category,
               cg.description,
               cg.number,
               cg.raw_json,
               cg.search_string1,
               cg.search_string2,
               cg.search_string3
        FROM chapter_glossary cg
        WHERE cg.book_id = p_book_id
          AND cg.number between p_chapter_start and p_chapter_end
          AND cg.name IS NOT NULL
    ),
    exact_match AS (
        SELECT bd.id, '1_exact' as search_type, 1.0 as score
        FROM base_data bd
        WHERE lower(bd.name) = lower(p_search_text)
    ),
    contains_match AS (
        SELECT bd.id, '2_contains' as search_type,
               CASE
                   WHEN lower(bd.name) LIKE '%' || lower(p_search_text) || '%' THEN 0.9
                   WHEN lower(bd.description) LIKE '%' || lower(p_search_text) || '%' THEN 0.7
                   ELSE 0.5
               END as score
        FROM base_data bd
        WHERE (lower(bd.name) LIKE '%' || lower(p_search_text) || '%'
               OR lower(bd.description) LIKE '%' || lower(p_search_text) || '%')
          AND bd.id NOT IN (SELECT id FROM exact_match)
    ),
    fulltext_search AS (
        SELECT bd.id, '3_fulltext' as search_type,
               GREATEST(
                   ts_rank(bd.search_string1, websearch_to_tsquery('english', p_search_text)),
                   ts_rank(bd.search_string2, websearch_to_tsquery('english', p_search_text))
               ) as score
        FROM base_data bd
        WHERE bd.search_string2 @@ websearch_to_tsquery('english', p_search_text)
          AND bd.id NOT IN (SELECT id FROM exact_match UNION SELECT id FROM contains_match)
    ),
    trigram_search AS (
        SELECT bd.id, '4_trigram' as search_type,
               GREATEST(
                   similarity(bd.name, p_search_text),
                   similarity(bd.search_string3, p_search_text)
               ) as score
        FROM base_data bd
        WHERE (bd.name % p_search_text OR bd.search_string3 % p_search_text)
          AND bd.id NOT IN (
              SELECT id FROM exact_match
              UNION SELECT id FROM contains_match
              UNION SELECT id FROM fulltext_search
          )
    ),
    levenshtein_search AS (
        SELECT bd.id, '5_levenshtein' as search_type,
               1.0 / (1.0 + levenshtein(lower(bd.name), lower(p_search_text))) as score
        FROM base_data bd
        WHERE levenshtein(lower(bd.name), lower(p_search_text)) <= GREATEST(3, length(p_search_text) * 0.3)
          AND bd.id NOT IN (
              SELECT id FROM exact_match
              UNION SELECT id FROM contains_match
              UNION SELECT id FROM fulltext_search
              UNION SELECT id FROM trigram_search
          )
    ),
    all_results AS (
        SELECT * FROM exact_match
        UNION ALL SELECT * FROM contains_match
        UNION ALL SELECT * FROM fulltext_search
        UNION ALL SELECT * FROM trigram_search
        UNION ALL SELECT * FROM levenshtein_search
    ),
    ranked_results AS (
        SELECT ar.id, ar.search_type, ar.score,
               ROW_NUMBER() OVER (PARTITION BY ar.id ORDER BY ar.score DESC) as rn
        FROM all_results ar
        WHERE ar.score >= p_min_score
    ),
    final_results AS (
        SELECT rr.id, rr.search_type, rr.score,
               ROW_NUMBER() OVER (ORDER BY rr.score DESC, bd.number DESC) as final_rank
        FROM ranked_results rr
        JOIN base_data bd ON bd.id = rr.id
        WHERE rr.rn = 1
    )
    SELECT fr.id,
           bd.chapter_id,
           bd.name,
           bd.translated_name,
           bd.category,
           bd.description,
           bd.number,
           fr.search_type,
           fr.score,
           bd.raw_json
    FROM final_results fr
    JOIN base_data bd ON bd.id = fr.id
    WHERE fr.final_rank <= p_top_k
    ORDER BY fr.score DESC, bd.number DESC;
END;
$$ LANGUAGE plpgsql;