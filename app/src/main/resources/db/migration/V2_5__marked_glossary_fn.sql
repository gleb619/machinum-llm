CREATE OR REPLACE FUNCTION mt_toggle_glossary_mark(
    b_id VARCHAR(36),
    glossary_name TEXT,
    p_marked BOOLEAN,
    name_filter TEXT DEFAULT NULL
) RETURNS TEXT AS $$
DECLARE
    result_json TEXT;
BEGIN
    WITH updated AS (
        UPDATE chapter_info
        SET names = (
            SELECT json_agg(
                CASE WHEN elem->>'name' = glossary_name
                THEN (jsonb_set(elem::jsonb, '{marked}', to_jsonb(p_marked)))::json
                ELSE elem END
            )
            FROM json_array_elements(names) AS elem
        )
        WHERE book_id = b_id
        AND EXISTS (
            SELECT 1 FROM json_array_elements(names) AS elem
            WHERE elem->>'name' = glossary_name
            AND (name_filter IS NULL OR elem->>'name' = name_filter)
        )
        RETURNING id
    )
    SELECT json_agg(id)::TEXT INTO result_json FROM updated;

    RETURN COALESCE(result_json, '[]');
END;
$$ LANGUAGE plpgsql;
