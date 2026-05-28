CREATE TABLE IF NOT EXISTS company (
    ticker VARCHAR(10) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    sector VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS fundamental (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticker VARCHAR(10) NOT NULL REFERENCES company(ticker),
    metric VARCHAR(50) NOT NULL,
    metric_value DOUBLE NOT NULL,
    date DATE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_fund_ticker_metric ON fundamental(ticker, metric);