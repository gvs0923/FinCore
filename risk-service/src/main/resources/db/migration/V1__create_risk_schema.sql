CREATE TABLE risk_decisions (
    id          BIGSERIAL PRIMARY KEY,
    payment_id  VARCHAR(64) NOT NULL,
    decision    VARCHAR(8)  NOT NULL,   -- ALLOW | REVIEW | BLOCK
    score       INT         NOT NULL,
    fired_rules TEXT        NOT NULL,   -- comma-joined rule names
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_risk_decisions_review
    ON risk_decisions (created_at) WHERE decision = 'REVIEW';