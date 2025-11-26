CREATE TABLE IF NOT EXISTS chapter_context (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    book_id VARCHAR(36) NOT NULL,
    chapter_number INTEGER NOT NULL,
    -- Title fields
    title_content TEXT,
    title_embedding vector(384),
    -- Translated title fields
    translated_title_content TEXT,
    translated_title_embedding vector(384),
    -- Text content fields
    text_content JSONB,
    text_embedding JSONB,
    -- Translated text fields
    translated_text_content JSONB,
    translated_text_embedding JSONB,
    -- Summary fields
    summary_content TEXT,
    summary_embedding vector(384),
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- Foreign key constraint (one-to-one relationship)
    CONSTRAINT fk_chapter_context_chapter
        FOREIGN KEY(id)
        REFERENCES chapter_info(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_chapter_context_book
        FOREIGN KEY(book_id)
        REFERENCES books(id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS names_context (
    id VARCHAR(255) PRIMARY KEY, -- chapterId + sequential number (e.g., 'abc1', 'abc2')
    chapter_id VARCHAR(36) NOT NULL,
    name TEXT NOT NULL,
    category VARCHAR(100),
    description TEXT,
    translated_name TEXT,
    embedding vector(384),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_names_context_chapter
        FOREIGN KEY(chapter_id)
        REFERENCES chapter_info(id)
        ON DELETE CASCADE
);

-- Custom functions for JSON vector similarity
CREATE OR REPLACE FUNCTION json_array_cosine_similarity(target_vector vector(384), json_vectors jsonb)
RETURNS float AS $$
DECLARE
    total_similarity float := 0;
    count int := 0;
    elem_vector vector(384);
BEGIN
    FOR elem_vector IN SELECT (value::text)::vector(384)
                      FROM jsonb_array_elements_text(json_vectors) as value
    LOOP
        total_similarity := total_similarity + (1 - (target_vector <=> elem_vector));
        count := count + 1;
    END LOOP;

    IF count = 0 THEN
        RETURN 0;
    END IF;

    RETURN total_similarity / count;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION find_similar_embeddings_by_json(
    target_embedding vector(384),
    json_content jsonb,
    json_embeddings jsonb,
    min_similarity float DEFAULT 0.6,
    max_results int DEFAULT 5
)
RETURNS TABLE (
    chunk_text text,
    chunk_embedding vector(384),
    similarity float
) AS $$
DECLARE
    i int := 0;
BEGIN
    RETURN QUERY
    SELECT
        (jsonb_array_elements_text(json_content))[ordinality] as chunk_text,
        (jsonb_array_elements_text(json_embeddings))[ordinality]::vector(384) as chunk_embedding,
        (1 - (target_embedding <=> (jsonb_array_elements_text(json_embeddings))[ordinality]::vector(384))) as similarity
    FROM generate_series(1, jsonb_array_length(json_content)) as ordinality
    WHERE (1 - (target_embedding <=> (jsonb_array_elements_text(json_embeddings))[ordinality]::vector(384))) >= min_similarity
    ORDER BY (1 - (target_embedding <=> (jsonb_array_elements_text(json_embeddings))[ordinality]::vector(384))) DESC
    LIMIT max_results;
END;
$$ LANGUAGE plpgsql;

-- Create individual indexes for each embedding column in chapter_context
CREATE INDEX IF NOT EXISTS chapter_context_title_embedding_idx ON chapter_context USING hnsw (title_embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS chapter_context_translated_title_embedding_idx ON chapter_context USING hnsw (translated_title_embedding vector_cosine_ops);
-- Text fields now use JSONB, add GIN indexes for searching
CREATE INDEX IF NOT EXISTS chapter_context_text_content_idx ON chapter_context USING GIN (text_content);
CREATE INDEX IF NOT EXISTS chapter_context_text_embedding_idx ON chapter_context USING GIN (text_embedding);
CREATE INDEX IF NOT EXISTS chapter_context_translated_text_content_idx ON chapter_context USING GIN (translated_text_content);
CREATE INDEX IF NOT EXISTS chapter_context_translated_text_embedding_idx ON chapter_context USING GIN (translated_text_embedding);
CREATE INDEX IF NOT EXISTS chapter_context_summary_embedding_idx ON chapter_context USING hnsw (summary_embedding vector_cosine_ops);

-- Indexes for chapter_context relationships
CREATE INDEX IF NOT EXISTS chapter_context_book_id_idx ON chapter_context(book_id);
CREATE INDEX IF NOT EXISTS chapter_context_number_idx ON chapter_context(chapter_number);
CREATE INDEX IF NOT EXISTS chapter_context_book_number_idx ON chapter_context(book_id, chapter_number);

CREATE INDEX IF NOT EXISTS names_context_chapter_id_idx ON names_context(chapter_id);
CREATE INDEX IF NOT EXISTS names_context_name_idx ON names_context(name);
CREATE INDEX IF NOT EXISTS names_context_embedding_idx ON names_context USING hnsw (embedding vector_cosine_ops);

-- Create a new view that joins chapter_glossary with names_context
CREATE OR REPLACE VIEW chapter_context_glossary AS
SELECT
    cg.id,
    cg.chapter_id,
    cg.source_key,
    cg.number,
    cg.title,
    cg.book_id,
    cg.name,
    cg.category,
    cg.description,
    cg.translated,
    cg.translated_name,
    cg.raw_json,
    cg.search_string1,
    cg.search_string2,
    cg.search_string3,
    nc.description AS context_description,
    nc.translated_name AS context_translated_name,
    nc.embedding,
    nc.created_at AS context_created_at,
    nc.updated_at AS context_updated_at
FROM
    chapter_glossary cg
LEFT JOIN
    names_context nc ON cg.chapter_id = nc.chapter_id AND cg.name = nc.name;
