CREATE TABLE risk_decision_metrics_hourly (
    window_start TIMESTAMPTZ NOT NULL,
    decision VARCHAR(8) NOT NULL,
    score_bucket VARCHAR(16) NOT NULL,
    decision_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (window_start, decision, score_bucket)
);