-- Create necessary extensions
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS fuzzystrmatch;

-- Create Jaro-Winkler function if not exists
CREATE OR REPLACE FUNCTION jaro_winkler(text, text)
RETURNS float AS $$
DECLARE
  s1 text := UPPER($1);
  s2 text := UPPER($2);
  len1 int := LENGTH(s1);
  len2 int := LENGTH(s2);
  match_distance int;
  matches int := 0;
  transpositions int := 0;
  s1_matched boolean[];
  s2_matched boolean[];
  i int;
  j int;
  k int;
  jaro float;
  prefix int := 0;
BEGIN
  IF len1 = 0 AND len2 = 0 THEN RETURN 1.0; END IF;
  IF len1 = 0 OR len2 = 0 THEN RETURN 0.0; END IF;

  match_distance := GREATEST(len1, len2) / 2 - 1;
  IF match_distance < 0 THEN match_distance := 0; END IF;

  s1_matched := array_fill(false, ARRAY[len1]);
  s2_matched := array_fill(false, ARRAY[len2]);

  FOR i IN 1..len1 LOOP
    j := GREATEST(1, i - match_distance);
    WHILE j <= LEAST(len2, i + match_distance) LOOP
      IF NOT s2_matched[j] AND SUBSTR(s1, i, 1) = SUBSTR(s2, j, 1) THEN
        s1_matched[i] := true;
        s2_matched[j] := true;
        matches := matches + 1;
        EXIT;
      END IF;
      j := j + 1;
    END LOOP;
  END LOOP;

  IF matches = 0 THEN RETURN 0.0; END IF;

  k := 1;
  FOR i IN 1..len1 LOOP
    IF s1_matched[i] THEN
      WHILE NOT s2_matched[k] LOOP
        k := k + 1;
      END LOOP;
      IF SUBSTR(s1, i, 1) != SUBSTR(s2, k, 1) THEN
        transpositions := transpositions + 1;
      END IF;
      k := k + 1;
    END IF;
  END LOOP;

  jaro := (matches::float / len1 + matches::float / len2 + (matches - transpositions / 2.0) / matches) / 3.0;

  WHILE prefix < 4 AND prefix < len1 AND prefix < len2 AND SUBSTR(s1, prefix + 1, 1) = SUBSTR(s2, prefix + 1, 1) LOOP
    prefix := prefix + 1;
  END LOOP;

  RETURN jaro + prefix * 0.1 * (1.0 - jaro);
END;
$$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE;

-- Create match result type
DROP TYPE IF EXISTS match_result_type CASCADE;
CREATE TYPE match_result_type AS (
    search_type TEXT,
    score REAL
);

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
DECLARE
    search_lower TEXT := lower(p_search_text);
    search_len INT := length(p_search_text);
    lev_threshold INT := GREATEST(3, (search_len * 0.3)::INT);
    pg_fuzzy_threshold FLOAT := 0.3;
BEGIN
    RETURN QUERY
    WITH base_data AS (
        SELECT
            cg.id,
            cg.chapter_id,
            cg.name,
            cg.translated_name,
            cg.category,
            cg.description,
            cg.number,
            cg.raw_json,
            cg.search_string1,
            cg.search_string2,
            cg.search_string3,
            lower(cg.name) as name_lower,
            lower(cg.description) as desc_lower
        FROM chapter_glossary cg
        WHERE cg.book_id = p_book_id
          AND cg.number BETWEEN p_chapter_start AND p_chapter_end
          AND cg.name IS NOT NULL
    ),
    scored_results AS (
        SELECT
            bd.id,
            bd.chapter_id,
            bd.name,
            bd.translated_name,
            bd.category,
            bd.description,
            bd.number,
            bd.raw_json,
            CASE
                WHEN bd.name_lower = search_lower THEN
                    ROW('1_exact', 1.0::REAL)::match_result_type
                WHEN bd.name_lower LIKE '%' || search_lower || '%' THEN
                    ROW('2_contains', 0.9::REAL)::match_result_type
                WHEN bd.desc_lower LIKE '%' || search_lower || '%' THEN
                    ROW('2_contains', 0.7::REAL)::match_result_type
                WHEN bd.search_string2 @@ websearch_to_tsquery('english', p_search_text) THEN
                    ROW('3_fulltext', GREATEST(
                        ts_rank(bd.search_string1, websearch_to_tsquery('english', p_search_text)),
                        ts_rank(bd.search_string2, websearch_to_tsquery('english', p_search_text))
                    )::REAL)::match_result_type
                WHEN bd.name % p_search_text OR bd.search_string3 % p_search_text THEN
                    ROW('4_trigram', GREATEST(
                        similarity(bd.name, p_search_text),
                        similarity(bd.search_string3, p_search_text)
                    )::REAL)::match_result_type
                WHEN levenshtein(bd.name_lower, search_lower) <= lev_threshold THEN
                    ROW('5_levenshtein', (1.0 / (1.0 + levenshtein(bd.name_lower, search_lower)::FLOAT))::REAL)::match_result_type
                WHEN dmetaphone(bd.name) = dmetaphone(p_search_text)
                     OR dmetaphone_alt(bd.name) = dmetaphone(p_search_text)
                     OR soundex(bd.name) = soundex(p_search_text) THEN
                    ROW('6_phonetic', 0.6::REAL)::match_result_type
                WHEN jaro_winkler(bd.name, p_search_text) >= 0.8 THEN
                    ROW('7_jaro_winkler', jaro_winkler(bd.name, p_search_text)::REAL)::match_result_type
                WHEN word_similarity(p_search_text, bd.name) >= pg_fuzzy_threshold
                     OR word_similarity(p_search_text, bd.description) >= pg_fuzzy_threshold THEN
                    ROW('8_pg_fuzzy', GREATEST(
                        word_similarity(p_search_text, bd.name),
                        word_similarity(p_search_text, COALESCE(bd.description, ''))
                    )::REAL)::match_result_type
            END as match_result
        FROM base_data bd
    )
    SELECT
        sr.id,
        sr.chapter_id,
        sr.name,
        sr.translated_name,
        sr.category,
        sr.description,
        sr.number,
        (sr.match_result).search_type,
        (sr.match_result).score,
        sr.raw_json
    FROM scored_results sr
    WHERE sr.match_result IS NOT NULL
      AND (sr.match_result).score >= p_min_score
    ORDER BY (sr.match_result).score DESC, sr.number DESC
    LIMIT p_top_k;
END;
$$ LANGUAGE plpgsql;

DROP FUNCTION IF EXISTS fuzzy_search_glossary;

CREATE OR REPLACE FUNCTION fuzzy_search_glossary(
    p_book_id VARCHAR(36),
    p_fuzzy_query TEXT,
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
DECLARE
    search_data JSONB;
    search_term TEXT;
    min_len INT;
    max_len INT;
    ngram_list TEXT[];
BEGIN
    -- Parse the JSON fuzzy query
    search_data := p_fuzzy_query::JSONB;
    search_term := search_data->>'query';
    min_len := (search_data->'lengthRange'->>'min')::INT;
    max_len := (search_data->'lengthRange'->>'max')::INT;
    ngram_list := ARRAY(SELECT jsonb_array_elements_text(search_data->'nGrams'));

    RETURN QUERY
    WITH base_data AS (
        SELECT
            cg.id,
            cg.chapter_id,
            cg.name,
            cg.translated_name,
            cg.category,
            cg.description,
            cg.number,
            cg.raw_json,
            lower(cg.name) as name_lower
        FROM chapter_glossary cg
        WHERE cg.book_id = p_book_id
          AND cg.number BETWEEN p_chapter_start AND p_chapter_end
          AND cg.name IS NOT NULL
          AND length(cg.name) BETWEEN min_len AND max_len
    ),
    scored_results AS (
        SELECT
            bd.id,
            bd.chapter_id,
            bd.name,
            bd.translated_name,
            bd.category,
            bd.description,
            bd.number,
            bd.raw_json,
            CASE
                WHEN bd.name_lower = search_term THEN
                    ROW('1_exact', 1.0::REAL)::match_result_type
                WHEN bd.name_lower LIKE '%' || search_term || '%' THEN
                    ROW('2_contains', 0.9::REAL)::match_result_type
                ELSE
                    ROW('3_fuzzy_ngram', (
                        SELECT COUNT(*)
                        FROM unnest(ngram_list) AS ng
                        WHERE bd.name_lower LIKE '%' || ng || '%'
                    )::REAL / GREATEST(1, array_length(ngram_list, 1))::REAL)::match_result_type
            END as match_result
        FROM base_data bd
    )
    SELECT
        sr.id,
        sr.chapter_id,
        sr.name,
        sr.translated_name,
        sr.category,
        sr.description,
        sr.number,
        (sr.match_result).search_type,
        (sr.match_result).score,
        sr.raw_json
    FROM scored_results sr
    WHERE sr.match_result IS NOT NULL
      AND (sr.match_result).score >= p_min_score
    ORDER BY (sr.match_result).score DESC, sr.number DESC
    LIMIT p_top_k;
END;
$$ LANGUAGE plpgsql;
