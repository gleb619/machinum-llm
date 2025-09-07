-- Drop migration objects
DROP TRIGGER IF EXISTS refresh_lines_info_tr ON chapter_info;
DROP FUNCTION IF EXISTS refresh_lines_info_fn(INTEGER);
DROP MATERIALIZED VIEW IF EXISTS lines_info;

-- Create a new materialized view named 'lines_info' which splits the text and translated_text fields of chapter_info
-- into individual lines
CREATE MATERIALIZED VIEW lines_info AS
WITH split_data AS (
    SELECT 
        ci0.id,
        ci0.source_key,
        ci0.number,
        ci0.book_id,
        unnest(string_to_array(ci0.text, E'\n')) AS original_line,
        unnest(string_to_array(ci0.translated_text, E'\n')) AS translated_line,
        generate_series(1, 1_000_000) AS line_index
    FROM chapter_info ci0 
    ORDER BY ci0.book_id, ci0.number
)
SELECT 
	MD5(ci1.id || '-' || ci1.line_index)::text AS id,
    ci1.id AS chapter_id,
    ci1.source_key,
    ci1.number,
    ci1.book_id,
    ci1.line_index,
    ci1.original_line,
    ci1.translated_line,
    to_jsonb(
        regexp_split_to_array(
            regexp_replace(ci1.original_line, '([.!?])\s+', '\1|', 'g'),
            '\|'
    )) AS original_sentences,
    to_jsonb(
        regexp_split_to_array(
        regexp_replace(ci1.translated_line, '([.!?])\s+', '\1|', 'g'),
        '\|'
    )) AS translated_sentences
FROM split_data ci1
WHERE LENGTH(original_line) > 0 OR LENGTH(translated_line) > 0;

-- Create a unique index on the 'id' column of the materialized view 'lines_info' to ensure each row
-- has a unique identifier
CREATE UNIQUE INDEX ON lines_info (id);

CREATE INDEX idx_lines_info_chapter_id ON lines_info(chapter_id);
CREATE INDEX idx_lines_info_book_id ON lines_info(book_id);
CREATE INDEX idx_lines_info_source_key ON lines_info(source_key);

CREATE INDEX idx_lines_info_original_sentences ON lines_info USING GIN (original_sentences);
CREATE INDEX idx_lines_info_translated_sentences ON lines_info USING GIN (translated_sentences);

CREATE INDEX idx_lines_info_original_line_ts ON lines_info USING GIN (to_tsvector('english', original_line));
CREATE INDEX idx_lines_info_translated_line_ts ON lines_info USING GIN (to_tsvector('russian', translated_line));

CREATE INDEX idx_lines_info_original_line_gin ON lines_info USING GIN (original_line gin_trgm_ops);
CREATE INDEX idx_lines_info_translated_line_gin ON lines_info USING GIN (translated_line gin_trgm_ops);
