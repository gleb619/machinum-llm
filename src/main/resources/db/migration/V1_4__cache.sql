CREATE TABLE IF NOT EXISTS cache_store (
    key_name VARCHAR(255) PRIMARY KEY,
    value_data TEXT NOT NULL,
    value_type VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_key_value_store_key ON cache_store (key_name);