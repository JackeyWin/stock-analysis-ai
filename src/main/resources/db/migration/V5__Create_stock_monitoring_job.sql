CREATE TABLE stock_monitoring_job (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(255) UNIQUE NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    interval_minutes INTEGER NOT NULL,
    analysis_id VARCHAR(255),
    machine_id VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'running',
    start_time TIMESTAMP NOT NULL,
    last_run_time TIMESTAMP,
    last_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_stock_monitoring_job_stock_code ON stock_monitoring_job(stock_code);
CREATE INDEX idx_stock_monitoring_job_status ON stock_monitoring_job(status);
CREATE INDEX idx_stock_monitoring_job_job_id ON stock_monitoring_job(job_id);
