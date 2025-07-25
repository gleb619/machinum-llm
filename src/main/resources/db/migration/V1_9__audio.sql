CREATE TABLE audio_files (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    chapter_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    minio_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    metadata JSONB DEFAULT '{}'::jsonb,
    FOREIGN KEY (chapter_id) REFERENCES chapter_info(id) ON DELETE CASCADE
);

CREATE INDEX idx_audio_files_chapter_id ON audio_files(chapter_id);
CREATE INDEX idx_audio_files_minio_key ON audio_files(minio_key);