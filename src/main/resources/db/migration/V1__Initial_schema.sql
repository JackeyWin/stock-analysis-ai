CREATE TABLE daily_recommendation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recommendation_id VARCHAR(255) UNIQUE NOT NULL,
    recommendation_date VARCHAR(255) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    market_overview TEXT,
    policy_hotspots TEXT,
    industry_hotspots TEXT,
    policy_hotspots_and_industry_hotspots TEXT,
    summary TEXT,
    analyst_view TEXT,
    risk_warning TEXT,
    status VARCHAR(255),
    version INT
);

CREATE TABLE stock_recommendation_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_code VARCHAR(255) NOT NULL,
    stock_name VARCHAR(255),
    sector VARCHAR(255),
    recommendation_reason TEXT,
    rating VARCHAR(255),
    score DOUBLE,
    target_price DOUBLE,
    current_price DOUBLE,
    expected_return DOUBLE,
    risk_level VARCHAR(255),
    investment_period VARCHAR(255),
    technical_analysis TEXT,
    fundamental_analysis TEXT,
    news_analysis TEXT,
    key_metrics TEXT,
    recommend_time TIMESTAMP,
    sort_order INT,
    is_hot BOOLEAN,
    daily_recommendation_id BIGINT,
    FOREIGN KEY (daily_recommendation_id) REFERENCES daily_recommendation(id)
);