CREATE TABLE IF NOT EXISTS statistics (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    date TIMESTAMP NOT NULL,
    data JSON NOT NULL
);

DROP VIEW IF EXISTS statistics_view;
CREATE OR REPLACE VIEW statistics_view AS
SELECT
    id,
    date,
    value->>'mode' AS mode,
    value->>'runId' AS run_id,
    value->>'operationName' AS operation_name,
    value->>'operationType' AS operation_type,
    value->>'chapter' AS chapter,
    value->>'rayId' AS ray_id,
    cast(value->>'operationDate' as timestamp) AS operation_date,
    cast(value->>'operationTimeSeconds' as int) AS operation_time_seconds,
    value->>'operationTimeString' AS operation_time_string,
    cast(value->>'inputHistoryTokens' as int) AS input_history_tokens,
    cast(value->>'inputHistoryWords' as int) AS input_history_words,
    cast(value->>'inputTokens' as int) AS input_tokens,
    cast(value->>'inputWords' as int) AS input_words,
    cast(value->>'outputHistoryTokens' as int) AS output_history_tokens,
    cast(value->>'outputHistoryWords' as int) AS output_history_words,
    cast(value->>'outputTokens' as int) AS output_tokens,
    cast(value->>'outputWords' as int) AS output_words,
    cast(value->>'conversionPercent' as decimal) AS conversion_percent,
    cast(value->>'tokens' as int) AS tokens,
    cast(value->>'tokensLeft' as int) AS tokens_left
FROM statistics, json_array_elements(data) as item;
