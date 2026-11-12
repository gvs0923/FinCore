ALTER TABLE risk_decisions
    ADD COLUMN resolution VARCHAR(7),
    ADD COLUMN resolved_at TIMESTAMPTZ,
    ADD COLUMN signals JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE risk_decisions
    ADD CONSTRAINT chk_risk_resolution
    CHECK (
        (resolution IS NULL AND resolved_at IS NULL)
        OR
        (
            decision = 'REVIEW'
            AND resolution IS NOT NULL
            AND resolution IN ('APPROVE', 'REJECT')
            AND resolved_at IS NOT NULL
        )
    );

CREATE INDEX idx_risk_unresolved_reviews
    ON risk_decisions (created_at)
    WHERE decision = 'REVIEW' AND resolution IS NULL;