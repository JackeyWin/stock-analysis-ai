CREATE TABLE IF NOT EXISTS stock_monitoring_record (
    id BIGSERIAL PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL,
    job_id VARCHAR(100),
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_monitoring_record_code_time
    ON stock_monitoring_record (stock_code, created_at DESC);

