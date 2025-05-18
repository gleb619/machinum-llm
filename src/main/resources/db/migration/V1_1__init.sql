CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS fuzzystrmatch;

CREATE TABLE IF NOT EXISTS books (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) UNIQUE,
    book_state JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chapter_info (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    source_key TEXT,
    number INTEGER DEFAULT 0,
    title TEXT,
    translated_title TEXT,
    raw_text TEXT,
    text TEXT,
    clean_chunks JSON,
    proofread_text TEXT,
    translated_text TEXT,
    fixed_translated_text TEXT,
    translated_chunks JSON,
    fixed_translated_chunks JSON,
    summary TEXT,
    consolidated_summary TEXT,
    keywords JSON,
    self_consistency JSON,
    quotes JSON,
    characters JSON,
    themes TEXT,
    perspective TEXT,
    tone TEXT,
    foreshadowing TEXT,
    names JSON,
    scenes JSON,
    warnings JSONB,
    book_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_book
        FOREIGN KEY(book_id) 
        REFERENCES books(id)
        ON DELETE CASCADE
);


CREATE INDEX IF NOT EXISTS chapter_info_book_number_idx ON chapter_info(book_id, number);
CREATE INDEX IF NOT EXISTS chapter_info_book_idx ON chapter_info(book_id);
CREATE INDEX IF NOT EXISTS chapter_info_number_idx ON chapter_info(number);

CREATE TABLE IF NOT EXISTS vector_store (
	id uuid DEFAULT uuid_generate_v4() NOT NULL,
	"content" text NULL,
	metadata json NULL,
	embedding vector(384) NULL,
	CONSTRAINT vector_store_pkey PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx ON vector_store USING hnsw (embedding vector_cosine_ops);