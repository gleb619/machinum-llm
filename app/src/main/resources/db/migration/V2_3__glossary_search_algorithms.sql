-- Individual glossary search algorithm functions for better maintainability and testability
-- These functions can be called independently instead of the monolithic search_glossary function

CREATE OR REPLACE FUNCTION find_glossary_exact_matches(
    p_book_id VARCHAR(36),
    p_search_text TEXT,
    p_chapter_start INTEGER DEFAULT 1,
    p_chapter_end INTEGER DEFAULT 999999
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
    SELECT
        cg.id,
        cg.chapter_id,
        cg.name,
        cg.translated_name,
        cg.category,
        cg.description,
        cg.number,
        '1_exact'::TEXT as search_type,
        1.0::REAL as score,
        cg.raw_json
    FROM chapter_glossary cg
    WHERE cg.book_id = p_book_id
      AND cg.number BETWEEN p_chapter_start AND p_chapter_end
      AND lower(cg.name) = lower(p_search_text)
      AND cg.name IS NOT NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION find_glossary_contains_matches(
    p_book_id VARCHAR(36),
    p_search_text TEXT,
    p_chapter_start INTEGER DEFAULT 1,
    p_chapter_end INTEGER DEFAULT 999999
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
    SELECT
        cg.id,
        cg.chapter_id,
        cg.name,
        cg.translated_name,
        cg.category,
        cg.description,
        cg.number,
        '2_contains'::TEXT as search_type,
        CASE
            WHEN lower(cg.name) LIKE '%' || lower(p_search_text) || '%' THEN 0.9::REAL
            WHEN lower(cg.description) LIKE '%' || lower(p_search_text) || '%' THEN 0.7::REAL
            ELSE 0.5::REAL
        END as score,
        cg.raw_json
    FROM chapter_glossary cg
    WHERE cg.book_id = p_book_id
      AND cg.number BETWEEN p_chapter_start AND p_chapter_end
      AND (lower(cg.name) LIKE '%' || lower(p_search_text) || '%'
           OR lower(cg.description) LIKE '%' || lower(p_search_text) || '%')
      AND cg.name IS NOT NULL
      AND lower(cg.name) != lower(p_search_text); -- Exclude exact matches
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION find_glossary_fulltext_matches(
    p_book_id VARCHAR(36),
    p_search_text TEXT,
    p_chapter_start INTEGER DEFAULT 1,
    p_chapter_end INTEGER DEFAULT 999999
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
    SELECT
        cg.id,
        cg.chapter_id,
        cg.name,
        cg.translated_name,
        cg.category,
        cg.description,
        cg.number,
        '3_fulltext'::TEXT as search_type,
        GREATEST(
            ts_rank(cg.search_string1, websearch_to_tsquery('english', p_search_text)),
            ts_rank(cg.search_string2, websearch_to_tsquery('english', p_search_text))
        ) as score,
        cg.raw_json
    FROM chapter_glossary cg
    WHERE cg.book_id = p_book_id
      AND cg.number BETWEEN p_chapter_start AND p_chapter_end
      AND cg.search_string2 @@ websearch_to_tsquery('english', p_search_text)
      AND lower(cg.name) != lower(p_search_text) -- Exclude exact matches
      AND NOT (lower(cg.name) LIKE '%' || lower(p_search_text) || '%' -- Exclude contains matches
               OR lower(cg.description) LIKE '%' || lower(p_search_text) || '%')
      AND cg.name IS NOT NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION find_glossary_trigram_matches(
    p_book_id VARCHAR(36),
    p_search_text TEXT,
    p_chapter_start INTEGER DEFAULT 1,
    p_chapter_end INTEGER DEFAULT 999999
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
    SELECT
        cg.id,
        cg.chapter_id,
        cg.name,
        cg.translated_name,
        cg.category,
        cg.description,
        cg.number,
        '4_trigram'::TEXT as search_type,
        GREATEST(
            similarity(cg.name, p_search_text),
            similarity(cg.search_string3, p_search_text)
        ) as score,
        cg.raw_json
    FROM chapter_glossary cg
    WHERE cg.book_id = p_book_id
      AND cg.number BETWEEN p_chapter_start AND p_chapter_end
      AND (cg.name % p_search_text OR cg.search_string3 % p_search_text)
      AND lower(cg.name) != lower(p_search_text) -- Exclude exact matches
      AND NOT (lower(cg.name) LIKE '%' || lower(p_search_text) || '%' -- Exclude contains matches
               OR lower(cg.description) LIKE '%' || lower(p_search_text) || '%')
      AND NOT (cg.search_string2 @@ websearch_to_tsquery('english', p_search_text)) -- Exclude fulltext matches
      AND cg.name IS NOT NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION find_glossary_levenshtein_matches(
    p_book_id VARCHAR(36),
    p_search_text TEXT,
    p_chapter_start INTEGER DEFAULT 1,
    p_chapter_end INTEGER DEFAULT 999999
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
DECLARE
    distance_threshold INTEGER;
BEGIN
    distance_threshold := GREATEST(3, length(p_search_text) * 0.3);

    RETURN QUERY
    SELECT
        cg.id,
        cg.chapter_id,
        cg.name,
        cg.translated_name,
        cg.category,
        cg.description,
        cg.number,
        '5_levenshtein'::TEXT as search_type,
        (1.0 / (1.0 + levenshtein(lower(cg.name), lower(p_search_text))::float))::REAL as score,
        cg.raw_json
    FROM chapter_glossary cg
    WHERE cg.book_id = p_book_id
      AND cg.number BETWEEN p_chapter_start AND p_chapter_end
      AND levenshtein(lower(cg.name), lower(p_search_text)) <= distance_threshold
      AND lower(cg.name) != lower(p_search_text) -- Exclude exact matches
      AND NOT (lower(cg.name) LIKE '%' || lower(p_search_text) || '%' -- Exclude contains matches
               OR lower(cg.description) LIKE '%' || lower(p_search_text) || '%')
      AND NOT (cg.search_string2 @@ websearch_to_tsquery('english', p_search_text)) -- Exclude fulltext matches
      AND NOT (cg.name % p_search_text OR cg.search_string3 % p_search_text) -- Exclude trigram matches
      AND cg.name IS NOT NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION find_glossary_phonetic_matches(
    p_book_id VARCHAR(36),
    p_search_text TEXT,
    p_chapter_start INTEGER DEFAULT 1,
    p_chapter_end INTEGER DEFAULT 999999
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
    SELECT
        cg.id,
        cg.chapter_id,
        cg.name,
        cg.translated_name,
        cg.category,
        cg.description,
        cg.number,
        CASE
            WHEN dmetaphone(cg.name) = dmetaphone(p_search_text) AND dmetaphone_alt(cg.name) = dmetaphone_alt(p_search_text) THEN '6_metaphone_exact'
            WHEN dmetaphone(cg.name) = dmetaphone(p_search_text) THEN '6_metaphone_primary'
            WHEN dmetaphone_alt(cg.name) = dmetaphone_alt(p_search_text) THEN '6_metaphone_alt'
            WHEN soundex(cg.name) = soundex(p_search_text) THEN '8_soundex'
            ELSE '6_phonetic_unknown'
        END::TEXT as search_type,
        CASE
            WHEN dmetaphone(cg.name) = dmetaphone(p_search_text) AND dmetaphone_alt(cg.name) = dmetaphone_alt(p_search_text) THEN 0.6::REAL
            WHEN dmetaphone(cg.name) = dmetaphone(p_search_text) THEN 0.5::REAL
            WHEN dmetaphone_alt(cg.name) = dmetaphone_alt(p_search_text) THEN 0.4::REAL
            WHEN soundex(cg.name) = soundex(p_search_text) THEN
                CASE
                    WHEN difference(cg.name, p_search_text) >= 3 THEN 0.35::REAL
                    WHEN difference(cg.name, p_search_text) = 2 THEN 0.25::REAL
                    WHEN difference(cg.name, p_search_text) = 1 THEN 0.15::REAL
                    ELSE 0::REAL
                END
            ELSE 0::REAL
        END as score,
        cg.raw_json
    FROM chapter_glossary cg
    WHERE cg.book_id = p_book_id
      AND cg.number BETWEEN p_chapter_start AND p_chapter_end
      AND (dmetaphone(cg.name) = dmetaphone(p_search_text) OR dmetaphone_alt(cg.name) = dmetaphone_alt(p_search_text) OR soundex(cg.name) = soundex(p_search_text))
      AND lower(cg.name) != lower(p_search_text) -- Exclude exact matches
      AND NOT (lower(cg.name) LIKE '%' || lower(p_search_text) || '%' -- Exclude contains matches
               OR lower(cg.description) LIKE '%' || lower(p_search_text) || '%')
      AND NOT (cg.search_string2 @@ websearch_to_tsquery('english', p_search_text)) -- Exclude fulltext matches
      AND NOT (cg.name % p_search_text OR cg.search_string3 % p_search_text) -- Exclude trigram matches
      AND levenshtein(lower(cg.name), lower(p_search_text)) > GREATEST(3, length(p_search_text) * 0.3) -- Exclude levenshtein matches
      AND cg.name IS NOT NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION find_glossary_jaro_winkler_matches(
    p_book_id VARCHAR(36),
    p_search_text TEXT,
    p_chapter_start INTEGER DEFAULT 1,
    p_chapter_end INTEGER DEFAULT 999999
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
    SELECT
        cg.id,
        cg.chapter_id,
        cg.name,
        cg.translated_name,
        cg.category,
        cg.description,
        cg.number,
        '7_jaro_winkler'::TEXT as search_type,
        (0.55::REAL * jaro_winkler(cg.name, p_search_text))::REAL as score,
        cg.raw_json
    FROM chapter_glossary cg
    WHERE cg.book_id = p_book_id
      AND cg.number BETWEEN p_chapter_start AND p_chapter_end
      AND jaro_winkler(cg.name, p_search_text) >= 0.7
      AND lower(cg.name) != lower(p_search_text) -- Exclude exact matches
      AND NOT (lower(cg.name) LIKE '%' || lower(p_search_text) || '%' -- Exclude contains matches
               OR lower(cg.description) LIKE '%' || lower(p_search_text) || '%')
      AND NOT (cg.search_string2 @@ websearch_to_tsquery('english', p_search_text)) -- Exclude fulltext matches
      AND NOT (cg.name % p_search_text OR cg.search_string3 % p_search_text) -- Exclude trigram matches
      AND levenshtein(lower(cg.name), lower(p_search_text)) > GREATEST(3, length(p_search_text) * 0.3) -- Exclude levenshtein matches
      AND NOT (dmetaphone(cg.name) = dmetaphone(p_search_text) OR dmetaphone_alt(cg.name) = dmetaphone_alt(p_search_text) OR soundex(cg.name) = soundex(p_search_text)) -- Exclude phonetic matches
      AND cg.name IS NOT NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION find_glossary_fuzzy_matches(
    p_book_id VARCHAR(36),
    p_search_text TEXT,
    p_chapter_start INTEGER DEFAULT 1,
    p_chapter_end INTEGER DEFAULT 999999
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
DECLARE
    search_len INTEGER := length(p_search_text);
    min_len INTEGER := GREATEST(1, search_len - 2);
    max_len INTEGER := search_len + 2;
    trigram_arr TEXT[];
BEGIN
    -- Generate trigrams from search text
    SELECT array_agg(SUBSTR(lower(p_search_text), i, 3))
    INTO trigram_arr
    FROM generate_series(1, search_len - 2) AS i;

    RETURN QUERY
    SELECT
        cg.id,
        cg.chapter_id,
        cg.name,
        cg.translated_name,
        cg.category,
        cg.description,
        cg.number,
        '8_fuzzy'::TEXT as search_type,
        -- Score based on average trigram similarity and length closeness
        (0.7::REAL * GREATEST(0, 1.0 - ABS(length(cg.name) - search_len)::float / search_len::float) +
         0.3::REAL * similarity(cg.name, p_search_text))::REAL as score,
        cg.raw_json
    FROM chapter_glossary cg
    WHERE cg.book_id = p_book_id
      AND cg.number BETWEEN p_chapter_start AND p_chapter_end
      -- Length tolerance: ±2 characters
      AND length(cg.name) BETWEEN min_len AND max_len
      -- Contains at least one trigram from search text
      AND EXISTS (
          SELECT 1
          FROM unnest(trigram_arr) AS trigram
          WHERE lower(cg.name) LIKE '%' || trigram || '%'
      )
      AND lower(cg.name) != lower(p_search_text) -- Exclude exact matches
      AND NOT (lower(cg.name) LIKE '%' || lower(p_search_text) || '%' -- Exclude contains matches
               OR lower(cg.description) LIKE '%' || lower(p_search_text) || '%')
      AND NOT (cg.search_string2 @@ websearch_to_tsquery('english', p_search_text)) -- Exclude fulltext matches
      AND NOT (cg.name % p_search_text OR cg.search_string3 % p_search_text) -- Exclude trigram matches
      AND levenshtein(lower(cg.name), lower(p_search_text)) > GREATEST(3, length(p_search_text) * 0.3) -- Exclude levenshtein matches
      AND NOT (dmetaphone(cg.name) = dmetaphone(p_search_text) OR dmetaphone_alt(cg.name) = dmetaphone_alt(p_search_text) OR soundex(cg.name) = soundex(p_search_text)) -- Exclude phonetic matches
      AND NOT (jaro_winkler(cg.name, p_search_text) >= 0.7) -- Exclude jaro_winkler matches
      AND cg.name IS NOT NULL;
END;
$$ LANGUAGE plpgsql;
