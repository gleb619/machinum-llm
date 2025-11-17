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
               cg.search_string3,
               dmetaphone(cg.name) as metaphone_code,
               dmetaphone_alt(cg.name) as metaphone_alt,
               soundex(cg.name) as soundex_code,
               jaro_winkler(cg.name, p_search_text) as jw_score
        FROM chapter_glossary cg
        WHERE cg.book_id = p_book_id
          AND cg.number between p_chapter_start and p_chapter_end
          AND cg.name IS NOT NULL
    ),
    exact_match AS (
        SELECT bd.id, '1_exact' as search_type, 1.0::REAL as score
        FROM base_data bd
        WHERE lower(bd.name) = lower(p_search_text)
    ),
    contains_match AS (
        SELECT bd.id, '2_contains' as search_type,
               CASE
                   WHEN lower(bd.name) LIKE '%' || lower(p_search_text) || '%' THEN 0.9::REAL
                   WHEN lower(bd.description) LIKE '%' || lower(p_search_text) || '%' THEN 0.7::REAL
                   ELSE 0.5::REAL
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
               (1.0 / (1.0 + levenshtein(lower(bd.name), lower(p_search_text))::float))::REAL as score
        FROM base_data bd
        WHERE levenshtein(lower(bd.name), lower(p_search_text)) <= GREATEST(3, length(p_search_text) * 0.3)
          AND bd.id NOT IN (
              SELECT id FROM exact_match
              UNION SELECT id FROM contains_match
              UNION SELECT id FROM fulltext_search
              UNION SELECT id FROM trigram_search
          )
    ),
    double_metaphone AS (
        SELECT bd.id, '6_double_metaphone' as search_type,
               CASE
                   WHEN bd.metaphone_code = dmetaphone(p_search_text) THEN 0.6::REAL
                   WHEN bd.metaphone_alt = dmetaphone_alt(p_search_text) THEN 0.4::REAL
                   ELSE 0.0::REAL
               END as score
        FROM base_data bd
        WHERE bd.metaphone_code = dmetaphone(p_search_text) OR bd.metaphone_alt = dmetaphone_alt(p_search_text)
          AND bd.id NOT IN (
              SELECT id FROM exact_match
              UNION SELECT id FROM contains_match
              UNION SELECT id FROM fulltext_search
              UNION SELECT id FROM trigram_search
              UNION SELECT id FROM levenshtein_search
          )
    ),
    jaro_winkler_search AS (
        SELECT bd.id, '7_jaro_winkler' as search_type,
               (0.55::REAL * bd.jw_score)::REAL as score
        FROM base_data bd
        WHERE bd.jw_score >= 0.7
          AND bd.id NOT IN (
              SELECT id FROM exact_match
              UNION SELECT id FROM contains_match
              UNION SELECT id FROM fulltext_search
              UNION SELECT id FROM trigram_search
              UNION SELECT id FROM levenshtein_search
              UNION SELECT id FROM double_metaphone
          )
    ),
    soundex_search AS (
        SELECT bd.id, '8_soundex' as search_type,
               CASE
                   WHEN difference(bd.name, p_search_text) >= 3 THEN 0.35::REAL
                   WHEN difference(bd.name, p_search_text) = 2 THEN 0.25::REAL
                   WHEN difference(bd.name, p_search_text) = 1 THEN 0.15::REAL
                   ELSE 0::REAL
               END as score
        FROM base_data bd
        WHERE bd.soundex_code = soundex(p_search_text)
          AND bd.id NOT IN (
              SELECT id FROM exact_match
              UNION SELECT id FROM contains_match
              UNION SELECT id FROM fulltext_search
              UNION SELECT id FROM trigram_search
              UNION SELECT id FROM levenshtein_search
              UNION SELECT id FROM double_metaphone
              UNION SELECT id FROM jaro_winkler_search
          )
    ),
    all_results AS (
        SELECT * FROM exact_match
        UNION ALL SELECT * FROM contains_match
        UNION ALL SELECT * FROM fulltext_search
        UNION ALL SELECT * FROM trigram_search
        UNION ALL SELECT * FROM levenshtein_search
        UNION ALL SELECT * FROM double_metaphone
        UNION ALL SELECT * FROM jaro_winkler_search
        UNION ALL SELECT * FROM soundex_search
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
