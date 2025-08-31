DROP FUNCTION IF EXISTS replace_text_in_chapter;
DROP FUNCTION IF EXISTS replace_text_in_chapter_by_id;
DROP FUNCTION IF EXISTS replace_text_in_for_column;
DROP FUNCTION IF EXISTS update_json_names_by_book_id;
DROP FUNCTION IF EXISTS replace_summary_in_chapter;

-- Function to replace text for a specific Book ID
CREATE OR REPLACE FUNCTION replace_text_in_chapter(
    b_id VARCHAR(36),
    p_search TEXT,
    p_replacement TEXT
) RETURNS VOID AS $$
BEGIN
    -- Replace text in standard text columns
    UPDATE chapter_info
    SET
        raw_text = REPLACE(raw_text, p_search, p_replacement),
        text = REPLACE(text, p_search, p_replacement),
        proofread_text = REPLACE(proofread_text, p_search, p_replacement),
        translated_text = REPLACE(translated_text, p_search, p_replacement),
        fixed_translated_text = REPLACE(fixed_translated_text, p_search, p_replacement),
        summary = REPLACE(summary, p_search, p_replacement)
    WHERE book_id = b_id 
    AND (
         raw_text LIKE '%' || p_search || '%'
      OR text LIKE '%' || p_search || '%'
      OR proofread_text LIKE '%' || p_search || '%'
      OR translated_text LIKE '%' || p_search || '%'
      OR fixed_translated_text LIKE '%' || p_search || '%'
      OR summary LIKE '%' || p_search || '%'
    );
    
    -- Replace text in JSON chunks
    UPDATE chapter_info
    SET 
        translated_chunks = REPLACE(translated_chunks::text, p_search, p_replacement)::json,
        fixed_translated_chunks = REPLACE(fixed_translated_chunks::text, p_search, p_replacement)::json
    WHERE book_id = b_id 
    AND (translated_chunks::text LIKE '%' || p_search || '%' 
      OR fixed_translated_chunks::text LIKE '%' || p_search || '%');
END;
$$ LANGUAGE plpgsql;

-- Function to replace text for a specific ID
CREATE OR REPLACE FUNCTION replace_text_in_chapter_by_id(
    c_id VARCHAR(36),
    p_search TEXT,
    p_replacement TEXT
) RETURNS VOID AS $$
BEGIN
    -- Replace text in standard text columns for specific ID
    UPDATE chapter_info
    SET
        raw_text = REPLACE(raw_text, p_search, p_replacement),
        text = REPLACE(text, p_search, p_replacement),
        proofread_text = REPLACE(proofread_text, p_search, p_replacement),
        translated_text = REPLACE(translated_text, p_search, p_replacement),
        fixed_translated_text = REPLACE(fixed_translated_text, p_search, p_replacement),
        summary = REPLACE(summary, p_search, p_replacement)
    WHERE id = c_id 
    AND (
         raw_text LIKE '%' || p_search || '%'
      OR text LIKE '%' || p_search || '%'
      OR proofread_text LIKE '%' || p_search || '%'
      OR translated_text LIKE '%' || p_search || '%'
      OR fixed_translated_text LIKE '%' || p_search || '%' 
      OR summary LIKE '%' || p_search || '%');

    -- Replace text in JSON chunks for specific ID
    UPDATE chapter_info
    SET
        translated_chunks = REPLACE(translated_chunks::text, p_search, p_replacement)::json,
        fixed_translated_chunks = REPLACE(fixed_translated_chunks::text, p_search, p_replacement)::json
    WHERE id = c_id 
    AND (translated_chunks::text LIKE '%' || p_search || '%' OR fixed_translated_chunks::text LIKE '%' || p_search || '%');
END;
$$ LANGUAGE plpgsql;

-- Function to replace text in a specific column
CREATE OR REPLACE FUNCTION replace_text_in_for_column(
    c_id VARCHAR(36),
    p_column_name TEXT,
    p_search TEXT,
    p_replacement TEXT
) RETURNS VOID AS $$
DECLARE
    sql_query TEXT;
BEGIN
    -- Check which column to update and perform appropriate action
    CASE p_column_name
        WHEN 'translated_text' THEN
            UPDATE chapter_info
            SET translated_text = REPLACE(translated_text, p_search, p_replacement)
            WHERE id = c_id 
            AND translated_text LIKE '%' || p_search || '%';

        WHEN 'fixed_translated_text' THEN
            UPDATE chapter_info
            SET fixed_translated_text = REPLACE(fixed_translated_text, p_search, p_replacement)
            WHERE id = c_id 
            AND fixed_translated_text LIKE '%' || p_search || '%';

        WHEN 'translated_chunks' THEN
            UPDATE chapter_info
            SET translated_chunks = REPLACE(translated_chunks::text, p_search, p_replacement)::json
            WHERE id = c_id 
            AND translated_chunks::text LIKE '%' || p_search || '%';

        WHEN 'fixed_translated_chunks' THEN
            UPDATE chapter_info
            SET fixed_translated_chunks = REPLACE(fixed_translated_chunks::text, p_search, p_replacement)::json
            WHERE id = c_id 
            AND fixed_translated_chunks::text LIKE '%' || p_search || '%';

        WHEN 'summary' THEN
            UPDATE chapter_info
            SET summary = REPLACE(summary, p_search, p_replacement)
            WHERE id = c_id 
            AND summary LIKE '%' || p_search || '%';

        ELSE
            RAISE EXCEPTION 'Invalid column name: %', p_column_name;
    END CASE;
END;
$$ LANGUAGE plpgsql;

-- Function to replace summary in chapter by ID
CREATE OR REPLACE FUNCTION replace_summary_in_chapter(
    b_id VARCHAR(36),
    p_search TEXT,
    p_replacement TEXT
) RETURNS VOID AS $$
BEGIN
    UPDATE chapter_info
    SET summary = REPLACE(summary, p_search, p_replacement)
    WHERE book_id = b_id
    AND summary LIKE '%' || p_search || '%';
END;
$$ LANGUAGE plpgsql;

-- Function to update JSON names in chapter_info based on book_id
CREATE OR REPLACE FUNCTION update_json_names_by_book_id(
    b_id VARCHAR(36),
    old_ru_name TEXT,
    new_ru_name TEXT,
    p_return_ids BOOLEAN DEFAULT FALSE
) RETURNS SETOF VARCHAR AS $$
BEGIN
    IF p_return_ids THEN
        RETURN QUERY
        SELECT DISTINCT ci.id::VARCHAR
        FROM chapter_info ci, jsonb_array_elements(ci.names) AS elem
        WHERE ci.book_id = b_id AND elem->>'ruName' = old_ru_name;
    ELSE
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
        );
    END IF;
END;
$$ LANGUAGE plpgsql;