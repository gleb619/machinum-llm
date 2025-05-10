-- History table to store patches for chapter_info changes
CREATE TABLE chapter_info_history (
    id VARCHAR(36) PRIMARY KEY,
    chapter_info_id VARCHAR(36) NOT NULL,
    number INTEGER NOT NULL DEFAULT 0,
    field_name VARCHAR(50) NOT NULL,
    patch JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key relationship to the main table
    CONSTRAINT fk_chapter_info
        FOREIGN KEY (chapter_info_id)
        REFERENCES chapter_info(id)
        ON DELETE CASCADE
);

-- Create indexes for efficient querying of history records
CREATE INDEX idx_chapter_info_history_chapter_id ON chapter_info_history(chapter_info_id);
CREATE INDEX idx_chapter_info_history_field ON chapter_info_history(field_name);
CREATE INDEX idx_chapter_info_history_created_at ON chapter_info_history(created_at);

-- Create a combined index for the most common query pattern
CREATE INDEX idx_chapter_info_history_lookup ON chapter_info_history(chapter_info_id, field_name, created_at);
