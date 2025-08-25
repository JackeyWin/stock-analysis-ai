CREATE TABLE stock_analysis_result (
    id BIGSERIAL PRIMARY KEY,
    machine_id VARCHAR(255) NOT NULL,
    stock_code VARCHAR(255) NOT NULL,
    stock_name VARCHAR(255),
    analysis_time TIMESTAMP NOT NULL,
    full_analysis TEXT,
    company_fundamental_analysis TEXT,
    operation_strategy TEXT,
    intraday_operations TEXT,
    industry_policy_orientation TEXT,
    status VARCHAR(255),
    analysis_duration BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_analysis_machine_id ON stock_analysis_result(machine_id);
CREATE INDEX idx_analysis_stock_code ON stock_analysis_result(stock_code);
CREATE INDEX idx_analysis_time ON stock_analysis_result(analysis_time);
CREATE INDEX idx_analysis_machine_stock ON stock_analysis_result(machine_id, stock_code);
CREATE INDEX idx_analysis_status ON stock_analysis_result(status);