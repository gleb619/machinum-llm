DROP FUNCTION IF EXISTS mt_replace_text;
DROP FUNCTION IF EXISTS mt_replace_text_by_id;
DROP FUNCTION IF EXISTS mt_replace_text_for_column;
DROP FUNCTION IF EXISTS mt_update_glossary_runame;
DROP FUNCTION IF EXISTS mt_replace_summary;

CREATE OR REPLACE FUNCTION mt_replace_text(
    b_id VARCHAR(36),
    p_search TEXT,
    p_replacement TEXT
) RETURNS INTEGER AS $$
DECLARE
    rows_affected INTEGER := 0;
BEGIN
    WITH updated AS (
        UPDATE chapter_info
        SET
            raw_text = REPLACE(raw_text, p_search, p_replacement),
            text = REPLACE(text, p_search, p_replacement),
            proofread_text = REPLACE(proofread_text, p_search, p_replacement),
            translated_text = REPLACE(translated_text, p_search, p_replacement),
            fixed_translated_text = REPLACE(fixed_translated_text, p_search, p_replacement),
            summary = REPLACE(summary, p_search, p_replacement)
        WHERE book_id = b_id
        AND (raw_text LIKE '%' || p_search || '%'
          OR text LIKE '%' || p_search || '%'
          OR proofread_text LIKE '%' || p_search || '%'
          OR translated_text LIKE '%' || p_search || '%'
          OR fixed_translated_text LIKE '%' || p_search || '%'
          OR summary LIKE '%' || p_search || '%')
        RETURNING 1
    )
    SELECT COUNT(*) INTO rows_affected FROM updated;

    WITH updated_json AS (
        UPDATE chapter_info
        SET
            translated_chunks = REPLACE(translated_chunks::text, p_search, p_replacement)::json,
            fixed_translated_chunks = REPLACE(fixed_translated_chunks::text, p_search, p_replacement)::json
        WHERE book_id = b_id
        AND (translated_chunks::text LIKE '%' || p_search || '%'
          OR fixed_translated_chunks::text LIKE '%' || p_search || '%')
        RETURNING 1
    )
    SELECT rows_affected + COUNT(*) INTO rows_affected FROM updated_json;

    RETURN rows_affected;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION mt_replace_text_by_id(
    c_id VARCHAR(36),
    p_search TEXT,
    p_replacement TEXT
) RETURNS INTEGER AS $$
DECLARE
    rows_affected INTEGER := 0;
BEGIN
    WITH updated AS (
        UPDATE chapter_info
        SET
            raw_text = REPLACE(raw_text, p_search, p_replacement),
            text = REPLACE(text, p_search, p_replacement),
            proofread_text = REPLACE(proofread_text, p_search, p_replacement),
            translated_text = REPLACE(translated_text, p_search, p_replacement),
            fixed_translated_text = REPLACE(fixed_translated_text, p_search, p_replacement),
            summary = REPLACE(summary, p_search, p_replacement)
        WHERE id = c_id
        AND (raw_text LIKE '%' || p_search || '%'
          OR text LIKE '%' || p_search || '%'
          OR proofread_text LIKE '%' || p_search || '%'
          OR translated_text LIKE '%' || p_search || '%'
          OR fixed_translated_text LIKE '%' || p_search || '%'
          OR summary LIKE '%' || p_search || '%')
        RETURNING 1
    )
    SELECT COUNT(*) INTO rows_affected FROM updated;

    WITH updated_json AS (
        UPDATE chapter_info
        SET
            translated_chunks = REPLACE(translated_chunks::text, p_search, p_replacement)::json,
            fixed_translated_chunks = REPLACE(fixed_translated_chunks::text, p_search, p_replacement)::json
        WHERE id = c_id
        AND (translated_chunks::text LIKE '%' || p_search || '%'
          OR fixed_translated_chunks::text LIKE '%' || p_search || '%')
        RETURNING 1
    )
    SELECT rows_affected + COUNT(*) INTO rows_affected FROM updated_json;

    RETURN rows_affected;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION mt_replace_text_for_column(
    c_id VARCHAR(36),
    p_column_name TEXT,
    p_search TEXT,
    p_replacement TEXT
) RETURNS INTEGER AS $$
DECLARE
    rows_affected INTEGER := 0;
BEGIN
    CASE p_column_name
        WHEN 'translated_text' THEN
            WITH updated AS (
                UPDATE chapter_info
                SET translated_text = REPLACE(translated_text, p_search, p_replacement)
                WHERE id = c_id AND translated_text LIKE '%' || p_search || '%'
                RETURNING 1
            )
            SELECT COUNT(*) INTO rows_affected FROM updated;

        WHEN 'fixed_translated_text' THEN
            WITH updated AS (
                UPDATE chapter_info
                SET fixed_translated_text = REPLACE(fixed_translated_text, p_search, p_replacement)
                WHERE id = c_id AND fixed_translated_text LIKE '%' || p_search || '%'
                RETURNING 1
            )
            SELECT COUNT(*) INTO rows_affected FROM updated;

        WHEN 'translated_chunks' THEN
            WITH updated AS (
                UPDATE chapter_info
                SET translated_chunks = REPLACE(translated_chunks::text, p_search, p_replacement)::json
                WHERE id = c_id AND translated_chunks::text LIKE '%' || p_search || '%'
                RETURNING 1
            )
            SELECT COUNT(*) INTO rows_affected FROM updated;

        WHEN 'fixed_translated_chunks' THEN
            WITH updated AS (
                UPDATE chapter_info
                SET fixed_translated_chunks = REPLACE(fixed_translated_chunks::text, p_search, p_replacement)::json
                WHERE id = c_id AND fixed_translated_chunks::text LIKE '%' || p_search || '%'
                RETURNING 1
            )
            SELECT COUNT(*) INTO rows_affected FROM updated;

        WHEN 'summary' THEN
            WITH updated AS (
                UPDATE chapter_info
                SET summary = REPLACE(summary, p_search, p_replacement)
                WHERE id = c_id AND summary LIKE '%' || p_search || '%'
                RETURNING 1
            )
            SELECT COUNT(*) INTO rows_affected FROM updated;

        ELSE
            RAISE EXCEPTION 'Invalid column name: %', p_column_name;
    END CASE;

    RETURN rows_affected;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION mt_replace_summary(
    b_id VARCHAR(36),
    p_search TEXT,
    p_replacement TEXT
) RETURNS INTEGER AS $$
DECLARE
    rows_affected INTEGER;
BEGIN
    WITH updated AS (
        UPDATE chapter_info
        SET summary = REPLACE(summary, p_search, p_replacement)
        WHERE book_id = b_id AND summary LIKE '%' || p_search || '%'
        RETURNING 1
    )
    SELECT COUNT(*) INTO rows_affected FROM updated;

    RETURN rows_affected;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION mt_update_glossary_runame(
    b_id VARCHAR(36),
    old_ru_name TEXT,
    new_ru_name TEXT,
    p_return_ids BOOLEAN DEFAULT FALSE
) RETURNS TEXT AS $$
DECLARE
    result_json TEXT;
BEGIN
    IF p_return_ids THEN
        SELECT jsonb_agg(DISTINCT ci.id)::TEXT INTO result_json
        FROM chapter_info ci, jsonb_array_elements(ci.names) AS elem
        WHERE ci.book_id = b_id AND elem->>'ruName' = old_ru_name;
    ELSE
        WITH updated AS (
            UPDATE chapter_info
            SET names = (
                SELECT jsonb_agg(
                    CASE WHEN elem->>'ruName' = old_ru_name
                    THEN jsonb_set(elem, '{ruName}', to_jsonb(new_ru_name))
                    ELSE elem END
                )
                FROM jsonb_array_elements(names) AS elem
            )
            WHERE book_id = b_id
            AND EXISTS (
                SELECT 1 FROM jsonb_array_elements(names) AS elem
                WHERE elem->>'ruName' = old_ru_name
            )
            RETURNING id
        )
        SELECT jsonb_agg(id)::TEXT INTO result_json FROM updated;
    END IF;

    RETURN COALESCE(result_json, '[]');
END;
$$ LANGUAGE plpgsql;