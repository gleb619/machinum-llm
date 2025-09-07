CREATE TABLE IF NOT EXISTS statistics (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    date TIMESTAMP NOT NULL,
    position INTEGER,
    mode VARCHAR(255),
    run_id VARCHAR(255),
    operation_name VARCHAR(255),
    operation_type VARCHAR(255),
    chapter VARCHAR(255),
    ray_id VARCHAR(255),
    operation_date TIMESTAMP,
    operation_time_seconds INTEGER,
    operation_time_string VARCHAR(255),
    input_history_tokens INTEGER,
    input_history_words INTEGER,
    input_tokens INTEGER,
    input_words INTEGER,
    output_history_tokens INTEGER,
    output_history_words INTEGER,
    output_tokens INTEGER,
    output_words INTEGER,
    conversion_percent DECIMAL,
    tokens INTEGER,
    tokens_left INTEGER,
    ai_options JSONB NOT NULL,
    messages JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

DROP VIEW IF EXISTS statistics_view;
CREATE OR REPLACE VIEW statistics_view AS
SELECT
    id,
    date,
    mode,
    run_id,
    operation_name,
    operation_type,
    chapter,
    ray_id,
    operation_date,
    operation_time_seconds,
    operation_time_string,
    input_history_tokens,
    input_history_words,
    input_tokens,
    input_words,
    output_history_tokens,
    output_history_words,
    output_tokens,
    output_words,
    conversion_percent,
    tokens,
    tokens_left
FROM statistics;

CREATE INDEX IF NOT EXISTS idx_statistics_date ON statistics(date);
CREATE INDEX IF NOT EXISTS idx_statistics_operation_name ON statistics(operation_name);
CREATE INDEX IF NOT EXISTS idx_statistics_run_id ON statistics(run_id);
CREATE INDEX IF NOT EXISTS idx_statistics_operation_date ON statistics(operation_date);
