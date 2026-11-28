ALTER TABLE risk_decisions
    ADD COLUMN ml_probability NUMERIC(5,4),
    ADD COLUMN ml_model_version VARCHAR(64);

CREATE INDEX idx_risk_decisions_payment
    ON risk_decisions (payment_id, created_at DESC);
