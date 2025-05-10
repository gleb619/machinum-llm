DROP FUNCTION IF EXISTS replace_text_in_chapter;
DROP FUNCTION IF EXISTS replace_text_in_chapter_by_id;
DROP FUNCTION IF EXISTS replace_text_in_for_column;

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
        translated_text = REPLACE(translated_text, p_search, p_replacement),
        fixed_translated_text = REPLACE(fixed_translated_text, p_search, p_replacement)
    WHERE book_id = b_id;
    
    -- Replace text in JSON chunks
    UPDATE chapter_info
    SET 
        translated_chunks = REPLACE(translated_chunks::text, p_search, p_replacement)::json,
        fixed_translated_chunks = REPLACE(fixed_translated_chunks::text, p_search, p_replacement)::json
    WHERE book_id = b_id;
END;
$$ LANGUAGE plpgsql;

-- Function to replace text for a specific ID
CREATE OR REPLACE FUNCTION replace_text_in_chapter_by_id(
    p_id VARCHAR(36),
    p_search TEXT,
    p_replacement TEXT
) RETURNS VOID AS $$
BEGIN
    -- Replace text in standard text columns for specific ID
    UPDATE chapter_info
    SET
        translated_text = REPLACE(translated_text, p_search, p_replacement),
        fixed_translated_text = REPLACE(fixed_translated_text, p_search, p_replacement)
    WHERE id = p_id;

    -- Replace text in JSON chunks for specific ID
    UPDATE chapter_info
    SET
        translated_chunks = REPLACE(translated_chunks::text, p_search, p_replacement)::json,
        fixed_translated_chunks = REPLACE(fixed_translated_chunks::text, p_search, p_replacement)::json
    WHERE id = p_id;
END;
$$ LANGUAGE plpgsql;

-- Function to replace text in a specific column
CREATE OR REPLACE FUNCTION replace_text_in_for_column(
    p_id VARCHAR(36),
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
            WHERE id = p_id;

        WHEN 'fixed_translated_text' THEN
            UPDATE chapter_info
            SET fixed_translated_text = REPLACE(fixed_translated_text, p_search, p_replacement)
            WHERE id = p_id;

        WHEN 'translated_chunks' THEN
            UPDATE chapter_info
            SET translated_chunks = REPLACE(translated_chunks::text, p_search, p_replacement)::json
            WHERE id = p_id;

        WHEN 'fixed_translated_chunks' THEN
            UPDATE chapter_info
            SET fixed_translated_chunks = REPLACE(fixed_translated_chunks::text, p_search, p_replacement)::json
            WHERE id = p_id;

        ELSE
            RAISE EXCEPTION 'Invalid column name: %', p_column_name;
    END CASE;
END;
$$ LANGUAGE plpgsql;