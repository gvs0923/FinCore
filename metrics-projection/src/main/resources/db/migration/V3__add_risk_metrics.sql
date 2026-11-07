CREATE TABLE risk_metrics_hourly (
    id           BIGSERIAL PRIMARY KEY,
    window_start TIMESTAMPTZ NOT NULL,
    decision     VARCHAR(8)  NOT NULL,
    rule_fired   VARCHAR(64) NOT NULL DEFAULT '',  -- '' = "no rule fired"
    decision_count BIGINT    NOT NULL DEFAULT 0,
    UNIQUE (window_start, decision, rule_fired)
);