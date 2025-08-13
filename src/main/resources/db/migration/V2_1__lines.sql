DROP TRIGGER IF EXISTS refresh_lines_info_tr ON chapter_info;
DROP FUNCTION IF EXISTS refresh_lines_info_fn(INTEGER);
DROP MATERIALIZED VIEW IF EXISTS lines_info;

DROP INDEX IF EXISTS lines_info_id_key;
DROP INDEX IF EXISTS idx_lines_info_chapter_id;
DROP INDEX IF EXISTS idx_lines_info_book_id;
DROP INDEX IF EXISTS idx_lines_info_source_key;
DROP INDEX IF EXISTS idx_lines_info_original_sentences;
DROP INDEX IF EXISTS idx_lines_info_translated_sentences;
DROP INDEX IF EXISTS idx_lines_info_original_line_ts;
DROP INDEX IF EXISTS idx_lines_info_translated_line_ts;
DROP INDEX IF EXISTS idx_lines_info_original_line_gin;
DROP INDEX IF EXISTS idx_lines_info_translated_line_gin;

/* Create new lines_info objects */


-- Configuration table
CREATE TABLE line_settings (
    id SERIAL PRIMARY KEY,
    book_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    data JSONB NOT NULL,
    settings_hash VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(book_id, name)
);

-- Function to create single sub materialized view
CREATE OR REPLACE FUNCTION create_single_sub_view(
    p_view_name TEXT,
    p_book_id VARCHAR(36),
    p_start_chapter INTEGER DEFAULT NULL,
    p_end_chapter INTEGER DEFAULT NULL
)
RETURNS boolean AS $$
DECLARE
    sql_cmd TEXT;
    chapter_filter TEXT := '';
BEGIN
    -- Drop existing view if exists
    EXECUTE format('DROP MATERIALIZED VIEW IF EXISTS %I CASCADE', p_view_name);

    -- Build chapter filter
    IF p_start_chapter IS NOT NULL AND p_end_chapter IS NOT NULL THEN
        chapter_filter := format('
            WITH ranked_chapters AS (
                SELECT *, ROW_NUMBER() OVER (ORDER BY number) as rn
                FROM chapter_info
                WHERE book_id = ''%s''
            )
            SELECT ci0.*
            FROM ranked_chapters ci0
            WHERE ci0.rn BETWEEN %s AND %s',
            p_book_id, p_start_chapter, p_end_chapter);
    ELSE
        chapter_filter := format('SELECT * FROM chapter_info WHERE book_id = ''%s''', p_book_id);
    END IF;

    sql_cmd := format('
        CREATE MATERIALIZED VIEW %I AS
        WITH source_chapters AS (%s),
        split_data AS (
            SELECT
                ci0.id,
                ci0.source_key,
                ci0.number,
                ci0.book_id,
                unnest(string_to_array(ci0.text, E''\n'')) AS original_line,
                unnest(string_to_array(ci0.translated_text, E''\n'')) AS translated_line,
                generate_series(1, 1000000) AS line_index
            FROM source_chapters ci0
            ORDER BY ci0.number
        )
        SELECT
            MD5(ci1.id || ''-'' || ci1.line_index)::text AS id,
            ci1.id AS chapter_id,
            ci1.source_key,
            ci1.number,
            ci1.book_id,
            ci1.line_index,
            ci1.original_line,
            ci1.translated_line,
            to_jsonb(
                regexp_split_to_array(
                    regexp_replace(ci1.original_line, ''([.!?])\s+'', ''\1|'', ''g''),
                    ''\|''
            )) AS original_sentences,
            to_jsonb(
                regexp_split_to_array(
                regexp_replace(ci1.translated_line, ''([.!?])\s+'', ''\1|'', ''g''),
                ''\|''
            )) AS translated_sentences
        FROM split_data ci1
        WHERE LENGTH(original_line) > 0 OR LENGTH(translated_line) > 0',
        p_view_name, chapter_filter);

    EXECUTE sql_cmd;

    RETURN TRUE;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'Error creating view %: %', p_view_name, SQLERRM;
        RETURN FALSE;
END;
$$ LANGUAGE plpgsql;

-- Function to create indexes for specific view
CREATE OR REPLACE FUNCTION create_view_indexes(p_view_name TEXT)
RETURNS boolean AS $$
DECLARE
    sql_cmd TEXT;
BEGIN
    -- Primary key index
    sql_cmd := format('CREATE UNIQUE INDEX IF NOT EXISTS %I ON %I (id)', p_view_name || '_id_idx', p_view_name);
    EXECUTE sql_cmd;

    -- Chapter ID index
    sql_cmd := format('CREATE INDEX IF NOT EXISTS %I ON %I (chapter_id)', p_view_name || '_chapter_id_idx', p_view_name);
    EXECUTE sql_cmd;

    -- Book ID index
    sql_cmd := format('CREATE INDEX IF NOT EXISTS %I ON %I (book_id)', p_view_name || '_book_id_idx', p_view_name);
    EXECUTE sql_cmd;

    -- Composite index
    sql_cmd := format('CREATE INDEX IF NOT EXISTS %I ON %I (book_id, number)', p_view_name || '_book_chapter_idx', p_view_name);
    EXECUTE sql_cmd;

    RETURN TRUE;
EXCEPTION
    WHEN OTHERS THEN
        RETURN FALSE;
END;
$$ LANGUAGE plpgsql;

-- Function to refresh single materialized view
CREATE OR REPLACE FUNCTION refresh_single_view(p_view_name TEXT)
RETURNS boolean AS $$
BEGIN
    EXECUTE format('REFRESH MATERIALIZED VIEW CONCURRENTLY %I', p_view_name);
    RETURN TRUE;
EXCEPTION
    WHEN OTHERS THEN
        RETURN FALSE;
END;
$$ LANGUAGE plpgsql;

-- Function to drop single view
CREATE OR REPLACE FUNCTION drop_single_view(p_view_name TEXT)
RETURNS boolean AS $$
BEGIN
    EXECUTE format('DROP MATERIALIZED VIEW IF EXISTS %I CASCADE', p_view_name);
    RETURN TRUE;
EXCEPTION
    WHEN OTHERS THEN
        RETURN FALSE;
END;
$$ LANGUAGE plpgsql;

-- Function to check if view exists
CREATE OR REPLACE FUNCTION view_exists(p_view_name TEXT)
RETURNS boolean AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = p_view_name
        AND table_type = 'BASE TABLE'
    );
END;
$$ LANGUAGE plpgsql;

-- Function to create lines_info view from view names list
CREATE OR REPLACE FUNCTION create_lines_info_view(p_view_names TEXT[])
RETURNS boolean AS $$
DECLARE
    union_query TEXT := '';
    view_name TEXT;
    first_view BOOLEAN := TRUE;
BEGIN
    -- Build union query
    FOREACH view_name IN ARRAY p_view_names
    LOOP
        IF first_view THEN
            union_query := format('SELECT * FROM %I', view_name);
            first_view := FALSE;
        ELSE
            union_query := union_query || format(' UNION ALL SELECT * FROM %I', view_name);
        END IF;
    END LOOP;

    -- Drop and recreate the view
    DROP VIEW IF EXISTS lines_info CASCADE;

    IF union_query != '' THEN
        EXECUTE format('CREATE OR REPLACE VIEW lines_info AS %s', union_query);
    END IF;

    RETURN TRUE;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'Error creating lines_info: %', SQLERRM;
        RETURN FALSE;
END;
$$ LANGUAGE plpgsql;

-- Function to get chapter count for book
CREATE OR REPLACE FUNCTION get_chapter_count(p_book_id VARCHAR(36))
RETURNS INTEGER AS $$
DECLARE
    chapter_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO chapter_count FROM chapter_info WHERE book_id = p_book_id;
    RETURN chapter_count;
END;
$$ LANGUAGE plpgsql;